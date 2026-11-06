package xuanmo.arcartxsuite.auth.engine;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JdbcAuthStoreTest {
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
