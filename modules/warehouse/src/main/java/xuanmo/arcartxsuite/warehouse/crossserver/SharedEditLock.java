package xuanmo.arcartxsuite.warehouse.crossserver;

import java.util.UUID;

/**
 * 共享仓库编辑锁持有者（本机或远程子服）。
 */
public record SharedEditLock(UUID playerUuid, String playerName, String nodeId) {

    public boolean heldBy(UUID playerUuid) {
        return this.playerUuid != null && this.playerUuid.equals(playerUuid);
    }
}
