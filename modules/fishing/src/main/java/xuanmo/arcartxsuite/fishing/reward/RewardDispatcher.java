package xuanmo.arcartxsuite.fishing.reward;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;

/**
 * 奖励发放器，处理等级奖励与结算奖励的实际发放。
 * <p>
 * 物品奖励统一通过 {@link ItemRewardDispatcher} 发放（放入背包→背包满转邮件→无邮件则掉落）。
 * MAIL 类型奖励通过 Mail 模块发送预设邮件。
 * 离线玩家奖励暂存到统一 {@link PendingRewardService}，由本体在玩家上线时自动补发。
 */
public final class RewardDispatcher {

    private final PendingRewardService pendingRewardService;
    private final ItemSourceRegistry itemSourceRegistry;
    private final IconResolver iconResolver;
    private final Supplier<CurrencyBridgeAPI> currencyProvider;
    private final Supplier<MailDispatchable> mailProvider;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final Logger logger;
    private final String seasonId;

    public RewardDispatcher(@NotNull PendingRewardService pendingRewardService,
                            @NotNull ItemSourceRegistry itemSourceRegistry,
                            @NotNull IconResolver iconResolver,
                            @Nullable Supplier<CurrencyBridgeAPI> currencyProvider,
                            @Nullable Supplier<MailDispatchable> mailProvider,
                            @NotNull ItemRewardDispatcher itemRewardDispatcher,
                            @NotNull Logger logger,
                            @NotNull String seasonId) {
        this.pendingRewardService = pendingRewardService;
        this.itemSourceRegistry = itemSourceRegistry;
        this.iconResolver = iconResolver;
        this.currencyProvider = currencyProvider;
        this.mailProvider = mailProvider;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.logger = logger;
        this.seasonId = seasonId;
    }

    /**
     * 直接向在线玩家发放物品（通过 ItemRewardDispatcher 统一发放：背包满转邮件/掉落）。
     * <p>
     * 供 FishingService 等需要直接发放物品的场景调用，避免手写"背包满→掉落"逻辑。
     *
     * @param player 在线玩家
     * @param item   要发放的物品
     * @param sourceDetail 来源详情（用于审计/邮件 sourceDetail）
     */
    public void dispatchItem(@NotNull Player player, @NotNull ItemStack item, @NotNull String sourceDetail) {
        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("fishing")
            .sourceDetail(sourceDetail)
            .overflowMailSubject("钓鱼物品溢出")
            .overflowMailBody("您的背包已满，钓鱼物品通过邮件补发。")
            .overflowMailSenderName("钓鱼")
            .build();
        itemRewardDispatcher.dispatchItem(player, item, options);
    }

    /**
     * 发放单个奖励给玩家。
     *
     * @param player 在线玩家（null 表示玩家离线）
     * @param uuid   玩家 UUID
     * @param reward 奖励定义
     * @param description 奖励描述（用于 pending 记录）
     */
    public void dispatch(@Nullable Player player, @NotNull UUID uuid,
                         @NotNull SeasonRewardDefinition reward, @NotNull String description) {
        if (player != null && player.isOnline()) {
            dispatchDirect(player, reward);
        } else {
            // 离线：MAIL 类型走邮件，其他暂存 pending
            if (SeasonRewardDefinition.Type.fromKey(reward.type()) == SeasonRewardDefinition.Type.MAIL
                && isMailAvailable()) {
                dispatchViaMailOffline(uuid, reward, description);
            } else {
                savePending(uuid, reward, description);
            }
        }
    }

    // ─── 在线直接发放 ────────────────────────────────────────

