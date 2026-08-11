package xuanmo.arcartxsuite.lottery.engine;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/**
 * 奖品发放器。
 * <p>
 * 根据 {@link PoolItem.DeliveryType} 将奖品以直接发放、命令执行或邮件发送的方式
 * 投递给玩家。背包满时自动转投邮件，投递失败时返回失败结果供上层入队待领取。
 */
public class PrizeDistributor {

    private final ItemSourceRegistry itemSourceRegistry;
    private final Supplier<MailDispatchable> mailSupplier;
    private final Logger logger;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    /**
     * 构造奖品发放器。
     *
     * @param itemSourceRegistry 物品来源注册表
     * @param mailSupplier       邮件能力延迟供应器
     * @param logger             日志记录器
     */
    public PrizeDistributor(org.bukkit.plugin.java.JavaPlugin plugin,
                            ItemSourceRegistry itemSourceRegistry,
                            Supplier<MailDispatchable> mailSupplier,
                            Logger logger) {
        this.plugin = plugin;
        this.itemSourceRegistry = itemSourceRegistry;
        this.mailSupplier = mailSupplier;
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
        ItemStack stack = generateItem(item);
        if (stack == null) {
            logger.warning("[Lottery] 无法生成物品: " + item.pluginId() + " (type=" + item.pluginType() + ")");
            return DistributionResult.failure(null, "物品生成失败");
        }

        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
            if (mail != null && item.mailPreset() != null && !item.mailPreset().isBlank()
                    && mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery")) {
                return DistributionResult.mailSent(stack);
            }
            return DistributionResult.failure(stack, "背包空间不足且邮件发送失败");
        }
        return DistributionResult.success(stack);
    }

    private DistributionResult executeCommands(Player player, PoolItem item) {
        List<String> commands = item.commands();
        if (commands == null || commands.isEmpty()) return DistributionResult.failure(null, "奖励命令为空");
        for (String command : commands) dispatchCommand(player, item, command);
        return DistributionResult.success(null);
    }

    private void executeExtraCommands(Player player, PoolItem item) {
        for (String command : item.extraCommands()) dispatchCommand(player, item, command);
    }

    private void dispatchCommand(Player player, PoolItem item, String command) {
        if (command == null || command.isBlank()) return;
        String processed = command.replace("{player}", player.getName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{amount}", Integer.toString(item.amount()))
            .replace("{item}", item.name());
        String normalized = processed.startsWith("/") ? processed.substring(1) : processed;
        AxsScheduler.runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized));
    }

    private DistributionResult sendMail(Player player, PoolItem item) {
        MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
        if (mail == null) {
            logger.warning("[Lottery] MailDispatchable 不可用，无法发送邮件奖励");
            return DistributionResult.failure(generateItem(item), "邮件能力不可用");
        }
        if (item.mailPreset() != null && !item.mailPreset().isBlank()) {
            ItemStack stack = generateItem(item);
            if (mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery")) {
                return DistributionResult.mailSent(stack);
            }
            return DistributionResult.failure(stack, "邮件发送失败");
        }
        logger.warning("[Lottery] 奖品 " + item.id() + " 配置了 MAIL 但未设置 mail-preset");
        return DistributionResult.failure(generateItem(item), "缺少 mail-preset");
    }

    /**
     * 根据奖品定义生成对应的 {@link ItemStack}。
     *
     * @param item 奖品定义
     * @return 生成的物品堆，生成失败时返回 {@code null}
     */
    @Nullable
    public ItemStack generateItem(PoolItem item) {
        if (item.pluginType() == PoolItem.PluginItemType.PLAIN && item.itemJson() != null
                && !item.itemJson().isBlank()) {
            try {
                return ItemSerializer.deserialize(java.util.Base64.getDecoder().decode(item.itemJson()));
            } catch (Exception e) {
                logger.warning("[Lottery] 预序列化物品解析失败: " + e.getMessage());
            }
        }
        if (item.pluginId() == null || item.pluginId().isBlank()) return null;
        return switch (item.pluginType()) {
            case MYTHIC -> itemSourceRegistry.generateMythicItem(item.pluginId(), item.amount());
            case NEIGE -> itemSourceRegistry.generateNeigeItem(item.pluginId(), item.amount());
            case OVERTURE -> itemSourceRegistry.generateOvertureItem(item.pluginId(), null, item.amount());
            case MMO -> itemSourceRegistry.generateMmoItem(item.pluginId(), item.pluginId(), item.amount());
            case PLAIN -> {
                String materialName = item.pluginId().startsWith("minecraft:")
                    ? item.pluginId().substring("minecraft:".length()) : item.pluginId();
                Material material = Material.matchMaterial(materialName, true);
                yield material == null ? null : new ItemStack(material, item.amount());
            }
        };
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
            logger.warning("[Lottery] 序列化物品失败: " + e.getMessage());
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
                                     boolean mailSent, @NotNull String failureReason) {
        /**
         * 构造直接发放成功的结果。
         *
         * @param item 涉及的物品堆
         * @return 成功结果实例
         */
        public static DistributionResult success(@Nullable ItemStack item) {
            return new DistributionResult(true, item, false, "");
        }

        /**
         * 构造邮件发送成功的结果。
         *
         * @param item 涉及的物品堆
         * @return 邮件发送结果实例
         */
        public static DistributionResult mailSent(@Nullable ItemStack item) {
            return new DistributionResult(true, item, true, "");
        }

        /**
         * 构造发放失败的结果。
         *
         * @param item   涉及的物品堆
         * @param reason 失败原因
         * @return 失败结果实例
         */
        public static DistributionResult failure(@Nullable ItemStack item, @NotNull String reason) {
            return new DistributionResult(false, item, false, reason);
        }
    }
}
