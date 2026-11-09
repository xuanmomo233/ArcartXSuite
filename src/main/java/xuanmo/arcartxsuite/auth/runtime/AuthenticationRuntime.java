package xuanmo.arcartxsuite.auth.runtime;

import java.util.*;
import xuanmo.arcartxsuite.auth.engine.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.*;
import xuanmo.arcartxsuite.api.capability.*;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/** Host-owned standalone backend. Neither UI lifetime nor account classification can grant admission. */
public final class AuthenticationRuntime implements HostAuthentication, Listener {
    private record Connection(Player player, AuthSession session, java.util.concurrent.atomic.AtomicLong loginDeadline,
            java.util.concurrent.atomic.AtomicLong authorityDeadline) {}
    private final JavaPlugin plugin;
    private final JdbcAuthStore store;
    private final Argon2Passwords passwords = new Argon2Passwords();
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Result>> futures = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Result>> running = ConcurrentHashMap.newKeySet();
    private final Map<CompletableFuture<Result>,char[][]> secretBuffers = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<BindingService.BindingResult>> bindingFutures = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Void>> revocationFutures = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker;
    private final ExecutorService securityChecks=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),
        r->{ Thread t=new Thread(r,"AXS-AuthVersionCheck"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final java.util.concurrent.atomic.AtomicBoolean checkingVersions=new java.util.concurrent.atomic.AtomicBoolean();
    private long nextVersionCheck;
    private final CompletableFuture<Void> drained=new CompletableFuture<>();
    private CompletionStage<Void> predecessor=CompletableFuture.completedFuture(null);
    private final Supplier<EventBusCapability> events;
    private final int minLength, maxLength, maxAttempts;
    private final long cooldown;
    private final long loginTimeoutNanos;
    private final boolean requireTerms;
    private final Terms terms;
    private final String realm;
    private final xuanmo.arcartxsuite.auth.minecraft.LocalHandshakeClient nativeGateway;
    private final List<xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Trust> trustedProxies;
    private final String audience;
    private final boolean passwordAllowed;
    private ProxyProofIngress proxyIngress;
    private final boolean requireBinding;
    private final Object bindingLock = new Object();
    private final Object invalidationLock = new Object();
    private volatile List<BindingService.Binding> bindings = List.of();
    private volatile long bindingsDeadline;
    private final java.util.concurrent.atomic.AtomicBoolean refreshingBindings=new java.util.concurrent.atomic.AtomicBoolean();
    private record Challenge(UUID account,long qq,long expires) {}
    private final Map<String,Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<UUID,JdbcAuthStore.Account> verifiedAccounts = new ConcurrentHashMap<>();
    private final Map<UUID,Long> invalidVersions = new ConcurrentHashMap<>();
    private final Map<UUID,Long> pendingRevocations=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> securityWriters=new ConcurrentHashMap<>();
    private final Set<UUID> reconciling=ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong revocationSequence=new java.util.concurrent.atomic.AtomicLong();
    private volatile CompletableFuture<Void> initialized = new CompletableFuture<>();
    private volatile boolean active = true;
    private final java.time.ZoneId timezone;
    private final Map<UUID,String> presenceDates = new HashMap<>();
    private final Map<UUID,AuthSnapshot> pendingPresence = new HashMap<>();
    private final Map<UUID,JdbcAuthStore.Account> pendingFirst=new HashMap<>();
    private final Set<UUID> firstBusy=new HashSet<>();
    private org.bukkit.scheduler.BukkitTask presenceTask;

    AuthenticationRuntime(JavaPlugin plugin, StorageManager storage, Supplier<EventBusCapability> events,AuthenticationSettings settings) {
        this.plugin = plugin; this.events = events;
        realm=settings.realm(); timezone=settings.timezone(); loginTimeoutNanos=settings.loginTimeoutNanos();
        nativeGateway=settings.nativeGateway(); trustedProxies=settings.trustedProxies(); audience=settings.audience();
        passwordAllowed=settings.passwordAllowed(); requireBinding=settings.requireBinding();
        terms=settings.terms(); requireTerms=terms.enabled();
        var policy=settings.passwordPolicy();
        minLength=policy.minLength(); maxLength=policy.maxLength(); maxAttempts=policy.maxAttempts(); cooldown=policy.cooldownMillis();
        int workers=settings.performance().workers(),capacity=settings.performance().queueCapacity();
        worker = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), r -> {
            Thread t = new Thread(r, "AXS-Auth"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.AbortPolicy()) {
            @Override protected void terminated() { drained.complete(null); }
        };
        store = new JdbcAuthStore(storage::getHostConnection, realm);
    }

    public void start() {
        if(!trustedProxies.isEmpty()) { proxyIngress=new ProxyProofIngress(plugin,this); proxyIngress.start(); }
        predecessor.whenComplete((ignored,failure)->{
            if(!active) return;
            if(failure!=null) { initialized.completeExceptionally(failure); return; }
            try { worker.execute(() -> {
                try {
                    if(!active) throw new CancellationException("Runtime closed");
                    store.initialize(); refreshBindingProjection(); initialized.complete(null);
                } catch(Exception | LinkageError e) { initialized.completeExceptionally(e); plugin.getLogger().severe("认证存储初始化失败"); }
            }); } catch(RejectedExecutionException e) { initialized.completeExceptionally(e); }
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) attach(player);
        presenceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::presence, 20, 20);
    }
    private void presence() {
        if(proxyIngress!=null) proxyIngress.tick();
        if(System.nanoTime()-nextVersionCheck>=0) { nextVersionCheck=System.nanoTime()+5_000_000_000L; checkVersions(); }
        for(UUID account:pendingRevocations.keySet()) if(!securityWriters.containsKey(account) && reconciling.add(account)) {
            try { worker.execute(()->{ try { reconcileRevocation(account); } finally { reconciling.remove(account); } }); }
            catch(RejectedExecutionException e) { reconciling.remove(account); }
        }
        for(Connection connection:connections.values()) {
            if(connection.session().snapshot().identityVerified() && System.nanoTime()-connection.authorityDeadline().get()>=0)
                restrict(connection,"STORAGE_LEASE_EXPIRED");
            if(!snapshot(connection.player().getUniqueId()).admitted() && System.nanoTime()-connection.loginDeadline().get()>0
                    && connection.player().isOnline()) connection.player().kickPlayer("登录验证已超时，请重新连接。");
            if(snapshot(connection.player().getUniqueId()).admitted()) deliverFirst(connection);
        }
        EventBusCapability currentBus=events.get();
        if(currentBus==null || !currentBus.hasSubscribers("axs.auth.presence_day_started")) return;
        String date = java.time.LocalDate.now(timezone).toString();
        for (var entry : connections.entrySet()) {
            UUID uuid=entry.getKey(); Connection c=entry.getValue(); AuthSnapshot state=snapshot(uuid);
            if(!state.admitted() || date.equals(presenceDates.get(uuid)) || busy.contains(uuid) || pendingPresence.putIfAbsent(uuid,state)!=null) continue;
            UUID account=state.accountId();
            try {
                worker.execute(()->{
                    try {
                        store.pendingDay(account,date);
                        String next=store.nextPendingDay(account,date);
                        boolean pending=next!=null;
                        String deliveryDate=pending?next:date;
                        main(()->{
                            AuthSnapshot current=snapshot(uuid);
                            if(connections.get(uuid)!=c || !current.admitted() || current.generation()!=state.generation()
                                    || !Objects.equals(current.accountId(),account)) { pendingPresence.remove(uuid,state); return; }
                            EventBusCapability bus=events.get();
                            if(bus==null) { pendingPresence.remove(uuid,state); return; }
                            CompletionStage<Boolean> receipt=pending?bus.publishConfirmed("axs.auth.presence_day_started",c.player(),Map.of("account_id",account.toString(),
                                "business_date",deliveryDate,"timezone",timezone.getId(),"event_id",realm+":"+account+":"+deliveryDate)):CompletableFuture.completedFuture(true);
                            receipt.whenComplete((confirmed,error)->{
                                if(error!=null || !Boolean.TRUE.equals(confirmed)) { main(()->pendingPresence.remove(uuid,state)); return; }
                                try { worker.execute(()->{
                                    boolean accepted=false;
                                    try { if(active) { store.acknowledgeDay(account,deliveryDate); accepted=true; } }
                                    catch(Exception ignored) { }
                                    boolean acknowledged=accepted;
                                    main(()->{ pendingPresence.remove(uuid,state); if(acknowledged && deliveryDate.equals(date) && connections.get(uuid)==c) presenceDates.put(uuid,date); });
                                }); } catch(RejectedExecutionException e) { main(()->pendingPresence.remove(uuid,state)); }
                            });
                        });
                    } catch(Exception e) { main(()->pendingPresence.remove(uuid,state)); }
                });
            } catch(RejectedExecutionException e) { pendingPresence.remove(uuid,state); }
        }
    }
    private void checkVersions() {
        if(!active || verifiedAccounts.isEmpty() || !checkingVersions.compareAndSet(false,true)) return;
        var expected=Map.copyOf(verifiedAccounts);
        try { securityChecks.execute(()->{
            try {
                var grouped=new HashMap<UUID,List<Map.Entry<UUID,JdbcAuthStore.Account>>>();
                expected.entrySet().forEach(entry->grouped.computeIfAbsent(entry.getValue().id(),ignored->new ArrayList<>()).add(entry));
                var accounts=List.copyOf(grouped.keySet());
                for(int from=0;from<accounts.size() && active;from+=256) {
                    var batch=accounts.subList(from,Math.min(from+256,accounts.size()));
                    long checkedAt=System.nanoTime(); var versions=store.versions(batch);
                    main(()->{
                        for(UUID id:batch) for(var entry:grouped.get(id)) {
                            var account=entry.getValue(); Connection c=connections.get(entry.getKey());
                            if(c==null || verifiedAccounts.get(entry.getKey())!=account) continue;
                            Long version=versions.get(account.id());
                            if(version==null || version!=account.version()) { restrict(c,"SECURITY_VERSION_CHANGED"); continue; }
                            if(!pendingRevocations.containsKey(account.id()) && c.session().snapshot().identityVerified()
                                    && System.nanoTime()-c.authorityDeadline().get()<0) c.authorityDeadline().set(checkedAt+15_000_000_000L);
                        }
                    });
                }
            } catch(Exception | LinkageError ignored) { /* Expiry is enforced by snapshots, including storage outages. */ }
            finally { checkingVersions.set(false); }
        }); } catch(RejectedExecutionException e) { checkingVersions.set(false); }
    }
    @Override public AuthSnapshot snapshot(UUID playerId) {
        Connection c = connections.get(playerId);
        if(c==null) return AuthSnapshot.absent(playerId);
        var snapshot=c.session().snapshot();
        var account=verifiedAccounts.get(playerId);
        if(account!=null && (System.nanoTime()-c.authorityDeadline().get()>=0 || pendingRevocations.containsKey(account.id()) || account.version()<=invalidVersions.getOrDefault(account.id(),0L)) && snapshot.identityVerified())
            return new AuthSnapshot(playerId,snapshot.connectionId(),snapshot.generation(),null,snapshot.registration(),
                AuthSnapshot.Stage.RESTRICTED,"",Set.of(),"REVOKED");
        return snapshot;
    }
    private void restrict(Connection connection,String reason) {
        var before=connection.session().snapshot();
        connection.session().pauseEligibilityAt(connection.authorityDeadline().get());
        connection.session().revoke();
        connection.loginDeadline().set(System.nanoTime()+loginTimeoutNanos);
        verifiedAccounts.remove(before.playerId());
        presenceDates.remove(before.playerId());
        if(before.identityVerified()) Bukkit.getPluginManager().callEvent(new AuthRestrictionEvent(before,reason));
    }
    private void invalidateVersion(UUID account,long version) {
        synchronized(invalidationLock) {
        for(var entry:verifiedAccounts.entrySet()) {
            if(!entry.getValue().id().equals(account)) continue;
            Connection connection=connections.get(entry.getKey());
            if(connection!=null) {
                connection.session().pauseEligibility();
                connection.loginDeadline().set(System.nanoTime()+loginTimeoutNanos);
            }
        }
        invalidVersions.merge(account,version,Math::max);
        }
    }
    private boolean completeIdentity(Connection c,JdbcAuthStore.Account account,String provider,Set<AuthSnapshot.Requirement> pending) {
        synchronized(invalidationLock) {
            if(pendingRevocations.containsKey(account.id()) || account.version()<=invalidVersions.getOrDefault(account.id(),0L)) return false;
            var snapshot=c.session().snapshot();
            var operation=new AuthSession.Operation(snapshot.connectionId(),snapshot.generation());
            if(!c.session().verified(operation,account.id(),provider,pending)) return false;
            c.authorityDeadline().set(System.nanoTime()+15_000_000_000L);
            verifiedAccounts.put(snapshot.playerId(),account);
            return !pending.isEmpty() || c.session().admit(operation);
        }
    }
    private Result completeRequirement(Connection c,JdbcAuthStore.Account account,AuthSnapshot.Requirement requirement) {
        UUID uuid=c.player().getUniqueId();
        synchronized(invalidationLock) {
            if(pendingRevocations.containsKey(account.id()) || account.version()<=invalidVersions.getOrDefault(account.id(),0L)) return result(uuid,false,"STALE_CREDENTIALS");
            if(c.session().snapshot().admitted()) return result(uuid,true,"BOUND");
            if(!c.session().satisfy(requirement)) return result(uuid,false,"INVALID_STATE");
            var snapshot=c.session().snapshot();
            if(!snapshot.requirements().isEmpty()) return result(uuid,true,
                snapshot.requirements().contains(AuthSnapshot.Requirement.TERMS)?"TERMS_REQUIRED":"QQ_BINDING_REQUIRED");
            if(!c.session().admit(new AuthSession.Operation(snapshot.connectionId(),snapshot.generation()))) return result(uuid,false,"STALE_CONNECTION");
        }
        publishAdmission(c,account);
        return result(uuid,true,"ADMITTED");
    }
    @Override public long eligibleNanos(UUID playerId) {
        Connection c = connections.get(playerId);
        return c == null ? 0 : c.session().eligibleNanosUntil(c.authorityDeadline().get());
    }
    @Override public Terms terms() { return terms; }
    @Override public boolean passwordRegistrationAllowed() { return passwordAllowed; }
    @Override public boolean passwordAuthenticationAllowed() { return passwordAllowed; }
    String proxyChallenge(Player player) {
        var state=snapshot(player.getUniqueId());
        if(trustedProxies.isEmpty() || state.identityVerified() || busy.contains(player.getUniqueId())
                || state.stage()==AuthSnapshot.Stage.LOADING || state.connectionId()==null) return null;
        Connection c=connections.get(player.getUniqueId());
        var payload=new com.google.gson.JsonObject(); payload.addProperty("type","challenge");
        payload.addProperty("profile",player.getUniqueId().toString()); payload.addProperty("name",player.getName());
        payload.addProperty("connection",state.connectionId().toString()); payload.addProperty("challenge",c.session().challengeId().toString());
        payload.addProperty("realm",realm); payload.addProperty("audience",audience);
        c.session().note(new AuthSession.Operation(state.connectionId(),state.generation()),"WAITING_PROXY");
        return payload.toString();
    }
    void acceptProxyProof(Player player,UUID connection,String envelope) {
        UUID uuid=player.getUniqueId(); Connection c=connection(uuid,connection);
        if(c==null || trustedProxies.isEmpty() || snapshot(uuid).identityVerified() || snapshot(uuid).stage()==AuthSnapshot.Stage.LOADING) return;
        String name=player.getName(); UUID challenge=c.session().challengeId();
        submit(c,()->{
            xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Identity identity;
            try { identity=xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.verify(envelope,trustedProxies,uuid,name,
                new xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Target(realm,audience,connection,challenge),System.currentTimeMillis()); }
            catch(Exception e) { return new IdentityVerification(null,"",Set.of(),"SIGNED_PROOF_REJECTED"); }
            return resolveTransportIdentity(uuid,name,identity.provider());
        },identity->{
            var state=c.session().snapshot(); var operation=new AuthSession.Operation(connection,state.generation());
            if(identity.account()==null) { c.session().note(operation,identity.error()); return result(uuid,false,identity.error()); }
            if(!completeIdentity(c,identity.account(),identity.provider(),identity.pending())) return result(uuid,false,"STALE_CREDENTIALS");
            if(c.session().snapshot().admitted()) publishAdmission(c,identity.account());
            return result(uuid,true,c.session().snapshot().admitted()?"ADMITTED":"REQUIREMENTS");
        });
    }
    private void ensureInitialized() throws Exception {
        if(!active) throw new IllegalStateException("Runtime closed");
        try { initialized.join(); }
        catch(CompletionException e) {
            synchronized(bindingLock) {
                if(!active) throw new IllegalStateException("Runtime closed");
                if(initialized.isCompletedExceptionally()) {
                    store.initialize(); refreshBindingProjection();
                    initialized=CompletableFuture.completedFuture(null);
                }
            }
        }
    }
    @Override public CompletionStage<Result> refresh(UUID uuid,UUID connectionId) {
        Connection c=connection(uuid,connectionId);
        if(c==null) return CompletableFuture.completedFuture(result(uuid,false,"STALE_CONNECTION"));
        var verified=verifiedAccounts.get(uuid);
        if(verified!=null && snapshot(uuid).identityVerified()
                && c.session().snapshot().requirements().contains(AuthSnapshot.Requirement.QQ_BINDING)) {
            return submit(c,()->{
                boolean bound=store.isBound(verified.id());
                if(bound && c.session().snapshot().requirements().size()==1 && !store.admit(verified))
                    throw new IllegalStateException("Credentials changed");
                return bound;
            },bound->bound?completeRequirement(c,verified,AuthSnapshot.Requirement.QQ_BINDING):result(uuid,true,"QQ_BINDING_REQUIRED"));
        }
        if(snapshot(uuid).identityVerified()) return CompletableFuture.completedFuture(result(uuid,true,"READY"));
        String name=c.player().getName();
        return submit(c,()->{ ensureInitialized(); return store.find(name); },account->{
            var snapshot=c.session().snapshot();
            c.session().loaded(new AuthSession.Operation(connectionId,snapshot.generation()),account!=null);
            if(nativeGateway!=null) Bukkit.getScheduler().runTask(plugin,()->verifyNative(c));
            return result(uuid,true,"READY");
        });
    }
    private Connection connection(UUID playerId, UUID connectionId) {
        Connection c = connections.get(playerId);
        return c != null && Objects.equals(c.session().snapshot().connectionId(), connectionId) ? c : null;
    }
    private Result result(UUID player, boolean ok, String code) { return new Result(ok, code, snapshot(player)); }
    private void main(Runnable action) {
        if (!active) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (active) action.run(); });
    }
    private void executeReady(Runnable action,java.util.function.Consumer<RuntimeException> rejected) {
        initialized.whenComplete((ignored,error)->{
            if(!active) { rejected.accept(new CancellationException("Runtime closed")); return; }
            try { worker.execute(action); }
            catch(RejectedExecutionException e) { rejected.accept(e); }
        });
    }
    private <T> CompletionStage<Result> submit(Connection c, Callable<T> operation,
            java.util.function.Function<T, Result> completion, char[]... secrets) {
        UUID uuid = c.player().getUniqueId();
        CompletableFuture<Result> future = new CompletableFuture<>();
        if (!active || !busy.add(uuid)) {
            for (char[] secret : secrets) Arrays.fill(secret, '\0');
            return CompletableFuture.completedFuture(result(uuid, false, "BUSY"));
        }
        AuthSession.Operation ticket;
        try { ticket = c.session().begin(); }
        catch(IllegalStateException e) {
            busy.remove(uuid);
            for(char[] secret:secrets) Arrays.fill(secret,'\0');
            return CompletableFuture.completedFuture(result(uuid,false,"STALE_CONNECTION"));
        }
        futures.add(future);
        secretBuffers.put(future,secrets);
        executeReady(() -> {
                running.add(future);
                T value = null; Throwable failure = null;
                try { if (active && c.session().current(ticket)) value = operation.call(); }
                catch (Exception | LinkageError e) { failure = e; }
                finally {
                    for (char[] secret : secrets) Arrays.fill(secret, '\0');
                    secretBuffers.remove(future); running.remove(future);
                }
                final T answer = value; final Throwable error = failure;
                if (!active) { future.complete(result(uuid, false, "CLOSED")); futures.remove(future); return; }
                main(() -> {
                    try {
                        if (connections.get(uuid) != c || !c.session().current(ticket)) {
                            future.complete(result(uuid, false, "STALE_CONNECTION"));
                        } else if (error != null) {
                            if(c.session().snapshot().identityVerified()) restrict(c,"AUTHORITY_UNAVAILABLE");
                            c.session().note(ticket,"UNAVAILABLE");
                            plugin.getLogger().warning("认证操作失败: " + error.getClass().getSimpleName());
                            future.complete(result(uuid, false, "UNAVAILABLE"));
                        } else future.complete(completion.apply(answer));
                    } catch (Exception e) { future.completeExceptionally(e); }
                    finally { if (connections.get(uuid) == c) busy.remove(uuid); futures.remove(future); }
                });
            }, e -> {
            busy.remove(uuid); futures.remove(future);
            secretBuffers.remove(future);
            for (char[] secret : secrets) Arrays.fill(secret, '\0');
            future.complete(result(uuid, false, "OVERLOADED"));
        });
        return future;
    }
    private void attach(Player player) {
        if(!player.getName().matches("[\\p{L}\\p{N}_]{1,16}")) {
            player.kickPlayer("当前账号名不受支持，请使用 1 至 16 个字母、数字或下划线。"); return;
        }
        UUID uuid = player.getUniqueId();
        Connection previous = connections.remove(uuid);
        if (previous != null) previous.session().close();
        verifiedAccounts.remove(uuid); presenceDates.remove(uuid); pendingPresence.remove(uuid); pendingFirst.remove(uuid); firstBusy.remove(uuid);
        busy.remove(uuid);
        Connection c = new Connection(player, new AuthSession(uuid),new java.util.concurrent.atomic.AtomicLong(System.nanoTime()+loginTimeoutNanos),new java.util.concurrent.atomic.AtomicLong());
        connections.put(uuid, c);
        String name = player.getName();
        submit(c, () -> { ensureInitialized(); return store.find(name); }, account -> {
            c.session().loaded(new AuthSession.Operation(c.session().snapshot().connectionId(), c.session().snapshot().generation()), account != null);
            if(nativeGateway!=null) Bukkit.getScheduler().runTask(plugin,()->verifyNative(c));
            return result(uuid, true, "READY");
        });
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onJoin(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Connection c = connections.get(uuid);
        if (c != null && c.player() == event.getPlayer() && connections.remove(uuid, c)) {
            c.session().close(); busy.remove(uuid);
            verifiedAccounts.remove(uuid);
            presenceDates.remove(uuid); pendingPresence.remove(uuid); pendingFirst.remove(uuid); firstBusy.remove(uuid);
        }
    }

    @Override public CompletionStage<Result> authenticate(UUID uuid, UUID connectionId, char[] password, boolean register, boolean agreed,String termsVersion) {
        Connection c = connection(uuid, connectionId);
        if(!passwordAllowed) { Arrays.fill(password,'\0'); return CompletableFuture.completedFuture(result(uuid,false,"TRUSTED_INGRESS_REQUIRED")); }
        if(register && !passwordRegistrationAllowed()) {
            Arrays.fill(password,'\0'); return CompletableFuture.completedFuture(result(uuid,false,"TRANSPORT_RECONNECT"));
        }
        if (c == null || snapshot(uuid).stage() != AuthSnapshot.Stage.AWAITING_IDENTITY
                && snapshot(uuid).stage() != AuthSnapshot.Stage.RESTRICTED) {
            Arrays.fill(password, '\0'); return CompletableFuture.completedFuture(result(uuid, false, "INVALID_STATE"));
        }
        if (password.length < (register ? minLength : 1) || password.length > maxLength) {
            Arrays.fill(password, '\0'); return CompletableFuture.completedFuture(result(uuid, false, "PASSWORD_LENGTH"));
        }
        if (requireTerms && (!agreed || !terms.version().equals(termsVersion))) {
            Arrays.fill(password, '\0'); return CompletableFuture.completedFuture(result(uuid, false, "TERMS_REQUIRED"));
        }
        String name = c.player().getName();
        return submit(c, () -> {
            var account = store.find(name);
            if (register) {
                if (account != null) return null;
                String hash=passwords.hash(password);
                if(!active) throw new CancellationException("Runtime closed");
                account = store.create(name, uuid, hash);
            } else {
                if (account == null || !account.provider().equals("local-password") || !account.profile().equals(uuid) || account.blockedUntil() > System.currentTimeMillis()) return null;
                if (!passwords.verify(password, account.hash())) {
                    store.failure(account, System.currentTimeMillis(), maxAttempts, cooldown); return null;
                }
            }
            if(requireTerms) store.recordTerms(account,terms.version());
            Set<AuthSnapshot.Requirement> pending=requireBinding && !store.isBound(account.id()) ? Set.of(AuthSnapshot.Requirement.QQ_BINDING) : Set.of();
            if (pending.isEmpty() && !store.admit(account)) return null;
            return new IdentityVerification(account,"local-password",pending,"");
        }, verified -> {
            if (verified == null) return result(uuid, false, "CREDENTIALS_REJECTED");
            var account=verified.account();
            if(!completeIdentity(c,account,"local-password",verified.pending())) return result(uuid,false,"STALE_CREDENTIALS");
            if(!verified.pending().isEmpty()) return result(uuid,true,"QQ_BINDING_REQUIRED");
            publishAdmission(c,account);
            return result(uuid, true, "ADMITTED");
        }, password);
    }
    @Override public CompletionStage<Result> changePassword(UUID uuid, UUID connectionId, char[] oldPassword, char[] newPassword) {
        if(!snapshot(uuid).provider().equals("local-password")) {
            Arrays.fill(oldPassword,'\0'); Arrays.fill(newPassword,'\0');
            return CompletableFuture.completedFuture(result(uuid,false,"EXTERNAL_PROVIDER"));
        }
        Connection c = connection(uuid, connectionId);
        if (c == null || !snapshot(uuid).admitted() || newPassword.length < minLength || newPassword.length > maxLength) {
            Arrays.fill(oldPassword, '\0'); Arrays.fill(newPassword, '\0');
            return CompletableFuture.completedFuture(result(uuid, false, "INVALID_STATE"));
        }
        String name = c.player().getName();
        return submit(c, () -> {
            var account = store.find(name);
            if (account == null || !account.id().equals(c.session().snapshot().accountId())
                    || account.blockedUntil() > System.currentTimeMillis()) return false;
            if (!passwords.verify(oldPassword, account.hash())) {
                store.failure(account, System.currentTimeMillis(), maxAttempts, cooldown); return false;
            }
            String hash=passwords.hash(newPassword);
            if(!active) throw new CancellationException("Runtime closed");
            beginSecurityWrite(account.id(),c);
            try { return store.changePassword(account,hash); }
            finally { endSecurityWrite(account.id()); }
        }, changed -> {
            if (changed) restrict(c,"password_changed");
            return result(uuid, changed, changed ? "PASSWORD_CHANGED" : "CREDENTIALS_REJECTED");
        }, oldPassword, newPassword);
    }
    @Override public CompletionStage<Result> confirmBinding(UUID uuid, UUID connectionId, String code) {
        Connection c=connection(uuid,connectionId);
        var account=verifiedAccounts.get(uuid);
        var challenge=challenges.get(code);
        if(c==null || account==null || !snapshot(uuid).identityVerified() || challenge==null
                || !challenge.account().equals(account.id()) || challenge.expires()<System.currentTimeMillis()
                ) return CompletableFuture.completedFuture(result(uuid,false,"INVALID_BINDING_CODE"));
        String name=c.player().getName();
        return submit(c,()->{
            if(challenge.expires()<System.currentTimeMillis() || !challenges.remove(code,challenge))
                throw new IllegalStateException("Binding challenge expired or consumed");
            synchronized(bindingLock) {
                store.bind(account,name,challenge.qq());
                publishBinding(account,true);
                updateBinding(account.id());
                if(!c.session().snapshot().requirements().contains(AuthSnapshot.Requirement.TERMS) && !store.admit(account))
                    throw new IllegalStateException("Credentials changed");
            }
            return true;
        },ok->completeRequirement(c,account,AuthSnapshot.Requirement.QQ_BINDING)
        );
    }
    private IdentityVerification resolveTransportIdentity(UUID uuid,String name,String provider) throws Exception {
        String namespace="minecraft/"+provider;
        var account=store.findIdentity(uuid,namespace); var named=store.find(name);
        if(named!=null && (account==null || !named.id().equals(account.id())))
            return new IdentityVerification(null,"",Set.of(),"IDENTITY_LINK_REQUIRED");
        if(account==null) account=store.createIdentity(name,uuid,"",namespace);
        synchronized(bindingLock) { if(store.renameVerifiedProfile(account,name)) updateBinding(account.id()); }
        Set<AuthSnapshot.Requirement> pending=new HashSet<>();
        if(requireTerms && !store.acceptedTerms(account.id(),terms.version())) pending.add(AuthSnapshot.Requirement.TERMS);
        if(requireBinding && !store.isBound(account.id())) pending.add(AuthSnapshot.Requirement.QQ_BINDING);
        if(pending.isEmpty() && !store.admit(account)) return new IdentityVerification(null,"",Set.of(),"STALE_CREDENTIALS");
        return new IdentityVerification(account,provider,Set.copyOf(pending),"");
    }
    private record IdentityVerification(JdbcAuthStore.Account account,String provider,Set<AuthSnapshot.Requirement> pending,String error) {}
    private void verifyNative(Connection c) {
        if(!active || nativeGateway==null || connections.get(c.player().getUniqueId())!=c || c.session().snapshot().identityVerified()) return;
        UUID uuid=c.player().getUniqueId(),connection=c.session().snapshot().connectionId();
        UUID challenge=c.session().challengeId();
        String name=c.player().getName();
        submit(c,()->{
            xuanmo.arcartxsuite.auth.minecraft.HandshakeReceipts.Claim claim;
            try { claim=nativeGateway.claim(uuid,name,challenge,realm); }
            catch(Exception e) { return new IdentityVerification(null,"",Set.of(),"TRANSPORT_RECONNECT"); }
            return resolveTransportIdentity(uuid,name,claim.receipt().provider());
        },identity->{
            var snapshot=c.session().snapshot(); var ticket=new AuthSession.Operation(connection,snapshot.generation());
            if(identity.account()==null) { c.session().note(ticket,identity.error()); return result(uuid,false,identity.error()); }
            var account=identity.account();
            if(!completeIdentity(c,account,identity.provider(),identity.pending())) return result(uuid,false,"STALE_CREDENTIALS");
            if(c.session().snapshot().admitted()) publishAdmission(c,account);
            return result(uuid,true,c.session().snapshot().admitted()?"ADMITTED":"REQUIREMENTS");
        });
    }
    @Override public CompletionStage<Result> acceptTerms(UUID uuid,UUID connection,String version) {
        Connection c=connection(uuid,connection);
        var account=verifiedAccounts.get(uuid);
        if(c==null || account==null || !snapshot(uuid).identityVerified() || !terms.version().equals(version)
                || !c.session().snapshot().requirements().contains(AuthSnapshot.Requirement.TERMS))
            return CompletableFuture.completedFuture(result(uuid,false,"INVALID_STATE"));
        return submit(c,()->{
            store.recordTerms(account,version);
            if(c.session().snapshot().requirements().size()==1 && !store.admit(account)) throw new IllegalStateException("Credentials changed");
            return true;
        },ok->completeRequirement(c,account,AuthSnapshot.Requirement.TERMS)
        );
    }
    private void publishAdmission(Connection connection,JdbcAuthStore.Account account) {
        var snapshot=connection.session().snapshot();
        Bukkit.getPluginManager().callEvent(new AuthAdmissionEvent(snapshot));
        EventBusCapability bus=events.get();
        if(bus!=null) {
            var payload=Map.of("account_id",account.id().toString(),"connection_id",snapshot.connectionId().toString(),
                "event_id",snapshot.connectionId().toString(),"provider",snapshot.provider());
            bus.publish("axs.auth.admission_granted",connection.player(),payload);
        }
        if(!account.firstAdmitted()) { pendingFirst.put(snapshot.playerId(),account); deliverFirst(connection); }
    }
    private void deliverFirst(Connection connection) {
        UUID profile=connection.player().getUniqueId(); var account=pendingFirst.get(profile); var bus=events.get();
        if(account==null || bus==null || !snapshot(profile).admitted() || !firstBusy.add(profile)) return;
        var state=snapshot(profile);
        bus.publishConfirmed("axs.auth.profile_first_admitted",connection.player(),Map.of("account_id",account.id().toString(),
            "event_id",realm+":"+account.id()+":first","provider",state.provider())).whenComplete((confirmed,error)->{
            if(error!=null || !Boolean.TRUE.equals(confirmed)) { main(()->firstBusy.remove(profile)); return; }
            try { worker.execute(()->{
                boolean accepted=false;
                try { if(active) { store.acknowledgeFirst(account); accepted=true; } }
                catch(Exception ignored) { }
                boolean acknowledged=accepted;
                main(()->{ firstBusy.remove(profile); if(acknowledged) pendingFirst.remove(profile,account); });
            }); } catch(RejectedExecutionException e) { main(()->firstBusy.remove(profile)); }
        });
    }

    private List<BindingService.Binding> bindingProjection() throws java.sql.SQLException {
        return store.bindings().stream().map(b->new BindingService.Binding(b.accountId(),b.profileId(),b.profileName(),b.qqId(),b.boundAt())).toList();
    }
    private void refreshBindingProjection() throws java.sql.SQLException {
        long checkedAt=System.nanoTime(); bindings=bindingProjection(); bindingsDeadline=checkedAt+15_000_000_000L;
    }
    private void updateBinding(UUID account) throws java.sql.SQLException {
        var value=store.bindingByAccount(account);
        var updated=new ArrayList<>(bindings);
        updated.removeIf(binding->binding.accountId().equals(account));
        if(value!=null) updated.add(new BindingService.Binding(value.accountId(),value.profileId(),value.profileName(),value.qqId(),value.boundAt()));
        bindings=List.copyOf(updated);
    }
    @Override public List<BindingService.Binding> snapshot() {
        if(!active) return List.of();
        long now=System.nanoTime();
        if(now-bindingsDeadline>=-5_000_000_000L && refreshingBindings.compareAndSet(false,true)) {
            bindingWork(()->{
                refreshBindingProjection();
                return new BindingService.BindingResult(true,"");
            }).whenComplete((ignored,error)->refreshingBindings.set(false));
        }
        return now-bindingsDeadline<0?bindings:List.of();
    }
    private CompletionStage<BindingService.BindingResult> bindingWork(Callable<BindingService.BindingResult> work) {
        var future=new CompletableFuture<BindingService.BindingResult>();
        if(!active) return CompletableFuture.completedFuture(new BindingService.BindingResult(false,"认证服务已关闭"));
        bindingFutures.add(future);
        future.whenComplete((r,e)->bindingFutures.remove(future));
        executeReady(()->{
            try {
                ensureInitialized();
                BindingService.BindingResult result;
                synchronized(bindingLock) { if(!active) throw new CancellationException("Runtime closed"); result=work.call(); }
                if(active) main(()->future.complete(result));
                else future.complete(new BindingService.BindingResult(false,"认证服务已关闭"));
            }
            catch(Exception | LinkageError e) {
                var failed=new BindingService.BindingResult(false,"绑定服务暂时不可用");
                if(active) main(()->future.complete(failed)); else future.complete(failed);
            }
        },e->future.complete(new BindingService.BindingResult(false,"服务繁忙，请稍后重试")));
        return future;
    }
    @Override public CompletionStage<BindingService.BindingResult> requestFromQq(long qqId,String name) {
        return bindingWork(()->{
            if(qqId<=0 || name==null || !name.matches("[\\p{L}\\p{N}_]{1,16}")) return new BindingService.BindingResult(false,"请求无效");
            var account=store.find(name);
            if(account==null) return new BindingService.BindingResult(false,"请先在游戏内注册并验证身份");
            if(store.isBound(account.id()) || store.bindingByQq(qqId)!=null) return new BindingService.BindingResult(false,"账号或QQ已经绑定");
            long now=System.currentTimeMillis();
            challenges.entrySet().removeIf(e->e.getValue().expires()<now);
            if(challenges.size()>=1024) return new BindingService.BindingResult(false,"绑定请求过多，请稍后重试");
            if(challenges.values().stream().anyMatch(c->c.account().equals(account.id()) || c.qq()==qqId))
                return new BindingService.BindingResult(false,"已有有效验证码，请等待或使用原验证码");
            byte[] random=new byte[9]; new java.security.SecureRandom().nextBytes(random);
            String code=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            challenges.put(code,new Challenge(account.id(),qqId,now+300_000));
            return new BindingService.BindingResult(true,code);
        });
    }
    private void publishBinding(JdbcAuthStore.Account account,boolean bound) {
        main(()->{
            var bus=events.get();
            if(bus!=null) bus.publish("axs.auth.binding_changed",Bukkit.getPlayer(account.profile()),Map.of(
                "account_id",account.id().toString(),"profile_id",account.profile().toString(),"bound",Boolean.toString(bound),
                "event_id",account.id()+":"+account.version()+":"+bound));
        });
    }
    private BindingService.BindingResult removeBinding(BindingService.Binding binding) throws Exception {
        if(binding==null) return new BindingService.BindingResult(false,"未找到绑定");
        var before=store.findById(binding.accountId());
        if(before==null) return new BindingService.BindingResult(false,"未找到账号");
        beginSecurityWrite(before.id());
        bindings=bindings.stream().filter(value->!value.accountId().equals(binding.accountId())).toList();
        challenges.entrySet().removeIf(entry->entry.getValue().account().equals(binding.accountId()) || entry.getValue().qq()==binding.qqId());
        try { store.unbind(binding.accountId()); }
        finally { endSecurityWrite(before.id()); }
        updateBinding(binding.accountId());
        var updated=store.findById(binding.accountId());
        if(updated!=null) invalidateVersion(updated.id(),updated.version()-1);
        if(updated!=null) publishBinding(updated,false);
        main(()->{
            for(Connection c:connections.values()) if(binding.accountId().equals(c.session().snapshot().accountId())) restrict(c,"binding_removed");
        });
        return new BindingService.BindingResult(true,binding.profileName());
    }
    @Override public CompletionStage<BindingService.BindingResult> revokeByQq(long qq) {
        return bindingWork(()->removeBinding(project(store.bindingByQq(qq))));
    }
    @Override public CompletionStage<BindingService.BindingResult> revokeByProfile(UUID profile) {
        return bindingWork(()->removeBinding(project(store.bindingByProfile(profile))));
    }
    @Override public CompletionStage<Void> revoke(UUID account, String reason) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("Runtime closed"));
        revocationFutures.add(done);
        done.whenComplete((result,error)->revocationFutures.remove(done));
        executeReady(()->{
            try {
                ensureInitialized();
                var before=store.findById(account);
                if(before==null) throw new IllegalArgumentException("Account not found");
                beginSecurityWrite(account);
                try { store.invalidate(account); }
                finally { endSecurityWrite(account); }
                if(!active) { done.completeExceptionally(new IllegalStateException("Runtime closed")); return; }
                main(()->{
                    for(Connection c:connections.values()) if(account.equals(c.session().snapshot().accountId())) restrict(c,reason);
                    done.complete(null);
                });
            } catch(Exception | LinkageError e) { done.completeExceptionally(e); }
        },done::completeExceptionally);
        return done;
    }
    private static BindingService.Binding project(JdbcAuthStore.Binding value) {
        return value==null?null:new BindingService.Binding(value.accountId(),value.profileId(),value.profileName(),value.qqId(),value.boundAt());
    }
    private void beginSecurityWrite(UUID account) {
        beginSecurityWrite(account,null);
    }
    private void beginSecurityWrite(UUID account,Connection initiator) {
        synchronized(invalidationLock) {
            securityWriters.merge(account,1,Integer::sum);
            pendingRevocations.put(account,revocationSequence.incrementAndGet());
            for(var entry:verifiedAccounts.entrySet()) if(entry.getValue().id().equals(account)) {
                Connection connection=connections.get(entry.getKey());
                if(connection==null) continue;
                var state=connection.session().snapshot();
                connection.session().pauseEligibility();
                if(connection!=initiator) main(()->{ if(connection.session().current(new AuthSession.Operation(state.connectionId(),state.generation()))) restrict(connection,"SECURITY_CHANGE_PENDING"); });
            }
        }
    }
    private void endSecurityWrite(UUID account) {
        securityWriters.computeIfPresent(account,(key,count)->count==1?null:count-1);
        reconcileRevocation(account);
    }
    /** A failed commit may be ambiguous. Keep admission closed until an authoritative version can be read. */
    private void reconcileRevocation(UUID account) {
        Long epoch=pendingRevocations.get(account);
        if(epoch==null || securityWriters.containsKey(account)) return;
        try {
            var current=store.findById(account);
            if(current==null) return;
            synchronized(bindingLock) { updateBinding(account); }
            synchronized(invalidationLock) {
                if(!securityWriters.containsKey(account) && Objects.equals(epoch,pendingRevocations.get(account))) {
                    invalidateVersion(account,current.version()-1);
                    pendingRevocations.remove(account,epoch);
                }
            }
        } catch(Exception e) { /* Timer retries; uncertain commits never restore an old authority. */ }
    }
    @Override public void awaitPrevious(CompletionStage<Void> previous) { predecessor=previous; }
    @Override public CompletionStage<Void> drained() { return drained; }
    @Override public void close() {
        active = false;
        pendingFirst.clear(); firstBusy.clear(); pendingPresence.clear();
        initialized.completeExceptionally(new IllegalStateException("Runtime closed"));
        if (presenceTask != null) presenceTask.cancel();
        if(proxyIngress!=null) proxyIngress.close();
        HandlerList.unregisterAll(this);
        connections.values().forEach(c -> c.session().close()); connections.clear(); busy.clear();
        for (var f : futures) f.complete(new Result(false, "CLOSED", AuthSnapshot.absent(new UUID(0, 0))));
        futures.clear(); worker.shutdownNow(); securityChecks.shutdownNow();
        secretBuffers.forEach((future,buffers)->{
            if(!running.contains(future)) for(char[] buffer:buffers) Arrays.fill(buffer,'\0');
        });
        for(var future:bindingFutures) future.complete(new BindingService.BindingResult(false,"认证服务已关闭"));
        bindingFutures.clear();
        for(var future:revocationFutures) future.completeExceptionally(new IllegalStateException("Runtime closed"));
        revocationFutures.clear();
    }
}
