package xuanmo.arcartxsuite.auth.center;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CenterLedgerCapacityTest {
    @TempDir Path directory;
    @Test void oneNodeCannotConsumeAnotherNodesReplayCapacityAndRestartStillRejectsReplay() throws Exception {
        xuanmo.arcartxsuite.auth.engine.JdbcAuthStore.Connections connections=()->DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("center.sqlite"));
        var ledger=new CenterLedger(connections); ledger.initialize();
        UUID first=UUID.randomUUID();
        assertTrue(ledger.claimNonce("lobby",first,2));
        assertTrue(ledger.claimNonce("lobby",UUID.randomUUID(),2));
        assertFalse(ledger.claimNonce("lobby",UUID.randomUUID(),2));
        assertTrue(ledger.claimNonce("survival",UUID.randomUUID(),2));
        var restarted=new CenterLedger(connections); restarted.initialize();
        assertFalse(restarted.claimNonce("lobby",first,10));
    }
    @Test void replayIsStillRejectedAtTheInclusiveTimestampWindowBoundary() throws Exception {
        var clock=new java.util.concurrent.atomic.AtomicLong();
        var ledger=new CenterLedger(()->DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("boundary.sqlite")),clock::get);
        ledger.initialize(); UUID nonce=UUID.randomUUID();
        assertTrue(ledger.claimNonce("node",nonce,10));
        clock.set(60_000);
        assertFalse(ledger.claimNonce("node",nonce,10));
        clock.set(60_001);
        assertFalse(ledger.claimNonce("node",nonce,10));
        clock.set(61_000);
        assertTrue(ledger.claimNonce("node",nonce,10));
    }
}
