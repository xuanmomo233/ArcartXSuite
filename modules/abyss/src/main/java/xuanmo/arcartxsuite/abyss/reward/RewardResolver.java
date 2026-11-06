package xuanmo.arcartxsuite.abyss.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import xuanmo.arcartxsuite.abyss.template.RewardConfig;
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
    private final xuanmo.arcartxsuite.api.item.ItemMatcherAPI matcher;

    public RewardResolver(ItemSourceRegistry itemSourceRegistry,
                          ItemRewardDispatcher dispatcher,
                          PendingRewardService pendingRewardService,
                          CurrencyBridgeAPI currencyManager,
                          Logger logger) {
        this(itemSourceRegistry,dispatcher,pendingRewardService,currencyManager,logger,null);
    }

    public RewardResolver(ItemSourceRegistry itemSourceRegistry,ItemRewardDispatcher dispatcher,
            PendingRewardService pendingRewardService,CurrencyBridgeAPI currencyManager,Logger logger,
            xuanmo.arcartxsuite.api.item.ItemMatcherAPI matcher) {
        this.itemSourceRegistry = itemSourceRegistry;
        this.dispatcher = dispatcher;
        this.pendingRewardService = pendingRewardService;
        this.currencyManager = currencyManager;
        this.logger = logger;
        this.matcher=matcher;
    }

    /** 按名字发放奖励集（支持 random 型递归）。 */
    public void grant(Player player, String rewardName, AbyssInstance instance) {
        RewardSet set = instance.getTemplate().rewards().rewards().get(rewardName);
        if (set == null) {
            logger.warning("[abyss] 奖励集不存在: " + rewardName);
            return;
        }
        List<PendingReward> batch = new ArrayList<>();
        collectSet(player, set, instance, 0, batch);
        if (!queue(player, "reward:" + instance.getInstanceId() + ":" + java.util.UUID.randomUUID(), batch))
            throw new IllegalStateException("奖励未确认保存，请保留结算记录并重试: " + rewardName);
    }

    public record GamePrize(String label, List<PendingReward> rewards) {
        public GamePrize { rewards = List.copyOf(rewards); }
    }

    public List<GamePrize> rollGame(Player player, AbyssInstance instance, RewardConfig.RewardGame game) {
        var sets = instance.getTemplate().rewards().rewards();
        var pool = new ArrayList<>(sets.get(game.pool()).pool());
        var deck = new ArrayList<GamePrize>();
        int totalEntries = 0;
        for (int slot = 0; slot < game.slots(); slot++) {
            String picked = pickWeighted(pool);
            if (picked == null) throw new IllegalArgumentException("奖励玩法池为空");
            var batch = new ArrayList<PendingReward>();
            collectSet(player, sets.get(picked), instance, 0, batch);
            totalEntries += batch.size();
            if (batch.isEmpty() || totalEntries > 1024) throw new IllegalArgumentException("奖励玩法内容为空或过大");
            deck.add(new GamePrize(picked, batch));
            if (!game.repeat()) pool.removeIf(entry -> entry.rewardId().equals(picked));
        }
        return List.copyOf(deck);
    }

    private void collectSet(Player player, RewardSet set, AbyssInstance instance, int depth, List<PendingReward> batch) {
        if(batch.size()>4096)throw new IllegalArgumentException("单次奖励条目超过4096");
        if (depth > 8) throw new IllegalArgumentException("奖励池循环或嵌套过深");
        if (set.random()) {
            for (int i = 0; i < Math.max(1, set.picks()); i++) {
                String picked = pickWeighted(set.pool());
                if (picked != null) {
                    RewardSet sub = instance.getTemplate().rewards().rewards().get(picked);
                    if (sub == null) throw new IllegalArgumentException("奖励引用不存在: " + picked);
                    collectSet(player, sub, instance, depth + 1, batch);
                }
            }
            return;
        }
        // 物品
        List<ItemStack> items = new ArrayList<>();
        for (String def : set.items()) {
            ItemStack it = buildItem(def, player);
            items.add(requireItem(it, def));
        }
        for (String def : set.mythicItems()) {
            ItemStack it = buildItem("mythic:" + def, player);
            items.add(requireItem(it, def));
        }
        for (String def : set.neigeItems()) {
            ItemStack it = buildItem("neige:" + def, player);
            items.add(requireItem(it, def));
        }
        for (String def : set.overtureItems()) {
            ItemStack it = buildItem("overture:" + def, player);
            items.add(requireItem(it, def));
        }
        for (String def : set.mmoItems()) {
            ItemStack it = buildItem("mmoitems:" + def, player);
            items.add(requireItem(it, def));
        }
        if (!items.isEmpty()) {
            for (ItemStack item : items) batch.add(serialized(player, item, "reward:" + set.name()));
        }
        // 货币
        if (set.money() != 0) {
            batch.add(PendingReward.currency(player.getUniqueId(), "abyss", set.currency(), set.money(), set.name()));
        }
        // 经验
        if (set.exp() != 0) {
            batch.add(PendingReward.command(player.getUniqueId(), "abyss",
                List.of("minecraft:experience add {player} " + set.exp() + " points"), set.name()));
        }
        // 命令（{player} 占位）
        for(String command:set.commands())batch.add(PendingReward.command(player.getUniqueId(),"abyss",List.of(command),set.name()));
    }

    private static ItemStack requireItem(ItemStack item, String definition) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1)
            throw new IllegalArgumentException("奖励物品无法生成: " + definition);
        return item;
    }

    private static PendingReward serialized(Player player, ItemStack item, String detail) {
        String bytes = java.util.Base64.getEncoder().encodeToString(xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(item));
        return PendingReward.itemSerialized(player.getUniqueId(), "abyss", bytes, detail);
    }

    public boolean queueItems(Player player, List<ItemStack> items, String requestKey) {
        List<PendingReward> batch = new ArrayList<>();
        for (ItemStack item : items) batch.add(serialized(player, requireItem(item, requestKey), requestKey));
        return queue(player, requestKey, batch);
    }

    private boolean queue(Player player, String requestKey, List<PendingReward> batch) {
        if (batch.isEmpty()) return true;
        boolean confirmed=false;
        try { confirmed=pendingRewardService!=null && pendingRewardService.savePendingBatchConfirmed(requestKey,batch); }
        catch(RuntimeException | LinkageError error){logger.warning("[abyss] 宿主奖励确认API不可用: "+error.getMessage());}
        if (!confirmed) {
            player.sendMessage("§c奖励暂存未完成，尚未确认领取，请稍后重试或联系管理员。");
            return false;
        }
        try { pendingRewardService.flushPlayer(player); }
        catch (RuntimeException error) { logger.warning("[abyss] 奖励已暂存，等待上线补发: " + error.getMessage()); }
        return true;
    }

    /** 货币发放（CurrencyBridge.deposit）。 */
    public boolean giveMoney(Player player, double amount, String currencyId) {
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("奖励金额必须为正数");
        return queue(player,"currency:"+java.util.UUID.randomUUID(),List.of(
            PendingReward.currency(player.getUniqueId(),"abyss",currencyId,amount,"script-currency")));
    }

    public boolean refundMoney(Player player, double amount, String currencyId, String requestKey) {
        try {
            var bridge=currencyManager.bridge(currencyId);
            if(bridge!=null && bridge.available() && bridge.deposit(player,BigDecimal.valueOf(amount)).success()) return true;
        } catch (RuntimeException error) { logger.warning("[abyss] 即时退款失败，转宿主待发: "+error.getMessage()); }
        return queue(player,requestKey,List.of(PendingReward.currency(player.getUniqueId(),"abyss",currencyId,amount,"entry-refund")));
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
        try {
            ItemDefinition item=ItemDefinition.parse(def);
            return itemSourceRegistry.generateItem(item.source(),item.id(),item.amount(),null,player);
        } catch(RuntimeException error) {
            logger.warning("[abyss] 物品构建失败 "+def+": "+error.getMessage());
            return null;
        }
    }

    /** 判断背包物品是否匹配定义串（交互点 required_item / take_item 用）。 */
    public boolean matches(String def, ItemStack hand) {
        if(hand==null || def==null)return false;
        try {
            ItemDefinition item=ItemDefinition.parse(def);
            if(item.source().equals("mmoitems"))
                return item.id().equalsIgnoreCase(itemSourceRegistry.mmoItemId(hand));
            if(matcher!=null) {
                String id=item.id().toLowerCase(java.util.Locale.ROOT);
                var material=item.source().equals("minecraft")?List.of(id):List.<String>of();
                var mythic=item.source().equals("mythic")?List.of(id):List.<String>of();
                var neige=item.source().equals("neige")?List.of(id):List.<String>of();
                var overture=item.source().equals("overture")?List.of(id):List.<String>of();
                return matcher.matches(new xuanmo.arcartxsuite.api.item.ItemMatcher(material,mythic,neige,overture,
                    List.of(),List.of(),List.of(),List.of(),Map.of(),List.of(),List.of()),hand);
            }
            return switch(item.source()) {
                case "minecraft" -> hand.getType()==Material.matchMaterial(item.id());
                case "mythic" -> item.id().equalsIgnoreCase(itemSourceRegistry.mythicItemId(hand));
                case "neige" -> item.id().equalsIgnoreCase(itemSourceRegistry.neigeItemId(hand));
                case "overture" -> item.id().equalsIgnoreCase(itemSourceRegistry.overtureItemId(hand));
                default -> false;
            };
        } catch(RuntimeException error) { return false; }
    }

    /** 从背包移除匹配物品（take_item）。 */
    public boolean takeItem(Player player, String def) {
        ItemDefinition item=ItemDefinition.parse(def);
        ItemStack[] copy=java.util.Arrays.stream(player.getInventory().getContents())
            .map(it->it==null?null:it.clone()).toArray(ItemStack[]::new);
        int remaining=item.amount();
        for(int i=0;i<copy.length && remaining>0;i++) {
            ItemStack stack=copy[i];
            if(stack==null || !matches(item.source()+":"+item.id(),stack))continue;
            int take=Math.min(remaining,stack.getAmount()); remaining-=take;
            stack.setAmount(stack.getAmount()-take); if(stack.getAmount()==0)copy[i]=null;
        }
        if(remaining>0)return false;
        player.getInventory().setContents(copy);
        return true;
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
