package xuanmo.arcartxsuite.vanilla.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;

/** Rearranges only storage slots, without transferring anything to another inventory. */
public final class InventorySorter {
    public static final String PERMISSION = "arcartxsuite.vanilla.sort";

    private InventorySorter() { }

    public enum Mode {
        TYPE, NAME, AMOUNT;

        public static Mode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TYPE;
            }
        }
    }

    public static void sort(Inventory inventory, int first, Mode mode) {
        ItemStack[] before = InventoryItems.copy(inventory.getStorageContents());
        ItemStack[] after = arrange(before, first, inventory.getMaxStackSize(), mode);
        List<Integer> attempted = new ArrayList<>();
        try {
            for (int slot = first; slot < before.length; slot++) {
                if (Objects.equals(before[slot], after[slot])) continue;
                attempted.add(slot);
                inventory.setItem(slot, after[slot]);
            }
        } catch (RuntimeException failure) {
            for (int slot : attempted) {
                try {
                    inventory.setItem(slot, before[slot]);
                } catch (RuntimeException restoreFailure) {
                    if (failure != restoreFailure) failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    static ItemStack[] arrange(ItemStack[] before, int first, int inventoryLimit, Mode mode) {
        ItemStack[] after = before.clone();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = first; i < before.length; i++) {
            ItemStack item = InventoryItems.copy(before[i]);
            // Other plugins can create over-limit stacks. Leave their slots untouched.
            if (item != null && item.getAmount() > Math.min(inventoryLimit, item.getMaxStackSize())) continue;
            slots.add(i);
            after[i] = null;
            if (item == null) continue;
            for (ItemStack existing : stacks) {
                int room = Math.min(inventoryLimit, existing.getMaxStackSize()) - existing.getAmount();
                if (room <= 0 || !existing.isSimilar(item)) continue;
                int moved = Math.min(room, item.getAmount());
                existing.setAmount(existing.getAmount() + moved);
                item.setAmount(item.getAmount() - moved);
                if (item.getAmount() == 0) break;
            }
            if (item.getAmount() > 0) stacks.add(item);
        }
        Comparator<ItemStack> byType = Comparator.comparingInt(item -> item.getType().ordinal());
        stacks.sort(switch (mode) {
            case TYPE -> byType;
            case NAME -> Comparator.comparing(item -> item.getType().name());
            case AMOUNT -> Comparator.comparingInt(ItemStack::getAmount).reversed().thenComparing(byType);
        });
        for (int i = 0; i < stacks.size(); i++) after[slots.get(i)] = stacks.get(i);
        return after;
    }
}
