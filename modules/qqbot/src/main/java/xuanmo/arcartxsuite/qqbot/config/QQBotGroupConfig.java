package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的单个 QQ 群配置 record。
 * <p>
 * 关联模块：{@code qqbot}。定义群号、消息同步方向与格式、是否允许群内执行命令，
 * 以及玩家加入 / 退出服务器时推送到群的提示文本。
 */
public record QQBotGroupConfig(
    /** QQ 群号 */
    long groupId,
    /** 消息同步方向：{@code both}、{@code game-to-qq}、{@code qq-to-game} */
    String syncMode,
    /** 游戏 → QQ 的消息格式模板 */
    String gameToQqFormat,
    /** QQ → 游戏的消息格式模板 */
    String qqToGameFormat,
    /** 是否允许在该群执行自定义命令 */
    boolean commandsEnabled,
    /** 玩家加入服务器时推送到群的消息模板 */
    String joinMessage,
    /** 玩家退出服务器时推送到群的消息模板 */
    String quitMessage
) {
    public QQBotGroupConfig {
        syncMode = syncMode == null ? "none" : syncMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("both", "game-to-qq", "qq-to-game", "none").contains(syncMode))
            throw new IllegalArgumentException("QQBot 群同步模式无效: " + syncMode);
    }
    /**
     * 是否开启「游戏 → QQ」方向的消息同步。
     *
     * @return {@code true} 表示同步方向为 {@code both} 或 {@code game-to-qq}
     */
    public boolean syncGameToQq() {
        return "both".equalsIgnoreCase(syncMode) || "game-to-qq".equalsIgnoreCase(syncMode);
    }

    /**
     * 是否开启「QQ → 游戏」方向的消息同步。
     *
     * @return {@code true} 表示同步方向为 {@code both} 或 {@code qq-to-game}
     */
    public boolean syncQqToGame() {
        return "both".equalsIgnoreCase(syncMode) || "qq-to-game".equalsIgnoreCase(syncMode);
    }
}
