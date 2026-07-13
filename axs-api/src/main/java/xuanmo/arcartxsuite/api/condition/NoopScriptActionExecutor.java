package xuanmo.arcartxsuite.api.condition;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NoopScriptActionExecutor implements ScriptActionExecutor {

    static final NoopScriptActionExecutor INSTANCE =
        new NoopScriptActionExecutor();

    private NoopScriptActionExecutor() {
    }

    @Override
    public @Nullable Object execute(
        @Nullable Player player,
        @NotNull ScriptActionKind kind,
        @NotNull String script
    ) {
        return null;
    }
}
