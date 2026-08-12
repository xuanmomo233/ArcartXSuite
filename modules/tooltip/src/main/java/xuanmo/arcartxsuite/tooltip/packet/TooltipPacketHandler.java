package xuanmo.arcartxsuite.tooltip.packet;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.service.TooltipJsonParser;

/**
 * Tooltip 客户端数据包处理器。
 * <p>
 * 接收客户端 Forge mod（ArcartX-Suite-Mod）通过 ArcartX 自定义网络包
 * 发来的 tooltip 数据，解析后存入 {@link TooltipDataCache}。
 * <p>
 * 数据包格式：
 * <pre>
 * packetId: "AXS_TOOLTIP_DATA"
 * data[0]: 物品指纹（类型 ID + NBT hash，用于服务端去重）
 * data[1]: 物品类型 ID
 * data[2]: tooltip 文本行（JSON 数组字符串）
 * data[3]: 结构化数据（JSON 对象字符串）
 * </pre>
 */
public final class TooltipPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_TOOLTIP_DATA";

    private final TooltipDataCache cache;
    private final TooltipJsonParser parser;

    public TooltipPacketHandler(@NotNull TooltipDataCache cache, @NotNull TooltipJsonParser parser) {
        this.cache = cache;
        this.parser = parser;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId,
                                      @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;

        if (data.size() < 4) return true; // 数据不完整，消费但不处理

        try {
            String fingerprint = data.get(0);
            // data.get(1) 是物品类型 ID，目前不需要单独使用
            String linesJson = data.get(2);
            String structuredData = data.get(3);

            List<String> textLines = parser.parseStringArray(linesJson);
            if (textLines == null || textLines.isEmpty()) return true;

            // 异步存入缓存（避免阻塞网络线程）
            final UUID playerUuid = player.getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("ArcartXSuite"),
                () -> cache.put(player, fingerprint, textLines, structuredData)
            );
        } catch (Exception e) {
            // 静默处理解析错误
        }

        return true;
    }
}
