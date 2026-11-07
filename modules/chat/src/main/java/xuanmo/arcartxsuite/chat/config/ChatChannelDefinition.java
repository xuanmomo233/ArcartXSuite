package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 聊天频道定义，描述单个频道的全部配置项。
 * <p>
 * 由 {@link ChatModuleConfiguration#load} 从 {@code chat/channels/} 目录下的 yml 文件加载，
 * 包含频道模式、权限、跨服开关、格式模板等信息。
 *
 * @param id                频道 ID（小写规范化）
 * @param enabled           是否启用
 * @param displayName       频道显示名称
 * @param mode              频道模式（普通 / 全局 / 私聊 / 管理）
 * @param sendPermission    发送所需权限，空串表示无限制
 * @param receivePermission 接收所需权限，默认继承发送权限
 * @param crossServer       是否跨服传输
 * @param range             本地聊天半径（0 表示全图）
 * @param format            常规消息格式模板
 * @param consoleFormat     控制台消息格式模板
 * @param senderFormat      私聊发送者视角格式模板
 * @param recipientFormat   私聊接收者视角格式模板
 * @param spyFormat         监听（SocialSpy）视角格式模板
 */
public record ChatChannelDefinition(
    String id,
    boolean enabled,
    String displayName,
    ChatChannelMode mode,
    String sendPermission,
    String receivePermission,
    boolean crossServer,
    double range,
    String format,
    String consoleFormat,
    String senderFormat,
    String recipientFormat,
    String spyFormat
) {

    /** External chat relays must never widen a channel's audience. */
    public boolean permitsExternalRelay() {
        return enabled && (mode == ChatChannelMode.NORMAL || mode == ChatChannelMode.GLOBAL)
            && range == 0.0D && receivePermission.isBlank();
    }

    /**
     * 从 yml 配置节加载频道定义。
     *
     * @param rawId        频道原始 ID（文件名），将被规范化为小写
     * @param configuration 包含频道配置的 yml 配置节
     * @return 加载后的频道定义实例
     */
    public static ChatChannelDefinition load(String rawId, FileConfiguration configuration) {
        String id = normalizeId(rawId);
        boolean enabled = configuration.getBoolean("enabled", true);
        String displayName = string(configuration.getString("display-name", rawId));
        ChatChannelMode mode = ChatChannelMode.parse(configuration.getString("mode", "normal"));
        String sendPermission = string(configuration.getString("send-permission", ""));
        String receivePermission = string(configuration.getString("receive-permission", sendPermission));
        boolean crossServer = configuration.getBoolean("cross-server", mode == ChatChannelMode.GLOBAL || mode == ChatChannelMode.STAFF);
        double range = Math.max(0.0D, configuration.getDouble("range", 0.0D));
        String format = string(configuration.getString("format", "&7[{channel}] &f{player_name}&7: &r{message}"));
        String consoleFormat = string(configuration.getString("console-format", "[{channel}] {player_name}: {message}"));
        String senderFormat = string(configuration.getString("sender-format", "&d[私聊 -> {target_name}] &f{player_name}&7: &r{message}"));
        String recipientFormat = string(configuration.getString("recipient-format", "&d[私聊 <- {player_name}] &f{player_name}&7: &r{message}"));
        String spyFormat = string(configuration.getString("spy-format", "&5[监听 {player_name} -> {target_name}] &r{message}"));
        return new ChatChannelDefinition(
            id,
            enabled,
            displayName.isBlank() ? rawId : displayName,
            mode,
            sendPermission,
            receivePermission,
            crossServer,
            range,
            format,
            consoleFormat,
            senderFormat,
            recipientFormat,
            spyFormat
        );
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }
}
