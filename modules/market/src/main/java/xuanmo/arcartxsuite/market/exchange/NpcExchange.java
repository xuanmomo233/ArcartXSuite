package xuanmo.arcartxsuite.market.exchange;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.inventory.*;

/** External currency and player saves have no shared transaction: journal intent,
 * execute once, and retain uncertain operations instead of replaying payments. */
public final class NpcExchange {
    private final Plugin plugin;
    private final NpcTradeRepository repository;
    private final CurrencyBridgeAPI currencies;
    private final Logger logger;
    private final Gson gson=new Gson();
    private final Map<UUID,Operation> operations=new HashMap<>();
    private final java.util.concurrent.ConcurrentMap<String,String> reviewBuffer=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean reviewQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private final org.bukkit.scheduler.BukkitTask reviewPulse;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"axs-npc-exchange");t.setDaemon(true);return t;});
    private volatile boolean closed;
    private final class Operation {
        final String id=UUID.randomUUID().toString();final InventoryOperation reservation;final Player player;final NpcTradePlan plan;final MaterialStock stock;final BooleanSupplier authority;final Consumer<String> done;
        final Map<String,Object> evidence=new LinkedHashMap<>();String state="PREPARED",held="";boolean effects,authorized,applied,finished;
        Operation(InventoryOperation reservation,Player player,NpcTradePlan plan,MaterialStock stock,BooleanSupplier authority,Consumer<String> done,String shop,String offer){
            this.reservation=reservation;this.player=player;this.plan=plan;this.stock=stock;this.authority=authority;this.done=done;
            evidence.put("operation",id);evidence.put("player",player.getUniqueId().toString());evidence.put("shop",shop);evidence.put("offer",offer);evidence.put("plan",plan.evidence());evidence.put("effects",new ArrayList<>());
        }
        boolean valid(){try{return !closed&&player.isOnline()&&!player.isDead()&&authority.getAsBoolean()&&plan.unchanged(player);}catch(RuntimeException e){return false;}}
        String json(){evidence.put("warehouseOperation",held);evidence.put("phase",state);return gson.toJson(evidence);}
        @SuppressWarnings("unchecked") void effect(String kind,String currency,BigDecimal amount,String result){((List<Object>)evidence.get("effects")).add(Map.of("kind",kind,"currency",currency,"amount",amount.toPlainString(),"result",result));}
    }
    public NpcExchange(Plugin plugin,NpcTradeRepository repository,CurrencyBridgeAPI currencies,Logger logger){this.plugin=plugin;this.repository=repository;this.currencies=currencies;this.logger=logger;reviewPulse=Bukkit.getScheduler().runTaskTimer(plugin,this::queueReviews,20L,20L);}
    public <T> void query(Callable<T> task,Consumer<T> answer,Consumer<Exception> error){
        if(closed)return;
        if(worker.getQueue().remainingCapacity()<=1){error.accept(new RejectedExecutionException("交易记录服务繁忙"));return;}
        try{worker.execute(()->{try{T value=task.call();main(()->{try{answer.accept(value);}catch(RuntimeException e){error.accept(e);}});}catch(Exception e){main(()->error.accept(e));}});}catch(RejectedExecutionException e){error.accept(e);}
    }
    private void main(Runnable run){if(!closed&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closed)run.run();});}
    private void queueReviews(){
        if(closed||reviewBuffer.isEmpty()||!reviewQueued.compareAndSet(false,true))return;
        try{worker.execute(()->{
            try{for(var row:reviewBuffer.entrySet())try{var stored=repository.get(row.getKey());if(stored==null||repository.review(row.getKey(),row.getValue())||Set.of("CANCELLED","CONFIRMED").contains(stored.state()))reviewBuffer.remove(row.getKey(),row.getValue());}catch(Exception ignored){ }}
            finally{reviewQueued.set(false);}
        });}catch(RejectedExecutionException e){reviewQueued.set(false);}
    }
    private void phase(Operation op,String next,Runnable continuation){
        if(op.finished)return;
        String expected=op.state;op.state=next;String json=op.json();
        query(()->repository.state(op.id,expected,next,json),ok->{if(op.finished)return;if(ok)continuation.run();else uncertain(op,"交易记录状态冲突，禁止重放");},e->uncertain(op,"交易记录写入失败，保留凭证"));
    }
    private void finish(Operation op,String message){if(op.finished)return;op.finished=true;op.reservation.close();operations.remove(op.player.getUniqueId(),op);op.done.accept(message==null||message.isBlank()?"兑换未完成，请刷新":message);}
    private void uncertain(Operation op,String reason){
        if(op.finished)return;
        if(!op.held.isEmpty()){
            try{if(op.authorized)op.stock.access().recordUncertain(op.player,op.held,reason);else op.stock.access().cancelPrepared(op.player,op.held);}catch(RuntimeException ignored){ }
        }
        op.evidence.put("review",reason);String json=op.json();
        reviewBuffer.put(op.id,json);queueReviews();
        logger.warning("NPC 交易保留待核对："+op.id+" "+reason);
        finish(op,reason+"；凭证 "+op.id);
    }
    private void abort(Operation op,String reason){
        if(op.finished)return;
        if(op.effects||op.authorized){uncertain(op,reason);return;}
        if (!op.held.isEmpty()) {
            try {
                op.stock.access().cancelPrepared(op.player, op.held);
            } catch (RuntimeException exception) {
                uncertain(op, "仓库预留取消未确认，保留凭证核对");
                return;
            }
        }
        op.evidence.put("cancel",reason);phase(op,"CANCELLED",()->finish(op,reason));
    }
    public void execute(Player player,String shop,String offer,NpcTradePlan plan,MaterialStock stock,BooleanSupplier authority,Consumer<String> done){
        InventoryOperation reservation = closed ? null : InventoryOperation.tryAcquire(player);
        if (reservation == null) {
            done.accept("上一笔材料或兑换仍在处理");
            return;
        }
        Operation op;
        try {
            op = new Operation(reservation, player, plan, stock, authority, done, shop, offer);
        } catch (RuntimeException exception) {
            reservation.close();
            throw exception;
        }
        operations.put(player.getUniqueId(),op);
        try{
            for(var row:plan.debits().entrySet()){
                var bridge=currencies.bridge(row.getKey());
                if(bridge==null||!bridge.available()||bridge.balance(player).compareTo(row.getValue())<0)throw new IllegalArgumentException("货币不可用或余额不足："+row.getKey());
            }
            for(String id:plan.credits().keySet()){var bridge=currencies.bridge(id);if(bridge==null||!bridge.available())throw new IllegalArgumentException("奖励货币不可用："+id);}
        }catch(RuntimeException e){finish(op,e.getMessage());return;}
        String evidence=op.json();
        if(evidence.length()>240000){finish(op,"本次物品数据过大，未执行兑换");return;}
        if(op.finished)return;
        query(()->{repository.begin(op.id,player.getUniqueId(),evidence);return true;},ok->{
            if(op.finished)return;
            if(!op.valid()){abort(op,"窗口、兑换配置或背包已变化，未执行兑换");return;}
            if(plan.requests().isEmpty()){debit(op,new ArrayList<>(plan.debits().entrySet()),0);return;}
            stock.access().prepareMaterials(player,stock.warehouse(),plan.requests(),new WarehouseMaterialAccess.Target("market:npc_exchange",shop+":"+offer,"PLAYER_UNPROVEN",evidence),prepared->{
                if(op.finished){if(prepared.success())stock.access().cancelPrepared(player,prepared.operationId());return;}
                if(!prepared.success()){abort(op,prepared.message());return;}
                op.held=prepared.operationId();phase(op,"MATERIAL_HELD",()->debit(op,new ArrayList<>(plan.debits().entrySet()),0));
            });
        },e->{String reason=e.getMessage();finish(op,reason!=null&&reason.startsWith("NPC_REVIEW:")?"已有处理中或待核对的兑换，未继续扣款；凭证 "+reason.substring(11):"无法确认兑换记录，未继续扣款；凭证 "+op.id);});
    }
    private void debit(Operation op,List<Map.Entry<String,BigDecimal>> rows,int index){
        if(op.finished)return;
        if(!op.valid()){abort(op,"兑换环境已变化，已执行部分需核对");return;}
        if(index==rows.size()){delivery(op);return;}
        var row=rows.get(index);op.effect("DEBIT",row.getKey(),row.getValue(),"INTENT");
        phase(op,"DEBIT_INTENT",()->{
            if(!op.valid()){abort(op,"扣款前状态已变化");return;}
            var bridge=currencies.bridge(row.getKey());
            if(bridge==null||!bridge.available()){abort(op,"货币服务已不可用");return;}
            op.effects=true;
            try{
                var result=bridge.withdraw(op.player,row.getValue());op.effect("DEBIT",row.getKey(),row.getValue(),result.success()?"OK":"FAILED");
                if(!result.success()){uncertain(op,"货币接口报告扣款失败，需核对是否部分执行");return;}
                phase(op,"DEBIT_OK",()->debit(op,rows,index+1));
            }catch(RuntimeException e){uncertain(op,"货币扣款结果不明确，禁止自动重试");}
        });
    }
    private void delivery(Operation op){
        if(op.finished)return;
        if(!op.valid()){abort(op,"交付前状态已变化");return;}
        phase(op,"DELIVERY_INTENT",()->{
            if(!op.valid()){abort(op,"交付前背包或兑换条件已变化");return;}
            if(op.held.isEmpty()){apply(op);return;}
            op.stock.access().authorizeDelivery(op.player,op.held,authorized->{
                if(op.finished)return;
                if(!authorized){uncertain(op,"仓库取料授权未确认");return;}
                op.authorized=true;apply(op);
            });
        });
    }
    private void apply(Operation op){
        if(op.finished)return;
        if(!op.valid()){abort(op,"交付环境已变化，保留凭证核对");return;}
        op.effects=true;
        try{op.plan.apply(op.player);op.applied=true;}
        catch(RuntimeException e){uncertain(op,"背包交付可能部分完成，禁止补发");return;}
        Runnable next=()->phase(op,"ITEMS_APPLIED",()->credit(op,new ArrayList<>(op.plan.credits().entrySet()),0));
        if(op.held.isEmpty())next.run();
        else {
            try {
                op.stock.access().completeDelivery(op.player,op.held,ack->{
                    if (ack) next.run();
                    else uncertain(op,"物品已放置，仓库凭证确认失败");
                });
            } catch (RuntimeException exception) {
                uncertain(op, "物品已放置，仓库凭证确认失败");
            }
        }
    }
    private void credit(Operation op,List<Map.Entry<String,BigDecimal>> rows,int index){
        if(op.finished)return;
        if(index==rows.size()){phase(op,"APPLIED",()->finish(op,"兑换完成"));return;}
        var row=rows.get(index);op.effect("CREDIT",row.getKey(),row.getValue(),"INTENT");
        phase(op,"CREDIT_INTENT",()->{
            if(!op.player.isOnline()){uncertain(op,"玩家离线，奖励货币待核对");return;}
            var bridge=currencies.bridge(row.getKey());if(bridge==null||!bridge.available()){uncertain(op,"奖励货币服务不可用");return;}
            try{
                var result=bridge.deposit(op.player,row.getValue());op.effect("CREDIT",row.getKey(),row.getValue(),result.success()?"OK":"FAILED");
                if(!result.success()){uncertain(op,"货币奖励结果未确认");return;}
                phase(op,"CREDIT_OK",()->credit(op,rows,index+1));
            }catch(RuntimeException e){uncertain(op,"货币奖励结果不明确，禁止补发");}
        });
    }
    public void warehouseUnavailable(){for(Operation op:List.copyOf(operations.values()))if(!op.plan.requests().isEmpty())uncertain(op,"仓库服务已变化，兑换保留待核对");}
    public void close(){for(Operation op:List.copyOf(operations.values()))uncertain(op,"兑换模块关闭，保留凭证核对");queueReviews();reviewPulse.cancel();closed=true;worker.shutdown();try{worker.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}operations.clear();}
}
