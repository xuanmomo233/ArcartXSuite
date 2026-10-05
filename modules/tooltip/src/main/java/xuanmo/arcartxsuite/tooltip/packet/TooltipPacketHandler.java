package xuanmo.arcartxsuite.tooltip.packet;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.service.GunListService;
import xuanmo.arcartxsuite.tooltip.service.TooltipJsonParser;

/**
 * Tooltip 客户端数据包处理器。
 * <p>
 * 接收客户端 Forge mod（ArcartX-Suite-Tooltips）通过 ArcartX 自定义网络包
 * 发来的数据，解析后分发：
 * <ul>
 *   <li>{@link #PACKET_ID} — 物品 tooltip 文本缓存（聊天预览）</li>
 *   <li>{@link #GUN_LIST_PACKET_ID} — TACZ 配件支持枪械列表，打开浏览 UI</li>
 * </ul>
 * 数据包格式（AXS_GUN_LIST）：
 * <pre>
 * data[0]: 动作（"open"）——请求打开支持枪械浏览界面；
 *          枪械数据由客户端 axstooltip.* 函数直供，不走网络包
 * </pre>
 * <p>
 * 缓存操作同步执行：{@link TooltipDataCache} 内部使用 {@code ConcurrentHashMap}，
 * 线程安全，无需异步调度。
 */
public final class TooltipPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_TOOLTIP_DATA";
    public static final String GUN_LIST_PACKET_ID = "AXS_GUN_LIST";

    private final TooltipDataCache cache;
    private final TooltipJsonParser parser;
    private final GunListService gunListService;

    public TooltipPacketHandler(@NotNull TooltipDataCache cache, @NotNull TooltipJsonParser parser,
                                @Nullable GunListService gunListService) {
        this.cache = cache;
        this.parser = parser;
        this.gunListService = gunListService;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId,
                                      @NotNull List<String> data) {
        if (GUN_LIST_PACKET_ID.equalsIgnoreCase(packetId)) {
            handleGunList(player, data);
            return true;
        }
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;

        if (data.size() < 4) return true; // 数据不完整，消费但不处理

        try {
            String fingerprint = data.get(0);
            // data.get(1) 是物品类型 ID，目前不需要单独使用
            String linesJson = data.get(2);
            String structuredData = data.get(3);

            List<String> textLines = parser.parseStringArray(linesJson);
            if (textLines == null || textLines.isEmpty()) return true;

            // 同步存入缓存（ConcurrentHashMap 线程安全，无需异步）
            cache.put(player, fingerprint, textLines, structuredData);
        } catch (Exception e) {
            // 静默处理解析错误
        }

        return true;
    }

    private void handleGunList(@NotNull Player player, @NotNull List<String> data) {
        if (gunListService == null || data.isEmpty() || !"open".equalsIgnoreCase(data.get(0))) return;
        gunListService.requestOpen(player);
    }
}