    private void dispatchDirect(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case CURRENCY -> dispatchCurrency(player, reward);
            case ITEM -> dispatchItem(player, reward);
            case COMMAND -> dispatchCommand(player, reward);
            case MAIL -> {
                String description = reward.icon() != null && reward.icon().name() != null && !reward.icon().name().isBlank()
                    ? reward.icon().name() : reward.rewardId();
                dispatchViaMail(player, reward, description);
            }
        }
    }

    private void dispatchCurrency(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        if (reward.currencyId() == null) return;
        CurrencyBridgeAPI api = currencyProvider != null ? currencyProvider.get() : null;
        if (api == null) {
            logger.warning("货币桥不可用，无法发放货币奖励: " + reward.currencyId());
            return;
        }
        var bridge = api.bridge(reward.currencyId());
        if (bridge != null && bridge.available()) {
            bridge.deposit(player, BigDecimal.valueOf(reward.currencyAmount()));
        }
    }

    private void dispatchItem(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        if (reward.item() == null) return;
        ItemStack item = iconResolver.resolveItemStack(player, reward.item());
        if (item == null) return;
        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("fishing")
            .sourceDetail("season:" + seasonId)
            .overflowMailSubject("钓鱼奖励溢出物品")
            .overflowMailBody("您的背包已满，钓鱼奖励物品通过邮件补发。")
            .overflowMailSenderName("钓鱼")
            .build();
        itemRewardDispatcher.dispatchItem(player, item, options);
    }

    private void dispatchCommand(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        if (reward.commands() == null) return;
        for (String cmd : reward.commands()) {
            ThrottledCommandExecutor.dispatch(
                cmd.replace("{player}", xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName())));
        }
    }

    // ─── Mail 发放 ────────────────────────────────────────────

    private boolean isMailAvailable() {
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        return mail != null;
    }

    private void dispatchViaMail(@NotNull Player player, @NotNull SeasonRewardDefinition reward,
                                 @NotNull String description) {
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail == null) {
            // Mail 不可用：MAIL 类型降级为直接发放（物品走 ItemRewardDispatcher）
            dispatchDirect(player, reward);
            return;
        }
        switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case CURRENCY -> {
                if (reward.currencyId() != null) {
                    try {
                        MailSendResult result = mail.sendMail(MailSendRequest.builder(
                                player.getUniqueId(), description, description)
                            .attachment(MailAttachmentSpec.currency(reward.currencyId(), reward.currencyAmount()))
                            .senderName("钓鱼")
                            .sourceModule("fishing")
                            .build());
                        if (!result.success()) {
                            logger.warning("mail 货币发放失败，降级直接发放: " + result.message());
                            dispatchCurrency(player, reward);
                        }
                    } catch (Exception e) {
                        logger.warning("mail 货币发放失败，降级直接发放: " + e.getMessage());
                        dispatchCurrency(player, reward);
                    }
                }
            }
            case ITEM -> dispatchItem(player, reward);
            case COMMAND -> dispatchCommand(player, reward);
            case MAIL -> {
                xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
                    mailProvider, player.getName(), reward.mailPresets(), "fishing", logger);
            }
        }
    }

    private void dispatchViaMailOffline(@NotNull UUID uuid, @NotNull SeasonRewardDefinition reward,
                                        @NotNull String description) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            dispatchViaMail(player, reward, description);
            return;
        }
        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName == null) playerName = uuid.toString();

        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail == null) {
            savePending(uuid, reward, description);
            return;
        }
        switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case CURRENCY -> {
                if (reward.currencyId() != null) {
                    try {
                        MailSendResult result = mail.sendMail(MailSendRequest.builder(
                                uuid, description, description)
                            .attachment(MailAttachmentSpec.currency(reward.currencyId(), reward.currencyAmount()))
                            .senderName("钓鱼")
                            .sourceModule("fishing")
                            .build());
                        if (!result.success()) {
                            logger.warning("离线 mail 货币发放失败，降级 pending: " + result.message());
                            savePending(uuid, reward, description);
                        }
                    } catch (Exception e) {
                        logger.warning("离线 mail 货币发放失败，降级 pending: " + e.getMessage());
                        savePending(uuid, reward, description);
                    }
                }
            }
            case ITEM, COMMAND -> savePending(uuid, reward, description);
            case MAIL -> {
                if (reward.mailPresets() != null && !reward.mailPresets().isEmpty()) {
                    boolean allSuccess = xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
                        mailProvider, playerName, reward.mailPresets(), "fishing", logger);
                    if (!allSuccess) {
                        savePending(uuid, reward, description);
                    }
                } else {
                    savePending(uuid, reward, description);
                }
            }
        }
    }

    // ─── Pending 暂存（统一服务） ──────────────────────────────

    private void savePending(@NotNull UUID uuid, @NotNull SeasonRewardDefinition reward,
                             @NotNull String description) {
        SeasonRewardDefinition.Type type = SeasonRewardDefinition.Type.fromKey(reward.type());
        switch (type) {
            case ITEM -> {
                if (reward.item() != null) {
                    IconDefinition itemDef = reward.item();
                    String source = itemDef.source() != null && !itemDef.source().isBlank()
                        ? itemDef.source() : "minecraft";
                    String itemId = itemDef.sourceId() != null && !itemDef.sourceId().isBlank()
                        ? itemDef.sourceId() : (itemDef.material() != null ? itemDef.material() : "");
                    int amount = itemDef.amount();
                    String nbt = itemDef.nbtString() != null ? itemDef.nbtString() : "";
                    pendingRewardService.savePending(PendingRewardService.PendingReward.item(
                        uuid, "fishing", source, itemId, amount, nbt, description));
                }
            }
            case CURRENCY -> {
                if (reward.currencyId() != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.currency(
                        uuid, "fishing", reward.currencyId(), reward.currencyAmount(), description));
                }
            }
            case COMMAND -> {
                if (reward.commands() != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.command(
                        uuid, "fishing", reward.commands(), description));
                }
            }
            case MAIL -> {
                if (reward.mailPresets() != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.mailPreset(
                        uuid, "fishing", reward.mailPresets(), description));
                }
            }
        }
    }
}
