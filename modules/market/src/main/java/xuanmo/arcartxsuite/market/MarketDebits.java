package xuanmo.arcartxsuite.market;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;

/** Dispatches confirmed debit intents. Providers that require Bukkit get one call per tick. */
public final class MarketDebits {
    private static final int MAX_PER_TICK = 4;
    private final MarketWork work;
    private final Logger logger;
    private final ConcurrentLinkedQueue<Job> pending = new ConcurrentLinkedQueue<>();
    private final Semaphore backgroundSlots = new Semaphore(MAX_PER_TICK);
    private volatile boolean closed;

    public MarketDebits(MarketWork work, Logger logger) {
        this.work = work;
        this.logger = logger;
    }

    public record Job(String id, BooleanSupplier authorizedOnMain, BooleanSupplier currentSession,
                      @Nullable Supplier<CurrencyTransactionResult> background, Supplier<CurrencyTransactionResult> mainDebit,
                      Consumer<CurrencyTransactionResult> finish) {}

    public void enqueue(Job debit) {
        pending.add(debit);
        if ((closed || work.isStopping()) && pending.remove(debit)) cancel(debit);
    }

    void drain() {
        for (int i = 0; i < MAX_PER_TICK && !closed && !work.isStopping(); i++) {
            if (backgroundSlots.availablePermits() == 0) return;
            Job debit = pending.poll();
            if (debit == null) return;
            if (!authorized(debit)) {
                work.async(() -> cancel(debit));
                continue;
            }
            if (debit.background() == null) {
                var payment = invoke(debit, debit.mainDebit());
                work.async(() -> debit.finish().accept(payment));
                return;
            }
            if (!backgroundSlots.tryAcquire()) {
                pending.add(debit);
                return;
            }
            work.async(() -> {
                try {
                    if (!debit.currentSession().getAsBoolean()) cancel(debit);
                    else debit.finish().accept(invoke(debit, debit.background()));
                } finally {
                    backgroundSlots.release();
                }
            });
        }
    }

    void close() {
        closed = true;
        for (Job debit; (debit = pending.poll()) != null; ) {
            Job waiting = debit;
            work.async(() -> cancel(waiting));
        }
    }

    private boolean authorized(Job debit) {
        try {
            return debit.authorizedOnMain().getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Market 扣款会话校验失败 " + debit.id(), failure);
            return false;
        }
    }

    private CurrencyTransactionResult invoke(Job debit, Supplier<CurrencyTransactionResult> effect) {
        try {
            return java.util.Objects.requireNonNull(effect.get());
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Market 扣款结果不明 " + debit.id(), failure);
            return CurrencyTransactionResult.unknown("");
        }
    }

    private void cancel(Job debit) {
        debit.finish().accept(CurrencyTransactionResult.failure("页面、商品或会话已失效，本次未扣款"));
    }
}
