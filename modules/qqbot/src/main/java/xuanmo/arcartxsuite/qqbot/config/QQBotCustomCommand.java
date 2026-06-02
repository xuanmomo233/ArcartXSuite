package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

public record QQBotCustomCommand(
    String name,
    int permission,
    String type,
    String builtinId,
    String command,
    String usage,
    List<String> placeholders,
    String format
) {
    public boolean isBuiltin() {
        return "builtin".equalsIgnoreCase(type);
    }

    public boolean isServerCommand() {
        return "server-command".equalsIgnoreCase(type);
    }

    public boolean isPapiQuery() {
        return "papi-query".equalsIgnoreCase(type);
    }
}
