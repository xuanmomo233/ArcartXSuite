package xuanmo.arcartxsuite.fishing.reward;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.fishing.model.FishingPendingReward;
import xuanmo.arcartxsuite.fishing.service.FishingItemGenerator;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;

/**
 * 奖励发放器，处理等级奖励与结算奖励的实际发放。
 * <p>
 * 支持四种发放策略：
 * <ul>
 *   <li>{@code ONLINE_DIRECT} — 在线直接发放，离线暂存到 pending 表，上线补发</li>
 *   <li>{@code MAIL} — 统一通过 mail 模块发放（mail 不可用时降级为 ONLINE_DIRECT）</li>
 *   <li>{@code HYBRID} — 在线直接发放，离线走 mail（mail 不可用时降级为在线补发）</li>
 * </ul>
 */
public final class RewardDispatcher {

    public static final String MODE_ONLINE_DIRECT = "ONLINE_DIRECT";
    public static final String MODE_MAIL = "MAIL";
    public static final String MODE_HYBRID = "HYBRID";

    private final FishingRepository repository;
    private final FishingItemGenerator itemGenerator;
    private final Supplier<CurrencyBridgeAPI> currencyProvider;
    private final Supplier<MailDispatchable> mailProvider;
    private final Logger logger;
    private final String seasonId;
    private final String dispatchMode;

