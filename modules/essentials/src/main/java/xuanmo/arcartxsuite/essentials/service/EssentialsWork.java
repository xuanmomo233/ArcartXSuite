package xuanmo.arcartxsuite.essentials.service;

import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** One ordered, bounded background queue plus a fence for externally scheduled/read callbacks. */
public final class EssentialsWork implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ModuleCallback callbacks=new ModuleCallback(()->{});
    private final ModuleExecutor worker=new ModuleExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),task->{
        var thread=new Thread(task,"AXS-Essentials-Storage");thread.setDaemon(true);return thread;
    });
    private volatile boolean closed;
    private final java.util.Set<CompletableFuture<?>> mainResults=ConcurrentHashMap.newKeySet();
    private final Semaphore mainSlots=new Semaphore(256);

    public EssentialsWork(JavaPlugin plugin){this.plugin=plugin;}

    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        if(closed)return CompletableFuture.failedFuture(new RejectedExecutionException("Essentials retired"));
        var result=new CompletableFuture<T>();
        try{worker.execute(()->{
            try{result.complete(operation.call());}
            catch(Exception|LinkageError failure){result.completeExceptionally(failure);}
        });}catch(RejectedExecutionException failure){result.completeExceptionally(failure);}
        return result.copy();
    }

    /** Unstarted read-only work can be discarded on retirement; accepted writes still drain through submit. */
    public <T> CompletableFuture<T> read(Callable<T> query) {
        return submit(() -> {
            if(closed)throw new RejectedExecutionException("Essentials retired before read");
            return query.call();
        });
    }

    public <T> T call(Supplier<T> operation,T unavailable){return callbacks.call(operation,unavailable);}

    public void onMain(Runnable operation) {
        if(closed)return;
        try{Bukkit.getScheduler().runTask(plugin,()->callbacks.call(()->{operation.run();return null;},null));}
        catch(RuntimeException|LinkageError closing){if(!closed)throw closing;}
    }

    public boolean isClosed(){return closed;}

    /** A bounded main-thread continuation. Retirement fails queued replies instead of leaving their futures pending. */
    public <T> CompletableFuture<T> main(Supplier<T> operation) {
        if(closed)return CompletableFuture.failedFuture(new RejectedExecutionException("Essentials retired"));
        if(Bukkit.isPrimaryThread()) {
            try{
                CompletableFuture<T> value=callbacks.call(()->CompletableFuture.completedFuture(operation.get()),null);
                return value!=null?value:CompletableFuture.failedFuture(new RejectedExecutionException("Essentials retired"));
            }
            catch(RuntimeException|LinkageError failure){return CompletableFuture.failedFuture(failure);}
        }
        if(!mainSlots.tryAcquire())return CompletableFuture.failedFuture(new RejectedExecutionException("Essentials main queue full"));
        var result=new CompletableFuture<T>();
        mainResults.add(result);
        result.whenComplete((value,failure)->{mainResults.remove(result);mainSlots.release();});
        if(closed)result.completeExceptionally(new RejectedExecutionException("Essentials retired"));
        else try {
            Bukkit.getScheduler().runTask(plugin,()->{
                boolean ran=callbacks.call(()->{
                    try{result.complete(operation.get());}
                    catch(RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
                    return true;
                },false);
                if(!ran)result.completeExceptionally(new RejectedExecutionException("Essentials retired"));
            });
        }catch(RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
        return result.copy();
    }

    @Override public void close(){
        closed=true;callbacks.close();worker.shutdown();
        for(var result:mainResults)result.completeExceptionally(new RejectedExecutionException("Essentials retired"));
    }

    public CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(worker.stopped().toCompletableFuture(),callbacks.stopped().toCompletableFuture());
    }
}
