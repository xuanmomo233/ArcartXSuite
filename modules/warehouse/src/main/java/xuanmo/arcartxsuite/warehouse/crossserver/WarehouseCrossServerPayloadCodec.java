package xuanmo.arcartxsuite.warehouse.crossserver;

import java.util.UUID;

/**
 * 共享仓库编辑锁跨服 payload（Tab 分隔）。
 */
public final class WarehouseCrossServerPayloadCodec {

    public static final String TYPE_LOCK = "LOCK";
    public static final String TYPE_UNLOCK = "UNLOCK";

    private WarehouseCrossServerPayloadCodec() {
    }

    public static String encodeLock(String sharedId, SharedEditLock lock) {
        return String.join("\t",
            TYPE_LOCK,
            sanitize(sharedId),
            lock.playerUuid().toString(),
            sanitize(lock.playerName()),
            sanitize(lock.nodeId())
        );
    }

    public static String encodeUnlock(String sharedId, UUID playerUuid, String nodeId) {
        return String.join("\t",
            TYPE_UNLOCK,
            sanitize(sharedId),
            playerUuid.toString(),
            sanitize(nodeId)
        );
    }

    public static DecodedPayload decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("empty payload");
        }
        String[] parts = payload.split("\t", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException("invalid payload");
        }
        String type = parts[0];
        if (TYPE_LOCK.equals(type)) {
            return new DecodedPayload(type, parts[1], decodeLock(parts), null);
        }
        if (TYPE_UNLOCK.equals(type)) {
            return new DecodedPayload(type, parts[1], null, decodeUnlock(parts));
        }
        throw new IllegalArgumentException("unsupported payload: " + type);
    }

    private static SharedEditLock decodeLock(String[] parts) {
        if (parts.length < 5) {
            throw new IllegalArgumentException("invalid lock payload");
        }
        return new SharedEditLock(
            UUID.fromString(parts[2]),
            parts[3],
            parts[4]
        );
    }

    private static UnlockPayload decodeUnlock(String[] parts) {
        if (parts.length < 4) {
            throw new IllegalArgumentException("invalid unlock payload");
        }
        return new UnlockPayload(parts[1], UUID.fromString(parts[2]), parts[3]);
    }

    public record DecodedPayload(String type, String sharedId, SharedEditLock lock, UnlockPayload unlock) {
    }

    public record UnlockPayload(String sharedId, UUID playerUuid, String nodeId) {
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
