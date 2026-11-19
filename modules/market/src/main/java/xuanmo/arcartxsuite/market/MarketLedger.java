package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Write-ahead audit and claimed delivery queue. Uncertain external effects require reconciliation. */
public final class MarketLedger {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private record ItemLabel(String name,String material,String snapshot) {}
    private static final ThreadLocal<ItemLabel> CURRENT_ITEM=new ThreadLocal<>();
    public static String currentTradeId(){return CURRENT.get();}
    public static String currentItemMaterial(){ItemLabel item=CURRENT_ITEM.get();return item==null?"":item.material();}
    private final MarketRepository repository;
    private final String owner = UUID.randomUUID().toString();
    private final Logger logger;

    public MarketLedger(MarketRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
        repository.heartbeatMarketOwner(owner);
    }

    public void heartbeat() {
        repository.heartbeatMarketOwner(owner);
        repository.recoverAbandonedOperations();
        repository.finishConfirmedShopRefunds();
    }

    public String owner() { return owner; }

    public MarketOperation beginShopDebit(MarketOperation order) {
        long now = System.currentTimeMillis();
        var debit = new MarketOperation(UUID.randomUUID().toString(), order.id(), order.player(), "DEBIT",
            "currency", "RUNNING", order.currency(), order.amount(), order.itemData(), "调用货币接口前记录",
            order.owner(), now, now, order.itemName(), order.material());
        repository.insertOperation(debit);
        return debit;
    }

    public void recordShopDebit(MarketOperation debit, CurrencyTransactionResult result) {
        String state = result.uncertain() ? "REVIEW" : result.success() ? "DONE" : "FAILED";
        if (!repository.transitionOperation(debit.id(), "RUNNING", state,
            result.uncertain() ? "扣款结果不明，禁止自动重试" : "货币接口已返回", debit.owner())) {
            throw new IllegalStateException("货币流水落库失败: " + debit.id());
        }
    }

