package xuanmo.arcartxsuite.battlepass.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.battlepass.model.BattlePassItemDefinition;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;

/**
 * 战令奖励发放器，负责根据奖励类型执行实际的奖励发放逻辑。
 * <p>
 * 支持三种奖励类型：
 * <ul>
 *   <li>{@code command} — 以控制台身份执行命令列表，支持 {@code {player}} 占位符</li>
 *   <li>{@code item} — 通过 {@link ItemSourceRegistry} 生成物品并直接放入玩家背包</li>
 *   <li>{@code currency} — 通过 {@link CurrencyBridgeAPI} 向玩家发放货币</li>
 * </ul>
 * 命令执行和物品发放在主线程同步调度。
 */
public final class BattlePassRewardDispatcher {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final @Nullable ItemSourceRegistry itemSourceRegistry;
    private final @Nullable CurrencyBridgeAPI currencyManager;

    /**
     * 构造奖励发放器。
     *
     * @param plugin             插件实例
     * @param logger             日志记录器
     * @param itemSourceRegistry 物品来源注册表，可为 null（item 类型奖励将无法发放）
     * @param currencyManager    货币管理器，可为 null（currency 类型奖励将无法发放）
     */
    public BattlePassRewardDispatcher(JavaPlugin plugin, Logger logger,
                                       @Nullable ItemSourceRegistry itemSourceRegistry,
                                       @Nullable CurrencyBridgeAPI currencyManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.itemSourceRegistry = itemSourceRegistry;
        this.currencyManager = currencyManager;
    }

    /**
     * 向在线玩家发放指定奖励。
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
            default -> logger.warning("未知奖励类型 [" + type + "]: " + reward.rewardId());
        }
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
        ItemStack item = generateItem(itemDef);
        if (item == null) {
            logger.warning("物品奖励生成失败: " + reward.rewardId()
                + " source=" + itemDef.source() + " id=" + itemDef.id());
            return;
        }
        AxsScheduler.runTask(plugin, () -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        });
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

    @Nullable
    private ItemStack generateItem(BattlePassItemDefinition def) {
        if (itemSourceRegistry == null && !"minecraft".equalsIgnoreCase(def.source())) {
            logger.warning("物品来源注册表不可用，无法生成外部物品: " + def.source() + ":" + def.id());
            return null;
        }
        try {
            String source = def.source().toLowerCase(java.util.Locale.ROOT);
            ItemStack item = switch (source) {
                case "mythic", "mythicmobs" -> itemSourceRegistry == null ? null
                    : itemSourceRegistry.generateMythicItem(def.id(), def.amount());
                case "neige", "neigeitems" -> itemSourceRegistry == null ? null
                    : itemSourceRegistry.generateNeigeItem(def.id(), def.amount());
                case "overture" -> itemSourceRegistry == null ? null
                    : itemSourceRegistry.generateOvertureItem(def.id(), null, def.amount());
                case "mmoitems", "mmo" -> {
                    if (itemSourceRegistry == null) yield null;
                    String[] parts = def.id().split(";", 2);
                    yield parts.length == 2
                        ? itemSourceRegistry.generateMmoItem(parts[0], parts[1], def.amount()) : null;
                }
                default -> {
                    Material material = Material.matchMaterial(def.id(), true);
                    ItemStack base = material == null ? null : new ItemStack(material, def.amount());
                    yield applyItemNbt(base, def.nbt());
                }
            };
            return item;
        } catch (Exception e) {
            logger.warning("物品生成异常: " + def.source() + ":" + def.id() + " | " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static ItemStack applyItemNbt(@Nullable ItemStack item, @Nullable String nbt) {
        if (item == null || nbt == null || nbt.isBlank()) return item;
        try {
            return Bukkit.getUnsafe().modifyItemStack(item.clone(), nbt);
        } catch (Exception ignored) {
            return item;
        }
    }
}
