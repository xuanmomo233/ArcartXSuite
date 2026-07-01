package xuanmo.arcartxsuite.warehouse.crossserver;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;

/**
 * 共享仓库编辑锁跨服广播：获取/释放时通知其他子服更新锁视图。
 */
public final class WarehouseCrossServerLockService {

    private final JavaPlugin plugin;
    private final CrossServerAPI crossServer;
    private final CrossServerChannelConfig channelConfig;
    private final BiConsumer<String, SharedEditLock> remoteLockHandler;
    private final BiConsumer<WarehouseCrossServerPayloadCodec.UnlockPayload, Void> remoteUnlockHandler;

    private CrossServerChannel channel;

    public WarehouseCrossServerLockService(
        JavaPlugin plugin,
        CrossServerAPI crossServer,
        CrossServerChannelConfig channelConfig,
        BiConsumer<String, SharedEditLock> remoteLockHandler,
        BiConsumer<WarehouseCrossServerPayloadCodec.UnlockPayload, Void> remoteUnlockHandler
    ) {
        this.plugin = plugin;
        this.crossServer = crossServer;
        this.channelConfig = channelConfig == null ? CrossServerChannelConfig.disabled() : channelConfig;
        this.remoteLockHandler = remoteLockHandler;
        this.remoteUnlockHandler = remoteUnlockHandler;
    }

    public void start() {
        if (!channelConfig.enabled()) {
            return;
        }
        channel = crossServer.openChannel(
            "warehouse",
            channelConfig,
            delivery -> handlePayload(delivery.payload())
        );
        if (channel.isActive()) {
            plugin.getLogger().info("[Warehouse] 跨服共享仓库编辑锁已启用");
        }
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public String nodeId() {
        return crossServer.nodeId();
    }

    public void publishLock(String sharedId, SharedEditLock lock) {
        if (channel == null || !channel.isActive() || sharedId == null || lock == null) {
            return;
        }
        channel.publish(WarehouseCrossServerPayloadCodec.encodeLock(sharedId, lock));
    }

    public void publishUnlock(String sharedId, UUID playerUuid) {
        if (channel == null || !channel.isActive() || sharedId == null || playerUuid == null) {
            return;
        }
        channel.publish(WarehouseCrossServerPayloadCodec.encodeUnlock(sharedId, playerUuid, nodeId()));
    }

    private void handlePayload(@Nullable String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            WarehouseCrossServerPayloadCodec.DecodedPayload decoded = WarehouseCrossServerPayloadCodec.decode(payload);
            if (WarehouseCrossServerPayloadCodec.TYPE_LOCK.equals(decoded.type())) {
                if (decoded.lock() != null && decoded.sharedId() != null) {
                    remoteLockHandler.accept(decoded.sharedId(), decoded.lock());
                }
            } else if (WarehouseCrossServerPayloadCodec.TYPE_UNLOCK.equals(decoded.type())
                && decoded.unlock() != null) {
                remoteUnlockHandler.accept(decoded.unlock(), null);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}

