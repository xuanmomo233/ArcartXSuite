package xuanmo.arcartxsuite.lottery.engine;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/**
 * 奖品发放器。
 * <p>
 * 根据 {@link PoolItem.DeliveryType} 将奖品以直接发放、命令执行或邮件发送的方式
 * 投递给玩家。DIRECT 类型物品发放统一通过 {@link ItemRewardDispatcher}
 * （放入背包→背包满转邮件→无邮件则掉落），投递失败时返回失败结果供上层入队待领取。
 */
public class PrizeDistributor {

    private final ItemSourceRegistry itemSourceRegistry;
    private final Supplier<MailDispatchable> mailSupplier;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final Logger logger;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    /**
     * 构造奖品发放器。
     *
     * @param itemSourceRegistry 物品来源注册表
     * @param mailSupplier       邮件能力延迟供应器
     * @param itemRewardDispatcher 统一物品奖励发放器
     * @param logger             日志记录器
     */
    public PrizeDistributor(org.bukkit.plugin.java.JavaPlugin plugin,
                            ItemSourceRegistry itemSourceRegistry,
                            Supplier<MailDispatchable> mailSupplier,
                            ItemRewardDispatcher itemRewardDispatcher,
                            Logger logger) {
        this.plugin = plugin;
        this.itemSourceRegistry = itemSourceRegistry;
        this.mailSupplier = mailSupplier;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.logger = logger;
    }

    /**
     * 向玩家发放奖品。
     *
     * @param player 目标玩家
     * @param item   奖品定义
     * @return 发放结果，包含成功/失败状态与失败原因
     */
    @NotNull
    public DistributionResult distribute(@NotNull Player player, @NotNull PoolItem item) {
        DistributionResult result = switch (item.delivery()) {
            case DIRECT -> giveDirectItem(player, item);
            case COMMAND -> executeCommands(player, item);
            case MAIL -> sendMail(player, item);
        };
        if (result.success()) executeExtraCommands(player, item);
        return result;
    }

    private DistributionResult giveDirectItem(Player player, PoolItem item) {
        ItemStack stack = generateItem(item, player);
        if (stack == null) {
            logger.warning("无法生成物品: " + item.id() + " (source=" + item.source() + ")");
            return DistributionResult.failure(null, "物品生成失败");
        }

        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("lottery")
            .sourceDetail("prize:" + item.key())
            .overflowMailSubject("抽奖奖励溢出物品")
            .overflowMailBody("您的背包已满，抽奖奖励物品通过邮件补发。")
            .overflowMailSenderName("抽奖")
            .build();
        ItemRewardDispatcher.DispatchResult result = itemRewardDispatcher.dispatchItem(player, stack, options);
        if (result.success()) {
            if (result.mailedCount() > 0) return DistributionResult.mailSent(stack);
            if (result.pendingCount() > 0) return DistributionResult.pendingSaved(stack);
            return DistributionResult.success(stack);
        }
        return DistributionResult.failure(stack, result.message());
    }

    private DistributionResult executeCommands(Player player, PoolItem item) {
        List<String> commands = item.commands();
        if (commands == null || commands.isEmpty()) return DistributionResult.failure(null, "奖励命令为空");
        boolean allSuccess = true;
        for (String command : commands) {
            if (!dispatchCommand(player, item, command)) allSuccess = false;
        }
        if (allSuccess) return DistributionResult.success(null);
        return DistributionResult.failure(null, "部分命令执行失败");
    }

    private void executeExtraCommands(Player player, PoolItem item) {
        for (String command : item.extraCommands()) dispatchCommand(player, item, command);
    }

    private boolean dispatchCommand(Player player, PoolItem item, String command) {
        if (command == null || command.isBlank()) return true;
        String processed = command.replace("{player}", xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()))
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{amount}", Integer.toString(item.amount()))
            .replace("{item}", item.name());
        String normalized = processed.startsWith("/") ? processed.substring(1) : processed;
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
        }
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized));
        return true;
    }

    private DistributionResult sendMail(Player player, PoolItem item) {
        if (item.mailPresets() == null || item.mailPresets().isEmpty()) {
            logger.warning("奖品 " + item.key() + " 配置了 MAIL 但未设置 mail.presets");
            return DistributionResult.failure(null, "缺少 mail.presets");
        }
        boolean allSuccess = xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
            mailSupplier, player.getName(), item.mailPresets(), "lottery", logger);
        if (allSuccess) {
            // MAIL 成功时不携带 generated item，实际奖励来自 mailPresets
            return DistributionResult.mailSent(null);
        }
        MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
        if (mail == null) {
            return DistributionResult.failure(null, "邮件能力不可用");
        }
        // MAIL 失败时不传 generated item，避免 pending 队列内容与邮件预设不一致
        return DistributionResult.failure(null, "邮件发送失败");
    }

    /**
     * 根据奖品定义生成对应的 {@link ItemStack}。
     * <p>
     * 委托给 {@link ItemSourceRegistry#generateItem} 统一生成，
     * 根据 source 字符串分发到对应桥接。
     *
     * @param item   奖品定义
     * @param player 玩家上下文（Overture 等需要），可为 {@code null}
     * @return 生成的物品堆，生成失败时返回 {@code null}
     */
    @Nullable
    public ItemStack generateItem(PoolItem item, @Nullable Player player) {
        if (item.id() == null || item.id().isBlank()) return null;
        return itemSourceRegistry.generateItem(item.source(), item.id(), item.amount(), item.nbt(), player);
    }

    /**
     * 将物品堆序列化为 Base64 字符串。
     *
     * @param stack 物品堆，为 {@code null} 时返回空字符串
     * @return Base64 编码的序列化字符串，序列化失败时返回空字符串
     */
    @NotNull
    public String toItemJson(@Nullable ItemStack stack) {
        if (stack == null) return "";
        try {
            byte[] bytes = ItemSerializer.serialize(stack);
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            logger.warning("序列化物品失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 奖品发放结果。
     *
     * @param success       是否发放成功
     * @param item          涉及的物品堆，可能为 {@code null}
     * @param mailSent      是否通过邮件发送
     * @param failureReason 失败原因，成功时为空字符串
     */
    public record DistributionResult(boolean success, @Nullable ItemStack item,
                                     boolean mailSent, boolean pendingSaved,
                                     @NotNull String failureReason) {
        /**
         * 构造直接发放成功的结果。
         *
         * @param item 涉及的物品堆
         * @return 成功结果实例
         */
        public static DistributionResult success(@Nullable ItemStack item) {
            return new DistributionResult(true, item, false, false, "");
        }

        /**
         * 构造邮件发送成功的结果。
         *
         * @param item 涉及的物品堆
         * @return 邮件发送结果实例
         */
        public static DistributionResult mailSent(@Nullable ItemStack item) {
            return new DistributionResult(true, item, true, false, "");
        }

        /**
         * 构造物品已暂存待发放的结果。
         *
         * @param item 涉及的物品堆
         * @return 暂存结果实例
         */
        public static DistributionResult pendingSaved(@Nullable ItemStack item) {
            return new DistributionResult(true, item, false, true, "");
        }

        /**
         * 构造发放失败的结果。
         *
         * @param item   涉及的物品堆
         * @param reason 失败原因
         * @return 失败结果实例
         */
        public static DistributionResult failure(@Nullable ItemStack item, @NotNull String reason) {
            return new DistributionResult(false, item, false, false, reason);
        }
    }
}
