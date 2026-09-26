package xuanmo.arcartxsuite.abyss.interact;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.InteractConfig;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.Interact;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.InteractType;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;

/**
 * 交互点管理器：7 类触发的匹配与一次性语义。
 * <p>
 * 注册时全部加载；触发匹配由监听器调用 handle* 方法。
 */
public final class InteractManager {

    private final Map<UUID, Map<String, Interact>> active = new ConcurrentHashMap<>();
    private BiConsumer<AbyssInstance, FireEvent> fireCallback;
    private Function<AbyssInstance, Function<String, Location>> locParser;
    private final ScriptConditionEvaluator evaluator;
    private final Logger logger;

    public record FireEvent(String interactName, java.util.List<String> script,
                            @Nullable Player trigger) { }

    public InteractManager(ScriptConditionEvaluator evaluator, Logger logger) {
        this.evaluator = evaluator;
        this.logger = logger;
    }

    public void setCallbacks(BiConsumer<AbyssInstance, FireEvent> fireCallback,
                             Function<AbyssInstance, Function<String, Location>> locParser) {
        this.fireCallback = fireCallback;
        this.locParser = locParser;
    }

    /** 副本 start：注册全部交互点。 */
    public void initInstance(AbyssInstance instance) {
        active.put(instance.getInstanceId(),
            new ConcurrentHashMap<>(instance.getTemplate().interacts().interacts()));
    }

    /** 动态注册（脚本 create_interact）。 */
    public boolean create(AbyssInstance instance, String name) {
        Interact i = instance.getTemplate().interacts().interacts().get(name);
        if (i == null) return false;
        Map<String, Interact> map = active.computeIfAbsent(
            instance.getInstanceId(), k -> new ConcurrentHashMap<>());
        map.put(name, i);
        return true;
    }

    public boolean delete(AbyssInstance instance, String name) {
        Map<String, Interact> map = active.get(instance.getInstanceId());
        return map != null && map.remove(name) != null;
    }

    // ═══ 触发匹配 ═══

    /** 方块点击/破坏/放置类触发统一入口。 */
    public void onBlockTrigger(AbyssInstance instance, Player player, InteractType type,
                               @Nullable Block block, @Nullable ItemStack hand) {
        Map<String, Interact> map = active.get(instance.getInstanceId());
        if (map == null || block == null) return;
        for (Interact i : map.values()) {
            if (i.type() != type) continue;
            if (!matchBlock(instance, i, block)) continue;
            if (!matchItem(i, hand)) continue;
            tryFire(instance, i, player);
        }
    }

    /** 踩踏/进区域（由周期检测调用）。 */
    public void onRegionTrigger(AbyssInstance instance, Player player, InteractType type) {
        Map<String, Interact> map = active.get(instance.getInstanceId());
        if (map == null) return;
        for (Interact i : map.values()) {
            if (i.type() != type) continue;
            if (!matchRegion(instance, i, player.getLocation())) continue;
            tryFire(instance, i, player);
        }
    }

    /** 实体右键。 */
    public void onEntityTrigger(AbyssInstance instance, Player player, Entity entity) {
        Map<String, Interact> map = active.get(instance.getInstanceId());
        if (map == null || entity == null) return;
        String name = entity.getCustomName() != null ? entity.getCustomName() : entity.getName();
        for (Interact i : map.values()) {
            if (i.type() != InteractType.RIGHT_CLICK_ENTITY) continue;
            if (i.entityName() != null && !name.contains(i.entityName())
                && !entity.getUniqueId().toString().equals(i.entityName())) continue;
            tryFire(instance, i, player);
        }
    }

    private boolean matchBlock(AbyssInstance instance, Interact i, Block block) {
        if (i.location() != null) {
            Function<String, Location> parse = locParser.apply(instance);
            Location target = parse.apply(i.location());
            if (target == null || !target.getBlock().equals(block)) return false;
        }
        if (i.requiredBlock() != null) {
            Material req = Material.matchMaterial(i.requiredBlock().toUpperCase());
            if (req == null || block.getType() != req) return false;
        }
        if (!i.region().isEmpty()) {
            return matchRegion(instance, i, block.getLocation());
        }
        return true;
    }

