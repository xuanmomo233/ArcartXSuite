package xuanmo.arcartxsuite.auth.center;

import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import xuanmo.arcartxsuite.auth.engine.*;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Single authoritative SQLite writer for P3. No Bukkit, UI, proxy, or legacy account dependencies. */
final class CenterService {
    private final CenterConfiguration config;
    private final Map<String,JdbcAuthStore> stores=new HashMap<>();
    private final CenterLedger ledger;
    private final CenterOutbox outbox;
    private final GameExternalStore external;
    private final Argon2Passwords passwords;
    private final Semaphore passwordSlots=new Semaphore(2);
    private final Map<String,PasswordWork> passwordWork=new HashMap<>();
    private record PasswordWork(JdbcAuthStore.Account account,CenterLedger.Flow flow,char[] password,char[] replacement,
            boolean register,String terms) implements AutoCloseable {
        @Override public void close() { Arrays.fill(password,'\0'); Arrays.fill(replacement,'\0'); }
    }
    private static final class RejectedPassword extends RuntimeException {
        final Reply reply;
        RejectedPassword(Reply reply) { this.reply=reply; }
    }
    private long nextCleanup=System.nanoTime();
    CenterService(CenterConfiguration config,JdbcAuthStore.Connections connections) throws Exception {
        this(config,connections,new Argon2Passwords());
    }
    CenterService(CenterConfiguration config,JdbcAuthStore.Connections connections,Argon2Passwords passwords) throws Exception {
        this.passwords=Objects.requireNonNull(passwords);
        this.config=config; ledger=new CenterLedger(connections,System::currentTimeMillis,config.commandCapacityPerNode()); ledger.initialize(); outbox=new CenterOutbox(connections);
        external=config.external()==null?null:new GameExternalStore(connections);
        for(String realm:config.realms().keySet()) {
            JdbcAuthStore store=new JdbcAuthStore(connections,realm); store.initialize(); stores.put(realm,store);
        }
    }
    synchronized boolean claimNonce(String node,UUID nonce) throws java.sql.SQLException { return ledger.claimNonce(node,nonce,config.nonceCapacityPerNode()); }
    Reply execute(CenterConfiguration.Node node,Request request) throws Exception {
        validate(node,request);
        String scope=switch(request.operation()) {
            case BINDINGS -> "binding-read";
            case REQUEST_BINDING,UNBIND_QQ,UNBIND_PROFILE -> "binding-write";
            case REVOKE -> "account-revoke";
            default -> "auth";
        };
        if(!node.scopes().contains(scope)) return Reply.fail("NODE_SCOPE_DENIED");
        boolean journal=switch(request.operation()) { case OPEN,CHECK,BINDINGS,PRESENCE,EXCHANGE,OUTBOX -> false; default -> true; };
        PasswordWork prepared;
        String operationKey=node.id()+":"+request.context().connection();
        synchronized(this) {
        if(System.nanoTime()-nextCleanup>=0) {
            ledger.cleanup();
            outbox.prune();
            if(external!=null) external.prune();
            nextCleanup=System.nanoTime()+60_000_000_000L;
        }
        if(journal) {
            var status=ledger.beginCommand(node,request);
            if(status==CenterLedger.CommandStatus.REPLAYED) return Reply.fail("OUTCOME_UNKNOWN");
            if(status==CenterLedger.CommandStatus.FULL) return Reply.fail("NODE_COMMAND_CAPACITY");
        }
        if(request.operation()!=Operation.PASSWORD && request.operation()!=Operation.CHANGE_PASSWORD) {
            if(request.operation()==Operation.CLOSE) passwordWork.remove(operationKey);
            Reply reply;
            try { reply=dispatch(node,request); }
            catch(SecurityException e) { reply=Reply.fail("CREDENTIALS_REJECTED"); }
            return finish(request,reply,journal);
        }
        if(!passwordSlots.tryAcquire()) return finish(request,Reply.fail("BUSY"),journal);
        try { prepared=preparePassword(node,request); }
        catch(RejectedPassword e) { passwordSlots.release(); return finish(request,e.reply,journal); }
        catch(Exception | LinkageError e) { passwordSlots.release(); throw e; }
        passwordWork.put(operationKey,prepared);
        }
        // Password computation owns no service/SQLite monitor. Two RPC workers remain available to renew/revoke.
        try(prepared) {
            boolean verified=prepared.register() || passwords.verify(prepared.password(),prepared.account().hash());
            String hash=verified && (prepared.register() || request.operation()==Operation.CHANGE_PASSWORD)
                ?passwords.hash(prepared.register()?prepared.password():prepared.replacement()):null;
            synchronized(this) {
                Reply reply=passwordWork.get(operationKey)==prepared?commitPassword(node,request,prepared,verified,hash):Reply.fail("STALE_CONNECTION");
                return finish(request,reply,journal);
            }
        } finally {
            synchronized(this) { passwordWork.remove(operationKey,prepared); }
            passwordSlots.release();
        }
    }
    private Reply finish(Request request,Reply reply,boolean journal) throws java.sql.SQLException {
        if(journal) ledger.finishCommand(request,reply.code());
        return reply;
    }
    private static void rejectPassword(String code) { throw new RejectedPassword(Reply.fail(code)); }
    private PasswordWork preparePassword(CenterConfiguration.Node node,Request request) throws Exception {
        var store=stores.get(node.realm()); var policy=config.realms().get(node.realm());
        if(!policy.gamePasswordEnabled()) { rejectPassword("PASSWORD_ENTRY_DISABLED"); }
        char[] password=secret(request.data(),"password"),replacement=new char[0];
        boolean accepted=false;
        try {
            var account=store.find(request.context().name());
            boolean register=request.operation()==Operation.PASSWORD && CenterConfiguration.bool(request.data(),"register");
            String terms=""; CenterLedger.Flow flow=null;
            if(request.operation()==Operation.PASSWORD) {
                if(password.length<(register?policy.minLength():1) || password.length>policy.maxLength()) rejectPassword("PASSWORD_LENGTH");
                terms=CenterConfiguration.string(request.data(),"termsVersion");
                if(policy.requireTerms() && (!CenterConfiguration.bool(request.data(),"agreed") || !policy.termsVersion().equals(terms)))
                    throw new RejectedPassword(new Reply(false,"TERMS_REQUIRED",null,policy,List.of(),""));
                if(register && account!=null) rejectPassword("CREDENTIALS_REJECTED");
            } else {
                replacement=secret(request.data(),"newPassword");
                if(password.length<1 || replacement.length<policy.minLength() || replacement.length>policy.maxLength()) rejectPassword("PASSWORD_LENGTH");
                flow=ledger.find(node.id(),request.context());
                if(!recentPassword(flow,account,request.context())) rejectPassword("INVALID_STATE");
            }
            if(!register && (account==null || !account.profile().equals(request.context().profile()) || !"local-password".equals(account.provider())
                    || account.blockedUntil()>System.currentTimeMillis())) rejectPassword("CREDENTIALS_REJECTED");
            accepted=true;
            return new PasswordWork(account,flow,password,replacement,register,terms);
        } finally { if(!accepted) { Arrays.fill(password,'\0'); Arrays.fill(replacement,'\0'); } }
    }
    private Reply commitPassword(CenterConfiguration.Node node,Request request,PasswordWork prepared,boolean verified,String hash) throws Exception {
        var store=stores.get(node.realm()); var policy=config.realms().get(node.realm()); var context=request.context();
        var current=store.find(context.name()); var expected=prepared.account();
        if(prepared.register()?current!=null:current==null || !current.id().equals(expected.id()) || current.version()!=expected.version()
                || !current.hash().equals(expected.hash())) return Reply.fail("STALE_CREDENTIALS");
        if(current!=null && current.blockedUntil()>System.currentTimeMillis()) return Reply.fail("CREDENTIALS_REJECTED");
        if(!verified) { store.failure(current,System.currentTimeMillis(),5,300_000); return Reply.fail("CREDENTIALS_REJECTED"); }
        if(request.operation()==Operation.CHANGE_PASSWORD) {
            var flow=ledger.find(node.id(),context);
            if(!Objects.equals(flow,prepared.flow()) || !recentPassword(flow,current,context)) return Reply.fail("STALE_CONNECTION");
            boolean changed=outbox.transaction(c->{
                if(!store.changePassword(c,current,hash)) return false;
                ledger.clearBindingCodes(c,node.realm(),current.id());
                CenterOutbox.append(c,node.realm(),current.id()+":security:"+(current.version()+1),EventType.PASSWORD_CHANGED,current,current.version()+1,null,"");
                return true;
            });
            return changed?Reply.ok("PASSWORD_CHANGED"):Reply.fail("STALE_CREDENTIALS");
        }
        var account=current;
        if(prepared.register()) account=outbox.transaction(c->{
            var created=store.createIdentity(c,context.name(),context.profile(),hash,"local-password");
            CenterOutbox.append(c,node.realm(),created.id()+":created",EventType.ACCOUNT_CREATED,created,created.version(),null,""); return created;
        });
        if(policy.requireTerms()) recordTerms(node.realm(),store,account,prepared.terms());
        if(external!=null) external.cancel(node.id(),context);
        return view(ledger.create(node.id(),context,account),account,store,policy);
    }
    private static void validate(CenterConfiguration.Node node,Request request) {
        if(request==null || request.version()!=1 || request.requestId()==null || request.operation()==null || request.context()==null || request.data()==null)
            throw new IllegalArgumentException("Invalid request");
        Context context=request.context();
        if(!node.realm().equals(context.realm()) || !node.audience().equals(context.audience()) || context.profile()==null
                || context.connection()==null || context.name()==null || !context.name().matches("[\\p{L}\\p{N}_]{1,16}")) throw new SecurityException("Node target mismatch");
        Set<String> allowed=switch(request.operation()) {
            case BINDINGS -> Set.of("after");
            case PASSWORD -> Set.of("password","register","agreed","termsVersion");
            case TERMS -> Set.of("version"); case BIND -> Set.of("code"); case EXCHANGE -> Set.of("ticket");
            case CHECK -> Set.of("session"); case CHANGE_PASSWORD -> Set.of("password","newPassword");
            case EXTERNAL_CONFIRM -> Set.of("pairing");
            case REQUEST_BINDING -> Set.of("qq","name"); case UNBIND_QQ -> Set.of("qq");
            case UNBIND_PROFILE -> Set.of("profile"); case REVOKE -> Set.of("account"); case ACK_PRESENCE -> Set.of("date"); case OUTBOX -> Set.of("cursor");
            default -> Set.of();
        };
        if(!allowed.equals(request.data().keySet())) throw new IllegalArgumentException("Missing or unknown operation fields");
    }
    private Reply dispatch(CenterConfiguration.Node node,Request request) throws Exception {
        var context=request.context(); var data=request.data();
        var store=stores.get(node.realm()); var policy=config.realms().get(node.realm());
        switch(request.operation()) {
            case OPEN: {
                var pairing=policy.externalEnabled()?external.byConnection(node.id(),context):null;
                if(pairing!=null && !"pending".equals(pairing.status())) {
                    if("approved".equals(pairing.status())) {
                        State state=pairing.link()?null:ready(policy,store.find(context.name())!=null,"READY").state();
                        return externalPrompt(node,pairing,policy,state);
                    }
                    external.cancel(node.id(),context);
                    return Reply.fail("EXTERNAL_DENIED");
                }
                if(pairing!=null && pairing.link()) return externalPrompt(node,pairing,policy,null);
                var flow=ledger.find(node.id(),context);
                if(flow!=null && flow.context().equals(context)) {
                    var account=store.findById(flow.account());
                    var lease=ledger.session(flow);
                    if(account!=null && methodAllowed(flow,policy) && account.version()==flow.version() && (flow.expires()>System.currentTimeMillis() || lease!=null && lease.expires()>System.currentTimeMillis()))
                        return view(flow,account,store,policy);
                    ledger.close(node.id(),context);
                }
                if(policy.externalEnabled()) {
                    if(pairing==null) pairing=external.create(node,context,store.findByProfile(context.profile()),false,
                        System.currentTimeMillis()+300_000);
                    return externalPrompt(node,pairing,policy,ready(policy,store.find(context.name())!=null,"READY").state());
                }
                if(!policy.gamePasswordEnabled()) return ready(policy,false,"PASSWORD_ENTRY_DISABLED");
                return ready(policy,store.find(context.name())!=null,"READY");
            }
            case EXTERNAL_LINK: {
                if(!policy.externalEnabled() || !policy.gamePasswordEnabled()) return Reply.fail("EXTERNAL_DISABLED");
                var flow=ledger.find(node.id(),context);
                var account=flow==null?null:store.findById(flow.account());
                if(!recentPassword(flow,account,context)) return Reply.fail("RECENT_PASSWORD_REQUIRED");
                var existing=external.byConnection(node.id(),context);
                if(existing!=null && existing.link())
                    return externalPrompt(node,existing,policy,null);
                external.cancel(node.id(),context);
                return externalPrompt(node,external.create(node,context,account,true,flow.expires()),policy,null);
            }
            case EXTERNAL_CONFIRM: {
                if(!policy.externalEnabled()) return Reply.fail("EXTERNAL_DISABLED");
                var pairing=external.byConnection(node.id(),context);
                if(pairing==null || !pairing.id().equals(UUID.fromString(CenterConfiguration.string(data,"pairing")))
                        || !"approved".equals(pairing.status())) return Reply.fail("EXTERNAL_NOT_APPROVED");
                try { return finishExternal(node,pairing,store,policy); }
                catch(SecurityException e) {
                    external.cancel(node.id(),context);
                    return Reply.fail("IDENTITY_LINK_REQUIRED");
                }
            }
            case BINDINGS: {
                String after=CenterConfiguration.string(data,"after");
                if(!after.isEmpty()) UUID.fromString(after);
                var page=store.bindingsPage(after,256);
                return new Reply(true,"READY",null,null,page.stream().map(value->new Binding(value.accountId(),value.profileId(),value.profileName(),value.qqId(),value.boundAt())).toList(),
                    page.size()==256?page.get(page.size()-1).accountId().toString():"");
            }
            case OUTBOX: {
                JsonElement cursor=data.get("cursor");
                if(!cursor.isJsonPrimitive() || !cursor.getAsJsonPrimitive().isNumber() || !cursor.getAsString().matches("-1|[0-9]{1,19}")) throw new IllegalArgumentException("Invalid outbox cursor");
                return new Reply(true,"OUTBOX",null,null,List.of(),JSON.toJson(outbox.page(node.id(),node.realm(),Long.parseLong(cursor.getAsString()))));
            }
            case REQUEST_BINDING: {
                long qq=qq(data); String name=CenterConfiguration.string(data,"name");
                if(!name.matches("[\\p{L}\\p{N}_]{1,16}")) return Reply.fail("INVALID_BINDING_REQUEST");
                var account=store.find(name);
                if(account==null || store.isBound(account.id()) || store.bindingByQq(qq)!=null) return Reply.fail("BINDING_CONFLICT");
                String code=ledger.bindingCode(node.realm(),account.id(),qq);
                if(code==null) return Reply.fail("BINDING_PENDING");
                return new Reply(true,"BINDING_CODE",null,null,List.of(),code);
            }
            case UNBIND_QQ,UNBIND_PROFILE: {
                var binding=request.operation()==Operation.UNBIND_QQ?store.bindingByQq(qq(data))
                    :store.bindingByProfile(UUID.fromString(CenterConfiguration.string(data,"profile")));
                if(binding==null) return Reply.fail("NOT_BOUND");
                var account=store.findById(binding.accountId());
                if(account==null) return Reply.fail("ACCOUNT_UNKNOWN");
                long updated=outbox.transaction(c->{
                    long version=store.unbind(c,account.id()); ledger.clearBindingCodes(c,node.realm(),account.id());
                    CenterOutbox.append(c,node.realm(),account.id()+":security:"+version,EventType.BINDING_REMOVED,account,version,null,""); return version;
                });
                return new Reply(true,"UNBOUND",null,null,List.of(new Binding(binding.accountId(),binding.profileId(),binding.profileName(),binding.qqId(),binding.boundAt())),Long.toString(updated));
            }
            case REVOKE: {
                var account=store.findById(UUID.fromString(CenterConfiguration.string(data,"account")));
                if(account==null) return Reply.fail("ACCOUNT_UNKNOWN");
                long updated=outbox.transaction(c->{
                    long version=store.invalidate(c,account.id()); ledger.clearBindingCodes(c,node.realm(),account.id());
                    CenterOutbox.append(c,node.realm(),account.id()+":security:"+version,EventType.ACCOUNT_REVOKED,account,version,null,""); return version;
                });
                return new Reply(true,"REVOKED",null,null,List.of(),Long.toString(updated));
            }
            case CLOSE: {
                var flow=ledger.find(node.id(),context);
                if(flow!=null && !flow.context().equals(context)) throw new SecurityException("Connection mismatch");
                if(external!=null) external.cancel(node.id(),context);
                ledger.close(node.id(),context); return Reply.ok("CLOSED");
            }
            default: break;
        }
        var flow=ledger.find(node.id(),context);
        if(flow==null && !policy.gamePasswordEnabled() && !policy.externalEnabled()) return Reply.fail("PASSWORD_ENTRY_DISABLED");
        if(flow==null || !flow.context().equals(context)) return Reply.fail("STALE_CONNECTION");
        if(!methodAllowed(flow,policy)) return Reply.fail("AUTH_METHOD_DISABLED");
        var account=store.findById(flow.account()); var lease=ledger.session(flow);
        if(account==null || account.version()!=flow.version()) return Reply.fail("REVOKED");
        boolean live=lease!=null && lease.expires()>System.currentTimeMillis();
        if(!live && flow.expires()<=System.currentTimeMillis()) return Reply.fail("STALE_CONNECTION");
        switch(request.operation()) {
            case TERMS -> recordTerms(node.realm(),store,account,matchingTerms(data,policy));
            case BIND -> {
                long qq=ledger.bindingQq(node.realm(),account.id(),CenterConfiguration.string(data,"code"));
                outbox.transaction(c->{
                    store.bind(c,account,context.name(),qq); ledger.clearBindingCodes(c,node.realm(),account.id());
                    CenterOutbox.append(c,node.realm(),account.id()+":binding:"+account.version(),EventType.BINDING_ADDED,account,account.version(),null,""); return null;
                });
            }
            case EXCHANGE -> {
                if(!requirements(account,store,policy).isEmpty()) return Reply.fail("REQUIREMENTS");
                lease=ledger.exchange(flow,CenterConfiguration.string(data,"ticket"),request.requestId(),store,account);
                return admitted(flow,account,policy,lease);
            }
            case CHECK -> {
                if(!requirements(account,store,policy).isEmpty()) return Reply.fail("REQUIREMENTS");
                lease=ledger.renew(flow,UUID.fromString(CenterConfiguration.string(data,"session")));
                return admitted(flow,account,policy,lease);
            }
            case PRESENCE -> {
                if(!live) return Reply.fail("INVALID_STATE");
                String date=LocalDate.now(ZoneId.of(policy.timezone())).toString();
                outbox.transaction(c->{
                    if(!store.pendingDay(c,account.id(),date)) return false;
                    CenterOutbox.append(c,node.realm(),account.id()+":day:"+date,EventType.PRESENCE_DAY,account,account.version(),null,date); return true;
                });
                String next=store.nextPendingDay(account.id(),date);
                return new Reply(true,"PRESENCE",null,null,List.of(),(next==null?date:next)+":"+(next!=null));
            }
            case ACK_PRESENCE -> {
                if(!live) return Reply.fail("INVALID_STATE");
                LocalDate date=LocalDate.parse(CenterConfiguration.string(data,"date"));
                LocalDate today=LocalDate.now(ZoneId.of(policy.timezone()));
                if(date.isAfter(today)) return Reply.fail("INVALID_DATE");
                store.acknowledgeDay(account.id(),date.toString()); return Reply.ok("ACKNOWLEDGED");
            }
            case ACK_FIRST -> { if(!live) return Reply.fail("INVALID_STATE"); store.acknowledgeFirst(account); return Reply.ok("ACKNOWLEDGED"); }
            default -> throw new IllegalArgumentException("Invalid operation");
        }
        return view(flow,account,store,policy);
    }
    private boolean methodAllowed(CenterLedger.Flow flow,Policy policy) {
        if("local-password".equals(flow.method())) return policy.gamePasswordEnabled();
        return policy.externalEnabled() && flow.method().equals("oidc:"+config.external().providers().get(flow.context().realm()).issuer());
    }
    private static String provider(CenterLedger.Flow flow) {
        return "local-password".equals(flow.method())?"local-password":"oidc";
    }
    private boolean recentPassword(CenterLedger.Flow flow,JdbcAuthStore.Account account,Context context) throws Exception {
        if(flow==null || account==null || !flow.context().equals(context) || !"local-password".equals(flow.method())
                || flow.version()!=account.version() || flow.expires()<=System.currentTimeMillis()) return false;
        var lease=ledger.session(flow);
        return lease!=null && lease.expires()>System.currentTimeMillis();
    }
    private Reply externalPrompt(CenterConfiguration.Node node,GameExternalStore.Pairing pairing,Policy policy,State state) {
        String confirmation="approved".equals(pairing.status())?GameExternalStore.confirmation(pairing,pairing.identity()):"";
        var prompt=new ExternalPrompt(pairing.id(),config.external().url("/verify").toString()+"?realm="+node.realm(),
            GameExternalStore.code(node,pairing.id()),pairing.expires(),confirmation);
        return new Reply(true,confirmation.isEmpty()?"EXTERNAL_PENDING":"EXTERNAL_APPROVED",state,policy,List.of(),JSON.toJson(prompt));
    }
    synchronized GameExternalStore.Pairing lookupPairing(String realm,String code) throws Exception {
        return external.byCode(realm,code);
    }
    synchronized GameExternalStore.Ownership externalOwnership(String realm,OidcLogin.Identity identity) throws Exception {
        return external.ownership(realm,identity);
    }
    synchronized boolean decidePairing(GameExternalStore.Pairing pairing,OidcLogin.Identity identity,boolean approved) throws Exception {
        return external.decide(pairing,identity,approved);
    }
    synchronized boolean attachLauncher(GameExternalStore.Pairing pairing,OidcLogin.Identity identity,GameExternalStore.Ownership expected) throws Exception {
        return Objects.equals(expected,external.ownership(pairing.context().realm(),identity)) && external.decide(pairing,identity,true);
    }
    private Reply finishExternal(CenterConfiguration.Node node,GameExternalStore.Pairing pairing,JdbcAuthStore store,Policy policy) throws Exception {
        Context context=pairing.context();
        var identity=pairing.identity();
        if(!config.external().providers().get(node.realm()).issuer().toString().equals(identity.issuer()))
            throw new SecurityException("External issuer changed");
        var account=store.findByProfile(context.profile());
        var named=store.find(context.name());
        if(!Objects.equals(pairing.account(),account==null?null:account.id()) || account!=null && pairing.version()!=account.version()
                || named!=null && (account==null || !named.id().equals(account.id()))) throw new SecurityException("Account changed or name conflicts");
        var owner=external.ownership(node.realm(),identity);
        if(pairing.link()) {
            if(!policy.gamePasswordEnabled() || owner!=null || !recentPassword(ledger.find(node.id(),context),account,context))
                throw new SecurityException("Recent password ownership required");
        } else if(account==null ? owner!=null : owner==null || !owner.account().equals(account.id())) {
            throw new SecurityException("External identity not linked to profile");
        }
        var flow=outbox.transaction(c->{
            external.consume(c,pairing);
            var resolved=account;
            long version;
            if(resolved==null) {
                resolved=store.createIdentity(c,context.name(),context.profile(),"","oidc");
                external.link(c,node.realm(),identity,resolved.id());
                version=resolved.version();
                CenterOutbox.append(c,node.realm(),resolved.id()+":created",EventType.ACCOUNT_CREATED,resolved,version,null,"");
            } else if(pairing.link()) {
                external.link(c,node.realm(),identity,resolved.id());
                version=store.invalidate(c,resolved.id());
                ledger.clearBindingCodes(c,node.realm(),resolved.id());
                CenterOutbox.append(c,node.realm(),resolved.id()+":security:"+version,EventType.IDENTITY_LINKED,resolved,version,null,"");
            } else version=resolved.version();
            return ledger.create(c,node.id(),context,resolved.id(),version,"oidc:"+identity.issuer());
        });
        if(pairing.link()) return Reply.ok("EXTERNAL_LINKED");
        return view(flow,store.findById(flow.account()),store,policy);
    }
    private void recordTerms(String realm,JdbcAuthStore store,JdbcAuthStore.Account account,String version) throws java.sql.SQLException {
        outbox.transaction(c->{
            store.recordTerms(c,account,version);
            CenterOutbox.append(c,realm,account.id()+":terms:"+version,EventType.TERMS_ACCEPTED,account,account.version(),null,version); return null;
        });
    }
    private static Reply ready(Policy policy,boolean registered,String code) {
        return new Reply(true,code,new State(registered,null,0,"",Set.of(),null,"",null,0,false),policy,List.of(),"");
    }
    private static char[] secret(JsonObject data,String field) {
        String value=CenterConfiguration.string(data,field);
        if(value.length()>128) throw new IllegalArgumentException("Password too long");
        return value.toCharArray();
    }
    private static long qq(JsonObject data) {
        String value=data.get("qq").getAsString();
        if(!value.matches("[1-9][0-9]{4,14}")) throw new IllegalArgumentException("Invalid QQ identity");
        return Long.parseLong(value);
    }
    private static String matchingTerms(JsonObject data,Policy policy) {
        String value=CenterConfiguration.string(data,"version");
        if(!policy.requireTerms() || !policy.termsVersion().equals(value)) throw new SecurityException("Terms version mismatch");
        return value;
    }
    private static Set<String> requirements(JdbcAuthStore.Account account,JdbcAuthStore store,Policy policy) throws Exception {
        Set<String> pending=new HashSet<>();
        if(policy.requireTerms() && !store.acceptedTerms(account.id(),policy.termsVersion())) pending.add("TERMS");
        if(policy.requireBinding() && !store.isBound(account.id())) pending.add("QQ_BINDING");
        return Set.copyOf(pending);
    }
    private Reply view(CenterLedger.Flow flow,JdbcAuthStore.Account account,JdbcAuthStore store,Policy policy) throws Exception {
        var pending=requirements(account,store,policy); var lease=ledger.session(flow);
        if(pending.isEmpty() && lease!=null && lease.expires()>System.currentTimeMillis()) return admitted(flow,account,policy,lease);
        String ticket=pending.isEmpty()?ledger.ticket(flow):"";
        return new Reply(true,pending.isEmpty()?"TICKET_ISSUED":"REQUIREMENTS",
            new State(true,account.id(),account.version(),provider(flow),pending,flow.id(),ticket,null,0,!account.firstAdmitted()),policy,List.of(),"");
    }
    private static Reply admitted(CenterLedger.Flow flow,JdbcAuthStore.Account account,Policy policy,CenterLedger.Lease lease) {
        long remaining=Math.max(0,Math.min(CenterLedger.LEASE_MILLIS,lease.expires()-System.currentTimeMillis()));
        return new Reply(true,"ADMITTED",new State(true,account.id(),account.version(),provider(flow),Set.of(),flow.id(),"",lease.id(),remaining,!account.firstAdmitted()),policy,List.of(),"");
    }
}
