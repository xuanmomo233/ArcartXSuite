package xuanmo.arcartxsuite.tooltip.service;

import java.nio.charset.StandardCharsets;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

/**
 * TACZ 配件"支持枪械"浏览 UI 服务。
 * <p>
 * 模式由模块配置 {@code gun-list.mode} 决定。玩家客户端初始化完成时，
 * 经 plugin message 通道 {@link #MODE_CHANNEL}（与 map 模块 axsmap:* 同机制）
 * 把模式推给客户端 mod；收不到推送时客户端默认 mod 模式：
 * <ul>
 *   <li>{@code AX} — 客户端 Shift 时回发 {@code AXS_GUN_LIST}，
 *       服务端 {@code openUi} 打开 {@code gun_list.yml} AX 界面</li>
 *   <li>{@code MOD} — 客户端 Shift 时直接本地开 Forge 界面，不发包</li>
 * </ul>
 * 枪械数据不走网络包：客户端 axstooltip.* 全局函数直供。
 */
public final class GunListService {

    /** 模式推送通道（plugin message，载荷 = "ax" / "mod" 原文）。 */
    public static final String MODE_CHANNEL = "axstooltip:gunui";

    public enum UiMode { AX, MOD }

    private final Plugin plugin;
    private final PacketBridgeAPI packetBridge;
    private volatile String gunListUiId;
    private volatile UiMode mode = UiMode.AX;

    public GunListService(@NotNull Plugin plugin, @NotNull PacketBridgeAPI packetBridge) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
    }

    /** 记录 gun_list 注册后的运行时 ID（AXS:gun_list）。 */
    public void bindUi(@NotNull String gunListRuntimeUiId) {
        this.gunListUiId = gunListRuntimeUiId;
    }

    public void setMode(@Nullable UiMode mode) {
        if (mode != null) this.mode = mode;
    }

    /** 注册模式推送的 outgoing plugin message 通道。 */
    public void registerChannels() {
        plugin.getServer().getMessenger()
            .registerOutgoingPluginChannel(plugin, MODE_CHANNEL);
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger()
            .unregisterOutgoingPluginChannel(plugin, MODE_CHANNEL);
    }

    /** 客户端初始化完成：把当前模式推送给该玩家的 mod。 */
    public void pushMode(@NotNull Player player) {
        player.sendPluginMessage(plugin, MODE_CHANNEL,
            mode.name().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 向所有在线玩家推送当前模式。
     * <p>
     * 用于模块启动/reload 后补推——{@code ClientInitializedHandler} 只在进服时
     * 触发一次，单纯 reload 改配置不会自动生效。
     */
    public void pushModeToAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            pushMode(player);
        }
    }

    /** 处理客户端 AXS_GUN_LIST["open"] 请求（ax 模式下才会到达）。 */
    public void requestOpen(@NotNull Player player) {
        if (!packetBridge.isAvailable() || gunListUiId == null) return;
        if (!packetBridge.openUi(player, gunListUiId)) {
            packetBridge.openUiUnsafe(player, gunListUiId);
        }
    }
}

