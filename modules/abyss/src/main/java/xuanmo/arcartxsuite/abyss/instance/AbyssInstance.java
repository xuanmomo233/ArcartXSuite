package xuanmo.arcartxsuite.abyss.instance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/**
 * 副本实例：一次地牢运行。
 * <p>
 * 持有运行期全部可变状态：世界、玩家、死亡计数、变量、统计、调度任务。
 * 状态迁移用 {@link AtomicReference} CAS 保证并发安全；
 * 实际编排（prepare/start/complete/fail/cleanup）由 {@code AbyssService} 驱动。
 */
public final class AbyssInstance {

    private final UUID instanceId;
    private volatile AbyssTemplate template;
    private boolean designMode;
    private boolean admitted;
    public boolean admitted() {return admitted;}
    public void confirmAdmission() {admitted=true;}
    private final @Nullable xuanmo.arcartxsuite.abyss.party.Party party;   // 可空（单人）
    private final long createTime;

    private final AtomicReference<AbyssStatus> status = new AtomicReference<>(AbyssStatus.PREPARING);
    private volatile World world;
    private volatile long startTime;
    private volatile long endTime;

    /** 副本内玩家（含旁观/死亡的在线玩家）。 */
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    /** 当前处于"死亡旁观"状态的玩家。 */
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();
    /** 已进入过副本世界的玩家（start 判定：全员进图）。 */
    private final Set<UUID> enteredPlayers = ConcurrentHashMap.newKeySet();
    /** 进本前保存的原位置。 */
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();
    /** 进本前保存的游戏模式。 */
    private final Map<UUID, GameMode> savedGameModes = new ConcurrentHashMap<>();
    /** 每人死亡次数。 */
    private final Map<UUID, Integer> deaths = new ConcurrentHashMap<>();
    /** 每人伤害输出统计。 */
    private final Map<UUID, Double> damageDealt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> damageTaken = new ConcurrentHashMap<>();
    private final Map<UUID, Double> healingDone = new ConcurrentHashMap<>();
    /** 实例变量表（脚本 setvar/getvar）。 */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    /** 交互点一次性触发记录。 */
    private final Set<String> triggeredOnce = ConcurrentHashMap.newKeySet();
    /** 实例级调度任务（统一取消）。 */
    private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
    /** 实例级脚本会话（取消时中断 wait）。 */
    private final List<xuanmo.arcartxsuite.abyss.script.ScriptSession> scriptSessions = new CopyOnWriteArrayList<>();
    /** 队伍击杀统计（记分板/UI 用）。 */
    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();
    /** 阻止重复 all_death 触发。 */
    private volatile boolean teamWipeHandled;

    public AbyssInstance(UUID instanceId, AbyssTemplate template,
                         @Nullable xuanmo.arcartxsuite.abyss.party.Party party) {
        this.instanceId = instanceId;
        this.template = template;
        this.party = party;
        this.createTime = System.currentTimeMillis();
    }

    // ── 基础 ──────────────────────────────────────────

