package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
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
                                               Supplier<CurrencyTransactionResult> body) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        repository.insertOperation(labelled(new MarketOperation(id, CURRENT.get(), player.getUniqueId(), kind, "currency", "RUNNING",
            currency, amount.toPlainString(), "", "调用货币接口前记录", owner, now, now)));
        try {
            CurrencyTransactionResult result = body.get();
            if (!repository.transitionOperation(id, "RUNNING", result.success() ? "DONE" : "FAILED", "货币接口已返回", owner)) {
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
                    public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
                        return effect(player, id, amount, "DEBIT", () -> bridge.withdraw(player, amount));
                    }
                    public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
                        return effect(player, id, amount, "CREDIT", () -> bridge.deposit(player, amount));
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

    public void deliver(Player player, List<MarketOperation> tasks, CurrencyBridgeAPI currencies,
                        AuctionItemSerializer serializer, ItemRewardDispatcher dispatcher) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("交付必须在主线程执行");
        for (MarketOperation task : tasks) {
            if (!player.isOnline()) return;
            if (!"ITEM".equals(task.kind()) && !"CURRENCY".equals(task.kind()) && !"COMMAND".equals(task.kind()) && !"NOTICE".equals(task.kind())) {
                repository.transitionOperation(task.id(),"READY","REVIEW","未知交付类型，保留记录待核对",owner);
                continue;
            }
            if (!repository.transitionOperation(task.id(), "READY", "RUNNING", "交付已抢占", owner)) continue;
            boolean called = false;
            try {
                boolean success;
                if ("ITEM".equals(task.kind())) {
                    var item = serializer.deserialize(task.itemData());
                    if (item == null) {
                        repository.transitionOperation(task.id(), "RUNNING", "REVIEW", "物品数据无法解析，保留记录", owner);
                        continue;
                    }
                    called = true;
                    success = dispatcher.dispatchItem(player, item, ItemRewardDispatcher.DispatchOptions.builder("market")
                        .sourceDetail("ledger:" + task.id()).overflowMailSubject("市场交易物品交付")
                        .overflowMailBody("背包空间不足，交易物品通过邮件发放。").overflowMailSenderName("全球市场").build()).success();
                } else if ("NOTICE".equals(task.kind())) {
                    called = true;
                    String text=org.bukkit.ChatColor.translateAlternateColorCodes('&',task.itemData());
                    if(task.reference().startsWith("notice-title:")) player.sendTitle("拍卖通知",text,10,80,20);
                    else if(task.reference().startsWith("notice-actionbar:")) player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
                    else player.sendMessage(text);
                    success = true;
                } else if ("COMMAND".equals(task.kind())) {
                    called = true;
                    success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), task.itemData());
                } else {
                    var bridge = currencies.bridge(task.currency());
                    if (bridge == null || !bridge.available()) {
                        repository.transitionOperation(task.id(), "RUNNING", "READY", "等待货币接口恢复", owner);
                        continue;
                    }
                    called = true;
                    success = bridge.deposit(player, new BigDecimal(task.amount())).success();
                }
                // A failed or partially applied item dispatch is not safe to replay automatically.
                repository.transitionOperation(task.id(), "RUNNING", success ? "DONE" : "REVIEW",
                    success ? "交付成功" : "接口报告失败，需核对是否部分交付", owner);
            } catch (RuntimeException exception) {
                repository.transitionOperation(task.id(), "RUNNING", called ? "REVIEW" : "READY",
                    called ? "交付结果未知" : "交付尚未执行", owner);
                logger.warning("[Market] 交付异常 id=" + task.id() + ": " + exception.getMessage());
            }
        }
    }
}
