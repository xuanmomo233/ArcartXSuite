package xuanmo.arcartxsuite.abyss.mob;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterGroup;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/**
 * 怪物组管理器：生成/存活追踪/完成判定/依赖联动。
 * <p>
 * 存活追踪双保险：① 实体死亡事件（EntityDeathEvent / MM death 中继）即时移除
 * ② 周期消失扫描兜底（怪被刷掉/区块卸载时也能检测到）。
 */
public final class MonsterGroupManager {

    public enum GroupState { PENDING, ACTIVE, COMPLETED, REMOVED }

    /** 组运行态。 */
    public static final class GroupRuntime {
        final MonsterGroup group;
        volatile GroupState state = GroupState.PENDING;
        /** 休整/延迟 spawn 已排队（防多前置组重复排队）。 */
        volatile boolean spawnQueued;
        final Set<UUID> alive = ConcurrentHashMap.newKeySet();
        ScheduledTask delayedTask;
        GroupRuntime(MonsterGroup g) { this.group = g; }
    }

    private final Map<UUID, Map<String, GroupRuntime>> instanceGroups = new ConcurrentHashMap<>();
    private final Map<UUID, GroupRuntime> entityToGroup = new ConcurrentHashMap<>();
    private final SchedulerAPI scheduler;
    private final org.bukkit.plugin.Plugin plugin;
    private final Logger logger;

    /** 回调：组开始/组完成时通知外层跑脚本（避免循环依赖，用函数注入）。 */
    private GroupCallback callback;

    public interface GroupCallback {
        void onGroupStart(AbyssInstance instance, String groupName);
        void onGroupComplete(AbyssInstance instance, String groupName);
        void onMobDeath(AbyssInstance instance, UUID entityUuid, @Nullable Player killer);
        void runGroupScript(AbyssInstance instance, List<String> lines, String groupName,
                            @Nullable Player trigger);
        @Nullable Location parseLocation(AbyssInstance instance, String locStr);
        @Nullable MobProvider provider();
        void publishEvent(String topic, Map<String, Object> data);
    }

    public MonsterGroupManager(org.bukkit.plugin.Plugin plugin, SchedulerAPI scheduler,
                               Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setCallback(GroupCallback callback) { this.callback = callback; }

    /**
     * 组是否匹配当前作战方案：组声明了 {@code plans} 且实例 {@code plan} 变量不在其中则跳过。
     * plan 变量由进本流程写入（无方案机制的副本变量为 null → 声明了 plans 的组不刷）。
     */
    private boolean planAllowed(AbyssInstance instance, MonsterGroup g) {
        if (g.plans().isEmpty()) return true;
        Object plan = instance.getVariables().get("plan");
        return plan != null && g.plans().contains(String.valueOf(plan));
    }

    /** 初始化实例的组表（副本 start 时调用）。 */
    public void initInstance(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = new ConcurrentHashMap<>();
        for (MonsterGroup g : instance.getTemplate().monsters().groups().values()) {
            if (!planAllowed(instance, g)) continue;
            groups.put(g.name(), new GroupRuntime(g));
        }
        instanceGroups.put(instance.getInstanceId(), groups);

        // AUTO_START 立即刷；DELAYED 排延迟
        for (GroupRuntime rt : groups.values()) {
            switch (rt.group.spawnTiming()) {
                case AUTO_START -> spawnGroup(instance, rt.group.name(), null);
                case DELAYED -> {
                    rt.delayedTask = scheduler.scheduleOnceSeconds(plugin, () -> {
                        rt.delayedTask = null;
                        spawnGroup(instance, rt.group.name(), null);
                    }, rt.group.delaySeconds());
                    instance.registerTask(rt.delayedTask);
                }
                default -> { }
            }
        }
    }

    /**
     * 注册运行期物化组（floors 房间怪组等动态来源）。
     * <p>组名必须实例内唯一；房间组约定 {@code roomId:短名}。
     * AUTO_START 不会立即刷——由调用方（如房间锁门）显式 spawnGroup。</p>
     */
    public void registerRuntimeGroup(AbyssInstance instance, MonsterGroup group) {
        if (!planAllowed(instance, group)) return;
        Map<String, GroupRuntime> groups =
            instanceGroups.computeIfAbsent(instance.getInstanceId(),
                k -> new ConcurrentHashMap<>());
        groups.put(group.name(), new GroupRuntime(group));
    }

    /** 生成怪物组（MANUAL/TRIGGERED/脚本均可调）。 */
    public boolean spawnGroup(AbyssInstance instance, String groupName, @Nullable Player trigger) {
        if (instance.isFinished()) return false;
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return false;
        GroupRuntime rt = groups.get(groupName);
        if (rt == null || rt.state == GroupState.ACTIVE) return false;
        if (rt.state == GroupState.COMPLETED) return false;

        World world = instance.getWorld();
        MobProvider provider = callback != null ? callback.provider() : null;
        if (world == null || provider == null || callback == null) return false;

        rt.state = GroupState.ACTIVE;
        int spawned = 0;
        for (MonsterConfig.MonsterSpawn ms : rt.group.monsters()) {
            Location loc = callback.parseLocation(instance, ms.location());
            if (loc == null) continue;
            // 区块票据：防怪被区块卸载
            keepChunkLoaded(loc);
            for (int i = 0; i < ms.amount(); i++) {
                Entity e = provider.spawn(ms.mobId(), loc, ms.level());
                if (e != null) {
                    rt.alive.add(e.getUniqueId());
                    entityToGroup.put(e.getUniqueId(), rt);
                    spawned++;
                }
            }
        }
        logger.fine("[abyss] 组 " + groupName + " 生成 " + spawned + " 只怪 (实例 "
            + instance.getInstanceId() + ")");
        callback.onGroupStart(instance, groupName);
        callback.runGroupScript(instance, rt.group.onStart(), groupName, trigger);
        callback.publishEvent("axs.abyss.mob_group_start", Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "group", groupName));
        return true;
    }

