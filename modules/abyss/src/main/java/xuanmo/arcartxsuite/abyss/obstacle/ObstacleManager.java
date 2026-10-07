package xuanmo.arcartxsuite.abyss.obstacle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.ObstacleConfig;
import xuanmo.arcartxsuite.abyss.template.ObstacleConfig.Obstacle;

/**
 * 障碍物管理器：按配置动态 set/remove 方块，支持玩家位置触发。
 * <p>
 * 方块解析格式："x,y,z,material"。触发判定由监听器每 tick/每半秒
 * 调用 {@link #tickProximity} 做玩家-障碍块距离检测（避免每移动事件全扫）。
 */
public final class ObstacleManager {

    /** 已创建的障碍运行态：实例id → 障碍名 → 原始方块快照（恢复用）。 */
    private final Map<UUID, Map<String, ObstacleRuntime>> active = new ConcurrentHashMap<>();

    public static final class ObstacleRuntime {
        final Obstacle config;
        final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
        final Map<UUID, Long> triggerCooldowns = new ConcurrentHashMap<>();
        boolean onceDone;
        ObstacleRuntime(Obstacle c) { this.config = c; }
    }

    /** 回调注入：跑脚本（obstacle 触发/on_create/on_delete）。 */
    private BiConsumer<AbyssInstance, TriggerEvent> scriptCallback;
    private Function<AbyssInstance, Function<String, Location>> locParser;

    public record TriggerEvent(String obstacleName, String kind,
                               java.util.List<String> script, @Nullable Player trigger) { }

    private final Logger logger;

    public ObstacleManager(Logger logger) {
        this.logger = logger;
    }

    public void setCallbacks(BiConsumer<AbyssInstance, TriggerEvent> scriptCallback,
                             Function<AbyssInstance, Function<String, Location>> locParser) {
        this.scriptCallback = scriptCallback;
        this.locParser = locParser;
    }

    /** 副本 start：创建全部 auto_create 障碍。 */
    public void initInstance(AbyssInstance instance) {
        for (Obstacle o : instance.getTemplate().obstacles().obstacles().values()) {
            if (o.autoCreate()) {
                create(instance, o.name());
            }
        }
    }

