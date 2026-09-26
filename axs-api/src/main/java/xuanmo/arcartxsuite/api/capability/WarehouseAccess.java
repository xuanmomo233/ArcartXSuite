package xuanmo.arcartxsuite.api.capability;

import java.math.BigDecimal;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability for operating a player's personal warehouse without opening its UI.
 * <p>
 * Personal warehouse slots are unlocked progressively ({@code base-slots} +
 * purchased slots); the same storage backs both the warehouse UI and the
 * extra-backpack UI. All operations are serialized per owner by the warehouse
 * service itself, callers do not need additional locking.
 * <p>
 * Public capability: external plugins may obtain it via
 * {@code AxsCapabilities.get(WarehouseAccess.class)}.
 */
@PublicCapability
public interface WarehouseAccess {

    /**
     * Returns whether the personal warehouse is enabled and usable for the player.
     */
    boolean isAvailable(@NotNull Player player);

    /**
     * Returns the player's personal warehouse snapshot: unlocked slot count,
     * used slot count, purchase availability, and the next-slot price.
     */
    @NotNull Snapshot snapshot(@NotNull Player player);

    /**
     * Returns all occupied slots of the player's personal warehouse in slot order.
     */
    @NotNull List<SlotItem> slots(@NotNull Player player);

    /**
     * Stores as much of the item stack as the warehouse can hold
     * (merges into existing stacks first, then fills free slots).
     */
    @NotNull DepositResult deposit(@NotNull Player player, @NotNull ItemStack itemStack);

    /**
     * Writes an item stack to a specific warehouse slot index.
     * {@code itemStack} may be null/air to clear the slot.
     * Fails when the index is out of the unlocked range.
     */
    @NotNull SlotWriteResult setSlot(@NotNull Player player, int slotIndex, @Nullable ItemStack itemStack);

    /**
     * Removes up to {@code amount} items from one warehouse slot.
     */
    @NotNull WithdrawResult withdraw(@NotNull Player player, int slotIndex, int amount);

    /**
     * Purchases one additional warehouse slot at the configured price.
     */
    @NotNull PurchaseResult purchaseSlot(@NotNull Player player);

    /**
     * Returns the price of purchasing {@code amount} additional slots.
     */
    @NotNull SlotPrice slotPrice(@NotNull Player player, int amount);

    /**
     * Admin: grants {@code amount} additional unlocked slots (clamped to the
     * configured maximum). Works for offline players.
     *
     * @return actually granted slot count; {@code -1} on invalid arguments
     */
    long grantSlots(@NotNull OfflinePlayer target, int amount);

    /**
     * Admin: resets purchased slots to zero (unlocked falls back to base-slots).
     */
    boolean resetSlots(@NotNull OfflinePlayer target);

    record Snapshot(
        boolean available,
        long unlockedSlots,
        long usedSlots,
        long maxSlots,
        boolean canPurchase,
        @NotNull SlotPrice nextSlotPrice
    ) {
    }

    record SlotItem(
        int slot,
        @NotNull ItemStack itemStack,
        @NotNull String displayName,
        @NotNull String categoryId
    ) {
    }

    record SlotPrice(
        @NotNull String currencyId,
        @NotNull BigDecimal amount
    ) {
    }

    record DepositResult(
        boolean success,
        long storedAmount,
        int remainingAmount,
        @NotNull String message
    ) {
    }

    record SlotWriteResult(
        boolean success,
        @NotNull String message
    ) {
    }

    record WithdrawResult(
        boolean success,
        int takenAmount,
        @Nullable ItemStack itemStack,
        @NotNull String message
    ) {
    }

    record PurchaseResult(
        boolean success,
        long newUnlockedSlots,
        @NotNull String message
    ) {
    }
}
