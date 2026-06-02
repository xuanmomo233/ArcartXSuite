package xuanmo.arcartxsuite.chat.service;

import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatItemPreview;

public final class ChatEnvelopeCodec {

    private ChatEnvelopeCodec() {
    }

    public static String encode(ChatEnvelope envelope) {
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
        if (envelope.itemPreview() != null) {
            yaml.set("item-preview.item-json", envelope.itemPreview().itemJson());
            yaml.set("item-preview.display-text", envelope.itemPreview().displayText());
            yaml.set("item-preview.material-key", envelope.itemPreview().materialKey());
            yaml.set("item-preview.amount", envelope.itemPreview().amount());
        }
        return yaml.saveToString();
    }

    public static ChatEnvelope decode(String content) throws InvalidConfigurationException {
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
        return new ChatEnvelope(
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
            string(yaml.getString("raw-message"))
        );
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }
}