    /** 实体死亡回调（由监听器/中继调用）。 */
    public void onEntityDeath(UUID entityUuid, AbyssInstance instance, @Nullable Player killer) {
        GroupRuntime rt = entityToGroup.remove(entityUuid);
        if (rt != null) {
            rt.alive.remove(entityUuid);
            if (callback != null) {
                callback.onMobDeath(instance, entityUuid, killer);
            }
            checkGroupComplete(instance, rt);
        }
    }

    /** 周期消失扫描兜底（监听器内调度，防怪被刷掉/区块卸载漏检）。 */
    public void sweepVanished(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return;
        for (GroupRuntime rt : groups.values()) {
            if (rt.state != GroupState.ACTIVE || rt.alive.isEmpty()) continue;
            boolean changed = false;
            for (UUID u : rt.alive) {
                Entity e = Bukkit.getEntity(u);
                if (e == null || e.isDead() || !e.isValid()) {
                    rt.alive.remove(u);
                    entityToGroup.remove(u);
                    changed = true;
                }
            }
            if (changed) checkGroupComplete(instance, rt);
        }
    }

    private void checkGroupComplete(AbyssInstance instance, GroupRuntime rt) {
        if (rt.state != GroupState.ACTIVE) return;
        if (instance.isFinished()) {
            rt.state = GroupState.COMPLETED;
            return;
        }
        if (rt.alive.size() <= rt.group.completeWhenAliveBelow()) {
            rt.state = GroupState.COMPLETED;
            fireGroupComplete(instance, rt);
        }
    }

