package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Personal warehouse material access. Call on the Bukkit main thread. */
@PublicCapability
public interface WarehouseMaterialAccess {
    /** Returns usable stock, independently of UI paging/filtering. Locked stock is excluded. */
    Snapshot materials(Player player);
    /** Nonblocking database read, with Bukkit item decoding and callback on the main thread. */
    void materialsAsync(Player player, Consumer<Snapshot> callback);

    /**
     * Legacy unjournaled withdrawal. Implementations now reject this path; use prepareMaterials,
     * authorizeDelivery and completeDelivery instead. Existing warehouse storage APIs are unaffected.
     */
    @Deprecated
    Result withdrawMaterials(Player player, Snapshot snapshot, List<Request> requests);

    /** Async durable escrow; callback runs on the main thread. Never delivers to a container itself. */
    void prepareMaterials(Player player, Snapshot snapshot, List<Request> requests, Target target, Consumer<Prepared> callback);
    /** Persist delivery authorization before applying inventory changes. False means do not apply. */
    void authorizeDelivery(Player player, String operationId, Consumer<Boolean> callback);
    /** Records an in-memory application, not a claim that native world/player saves are durable. */
    void recordApplied(Player player, String operationId);
    void completeDelivery(Player player, String operationId, Consumer<Boolean> callback);
    void recordUncertain(Player player, String operationId, String reason);
    /** Safe cancellation is allowed only while HELD, before authorization. */
    void cancelPrepared(Player player, String operationId);

    record Target(String adapter, String identity, String persistence, String evidence) { }
    record Prepared(boolean success, String operationId, String message) { }

    record Entry(int slot, String identity, long createdAt, long amount, ItemStack item) {
        public Entry { item = item.clone(); item.setAmount(1); }
        @Override public ItemStack item() { return item.clone(); }
    }
    record Snapshot(String warehouseId, List<Entry> entries, String unavailableReason) {
        public Snapshot { entries = List.copyOf(entries); }
        public boolean available() { return unavailableReason.isEmpty(); }
    }
    record Request(int slot, int amount) { }
    record Result(boolean success, List<ItemStack> items, String message) {
        public Result { items = items.stream().map(ItemStack::clone).toList(); }
        @Override public List<ItemStack> items() { return items.stream().map(ItemStack::clone).toList(); }
    }
}
