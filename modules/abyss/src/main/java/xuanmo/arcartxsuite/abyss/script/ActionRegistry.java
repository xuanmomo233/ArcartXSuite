package xuanmo.arcartxsuite.abyss.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/** 动作注册表：动作名 → Action 实现。 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new ConcurrentHashMap<>();

    public void register(String name, Action action) {
        actions.put(name.toLowerCase(), action);
    }

    public @Nullable Action get(String name) {
        return actions.get(name.toLowerCase());
    }

    public boolean has(String name) {
        return actions.containsKey(name.toLowerCase());
    }
}
