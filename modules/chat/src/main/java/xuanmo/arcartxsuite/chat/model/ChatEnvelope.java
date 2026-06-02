package xuanmo.arcartxsuite.chat.model;

import java.util.List;

public record ChatEnvelope(
    String messageId,
    String originNodeId,
    String serverId,
    String channelId,
    String senderUuid,
    String senderName,
    String senderDisplayName,
    String targetUuid,
    String targetName,
    String renderedText,
    String renderedTargetText,
    String renderedSpyText,
    String consoleText,
    boolean privateMessage,
    boolean staffMessage,
    boolean mentionAll,
    List<String> mentionedNames,
    ChatItemPreview itemPreview,
    String rawMessage
) {
    public String dedupeKey() {
        return originNodeId + ":" + messageId;
    }
}
