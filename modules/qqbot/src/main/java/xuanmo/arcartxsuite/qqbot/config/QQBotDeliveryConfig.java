package xuanmo.arcartxsuite.qqbot.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** Shared delivery policy for chat, commands, UI and public integrations. */
public record QQBotDeliveryConfig(int maxMessageLength, long playerCooldownMillis,
                                  int groupMessagesPerMinute, boolean requireBinding,
                                  boolean vanillaChatFallback) {
    public static QQBotDeliveryConfig load(YamlConfiguration yaml) {
        return new QQBotDeliveryConfig(
            Math.max(16, Math.min(2000, yaml.getInt("delivery.max-message-length", 300))),
            Math.max(1000L, Math.min(60000L, yaml.getLong("delivery.player-cooldown-seconds", 3) * 1000L)),
            Math.max(1, Math.min(300, yaml.getInt("delivery.group-messages-per-minute", 30))),
            yaml.getBoolean("delivery.require-binding", false),
            yaml.getBoolean("delivery.vanilla-chat-fallback", false));
    }
}
