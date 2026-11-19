package xuanmo.arcartxsuite.market;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Coalesces reads for each view and shares a fixed number of storage slots. Call on the server thread. */
final class MarketPageReads {
    enum View { AUCTIONS, PLAYER_SHOP_LISTINGS, PLAYER_SHOP_SELLERS }

    private static final int MAX_ACTIVE = 32;
    private final MarketWork work;
    private final Logger logger;
    private final Map<Key, Read<?>> pending = new LinkedHashMap<>();
    private final Set<Key> active = new HashSet<>();

    MarketPageReads(MarketWork work, Logger logger) {
        this.work = work;
        this.logger = logger;
    }

    <T> void submit(UUID viewer, View view, BooleanSupplier current, Supplier<T> read, Consumer<T> render, Runnable failure) {
        if (work.isStopping()) return;
        var key = new Key(viewer, view);
        pending.put(key, new Read<>(key, current, read, render, failure));
        dispatch();
    }

    void clear(UUID viewer) {
        pending.keySet().removeIf(key -> key.viewer().equals(viewer));
    }

    void close() {
        pending.clear();
        active.clear();
    }

    private void dispatch() {
        if (work.isStopping()) return;
        var entries = pending.entrySet().iterator();
        while (active.size() < MAX_ACTIVE && entries.hasNext()) {
            var entry = entries.next();
            var key = entry.getKey();
            if (active.contains(key)) continue;
            var read = entry.getValue();
            entries.remove();
            if (!read.current().getAsBoolean()) continue;
            active.add(key);
            try {
                work.async(() -> load(read));
            } catch (RejectedExecutionException closing) {
                active.remove(key);
                return;
            }
        }
    }

    private <T> void load(Read<T> read) {
        if (work.isStopping()) return;
        T result;
        try {
            result = read.query().get();
        } catch (RuntimeException failure) {
            logger.fine("Market 页面读取失败 " + read.key().view() + ": " + failure.getMessage());
            work.main(() -> finish(read, null, false));
            return;
        }
        work.main(() -> finish(read, result, true));
    }

    private <T> void finish(Read<T> read, T result, boolean loaded) {
        try {
            if (pending.containsKey(read.key()) || !read.current().getAsBoolean()) return;
            if (loaded) read.render().accept(result);
            else read.failure().run();
        } catch (RuntimeException failure) {
            logger.fine("Market 页面响应失败 " + read.key().view() + ": " + failure.getMessage());
        } finally {
            active.remove(read.key());
            dispatch();
        }
    }

    private record Key(UUID viewer, View view) {}
    private record Read<T>(Key key, BooleanSupplier current, Supplier<T> query, Consumer<T> render, Runnable failure) {}
}
