package xuanmo.arcartxsuite.fishing.packet;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;

/**
 * 钓鱼模块客户端包处理器。
 * <p>
 * 处理客户端回包：input / open_collection
 */
public final class FishingPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_FISHING";

    private final java.util.function.Function<Player, FishingMinigame> minigameLookup;

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup) {
        this.minigameLookup = minigameLookup;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;

        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "input" -> {
                if (data.size() > 1) {
                    boolean pressing = Boolean.parseBoolean(data.get(1));
                    FishingMinigame minigame = minigameLookup.apply(player);
                    if (minigame != null && !minigame.isFinished()) {
                        minigame.getSession().setPressing(pressing);
                    }
                }
            }
            case "open_collection" -> {
                // 打开图鉴，由服务端控制（暂留接口）
            }
            default -> {
                // unknown action
            }
        }
        return true;
    }
}
