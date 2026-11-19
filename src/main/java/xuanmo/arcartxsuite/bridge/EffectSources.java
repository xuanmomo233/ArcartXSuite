package xuanmo.arcartxsuite.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sanitized source state, independent of Bukkit; readers reuse one immutable merged view. */
final class EffectSources<K> {
    private final Map<String, Map<K, Integer>> sources = new LinkedHashMap<>();
    private volatile Map<K, Integer> merged = Map.of();

    record Change<K>(boolean changed, Map<K, Integer> previous, Map<K, Integer> merged) { }

    synchronized Change<K> replace(String sourceId, Map<K, Integer> effects) {
        Map<K, Integer> previous = sources.get(sourceId);
        if (effects.equals(previous)) return new Change<>(false, previous, merged);
        sources.put(sourceId, Map.copyOf(effects));
        rebuild();
        return new Change<>(true, previous, merged);
    }

    synchronized Change<K> remove(String sourceId) {
        Map<K, Integer> previous = sources.remove(sourceId);
        if (previous == null) return new Change<>(false, null, merged);
        rebuild();
        return new Change<>(true, previous, merged);
    }

    Map<K, Integer> snapshot() { return merged; }
    boolean isEmpty() { return merged.isEmpty(); }

    private void rebuild() {
        Map<K, Integer> result = new LinkedHashMap<>();
        for (Map<K, Integer> effects : sources.values()) {
            effects.forEach((type, level) -> result.merge(type, level, Math::max));
        }
        merged = Map.copyOf(result);
    }
}
