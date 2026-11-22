package xuanmo.arcartxsuite.warehouse.service;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.inventory.InventoryOperation;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.*;
import static xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.INVENTORY_DEPOSIT;
import static xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.INVENTORY_DONE;
import static xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.INVENTORY_TRANSFER;
import static xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.INVENTORY_WITHDRAW;
import static xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.INVENTORY_WITHDRAW_RESERVED;

/** Durable escrow. Uncertain native-container deliveries are never replayed. */
public final class WarehouseMaterials implements WarehouseMaterialAccess, Listener, AutoCloseable {
    private static final NamespacedKey INVENTORY_RECEIPT = Objects.requireNonNull(
        NamespacedKey.fromString("arcartxsuite:warehouse_inventory_transfer"));
    private final WarehouseService service;
    private final JavaPlugin plugin;
    private final String serverId, runId=UUID.randomUUID().toString();
    private record Authorization(SecondaryPasswordAccess provider, SecondaryPasswordAccess.Status status) { }
    private record Issued(Snapshot snapshot, Authorization authorization) { }
    private record Transfer(String id, Authorization authorization, boolean deliveryRequested) { }
    private final Map<Player,Issued> issued=new WeakHashMap<>();
    private final Map<UUID,Transfer> active=new ConcurrentHashMap<>();
    private final Set<UUID> notified=new HashSet<>();
    private final Map<Player,List<Consumer<Snapshot>>> reads=new HashMap<>();
    private final Gson gson=new Gson();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor queue;
    private final BukkitTask recovery;
    private final java.nio.file.Path evidenceFolder;
    private final Path playerData;
    private final Set<String> activeInventoryTransfers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> checkingPreviousInventory = ConcurrentHashMap.newKeySet();
    private volatile boolean closing;

    record InventoryPart(int slot, int amount, String itemData) { }
    record CursorDeposit(InventoryView view, ItemStack source, String itemData) {
        boolean unchanged(Player player) {
            return player.getOpenInventory() == view && Objects.equals(source, player.getItemOnCursor());
        }
    }

    private record CursorEvidence(int amount, String itemData) { }
    private record InventoryPayload(List<SlotChange> changes, SharedActor actor, List<InventoryPart> inventory,
                                    CursorEvidence cursor) { }

