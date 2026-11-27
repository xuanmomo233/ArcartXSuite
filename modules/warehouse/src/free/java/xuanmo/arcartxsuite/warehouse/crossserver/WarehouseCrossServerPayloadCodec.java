package xuanmo.arcartxsuite.warehouse.crossserver;

import java.util.UUID;

/** Keeps the local showcase view invalidation value without a transport codec. */
public final class WarehouseCrossServerPayloadCodec {
    private WarehouseCrossServerPayloadCodec() {}

    public record UnlockPayload(String sharedId, UUID playerUuid, String nodeId) {}

    public record ShowcaseRevocation(UUID ownerUuid, String ownerType, String warehouseId) {}

    public record DecodedPayload(ShowcaseRevocation showcaseRevocation) {}

    public static String encodeShowcaseRevoked(ShowcaseRevocation revoke) {
        throw new UnsupportedOperationException("Cross-server warehouse codec is not included");
    }

    public static DecodedPayload decode(String payload) {
        throw new UnsupportedOperationException("Cross-server warehouse codec is not included");
    }
}
