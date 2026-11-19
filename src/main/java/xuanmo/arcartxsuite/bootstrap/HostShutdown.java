package xuanmo.arcartxsuite.bootstrap;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plain bootstrap code: keep the protection layer alive until the core's actual retirement. */
public final class HostShutdown {
    interface Hooks {
        void add(Thread hook);
        void remove(Thread hook);
    }

    private final Logger logger;
    private final long waitMillis;
    private final Hooks hooks;
    private final Thread hook;
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private boolean started;

    public HostShutdown(Logger logger) {
        this(logger, Duration.ofSeconds(30), new Hooks() {
            @Override public void add(Thread hook) { Runtime.getRuntime().addShutdownHook(hook); }
            @Override public void remove(Thread hook) { Runtime.getRuntime().removeShutdownHook(hook); }
        });
    }

    HostShutdown(Logger logger, Duration timeout, Hooks hooks) {
        this.logger = logger;
        this.waitMillis = Math.max(1, timeout.toMillis());
        this.hooks = hooks;
        this.hook = new Thread(this::awaitExit, "AXS-Host-Shutdown-Wait");
        hooks.add(hook);
    }

    public synchronized void retire(CompletionStage<Void> coreStopped, Runnable releaseProtection) {
        if (started) return;
        started = true;
        coreStopped.whenCompleteAsync((ignored, failure) -> {
            Throwable problem = failure;
            try { releaseProtection.run(); }
            catch (Throwable error) {
                if (problem == null) problem = error;
                else if (problem != error) problem.addSuppressed(error);
            }
            try { hooks.remove(hook); }
            catch (IllegalStateException ignoredShutdown) { /* The JVM is already running its hooks. */ }
            catch (SecurityException error) {
                if (problem == null) problem = error;
                else problem.addSuppressed(error);
            }
            if (problem == null) completed.complete(null);
            else {
                try { logger.log(Level.SEVERE, "Suite 后台收尾未全部成功，请核对未确认记录。", problem); }
                catch (Throwable ignoredLogging) { /* Other JVM hooks may have already closed logging. */ }
                finally { completed.completeExceptionally(problem); }
            }
        });
    }

    public CompletionStage<Void> completion() { return completed.copy(); }

    void awaitExit() {
        try { completed.get(waitMillis, TimeUnit.MILLISECONDS); }
        catch (TimeoutException timeout) {
            logger.severe("Suite 后台收尾超过 " + waitMillis + " ms，JVM 将继续退出；尚未完成的保存不能视为成功。");
        } catch (ExecutionException failure) {
            logger.warning("Suite 带收尾错误退出，请查看之前的异常及未确认记录。");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warning("Suite 停机等待被中断，后台保存尚未确认完成。");
        }
    }
}