    public WarehouseMaterials(WarehouseService service,JavaPlugin plugin,java.io.File folder,boolean mysql) throws java.io.IOException {
        this.service=service;this.plugin=plugin;
        evidenceFolder=folder.toPath().resolve("material-transfer-evidence");
        var worlds = Bukkit.getWorlds();
        playerData = worlds.isEmpty() ? null : worlds.get(0).getWorldFolder().toPath().resolve("playerdata");
        var identity=folder.toPath().resolve("material-transfer-server.id");
        if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString(),StandardCharsets.UTF_8);
        serverId=UUID.fromString(Files.readString(identity,StandardCharsets.UTF_8).trim()).toString();
        int workers=mysql?2:1;
        queue=new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(workers,workers,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"AXS-Material-Transfers");t.setDaemon(true);return t;});
        recovery=Bukkit.getScheduler().runTaskTimer(plugin,()->enqueue(this::recoverBatch),1L,600L);
    }

    String transferInventory(Player player, String ownerType, String warehouseId, SharedActor actor,
                             List<SlotChange> changes, ItemStack[] after,
                             List<InventoryPart> deposits, boolean withdrawal) {
        return transferInventory(player, ownerType, warehouseId, actor, changes, after, deposits, withdrawal, null);
    }

    String transferInventory(Player player, String ownerType, String warehouseId, SharedActor actor,
                             List<SlotChange> changes, ItemStack[] after, List<InventoryPart> deposits,
                             boolean withdrawal, CursorDeposit cursor) {
        if (closing || playerData == null || !Bukkit.isPrimaryThread()) return "仓库物品存档暂不可用，请稍后重试";
        UUID owner = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(owner);
        if (!canApplyInventory(player, admission)) return "当前无法操作仓库物品，请重新打开界面";
        if (cursor != null && !cursor.unchanged(player)) return "光标物品已变化，请重试";
        if (checkingPreviousInventory.contains(owner)) return "正在核对上一笔仓库物品操作，请稍后重试";
        InventoryOperation operation = InventoryOperation.tryAcquire(player);
        if (operation == null) return "背包正在处理另一笔操作，请稍后重试";
        String id = UUID.randomUUID().toString();
        String pending = "仓库物品操作待核对，请将凭证编号 " + id + " 提供给管理员";
        activeInventoryTransfers.add(id);
        try (operation) {
            long now = System.currentTimeMillis();
            var cursorEvidence = cursor == null ? null : new CursorEvidence(cursor.source().getAmount(), cursor.itemData());
            var payload = new InventoryPayload(changes, actor, deposits, cursorEvidence);
            var transfer = new MaterialTransfer(id, owner.toString(), warehouseId, serverId, runId,
                withdrawal ? INVENTORY_WITHDRAW_RESERVED : INVENTORY_DEPOSIT, INVENTORY_TRANSFER, ownerType,
                INVENTORY_RECEIPT.toString(), gson.toJson(payload), now, now, "背包与仓库等待确认");
            if (!service.repository.stageInventoryTransfer(transfer, changes, actor)) {
                return "仓库已变化或存在待核对的物品操作，请刷新仓库或联系管理员";
            }
            if (withdrawal) {
                String note = "背包交付已开始，等待存档凭证";
                // Persist this boundary before offering items that could later move outside playerdata.
                if (!service.repository.transitionMaterialTransfer(id, transfer.ownerId(), INVENTORY_WITHDRAW_RESERVED,
                    INVENTORY_WITHDRAW, note)) return pending;
                transfer = transfer.withState(INVENTORY_WITHDRAW, note);
            }
            if (!canApplyInventory(player, admission) || (cursor != null && !cursor.unchanged(player))) {
                if (withdrawal) {
                    String note = "授权已失效，尚未执行背包交付";
                    if (!service.repository.transitionMaterialTransfer(id, transfer.ownerId(), INVENTORY_WITHDRAW,
                        INVENTORY_WITHDRAW_RESERVED, note)) return pending;
                    transfer = transfer.withState(INVENTORY_WITHDRAW_RESERVED, note);
                }
                if (cancelUnappliedInventory(transfer, payload)) return "仓库授权或物品已变化，请重新打开界面";
                return pending;
            }
            ItemStack[] before = InventoryItems.copy(player.getInventory().getStorageContents());
            try {
                player.getInventory().setStorageContents(after);
                if (cursor != null) player.setItemOnCursor(null);
                PlayerDataReceipt.save(player, INVENTORY_RECEIPT, id);
            } catch (RuntimeException | LinkageError failure) {
                if (!id.equals(player.getPersistentDataContainer().get(INVENTORY_RECEIPT, PersistentDataType.STRING))) {
                    player.getInventory().setStorageContents(before);
                    if (cursor != null && player.getOpenInventory() == cursor.view()) {
                        player.setItemOnCursor(cursor.source());
                    }
                }
                throw failure;
            } finally {
                player.updateInventory();
            }
            if (id.equals(PlayerDataReceipt.readExisting(playerFile(owner), INVENTORY_RECEIPT.toString()))
                && finishInventoryTransfer(transfer, payload)) return "";
        } catch (Exception | LinkageError failure) {
            log(failure);
        } finally {
            activeInventoryTransfers.remove(id);
        }
        return pending;
    }

    private Path playerFile(UUID owner) {
        return playerData.resolve(owner + ".dat");
    }

    private boolean canApplyInventory(Player player, AuthSnapshot admission) {
        if (closing || !admission.admitted() || !player.isOnline() || Bukkit.getPlayer(admission.playerId()) != player
            || !player.hasPermission("arcartxsuite.warehouse.use") || !service.isSecondaryUnlocked(player)) return false;
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return current.admitted() && current.generation() == admission.generation()
            && Objects.equals(current.connectionId(), admission.connectionId());
    }

    private boolean finishInventoryTransfer(MaterialTransfer row, InventoryPayload payload) throws java.sql.SQLException {
        boolean done = service.repository.settleInventoryTransfer(row, payload.changes(), payload.actor(), false);
        return done || service.repository.materialTransfer(row.id(), row.ownerId())
            .filter(current -> INVENTORY_DONE.equals(current.state())).isPresent();
    }

    private void recoverInventoryTransfer(MaterialTransfer row) throws Exception {
        if (activeInventoryTransfers.contains(row.id())) return;
        if (INVENTORY_WITHDRAW_RESERVED.equals(row.state())) {
            cancelUnappliedInventory(row, gson.fromJson(row.payload(), InventoryPayload.class));
            return;
        }
        UUID owner = UUID.fromString(row.ownerId());
        Path file = playerData == null ? null : playerFile(owner);
        String saved = null;
        try {
            if (file != null) saved = PlayerDataReceipt.read(file, INVENTORY_RECEIPT.toString());
        } catch (java.io.IOException failure) {
            log(failure);
        }
        if (row.id().equals(saved)) {
            completeRecoveredInventory(row);
            return;
        }
        // Let an online inventory reach its normal save before classifying a missing receipt.
        main(() -> {
            if (Bukkit.getPlayer(owner) == null) enqueue(() -> recoverDisconnectedInventory(row, file));
        });
    }

    private void completeRecoveredInventory(MaterialTransfer row) throws java.sql.SQLException {
        var payload = gson.fromJson(row.payload(), InventoryPayload.class);
        if (!finishInventoryTransfer(row, payload)) reviewInventoryTransfer(row, "背包已落盘，原仓库状态无法确认");
    }

    private void recoverDisconnectedInventory(MaterialTransfer row, Path file) {
        if (activeInventoryTransfers.contains(row.id())) return;
        String saved;
        try {
            if (INVENTORY_WITHDRAW_RESERVED.equals(row.state())) {
                cancelUnappliedInventory(row, gson.fromJson(row.payload(), InventoryPayload.class));
                return;
            }
            if (file == null || !Files.isRegularFile(file)) {
                reviewInventoryTransfer(row, "找不到原服背包存档");
                return;
            }
            try {
                saved = PlayerDataReceipt.readExisting(file, INVENTORY_RECEIPT.toString());
            } catch (java.io.IOException failure) {
                log(failure);
                reviewInventoryTransfer(row, "原服背包存档无法读取");
                return;
            }
            if (row.id().equals(saved)) {
                completeRecoveredInventory(row);
            } else if (INVENTORY_WITHDRAW.equals(row.state())) {
                reviewInventoryTransfer(row, "取出已开始交付，存档未确认，请核对物品是否已移出背包");
            } else {
                var payload = gson.fromJson(row.payload(), InventoryPayload.class);
                if (payload.cursor() != null) {
                    // A prior save can contain the carried stack, or the stack may have come from a native container
                    // and be absent. Keep the full source evidence without reconstructing either outcome.
                    reviewInventoryTransfer(row, "光标存入未找到对应存档凭证，请核对原光标物品与背包存档");
                } else {
                    cancelUnappliedInventory(row, payload);
                }
            }
        } catch (Exception failure) {
            log(failure);
        }
    }

    private boolean cancelUnappliedInventory(MaterialTransfer row, InventoryPayload payload) throws java.sql.SQLException {
        if (INVENTORY_DEPOSIT.equals(row.state())) {
            String note = payload.cursor() == null ? "背包未扣除，仓库未入库" : "存入未开始，仓库未入库";
            return service.repository.transitionMaterialTransfer(row.id(), row.ownerId(), INVENTORY_DEPOSIT,
                "CANCELLED", note);
        }
        var reverse = payload.changes().stream().map(change -> new SlotChange(change.after(), change.before())).toList();
        if (service.repository.settleInventoryTransfer(row, reverse, payload.actor(), true)) return true;
        reviewInventoryTransfer(row, "背包未收到，原仓库槽位已变化");
        return false;
    }

    private void reviewInventoryTransfer(MaterialTransfer row, String reason) throws java.sql.SQLException {
        service.repository.transitionMaterialTransfer(row.id(), row.ownerId(), row.state(), "REVIEW", reason);
    }

    private void recoverPreviousPlayerInventory(UUID owner) {
        if (!enqueue(() -> {
            try {
                var row = service.repository.pendingInventoryTransfer(owner, serverId).orElse(null);
                if (row != null) recoverDisconnectedInventory(row, playerData == null ? null : playerFile(owner));
            } catch (Exception failure) {
                log(failure);
            } finally {
                checkingPreviousInventory.remove(owner);
            }
        })) checkingPreviousInventory.remove(owner);
    }
    private void log(Throwable e){plugin.getLogger().log(Level.WARNING,"仓库材料转移凭证处理失败",e);}
    private boolean enqueue(Runnable task){if(closing)return false;try{queue.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
    private void main(Runnable task){if(!closing&&plugin.isEnabled())try{Bukkit.getScheduler().runTask(plugin,()->{if(!closing)task.run();});}catch(IllegalStateException ignored){}}
    private String check(Player p){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Material access requires main thread");
        if(closing||p==null||!p.isOnline())return "材料服务不可用";
        return "";
    }
    private SecondaryPasswordAccess passwords(){
        return service.secondaryPasswordSupplier==null?null:service.secondaryPasswordSupplier.get();
    }
    private boolean authorized(Authorization authorization){
        try{return authorization!=null&&authorization.provider()==passwords()
            &&authorization.status()!=null&&authorization.status().unlocked();}
        catch(RuntimeException exception){return false;}
    }
    private void authorizationAsync(Player player,Consumer<Authorization> callback){
        SecondaryPasswordAccess access=passwords();
        if(access==null){callback.accept(null);return;}
        try{
            access.statusAsync(player).whenComplete((status,failure)->main(()->callback.accept(
                failure==null&&access==passwords()?new Authorization(access,status):null)));
        }catch(RuntimeException exception){callback.accept(null);}
    }
    private void revalidateAsync(Authorization authorization,Consumer<Boolean> callback){
        try{
            authorization.status().revalidateAsync().whenComplete((current,failure)->main(()->callback.accept(
                failure==null&&Boolean.TRUE.equals(current)&&authorized(authorization))));
        }catch(RuntimeException exception){main(()->callback.accept(false));}
    }
    private boolean active(UUID owner,String id){
        Transfer transfer=active.get(owner);
        return transfer!=null&&transfer.id().equals(id);
    }
    private void release(UUID owner,String id){
        active.computeIfPresent(owner,(ignored,transfer)->transfer.id().equals(id)?null:transfer);
    }
    @Override public Snapshot materials(Player p){
        String reason=check(p);if(!reason.isEmpty())return new Snapshot("",List.of(),reason);
        // The synchronous API already performs synchronous storage reads. Its next
        // prepareMaterials call obtains a nonblocking, recheckable authorization.
        SecondaryPasswordAccess access=passwords();
        try{if(access==null||!access.isUnlocked(p))return new Snapshot("",List.of(),"仓库尚未解锁");}
        catch(RuntimeException exception){return new Snapshot("",List.of(),"仓库尚未解锁");}
        try{
            service.ensureEntitlements(p);var owned=service.personalWarehouseMap(p.getUniqueId());
            String id=service.configuration.warehouses().keySet().stream().filter(owned::containsKey).findFirst().orElse("");
            if(id.isEmpty())return new Snapshot("",List.of(),"没有可用个人仓库");
            List<Entry> entries=new ArrayList<>();
            for(var row:service.repository.loadSlots(WarehouseService.OWNER_PERSONAL,p.getUniqueId().toString(),id)){
                var item=service.deserializeSlotItem(row);
                if(item!=null&&!item.getType().isAir()&&row.amount()>0)entries.add(new Entry(row.slot(),row.itemHash(),row.createdAt(),row.amount(),item));
            }
            Snapshot snapshot=new Snapshot(id,entries,"");issued.put(p,new Issued(snapshot,null));return snapshot;
        }catch(Exception e){log(e);return new Snapshot("",List.of(),"读取仓库材料失败");}
    }
    @Override public Result withdrawMaterials(Player p,Snapshot snapshot,List<Request> requests){return new Result(false,List.of(),"请使用带凭证的 prepareMaterials 接口");}
    @Override public void materialsAsync(Player p,Consumer<Snapshot> callback){
        String error=check(p);if(!error.isEmpty()){callback.accept(new Snapshot("",List.of(),error));return;}
        UUID owner=p.getUniqueId();
        var waiting=reads.get(p);
        if(waiting!=null){if(waiting.size()<4)waiting.add(callback);else callback.accept(new Snapshot("",List.of(),"材料读取繁忙"));return;}
        var callbacks=new ArrayList<Consumer<Snapshot>>(List.of(callback));
        reads.put(p,callbacks);
        authorizationAsync(p,authorization->{
            if(reads.get(p)!=callbacks)return;
            if(!check(p).isEmpty()||!authorized(authorization)){
                finishRead(p,callbacks,new Snapshot("",List.of(),"仓库尚未解锁"),null);
                return;
            }
            loadMaterials(p,owner,callbacks,authorization);
        });
    }
    private void finishRead(Player player,List<Consumer<Snapshot>> callbacks,Snapshot snapshot,Authorization authorization){
        if(!reads.remove(player,callbacks))return;
        if(snapshot.available())issued.put(player,new Issued(snapshot,authorization));
        for(var cb:callbacks)try{cb.accept(snapshot);}catch(RuntimeException e){log(e);}
    }
    private void loadMaterials(Player p,UUID owner,List<Consumer<Snapshot>> callbacks,Authorization authorization){
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
                if(reads.get(p)!=callbacks)return;
                List<Entry> entries=new ArrayList<>();String reason=check(p);
                if(reason.isEmpty()&&!authorized(authorization))reason="仓库尚未解锁";
                if(reason.isEmpty())reason=message;
                if(reason.isEmpty())try{for(var row:records){var item=service.deserializeSlotItem(row);if(item!=null&&!item.getType().isAir()&&row.amount()>0)entries.add(new Entry(row.slot(),row.itemHash(),row.createdAt(),row.amount(),item));}}
                catch(RuntimeException e){reason="物品材料解码失败";}
                finishRead(p,callbacks,new Snapshot(id,entries,reason),authorization);
            });
        })){finishRead(p,callbacks,new Snapshot("",List.of(),"材料队列已满"),null);}
    }
    @Override public void prepareMaterials(Player p,Snapshot snapshot,List<Request> requests,Target target,Consumer<Prepared> callback){
        String error=check(p);
        if(!error.isEmpty()){callback.accept(new Prepared(false,"",error));return;}
        Issued source=issued.remove(p);
        if(snapshot==null||!snapshot.available()||source==null||source.snapshot()!=snapshot||requests==null||requests.isEmpty()||requests.size()>9||target==null||target.adapter()==null||target.adapter().length()>64||target.identity()==null||target.identity().length()>255||target.evidence()==null||target.evidence().length()>262144){callback.accept(new Prepared(false,"","材料计划无效或已过期"));return;}
        UUID owner=p.getUniqueId();String id=UUID.randomUUID().toString();
        if(active.putIfAbsent(owner,new Transfer(id,source.authorization(),false))!=null){callback.accept(new Prepared(false,"","上一笔填料仍在处理中"));return;}
        List<Request> copy=List.copyOf(requests);
        if(source.authorization()==null){
            authorizationAsync(p,authorization->prepareAuthorized(p,snapshot,copy,target,owner,id,authorization,callback));
        }else if(!authorized(source.authorization())){
            release(owner,id);callback.accept(new Prepared(false,"","仓库尚未解锁"));
        }else revalidateAsync(source.authorization(),current->
            prepareAuthorized(p,snapshot,copy,target,owner,id,current?source.authorization():null,callback));
    }
    private void prepareAuthorized(Player p,Snapshot snapshot,List<Request> copy,Target target,UUID owner,
                                   String id,Authorization authorization,Consumer<Prepared> callback){
        if(!active(owner,id))return;
        if(!check(p).isEmpty()||!authorized(authorization)){
            release(owner,id);callback.accept(new Prepared(false,"","仓库尚未解锁"));return;
        }
        active.put(owner,new Transfer(id,authorization,false));
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
            main(()->{if(!answer.success())release(owner,id);callback.accept(answer);});
        })){release(owner,id);callback.accept(new Prepared(false,"","材料队列已满，请稍后重试"));}
    }
    @Override public void authorizeDelivery(Player p,String id,Consumer<Boolean> callback){
        String error=check(p);
        UUID owner=p.getUniqueId();
        Transfer transfer=active.get(owner);
        if(transfer!=null&&id.equals(transfer.id())&&transfer.deliveryRequested()){
            callback.accept(false);return;
        }
        if(!error.isEmpty()||transfer==null||!id.equals(transfer.id())||!authorized(transfer.authorization())){
            cancelPrepared(p,id);callback.accept(false);return;
        }
        Transfer request=new Transfer(id,transfer.authorization(),true);
        if(!active.replace(owner,transfer,request)){callback.accept(false);return;}
        if(!enqueue(()->{
            boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner.toString(),"HELD","APPLYING","已授权交付，禁止自动补发");}catch(Exception e){log(e);}
            Consumer<Boolean> finish=current->{
                if(active.get(owner)!=request){callback.accept(false);return;}
                if(!current||!check(p).isEmpty()){
                    cancelUndelivered(p,id,ignored->callback.accept(false));return;
                }
                callback.accept(true);
            };
            // The receipt already protects recovery. Recheck shared identity before allowing item placement.
            if(ok)revalidateAsync(request.authorization(),finish);
            else main(()->finish.accept(false));
        })){cancelPrepared(p,id);callback.accept(false);}
    }
    @Override public void recordApplied(Player p,String id){
        completeDelivery(p,id,ignored -> {});
    }
    @Override public void completeDelivery(Player p,String id,Consumer<Boolean> callback){
        UUID owner=p.getUniqueId();if(!active(owner,id)){callback.accept(false);return;}
        if(!enqueue(()->{boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner.toString(),"APPLYING","APPLIED","内存已落槽，原版持久化未经确认");}catch(Exception e){log(e);}finally{release(owner,id);}boolean result=ok;main(()->callback.accept(result));})){release(owner,id);callback.accept(false);}
    }
    @Override public void recordUncertain(Player p,String id,String reason){
        UUID owner=p.getUniqueId();
        if(!enqueue(()->{try{service.repository.transitionMaterialTransfer(id,owner.toString(),"APPLYING","REVIEW",reason.substring(0,Math.min(200,reason.length())));}catch(Exception e){log(e);}finally{release(owner,id);}}))release(owner,id);
    }
    @Override public void cancelPrepared(Player p,String id){
        cancelPrepared(p, id, null);
    }

    @Override public void cancelPrepared(Player player, String id, Consumer<Boolean> restored) {
        cancelTransfer(player, id, false, restored);
    }

    @Override public void cancelUndelivered(Player player, String id, Consumer<Boolean> restored) {
        cancelTransfer(player, id, true, restored);
    }

    private void cancelTransfer(Player player, String id, boolean deliveryNotAttempted, Consumer<Boolean> restored) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("main thread required");
        UUID owner = player.getUniqueId();
        if (!enqueue(() -> {
            boolean returned = false;
            try {
                var row = service.repository.materialTransfer(id, owner.toString()).orElse(null);
                if (row != null) {
                    boolean held = row.state().equals("HELD");
                    Transfer transfer = active.get(owner);
                    if (deliveryNotAttempted && row.state().equals("APPLYING")
                            && transfer != null && id.equals(transfer.id()) && transfer.deliveryRequested()
                            && serverId.equals(row.serverId()) && runId.equals(row.runId())) {
                        // Persist the caller's proof before restoring. Recovery can safely finish a HELD transfer.
                        held = service.repository.transitionMaterialTransfer(id, row.ownerId(), "APPLYING", "HELD",
                            "交付未执行，恢复仓库预留");
                    }
                    returned = row.state().equals("RETURNED") || (held && restore(row));
                }
            } catch (Exception failure) { log(failure); }
            finally { release(owner, id); }
            boolean confirmed = returned;
            if (restored != null) main(() -> restored.accept(confirmed));
        })) {
            release(owner, id);
            if (restored != null) restored.accept(false);
        }
    }
    private static SlotItemRecord amount(SlotItemRecord r,long n){return new SlotItemRecord(r.ownerType(),r.ownerId(),r.warehouseId(),r.slot(),r.itemHash(),r.categoryId(),r.displayName(),r.materialId(),r.searchText(),r.pinyin(),r.initials(),r.itemData(),r.itemJson(),n,r.createdAt(),System.currentTimeMillis(),r.loreText());}
    private boolean restore(MaterialTransfer row)throws Exception{
        SlotChange[] changes=gson.fromJson(row.payload(),SlotChange[].class);
        List<SlotChange> reverse=Arrays.stream(changes).map(c->new SlotChange(c.after(),c.before())).toList();
        boolean restored = service.repository.restoreHeldMaterialTransfer(row, reverse);
        if (!restored) {
            service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),"HELD","REVIEW","回仓位置已变化，保留材料凭证");
        }
        release(UUID.fromString(row.ownerId()),row.id());
        return restored;
    }
    private void recoverBatch() {
        recoverBatch("");
    }

    private void recoverBatch(String afterId) {
        try{
            var rows=service.repository.oldMaterialTransfers(serverId,runId,afterId,100);
            for(var row:rows)try{
                if (INVENTORY_TRANSFER.equals(row.adapter())) {
                    recoverInventoryTransfer(row);
                    continue;
                }
                if (runId.equals(row.runId()) && active(UUID.fromString(row.ownerId()), row.id())) continue;
                if(row.state().equals("HELD"))restore(row);
                else if(service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),row.state(),"REVIEW","上次运行遗留，原版存档无法确认，禁止自动补发"))
                    main(()->{Player p=Bukkit.getPlayer(UUID.fromString(row.ownerId()));if(p!=null)notifyReview(p);});
            }catch(Exception e){
                log(e);
                if (!INVENTORY_TRANSFER.equals(row.adapter()))
                    service.repository.transitionMaterialTransfer(row.id(),row.ownerId(),row.state(),"REVIEW","恢复失败，保留完整凭证");
            }
            if (rows.size() == 100) {
                String next = rows.get(rows.size() - 1).id();
                enqueue(() -> recoverBatch(next));
            }
        }catch(Exception e){log(e);}
    }
    public void inspect(String owner,Consumer<List<MaterialTransfer>> callback){
        if(!enqueue(()->{try{var rows=service.repository.materialTransfers(owner,50);main(()->callback.accept(rows));}catch(Exception e){log(e);main(()->callback.accept(List.of()));}}))callback.accept(List.of());
    }
    public void confirmReviewed(String owner,String id,String actor,Consumer<Boolean> callback){
        if(!enqueue(()->{boolean ok=false;try{ok=service.repository.transitionMaterialTransfer(id,owner,"REVIEW","CONFIRMED","人工确认，无自动发放；操作人 "+actor);}catch(Exception e){log(e);}boolean answer=ok;main(()->callback.accept(answer));}))callback.accept(false);
    }
    private void notifyReview(Player p){if(notified.add(p.getUniqueId()))p.sendMessage("§e存在待核对的仓库材料转移凭证；系统未自动补发，请联系管理员。");}
    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID owner = player.getUniqueId();
        issued.remove(player);
        reads.remove(player);
        notified.remove(owner);
        if (checkingPreviousInventory.add(owner)) {
            main(() -> {
                if (Bukkit.getPlayer(owner) != player) recoverPreviousPlayerInventory(owner);
                else checkingPreviousInventory.remove(owner);
            });
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void login(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        UUID owner = event.getPlayer().getUniqueId();
        if (Bukkit.getPlayer(owner) == null && checkingPreviousInventory.add(owner)) recoverPreviousPlayerInventory(owner);
    }
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
    @Override public void close(){closing=true;issued.clear();reads.clear();try{if(recovery!=null)recovery.cancel();}finally{queue.shutdown();}}
    public CompletionStage<Void> stopped(){return queue.stopped();}
}