    /** Associate the public purchase history with its durable order. */
    public <T> T withShopOrder(MarketOperation order, Supplier<T> action) {
        String previous = CURRENT.get();
        ItemLabel previousItem = CURRENT_ITEM.get();
        CURRENT.set(order.id());
        CURRENT_ITEM.set(new ItemLabel(order.itemName(), order.material(), order.itemData()));
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            if (previousItem == null) CURRENT_ITEM.remove(); else CURRENT_ITEM.set(previousItem);
        }
    }

    public <T> T trade(String kind, UUID player, String reference, Supplier<T> body, Predicate<T> success) {
        return trade(kind, player, reference, "", body, success);
    }

    public <T> T trade(String kind, UUID player, String reference, String itemData, Supplier<T> body, Predicate<T> success) {
        String previous = CURRENT.get();
        ItemLabel previousItem=CURRENT_ITEM.get();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        repository.insertOperation(new MarketOperation(id, id, player, kind, reference, "RUNNING", "", "0", itemData, "业务开始", owner, now, now));
        CURRENT.set(id);
        CURRENT_ITEM.remove();
        try {
            T result = body.get();
            String detail=String.valueOf(result);
            if(detail.length()>1000)detail=detail.substring(0,1000);
            if (!repository.transitionOperation(id, "RUNNING", success.test(result) ? "DONE" : "FAILED", detail, owner)) {
                throw new IllegalStateException("交易流水状态未能提交: " + id);
            }
            return result;
        } catch (RuntimeException exception) {
            repository.transitionOperation(id, "RUNNING", "REVIEW", "业务中断，需核对外部操作结果", owner);
            logger.severe("[Market] 交易需核对 id=" + id + " reason=" + exception.getMessage());
            throw exception;
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            if(previousItem==null)CURRENT_ITEM.remove();else CURRENT_ITEM.set(previousItem);
        }
    }

    public void describe(String name,String material,String itemData){
        String plain=org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&',name==null?"":name));
        if(plain.length()>255)plain=plain.substring(0,255);
        CURRENT_ITEM.set(new ItemLabel(plain,material==null?"":material,itemData==null?"":itemData));
        repository.describeOperation(CURRENT.get(),plain,material==null?"":material,itemData);
    }
    private static MarketOperation labelled(MarketOperation operation){ItemLabel item=CURRENT_ITEM.get();return item==null?operation:operation.withItem(item.name(),item.material(),item.snapshot());}

    private CurrencyTransactionResult effect(Player player, String currency, BigDecimal amount, String kind,
                                               Supplier<CurrencyTransactionResult> body, boolean requiresReconciliation) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        repository.insertOperation(labelled(new MarketOperation(id, CURRENT.get(), player.getUniqueId(), kind, "currency", "RUNNING",
            currency, amount.toPlainString(), "", "调用货币接口前记录", owner, now, now)));
        try {
            CurrencyTransactionResult result = body.get();
            boolean review = result.uncertain() || (result.success() && requiresReconciliation);
            if (!repository.transitionOperation(id, "RUNNING", review ? "REVIEW" : result.success() ? "DONE" : "FAILED",
                review ? "货币结果需核对，禁止自动重试" : "货币接口已返回", owner)) {
                throw new IllegalStateException("货币流水落库失败: " + id);
            }
            return result;
        } catch (RuntimeException exception) {
            repository.transitionOperation(id, "RUNNING", "REVIEW", "外部货币结果未知，禁止自动重试", owner);
            throw exception;
        }
    }

    public CurrencyBridgeAPI wrap(CurrencyBridgeAPI delegate) {
        return new CurrencyBridgeAPI() {
            public CurrencyBridge bridge(String id) {
                CurrencyBridge bridge = delegate.bridge(id);
                if (bridge == null) return null;
                return new CurrencyBridge() {
                    public CurrencyDefinition definition() { return bridge.definition(); }
                    public boolean available() { return bridge.available(); }
                    public String unavailableReason() { return bridge.unavailableReason(); }
                    public BigDecimal balance(Player player) { return bridge.balance(player); }
                    public boolean creditRequiresReconciliation() { return bridge.creditRequiresReconciliation(); }
                    public boolean debitRequiresReconciliation() { return bridge.debitRequiresReconciliation(); }
                    public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
                        boolean needsReceipt = bridge.debitRequiresReconciliation();
                        var result = effect(player, id, amount, "DEBIT", () -> bridge.withdraw(player, amount), needsReceipt);
                        return needsReceipt && result.success()
                            ? CurrencyTransactionResult.unknown("扣款结果需核对") : result;
                    }
                    public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
                        return effect(player, id, amount, "CREDIT", () -> bridge.deposit(player, amount), bridge.creditRequiresReconciliation());
                    }
                };
            }
            public CurrencyDefinition definition(String id) { return delegate.definition(id); }
            public java.util.Collection<CurrencyDefinition> definitions() { return delegate.definitions(); }
            public java.util.Set<String> currencyIds() { return delegate.currencyIds(); }
            public String format(String id, BigDecimal amount) { return delegate.format(id, amount); }
        };
    }

    public static MarketOperation delivery(UUID player, String kind, String currency, String amount, String item, String reason) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        return labelled(new MarketOperation(id, CURRENT.get(), player, kind, reason, "READY", currency, amount, item,
            "等待交付", "", now, now));
    }

    public String prepareDelivery(MarketOperation task) {
        repository.insertOperation(new MarketOperation(task.id(),task.tradeId(),task.player(),task.kind(),task.reference(),"PREPARED",
            task.currency(),task.amount(),task.itemData(),"已保存快照，尚未确认交易条件",owner,task.createdAt(),task.updatedAt(),task.itemName(),task.material()));
        return task.id();
    }

    public void activatePrepared(java.util.List<String> ids, boolean paid) {
        activatePrepared(ids,paid,paid?"扣款成功，等待交付":"未扣款，取消奖励快照");
    }
    public void activatePrepared(java.util.List<String> ids,boolean paid,String note){
        for(String id:ids) if(!repository.transitionOperation(id,"PREPARED",paid?"READY":"CANCELLED",
            note,owner)) throw new IllegalStateException("奖励快照状态冲突: "+id);
    }

    boolean claimDelivery(MarketOperation task) {
        return repository.transitionOperation(task.id(), "READY", "RUNNING", "交付已抢占", owner);
    }

    void flagUnknownDelivery(MarketOperation task) {
        repository.transitionOperation(task.id(), "READY", "REVIEW", "未知交付类型，保留记录待核对", owner);
    }

    void finishDelivery(MarketOperation task, String state, String note) {
        if (!repository.transitionOperation(task.id(), "RUNNING", state, note, owner)) {
            throw new IllegalStateException("交付回执状态已变化: " + task.id());
        }
    }
}
