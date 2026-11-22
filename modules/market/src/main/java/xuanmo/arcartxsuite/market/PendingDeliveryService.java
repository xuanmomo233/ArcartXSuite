package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.shop.ShopService;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Persistent READY tasks resume automatically; uncertain tasks require reconciliation. */
public final class PendingDeliveryService implements Listener, AutoCloseable {
    private static final int MAX_PLAYERS_PER_TICK = 4;
    private static final long DELIVERY_BUDGET_NANOS = 2_000_000L;

    private final JavaPlugin plugin;
    private final MarketWork work;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencies;
    private final MarketItemDelivery items;
    private final MarketLedger ledger;
    private final Logger logger;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<ReadyAction> ready = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean beating = new AtomicBoolean();
    private volatile boolean closed;
    private BukkitTask timer;
    private BukkitTask drainTimer;
    private boolean preferPayment = true;

    public PendingDeliveryService(MarketWork work, JavaPlugin plugin, MarketRepository repository, CurrencyBridgeAPI currencies,
                                  MarketItemDelivery items, Logger logger, MarketLedger ledger) {
        this.plugin=plugin;this.work = work; this.repository=repository; this.currencies=currencies;
        this.items=items; this.logger=logger; this.ledger=ledger;
    }

    public void start(@Nullable ShopService shop) {
        drainTimer=work.repeat(() -> drainTick(shop), 1L, 1L);
        timer=work.repeat(() -> {
            if (closed) return;
            if (beating.compareAndSet(false,true)) work.async(() -> {
                try { if (!closed) ledger.heartbeat(); }
                catch(RuntimeException exception) {logger.warning("[Market] 恢复扫描失败: "+exception.getMessage());}
                finally {beating.set(false);}
            });
            for (Player player : Bukkit.getOnlinePlayers()) request(player);
        },40L,40L);
    }

