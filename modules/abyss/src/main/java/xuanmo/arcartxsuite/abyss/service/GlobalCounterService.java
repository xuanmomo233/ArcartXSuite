package xuanmo.arcartxsuite.abyss.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;

/**
 * 全服共享计数器：跨副本/跨实例的全局进度（全服收集、归档、完成率等）。
 * <p>
 * 职责边界：abyss 只提供"计数持久化 + 变更/阈值事件出口"，不承载全服事件玩法本体
 * （防守战/阵营混战等由 eventpacket 规则或外部玩法模块订阅事件实现）。
 * <p>
 * 事件：
 * <ul>
 *   <li>{@code axs.abyss.global_change} —— 每次 add/set：payload key/delta/value + 上下文</li>
 *   <li>{@code axs.abyss.global_threshold} —— 首次跨越配置阈值：payload key/value/threshold</li>
 * </ul>
 * 阈值在模块配置 {@code global-thresholds: {key: [n, ...]}} 声明；
 * 同一阈值只触发一次（{@code reset} 后可重新触发）。
 */
public final class GlobalCounterService {

    private final PlayerDataManager data;
    private final Supplier<Map<String, List<Long>>> thresholds;
    private final BiConsumer<String, Map<String, Object>> eventPublisher;

    /** key -> 已触发过的阈值集合（防重复触发）。 */
    private final Map<String, Set<Long>> fired = new ConcurrentHashMap<>();

    public GlobalCounterService(PlayerDataManager data,
                                Supplier<Map<String, List<Long>>> thresholds,
                                BiConsumer<String, Map<String, Object>> eventPublisher) {
        this.data = data;
        this.thresholds = thresholds;
        this.eventPublisher = eventPublisher;
    }

    public void start() {
        data.preloadGlobals();
    }

    /** 累加计数；返回新值。ctx 为附带事件上下文（dungeon/instance/player 等，可空）。 */
    public long add(String key, long delta, @Nullable Map<String, Object> ctx) {
        long value = data.addGlobal(key, delta);
        publish("axs.abyss.global_change", key, value, delta, null, ctx);
        checkThresholds(key, value, ctx);
        return value;
    }

    public void set(String key, long value, @Nullable Map<String, Object> ctx) {
        data.setGlobal(key, value);
        publish("axs.abyss.global_change", key, value, 0, null, ctx);
        checkThresholds(key, value, ctx);
    }

    public long get(String key) {
        return data.getGlobal(key);
    }

    /** 归零并清阈值触发记录（一轮全服事件结束后重置用）。 */
    public void reset(String key, @Nullable Map<String, Object> ctx) {
        data.setGlobal(key, 0);
        fired.remove(key);
        publish("axs.abyss.global_change", key, 0, 0, null, ctx);
    }

    private void checkThresholds(String key, long value, @Nullable Map<String, Object> ctx) {
        List<Long> list = thresholds.get().get(key);
        if (list == null) return;
        Set<Long> hit = fired.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        for (long th : list) {
            if (value >= th && hit.add(th)) {
                publish("axs.abyss.global_threshold", key, value, 0, th, ctx);
            }
        }
    }

    private void publish(String topic, String key, long value, long delta,
                         @Nullable Long threshold, @Nullable Map<String, Object> ctx) {
        Map<String, Object> data = ctx != null
            ? new java.util.HashMap<>(ctx) : new java.util.HashMap<>();
        data.put("key", key);
        data.put("value", value);
        if (delta != 0) data.put("delta", delta);
        if (threshold != null) data.put("threshold", threshold);
        eventPublisher.accept(topic, data);
    }
}