    public UUID getInstanceId() { return instanceId; }
    public AbyssTemplate getTemplate() { return template; }
    public boolean designMode() { return designMode; }
    public void designMode(boolean value) { designMode = value; }
    public void updateDesignTemplate(AbyssTemplate template) { this.template = template; }
    public @Nullable xuanmo.arcartxsuite.abyss.party.Party getParty() { return party; }
    public long getCreateTime() { return createTime; }
    public AbyssStatus getStatus() { return status.get(); }
    public @Nullable World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }

    // ── 状态迁移（CAS）────────────────────────────────

    public boolean transitionTo(AbyssStatus from, AbyssStatus to) {
        boolean ok = status.compareAndSet(from, to);
        if (ok && (to == AbyssStatus.COMPLETED || to == AbyssStatus.FAILED || to == AbyssStatus.CANCELLED)) {
            endTime = System.currentTimeMillis();
        }
        if (ok && to == AbyssStatus.ACTIVE) {
            startTime = System.currentTimeMillis();
        }
        return ok;
    }

    public boolean isFinished() {
        AbyssStatus s = status.get();
        return s == AbyssStatus.COMPLETED || s == AbyssStatus.FAILED || s == AbyssStatus.CANCELLED;
    }

    // ── 玩家 ──────────────────────────────────────────

    public Set<UUID> getPlayerUUIDs() { return players; }

    public List<Player> getPlayers() {
        return players.stream().map(Bukkit::getPlayer)
            .filter(p -> p != null && p.isOnline()).map(p -> (Player) p).toList();
    }

    public int getOnlinePlayerCount() {
        return (int) players.stream().map(Bukkit::getPlayer)
            .filter(p -> p != null && p.isOnline()).count();
    }

    public boolean hasPlayer(UUID uuid) { return players.contains(uuid); }
    public boolean hasEnteredPlayers() { return !enteredPlayers.isEmpty(); }
    public void addPlayer(Player p) { players.add(p.getUniqueId()); }
    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        deadPlayers.remove(uuid);
        enteredPlayers.remove(uuid);
        originalLocations.remove(uuid);
        savedGameModes.remove(uuid);
    }

    /** 已进入副本世界（用于全员进图判定）。 */
    public boolean markEntered(UUID uuid) { return enteredPlayers.add(uuid); }
    public boolean allEntered() {
        return !players.isEmpty() && enteredPlayers.containsAll(players);
    }

    // ── 位置/模式 存档与恢复 ───────────────────────────

    public void saveOriginalLocation(Player p, Location loc) {
        originalLocations.put(p.getUniqueId(), loc.clone());
    }

    public @Nullable Location getOriginalLocation(UUID uuid) {
        return originalLocations.get(uuid);
    }

    public void savePlayerGameMode(Player p) {
        savedGameModes.putIfAbsent(p.getUniqueId(), p.getGameMode());
    }

    public void restorePlayerGameMode(Player p) {
        GameMode gm = savedGameModes.remove(p.getUniqueId());
        if (gm != null && p.getGameMode() != gm) {
            p.setGameMode(gm);
        }
    }

    /** 读取已保存的游戏模式（不移除）。 */
    public @Nullable GameMode getSavedGameMode(UUID uuid) {
        return savedGameModes.get(uuid);
    }

    // ── 死亡 ──────────────────────────────────────────

    public int recordDeath(Player p) {
        UUID u = p.getUniqueId();
        deadPlayers.add(u);
        return deaths.merge(u, 1, Integer::sum);
    }

    public void revive(Player p) {
        deadPlayers.remove(p.getUniqueId());
    }

    public boolean isPlayerCurrentlyDead(Player p) {
        return deadPlayers.contains(p.getUniqueId());
    }

    public int getDeaths(UUID uuid) { return deaths.getOrDefault(uuid, 0); }

    /** 存活玩家数（副本内在线且未死亡）。 */
    public int getAlivePlayerCount() {
        int n = 0;
        for (UUID u : players) {
            if (deadPlayers.contains(u)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) n++;
        }
        return n;
    }

    public boolean isTeamWipeHandled() { return teamWipeHandled; }
    public boolean markTeamWipeHandled() {
        synchronized (this) {
            if (teamWipeHandled) return false;
            teamWipeHandled = true;
            return true;
        }
    }

    // ── 统计 ──────────────────────────────────────────

    public void recordDamage(UUID uuid, double amount) {
        damageDealt.merge(uuid, amount, Double::sum);
    }

    public void recordDamageTaken(UUID uuid, double amount) {
        damageTaken.merge(uuid, amount, Double::sum);
    }

    public void recordHealing(UUID uuid, double amount) {
        healingDone.merge(uuid, amount, Double::sum);
    }

    public Map<UUID, Double> getDamageDealt() { return damageDealt; }
    public Map<UUID, Double> getDamageTaken() { return damageTaken; }
    public Map<UUID, Double> getHealingDone() { return healingDone; }
    public Map<UUID, Integer> getKillCounts() { return killCounts; }
    public void recordKill(UUID uuid) { killCounts.merge(uuid, 1, Integer::sum); }

    // ── 变量/一次性标记 ────────────────────────────────

    public Map<String, Object> getVariables() { return variables; }

    /** once 标记：首次设置返回 true，重复返回 false。 */
    public boolean markTriggeredOnce(String key) { return triggeredOnce.add(key); }
    public boolean isTriggered(String key) { return triggeredOnce.contains(key); }

    // ── 任务/脚本会话注册 ──────────────────────────────

    public void registerTask(ScheduledTask task) { tasks.add(task); }
    public void registerScriptSession(xuanmo.arcartxsuite.abyss.script.ScriptSession s) { scriptSessions.add(s); }
    public void unregisterScriptSession(xuanmo.arcartxsuite.abyss.script.ScriptSession s) {scriptSessions.remove(s);}
    public List<ScheduledTask> getTasks() { return tasks; }
    public List<xuanmo.arcartxsuite.abyss.script.ScriptSession> getScriptSessions() { return scriptSessions; }

    // ── 时间 ──────────────────────────────────────────

    public long getRunningTime() {
        long s = startTime;
        if (s == 0) return 0;
        long e = endTime == 0 ? System.currentTimeMillis() : endTime;
        return (e - s) / 1000;
    }

    public boolean isTimeout() {
        int limit = template.timeLimitSeconds();
        return limit > 0 && getStatus() == AbyssStatus.ACTIVE && getRunningTime() >= limit;
    }

    /** 清理运行期集合（cleanup 时调用，释放内存引用）。 */
    public void clearRuntime() {
        players.clear();
        deadPlayers.clear();
        enteredPlayers.clear();
        originalLocations.clear();
        savedGameModes.clear();
        deaths.clear();
        damageDealt.clear();
        damageTaken.clear();
        healingDone.clear();
        variables.clear();
        triggeredOnce.clear();
        tasks.clear();
        scriptSessions.clear();
        killCounts.clear();
        world = null;
    }
}