    private void drainTick(@Nullable ShopService shop) {
        if (closed) return;
        // A debit and player-data save can each block on I/O. Give each active queue its own tick.
        if (shop != null && shop.hasQueuedPayments() && (preferPayment || ready.isEmpty())) {
            preferPayment = false;
            shop.drainPayments();
        } else if (!ready.isEmpty()) {
            preferPayment = true;
            deliverReady();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { request(event.getPlayer()); }

    private void request(Player player) {
        if (closed) return;
        UUID playerId = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(playerId);
        if (!admission.admitted() || !inFlight.add(playerId)) return;
        var session = new DeliverySession(player, admission);
        runOnWorker(playerId, () -> {
            try {
                var tasks=repository.pendingDeliveries(playerId,items.node(),50);
                if(closed || tasks.isEmpty()) {inFlight.remove(playerId);return;}
                if (!enqueue(() -> {
                    if (!canDeliver(session)) {
                        inFlight.remove(playerId);
                        return;
                    }
                    try {
                        items.deliver(player, tasks, this::enqueue, () -> canDeliver(session));
                        var effects = tasks.stream().filter(task -> !"ITEM".equals(task.kind())).iterator();
                        prepareNext(session, effects);
                    } catch (RuntimeException | LinkageError failure) {
                        inFlight.remove(playerId);
                        logger.warning("[Market] 准备交付失败: " + failure.getMessage());
                    }
                })) {
                    inFlight.remove(playerId);
                }
            } catch(RuntimeException exception) {
                inFlight.remove(playerId);
                logger.warning("[Market] 无法读取交付队列: "+exception.getMessage());
            }
        });
    }

    private void prepareNext(DeliverySession session, Iterator<MarketOperation> effects) {
        UUID playerId = session.admission().playerId();
        if (!effects.hasNext() || !canDeliver(session)) {
            inFlight.remove(playerId);
            return;
        }
        MarketOperation task = effects.next();
        Player player = session.player();
        try {
            switch (task.kind()) {
                case "CURRENCY" -> {
                    var bridge = currencies.bridge(task.currency());
                    if (bridge == null || !bridge.available()) {
                        resume(session, effects);
                        return;
                    }
                    var amount = new BigDecimal(task.amount());
                    var background = bridge.prepareBackgroundCredit(player, amount);
                    if (background != null) {
                        claim(session, effects, task, () -> background.get().success(), true,
                            bridge.creditRequiresReconciliation());
                    } else {
                        claim(session, effects, task, () -> bridge.deposit(player, amount).success(),
                            false, bridge.creditRequiresReconciliation());
                    }
                }
                case "COMMAND" -> claim(session, effects, task,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), task.itemData()), false, false);
                case "NOTICE" -> claim(session, effects, task, () -> sendNotice(player, task), false, false);
                default -> runOnWorker(playerId, () -> {
                    try { ledger.flagUnknownDelivery(task); }
                    finally { resume(session, effects); }
                });
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("[Market] 交付准备失败 id=" + task.id() + ": " + failure.getMessage());
            resume(session, effects);
        }
    }

    private void claim(DeliverySession session, Iterator<MarketOperation> effects, MarketOperation task,
                       Supplier<Boolean> effect, boolean background, boolean requiresReconciliation) {
        UUID playerId = session.admission().playerId();
        runOnWorker(playerId, () -> {
            if (closed) { inFlight.remove(playerId); return; }
            try {
                if (!ledger.claimDelivery(task)) {
                    resume(session, effects);
                    return;
                }
            } catch (RuntimeException failure) {
                logger.warning("[Market] 交付抢占失败 id=" + task.id() + ": " + failure.getMessage());
                inFlight.remove(playerId);
                return;
            }
            if (!enqueue(() -> {
                if (!canDeliver(session)) {
                    runOnWorker(playerId, () -> finish(session, effects, task, "READY", "交付尚未执行"));
                } else if (background) {
                    runOnWorker(playerId, () -> execute(session, effects, task, effect, true, requiresReconciliation));
                } else {
                    execute(session, effects, task, effect, false, requiresReconciliation);
                }
            }, () -> runOnWorker(playerId, () -> finish(session, effects, task, "READY", "交付尚未执行")))) {
                finish(session, effects, task, "READY", "交付尚未执行");
            }
        });
    }

    private void execute(DeliverySession session, Iterator<MarketOperation> effects, MarketOperation task,
                         Supplier<Boolean> effect, boolean worker, boolean requiresReconciliation) {
        UUID playerId = session.admission().playerId();
        if (worker && (closed || work.isStopping() || !session.stillAdmitted())) {
            finish(session, effects, task, "READY", "交付尚未执行");
            return;
        }
        String state = "REVIEW";
        String note = "接口报告失败，需核对是否部分交付";
        try {
            if (effect.get()) {
                if (requiresReconciliation) {
                    note = "货币接口报告成功，尚无持久回执，请核对实际入账";
                } else {
                    state = "DONE";
                    note = "交付成功";
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            note = "交付结果未知";
            logger.warning("[Market] 交付异常 id=" + task.id() + ": " + failure.getMessage());
        }
        String result = state;
        String detail = note;
        if (worker) finish(session, effects, task, result, detail);
        else runOnWorker(playerId, () -> finish(session, effects, task, result, detail));
    }

    private void finish(DeliverySession session, Iterator<MarketOperation> effects,
                        MarketOperation task, String state, String note) {
        try {
            ledger.finishDelivery(task, state, note);
        } catch (RuntimeException failure) {
            // The effect may already have happened. Recovery leaves this claim for reconciliation.
            logger.warning("[Market] 交付回执待核对 id=" + task.id() + ": " + failure.getMessage());
        } finally {
            resume(session, effects);
        }
    }

    private void resume(DeliverySession session, Iterator<MarketOperation> effects) {
        if (!effects.hasNext() || !enqueue(() -> prepareNext(session, effects))) {
            inFlight.remove(session.admission().playerId());
        }
    }

    private void runOnWorker(UUID playerId, Runnable action) {
        try { work.async(action); }
        catch (RuntimeException failure) {
            inFlight.remove(playerId);
            if (!closed) logger.warning("[Market] 调度交付任务失败: " + failure.getMessage());
        }
    }

    private boolean canDeliver(DeliverySession session) {
        return !closed && session.player().isOnline() && Bukkit.getPlayer(session.admission().playerId()) == session.player()
            && session.stillAdmitted();
    }

    record DeliverySession(Player player, AuthSnapshot admission) {
        boolean stillAdmitted() {
            AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
            return admission.admitted() && current.admitted() && admission.generation() == current.generation()
                && Objects.equals(admission.connectionId(), current.connectionId());
        }
    }

    private static boolean sendNotice(Player player, MarketOperation task) {
        String text = ChatColor.translateAlternateColorCodes('&', task.itemData());
        if (task.reference().startsWith("notice-title:")) player.sendTitle("拍卖通知", text, 10, 80, 20);
        else if (task.reference().startsWith("notice-actionbar:")) player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
        else player.sendMessage(text);
        return true;
    }

    private boolean enqueue(Runnable action) {
        return enqueue(action, () -> { });
    }

    private boolean enqueue(Runnable action, Runnable discarded) {
        if (closed) return false;
        var pending = new ReadyAction(action, discarded);
        ready.add(pending);
        if (closed && ready.remove(pending)) return false;
        return true;
    }

    private static final class ReadyAction extends AtomicBoolean implements Runnable {
        private final Runnable action;
        private final Runnable discarded;
        ReadyAction(Runnable action, Runnable discarded) { this.action = action; this.discarded = discarded; }
        @Override public void run() { if (compareAndSet(false, true)) action.run(); }
        void discard() { if (compareAndSet(false, true)) discarded.run(); }
    }

    private void deliverReady() {
        if (closed || ready.isEmpty()) return;
        long start = System.nanoTime();
        for (int processed = 0; processed < MAX_PLAYERS_PER_TICK; processed++) {
            var action = ready.poll();
            if (action == null) return;
            try {
                action.run();
            } catch (RuntimeException exception) {
                logger.warning("[Market] 执行交付队列失败: " + exception.getMessage());
            }
            if (System.nanoTime() - start >= DELIVERY_BUDGET_NANOS) return;
        }
    }

    @Override
    public void close() {
        closed = true;
        if (timer != null) timer.cancel();
        if (drainTimer != null) drainTimer.cancel();
        for (ReadyAction pending; (pending = ready.poll()) != null; ) pending.discard();
        inFlight.clear();
    }
}
