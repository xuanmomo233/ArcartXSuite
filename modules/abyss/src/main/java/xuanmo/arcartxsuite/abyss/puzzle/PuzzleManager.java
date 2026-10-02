package xuanmo.arcartxsuite.abyss.puzzle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.PuzzleConfig;
import xuanmo.arcartxsuite.abyss.template.PuzzleConfig.Puzzle;

/**
 * 机关解谜管理器：puzzles.yml 的运行时状态机。
 * <p>
 * 两种机关：
 * <ul>
 *   <li>{@code sequence}：interact 触发成功 → {@link #onInteractFired} 比对当前步，
 *       正确推进 / 错序惩罚（可重置）；步数走完 → on-complete。</li>
 *   <li>{@code input}：脚本 {@code puzzle_prompt} 打开 ArcartX 输入面板，
 *       玩家提交 → {@link #submitInput} 服务端比对答案；错误计数超限 → on-fail 锁定。</li>
 * </ul>
 * 实例变量：{@code puzzle.<id>}=进度（步数/已尝试次数）、{@code puzzle.<id>.done}="1"、
 * {@code puzzle.<id>.locked}="1"（超限锁定）。
 * 事件：axs.abyss.puzzle_step / puzzle_complete / puzzle_fail。
 */
public final class PuzzleManager {

    /** 实例 → (机关id → 状态)。 */
    private final Map<UUID, Map<String, State>> states = new ConcurrentHashMap<>();
    private final Logger logger;
    private Callback callback;

    /** 单机关运行时状态。 */
    private static final class State {
        int progress;                    // sequence: 当前步数；input: 已尝试次数
        boolean done;
        boolean locked;
        final Map<UUID, Integer> perPlayerAttempts = new ConcurrentHashMap<>();
    }

    public interface Callback {
        /** 执行机关脚本段（on-step/on-miss/on-complete/on-fail）。 */
        void runScript(AbyssInstance instance, List<String> lines,
                       String puzzleId, @Nullable Player trigger);

        /** 打开输入面板（input 型）。 */
        void openInputUi(Player player, AbyssInstance instance, Puzzle puzzle,
                         int attemptsLeft);

        /** EventBus 发布。 */
        void publishEvent(String topic, @Nullable Player player, Map<String, Object> data);

        /** 副本内广播消息（msg key 已解析）。 */
        void broadcast(AbyssInstance instance, String message);

        /** 玩家提示消息（msg key 已解析）。 */
        void msg(Player player, String message);
    }

