package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeferredViewRefreshTest {
    @Test
    void manyNativeLoadEventsPublishOneRefresh() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger runs = new AtomicInteger();
        var refresh = new DeferredViewRefresh(action -> { queued.add(action); return () -> {}; }, runs::incrementAndGet);
        refresh.start();
        refresh.request();
        refresh.request();
        refresh.request();
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, runs.get());
        refresh.request();
        assertEquals(2, queued.size());
    }

    @Test
    void queuedCallbacksCannotPublishAfterShutdownOrAfterRestart() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger runs = new AtomicInteger(), cancels = new AtomicInteger();
        var refresh = new DeferredViewRefresh(action -> { queued.add(action); return cancels::incrementAndGet; }, runs::incrementAndGet);
        refresh.start();
        refresh.request();
        refresh.close();
        assertEquals(1, cancels.get());
        refresh.start();
        refresh.request();
        queued.get(0).run();
        assertEquals(0, runs.get());
        refresh.request();
        assertEquals(2, queued.size(), "stale callback must not clear the current pending refresh");
        queued.get(1).run();
        assertEquals(1, runs.get());
        refresh.close();
        refresh.request();
        assertEquals(2, queued.size());
    }
}
