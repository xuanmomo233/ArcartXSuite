package xuanmo.arcartxsuite.announcer.transport;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AnnouncerEnvelopeCodec {

    private AnnouncerEnvelopeCodec() {
    }

    public static String encode(AnnouncerEnvelope envelope) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("message-id", envelope.messageId());
        yaml.set("origin-node", envelope.originNode());
        yaml.set("text", envelope.text());
        yaml.set("immediate", envelope.immediate());
        return yaml.saveToString();
    }

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
