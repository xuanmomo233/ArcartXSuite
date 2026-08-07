package xuanmo.arcartxsuite.announcer.transport;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 公告信封的编解码工具类，隶属于 announcer 模块的跨服传输层。
 * <p>
 * 将 {@link AnnouncerEnvelope} 序列化为 YAML 字符串用于跨服传输，
 * 并将收到的 YAML 字符串反序列化回信封对象。该类不可实例化。
 */
public final class AnnouncerEnvelopeCodec {

    private AnnouncerEnvelopeCodec() {
    }

    /**
     * 将公告信封编码为 YAML 字符串。
     *
     * @param envelope 公告信封
     * @return YAML 格式的字符串
     */
    public static String encode(AnnouncerEnvelope envelope) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("message-id", envelope.messageId());
        yaml.set("origin-node", envelope.originNode());
        yaml.set("text", envelope.text());
        yaml.set("immediate", envelope.immediate());
        return yaml.saveToString();
    }

    /**
     * 将 YAML 字符串解码为公告信封。
     *
     * @param content YAML 格式的字符串
     * @return 公告信封对象
     * @throws InvalidConfigurationException 内容不是合法 YAML 时抛出
     */
    public static AnnouncerEnvelope decode(String content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return new AnnouncerEnvelope(
            str(yaml.getString("message-id")),
            str(yaml.getString("origin-node")),
            str(yaml.getString("text")),
            yaml.getBoolean("immediate", false)
        );
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }
}
