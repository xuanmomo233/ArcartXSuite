package xuanmo.arcartxsuite.abyss.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.RewardConfig.ChestReward;
import xuanmo.arcartxsuite.abyss.template.RewardConfig.ChestOption;
import xuanmo.arcartxsuite.abyss.template.RewardConfig.RewardEntry;
import xuanmo.arcartxsuite.abyss.template.RewardConfig.RewardSet;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;

/**
 * 奖励解析器：命名奖励集 + 随机权重 + 宝箱选项 → 统一发放。
 * <p>
 * 物品经 {@link ItemSourceRegistry}（minecraft/mythic/neige/overture/mmoitems 统一生成），
 * 发放经 {@link ItemRewardDispatcher}（背包→仓库→邮件→Pending→掉落链），
 * 货币经 {@link CurrencyBridgeAPI}，离线奖励经 {@link PendingRewardService}。
 */
public final class RewardResolver {

    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemRewardDispatcher dispatcher;
    private final PendingRewardService pendingRewardService;
    private final CurrencyBridgeAPI currencyManager;
    private final Logger logger;

    public RewardResolver(ItemSourceRegistry itemSourceRegistry,
                          ItemRewardDispatcher dispatcher,
                          PendingRewardService pendingRewardService,
                          CurrencyBridgeAPI currencyManager,
                          Logger logger) {
        this.itemSourceRegistry = itemSourceRegistry;
        this.dispatcher = dispatcher;
        this.pendingRewardService = pendingRewardService;
        this.currencyManager = currencyManager;
        this.logger = logger;
    }

    /** 按名字发放奖励集（支持 random 型递归）。 */
    public void grant(Player player, String rewardName, AbyssInstance instance) {
        RewardSet set = instance.getTemplate().rewards().rewards().get(rewardName);
        if (set == null) {
            logger.warning("[abyss] 奖励集不存在: " + rewardName);
            return;
        }
        grantSet(player, set, instance, 0);
    }

    private void grantSet(Player player, RewardSet set, AbyssInstance instance, int depth) {
        if (depth > 8) return; // 防循环 random 引用
        if (set.random()) {
            for (int i = 0; i < Math.max(1, set.picks()); i++) {
                String picked = pickWeighted(set.pool());
                if (picked != null) {
                    RewardSet sub = instance.getTemplate().rewards().rewards().get(picked);
                    if (sub != null) grantSet(player, sub, instance, depth + 1);
                }
            }
            return;
        }
        // 物品
        List<ItemStack> items = new ArrayList<>();
        for (String def : set.items()) {
            ItemStack it = buildItem(def, player);
            if (it != null) items.add(it);
        }
        for (String def : set.mythicItems()) {
            ItemStack it = buildItem("mythic:" + def, player);
            if (it != null) items.add(it);
        }
        for (String def : set.neigeItems()) {
            ItemStack it = buildItem("neige:" + def, player);
            if (it != null) items.add(it);
        }
        for (String def : set.overtureItems()) {
            ItemStack it = buildItem("overture:" + def, player);
            if (it != null) items.add(it);
        }
        for (String def : set.mmoItems()) {
            ItemStack it = buildItem("mmoitems:" + def, player);
            if (it != null) items.add(it);
        }
        if (!items.isEmpty()) {
            dispatcher.dispatchItems(player, items,
                ItemRewardDispatcher.DispatchOptions.builder("abyss")
                    .sourceDetail("reward:" + set.name())
                    .build());
        }
        // 货币
        if (set.money() != 0) {
            giveMoney(player, set.money(), set.currency());
        }
        // 经验
        if (set.exp() != 0) {
            player.giveExp(set.exp());
        }
        // 命令（{player} 占位）
        for (String cmd : set.commands()) {
            String c = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
    }

    /** 货币发放（CurrencyBridge.deposit）。 */
    public boolean giveMoney(Player player, double amount, String currencyId) {
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) {
            logger.warning("[abyss] 货币不可用: " + currencyId);
            return false;
        }
        return bridge.deposit(player, BigDecimal.valueOf(amount)).success();
    }

    /** 货币扣除（CurrencyBridge.withdraw）。 */
    public boolean takeMoney(Player player, double amount, String currencyId) {
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return false;
        return bridge.withdraw(player, BigDecimal.valueOf(amount)).success();
    }

    /** 货币余额查询。 */
    public double balance(Player player, String currencyId) {
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return 0;
        return bridge.balance(player).doubleValue();
    }

