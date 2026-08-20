package xuanmo.arcartxsuite.battlepass.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.battlepass.model.BattlePassItemDefinition;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;

/**
 * 战令奖励发放器，负责根据奖励类型执行实际的奖励发放逻辑。
 * <p>
 * 支持四种奖励类型：
 * <ul>
 *   <li>{@code command} — 以控制台身份执行命令列表，支持 {@code {player}} 占位符</li>
 *   <li>{@code item} — 通过 {@link ItemSourceRegistry} 生成物品并直接放入玩家背包</li>
 *   <li>{@code currency} — 通过 {@link CurrencyBridgeAPI} 向玩家发放货币</li>
 *   <li>{@code mail} — 通过 {@link MailDispatchable} 发送邮件（预设 ID 或带物品附件的邮件）</li>
 * </ul>
 * 命令执行和物品发放在主线程同步调度。mail 类型在 Mail 模块不可用时降级为直接发放物品。
 */
public final class BattlePassRewardDispatcher {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final @Nullable ItemSourceRegistry itemSourceRegistry;
    private final @Nullable CurrencyBridgeAPI currencyManager;
    private volatile Supplier<MailDispatchable> mailProvider;
    private ItemRewardDispatcher itemRewardDispatcher;

    /**
     * 构造奖励发放器。
     *
     * @param plugin             插件实例
     * @param logger             日志记录器
     * @param itemSourceRegistry 物品来源注册表，可为 null（item 类型奖励将无法发放）
     * @param currencyManager    货币管理器，可为 null（currency 类型奖励将无法发放）
     * @param mailProvider       MailDispatchable 提供者，可为 null（mail 类型奖励将降级发放）
     */
    public BattlePassRewardDispatcher(JavaPlugin plugin, Logger logger,
                                       @Nullable ItemSourceRegistry itemSourceRegistry,
                                       @Nullable CurrencyBridgeAPI currencyManager,
                                       @Nullable Supplier<MailDispatchable> mailProvider) {
        this.plugin = plugin;
        this.logger = logger;
        this.itemSourceRegistry = itemSourceRegistry;
        this.currencyManager = currencyManager;
        this.mailProvider = mailProvider;
    }

    /**
     * 注入统一物品奖励发放器。
     * <p>
     * 注入后 item 类型奖励的物品发放（含背包满转邮件兜底）统一由 ItemRewardDispatcher 处理。
     *
     * @param itemRewardDispatcher 统一物品奖励发放器
     */
    public void setItemRewardDispatcher(ItemRewardDispatcher itemRewardDispatcher) {
        this.itemRewardDispatcher = itemRewardDispatcher;
    }

    /**
     * 向在线玩家发放指定奖励。
     * <p>
     * item 类型通过 ItemRewardDispatcher 统一发放（放入背包→背包满转邮件→无邮件则掉落），
     * currency 直接加，command 直接执行，mail 走邮箱。
     *
     * @param player 目标玩家
     * @param reward 要发放的奖励
     */
    public void dispatch(Player player, BattlePassReward reward) {
        if (player == null || !player.isOnline()) return;
        String type = reward.type();
        switch (type.toLowerCase()) {
            case "command" -> dispatchCommands(player, reward.commands());
            case "item" -> dispatchItem(player, reward);
            case "currency" -> dispatchCurrency(player, reward);
            case "mail" -> dispatchMail(player, player.getUniqueId(), player.getName(), reward);
            default -> logger.warning("未知奖励类型 [" + type + "]: " + reward.rewardId());
        }
    }

    /**
     * 向离线玩家发放指定奖励。
     * <p>
     * 返回是否已成功发放：
     * <ul>
     *   <li>mail 类型 → 通过 mail 发送，返回 true</li>
     *   <li>currency / command / item 类型 → 无法离线发放，返回 false（需上线后补发）</li>
     * </ul>
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名
     * @param reward     要发放的奖励
     * @return {@code true} 表示已成功发放，{@code false} 表示需上线后补发
     */
    public boolean dispatchOffline(UUID playerUuid, String playerName, BattlePassReward reward) {
        if (playerUuid == null || reward == null) return false;
        String type = reward.type();
        if ("mail".equalsIgnoreCase(type)) {
            dispatchMail(null, playerUuid, playerName, reward);
            return true;
        }
        // currency / command / item → 需上线后补发
        return false;
    }

