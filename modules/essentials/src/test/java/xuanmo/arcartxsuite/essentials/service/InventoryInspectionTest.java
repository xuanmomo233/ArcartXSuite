package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class InventoryInspectionTest {
    @Test
    void staleSnapshotCannotBeTakenOrOverwriteTheTarget() {
        assertNull(InventoryInspection.transferIfUnchanged(stack(20, 1), null, null, false, 64));
        assertNull(InventoryInspection.transferIfUnchanged(null, stack(1, 2), stack(20, 1), false, 64));
        assertNull(InventoryInspection.transferIfUnchanged(stack(20, 1), stack(10, 1), null, true, 64));
        assertNull(InventoryInspection.transferIfUnchanged(stack(20, 1), stack(20, 2), null, false, 64));
    }

    @Test
    void halvesOddStacksAndKeepsOverflowOnTheCursor() {
        var half = InventoryInspection.transferIfUnchanged(stack(7, 1), stack(7, 1), null, true, 64);
        assertEquals(3, half.slot().getAmount());
        assertEquals(4, half.cursor().getAmount());
        var limited = InventoryInspection.transferIfUnchanged(stack(63, 1), stack(63, 1), stack(16, 1), false, 64);
        assertEquals(64, limited.slot().getAmount());
        assertEquals(15, limited.cursor().getAmount());
    }

    @Test
    void supportedClicksConserveItemsOfEachKindAndDoNotMutateInputs() {
        for (boolean right : new boolean[]{false, true}) {
            for (int slotAmount : new int[]{0, 1, 2, 17, 64}) {
                for (int cursorAmount : new int[]{0, 1, 2, 17, 64}) {
                    for (int cursorKind : new int[]{1, 2}) {
                        ItemStack slot = stack(slotAmount, 1);
                        ItemStack cursor = stack(cursorAmount, cursorKind);
                        var result = InventoryInspection.transferIfUnchanged(slot, slot, cursor, right, 64);
                        assertNotNull(result);
                        for (int kind : new int[]{1, 2}) {
                            assertEquals(amount(slot, kind) + amount(cursor, kind),
                                amount(result.slot(), kind) + amount(result.cursor(), kind));
                        }
                        assertEquals(slotAmount, amount(slot, 1));
                        assertEquals(cursorAmount, amount(cursor, cursorKind));
                    }
                }
            }
        }
    }

    private static ItemStack stack(int amount, int kind) {
        return amount == 0 ? null : new TestStack(amount, kind);
    }

    private static int amount(ItemStack stack, int kind) {
        return stack instanceof TestStack item && item.kind == kind ? item.getAmount() : 0;
    }

    private static final class TestStack extends ItemStack {
        private final int kind;

        TestStack(int amount, int kind) {
            super(Material.STONE, amount);
            this.kind = kind;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof TestStack item && item.kind == kind;
        }
    }
}
