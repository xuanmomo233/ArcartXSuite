package xuanmo.arcartxsuite.chat.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome.Status;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery;
import xuanmo.arcartxsuite.chat.model.RedPacketType;

class ChatConsistencyTest {
    @TempDir Path directory;
    private final UUID sender = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private SQLiteDataSource data;
    private JdbcChatRepository repository;

    @BeforeEach
    void initialize() throws Exception {
        data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + directory.resolve("chat.db"));
        repository = openRepository();
    }

    private JdbcChatRepository openRepository() throws SQLException {
        var instance = new JdbcChatRepository(data, StorageDescriptor.sqlite("chat.db"),
            directory.toFile(), Logger.getLogger("ChatConsistencyTest"));
        instance.initialize();
        return instance;
    }

    @Test
    void reservedDebitCannotBeClaimedAndCompletionIsIdempotent() throws Exception {
        RedPacket draft = packet().withStatus(RedPacket.STATUS_DEBITING);
        repository.insertRedPacket(draft, List.of());
        assertEquals(Status.EXHAUSTED, repository.claimRedPacket(draft, receiver, "Receiver", true).status());
        assertTrue(repository.completeDebit(draft.id(), true));
        assertFalse(repository.completeDebit(draft.id(), true));
        var restarted = openRepository();
        assertEquals(Status.SUCCESS, restarted.claimRedPacket(draft, receiver, "Receiver", true).status());
        restarted.deletePlayerData(receiver);
        assertEquals(Status.ALREADY_CLAIMED, repository.claimRedPacket(draft, receiver, "Receiver", true).status());
        var work = repository.loadReadyDeliveries(Set.of(receiver), 10);
        assertEquals(1, work.size());
        var ready = work.get(0);
        Instant firstReservation = ready.updatedAt().plusMillis(1);
        assertTrue(repository.transitionDelivery(ready, RedPacketDelivery.Status.EXECUTING, firstReservation));
        assertFalse(restarted.transitionDelivery(ready, RedPacketDelivery.Status.EXECUTING, firstReservation));
        var stale = new RedPacketDelivery(ready.id(), ready.packetId(), ready.playerId(), ready.reward(),
            ready.refund(), RedPacketDelivery.Status.EXECUTING, firstReservation);
        assertTrue(restarted.reconcileDelivery(ready.id(), firstReservation, RedPacketDelivery.Status.READY));
        var retried = restarted.loadReadyDeliveries(Set.of(receiver), 10).get(0);
        assertTrue(restarted.transitionDelivery(retried, RedPacketDelivery.Status.EXECUTING, retried.updatedAt().plusMillis(1)));
        assertFalse(repository.transitionDelivery(stale, RedPacketDelivery.Status.DELIVERED, Instant.now()));
    }

    @Test
    void claimRollsBackIfItsDeliveryCannotBeWritten() throws Exception {
        RedPacket packet = packet();
        repository.insertRedPacket(packet, List.of());
        sql("""
            CREATE TRIGGER reject_delivery BEFORE INSERT ON chat_red_packet_delivery
            BEGIN SELECT RAISE(ABORT, 'test'); END
            """);
        assertThrows(SQLException.class, () -> repository.claimRedPacket(packet, receiver, "Receiver", true));
        assertEquals(0, repository.findRedPacket(packet.id()).orElseThrow().claimedCount());
        assertTrue(repository.listRedPacketClaims(packet.id()).isEmpty());
        sql("DROP TRIGGER reject_delivery");
        assertEquals(Status.SUCCESS, repository.claimRedPacket(packet, receiver, "Receiver", true).status());
    }

    @Test
    void expirationRefundUsesCommittedRemainingBalanceOnce() throws Exception {
        RedPacket packet = packet();
        repository.insertRedPacket(packet, List.of());
        assertEquals(Status.SUCCESS, repository.claimRedPacket(packet, receiver, "Receiver", true).status());
        sql("UPDATE chat_red_packets SET expires_at = 0");
        assertTrue(repository.expireRedPacket(packet.id()));
        assertFalse(openRepository().expireRedPacket(packet.id()));
        var refunds = repository.loadReadyDeliveries(Set.of(sender), 10);
        assertEquals(1, refunds.size());
        assertTrue(refunds.get(0).refund());
        assertEquals(new BigDecimal("6"), refunds.get(0).reward().amount());
    }

    @Test
    void confirmedDebitThatExpiredInReviewIsRefundedExactlyOnce() throws Exception {
        RedPacket packet = packet().withStatus(RedPacket.STATUS_DEBITING);
        repository.insertRedPacket(packet, List.of());
        sql("UPDATE chat_red_packets SET expires_at = 0");
        assertEquals(RedPacket.STATUS_EXPIRED,
            repository.reconcileDebit(packet.id(), true, Instant.now()).orElseThrow().status());
        assertTrue(openRepository().reconcileDebit(packet.id(), true, Instant.now()).isEmpty());
        assertEquals(new BigDecimal("10"), repository.loadReadyDeliveries(Set.of(sender), 10).get(0).reward().amount());
    }

    @Test
    void routeReadsObserveOtherConnectionsAndDoNotOverwritePreferences() throws Exception {
        var secondNode = openRepository();
        var defaults = repository.loadState(receiver, "normal");
        repository.saveState(defaults.withAcceptsPrivateMessages(false, Instant.now()), ChatRepository.StateField.PRIVATE_MESSAGES);
        secondNode.saveState(defaults.withAcceptsMentions(false, Instant.now()), ChatRepository.StateField.MENTIONS);
        assertTrue(secondNode.setIgnored(receiver, sender, true));
        repository.setChannelPreference(receiver, "staff", false, false);
        secondNode.setChannelPreference(receiver, "staff", true, true);
        var route = repository.loadRouteStates(Set.of(sender, receiver), "staff", "normal").get(receiver);
        assertFalse(route.player().acceptsPrivateMessages());
        assertFalse(route.player().acceptsMentions());
        assertTrue(route.player().ignores(sender));
        assertFalse(route.subscribed());
        assertTrue(route.quiet());
        RedPacket packet = packet();
        repository.insertRedPacket(packet, List.of());
        assertEquals(Status.NOT_ELIGIBLE, repository.claimRedPacket(packet, receiver, "Receiver", true).status());
        assertEquals(0, repository.findRedPacket(packet.id()).orElseThrow().claimedCount());
    }

    @Test
    void activeMutePagesFilterExpirationAndIncludeOfflineProfiles() throws Exception {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        UUID expired = new UUID(0, 0);
        Instant now = Instant.now();
        repository.upsertProfile(new ChatPlayerProfile(second, "Offline", now, "node-b"));
        for (UUID id : List.of(second, expired, first)) {
            Instant expiry = expired.equals(id) ? now.minusSeconds(1) : null;
            repository.saveMute(new ChatMuteRecord(id, "Moderator", "", now, expiry),
                new ChatModerationReceipt(UUID.randomUUID(), id, "Player", "MUTE", "Moderator", "", "node-a", now, expiry));
        }
        var otherNode = openRepository();
        assertEquals(List.of(first), otherNode.loadActiveMutes(now, 1, 0).stream().map(info -> info.playerUuid()).toList());
        var next = otherNode.loadActiveMutes(now, 1, 1).get(0);
        assertEquals(second, next.playerUuid());
        assertEquals("Offline", next.playerName());
        assertTrue(otherNode.loadActiveMutes(now, 1, 2).isEmpty());
    }

    @Test
    void moderationReceiptsAndPermanentMuteCommitAcrossConnections() throws Exception {
        Instant now = Instant.now();
        var mute = new ChatMuteRecord(receiver, "Moderator", "reason", now, null);
        var receipt = new ChatModerationReceipt(UUID.randomUUID(), receiver, "Receiver", "MUTE", "Moderator",
            "reason", "node-a", now, null);
        repository.saveMute(mute, receipt);
        var secondNode = openRepository();
        assertNull(secondNode.loadMute(receiver).orElseThrow().expiresAt());
        assertEquals(receipt.id(), secondNode.moderationHistory("receiver", 10).get(0).id());
        var unmute = new ChatModerationReceipt(UUID.randomUUID(), receiver, "Receiver", "UNMUTE", "Moderator2",
            "", "node-b", now.plusMillis(1), null);
        secondNode.deleteMute(receiver, unmute);
        assertTrue(repository.loadMute(receiver).isEmpty());
        assertEquals("node-b", repository.findModerationReceipt(unmute.id()).orElseThrow().node());
    }

    private RedPacket packet() {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        return new RedPacket("1234567890abcdef", RedPacketType.NORMAL, sender.toString(), "Sender", "Sender",
            "staff", "money", new BigDecimal("10"), new BigDecimal("10"),
            List.of(new BigDecimal("4"), new BigDecimal("6")), 2, 0, "", "", false,
            RedPacket.STATUS_ACTIVE, now, now.plusSeconds(120), "node-a", "STAFF|true|0.0|staff.read", true);
    }

    private void sql(String sql) throws SQLException {
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