    public PuzzleManager(Logger logger) {
        this.logger = logger;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    /** 副本 start：初始化机关状态表。 */
    public void initInstance(AbyssInstance instance) {
        Map<String, Puzzle> defs = instance.getTemplate().puzzles().puzzles();
        if (defs.isEmpty()) return;
        Map<String, State> map = new ConcurrentHashMap<>();
        defs.keySet().forEach(id -> map.put(id, new State()));
        states.put(instance.getInstanceId(), map);
    }

    public void cleanup(AbyssInstance instance) {
        states.remove(instance.getInstanceId());
        seqMissCounter.keySet().removeIf(
            k -> k.startsWith(instance.getInstanceId() + ":"));
    }

    private @Nullable Puzzle puzzle(AbyssInstance instance, String id) {
        return instance.getTemplate().puzzles().puzzles().get(id);
    }

    private @Nullable State state(AbyssInstance instance, String id) {
        Map<String, State> map = states.get(instance.getInstanceId());
        return map != null ? map.get(id) : null;
    }

    /** 写实例变量（进度/完成/锁定），供 {var:puzzle.xxx} 读取。 */
    private void syncVars(AbyssInstance instance, String id, State st) {
        instance.getVariables().put("puzzle." + id, st.progress);
        if (st.done) instance.getVariables().put("puzzle." + id + ".done", "1");
        if (st.locked) instance.getVariables().put("puzzle." + id + ".locked", "1");
    }

    // ═══ sequence：interact 触发推进 ═══

    /**
     * interact 脚本触发成功后回调（InteractManager.fireCallback 之外另接）。
     * 命中 sequence 机关的当前步 → 推进；错序 → miss（可重置进度）。
     */
    public void onInteractFired(AbyssInstance instance, String interactName, Player trigger) {
        Map<String, State> map = states.get(instance.getInstanceId());
        if (map == null) return;
        for (Map.Entry<String, State> e : map.entrySet()) {
            Puzzle p = puzzle(instance, e.getKey());
            State st = e.getValue();
            if (p == null || p.type() != PuzzleConfig.Type.SEQUENCE
                || st.done || st.locked) continue;
            List<String> steps = p.steps();
            if (steps.isEmpty()) continue;
            String expect = st.progress < steps.size() ? steps.get(st.progress) : null;
            if (interactName.equals(expect)) {
                // 步序正确
                st.progress++;
                syncVars(instance, p.id(), st);
                if (callback == null) continue;
                callback.publishEvent("axs.abyss.puzzle_step", trigger, Map.of(
                    "dungeon", instance.getTemplate().id(),
                    "instance", instance.getInstanceId().toString(),
                    "puzzle", p.id(), "step", st.progress,
                    "steps", steps.size()));
                if (st.progress >= steps.size()) {
                    complete(instance, p, trigger);
                } else {
                    callback.runScript(instance, p.onStep(), p.id(), trigger);
                }
            } else if (steps.contains(interactName)) {
                // 点中的是本机关的其他步 —— 错序
                miss(instance, p, st, trigger, "sequence");
            }
        }
    }

    // ═══ input：密码输入 ═══

    /**
     * 打开输入面板（脚本 puzzle_prompt / 管理命令调用）。
     * @return false=机关不存在/已完成/已锁定/类型不符
     */
    public boolean openInput(AbyssInstance instance, Player player, String puzzleId) {
        Puzzle p = puzzle(instance, puzzleId);
        State st = state(instance, puzzleId);
        if (p == null || st == null || p.type() != PuzzleConfig.Type.INPUT
            || st.done || st.locked) {
            return false;
        }
        int attemptsLeft = p.maxAttempts() <= 0 ? -1
            : Math.max(0, p.maxAttempts() - attemptsOf(instance, p, st, player));
        if (attemptsLeft == 0) {
            lock(instance, p, st, player);
            return false;
        }
        if (callback == null) return false;
        callback.openInputUi(player, instance, p, attemptsLeft);
        return true;
    }

    /**
     * 玩家提交答案（UI 回包 input_submit 路由到这里）。
     * @return true=已处理（无论对错）；false=机关状态无效（忽略包）
     */
    public boolean submitInput(AbyssInstance instance, Player player,
                               String puzzleId, String answer) {
        Puzzle p = puzzle(instance, puzzleId);
        State st = state(instance, puzzleId);
        if (p == null || st == null || p.type() != PuzzleConfig.Type.INPUT
            || st.done || st.locked || p.answer() == null) {
            return false;
        }
        String guess = answer == null ? "" : answer.trim();
        boolean match = p.caseSensitive()
            ? guess.equals(p.answer())
            : guess.equalsIgnoreCase(p.answer());
        if (match) {
            complete(instance, p, player);
            return true;
        }
        // 计次：per-player 记个人表（progress 同步为总尝试数展示用）；共享直接累加 progress
        if (p.perPlayer()) {
            st.perPlayerAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            st.progress = st.perPlayerAttempts.values().stream()
                .mapToInt(Integer::intValue).sum();
        } else {
            st.progress++;
        }
        syncVars(instance, p.id(), st);
        if (p.maxAttempts() > 0 && attemptsOf(instance, p, st, player) >= p.maxAttempts()) {
            lock(instance, p, st, player);
        } else {
            miss(instance, p, st, player, "input");
        }
        return true;
    }

    private int attemptsOf(AbyssInstance instance, Puzzle p, State st, Player player) {
        return p.perPlayer()
            ? st.perPlayerAttempts.getOrDefault(player.getUniqueId(), 0)
            : st.progress;
    }

    // ═══ 通用：完成/错误/锁定/重置 ═══

    private void complete(AbyssInstance instance, Puzzle p, @Nullable Player trigger) {
        State st = state(instance, p.id());
        if (st == null || st.done) return;
        st.done = true;
        syncVars(instance, p.id(), st);
        if (callback == null) return;
        callback.publishEvent("axs.abyss.puzzle_complete", trigger, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "puzzle", p.id()));
        callback.runScript(instance, p.onComplete(), p.id(), trigger);
    }

