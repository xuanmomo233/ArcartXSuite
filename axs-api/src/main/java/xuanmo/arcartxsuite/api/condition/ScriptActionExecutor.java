package xuanmo.arcartxsuite.api.condition;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ScriptActionExecutor {

    @Nullable
    Object execute(
        @Nullable Player player,
        @NotNull ScriptActionKind kind,
        @NotNull String script
    );

    static ScriptActionExecutor noop() {
        return NoopScriptActionExecutor.INSTANCE;
    }
}
