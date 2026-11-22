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
        Callable<Void> publish, Callable<Void> rollback, Callable<MapOperationResult> duplicate, MapOperationResult cancelled) {
        return storage.reserve(reserve, () -> eligible.getAsBoolean() ? settle.get() : cancelled,
            MapOperationResult::success, publish, rollback, duplicate, cancelled);
    }
}
