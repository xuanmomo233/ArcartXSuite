package xuanmo.arcartxsuite.api.inventory;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;

/** Material snapshot and the warehouse instance that issued it. */
public record MaterialStock(WarehouseMaterialAccess access, WarehouseMaterialAccess.Snapshot warehouse) {
    public static MaterialStock read(Player player, WarehouseMaterialAccess access) {
        return new MaterialStock(access, access == null ? null : access.materials(player));
    }

    public static void readAsync(Player player, WarehouseMaterialAccess access, Consumer<MaterialStock> callback) {
        if (access == null) {
            callback.accept(new MaterialStock(null, null));
        } else {
            access.materialsAsync(player, snapshot -> callback.accept(new MaterialStock(access, snapshot)));
        }
    }

    public List<WarehouseMaterialAccess.Entry> entries() {
        return warehouse == null || !warehouse.available() ? List.of() : warehouse.entries();
    }
}