    public boolean create(AbyssInstance instance, String name) {
        Obstacle o = instance.getTemplate().obstacles().obstacles().get(name);
        if (o == null || instance.getWorld() == null) return false;
        Map<String, ObstacleRuntime> map =
            active.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentHashMap<>());
        ObstacleRuntime rt = map.computeIfAbsent(name, k -> new ObstacleRuntime(o));
        Function<String, Location> parse = locParser != null ? locParser.apply(instance) : null;
        if (parse == null) return false;
        for (String b : o.blocks()) {
            String[] v = b.split(",");
            if (v.length < 4) continue;
            Location loc = parse.apply(v[0] + "," + v[1] + "," + v[2]);
            Material mat = Material.matchMaterial(v[3].trim().toUpperCase());
            if (loc == null || mat == null) continue;
            Block block = loc.getBlock();
            rt.originalBlocks.putIfAbsent(block.getLocation(), block.getType());
            block.setType(mat, false);
        }
        fire(instance, new TriggerEvent(name, "on_create", o.onCreate(), null));
        return true;
    }

    public boolean delete(AbyssInstance instance, String name) {
        Map<String, ObstacleRuntime> map = active.get(instance.getInstanceId());
        if (map == null) return false;
        ObstacleRuntime rt = map.remove(name);
        if (rt == null) return false;
        for (Map.Entry<Location, Material> e : rt.originalBlocks.entrySet()) {
            e.getKey().getBlock().setType(e.getValue(), false);
        }
        fire(instance, new TriggerEvent(name, "on_delete", rt.config.onDelete(), null));
        return true;
    }

    public boolean exists(AbyssInstance instance, String name) {
        Map<String, ObstacleRuntime> map = active.get(instance.getInstanceId());
        return map != null && map.containsKey(name);
    }

    /**
     * 周期触发检测（监听器每 ~10tick 调一次）：
     * 对每个 active 障碍，判定玩家与障碍块集合的位置关系并触发脚本。
     */
    public void tickProximity(AbyssInstance instance) {
        Map<String, ObstacleRuntime> map = active.get(instance.getInstanceId());
        if (map == null || map.isEmpty() || instance.getWorld() == null) return;
        Function<String, Location> parse = locParser != null ? locParser.apply(instance) : null;
        if (parse == null) return;
        for (ObstacleRuntime rt : map.values()) {
            Obstacle o = rt.config;
            if (o.triggerMode() == ObstacleConfig.TriggerMode.NONE) continue;
            for (Player p : instance.getPlayers()) {
                boolean inside = isInside(p, o, parse);
                long now = System.currentTimeMillis();
                boolean shouldFire = switch (o.triggerMode()) {
                    case ENTER -> inside && !rt.triggerCooldowns.containsKey(p.getUniqueId());
                    case LEAVE -> !inside && rt.triggerCooldowns.containsKey(p.getUniqueId())
                        && rt.triggerCooldowns.get(p.getUniqueId()) == -1L;
                    case PERSIST -> inside;
                    case NONE -> false;
                };
                if (o.triggerOnce() && rt.onceDone) continue;
                if (o.triggerMode() == ObstacleConfig.TriggerMode.ENTER) {
                    if (inside) {
                        Long last = rt.triggerCooldowns.get(p.getUniqueId());
                        if (last == null || last == -1L
                            || now - last >= o.triggerCooldown() * 1000L) {
                            rt.triggerCooldowns.put(p.getUniqueId(), now);
                            shouldFire = true;
                        } else {
                            shouldFire = false;
                        }
                    } else if (rt.triggerCooldowns.containsKey(p.getUniqueId())) {
                        rt.triggerCooldowns.put(p.getUniqueId(), -1L);
                        shouldFire = false;
                    }
                } else if (o.triggerMode() == ObstacleConfig.TriggerMode.PERSIST) {
                    Long last = rt.triggerCooldowns.get(p.getUniqueId());
                    if (last == null || now - last >= Math.max(1, o.triggerCooldown()) * 1000L) {
                        rt.triggerCooldowns.put(p.getUniqueId(), now);
                        shouldFire = inside;
                    } else {
                        shouldFire = false;
                    }
                }
                if (shouldFire) {
                    rt.onceDone = true;
                    fire(instance, new TriggerEvent(o.name(), "trigger",
                        o.triggerScript(), p));
                }
            }
        }
    }

    private boolean isInside(Player p, Obstacle o,
                             Function<String, Location> parse) {
        Location pl = p.getLocation();
        // 玩家中心/脚下与任一障碍块距离 < 1.6 视为"在障碍内"
        for (String b : o.blocks()) {
            String[] v = b.split(",");
            if (v.length < 3) continue;
            Location loc = parse.apply(v[0] + "," + v[1] + "," + v[2]);
            if (loc != null && loc.getWorld() == pl.getWorld()
                && loc.distanceSquared(pl) < 2.56) {
                return true;
            }
        }
        return false;
    }

    private void fire(AbyssInstance instance, TriggerEvent e) {
        if (scriptCallback != null && !e.script().isEmpty()) {
            scriptCallback.accept(instance, e);
        }
    }

    public void cleanup(AbyssInstance instance) {
        Map<String, ObstacleRuntime> map = active.remove(instance.getInstanceId());
        if (map == null) return;
        // 恢复原始方块（世界即将删除，其实不必恢复；但脚本/逻辑层依赖语义正确性）
        for (ObstacleRuntime rt : map.values()) {
            for (Map.Entry<Location, Material> e : rt.originalBlocks.entrySet()) {
                try { e.getKey().getBlock().setType(e.getValue(), false); } catch (Throwable ignored) { }
            }
        }
    }
}
