package xuanmo.arcartxsuite.qqbot.config;

public record QQBotGroupConfig(
    long groupId,
    String syncMode,
    String gameToQqFormat,
    String qqToGameFormat,
    boolean commandsEnabled,
    String joinMessage,
    String quitMessage
) {
    public boolean syncGameToQq() {
        return "both".equalsIgnoreCase(syncMode) || "game-to-qq".equalsIgnoreCase(syncMode);
    }

    public boolean syncQqToGame() {
        return "both".equalsIgnoreCase(syncMode) || "qq-to-game".equalsIgnoreCase(syncMode);
    }
}
