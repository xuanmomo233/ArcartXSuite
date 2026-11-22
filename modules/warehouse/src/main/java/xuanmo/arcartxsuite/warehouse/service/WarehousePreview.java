package xuanmo.arcartxsuite.warehouse.service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;

/** The entry point determines which warehouses the preview session may disclose. */
record WarehousePreview(UUID target, boolean administrator) {
    boolean permits(Player viewer) {
        return viewer.hasPermission(administrator ? "arcartxsuite.warehouse.admin" : "arcartxsuite.warehouse.use");
    }

    Warehouses read(Player viewer, WarehouseRepository repository) throws SQLException {
        Map<String, WarehouseRecord> personal = new LinkedHashMap<>();
        Map<String, SharedWarehouseRecord> shared = new LinkedHashMap<>();
        if (permits(viewer)) {
            for (WarehouseRecord row : repository.loadPersonalWarehouses(target)) {
                if (administrator || row.showcaseEnabled()) personal.put(row.warehouseId(), row);
            }
            for (SharedWarehouseRecord row : repository.loadSharedWarehousesByOwner(target)) {
                if (administrator || row.showcaseEnabled()) shared.put(row.id(), row);
            }
        }
        return new Warehouses(personal, shared);
    }

    record Warehouses(Map<String, WarehouseRecord> personal, Map<String, SharedWarehouseRecord> shared) {
        boolean contains(String ownerType, String id) {
            return WarehouseService.OWNER_SHARED.equals(ownerType) ? shared.containsKey(id) : personal.containsKey(id);
        }
    }
}