    private void miss(AbyssInstance instance, Puzzle p, State st,
                      @Nullable Player trigger, String kind) {
        if (p.type() == PuzzleConfig.Type.SEQUENCE && p.resetOnMiss()) {
            st.progress = 0;
        }
        syncVars(instance, p.id(), st);
        if (p.maxAttempts() > 0 && kind.equals("sequence")
            && seqMisses(instance, p.id()) >= p.maxAttempts()) {
            lock(instance, p, st, trigger);
            return;
        }
        if (callback == null) return;
        callback.publishEvent("axs.abyss.puzzle_fail", trigger, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "puzzle", p.id(), "reason", kind));
        callback.runScript(instance, p.onMiss(), p.id(), trigger);
    }

    /** sequence 的错序次数独立于 progress（progress 会重置）。 */
    private final Map<String, Integer> seqMissCounter = new ConcurrentHashMap<>();

    private int seqMisses(AbyssInstance instance, String puzzleId) {
        String key = instance.getInstanceId() + ":" + puzzleId;
        return seqMissCounter.merge(key, 1, Integer::sum);
    }

    private void lock(AbyssInstance instance, Puzzle p, State st, @Nullable Player trigger) {
        if (st.locked) return;
        st.locked = true;
        syncVars(instance, p.id(), st);
        if (callback == null) return;
        callback.publishEvent("axs.abyss.puzzle_fail", trigger, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "puzzle", p.id(), "reason", "locked"));
        callback.runScript(instance, p.onFail(), p.id(), trigger);
    }

    /** 脚本动作 puzzle_reset：清空进度+锁定，机关回到初始状态。 */
    public boolean reset(AbyssInstance instance, String puzzleId) {
        State st = state(instance, puzzleId);
        if (st == null) return false;
        st.progress = 0;
        st.done = false;
        st.locked = false;
        st.perPlayerAttempts.clear();
        seqMissCounter.remove(instance.getInstanceId() + ":" + puzzleId);
        instance.getVariables().remove("puzzle." + puzzleId + ".done");
        instance.getVariables().remove("puzzle." + puzzleId + ".locked");
        syncVars(instance, puzzleId, st);
        return true;
    }

    /** 脚本动作 puzzle_solve：直接判定完成（调试/跳关/管理员）。 */
    public boolean forceComplete(AbyssInstance instance, String puzzleId,
                                 @Nullable Player trigger) {
        Puzzle p = puzzle(instance, puzzleId);
        if (p == null) return false;
        complete(instance, p, trigger);
        return true;
    }

    /** 管理员动作：强制锁定（触发 on-fail + puzzle_fail 事件）。 */
    public boolean forceFail(AbyssInstance instance, String puzzleId,
                             @Nullable Player trigger) {
        Puzzle p = puzzle(instance, puzzleId);
        State st = state(instance, puzzleId);
        if (p == null || st == null) return false;
        lock(instance, p, st, trigger);
        return true;
    }

    /** 查询错误/锁定状态（管理列表用）。 */
    public boolean isLocked(AbyssInstance instance, String puzzleId) {
        State st = state(instance, puzzleId);
        return st != null && st.locked;
    }

    /**
     * 查询机关状态（脚本 puzzle_state / 调试）。
     * @return progress 值；-1=无此机关
     */
    public int progress(AbyssInstance instance, String puzzleId) {
        State st = state(instance, puzzleId);
        return st != null ? st.progress : -1;
    }

    public boolean isDone(AbyssInstance instance, String puzzleId) {
        State st = state(instance, puzzleId);
        return st != null && st.done;
    }

}
