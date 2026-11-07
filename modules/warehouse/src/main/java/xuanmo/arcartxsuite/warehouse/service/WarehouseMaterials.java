package xuanmo.arcartxsuite.warehouse.service;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.*;

/** Durable escrow. Uncertain native-container deliveries are never replayed. */
public final class WarehouseMaterials implements WarehouseMaterialAccess, Listener, AutoCloseable {
    private final WarehouseService service;
    private final JavaPlugin plugin;
    private final String serverId, runId=UUID.randomUUID().toString();
    private final Map<Player,Snapshot> issued=new WeakHashMap<>();
    private final Map<UUID,String> active=new ConcurrentHashMap<>();
    private final Set<UUID> notified=new HashSet<>();
    private final Map<Player,List<Consumer<Snapshot>>> reads=new HashMap<>();
    private final Gson gson=new Gson();
    private final ThreadPoolExecutor queue;
    private final BukkitTask recovery;
    private final java.nio.file.Path evidenceFolder;
    private volatile boolean closing;

    public WarehouseMaterials(WarehouseService service,JavaPlugin plugin,java.io.File folder,boolean mysql) throws java.io.IOException {
        this.service=service;this.plugin=plugin;
        evidenceFolder=folder.toPath().resolve("material-transfer-evidence");
        var identity=folder.toPath().resolve("material-transfer-server.id");
        if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString(),StandardCharsets.UTF_8);
        serverId=UUID.fromString(Files.readString(identity,StandardCharsets.UTF_8).trim()).toString();
        int workers=mysql?2:1;
        queue=new ThreadPoolExecutor(workers,workers,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"AXS-Material-Transfers");t.setDaemon(true);return t;});
        recovery=Bukkit.getScheduler().runTaskTimer(plugin,()->enqueue(this::recoverBatch),1L,600L);
    }
    private void log(Exception e){plugin.getLogger().log(Level.WARNING,"仓库材料转移凭证处理失败",e);}
    private boolean enqueue(Runnable task){if(closing)return false;try{queue.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
    private void main(Runnable task){if(!closing&&plugin.isEnabled())try{Bukkit.getScheduler().runTask(plugin,()->{if(!closing)task.run();});}catch(IllegalStateException ignored){}}
    private String check(Player p){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Material access requires main thread");
        if(closing||p==null||!p.isOnline())return "材料服务不可用";
        return service.isSecondaryUnlocked(p)?"":"仓库尚未解锁";
    }
    @Override public Snapshot materials(Player p){
        String reason=check(p);if(!reason.isEmpty())return new Snapshot("",List.of(),reason);
        try{
            service.ensureEntitlements(p);var owned=service.personalWarehouseMap(p.getUniqueId());
            String id=service.configuration.warehouses().keySet().stream().filter(owned::containsKey).findFirst().orElse("");
            if(id.isEmpty())return new Snapshot("",List.of(),"没有可用个人仓库");
            List<Entry> entries=new ArrayList<>();
            for(var row:service.repository.loadSlots(WarehouseService.OWNER_PERSONAL,p.getUniqueId().toString(),id)){
                var item=service.deserializeSlotItem(row);
                if(item!=null&&!item.getType().isAir()&&row.amount()>0)entries.add(new Entry(row.slot(),row.itemHash(),row.createdAt(),row.amount(),item));
            }
            Snapshot snapshot=new Snapshot(id,entries,"");issued.put(p,snapshot);return snapshot;
        }catch(Exception e){log(e);return new Snapshot("",List.of(),"读取仓库材料失败");}
    }
    @Override public Result withdrawMaterials(Player p,Snapshot snapshot,List<Request> requests){return new Result(false,List.of(),"请使用带凭证的 prepareMaterials 接口");}
    @Override public void materialsAsync(Player p,Consumer<Snapshot> callback){
        String error=check(p);if(!error.isEmpty()){callback.accept(new Snapshot("",List.of(),error));return;}
        UUID owner=p.getUniqueId();
        var waiting=reads.get(p);
        if(waiting!=null){if(waiting.size()<4)waiting.add(callback);else callback.accept(new Snapshot("",List.of(),"材料读取繁忙"));return;}
        reads.put(p,new ArrayList<>(List.of(callback)));
        if(!enqueue(()->{
            String warehouse="",failure="";List<SlotItemRecord> rows=List.of();
            try{
                var owned=service.repository.loadPersonalWarehouses(owner);
                warehouse=service.configuration.warehouses().keySet().stream().filter(id->owned.stream().anyMatch(w->w.warehouseId().equals(id))).findFirst().orElse("");
                if(warehouse.isEmpty())failure="没有可用个人仓库";
                else rows=service.repository.loadSlots(WarehouseService.OWNER_PERSONAL,owner.toString(),warehouse);
            }catch(Exception e){log(e);failure="读取仓库材料失败";}
            String id=warehouse,message=failure;List<SlotItemRecord> records=rows;
            main(()->{
                var callbacks=reads.remove(p);if(callbacks==null)return;
                List<Entry> entries=new ArrayList<>();String reason=check(p);
                if(reason.isEmpty())reason=message;
                if(reason.isEmpty())try{for(var row:records){var item=service.deserializeSlotItem(row);if(item!=null&&!item.getType().isAir()&&row.amount()>0)entries.add(new Entry(row.slot(),row.itemHash(),row.createdAt(),row.amount(),item));}}
                catch(RuntimeException e){reason="物品材料解码失败";}
                Snapshot snapshot=new Snapshot(id,entries,reason);if(snapshot.available())issued.put(p,snapshot);
                for(var cb:callbacks)try{cb.accept(snapshot);}catch(RuntimeException e){log(e);}
            });
        })){reads.remove(p);callback.accept(new Snapshot("",List.of(),"材料队列已满"));}
    }
    @Override public void prepareMaterials(Player p,Snapshot snapshot,List<Request> requests,Target target,Consumer<Prepared> callback){
        String error=check(p);
        if(!error.isEmpty()){callback.accept(new Prepared(false,"",error));return;}
        if(snapshot==null||!snapshot.available()||issued.remove(p)!=snapshot||requests==null||requests.isEmpty()||requests.size()>9||target==null||target.adapter()==null||target.adapter().length()>64||target.identity()==null||target.identity().length()>255||target.evidence()==null||target.evidence().length()>262144){callback.accept(new Prepared(false,"","材料计划无效或已过期"));return;}
        UUID owner=p.getUniqueId();String id=UUID.randomUUID().toString();
        if(active.putIfAbsent(owner,id)!=null){callback.accept(new Prepared(false,"","上一笔填料仍在处理中"));return;}
        List<Request> copy=List.copyOf(requests);
        if(!enqueue(()->{
            Prepared result;
            try{
                Map<Integer,Integer> counts=new LinkedHashMap<>();int total=0;
                for(Request r:copy){if(r.amount()<=0||r.amount()>576)throw new IllegalArgumentException();total=Math.addExact(total,r.amount());counts.merge(r.slot(),r.amount(),Math::addExact);}
                if(total>576)throw new IllegalArgumentException();
                if(service.repository.loadPersonalWarehouses(owner).stream().noneMatch(w->w.warehouseId().equals(snapshot.warehouseId())))throw new IllegalStateException("仓库权限变化");
                List<SlotChange> changes=new ArrayList<>();
                for(var wanted:counts.entrySet()){
                    Entry expected=snapshot.entries().stream().filter(e->e.slot()==wanted.getKey()).findFirst().orElseThrow();
                    SlotItemRecord row=service.repository.loadSlot(WarehouseService.OWNER_PERSONAL,owner.toString(),snapshot.warehouseId(),wanted.getKey()).orElseThrow();
                    if(!row.itemHash().equals(expected.identity())||row.createdAt()!=expected.createdAt()||row.amount()!=expected.amount()||row.amount()<wanted.getValue())throw new IllegalStateException("仓库材料变化");
                    changes.add(new SlotChange(row,row.amount()==wanted.getValue()?null:amount(row,row.amount()-wanted.getValue())));
                }
                long now=System.currentTimeMillis();
                var transfer=new MaterialTransfer(id,owner.toString(),snapshot.warehouseId(),serverId,runId,"HELD",target.adapter(),target.identity(),target.persistence()+"\n"+target.evidence(),gson.toJson(changes),now,now,"尚未授权交付");
                boolean ok=service.repository.escrowMaterials(changes,transfer);result=new Prepared(ok,id,ok?"":"库存已变化，请刷新");
            }catch(Exception e){log(e);result=new Prepared(false,id,"取料结果需要核对；未授权交付，恢复服务会处理凭证");}
            Prepared answer=result;
            main(()->{if(!answer.success())active.remove(owner,id);callback.accept(answer);});
        })){active.remove(owner,id);callback.accept(new Prepared(false,"","材料队列已满，请稍后重试"));}
    }
    @Override public void authorizeDelivery(Player p,String id,Consumer<Boolean> callback){
        if(!check(p).isEmpty()||!id.equals(active.get(p.getUniqueId()))){cancelPrepared(p,id);callback.accept(false);return;}
        UUID owner=p.getUniqueId();
        if(!enqueue(()->{
            boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner.toString(),"HELD","APPLYING","已授权交付，禁止自动补发");}catch(Exception e){log(e);}
            boolean answer=ok;main(()->{if(!answer)active.remove(owner,id);callback.accept(answer);});
        })){cancelPrepared(p,id);callback.accept(false);}
    }
    @Override public void recordApplied(Player p,String id){
        completeDelivery(p,id,ignored -> {});
    }
    @Override public void completeDelivery(Player p,String id,Consumer<Boolean> callback){
        UUID owner=p.getUniqueId();if(!id.equals(active.get(owner))){callback.accept(false);return;}
        if(!enqueue(()->{boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner.toString(),"APPLYING","APPLIED","内存已落槽，原版持久化未经确认");}catch(Exception e){log(e);}finally{active.remove(owner,id);}boolean result=ok;main(()->callback.accept(result));})){active.remove(owner,id);callback.accept(false);}
    }
    @Override public void recordUncertain(Player p,String id,String reason){
        UUID owner=p.getUniqueId();
        if(!enqueue(()->{try{service.repository.transitionMaterialTransfer(id,owner.toString(),"APPLYING","REVIEW",reason.substring(0,Math.min(200,reason.length())));}catch(Exception e){log(e);}finally{active.remove(owner,id);}}))active.remove(owner,id);
    }
    @Override public void cancelPrepared(Player p,String id){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");UUID owner=p.getUniqueId();
        if(!enqueue(()->{try{var row=service.repository.materialTransfer(id,owner.toString());if(row.isPresent()&&row.get().state().equals("HELD"))restore(row.get());}catch(Exception e){log(e);}finally{active.remove(owner,id);}}))active.remove(owner,id);
    }
    private static SlotItemRecord amount(SlotItemRecord r,long n){return new SlotItemRecord(r.ownerType(),r.ownerId(),r.warehouseId(),r.slot(),r.itemHash(),r.categoryId(),r.displayName(),r.materialId(),r.searchText(),r.pinyin(),r.initials(),r.itemData(),r.itemJson(),n,r.createdAt(),System.currentTimeMillis(),r.loreText());}
    private void restore(MaterialTransfer row)throws Exception{
        SlotChange[] changes=gson.fromJson(row.payload(),SlotChange[].class);
        List<SlotChange> reverse=Arrays.stream(changes).map(c->new SlotChange(c.after(),c.before())).toList();
        if(!service.repository.restoreHeldMaterialTransfer(row,reverse))service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),"HELD","REVIEW","回仓位置已变化，保留材料凭证");
        active.remove(UUID.fromString(row.ownerId()),row.id());
    }
    private void recoverBatch(){
        try{
            var rows=service.repository.oldMaterialTransfers(serverId,runId,100);
            for(var row:rows)try{
                if(row.state().equals("HELD"))restore(row);
                else if(service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),row.state(),"REVIEW","上次运行遗留，原版存档无法确认，禁止自动补发"))
                    main(()->{Player p=Bukkit.getPlayer(UUID.fromString(row.ownerId()));if(p!=null)notifyReview(p);});
            }catch(Exception e){log(e);service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),row.state(),"REVIEW","恢复失败，保留完整凭证");}
            if(rows.size()==100)enqueue(this::recoverBatch);
        }catch(Exception e){log(e);}
    }
    public void inspect(String owner,Consumer<List<MaterialTransfer>> callback){
        if(!enqueue(()->{try{var rows=service.repository.materialTransfers(owner,50);main(()->callback.accept(rows));}catch(Exception e){log(e);main(()->callback.accept(List.of()));}}))callback.accept(List.of());
    }
    public void confirmReviewed(String owner,String id,String actor,Consumer<Boolean> callback){
        if(!enqueue(()->{boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner,"REVIEW","CONFIRMED","人工确认，无自动发放；操作人 "+actor);}catch(Exception e){log(e);}boolean answer=ok;main(()->callback.accept(answer));}))callback.accept(false);
    }
    private void notifyReview(Player p){if(notified.add(p.getUniqueId()))p.sendMessage("§e存在待核对的仓库材料转移凭证；系统未自动补发，请联系管理员。");}
    @EventHandler public void quit(PlayerQuitEvent event){issued.remove(event.getPlayer());notified.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent event){
        Player p=event.getPlayer();
        inspect(p.getUniqueId().toString(),rows->{if(p.isOnline() && rows.stream().anyMatch(r->r.state().equals("REVIEW")))notifyReview(p);});
    }
    public boolean handleAdminCommand(org.bukkit.command.CommandSender sender,String[] args){
        if(args.length<2 || !List.of("material-transfers","material-evidence","material-confirm").contains(args[1].toLowerCase(Locale.ROOT)))return false;
        if(!sender.hasPermission("arcartxsuite.warehouse.admin")){sender.sendMessage("没有仓库管理权限");return true;}
        if(args.length<3){sender.sendMessage("用法: /axs warehouse "+args[1]+" <玩家UUID或在线名> [凭证UUID] [confirm]");return true;}
        UUID owner;
        try{owner=UUID.fromString(args[2]);}catch(IllegalArgumentException e){Player p=Bukkit.getPlayerExact(args[2]);if(p==null){sender.sendMessage("离线玩家请使用UUID");return true;}owner=p.getUniqueId();}
        String ownerId=owner.toString();
        if(args[1].equalsIgnoreCase("material-transfers")){
            inspect(ownerId,rows->{sender.sendMessage("待处理凭证（最多50条）: "+rows.size());for(var row:rows)sender.sendMessage(row.id()+" | "+row.state()+" | "+row.adapter()+" | "+row.note());});return true;
        }
        if(args.length<4){sender.sendMessage("请提供凭证UUID");return true;}
        String id;
        try{id=UUID.fromString(args[3]).toString();}catch(IllegalArgumentException e){sender.sendMessage("凭证UUID无效");return true;}
        if(args[1].equalsIgnoreCase("material-confirm")){
            if(args.length<5 || !args[4].equalsIgnoreCase("confirm")){sender.sendMessage("人工核对后追加 confirm；此操作只确认结案，不发放物品");return true;}
            confirmReviewed(ownerId,id,sender.getName(),ok->sender.sendMessage(ok?"已人工确认，凭证保留，未发放物品":"未确认：凭证不存在或状态不是REVIEW"));return true;
        }
        String actor=sender.getName();
        if(!enqueue(()->{try{
            var found=service.repository.materialTransfer(id,ownerId);
            if(found.isEmpty()){main(()->sender.sendMessage("未找到该玩家的凭证"));return;}
            Files.createDirectories(evidenceFolder);
            var file=evidenceFolder.resolve(id+"-"+System.currentTimeMillis()+".json");
            Files.writeString(file,gson.toJson(found.get()),StandardCharsets.UTF_8);
            plugin.getLogger().info("材料凭证导出 "+id+"，操作人 "+actor);
            main(()->sender.sendMessage("完整凭证已导出: "+file));
        }catch(Exception e){log(e);main(()->sender.sendMessage("导出失败，请查看服务端日志"));}}))sender.sendMessage("队列已满，请稍后重试");
        return true;
    }
    @Override public void close(){closing=true;recovery.cancel();queue.shutdown();try{if(!queue.awaitTermination(2,TimeUnit.SECONDS))queue.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();queue.shutdownNow();}}
}
