package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.Map;
import java.util.Objects;

/** Never replaces or removes an addon type registered by another owner. */
final class OwnedAddonRegistration<T> implements AutoCloseable {
    private final Map<String, T> registry;
    private final String key;
    private final T type;
    private boolean active;

    OwnedAddonRegistration(Map<String, T> registry, String key, T type) {
        this.registry = Objects.requireNonNull(registry);
        this.key = Objects.requireNonNull(key);
        this.type = Objects.requireNonNull(type);
    }

    void register() {
        synchronized (registry) {
            T existing = registry.get(key);
            if (existing != null && existing != type) {
                throw new IllegalStateException("Chemdah 展示组件 ID 已被其他插件注册: " + key);
            }
            if (!active && existing == type) {
                throw new IllegalStateException("Chemdah 展示组件存在未释放的注册: " + key);
            }
            if (existing == null) registry.put(key, type);
            if (registry.get(key) != type) throw new IllegalStateException("Chemdah 展示组件注册未成功: " + key);
            active = true;
        }
    }

    @Override
    public void close() {
        synchronized (registry) {
            if (active && registry.get(key) == type) registry.remove(key);
            active = false;
        }
    }
}
