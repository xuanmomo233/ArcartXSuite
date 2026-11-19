package xuanmo.arcartxsuite.auth.runtime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.*;
import xuanmo.arcartxsuite.api.capability.*;
import xuanmo.arcartxsuite.module.SimpleEventBus;

/** Normal server admission while the LoginView module is absent, disabled, or unloaded. */
final class ServerAdmissionRuntime implements HostAuthentication,Listener {
    private static final String PRESENCE_TOPIC = "axs.auth.presence_day_started";
    private record Connection(Player player,AuthSnapshot snapshot,long joinedAt) {}
    private static final class DailyPresence {
        final String date;
        CompletableFuture<Boolean> receipt;

        DailyPresence(String date) { this.date = date; }
    }

    private final JavaPlugin plugin;
    private final Supplier<EventBusCapability> events;
    private final ZoneId timezone;
    private final Map<UUID,Connection> connections=new ConcurrentHashMap<>();
    private final Map<UUID,DailyPresence> dailyPresence=new HashMap<>();
    private BukkitTask presenceTask;
    private boolean active;

    ServerAdmissionRuntime(JavaPlugin plugin,Supplier<EventBusCapability> events,ZoneId timezone) {
        this.plugin=plugin;
        this.events=events;
        this.timezone=timezone;
    }

    @Override public boolean requiresAdmission() { return false; }

    @Override public void start() {
        active=true;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers()) attach(player,false);
        presenceTask=Bukkit.getScheduler().runTaskTimer(plugin,this::presence,20,20);
    }

    private void attach(Player player,boolean firstJoin) {
        UUID profile=player.getUniqueId();
        var state=new AuthSnapshot(profile,UUID.randomUUID(),0,null,AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED,"server",Set.of(),"SERVER_ADMITTED");
        var connection=new Connection(player,state,System.nanoTime());
        connections.put(profile,connection);
        dailyPresence.remove(profile);
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!active || connections.get(profile)!=connection || !player.isOnline()) return;
            Bukkit.getPluginManager().callEvent(new AuthAdmissionEvent(state));
            var bus=events.get();
            if(bus!=null) {
                bus.publish("axs.auth.admission_granted",player,Map.of("profile_id",profile.toString(),
                    "connection_id",state.connectionId().toString(),"event_id",state.connectionId().toString(),"provider","server"));
                if(firstJoin) bus.publish("axs.auth.profile_first_admitted",player,
                    Map.of("profile_id",profile.toString(),"event_id","server:"+profile+":first","provider","server"));
            }
        });
    }

    private void presence() {
        var bus=events.get();
        if(!active || bus==null || !bus.hasSubscribers(PRESENCE_TOPIC)) return;
        var localBus=bus instanceof SimpleEventBus local ? local : null;
        boolean hasConfirmed=localBus==null || localBus.hasConfirmedSubscriber(PRESENCE_TOPIC);
        String date=LocalDate.now(timezone).toString();
        for(var entry:connections.entrySet()) {
            UUID profile=entry.getKey();
            Connection connection=entry.getValue();
            if(connections.get(profile)!=connection || !connection.player().isOnline()) continue;
            var fact=dailyPresence.get(profile);
            if(fact==null || !fact.date.equals(date)) {
                fact=new DailyPresence(date);
                dailyPresence.put(profile,fact);
            }
            var previous=fact.receipt;
            if(previous!=null && (!previous.isDone()
                || (!previous.isCompletedExceptionally() && Boolean.TRUE.equals(previous.getNow(false)))
                || !hasConfirmed)) continue;
            var payload=Map.of("profile_id",profile.toString(),"business_date",date,"timezone",timezone.getId(),
                "event_id","server:"+profile+":"+date,"provider","server");
            try {
                var receipt = previous==null && localBus!=null
                    ? localBus.publishAuthFact(PRESENCE_TOPIC,connection.player(),payload)
                    : bus.publishConfirmed(PRESENCE_TOPIC,connection.player(),payload);
                fact.receipt=receipt.toCompletableFuture();
            } catch(RuntimeException | LinkageError failure) {
                fact.receipt=CompletableFuture.completedFuture(false);
                plugin.getLogger().warning("登录任务回执未确认，将重新提交：" + failure.getClass().getSimpleName());
            }
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) {
        attach(event.getPlayer(),!event.getPlayer().hasPlayedBefore());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID profile=event.getPlayer().getUniqueId();
        var connection=connections.get(profile);
        if(connection!=null && connection.player()==event.getPlayer() && connections.remove(profile,connection))
            dailyPresence.remove(profile);
    }

    @Override public AuthSnapshot snapshot(UUID profile) {
        var connection=connections.get(profile);
        return connection==null?AuthSnapshot.absent(profile):connection.snapshot();
    }

    @Override public long eligibleNanos(UUID profile) {
        var connection=connections.get(profile);
        return connection==null?0:Math.max(0,System.nanoTime()-connection.joinedAt());
    }

    @Override public Terms terms() { return new Terms(false,"",List.of()); }
    @Override public boolean passwordRegistrationAllowed() { return false; }
    @Override public boolean passwordAuthenticationAllowed() { return false; }

    private CompletionStage<Result> disabled(UUID profile) {
        return CompletableFuture.completedFuture(new Result(false,"AUTH_DISABLED",snapshot(profile)));
    }

    @Override public CompletionStage<Result> refresh(UUID profile,UUID connectionId) {
        var state=snapshot(profile);
        boolean current=state.admitted() && Objects.equals(connectionId,state.connectionId());
        return CompletableFuture.completedFuture(new Result(current,current?"SERVER_ADMITTED":"STALE_CONNECTION",state));
    }

    @Override public CompletionStage<Result> authenticate(UUID profile,UUID connectionId,char[] password,boolean register,boolean agreed,String version) {
        Arrays.fill(password,'\0');
        return disabled(profile);
    }

    @Override public CompletionStage<Result> changePassword(UUID profile,UUID connectionId,char[] oldPassword,char[] newPassword) {
        Arrays.fill(oldPassword,'\0');
        Arrays.fill(newPassword,'\0');
        return disabled(profile);
    }

    @Override public CompletionStage<Result> confirmBinding(UUID profile,UUID connectionId,String code) { return disabled(profile); }
    @Override public CompletionStage<Result> acceptTerms(UUID profile,UUID connectionId,String version) { return disabled(profile); }
    @Override public CompletionStage<Void> revoke(UUID account,String reason) {
        return CompletableFuture.failedFuture(new IllegalStateException("Suite authentication is disabled"));
    }
    @Override public List<BindingService.Binding> snapshot() { return List.of(); }
    private CompletionStage<BindingService.BindingResult> bindingDisabled() {
        return CompletableFuture.completedFuture(new BindingService.BindingResult(false,"Suite 认证未启用"));
    }
    @Override public CompletionStage<BindingService.BindingResult> requestFromQq(long qq,String name) { return bindingDisabled(); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByQq(long qq) { return bindingDisabled(); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByProfile(UUID profile) { return bindingDisabled(); }

    @Override public void close() {
        active=false;
        if(presenceTask!=null) presenceTask.cancel();
        HandlerList.unregisterAll(this);
        connections.clear();
        dailyPresence.clear();
    }
}
