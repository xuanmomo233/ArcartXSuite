package xuanmo.arcartxsuite.extrabackpack.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ExtraBackpackRepository {

    void initialize() throws SQLException;

    List<String> playerDataTables();

    List<String> allTables();

    long loadExtraBackpackCapacity(UUID playerUuid, String categoryId) throws SQLException;

    void setExtraBackpackCapacity(UUID playerUuid, String categoryId, long capacity, long updatedAt) throws SQLException;

    List<ExtraBackpackSlotRecord> loadExtraBackpackSlots(UUID playerUuid) throws SQLException;

    void saveExtraBackpackSlots(UUID playerUuid, List<ExtraBackpackSlotRecord> items) throws SQLException;

    int deletePlayerData(UUID playerUuid) throws SQLException;

    int deleteAllPlayerData() throws SQLException;

    void close();

    record ExtraBackpackSlotRecord(UUID playerUuid, String categoryId, int slot, String itemData, long updatedAt) {
    }
}