    /**
     * 按"物品定义串"构建 ItemStack：
     * material:X:n / item:X:n / minecraft:X[:n] / mythic:id:n / neige:id:n /
     * overture:id:n / mmo:TYPE:ID:n（或 mmoitems:TYPE;ID:n）/ 裸 MATERIAL[:n]
     */
    public @Nullable ItemStack buildItem(String def, @Nullable Player player) {
        if (def == null || def.isBlank()) return null;
        String[] v = def.trim().split(":");
        String source = v[0].toLowerCase();
        try {
            return switch (source) {
                case "material", "item", "minecraft" -> {
                    Material m = Material.matchMaterial(v[1].toUpperCase());
                    if (m == null) yield null;
                    int amount = v.length > 2 ? Integer.parseInt(v[2]) : 1;
                    yield new ItemStack(m, amount);
                }
                case "mythic" -> itemSourceRegistry.generateItem("mythic", v[1],
                    v.length > 2 ? Integer.parseInt(v[2]) : 1, null, player);
                case "neige" -> itemSourceRegistry.generateItem("neige", v[1],
                    v.length > 2 ? Integer.parseInt(v[2]) : 1, null, player);
                case "overture" -> itemSourceRegistry.generateItem("overture", v[1],
                    v.length > 2 ? Integer.parseInt(v[2]) : 1, null, player);
                case "mmo", "mmoitems" -> {
                    // mmo:TYPE:ID:n → "TYPE;ID"
                    String id = v.length > 3 ? v[1] + ";" + v[2]
                        : v.length > 2 ? v[1] + ";" + v[2] : v[1];
                    int amount = v.length > 3 ? Integer.parseInt(v[3]) : 1;
                    yield itemSourceRegistry.generateItem("mmoitems", id, amount, null, player);
                }
                default -> {
                    Material m = Material.matchMaterial(v[0].toUpperCase());
                    if (m == null) yield null;
                    yield new ItemStack(m, v.length > 1 ? Integer.parseInt(v[1]) : 1);
                }
            };
        } catch (Throwable t) {
            logger.warning("[abyss] 物品构建失败 " + def + ": " + t.getMessage());
            return null;
        }
    }

    /** 判断背包物品是否匹配定义串（交互点 required_item / take_item 用）。 */
    public boolean matches(String def, ItemStack hand) {
        if (hand == null || def == null) return false;
        String[] v = def.trim().split(":");
        String source = v[0].toLowerCase();
        try {
            return switch (source) {
                case "material", "item", "minecraft" -> {
                    Material m = Material.matchMaterial(v[1].toUpperCase());
                    yield m != null && hand.getType() == m;
                }
                case "mythic" -> v[1].equalsIgnoreCase(itemSourceRegistry.mythicItemId(hand));
                case "neige" -> v[1].equalsIgnoreCase(itemSourceRegistry.neigeItemId(hand));
                case "overture" -> v[1].equalsIgnoreCase(itemSourceRegistry.overtureItemId(hand));
                case "mmo", "mmoitems" -> {
                    String id = v.length > 2 ? v[1] + ";" + v[2] : v[1];
                    yield id.equalsIgnoreCase(itemSourceRegistry.mmoItemId(hand));
                }
                default -> {
                    Material m = Material.matchMaterial(v[0].toUpperCase());
                    yield m != null && hand.getType() == m;
                }
            };
        } catch (Throwable t) {
            return false;
        }
    }

    /** 从背包移除匹配物品（take_item）。 */
    public boolean takeItem(Player player, String def) {
        String[] v = def.trim().split(":");
        int need = 1;
        if (v.length >= 2 && v[v.length - 1].matches("\\d+")) {
            try { need = Integer.parseInt(v[v.length - 1]); } catch (NumberFormatException ignored) { }
            def = def.substring(0, def.lastIndexOf(':'));
        }
        int remaining = need;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !matches(def, it)) continue;
            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return remaining <= 0;
    }

    /** 加权随机抽取奖励 id。 */
    private @Nullable String pickWeighted(List<RewardEntry> pool) {
        if (pool == null || pool.isEmpty()) return null;
        int total = pool.stream().mapToInt(RewardEntry::weight).sum();
        if (total <= 0) return pool.get(0).rewardId();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (RewardEntry e : pool) {
            roll -= e.weight();
            if (roll < 0) return e.rewardId();
        }
        return pool.get(0).rewardId();
    }

    /** 宝箱加权抽取 N 个物品（open_chest_ui 用）。 */
    public List<ItemStack> rollChestOptions(ChestReward chest, int count) {
        List<ItemStack> out = new ArrayList<>();
        if (chest.options().isEmpty()) return out;
        int total = chest.options().stream().mapToInt(ChestOption::weight).sum();
        for (int i = 0; i < count; i++) {
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            ChestOption picked = chest.options().get(0);
            for (ChestOption o : chest.options()) {
                roll -= o.weight();
                if (roll < 0) { picked = o; break; }
            }
            ItemStack it = buildItem(picked.item(), null);
            if (it != null) out.add(it);
        }
        return out;
    }

    /** 给离线玩家暂存物品奖励（宿主上线补发：走 ItemSourceRegistry 生成）。 */
    public void grantOfflineItem(java.util.UUID playerUuid, String source, String itemId,
                                 int amount, String description) {
        try {
            pendingRewardService.savePending(PendingReward.item(
                playerUuid, "abyss", source, itemId, amount, null, description));
        } catch (Throwable t) {
            logger.warning("[abyss] 离线物品奖励暂存失败 " + playerUuid + ": " + t.getMessage());
        }
    }

    /** 给离线玩家暂存货币奖励。 */
    public void grantOfflineCurrency(java.util.UUID playerUuid, String currencyId,
                                     double amount, String description) {
        try {
            pendingRewardService.savePending(PendingReward.currency(
                playerUuid, "abyss", currencyId, amount, description));
        } catch (Throwable t) {
            logger.warning("[abyss] 离线货币奖励暂存失败 " + playerUuid + ": " + t.getMessage());
        }
    }

    /** 给离线玩家暂存命令奖励。 */
    public void grantOfflineCommand(java.util.UUID playerUuid, List<String> commands,
                                    String description) {
        try {
            pendingRewardService.savePending(PendingReward.command(
                playerUuid, "abyss", commands, description));
        } catch (Throwable t) {
            logger.warning("[abyss] 离线命令奖励暂存失败 " + playerUuid + ": " + t.getMessage());
        }
    }
}
