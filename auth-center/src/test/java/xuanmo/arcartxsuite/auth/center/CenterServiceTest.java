package xuanmo.arcartxsuite.auth.center;

import com.google.gson.JsonObject;
import java.net.URI;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;
import static org.junit.jupiter.api.Assertions.*;

class CenterServiceTest {
    @Test void fullMutationJournalDoesNotBlockActiveLeaseChecks() throws Exception {
        service=new CenterService(new CenterConfiguration(25600,configuration.database(),configuration.realms(),configuration.nodes(),null,120000,1),connections);
        var verified=password(true); var admitted=service.execute(node,exchange(verified.state().ticket()));
        var data=new JsonObject(); data.addProperty("password","correct-password"); data.addProperty("newPassword","replacement-password");
        assertEquals("NODE_COMMAND_CAPACITY",service.execute(node,request(Operation.CHANGE_PASSWORD,data)).code());
        var check=new JsonObject(); check.addProperty("session",admitted.state().session().toString());
        assertTrue(service.execute(node,request(Operation.CHECK,check)).success());
    }
    @Test void slowPasswordVerificationCannotBlockRevocationAndCannotCommitAcrossIt() throws Exception {
        var ready=password(true); service.execute(node,exchange(ready.state().ticket()));
        var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        var hasher=org.mockito.Mockito.mock(xuanmo.arcartxsuite.auth.engine.Argon2Passwords.class);
        org.mockito.Mockito.when(hasher.verify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->{
            entered.countDown(); if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout"); return true;
        });
        org.mockito.Mockito.when(hasher.hash(org.mockito.ArgumentMatchers.any())).thenReturn("replacement-hash");
        service=new CenterService(configuration,connections,hasher);
        var changing=new JsonObject(); changing.addProperty("password","correct-password"); changing.addProperty("newPassword","replacement-password");
        var command=request(Operation.CHANGE_PASSWORD,changing);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var result=executor.submit(()->service.execute(node,command));
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            JsonObject revoke=new JsonObject(); revoke.addProperty("account",ready.state().account().toString());
            var control=executor.submit(()->service.execute(node,request(Operation.REVOKE,revoke)));
            assertTrue(control.get(1,java.util.concurrent.TimeUnit.SECONDS).success());
            release.countDown();
            assertEquals("STALE_CREDENTIALS",result.get(2,java.util.concurrent.TimeUnit.SECONDS).code());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void bindingsArePagedBeyondOneThousandWithoutDroppingRows() throws Exception {
        var store=new JdbcAuthStore(connections,"realm");
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            for(int index=0;index<1100;index++) {
                var account=store.createIdentity(c,"Player"+index,UUID.randomUUID(),"hash","local-password");
                store.bind(c,account,"Player"+index,123450000L+index);
            }
            c.commit();
        }
        Set<UUID> seen=new HashSet<>(); String after="";
        do {
            var data=new JsonObject(); data.addProperty("after",after);
            var page=service.execute(node,request(Operation.BINDINGS,data));
            assertTrue(page.success()); assertTrue(page.bindings().size()<=256);
            for(var row:page.bindings()) assertTrue(seen.add(row.accountId()));
            after=page.value();
        } while(!after.isEmpty());
        assertEquals(1100,seen.size());
    }
    @TempDir Path directory;
    private JdbcAuthStore.Connections connections;
    private CenterConfiguration configuration;
    private CenterConfiguration.Node node;
    private CenterService service;
    private Context context;
    @BeforeEach void setup() throws Exception {
        connections=()->DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("center.sqlite"));
        node=new CenterConfiguration.Node("lobby","realm","lobby-node",new byte[32],Set.of("auth","binding-read","binding-write","account-revoke"));
        var policy=new Policy(8,128,true,"1",List.of("条款"),false,"Asia/Shanghai",300,true,false);
        configuration=new CenterConfiguration(25600,directory.resolve("center.sqlite"),Map.of("realm",policy),Map.of(node.id(),node),null,120000,1000000);
        service=new CenterService(configuration,connections);
        context=new Context("realm","lobby-node",UUID.randomUUID(),"Alice",UUID.randomUUID());
    }
    private Request request(Operation operation,JsonObject data) { return new Request(1,UUID.randomUUID(),operation,context,data); }
    private Reply password(boolean register) throws Exception {
        JsonObject data=new JsonObject(); data.addProperty("password","correct-password"); data.addProperty("register",register); data.addProperty("agreed",true); data.addProperty("termsVersion","1");
        return service.execute(node,request(Operation.PASSWORD,data));
    }
    private Request exchange(String token) { JsonObject data=new JsonObject(); data.addProperty("ticket",token); return request(Operation.EXCHANGE,data); }
    private void enableExternal(boolean passwords) throws Exception {
        URI issuer=URI.create("https://idp.example");
        var provider=new ExternalConfiguration.Provider(issuer,issuer.resolve("/authorize"),issuer.resolve("/token"),"center","test","test",null);
        var external=new ExternalConfiguration(25601,URI.create("https://center.example"),Map.of("realm",provider),Map.of());
        var policy=new Policy(8,128,true,"1",List.of("条款"),false,"Asia/Shanghai",300,passwords,true);
        service=new CenterService(new CenterConfiguration(25600,configuration.database(),Map.of("realm",policy),configuration.nodes(),external,120000,1000000),connections);
    }
    private ExternalPrompt prompt(Operation operation) throws Exception {
        Reply reply=service.execute(node,request(operation,new JsonObject()));
        assertEquals("EXTERNAL_PENDING",reply.code());
        return JSON.fromJson(reply.value(),ExternalPrompt.class);
    }
    private Reply confirm(ExternalPrompt prompt) throws Exception {
        JsonObject data=new JsonObject(); data.addProperty("pairing",prompt.requestId().toString());
        return service.execute(node,request(Operation.EXTERNAL_CONFIRM,data));
    }
    @Test void websiteApprovalRequiresOriginalConnectionAndGameConfirmationBeforeAdmission() throws Exception {
        enableExternal(false);
        var pending=prompt(Operation.OPEN);
        var pairing=service.lookupPairing("realm",pending.userCode());
        assertTrue(service.decidePairing(pairing,new OidcLogin.Identity("https://idp.example","website-alice"),true));
        assertEquals("EXTERNAL_APPROVED",service.execute(node,request(Operation.OPEN,new JsonObject())).code());
        assertNull(new JdbcAuthStore(connections,"realm").findByProfile(context.profile()));
        Context original=context;
        context=new Context(original.realm(),original.audience(),original.profile(),original.name(),UUID.randomUUID());
        assertEquals("EXTERNAL_NOT_APPROVED",confirm(pending).code());
        context=original;
        var verified=confirm(pending);
        assertEquals("oidc",verified.state().provider());
        assertEquals(Set.of("TERMS"),verified.state().requirements());
        assertEquals("",verified.state().ticket());
        assertFalse(confirm(pending).success());
        JsonObject terms=new JsonObject(); terms.addProperty("version","1");
        var ready=service.execute(node,request(Operation.TERMS,terms));
        assertTrue(service.execute(node,exchange(ready.state().ticket())).success());
    }
    @Test void sameGameNameCannotLinkWithoutRecentLocalPasswordOwnership() throws Exception {
        enableExternal(true);
        var verified=password(true);
        var admitted=service.execute(node,exchange(verified.state().ticket()));
        Context original=context;
        context=new Context(original.realm(),original.audience(),original.profile(),original.name(),UUID.randomUUID());
        var pending=prompt(Operation.OPEN);
        var identity=new OidcLogin.Identity("https://idp.example","website-alice");
        assertTrue(service.decidePairing(service.lookupPairing("realm",pending.userCode()),identity,true));
        assertEquals("IDENTITY_LINK_REQUIRED",confirm(pending).code());
        context=original;
        var link=prompt(Operation.EXTERNAL_LINK);
        assertTrue(service.decidePairing(service.lookupPairing("realm",link.userCode()),identity,true));
        assertEquals("EXTERNAL_LINKED",confirm(link).code());
        JsonObject check=new JsonObject(); check.addProperty("session",admitted.state().session().toString());
        assertFalse(service.execute(node,request(Operation.CHECK,check)).success());
        var reopened=service.execute(node,request(Operation.OPEN,new JsonObject()));
        assertEquals("oidc",reopened.state().provider());
        assertEquals(admitted.state().account(),reopened.state().account());
        assertTrue(reopened.state().securityVersion()>admitted.state().securityVersion());
    }
    @Test void verificationIssuesBoundTicketBeforeItCanAdmit() throws Exception {
        Reply verified=password(true);
        assertTrue(verified.success()); assertNotNull(verified.state().ticket()); assertNull(verified.state().session());
        Request redeem=exchange(verified.state().ticket());
        Reply admitted=service.execute(node,redeem);
        assertTrue(admitted.success()); assertNotNull(admitted.state().session());
        assertTrue(admitted.state().leaseMillis()>0 && admitted.state().leaseMillis()<=15_000);
        assertEquals(admitted.state().session(),service.execute(node,redeem).state().session());
        assertFalse(service.execute(node,exchange(verified.state().ticket())).success());
    }
    @Test void ticketCannotMoveToAnotherPhysicalConnectionOrAudience() throws Exception {
        Reply verified=password(true);
        Context original=context;
        context=new Context(original.realm(),original.audience(),original.profile(),original.name(),UUID.randomUUID());
        assertFalse(service.execute(node,exchange(verified.state().ticket())).success());
        context=new Context(original.realm(),"wrong-node",original.profile(),original.name(),original.connection());
        assertThrows(SecurityException.class,()->service.execute(node,exchange(verified.state().ticket())));
    }
    @Test void revocationInvalidatesBothOutstandingTicketsAndActiveLeases() throws Exception {
        Reply verified=password(true); Reply admitted=service.execute(node,exchange(verified.state().ticket()));
        Context first=context;
        Context second=new Context(first.realm(),first.audience(),first.profile(),first.name(),UUID.randomUUID());
        context=second; Reply outstanding=password(false); assertFalse(outstanding.state().ticket().isBlank());
        context=first;
        JsonObject revoke=new JsonObject(); revoke.addProperty("account",verified.state().account().toString());
        assertTrue(service.execute(node,request(Operation.REVOKE,revoke)).success());
        JsonObject check=new JsonObject(); check.addProperty("session",admitted.state().session().toString());
        assertFalse(service.execute(node,request(Operation.CHECK,check)).success());
        context=second; assertFalse(service.execute(node,exchange(outstanding.state().ticket())).success());
    }
    @Test void consumedTicketsAndNonceClaimsSurviveCenterRestart() throws Exception {
        Reply verified=password(true); Request exchange=exchange(verified.state().ticket());
        Reply admitted=service.execute(node,exchange);
        UUID nonce=UUID.randomUUID(); assertTrue(service.claimNonce(node.id(),nonce));
        var restarted=new CenterService(configuration,connections);
        assertFalse(restarted.claimNonce(node.id(),nonce));
        assertEquals(admitted.state().session(),restarted.execute(node,exchange).state().session());
        assertFalse(restarted.execute(node,exchange(verified.state().ticket())).success());
    }
    @Test void duplicateMutationIsFencedAndRefreshCanReadBackItsOutcome() throws Exception {
        JsonObject data=new JsonObject(); data.addProperty("password","correct-password"); data.addProperty("register",true); data.addProperty("agreed",true); data.addProperty("termsVersion","1");
        Request register=request(Operation.PASSWORD,data);
        assertTrue(service.execute(node,register).success());
        assertEquals("OUTCOME_UNKNOWN",service.execute(node,register).code());
        Reply refresh=service.execute(node,request(Operation.OPEN,new JsonObject()));
        assertTrue(refresh.success()); assertNotNull(refresh.state().account()); assertNotNull(refresh.state().ticket());
    }
    @Test void nodeScopesPreventPrivilegedOperations() throws Exception {
        var restricted=new CenterConfiguration.Node(node.id(),node.realm(),node.audience(),node.key(),Set.of("auth"));
        JsonObject page=new JsonObject(); page.addProperty("after","");
        assertEquals("NODE_SCOPE_DENIED",service.execute(restricted,request(Operation.BINDINGS,page)).code());
        JsonObject revoke=new JsonObject(); revoke.addProperty("account",UUID.randomUUID().toString());
        assertEquals("NODE_SCOPE_DENIED",service.execute(restricted,request(Operation.REVOKE,revoke)).code());
    }
    @Test void canonicalBindingPolicyPreventsTicketIssuanceUntilConfirmed() throws Exception {
        Policy bindingPolicy=new Policy(8,128,true,"1",List.of("条款"),true,"Asia/Shanghai",300,true,false);
        var bindingConfig=new CenterConfiguration(25600,configuration.database(),Map.of("realm",bindingPolicy),configuration.nodes(),null,120000,1000000);
        service=new CenterService(bindingConfig,connections);
        Reply verified=password(true);
        assertEquals(Set.of("QQ_BINDING"),verified.state().requirements()); assertEquals("",verified.state().ticket());
        JsonObject requestCode=new JsonObject(); requestCode.addProperty("qq",123456789L); requestCode.addProperty("name",context.name());
        Reply code=service.execute(node,request(Operation.REQUEST_BINDING,requestCode)); assertTrue(code.success());
        JsonObject confirm=new JsonObject(); confirm.addProperty("code",code.value());
        Reply bound=service.execute(node,request(Operation.BIND,confirm));
        assertTrue(bound.success()); assertFalse(bound.state().ticket().isEmpty());
        assertTrue(service.execute(node,exchange(bound.state().ticket())).success());
    }
    private OutboxPage outbox(CenterService center,long cursor) throws Exception {
        JsonObject data=new JsonObject(); data.addProperty("cursor",cursor);
        Reply reply=center.execute(node,request(Operation.OUTBOX,data));
        assertTrue(reply.success()); return JSON.fromJson(reply.value(),OutboxPage.class);
    }
    @Test void durableCursorRestartsWithoutReplayingAcknowledgedEvents() throws Exception {
        Reply verified=password(true);
        assertTrue(service.execute(node,exchange(verified.state().ticket())).success());
        OutboxPage page=outbox(service,0);
        assertFalse(page.gap());
        assertEquals(List.of(EventType.ACCOUNT_CREATED,EventType.TERMS_ACCEPTED,EventType.ADMISSION_GRANTED,EventType.FIRST_ADMISSION),
            page.events().stream().map(OutboxEvent::type).toList());
        assertTrue(outbox(service,page.cursor()).events().isEmpty());
        var restarted=new CenterService(configuration,connections);
        assertEquals(page.cursor(),outbox(restarted,-1).cursor());
        assertTrue(outbox(restarted,-1).events().isEmpty());
    }
    @Test void prunedCursorRequiresAuthoritativeResync() throws Exception {
        password(true);
        try(var c=connections.open(); var s=c.createStatement()) { s.executeUpdate("UPDATE axs_center_outbox_v1 SET created=0"); }
        var restarted=new CenterService(configuration,connections);
        OutboxPage gap=outbox(restarted,0);
        assertTrue(gap.gap()); assertTrue(gap.cursor()>0); assertTrue(gap.events().isEmpty());
    }
    @Test void outboxFailureRollsBackTicketConsumptionAndSessionCreation() throws Exception {
        Reply verified=password(true); Request redeem=exchange(verified.state().ticket());
        try(var c=connections.open(); var s=c.createStatement()) { s.executeUpdate("DROP TABLE axs_center_outbox_v1"); }
        assertThrows(java.sql.SQLException.class,()->service.execute(node,redeem));
        var restarted=new CenterService(configuration,connections);
        Reply admitted=restarted.execute(node,redeem);
        assertTrue(admitted.success()); assertNotNull(admitted.state().session());
    }
    @Test void gamePasswordsRequireCenterOptIn() throws Exception {
        Policy disabled=new Policy(8,128,true,"1",List.of("条款"),false,"Asia/Shanghai",300,false,false);
        service=new CenterService(new CenterConfiguration(25600,configuration.database(),Map.of("realm",disabled),configuration.nodes(),null,120000,1000000),connections);
        assertEquals("PASSWORD_ENTRY_DISABLED",service.execute(node,request(Operation.OPEN,new JsonObject())).code());
        assertEquals("PASSWORD_ENTRY_DISABLED",password(true).code());
    }
    @Test void staleTermsConsentCannotBeRecordedAsTheNewVersion() throws Exception {
        JsonObject data=new JsonObject(); data.addProperty("password","correct-password"); data.addProperty("register",true);
        data.addProperty("agreed",true); data.addProperty("termsVersion","0");
        assertEquals("TERMS_REQUIRED",service.execute(node,request(Operation.PASSWORD,data)).code());
        assertFalse(service.execute(node,request(Operation.OPEN,new JsonObject())).state().registered());
    }
}
