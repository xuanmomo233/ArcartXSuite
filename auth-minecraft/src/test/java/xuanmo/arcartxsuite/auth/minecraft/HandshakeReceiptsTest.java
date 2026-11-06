package xuanmo.arcartxsuite.auth.minecraft;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeReceiptsTest {
    private YggdrasilVerifier.Result verified(UUID id) {
        return new YggdrasilVerifier.Result(YggdrasilVerifier.Status.VERIFIED,"microsoft",id,"Alice","{}");
    }
    @Test void claimIsBoundToConnectionRealmAndIdentityWithIdempotentRetry() {
        var receipts=new HandshakeReceipts(Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"),ZoneOffset.UTC));
        UUID player=UUID.randomUUID(),connection=UUID.randomUUID();
        assertTrue(receipts.observe(verified(player),"abc"));
        var first=receipts.claim(player,"Alice",connection,"realm"); assertNotNull(first);
        assertEquals(first,receipts.claim(player,"Alice",connection,"realm"));
        assertNull(receipts.claim(player,"Alice",connection,"other"));
        assertNull(receipts.claim(player,"Alice",UUID.randomUUID(),"realm"));
    }
    @Test void multiplePendingHandshakesAreRejectedRatherThanSelectingByUuid() {
        var receipts=new HandshakeReceipts(); UUID player=UUID.randomUUID();
        receipts.observe(verified(player),"abc"); receipts.observe(verified(player),"def");
        assertNull(receipts.claim(player,"Alice",UUID.randomUUID(),"realm"));
    }
    @Test void profileMetadataCannotCreateHandshakeReceipt() {
        var receipts=new HandshakeReceipts();
        assertThrows(IllegalArgumentException.class,()->receipts.observe(
            new YggdrasilVerifier.Result(YggdrasilVerifier.Status.NO_MATCH,"microsoft",UUID.randomUUID(),"Alice","{}"),"abc"));
    }
    @Test void expiredReceiptsAndClaimsCannotBeReused() {
        var now=new java.util.concurrent.atomic.AtomicLong(Instant.parse("2026-11-06T00:00:00Z").toEpochMilli());
        Clock clock=new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        var receipts=new HandshakeReceipts(clock); UUID profile=UUID.randomUUID(),connection=UUID.randomUUID();
        receipts.observe(verified(profile),"abc");
        assertNotNull(receipts.claim(profile,"Alice",connection,"realm"));
        now.addAndGet(30_000);
        assertNull(receipts.claim(profile,"Alice",connection,"realm"));
    }
}