    private void dispatchCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        AxsScheduler.runTask(plugin, () -> {
            for (String raw : commands) {
                if (raw == null || raw.isBlank()) continue;
                String command = raw.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
                if (command.startsWith("/")) command = command.substring(1);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Exception e) {
                    logger.warning("执行战令奖励命令失败: " + command + " | " + e.getMessage());
                }
            }
        });
    }

    private void dispatchItem(Player player, BattlePassReward reward) {
        BattlePassItemDefinition itemDef = reward.item();
        if (itemDef == null) {
            logger.warning("物品奖励缺少 item 定义: " + reward.rewardId());
            return;
        }
        if (itemSourceRegistry == null) {
            logger.warning("物品来源注册表不可用，无法发放物品奖励: " + reward.rewardId());
            return;
        }
        ItemStack item = itemSourceRegistry.generateItem(
            itemDef.source(), itemDef.id(), itemDef.amount(), itemDef.nbt(), player);
        if (item == null) {
            logger.warning("物品奖励生成失败: " + reward.rewardId()
                + " source=" + itemDef.source() + " id=" + itemDef.id());
            return;
        }
        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("battlepass")
            .sourceDetail("reward:" + reward.rewardId())
            .overflowMailSubject("战令奖励溢出物品")
            .overflowMailBody("您的背包已满，战令奖励物品通过邮件补发。")
            .overflowMailSenderName("战令")
            .build();
        itemRewardDispatcher.dispatchItem(player, item, options);
    }

    private void dispatchCurrency(Player player, BattlePassReward reward) {
        String currencyId = reward.currency();
        double amount = reward.currencyAmount();
        if (currencyId == null || currencyId.isBlank() || amount <= 0) {
            logger.warning("货币奖励配置无效: " + reward.rewardId()
                + " currency=" + currencyId + " amount=" + amount);
            return;
        }
        if (currencyManager == null) {
            logger.warning("货币管理器不可用，无法发放货币奖励: " + reward.rewardId());
            return;
        }
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) {
            logger.warning("货币桥接不可用: " + currencyId + "，奖励 " + reward.rewardId() + " 发放失败");
            return;
        }
        AxsScheduler.runTask(plugin, () -> {
            CurrencyTransactionResult result =
                bridge.deposit(player, BigDecimal.valueOf(amount));
            if (!result.success()) {
                logger.warning("货币发放失败: " + reward.rewardId()
                    + " currency=" + currencyId + " amount=" + amount
                    + " reason=" + result.message());
            }
        });
    }

    /**
     * 通过 mail 模块发放奖励。
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li>预设模式：配置了 mail-preset-id，调用 {@link MailDispatchable#dispatchPreset}</li>
     *   <li>直接模式：配置了 mail-subject/mail-body/item，调用 {@link MailDispatchable#sendItemMail}</li>
     * </ul>
     * Mail 模块不可用时：
     * <ul>
     *   <li>预设模式：降级为执行命令（commands 字段）</li>
     *   <li>直接模式且有物品：降级为直接发放物品（在线时放入背包，离线时跳过）</li>
     *   <li>直接模式无物品：降级为执行命令</li>
     * </ul>
     *
     * @param onlinePlayer 在线玩家实例，离线发放时为 null
     * @param playerUuid   玩家 UUID
     * @param playerName   玩家名
     * @param reward       奖励定义
     */
    private void dispatchMail(@Nullable Player onlinePlayer, UUID playerUuid, String playerName, BattlePassReward reward) {
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail == null) {
            // Mail 模块不可用，降级处理
            logger.fine("Mail 模块不可用，降级发放奖励: " + reward.rewardId());
            fallbackFromMail(onlinePlayer, reward);
            return;
        }
        List<String> presetIds = reward.mailPresets();
        if (presetIds != null && !presetIds.isEmpty()) {
            // 预设模式（异步发送，失败时降级为直接发放）
            AxsScheduler.runAsync(plugin, () -> {
                boolean allSuccess = xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
                    mailProvider, playerName, presetIds, "battlepass", logger);
                if (!allSuccess) {
                    logger.warning("邮件预设发送存在失败，降级为直接发放: player=" + playerName
                        + " reward=" + reward.rewardId());
                    AxsScheduler.runTask(plugin, () -> fallbackFromMail(onlinePlayer, reward));
                }
            });
            return;
        }
        // 直接模式：发送带物品附件的邮件
        String subject = reward.mailSubject() != null ? reward.mailSubject() : "战令奖励";
        String body = reward.mailBody() != null ? reward.mailBody() : "您收到了战令奖励，请查收附件。";
        BattlePassItemDefinition itemDef = reward.item();
        if (itemDef == null) {
            // 无物品附件，降级为执行命令
            logger.fine("mail 类型奖励无物品附件，降级为执行命令: " + reward.rewardId());
            fallbackFromMail(onlinePlayer, reward);
            return;
        }
        if (itemSourceRegistry == null) {
            logger.warning("物品来源注册表不可用，无法生成 mail 类型奖励物品: " + reward.rewardId());
            return;
        }
        ItemStack item = itemSourceRegistry.generateItem(
            itemDef.source(), itemDef.id(), itemDef.amount(), itemDef.nbt(), onlinePlayer);
        if (item == null) {
            logger.warning("mail 类型奖励物品生成失败: " + reward.rewardId()
                + " source=" + itemDef.source() + " id=" + itemDef.id());
            return;
        }
        AxsScheduler.runAsync(plugin, () -> {
            try {
                MailSendResult result = mail.sendMail(MailSendRequest.builder(playerUuid, subject, body)
                    .attachment(MailAttachmentSpec.item(item))
                    .senderName("战令")
                    .sourceModule("battlepass")
                    .build());
                if (!result.success()) {
                    logger.warning("带附件邮件发送失败: player=" + playerName
                        + "，降级为直接发放物品 — " + result.message());
                    AxsScheduler.runTask(plugin, () -> fallbackFromMail(onlinePlayer, reward));
                }
            } catch (Exception e) {
                logger.warning("带附件邮件发送异常: " + e.getMessage());
            }
        });
    }

    /**
     * Mail 模块不可用时的降级发放逻辑。
     */
    private void fallbackFromMail(@Nullable Player player, BattlePassReward reward) {
        if (player == null || !player.isOnline()) {
            // 离线玩家无法降级发放，记录日志
            logger.fine("离线玩家无法降级发放 mail 奖励: " + reward.rewardId());
            return;
        }
        // 优先尝试发放物品
        if (reward.item() != null) {
            dispatchItem(player, reward);
        }
        // 再执行命令（如果有）
        if (reward.commands() != null && !reward.commands().isEmpty()) {
            dispatchCommands(player, reward.commands());
        }
    }
}
