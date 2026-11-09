package xuanmo.arcartxsuite.auth.engine;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JdbcAuthStoreTest {
    @Test void concurrentFailuresCannotOverwriteOneAnothersAttemptCounters() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        var workers=java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready=new java.util.concurrent.CountDownLatch(8); var start=new java.util.concurrent.CountDownLatch(1);
        var results=new java.util.ArrayList<java.util.concurrent.Future<?>>();
        long now=System.currentTimeMillis();
        try {
            for(int index=0;index<8;index++) results.add(workers.submit(()->{
                ready.countDown(); start.await(); store.failure(account,now,20,60000); return null;
            }));
            assertTrue(ready.await(2,java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            for(var result:results) result.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(8,store.findById(account.id()).failures());
        } finally { start.countDown(); workers.shutdownNow(); }
    }
    @Test void securityVersionBatchesObserveRevocationAndCannotGrowWithoutBound() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        assertEquals(1L,store.versions(java.util.List.of(account.id())).get(account.id()).longValue());
        store.invalidate(account.id());
        assertEquals(2L,store.versions(java.util.List.of(account.id())).get(account.id()).longValue());
        assertThrows(IllegalArgumentException.class,()->store.versions(java.util.List.of()));
        assertThrows(IllegalArgumentException.class,()->store.versions(java.util.Collections.nCopies(257,account.id())));
    }
    @Test void repeatedTransportLoginDoesNotRewriteAnUnchangedName() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        assertFalse(store.renameVerifiedProfile(account,"Alice"));
        assertTrue(store.renameVerifiedProfile(account,"AliceNew"));
        assertFalse(store.renameVerifiedProfile(account,"AliceNew"));
    }
    @TempDir Path temp;
    private JdbcAuthStore store(String realm) throws Exception {
        String url="jdbc:sqlite:"+temp.resolve("auth.sqlite");
        var store=new JdbcAuthStore(()->DriverManager.getConnection(url),realm); store.initialize(); return store;
    }
    @Test void bindingsBelongToBackendAndUnbindingInvalidatesCredentials() throws Exception {
        var store=store("realm");
        var alice=store.create("Alice",UUID.randomUUID(),"hash");
        store.bind(alice,"Alice",123456);
        assertEquals(alice.id(),store.bindings().get(0).accountId());
        assertEquals(1,store.bindings().size());
        store.unbind(alice.id());
        assertTrue(store.bindings().isEmpty());
        assertTrue(store.find("Alice").version()>alice.version());
        assertFalse(store.admit(alice));
        assertFalse(store.changePassword(alice,"replacement"));
    }
    @Test void qqUniquenessAndRealmIsolationAreEnforced() throws Exception {
        var first=store("first"); var second=store("second");
        var alice=first.create("Alice",UUID.randomUUID(),"hash");
        var bob=first.create("Bob",UUID.randomUUID(),"hash");
        first.bind(alice,"Alice",123456);
        assertThrows(java.sql.SQLException.class,()->first.bind(bob,"Bob",123456));
        assertNull(second.find("Alice")); assertTrue(second.bindings().isEmpty());
    }
    @Test void firstAdmissionRemainsPendingUntilAcknowledged() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        assertTrue(store.admit(account)); assertFalse(store.find("Alice").firstAdmitted());
        store.acknowledgeFirst(account); assertTrue(store.find("Alice").firstAdmitted());
    }
    @Test void dailyPresenceSurvivesRestartAndTracksDelivery() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        assertTrue(store.pendingDay(account.id(),"2026-11-05"));
        var restarted=store("realm");
        assertTrue(restarted.pendingDay(account.id(),"2026-11-05"));
        restarted.acknowledgeDay(account.id(),"2026-11-05");
        assertFalse(store.pendingDay(account.id(),"2026-11-05"));
        assertTrue(store.pendingDay(account.id(),"2026-11-06"));
    }
    @Test void undeliveredBusinessDaysReplayInOrderAfterRestart() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        store.pendingDay(account.id(),"2026-10-01"); store.pendingDay(account.id(),"2026-11-08");
        var restarted=store("realm");
        assertEquals("2026-10-01",restarted.nextPendingDay(account.id(),"2026-11-08"));
        restarted.acknowledgeDay(account.id(),"2026-10-01");
        assertEquals("2026-11-08",restarted.nextPendingDay(account.id(),"2026-11-08"));
        restarted.acknowledgeDay(account.id(),"2026-11-08");
        assertNull(restarted.nextPendingDay(account.id(),"2026-11-08"));
    }
    @Test void verifiedSubjectSurvivesRenameWithoutMergingDifferentProvider() throws Exception {
        var store=store("realm"); UUID profile=UUID.randomUUID();
        var account=store.createIdentity("Alice",profile,"","native/microsoft");
        store.bind(account,"Alice",123456);
        store.renameVerifiedProfile(account,"AliceNew");
        assertNull(store.find("Alice"));
        assertEquals(account.id(),store.find("AliceNew").id());
        assertEquals(account.id(),store.findIdentity(profile,"native/microsoft").id());
        assertNull(store.findIdentity(profile,"native/littleskin"));
        assertEquals("AliceNew",store.bindings().get(0).profileName());
    }
    @Test void revokedCredentialsCannotRecordNewConsent() throws Exception {
        var store=store("realm"); var account=store.create("Alice",UUID.randomUUID(),"hash");
        store.recordTerms(account,"1");
        store.recordTerms(account,"1");
        assertTrue(store.acceptedTerms(account.id(),"1"));
        store.invalidate(account.id());
        assertThrows(java.sql.SQLException.class,()->store.recordTerms(account,"2"));
        assertFalse(store.acceptedTerms(account.id(),"2"));
        var refreshed=store.findById(account.id());
        store.recordTerms(refreshed,"2");
        assertTrue(store.acceptedTerms(account.id(),"2"));
    }
}
