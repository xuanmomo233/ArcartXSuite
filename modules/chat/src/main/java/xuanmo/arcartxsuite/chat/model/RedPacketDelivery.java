package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

/** Durable delivery for one committed claim or expiration refund. */
public record RedPacketDelivery(String id, String packetId, UUID playerId, RedPacketReward reward,
                                boolean refund, Status status, Instant updatedAt) {
    public enum Status { READY, EXECUTING, DELIVERED, HANDED_OFF, REVIEW }

    public static RedPacketDelivery claim(String packetId, UUID playerId, RedPacketReward reward, Instant now) {
        return new RedPacketDelivery(packetId + ":claim:" + playerId, packetId, playerId,
            reward, false, Status.READY, now);
    }

    public static RedPacketDelivery refund(RedPacket packet, Instant now) {
        return new RedPacketDelivery(packet.id() + ":refund", packet.id(), UUID.fromString(packet.senderUuid()),
            RedPacketReward.currency(packet.currencyId(), packet.remainingAmount(), "红包过期退款"),
            true, Status.READY, now);
    }
}
