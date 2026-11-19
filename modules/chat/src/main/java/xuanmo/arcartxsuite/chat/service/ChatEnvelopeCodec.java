package xuanmo.arcartxsuite.chat.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatItemPreview;

/**
 * 聊天消息信封编解码器，将 {@link ChatEnvelope} 序列化为 yml 字符串或从 yml 反序列化。
 * <p>
 * 用于跨服传输场景下将消息信封通过 {@link xuanmo.arcartxsuite.api.crossserver.CrossServerChannel} 传输。
 */
public final class ChatEnvelopeCodec {
    private static final String COMPRESSED_PREFIX = "axs_chat_gzip|";
    private static final int COMPRESS_AT_BYTES = 12 * 1024;
    private static final int MAX_EXPANDED_BYTES = 512 * 1024;

    private ChatEnvelopeCodec() {
    }

    /**
     * 将消息信封编码为 yml 字符串。
     *
     * @param envelope 消息信封
     * @return yml 格式的字符串
     */
    public static String encode(ChatEnvelope envelope) {
        return encode(envelope, Set.of());
    }

    static String encodeForTransport(ChatEnvelope envelope, Set<UUID> originRecipients) {
        String original = encode(envelope, originRecipients);
        if (original.length() < COMPRESS_AT_BYTES / 3) return original;
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < COMPRESS_AT_BYTES || bytes.length > MAX_EXPANDED_BYTES) return original;
        try {
            var buffer = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(buffer)) {
                gzip.write(bytes);
            }
            String compressed = COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(buffer.toByteArray());
            return compressed.length() < bytes.length ? compressed : original;
        } catch (IOException failure) {
            return original;
        }
    }

    /** Includes the local recipients so a player switching to the other node receives one copy. */
    public static String encode(ChatEnvelope envelope, Set<UUID> originRecipients) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("message-id", envelope.messageId());
        yaml.set("origin-node-id", envelope.originNodeId());
        yaml.set("server-id", envelope.serverId());
        yaml.set("channel-id", envelope.channelId());
        yaml.set("sender-uuid", envelope.senderUuid());
        yaml.set("sender-name", envelope.senderName());
        yaml.set("sender-display-name", envelope.senderDisplayName());
        yaml.set("target-uuid", envelope.targetUuid());
        yaml.set("target-name", envelope.targetName());
        yaml.set("rendered-text", envelope.renderedText());
        yaml.set("rendered-target-text", envelope.renderedTargetText());
        yaml.set("rendered-spy-text", envelope.renderedSpyText());
        yaml.set("console-text", envelope.consoleText());
        yaml.set("private-message", envelope.privateMessage());
        yaml.set("staff-message", envelope.staffMessage());
        yaml.set("mention-all", envelope.mentionAll());
        yaml.set("mentioned-names", envelope.mentionedNames());
        yaml.set("raw-message", envelope.rawMessage());
        yaml.set("audience-key", envelope.audienceKey());
        if (!originRecipients.isEmpty()) {
            ByteBuffer ids = ByteBuffer.allocate(originRecipients.size() * 16);
            for (UUID id : originRecipients) ids.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
            yaml.set("origin-recipients", Base64.getEncoder().encodeToString(ids.array()));
        }
        if (envelope.itemPreview() != null) {
            yaml.set("item-preview.item-json", envelope.itemPreview().itemJson());
            yaml.set("item-preview.display-text", envelope.itemPreview().displayText());
            yaml.set("item-preview.material-key", envelope.itemPreview().materialKey());
            yaml.set("item-preview.amount", envelope.itemPreview().amount());
        }
        return yaml.saveToString();
    }

    /**
     * 从 yml 字符串解码消息信封。
     *
     * @param content yml 格式的字符串
     * @return 解码后的消息信封
     * @throws InvalidConfigurationException yml 格式非法时抛出
     */
    public static ChatEnvelope decode(String content) throws InvalidConfigurationException {
        return decodeDelivery(content).envelope();
    }

    public static Delivery decodeDelivery(String content) throws InvalidConfigurationException {
        if (content.startsWith(COMPRESSED_PREFIX)) content = expand(content.substring(COMPRESSED_PREFIX.length()));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        ChatItemPreview itemPreview = null;
        if (yaml.isConfigurationSection("item-preview")) {
            itemPreview = new ChatItemPreview(
                string(yaml.getString("item-preview.item-json")),
                string(yaml.getString("item-preview.display-text")),
                string(yaml.getString("item-preview.material-key")),
                Math.max(0, yaml.getInt("item-preview.amount", 0))
            );
        }
        ChatEnvelope envelope = new ChatEnvelope(
            string(yaml.getString("message-id")),
            string(yaml.getString("origin-node-id")),
            string(yaml.getString("server-id")),
            string(yaml.getString("channel-id")),
            string(yaml.getString("sender-uuid")),
            string(yaml.getString("sender-name")),
            string(yaml.getString("sender-display-name")),
            string(yaml.getString("target-uuid")),
            string(yaml.getString("target-name")),
            string(yaml.getString("rendered-text")),
            string(yaml.getString("rendered-target-text")),
            string(yaml.getString("rendered-spy-text")),
            string(yaml.getString("console-text")),
            yaml.getBoolean("private-message", false),
            yaml.getBoolean("staff-message", false),
            yaml.getBoolean("mention-all", false),
            List.copyOf(yaml.getStringList("mentioned-names")),
            itemPreview,
            string(yaml.getString("raw-message")),
            string(yaml.getString("audience-key"))
        );
        Set<UUID> originRecipients = new LinkedHashSet<>();
        String encoded = yaml.getString("origin-recipients", "");
        if (!encoded.isEmpty()) {
            try {
                ByteBuffer ids = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
                if (ids.remaining() % 16 != 0) throw new IllegalArgumentException("invalid recipient list length");
                while (ids.hasRemaining()) originRecipients.add(new UUID(ids.getLong(), ids.getLong()));
            } catch (IllegalArgumentException invalid) {
                throw new InvalidConfigurationException("Invalid chat delivery recipients", invalid);
            }
        }
        return new Delivery(envelope, Set.copyOf(originRecipients));
    }

    public record Delivery(ChatEnvelope envelope, Set<UUID> originRecipients) {
    }

    private static String expand(String encoded) throws InvalidConfigurationException {
        if (encoded.length() > MAX_EXPANDED_BYTES) {
            throw new InvalidConfigurationException("Compressed chat envelope is too large");
        }
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            byte[] bytes = gzip.readNBytes(MAX_EXPANDED_BYTES + 1);
            if (bytes.length > MAX_EXPANDED_BYTES) {
                throw new InvalidConfigurationException("Expanded chat envelope is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException malformed) {
            throw new InvalidConfigurationException("Invalid compressed chat envelope", malformed);
        }
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }
}