    public RewardDispatcher(@NotNull FishingRepository repository,
                            @NotNull FishingItemGenerator itemGenerator,
                            @Nullable Supplier<CurrencyBridgeAPI> currencyProvider,
                            @Nullable Supplier<MailDispatchable> mailProvider,
                            @NotNull Logger logger,
                            @NotNull String seasonId,
                            @NotNull String dispatchMode) {
        this.repository = repository;
        this.itemGenerator = itemGenerator;
        this.currencyProvider = currencyProvider;
        this.mailProvider = mailProvider;
        this.logger = logger;
        this.seasonId = seasonId;
        this.dispatchMode = dispatchMode;
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
            // 在线：根据模式决定直接发还是走 mail
            if (MODE_MAIL.equals(dispatchMode) && isMailAvailable()) {
                dispatchViaMail(player, reward, description);
            } else {
                dispatchDirect(player, reward);
            }
        } else {
            // 离线：根据模式决定 mail 还是 pending
            if ((MODE_MAIL.equals(dispatchMode) || MODE_HYBRID.equals(dispatchMode)) && isMailAvailable()) {
                dispatchViaMailOffline(uuid, reward, description);
            } else {
                savePending(uuid, reward, description);
            }
        }
    }

    /**
     * 发放玩家所有待发放奖励（上线时调用）。
     *
     * @param player 上线玩家
     */
    public void dispatchPending(@NotNull Player player) {
        List<FishingPendingReward> pending = repository.loadPendingRewards(player.getUniqueId());
        for (FishingPendingReward pr : pending) {
            try {
                dispatchPendingItem(player, pr);
                repository.deletePendingReward(pr.id());
            } catch (Exception e) {
                logger.warning("补发奖励失败 [" + pr.description() + "]: " + e.getMessage());
            }
        }
    }

    // ─── 在线直接发放 ────────────────────────────────────────

    private void dispatchDirect(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case CURRENCY -> dispatchCurrency(player, reward);
            case ITEM -> dispatchItem(player, reward);
            case COMMAND -> dispatchCommand(player, reward);
            case MAIL -> dispatchViaMail(player, reward, reward.iconName());
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
        if (reward.itemId() == null) return;
        String nbt = reward.itemNbt() != null ? reward.itemNbt() : "";
        xuanmo.arcartxsuite.fishing.model.FishingItemRef ref;
        if (!nbt.isBlank()) {
            // 带 NBT 的物品：通过 json 字段传递 SNBT
            ref = new xuanmo.arcartxsuite.fishing.model.FishingItemRef(
                reward.itemSource() != null ? reward.itemSource() : "minecraft",
                reward.itemId(), "", "", nbt, "", "", reward.itemAmount());
        } else {
            ref = xuanmo.arcartxsuite.fishing.model.FishingItemRef.fromMaterial(
                reward.itemId(), reward.itemAmount());
            if (reward.itemSource() != null && !reward.itemSource().equals("minecraft")) {
                ref = new xuanmo.arcartxsuite.fishing.model.FishingItemRef(
                    reward.itemSource(), reward.itemId(), "", "", "", "", "", reward.itemAmount());
            }
        }
        ItemStack item = itemGenerator.generate(ref, player);
        if (item != null) {
            player.getInventory().addItem(item).forEach((idx, leftover) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private void dispatchCommand(@NotNull Player player, @NotNull SeasonRewardDefinition reward) {
        if (reward.commands() == null) return;
        for (String cmd : reward.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("{player}", player.getName()));
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
            dispatchDirect(player, reward);
            return;
        }
        switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case CURRENCY -> {
                if (reward.currencyId() != null) {
                    try {
                        mail.sendCurrencyMail(player.getUniqueId(), description, description,
                            reward.currencyId(), reward.currencyAmount(), "Fishing");
                    } catch (Exception e) {
                        logger.warning("mail 货币发放失败，降级直接发放: " + e.getMessage());
                        dispatchCurrency(player, reward);
                    }
                }
            }
            case ITEM -> {
                if (reward.mailPresetId() != null && !reward.mailPresetId().isBlank()) {
                    try {
                        mail.dispatchPreset(reward.mailPresetId(), player.getName(), "Fishing");
                    } catch (Exception e) {
                        logger.warning("mail 预设发放失败，降级直接发放: " + e.getMessage());
                        dispatchItem(player, reward);
                    }
                } else {
                    // 无预设 ID，降级直接发放
                    dispatchItem(player, reward);
                }
            }
            case COMMAND -> dispatchCommand(player, reward);
            case MAIL -> {
                if (reward.mailPresetId() != null && !reward.mailPresetId().isBlank()) {
                    try {
                        mail.dispatchPreset(reward.mailPresetId(), player.getName(), "Fishing");
                    } catch (Exception e) {
                        logger.warning("mail 预设发放失败: " + e.getMessage());
                    }
                }
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
                        mail.sendCurrencyMail(uuid, description, description,
                            reward.currencyId(), reward.currencyAmount(), "Fishing");
                    } catch (Exception e) {
                        logger.warning("离线 mail 货币发放失败，降级 pending: " + e.getMessage());
                        savePending(uuid, reward, description);
                    }
                }
            }
            case ITEM -> {
                if (reward.mailPresetId() != null && !reward.mailPresetId().isBlank()) {
                    try {
                        mail.dispatchPreset(reward.mailPresetId(), playerName, "Fishing");
                    } catch (Exception e) {
                        logger.warning("离线 mail 预设发放失败，降级 pending: " + e.getMessage());
                        savePending(uuid, reward, description);
                    }
                } else {
                    savePending(uuid, reward, description);
                }
            }
            case COMMAND, MAIL -> savePending(uuid, reward, description);
        }
    }

    // ─── Pending 补发 ─────────────────────────────────────────

    private void savePending(@NotNull UUID uuid, @NotNull SeasonRewardDefinition reward,
                             @NotNull String description) {
        String rewardType = reward.type().toUpperCase();
        FishingPendingReward pending = new FishingPendingReward(
            0, uuid, seasonId, rewardType,
            reward.currencyId(), reward.currencyAmount(),
            reward.itemId() != null ? reward.itemId() : null,
            reward.commands() != null ? String.join(";", reward.commands()) : null,
            description, System.currentTimeMillis()
        );
        repository.savePendingReward(pending);
    }

    private void dispatchPendingItem(@NotNull Player player, @NotNull FishingPendingReward pr) {
        switch (pr.rewardType().toUpperCase()) {
            case "CURRENCY" -> {
                if (pr.currencyId() != null && !pr.currencyId().isBlank()) {
                    CurrencyBridgeAPI api = currencyProvider != null ? currencyProvider.get() : null;
                    if (api != null) {
                        var bridge = api.bridge(pr.currencyId());
                        if (bridge != null && bridge.available()) {
                            bridge.deposit(player, BigDecimal.valueOf(pr.amount()));
                        }
                    }
                }
            }
            case "ITEM" -> {
                if (pr.itemData() != null && !pr.itemData().isBlank()) {
                    ItemStack item = itemGenerator.generate(
                        xuanmo.arcartxsuite.fishing.model.FishingItemRef.fromMaterial(pr.itemData(), 1),
                        player);
                    if (item != null) {
                        player.getInventory().addItem(item).forEach((idx, leftover) ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    }
                }
            }
            case "COMMAND" -> {
                if (pr.command() != null && !pr.command().isBlank()) {
                    for (String cmd : pr.command().split(";")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            cmd.replace("{player}", player.getName()));
                    }
                }
            }
        }
    }
}
