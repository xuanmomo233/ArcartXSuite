package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

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
