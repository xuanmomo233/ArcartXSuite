package xuanmo.arcartxsuite.chat.service;

import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;

public final class ChatEnvelopeCodec {
    private ChatEnvelopeCodec() {}

    public static String encode(ChatEnvelope envelope) { throw unavailable(); }
    public static String encode(ChatEnvelope envelope, Set<UUID> recipients) { throw unavailable(); }
    static String encodeForTransport(ChatEnvelope envelope, Set<UUID> recipients) { throw unavailable(); }
    public static ChatEnvelope decode(String content) throws InvalidConfigurationException { throw unavailable(); }
    public static Delivery decodeDelivery(String content) throws InvalidConfigurationException { throw unavailable(); }

    public record Delivery(ChatEnvelope envelope, Set<UUID> originRecipients) {}

    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException("Cross-server Chat envelope codec is not included");
    }
}
