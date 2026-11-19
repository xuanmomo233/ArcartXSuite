package xuanmo.arcartxsuite.tooltip.packet;

import java.util.List;
import org.bukkit.Bukkit;
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
 *   <li>{@link #PACKET_ID} — 仅供发送者本人展示的客户端 tooltip 文本缓存</li>
 *   <li>{@link #GUN_LIST_PACKET_ID} — TACZ 配件支持枪械列表，打开浏览 UI</li>
 * </ul>
 * 数据包格式（AXS_GUN_LIST）：
 * <pre>
 * data[0]: 动作（"open"）——请求打开支持枪械浏览界面；
 *          枪械数据由客户端 axstooltip.* 函数直供，不走网络包
 * </pre>
 * <p>
 * 只有更新后的合法数据进入缓存。相同的数据包直接续期。
 */
public final class TooltipPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_TOOLTIP_DATA";
    public static final String GUN_LIST_PACKET_ID = "AXS_GUN_LIST";
    private static final int MAX_ITEM_ID_LENGTH = 192;
    private static final int MAX_JSON_BYTES = 32 * 1024;

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
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (data.size() != 4) return true;
        var playerId = player.getUniqueId();
        if (Bukkit.getPlayer(playerId) != player) return true;

        try {
            String fingerprint = data.get(0);
            String itemId = data.get(1);
            String linesJson = data.get(2);
            String structuredData = data.get(3);
            if (fingerprint == null || fingerprint.length() > MAX_ITEM_ID_LENGTH || !fingerprint.equals(itemId)) return true;
            var match = cache.compareUpload(player, fingerprint, linesJson, structuredData);
            if (match.renewed()) return true;
            if (!validItemId(fingerprint)) return true;
            List<String> textLines = match.textLines();
            if (textLines == null && !withinBytes(linesJson, MAX_JSON_BYTES)) return true;
            if (!match.structuredUnchanged() && !withinBytes(structuredData, MAX_JSON_BYTES)) return true;
            if (textLines == null) textLines = parser.parseStringArray(linesJson);
            if (textLines == null || textLines.isEmpty()) return true;
            if (!match.structuredUnchanged() && !parser.isStructuredObject(structuredData)) return true;
            if (Bukkit.getPlayer(playerId) == player) cache.put(player, fingerprint, linesJson, textLines, structuredData);
        } catch (Exception e) {
            // 静默处理解析错误
        }

        return true;
    }

    public boolean handleGunList(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!GUN_LIST_PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (gunListService != null && data.size() == 1 && "open".equalsIgnoreCase(data.get(0))
                && Bukkit.getPlayer(player.getUniqueId()) == player) {
            gunListService.requestOpen(player);
        }
        return true;
    }

    private static boolean validItemId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) return false;
        for (int index = 0; index < id.length(); index++) {
            char c = id.charAt(index);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
                    || index == colon || (index > colon && c == '/')) continue;
            return false;
        }
        return true;
    }

    static boolean withinBytes(String value, int limit) {
        if (value == null || value.length() > limit) return false;
        int bytes = value.length();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c < 0x80) continue;
            if (c < 0x800) {
                bytes++;
            } else if (!Character.isSurrogate(c)) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 2;
                index++;
            }
            // A lone surrogate is replaced by one byte by the UTF-8 encoder.
            if (bytes > limit) return false;
        }
        return true;
    }
}
