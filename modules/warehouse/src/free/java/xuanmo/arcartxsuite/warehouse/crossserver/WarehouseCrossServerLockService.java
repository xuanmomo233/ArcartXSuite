package xuanmo.arcartxsuite.warehouse.crossserver;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;

public final class WarehouseCrossServerLockService {
    public WarehouseCrossServerLockService(
        JavaPlugin plugin, Logger logger, CrossServerAPI crossServer, CrossServerChannelConfig channelConfig,
        BiConsumer<String, SharedEditLock> remoteLock,
        BiConsumer<WarehouseCrossServerPayloadCodec.UnlockPayload, Void> remoteUnlock,
        Consumer<WarehouseCrossServerPayloadCodec.ShowcaseRevocation> remoteShowcase
    ) {
        throw new UnsupportedOperationException("Cross-server warehouse synchronization is not included");
    }

    public void start() {}
    public void shutdown() {}
    public boolean isActive() { return false; }
    public String nodeId() { return "local"; }
    public void publishShowcaseRevocation(WarehouseCrossServerPayloadCodec.ShowcaseRevocation revoke) {}
}
