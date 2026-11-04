package xuanmo.arcartxsuite.map.storage;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.map.model.MapOperationResult;

/** Reserve in SQL, settle on the server thread, compensate before releasing the action. */
public final class MapUnlockTransaction {
    private MapUnlockTransaction() { }

    public static CompletableFuture<MapOperationResult> execute(MapStorageExecutor storage,
        Callable<Boolean> reserve, BooleanSupplier eligible, Supplier<MapOperationResult> settle,
        Callable<Void> rollback, MapOperationResult duplicate, MapOperationResult cancelled) {
        return storage.query(reserve).thenCompose(inserted -> {
            if (!inserted) return CompletableFuture.completedFuture(duplicate);
            MapOperationResult result;
            try {
                result = eligible.getAsBoolean() ? settle.get() : cancelled;
            } catch (Throwable error) {
                return storage.compensate(rollback).thenCompose(ignored -> CompletableFuture.failedFuture(error));
            }
            if (result.success()) return CompletableFuture.completedFuture(result);
            MapOperationResult failure = result;
            return storage.compensate(rollback).thenApply(ignored -> failure);
        });
    }
}
