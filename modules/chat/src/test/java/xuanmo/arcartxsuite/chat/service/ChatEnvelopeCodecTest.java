package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;

class ChatEnvelopeCodecTest {
    @Test
    void roundTripsThePlayersAlreadyServedAtOriginWithoutChangingTheMessage() throws Exception {
        ChatEnvelope envelope = new ChatEnvelope("1", "a", "a", "normal", UUID.randomUUID().toString(),
            "Alex", "Alex", "", "", "你好", "你好", "", "你好", false, false, false, List.of(),
            null, "你好", "normal");
        Set<UUID> recipients = Set.of(UUID.randomUUID(), UUID.randomUUID());

        var delivery = ChatEnvelopeCodec.decodeDelivery(ChatEnvelopeCodec.encode(envelope, recipients));
        assertEquals(envelope, delivery.envelope());
        assertEquals(recipients, delivery.originRecipients());
        assertEquals(Set.of(), ChatEnvelopeCodec.decodeDelivery(ChatEnvelopeCodec.encode(envelope)).originRecipients());
        assertThrows(InvalidConfigurationException.class,
            () -> ChatEnvelopeCodec.decodeDelivery("origin-recipients: 'AA=='\n"));
    }

    @Test
    void compressesLargeItemMessagesWithoutLosingMetadataOrRecipientDedupe() throws Exception {
        String largeTag = "{tag:\"" + "真实物品属性".repeat(3_000) + "\"}";
        var item = new xuanmo.arcartxsuite.chat.model.ChatItemPreview(largeTag, "物品", "minecraft:stone", 1);
        var envelope = new ChatEnvelope("1", "a", "a", "normal", UUID.randomUUID().toString(), "Alex", "Alex",
            "", "", "物品", "物品", "", "物品", false, false, false, List.of(), item, "物品", "normal");
        Set<UUID> recipients = Set.of(UUID.randomUUID(), UUID.randomUUID());
        String encoded = ChatEnvelopeCodec.encodeForTransport(envelope, recipients);
        assertTrue(encoded.length() < ChatEnvelopeCodec.encode(envelope, recipients).length());
        assertEquals(new ChatEnvelopeCodec.Delivery(envelope, recipients), ChatEnvelopeCodec.decodeDelivery(encoded));
        assertThrows(InvalidConfigurationException.class,
            () -> ChatEnvelopeCodec.decodeDelivery("axs_chat_gzip|AA=="));
    }
}
