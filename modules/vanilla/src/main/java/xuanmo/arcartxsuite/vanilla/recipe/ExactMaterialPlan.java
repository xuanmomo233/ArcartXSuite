package xuanmo.arcartxsuite.vanilla.recipe;

import java.util.*;
import static xuanmo.arcartxsuite.api.inventory.InventoryItems.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;

/** Quantified, exact-metadata inputs for brewing and native merchant costs. */
public record ExactMaterialPlan(ItemStack[] storage, ItemStack[] inputs, List<WarehouseMaterialAccess.Request> requests) {
    private record Source(ItemStack item, int kind, int slot, long amount) { }
    public static ExactMaterialPlan build(Player player, WorkshopType type, List<ItemStack> wanted, MaterialStock stock) {
        if (!type.matches(player.getOpenInventory()) || wanted.size() != type.inputCount(player.getOpenInventory())) return null;
        ItemStack[] storage = copy(player.getInventory().getStorageContents());
        ItemStack[] current = new ItemStack[wanted.size()];
        boolean ready = true;
        for (int i = 0; i < current.length; i++) {
            current[i] = copy(player.getOpenInventory().getTopInventory().getItem(type.inputSlot(i)));
            ItemStack need = wanted.get(i);
            if (empty(need) ? !empty(current[i]) : empty(current[i]) || !current[i].isSimilar(need) || current[i].getAmount() < need.getAmount()) ready = false;
        }
        if (ready) return new ExactMaterialPlan(storage, current, List.of());
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < current.length; i++) if (!empty(current[i])) sources.add(new Source(current[i], 0, i, current[i].getAmount()));
        for (int i = 0; i < storage.length; i++) if (!empty(storage[i])) sources.add(new Source(storage[i], 1, i, storage[i].getAmount()));
        if (stock.warehouse() != null && stock.warehouse().available()) for (var entry : stock.warehouse().entries())
            sources.add(new Source(entry.item(), 2, entry.slot(), entry.amount()));
        long[] left = sources.stream().mapToLong(Source::amount).toArray();
        Map<Integer, Integer> warehouse = new LinkedHashMap<>();
        ItemStack[] target = new ItemStack[wanted.size()];
        for (int i = 0; i < wanted.size(); i++) {
            ItemStack need = wanted.get(i);
            if (empty(need)) continue;
            int missing = need.getAmount();
            if (missing > need.getMaxStackSize()) return null;
            for (int n = 0; n < sources.size() && missing > 0; n++) {
                Source source = sources.get(n);
                if (!source.item().isSimilar(need)) continue;
                int take = (int)Math.min(missing, left[n]);
                if (take == 0) continue;
                if (target[i] == null) { target[i] = source.item().clone(); target[i].setAmount(0); }
                target[i].setAmount(target[i].getAmount() + take); left[n] -= take; missing -= take;
                if (source.kind() == 2) warehouse.merge(source.slot(), take, Integer::sum);
            }
            if (missing != 0) return null;
        }
        for (int n = 0; n < sources.size(); n++) {
            Source source = sources.get(n);
            if (source.kind() == 1) {
                storage[source.slot()] = left[n] == 0 ? null : source.item().clone();
                if (storage[source.slot()] != null) storage[source.slot()].setAmount((int)left[n]);
            }
        }
        for (int n = 0; n < sources.size(); n++) {
            Source source = sources.get(n);
            if (source.kind() != 0 || left[n] == 0) continue;
            int remaining = (int)left[n], slot = source.slot();
            if (target[slot] != null && target[slot].isSimilar(source.item())) {
                int add = Math.min(remaining, target[slot].getMaxStackSize() - target[slot].getAmount());
                target[slot].setAmount(target[slot].getAmount() + add); remaining -= add;
            }
            if (remaining > 0) { ItemStack returned = source.item().clone(); returned.setAmount(remaining); if (!put(storage, returned)) return null; }
        }
        if (warehouse.size() > 9) return null;
        return new ExactMaterialPlan(storage, target, warehouse.entrySet().stream().map(e -> new WarehouseMaterialAccess.Request(e.getKey(), e.getValue())).toList());
    }
}