    private boolean matchRegion(AbyssInstance instance, Interact i, Location loc) {
        if (i.region().size() < 2) return i.location() == null || matchPoint(instance, i, loc);
        Function<String, Location> parse = locParser.apply(instance);
        Location a = parse.apply(i.region().get(0));
        Location b = parse.apply(i.region().get(1));
        if (a == null || b == null || a.getWorld() != loc.getWorld()) return false;
        return loc.getX() >= Math.min(a.getX(), b.getX()) && loc.getX() <= Math.max(a.getX(), b.getX())
            && loc.getY() >= Math.min(a.getY(), b.getY()) && loc.getY() <= Math.max(a.getY(), b.getY())
            && loc.getZ() >= Math.min(a.getZ(), b.getZ()) && loc.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    private boolean matchPoint(AbyssInstance instance, Interact i, Location loc) {
        Function<String, Location> parse = locParser.apply(instance);
        Location target = parse.apply(i.location());
        return target != null && target.getWorld() == loc.getWorld()
            && target.distanceSquared(loc) < 2.56;
    }

    private boolean matchItem(Interact i, @Nullable ItemStack hand) {
        if (i.requiredItem() == null || i.requiredItem().isBlank()) return true;
        if (hand == null || hand.getType().isAir()) return false;
        String req = i.requiredItem();
        // required_item 形如 "material:X:1" 或裸 "MATERIAL"
        if (req.contains(":")) {
            String[] v = req.split(":");
            if (v[0].equalsIgnoreCase("material") || v[0].equalsIgnoreCase("item")) {
                Material m = Material.matchMaterial(v[1].toUpperCase());
                return m != null && hand.getType() == m;
            }
            // 非原版（mythic/neige 等）由 RewardResolver/物品桥比对——此处走 itemMatcher 由外层判定
            return matchExternalItem(req, hand);
        }
        Material m = Material.matchMaterial(req.toUpperCase());
        return m != null && hand.getType() == m;
    }

    private java.util.function.BiFunction<String, ItemStack, Boolean> externalMatcher;

    public void setExternalItemMatcher(java.util.function.BiFunction<String, ItemStack, Boolean> fn) {
        this.externalMatcher = fn;
    }

    private boolean matchExternalItem(String req, ItemStack hand) {
        return externalMatcher != null && externalMatcher.apply(req, hand);
    }

    private void tryFire(AbyssInstance instance, Interact i, Player player) {
        String globalKey = "interact:" + i.name();
        String playerKey = globalKey + ":" + player.getUniqueId();
        if (i.once()) {
            // once = 实例全局一次（QueDP 语义）
            if (instance.isTriggered(globalKey)) return;
        } else {
            // 非 once：每玩家 500ms 防抖（防同一事件多次匹配触发）
            long last = lastFire(player, i);
            if (System.currentTimeMillis() - last < 500) return;
        }
        // 条件评估
        if (!i.conditions().isEmpty()
            && evaluator.firstFailed(player, i.conditions()) != null) {
            if (i.failMessage() != null) {
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', i.failMessage()));
            }
            return;
        }
        if (i.once()) {
            instance.markTriggeredOnce(globalKey);
        }
        lastFires.put(playerKey, System.currentTimeMillis());
        if (fireCallback != null && !i.script().isEmpty()) {
            fireCallback.accept(instance, new FireEvent(i.name(), i.script(), player));
        }
    }

    private final Map<String, Long> lastFires = new ConcurrentHashMap<>();
    private long lastFire(Player p, Interact i) {
        return lastFires.getOrDefault(p.getUniqueId() + ":" + i.name(), 0L);
    }

    public void cleanup(AbyssInstance instance) {
        active.remove(instance.getInstanceId());
    }
}
