package xuanmo.arcartxsuite.auth.runtime;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.*;
import xuanmo.arcartxsuite.api.capability.*;
import xuanmo.arcartxsuite.auth.protocol.*;
import xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Node adapter. No account SQL, password hashing, or authority fallback exists in this class. */
final class CenterAuthenticationRuntime implements HostAuthentication,Listener {
    private static final class Connection {
        final Player player;
        final AuthSession session;
        final Context context;
        final AtomicBoolean busy=new AtomicBoolean();
        final AtomicBoolean controlBusy=new AtomicBoolean();
        final AtomicBoolean presenceRequest=new AtomicBoolean();
        volatile State authority;
        volatile AuthRuntimeCapability.ExternalPrompt externalPrompt;
        volatile long leaseDeadline;
        long loginDeadline,nextCheck,authWindowStarted;
        boolean resyncPending;
        boolean firstPending,firstBusy,presenceBusy;
        String presenceDate="";
        Connection(Player player,String realm,String audience,long timeout) {
            this.player=player; session=new AuthSession(player.getUniqueId());
            context=new Context(realm,audience,player.getUniqueId(),player.getName(),session.snapshot().connectionId());
            authWindowStarted=System.nanoTime(); loginDeadline=authWindowStarted+timeout;
            nextCheck=System.nanoTime();
        }
    }
    private final JavaPlugin plugin;
    private final Supplier<EventBusCapability> events;
    private final AuthenticationSettings settings;
    private final CenterClient client;
    private final ExecutorService workers;
    private final ExecutorService renewals;
    private final ExecutorService maintenance;
    private record TimedReply(Reply reply,long sentAt) {}
    private final Context control;
    private final Map<UUID,Connection> connections=new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> futures=ConcurrentHashMap.newKeySet();
    private final Map<CompletableFuture<?>,char[][]> queuedSecrets=new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> runningSecrets=ConcurrentHashMap.newKeySet();
    private record VersionFloor(long version,long expires) {}
    private final Map<UUID,VersionFloor> versionFloors=new HashMap<>();
    private volatile List<BindingService.Binding> bindings=List.of();
    private volatile long bindingsDeadline,outboxDeadline;
    private volatile Policy policy;
    private volatile boolean active=true;
    private BukkitTask task;
    private long nextBindings,nextOutbox,outboxCursor=-1;
    private boolean outboxBusy;
    private final AtomicBoolean bindingsBusy=new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong bindingRevision=new java.util.concurrent.atomic.AtomicLong();
    CenterAuthenticationRuntime(JavaPlugin plugin,Supplier<EventBusCapability> events,AuthenticationSettings settings) {
        this.plugin=plugin; this.events=events; this.settings=settings;
        if(!Bukkit.getOnlineMode()) throw new IllegalArgumentException("中心入口要求直连且 online-mode=true 的游戏节点");
        var center=settings.center(); byte[] key=RpcAuthentication.key(System.getenv(center.keyEnvironment()));
        try { client=new CenterClient(center.url(),center.nodeId(),key); } finally { Arrays.fill(key,(byte)0); }
        var performance=settings.performance();
        workers=new ThreadPoolExecutor(performance.workers(),performance.workers(),0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(performance.queueCapacity()),r->{ Thread t=new Thread(r,"AXS-CenterClient"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
        renewals=new ThreadPoolExecutor(performance.renewalWorkers(),performance.renewalWorkers(),0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(performance.renewalQueueCapacity()),
            r->{ Thread t=new Thread(r,"AXS-CenterRenewal"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
        maintenance=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
            r->{ Thread t=new Thread(r,"AXS-CenterControl"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
        control=new Context(settings.realm(),settings.audience(),new UUID(0,0),"AXSNode",UUID.randomUUID());
        nextBindings=System.nanoTime();
        nextOutbox=nextBindings;
    }
    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers()) attach(player);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    private void main(Runnable action) {
        if(active) Bukkit.getScheduler().runTask(plugin,()->{ if(active) action.run(); });
    }
    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        futures.add(future); future.whenComplete((value,error)->futures.remove(future));
        if(!active) future.completeExceptionally(new CancellationException("Runtime closed"));
        return future;
    }
    private <T> CompletableFuture<T> schedule(Callable<T> work,char[]... secrets) {
        return schedule(workers,System.nanoTime()+3_000_000_000L,work,secrets);
    }
    private <T> CompletableFuture<T> control(Callable<T> work) {
        return schedule(maintenance,System.nanoTime()+3_000_000_000L,work);
    }
    private <T> CompletableFuture<T> schedule(ExecutorService executor,long notAfter,Callable<T> work,char[]... secrets) {
        CompletableFuture<T> future=track(new CompletableFuture<>());
        if(!active) { clear(secrets); future.completeExceptionally(new CancellationException("Runtime closed")); return future; }
        queuedSecrets.put(future,secrets);
        try { executor.execute(()->{
            runningSecrets.add(future);
            queuedSecrets.remove(future);
            try {
                if(System.nanoTime()-notAfter>=0) throw new TimeoutException("Queued authentication request expired");
                if(active && !future.isDone()) future.complete(work.call());
                else future.completeExceptionally(new CancellationException("Runtime closed"));
            } catch(Exception | LinkageError e) { future.completeExceptionally(e); }
            finally { clear(secrets); runningSecrets.remove(future); }
        }); } catch(RejectedExecutionException e) { queuedSecrets.remove(future); clear(secrets); future.completeExceptionally(e); }
        return future;
    }
    private static void clear(char[][] secrets) { for(char[] secret:secrets) Arrays.fill(secret,'\0'); }
    private Reply call(Operation operation,Context context,JsonObject data) throws Exception {
        return client.call(new Request(1,UUID.randomUUID(),operation,context,data));
    }
    private Connection connection(UUID profile,UUID physical) {
        Connection connection=connections.get(profile);
        return connection!=null && connection.context.connection().equals(physical)?connection:null;
    }
    private Result result(UUID profile,boolean success,String code) { return new Result(success,code,snapshot(profile)); }
    private void attach(Player player) {
        if(!player.getName().matches("[\\p{L}\\p{N}_]{1,16}")) { player.kickPlayer("账号名不受支持。"); return; }
        Connection connection=new Connection(player,settings.realm(),settings.audience(),timeout());
        Connection previous=connections.put(player.getUniqueId(),connection);
        if(previous!=null) {
            previous.session.close();
            schedule(()->call(Operation.CLOSE,previous.context,new JsonObject()));
        }
        submit(connection,Operation.OPEN,new JsonObject());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        Connection connection=connections.get(event.getPlayer().getUniqueId());
        if(connection!=null && connection.player==event.getPlayer() && connections.remove(event.getPlayer().getUniqueId(),connection)) {
            connection.session.close();
            schedule(()->call(Operation.CLOSE,connection.context,new JsonObject()));
        }
    }
    private long timeout() { return policy==null?settings.loginTimeoutNanos():TimeUnit.SECONDS.toNanos(policy.loginTimeoutSeconds()); }
    @Override public Terms terms() { Policy current=policy; return current==null?new Terms(false,"",List.of()):new Terms(current.requireTerms(),current.termsVersion(),current.termsLines()); }
    @Override public boolean passwordAuthenticationAllowed() { Policy current=policy; return current!=null && current.gamePasswordEnabled(); }
    @Override public boolean passwordRegistrationAllowed() { return passwordAuthenticationAllowed(); }
    @Override public boolean externalLinkAvailable() { Policy current=policy; return current!=null && current.externalEnabled() && current.gamePasswordEnabled(); }
    @Override public AuthRuntimeCapability.ExternalPrompt externalPrompt(UUID profile) {
        Connection connection=connections.get(profile);
        var prompt=connection==null?null:connection.externalPrompt;
        return prompt!=null && prompt.expiresAt()>System.currentTimeMillis()?prompt:null;
    }
    @Override public AuthSnapshot snapshot(UUID profile) {
        Connection connection=connections.get(profile);
        if(connection==null) return AuthSnapshot.absent(profile);
        synchronized(connection) {
            AuthSnapshot state=connection.session.snapshot();
            if(state.admitted() && System.nanoTime()-connection.leaseDeadline>=0)
                return new AuthSnapshot(profile,state.connectionId(),state.generation(),null,state.registration(),AuthSnapshot.Stage.RESTRICTED,"",Set.of(),"CENTER_LEASE_EXPIRED");
            return state;
        }
    }
    @Override public long eligibleNanos(UUID profile) {
        Connection connection=connections.get(profile);
        if(connection==null) return 0;
        synchronized(connection) { return connection.session.eligibleNanosUntil(connection.leaseDeadline); }
    }
    private void restrict(Connection connection,String reason) {
        AuthSnapshot previous;
        synchronized(connection) {
            previous=connection.session.snapshot();
            if(previous.admitted()) connection.session.pauseEligibilityAt(connection.leaseDeadline);
            connection.session.revoke(); connection.authority=null; connection.leaseDeadline=0; connection.externalPrompt=null;
            connection.presenceDate=""; connection.authWindowStarted=System.nanoTime(); connection.loginDeadline=connection.authWindowStarted+timeout();
        }
        if(previous.identityVerified()) Bukkit.getPluginManager().callEvent(new AuthRestrictionEvent(previous,reason));
    }
    private void tick() {
        long now=System.nanoTime();
        versionFloors.values().removeIf(floor->now-floor.expires()>=0);
        for(Connection connection:connections.values()) {
            UUID profile=connection.context.profile();
            if(connection.session.snapshot().admitted() && !snapshot(profile).admitted()) restrict(connection,"CENTER_LEASE_EXPIRED");
            var state=snapshot(profile);
            if(!state.admitted() && now-connection.loginDeadline>=0) { connection.player.kickPlayer("登录验证已超时，请重新连接。"); continue; }
            if(connection.resyncPending && !connection.busy.get()) {
                connection.resyncPending=false; submit(connection,Operation.OPEN,new JsonObject()); continue;
            }
            if(!state.admitted()) continue;
            deliverFirst(connection);
            if(now-connection.nextCheck>=0 && !connection.controlBusy.get()) {
                State authority=connection.authority;
                if(authority==null || authority.session()==null) { restrict(connection,"CENTER_SESSION_MISSING"); continue; }
                connection.nextCheck=now+5_000_000_000L;
                JsonObject data=new JsonObject(); data.addProperty("session",authority.session().toString());
                submit(connection,Operation.CHECK,data);
            } else if(!connection.busy.get() && !connection.controlBusy.get()) {
                EventBusCapability bus=events.get();
                String today=policy==null?"":java.time.LocalDate.now(java.time.ZoneId.of(policy.timezone())).toString();
                if(bus!=null && !connection.presenceBusy && !connection.presenceRequest.get() && bus.hasSubscribers("axs.auth.presence_day_started") && !today.equals(connection.presenceDate)) submit(connection,Operation.PRESENCE,new JsonObject());
            }
        }
        if(now-nextBindings>=0) { nextBindings=now+30_000_000_000L; refreshBindings(); }
        if(now-nextOutbox>=0 && !outboxBusy) { nextOutbox=now+2_000_000_000L; pollOutbox(); }
    }
    private void pollOutbox() {
        outboxBusy=true;
        JsonObject data=new JsonObject(); data.addProperty("cursor",outboxCursor);
        control(()->{
            Reply reply=call(Operation.OUTBOX,control,data);
            return reply.success()?AuthWire.JSON.fromJson(reply.value(),OutboxPage.class):null;
        }).whenComplete((page,error)->main(()->{
            try { if(error==null && page!=null) applyOutbox(page); }
            catch(RuntimeException e) { plugin.getLogger().warning("认证中心事件页无效，保留原游标"); }
            finally { outboxBusy=false; }
        }));
    }
    private void applyOutbox(OutboxPage page) {
        if(page.cursor()<0 || page.events().size()>128) throw new IllegalArgumentException("Invalid outbox page");
        if(page.gap()) {
            if(!page.events().isEmpty()) throw new IllegalArgumentException("Invalid outbox gap response");
            versionFloors.clear();
            bindings=List.of(); bindingsDeadline=0; bindingRevision.incrementAndGet();
            for(Connection connection:connections.values()) { restrict(connection,"CENTER_OUTBOX_RESYNC"); connection.resyncPending=true; }
            outboxCursor=page.cursor(); outboxDeadline=System.nanoTime()+15_000_000_000L; refreshBindings(); return;
        }
        long last=outboxCursor;
        for(OutboxEvent event:page.events()) {
            if(event.sequence()<=last || event.sequence()>page.cursor() || event.account()==null || event.type()==null || event.securityVersion()<1)
                throw new IllegalArgumentException("Invalid outbox event ordering");
            last=event.sequence();
        }
        if(!page.events().isEmpty() && last!=page.cursor()) throw new IllegalArgumentException("Invalid outbox tail");
        boolean bindingChanged=false;
        for(OutboxEvent event:page.events()) {
            switch(event.type()) {
                case BINDING_ADDED -> { bindingChanged=true; bindingRevision.incrementAndGet(); }
                case BINDING_REMOVED,PASSWORD_CHANGED,ACCOUNT_REVOKED,IDENTITY_LINKED -> {
                    bindingChanged|=event.type()==EventType.BINDING_REMOVED;
                    if(event.type()==EventType.BINDING_REMOVED) removeBindingProjection(event.account());
                    invalidateVersion(event.account(),event.securityVersion(),event.type().name());
                }
                default -> { }
            }
        }
        outboxCursor=page.cursor();
        outboxDeadline=System.nanoTime()+15_000_000_000L;
        if(bindingChanged) refreshBindings();
    }
    private void invalidateVersion(UUID account,long version,String reason) {
        if(version<1) throw new IllegalArgumentException("Invalid security version");
        versionFloors.merge(account,new VersionFloor(version,System.nanoTime()+60_000_000_000L),(before,after)->before.version()>after.version()?before:after);
        for(Connection connection:connections.values()) {
            State authority=connection.authority;
            if(authority!=null && account.equals(authority.account()) && authority.securityVersion()<version) {
                restrict(connection,reason);
                if("IDENTITY_LINKED".equals(reason)) connection.resyncPending=true;
            }
        }
    }
    private CompletionStage<Result> submit(Connection connection,Operation operation,JsonObject data,char[]... secrets) {
        UUID profile=connection.context.profile();
        boolean background=operation==Operation.CHECK || operation==Operation.PRESENCE;
        AtomicBoolean lock=operation==Operation.CHECK?connection.controlBusy:operation==Operation.PRESENCE?connection.presenceRequest:connection.busy;
        if(!active || !lock.compareAndSet(false,true)) { clear(secrets); return CompletableFuture.completedFuture(result(profile,false,"BUSY")); }
        AuthSession.Operation ticket;
        try {
            var state=connection.session.snapshot();
            ticket=background?new AuthSession.Operation(state.connectionId(),state.generation()):connection.session.begin();
        } catch(IllegalStateException e) { lock.set(false); clear(secrets); return CompletableFuture.completedFuture(result(profile,false,"STALE_CONNECTION")); }
        CompletableFuture<Result> completed=track(new CompletableFuture<>());
        long notAfter=System.nanoTime()+3_000_000_000L;
        if(operation==Operation.CHECK) notAfter=Math.min(notAfter,connection.leaseDeadline-4_000_000_000L);
        schedule(operation==Operation.CHECK?renewals:workers,notAfter,()->{
            try {
                if(connections.get(profile)!=connection || !connection.session.current(ticket))
                    throw new CancellationException("Connection replaced");
                if(secrets.length>0) data.addProperty("password",new String(secrets[0]));
                if(secrets.length>1) data.addProperty("newPassword",new String(secrets[1]));
                long sentAt=System.nanoTime();
                Reply reply=call(operation,connection.context,data);
                if(reply.success() && reply.state()!=null && reply.state().ticket()!=null && !reply.state().ticket().isEmpty()) {
                    if(connections.get(profile)!=connection || !connection.session.current(ticket))
                        throw new CancellationException("Connection replaced");
                    JsonObject exchange=new JsonObject(); exchange.addProperty("ticket",reply.state().ticket());
                    sentAt=System.nanoTime();
                    reply=call(Operation.EXCHANGE,connection.context,exchange);
                }
                return new TimedReply(reply,sentAt);
            } finally { data.remove("password"); data.remove("newPassword"); }
        },secrets).whenComplete((timed,error)->main(()->{
            Reply reply=timed==null?null:timed.reply();
            try {
                if(connections.get(profile)!=connection || !connection.session.current(ticket)) { completed.complete(result(profile,false,"STALE_CONNECTION")); return; }
                if(error!=null) {
                    if(operation==Operation.PRESENCE) { completed.complete(result(profile,false,"UNAVAILABLE")); return; }
                    if(connection.session.snapshot().identityVerified()) restrict(connection,"CENTER_UNAVAILABLE");
                    else connection.session.note(ticket,"UNAVAILABLE");
                    completed.complete(result(profile,false,"UNAVAILABLE")); return;
                }
                if(reply.policy()!=null && !reply.policy().equals(policy)) {
                    policy=reply.policy();
                    for(Connection waiting:connections.values()) if(!waiting.session.snapshot().admitted()) waiting.loginDeadline=waiting.authWindowStarted+timeout();
                }
                if(Set.of("EXTERNAL_PENDING","EXTERNAL_APPROVED").contains(reply.code())) {
                    var prompt=AuthWire.JSON.fromJson(reply.value(),AuthWire.ExternalPrompt.class);
                    var url=java.net.URI.create(prompt.verificationUrl());
                    if(!"https".equals(url.getScheme()) || url.getHost()==null || url.getUserInfo()!=null || prompt.requestId()==null
                            || !prompt.userCode().matches("[0-9A-F]{16}") || !prompt.confirmation().matches("(?:[0-9A-F]{8})?"))
                        throw new IllegalArgumentException("Invalid external prompt");
                    connection.externalPrompt=new AuthRuntimeCapability.ExternalPrompt(prompt.requestId(),prompt.verificationUrl(),
                        prompt.userCode(),prompt.expiresAt(),prompt.confirmation());
                } else if(operation==Operation.OPEN || operation==Operation.EXTERNAL_CONFIRM || reply.success() && operation==Operation.PASSWORD) {
                    connection.externalPrompt=null;
                }
                if(operation==Operation.CHECK && !reply.success()) restrict(connection,reply.code());
                else if(reply.success() && operation==Operation.CHANGE_PASSWORD) restrict(connection,"PASSWORD_CHANGED");
                else if("EXTERNAL_LINKED".equals(reply.code())) {
                    restrict(connection,"IDENTITY_LINKED");
                    connection.resyncPending=true;
                }
                else if(reply.state()!=null) apply(connection,ticket,reply,timed.sentAt());
                else if(reply.success() && operation==Operation.PRESENCE) presence(connection,reply.value());
                else if(!reply.success() && Set.of("REVOKED","STALE_CONNECTION","STALE_CREDENTIALS").contains(reply.code())) restrict(connection,reply.code());
                completed.complete(result(profile,reply.success(),reply.code()));
            } catch(Exception e) {
                restrict(connection,"CENTER_REPLY_REJECTED"); completed.complete(result(profile,false,"UNAVAILABLE"));
            } finally { lock.set(false); }
        }));
        return completed;
    }
    private void apply(Connection connection,AuthSession.Operation ticket,Reply reply,long started) {
        State authority=reply.state(); var before=connection.session.snapshot();
        if(authority.account()==null) {
            if(before.identityVerified()) { restrict(connection,"CENTER_IDENTITY_EXPIRED"); before=connection.session.snapshot(); ticket=new AuthSession.Operation(before.connectionId(),before.generation()); }
            connection.session.loaded(ticket,authority.registered()); connection.session.note(ticket,reply.code()); return;
        }
        if(authority.flow()==null || authority.securityVersion()<1 || !Set.of("local-password","oidc").contains(authority.provider()))
            throw new IllegalArgumentException("Invalid center authority");
        VersionFloor floor=versionFloors.get(authority.account());
        if(floor!=null && authority.securityVersion()<floor.version()) throw new IllegalStateException("Center authority already revoked");
        Set<AuthSnapshot.Requirement> pending=new HashSet<>();
        for(String requirement:authority.requirements()) pending.add(AuthSnapshot.Requirement.valueOf(requirement));
        if(authority.session()!=null && (!pending.isEmpty() || authority.leaseMillis()<=0 || authority.leaseMillis()>15_000)) throw new IllegalArgumentException("Invalid center lease");
        boolean granted=false;
        synchronized(connection) {
            if(before.admitted()) {
                if(System.nanoTime()-connection.leaseDeadline>=0) throw new IllegalStateException("Previous center lease expired");
                State previous=connection.authority;
                if(authority.session()==null || previous==null || previous.securityVersion()!=authority.securityVersion() || !Objects.equals(before.accountId(),authority.account()))
                    throw new IllegalArgumentException("Changed center authority");
            } else {
                if(before.stage()==AuthSnapshot.Stage.LOADING) connection.session.loaded(ticket,true);
                if(!connection.session.verified(ticket,authority.account(),authority.provider(),pending)) throw new IllegalStateException("Stale local identity");
            }
            if(authority.session()!=null) {
                long deadline=started+TimeUnit.MILLISECONDS.toNanos(authority.leaseMillis());
                if(System.nanoTime()-deadline>=0) throw new IllegalArgumentException("Expired center reply");
                if(!before.admitted()) {
                    if(!connection.session.admit(ticket)) throw new IllegalStateException("Cannot admit center identity");
                    connection.nextCheck=System.nanoTime()+5_000_000_000L;
                    granted=true;
                }
                connection.leaseDeadline=deadline;
            }
            connection.authority=authority;
        }
        if(granted) admission(connection,authority.firstAdmission());
    }
    private void admission(Connection connection,boolean first) {
        var state=connection.session.snapshot();
        Bukkit.getPluginManager().callEvent(new AuthAdmissionEvent(state));
        var bus=events.get();
        if(bus!=null) {
            bus.publish("axs.auth.admission_granted",connection.player,Map.of("account_id",state.accountId().toString(),"connection_id",state.connectionId().toString(),"event_id",state.connectionId().toString(),"provider",state.provider()));
        }
        connection.firstPending=first;
        deliverFirst(connection);
    }
    private void deliverFirst(Connection connection) {
        var bus=events.get(); var state=snapshot(connection.context.profile());
        if(!connection.firstPending || connection.firstBusy || bus==null || !state.admitted()) return;
        connection.firstBusy=true;
        bus.publishConfirmed("axs.auth.profile_first_admitted",connection.player,Map.of("account_id",state.accountId().toString(),
            "event_id",settings.realm()+":"+state.accountId()+":first","provider",state.provider())).whenComplete((confirmed,error)->{
            if(error!=null || !Boolean.TRUE.equals(confirmed)) { main(()->connection.firstBusy=false); return; }
            control(()->call(Operation.ACK_FIRST,connection.context,new JsonObject())).whenComplete((reply,failure)->main(()->{
                connection.firstBusy=false;
                if(failure==null && reply.success()) connection.firstPending=false;
            }));
        });
    }
    private void presence(Connection connection,String value) {
        int separator=value.lastIndexOf(':');
        String date=java.time.LocalDate.parse(value.substring(0,separator)).toString();
        var bus=events.get(); var state=snapshot(connection.context.profile());
        if(!state.admitted() || bus==null || !bus.hasSubscribers("axs.auth.presence_day_started")) return;
        connection.presenceBusy=true;
        JsonObject data=new JsonObject(); data.addProperty("date",date);
        CompletionStage<Boolean> receipt=Boolean.parseBoolean(value.substring(separator+1))?bus.publishConfirmed("axs.auth.presence_day_started",connection.player,
            Map.of("account_id",state.accountId().toString(),"business_date",date,"timezone",policy.timezone(),"event_id",settings.realm()+":"+state.accountId()+":"+date))
            :CompletableFuture.completedFuture(true);
        receipt.whenComplete((confirmed,error)->{
            if(error!=null || !Boolean.TRUE.equals(confirmed)) { main(()->connection.presenceBusy=false); return; }
            control(()->call(Operation.ACK_PRESENCE,connection.context,data)).whenComplete((reply,failure)->main(()->{
                connection.presenceBusy=false;
                if(failure==null && reply.success()) connection.presenceDate=date;
            }));
        });
    }
    @Override public CompletionStage<Result> refresh(UUID profile,UUID physical) {
        Connection connection=connection(profile,physical);
        if(connection==null) return CompletableFuture.completedFuture(result(profile,false,"STALE_CONNECTION"));
        return submit(connection,Operation.OPEN,new JsonObject());
    }
    @Override public CompletionStage<Result> beginExternalLink(UUID profile,UUID physical) {
        Connection connection=connection(profile,physical);
        var state=snapshot(profile);
        if(connection==null || !state.admitted() || !"local-password".equals(state.provider()) || !externalLinkAvailable())
            return CompletableFuture.completedFuture(result(profile,false,"RECENT_PASSWORD_REQUIRED"));
        return submit(connection,Operation.EXTERNAL_LINK,new JsonObject());
    }
    @Override public CompletionStage<Result> confirmExternal(UUID profile,UUID physical,UUID requestId) {
        Connection connection=connection(profile,physical);
        var prompt=externalPrompt(profile);
        if(connection==null || prompt==null || !prompt.requestId().equals(requestId) || prompt.confirmation().isEmpty())
            return CompletableFuture.completedFuture(result(profile,false,"EXTERNAL_NOT_APPROVED"));
        JsonObject data=new JsonObject(); data.addProperty("pairing",requestId.toString());
        return submit(connection,Operation.EXTERNAL_CONFIRM,data);
    }
    @Override public CompletionStage<Result> authenticate(UUID profile,UUID physical,char[] password,boolean register,boolean agreed,String termsVersion) {
        Connection connection=connection(profile,physical); var state=snapshot(profile);
        if(!passwordAuthenticationAllowed()) {
            clear(new char[][]{password}); return CompletableFuture.completedFuture(result(profile,false,"PASSWORD_ENTRY_DISABLED"));
        }
        if(connection==null || state.stage()!=AuthSnapshot.Stage.AWAITING_IDENTITY && state.stage()!=AuthSnapshot.Stage.RESTRICTED) {
            clear(new char[][]{password}); return CompletableFuture.completedFuture(result(profile,false,"INVALID_STATE"));
        }
        JsonObject data=new JsonObject(); data.addProperty("register",register); data.addProperty("agreed",agreed); data.addProperty("termsVersion",termsVersion);
        return submit(connection,Operation.PASSWORD,data,password);
    }
    @Override public CompletionStage<Result> changePassword(UUID profile,UUID physical,char[] oldPassword,char[] newPassword) {
        Connection connection=connection(profile,physical);
        if(connection==null || !snapshot(profile).admitted()) {
            clear(new char[][]{oldPassword,newPassword}); return CompletableFuture.completedFuture(result(profile,false,"INVALID_STATE"));
        }
        return submit(connection,Operation.CHANGE_PASSWORD,new JsonObject(),oldPassword,newPassword);
    }
    @Override public CompletionStage<Result> confirmBinding(UUID profile,UUID physical,String code) {
        Connection connection=connection(profile,physical);
        if(connection==null || !snapshot(profile).identityVerified()) return CompletableFuture.completedFuture(result(profile,false,"INVALID_STATE"));
        JsonObject data=new JsonObject(); data.addProperty("code",code);
        return submit(connection,Operation.BIND,data).thenApply(result->{ if(result.success()) { bindingRevision.incrementAndGet(); refreshBindings(); publishBinding(profile,true); } return result; });
    }
    @Override public CompletionStage<Result> acceptTerms(UUID profile,UUID physical,String version) {
        Connection connection=connection(profile,physical);
        if(connection==null || !snapshot(profile).identityVerified()) return CompletableFuture.completedFuture(result(profile,false,"INVALID_STATE"));
        JsonObject data=new JsonObject(); data.addProperty("version",version); return submit(connection,Operation.TERMS,data);
    }
    @Override public List<BindingService.Binding> snapshot() {
        long now=System.nanoTime();
        return active && now-bindingsDeadline<0 && now-outboxDeadline<0?bindings:List.of();
    }
    private CompletionStage<BindingService.BindingResult> binding(Operation operation,JsonObject data) {
        CompletableFuture<BindingService.BindingResult> completed=track(new CompletableFuture<>());
        schedule(()->call(operation,control,data)).whenComplete((reply,error)->main(()->{
            try {
                if(error!=null) { completed.complete(new BindingService.BindingResult(false,"认证中心暂时不可用")); return; }
                String message=reply.success()?reply.value():reply.code();
                if(reply.success() && (operation==Operation.UNBIND_QQ || operation==Operation.UNBIND_PROFILE)) {
                    long version=Long.parseLong(reply.value());
                    for(var removed:reply.bindings()) {
                        removeBindingProjection(removed.accountId());
                        invalidateVersion(removed.accountId(),version,"BINDING_REMOVED"); message=removed.profileName();
                        var bus=events.get();
                        if(bus!=null) bus.publish("axs.auth.binding_changed",Bukkit.getPlayer(removed.profileId()),Map.of("account_id",removed.accountId().toString(),"profile_id",removed.profileId().toString(),"bound","false","event_id",UUID.randomUUID().toString()));
                    }
                }
                if(reply.success()) refreshBindings();
                completed.complete(new BindingService.BindingResult(reply.success(),message));
            } catch(RuntimeException e) { completed.complete(new BindingService.BindingResult(false,"绑定结果待确认，请刷新状态")); }
        }));
        return completed;
    }
    private void refreshBindings() {
        if(!active || !bindingsBusy.compareAndSet(false,true)) return;
        readBindings("",new ArrayList<>(),bindingRevision.get());
    }
    private void removeBindingProjection(UUID account) {
        bindingRevision.incrementAndGet();
        bindings=bindings.stream().filter(binding->!binding.accountId().equals(account)).toList();
    }
    private void readBindings(String after,List<BindingService.Binding> collected,long revision) {
        JsonObject data=new JsonObject(); data.addProperty("after",after);
        control(()->call(Operation.BINDINGS,control,data)).whenComplete((reply,error)->{
            if(error!=null || !active || !reply.success()) { bindingsBusy.set(false); return; }
            try {
                String last=after;
                for(var value:reply.bindings()) {
                    String id=value.accountId().toString();
                    if(id.compareTo(last)<=0) throw new IllegalArgumentException("Invalid binding page order");
                    collected.add(new BindingService.Binding(value.accountId(),value.profileId(),value.profileName(),value.qqId(),value.boundAt())); last=id;
                }
                if(reply.bindings().size()>256 || reply.value().isEmpty() && reply.bindings().size()==256
                        || !reply.value().isEmpty() && (reply.bindings().size()!=256 || !reply.value().equals(last) || last.compareTo(after)<=0))
                    throw new IllegalArgumentException("Invalid binding page");
                if(reply.value().isEmpty()) main(()->{
                    if(bindingRevision.get()==revision) { bindings=List.copyOf(collected); bindingsDeadline=System.nanoTime()+35_000_000_000L; }
                    bindingsBusy.set(false);
                    if(bindingRevision.get()!=revision) refreshBindings();
                });
                else readBindings(reply.value(),collected,revision);
            } catch(RuntimeException e) { bindingsBusy.set(false); }
        });
    }
    private void publishBinding(UUID profile,boolean bound) {
        var bus=events.get(); var state=snapshot(profile);
        if(bus!=null && state.accountId()!=null) bus.publish("axs.auth.binding_changed",Bukkit.getPlayer(profile),Map.of("account_id",state.accountId().toString(),"profile_id",profile.toString(),"bound",Boolean.toString(bound),"event_id",UUID.randomUUID().toString()));
    }
    @Override public CompletionStage<BindingService.BindingResult> requestFromQq(long qq,String name) { JsonObject data=new JsonObject(); data.addProperty("qq",qq); data.addProperty("name",name); return binding(Operation.REQUEST_BINDING,data); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByQq(long qq) { JsonObject data=new JsonObject(); data.addProperty("qq",qq); return binding(Operation.UNBIND_QQ,data); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByProfile(UUID profile) { JsonObject data=new JsonObject(); data.addProperty("profile",profile.toString()); return binding(Operation.UNBIND_PROFILE,data); }
    @Override public CompletionStage<Void> revoke(UUID account,String reason) {
        JsonObject data=new JsonObject(); data.addProperty("account",account.toString());
        CompletableFuture<Void> completed=track(new CompletableFuture<>());
        control(()->call(Operation.REVOKE,control,data)).whenComplete((reply,error)->main(()->{
            try {
                if(error!=null || !reply.success()) { completed.completeExceptionally(new IllegalStateException("Center revocation not confirmed")); return; }
                invalidateVersion(account,Long.parseLong(reply.value()),reason); completed.complete(null);
            } catch(RuntimeException e) { completed.completeExceptionally(e); }
        }));
        return completed;
    }
    @Override public void close() {
        active=false; if(task!=null) task.cancel(); HandlerList.unregisterAll(this);
        connections.values().forEach(connection->connection.session.close()); connections.clear();
        queuedSecrets.forEach((future,secrets)->{ if(!runningSecrets.contains(future)) clear(secrets); }); queuedSecrets.clear();
        workers.shutdownNow(); renewals.shutdownNow(); maintenance.shutdownNow(); client.close(); bindings=List.of();
        for(CompletableFuture<?> future:futures) future.completeExceptionally(new CancellationException("Runtime closed"));
        futures.clear();
    }
}