    /**
     * 组完成统一出口：完成回调 + on-end 脚本 + 事件 + 依赖组触发。
     * ACTIVE（全清）与 PENDING（kill_group 跳过）组共用。
     */
    private void fireGroupComplete(AbyssInstance instance, GroupRuntime rt) {
        if (callback != null) {
            callback.onGroupComplete(instance, rt.group.name());
            callback.runGroupScript(instance, rt.group.onEnd(), rt.group.name(), null);
            callback.publishEvent("axs.abyss.mob_group_complete", Map.of(
                "dungeon", instance.getTemplate().id(),
                "instance", instance.getInstanceId().toString(),
                "group", rt.group.name()));
        }
        // 触发依赖组（休整/延迟生效 + 预告段）
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups != null) {
            for (GroupRuntime dep : groups.values()) {
                if (dep.state == GroupState.PENDING
                    && dep.group.spawnTiming() == MonsterConfig.SpawnTiming.TRIGGERED
                    && dep.group.triggerAfter().contains(rt.group.name())
                    && allDepsCompleted(groups, dep.group)) {
                    queueTriggeredSpawn(instance, dep);
                }
            }
        }
    }

    /** trigger-after 全前置已完成才算触发（任一先完成时提前排队会让波次越过休整）。 */
    private boolean allDepsCompleted(Map<String, GroupRuntime> groups, MonsterGroup g) {
        for (String depName : g.triggerAfter()) {
            GroupRuntime dep = groups.get(depName);
            if (dep == null || dep.state != GroupState.COMPLETED) return false;
        }
        return true;
    }

    /**
     * TRIGGERED 组被前置触发：delay-seconds 优先、rest-seconds 兜底。
     * 有休整期则先跑 on-prespawn 预告段（title/音效），再排一次性延迟 spawn；
     * spawnQueued 防 trigger-after 多前置组重复排队。
     */
    private void queueTriggeredSpawn(AbyssInstance instance, GroupRuntime dep) {
        int delay = dep.group.delaySeconds() > 0
            ? dep.group.delaySeconds() : Math.max(0, dep.group.restSeconds());
        if (delay <= 0) {
            spawnGroup(instance, dep.group.name(), null);
            return;
        }
        if (dep.spawnQueued) return;
        dep.spawnQueued = true;
        if (callback != null) {
            callback.runGroupScript(instance, dep.group.onPrespawn(),
                dep.group.name(), null);
        }
        dep.delayedTask = scheduler.scheduleOnceSeconds(plugin, () -> {
            dep.delayedTask = null;
            spawnGroup(instance, dep.group.name(), null);
        }, delay);
        instance.registerTask(dep.delayedTask);
    }

    /** 取消组的延迟任务（一次性延迟自取消/清理路径共用）。 */
    private void cancelDelayed(GroupRuntime rt) {
        if (rt.delayedTask != null) {
            try { rt.delayedTask.cancel(); } catch (Throwable ignored) { }
            rt.delayedTask = null;
        }
    }

    /**
     * 击杀整组并视为"清完"：触发完成联动（on-end/trigger-after/房间清判）。
     * <p>需要静默移除且不触发联动的场景用 {@link #removeGroup}。
     * 不触发联动会让依赖链/房间清判软锁（该组永不推进）。</p>
     */
    /** 组是否已清完（COMPLETED 含 kill_group 跳过）。供 stages/conditions 查询。 */
    public boolean isGroupComplete(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return false;
        GroupRuntime rt = groups.get(groupName);
        return rt != null && rt.state == GroupState.COMPLETED;
    }

    public void killGroup(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return;
        GroupRuntime rt = groups.get(groupName);
        if (rt == null) return;
        for (UUID u : rt.alive) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) {
                entityToGroup.remove(u);
                e.remove();
            }
        }
        rt.alive.clear();
        // ACTIVE 走正常完成链；PENDING（从未生成）也视为完成——随机波次等场景
        // 用 kill_group 显式跳过备选组，否则它永远 PENDING 卡住房间清判
        if (rt.state == GroupState.ACTIVE) {
            checkGroupComplete(instance, rt);
        } else if (rt.state == GroupState.PENDING) {
            rt.state = GroupState.COMPLETED;
            fireGroupComplete(instance, rt);
        }
    }

    /** 移除组（连同怪）且不触发完成。 */
    public void removeGroup(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return;
        GroupRuntime rt = groups.remove(groupName);
        if (rt == null) return;
        cancelDelayed(rt);
        for (UUID u : rt.alive) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
            entityToGroup.remove(u);
        }
        rt.state = GroupState.REMOVED;
    }

    /** 杀光实例全部怪（清理用，不触发联动）。 */
    public void killAll(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return;
        for (GroupRuntime rt : groups.values()) {
            for (UUID u : rt.alive) {
                Entity e = Bukkit.getEntity(u);
                if (e != null) e.remove();
                entityToGroup.remove(u);
            }
            rt.alive.clear();
        }
    }

    /** 实例全部组存活怪总数（HUD 显示用）。 */
    public int totalAlive(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return 0;
        int n = 0;
        for (GroupRuntime rt : groups.values()) n += rt.alive.size();
        return n;
    }

    /** 当前进行中的组名（首个 ACTIVE 组；HUD 显示用）。 */
    public @Nullable String activeGroupName(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return null;
        for (Map.Entry<String, GroupRuntime> e : groups.entrySet()) {
            if (e.getValue().state == GroupState.ACTIVE) return e.getKey();
        }
        return null;
    }

    /** 组总数/已完成数（HUD 进度用）。 */
    public int[] groupProgress(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return new int[]{0, 0};
        int done = 0;
        for (GroupRuntime rt : groups.values()) {
            if (rt.state == GroupState.COMPLETED) done++;
        }
        return new int[]{groups.size(), done};
    }

    public int aliveCount(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        if (groups == null) return 0;
        GroupRuntime rt = groups.get(groupName);
        return rt != null && rt.state == GroupState.ACTIVE ? rt.alive.size() : 0;
    }

    public @Nullable GroupState groupState(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        GroupRuntime rt = groups != null ? groups.get(groupName) : null;
        return rt != null ? rt.state : null;
    }

    /** 组 spawn-timing（房间锁门按此分流点火/脚本/依赖）。 */
    public @Nullable MonsterConfig.SpawnTiming timingOf(AbyssInstance instance, String groupName) {
        Map<String, GroupRuntime> groups = instanceGroups.get(instance.getInstanceId());
        GroupRuntime rt = groups != null ? groups.get(groupName) : null;
        return rt != null ? rt.group.spawnTiming() : null;
    }

    /** 实体是否属于本实例怪物组。 */
    public boolean isGroupMob(UUID entityUuid) {
        return entityToGroup.containsKey(entityUuid);
    }

    /** 清理实例全部组状态。 */
    public void cleanup(AbyssInstance instance) {
        Map<String, GroupRuntime> groups = instanceGroups.remove(instance.getInstanceId());
        if (groups != null) {
            for (GroupRuntime rt : groups.values()) {
                cancelDelayed(rt);
                for (UUID u : rt.alive) entityToGroup.remove(u);
            }
        }
    }

    private void keepChunkLoaded(Location loc) {
        try {
            Chunk c = loc.getChunk();
            if (!c.isLoaded()) c.load();
            c.setForceLoaded(true);
        } catch (Throwable ignored) { }
    }

    public void shutdown() {
        for (Map<String, GroupRuntime> groups : instanceGroups.values()) {
            for (GroupRuntime rt : groups.values()) {
                cancelDelayed(rt);
            }
        }
        instanceGroups.clear();
        entityToGroup.clear();
    }
}
