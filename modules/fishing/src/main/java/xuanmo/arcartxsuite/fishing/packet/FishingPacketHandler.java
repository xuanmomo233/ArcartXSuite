package xuanmo.arcartxsuite.fishing.packet;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
import xuanmo.arcartxsuite.fishing.service.FishingService;

/**
 * 钓鱼模块客户端包处理器。
 * <p>
 * 处理客户端回包：input / open_collection / refresh_collection
 */
public final class FishingPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_FISHING";

    private final java.util.function.Function<Player, FishingMinigame> minigameLookup;
    private final Supplier<FishingService> serviceSupplier;
    private final Supplier<String> collectionUiIdSupplier;

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> collectionUiIdSupplier) {
        this.minigameLookup = minigameLookup;
        this.serviceSupplier = serviceSupplier;
        this.collectionUiIdSupplier = collectionUiIdSupplier;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;

        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "input" -> {
                if (data.size() > 1) {
                    String pressingValue = data.get(1);
                    if (!"true".equalsIgnoreCase(pressingValue)
                        && !"false".equalsIgnoreCase(pressingValue)) {
                        break;
                    }
                    boolean pressing = Boolean.parseBoolean(pressingValue);
                    FishingMinigame minigame = minigameLookup.apply(player);
                    if (minigame != null && !minigame.isFinished()) {
                        minigame.getSession().setPressing(pressing);
                    }
                }
            }
            case "open_collection", "refresh_collection" -> {
                FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
                String uiId = collectionUiIdSupplier != null ? collectionUiIdSupplier.get() : null;
                if (service != null && uiId != null && !uiId.isBlank()) {
                    service.pushCollectionData(player, uiId);
                }
            }
            case "open_minigame" -> {
                // UI 打开后主动请求 init 数据，防止服务端主动推送时序问题
                FishingMinigame minigame = minigameLookup.apply(player);
                if (minigame != null && !minigame.isFinished()) {
                    minigame.sendInitPacket(player);
                }
            }
            default -> {
                // unknown action
            }
        }
        return true;
    }
}
