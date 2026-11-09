package xuanmo.arcartxsuite.eventpacket.service;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketAction;
import xuanmo.arcartxsuite.eventpacket.storage.EventPacketRepository;

/** Durable intake precedes source acknowledgement. Interrupted side effects require explicit review. */
public final class FirstAdmissionInbox implements AutoCloseable {
    public record Step(String rule,EventPacketAction action) {}
    private static final Gson JSON=new Gson();
    private final JavaPlugin plugin;
    private final EventPacketRepository repository;
    private final EventPacketDispatchService dispatch;
    private final Logger logger;
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),
        r->{ Thread t=new Thread(r,"AXS-EventPacketAuth"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final Set<UUID> busy=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Object> pendingProfiles=new ConcurrentHashMap<>();
    private record PendingAck(EventPacketRepository.AuthFact fact,String state) {}
    private final Map<UUID,PendingAck> pendingAcks=new ConcurrentHashMap<>();
    private final Set<UUID> savingAcks=ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> futures=ConcurrentHashMap.newKeySet();
    private volatile boolean active=true;
    private volatile boolean recovered;
    private final BukkitTask tick;

    public FirstAdmissionInbox(JavaPlugin plugin,EventPacketRepository repository,EventPacketDispatchService dispatch,Logger logger) {
        this.plugin=plugin; this.repository=repository; this.dispatch=dispatch; this.logger=logger;
        tick=Bukkit.getScheduler().runTaskTimer(plugin,this::drain,20,20);
    }
    public CompletionStage<Void> accept(EventBusCapability.BusEvent event) {
        Player player=event.player();
        if(!active || player==null || !AuthAccess.admitted(player.getUniqueId())) return CompletableFuture.failedFuture(new IllegalStateException("Player unavailable"));
        UUID id=UUID.nameUUIDFromBytes(event.payload().get("event_id").getBytes(StandardCharsets.UTF_8));
        UUID profile=player.getUniqueId();
        String plan=JSON.toJson(dispatch.firstAdmissionPlan(player));
        if(plan.getBytes(StandardCharsets.UTF_8).length>32768) return CompletableFuture.failedFuture(new IllegalArgumentException("First admission plan exceeds 32 KiB"));
        return submit(()->{ repository.enqueueAuthFact(id,profile,plan); pendingProfiles.put(profile,new Object()); return null; });
    }
    private <T> CompletableFuture<T> submit(Callable<T> work) {
        var future=new CompletableFuture<T>(); futures.add(future);
        future.whenComplete((value,error)->futures.remove(future));
        if(!active) { future.completeExceptionally(new CancellationException("Inbox closed")); return future; }
        try { worker.execute(()->{ try { if(!active) throw new CancellationException("Inbox closed"); future.complete(work.call()); }
            catch(Exception e) { future.completeExceptionally(e); } }); }
        catch(RejectedExecutionException e) { future.completeExceptionally(e); }
        return future;
    }
    private void drain() {
        if(!active) return;
        pendingAcks.values().forEach(this::saveAck);
        if(!recovered) {
            if(!busy.add(new UUID(0,0))) return;
            submit(()->{ repository.recoverAuthSteps(); repository.pendingAuthProfiles().forEach(profile->pendingProfiles.put(profile,new Object())); recovered=true; return null; })
                .whenComplete((ignored,error)->busy.remove(new UUID(0,0)));
            return;
        }
        for(var entry:pendingProfiles.entrySet()) {
            UUID profile=entry.getKey(); Object marker=entry.getValue(); Player player=Bukkit.getPlayer(profile);
            if(player==null || !AuthAccess.admitted(profile) || !busy.add(profile)) continue;
            submit(()->{
                var fact=repository.nextAuthFact(profile);
                if(fact==null) { pendingProfiles.remove(profile,marker); return null; }
                if(!repository.claimAuthStep(fact.id(),fact.step())) return null;
                return fact;
            }).whenComplete((fact,error)->{
                if(error!=null || fact==null || !active) { busy.remove(profile); return; }
                Bukkit.getScheduler().runTask(plugin,()->{
                    String outcome="REVIEW";
                    try {
                        if(!active || !player.isOnline() || !AuthAccess.admitted(profile)) outcome="RETRY";
                        else {
                            Step[] plan=JSON.fromJson(fact.plan(),Step[].class);
                            if(fact.step()>=plan.length) outcome="DONE";
                            else if(dispatch.executeFirstAdmissionStep(player,plan[fact.step()])) outcome=fact.step()+1==plan.length?"DONE":"PENDING";
                        }
                    } catch(Exception | LinkageError ignored) { }
                    String state=outcome;
                    var ack=new PendingAck(fact,state); pendingAcks.put(fact.id(),ack); saveAck(ack);
                });
            });
        }
    }
    private void saveAck(PendingAck ack) {
        var fact=ack.fact();
        if(!savingAcks.add(fact.id())) return;
        submit(()->{ repository.finishAuthStep(fact.id(),fact.step(),ack.state()); return null; }).whenComplete((ignored,error)->{
            savingAcks.remove(fact.id()); busy.remove(fact.player());
            if(error==null) {
                pendingAcks.remove(fact.id(),ack);
                if(ack.state().equals("REVIEW")) logger.warning("首次准入动作待人工核对: fact="+fact.id()+" step="+fact.step());
            }
        });
    }
    public CompletionStage<List<EventPacketRepository.AuthFact>> review(UUID profile) { return submit(()->repository.reviewAuthFacts(profile)); }
    public CompletionStage<Boolean> resolve(UUID fact,boolean retry) { return submit(()->{
        boolean changed=repository.resolveAuthStep(fact,retry);
        if(changed) repository.pendingAuthProfiles().forEach(profile->pendingProfiles.put(profile,new Object()));
        return changed;
    }); }
    public void reviewCommand(org.bukkit.command.CommandSender sender,String[] args) {
        if(!sender.hasPermission("arcartxsuite.admin")) { sender.sendMessage("没有管理权限。"); return; }
        if(args.length!=4 || !Set.of("list","retry","skip").contains(args[2])) {
            sender.sendMessage("/axs eventpacket auth-receipt list <玩家UUID> | retry/skip <事件UUID>"); return;
        }
        try {
            UUID id=UUID.fromString(args[3]);
            CompletionStage<String> result=args[2].equals("list")?review(id).thenApply(rows->rows.isEmpty()?"没有待核对动作。":rows.stream()
                .map(row->row.id()+" step="+row.step()+" "+row.state()).collect(java.util.stream.Collectors.joining("\n")))
                :resolve(id,args[2].equals("retry")).thenApply(changed->changed?"回执已处理；玩家完成准入后继续执行。":"未找到待核对回执。");
            result.whenComplete((message,error)->{ if(active) Bukkit.getScheduler().runTask(plugin,()->sender.sendMessage(error==null?message:"回执存储暂时不可用。")); });
        } catch(IllegalArgumentException e) { sender.sendMessage("UUID 格式无效。"); }
    }
    @Override public void close() {
        active=false; tick.cancel(); worker.shutdownNow();
        futures.forEach(future->future.completeExceptionally(new CancellationException("Inbox closed"))); futures.clear();
    }
}
