package xuanmo.arcartxsuite.conversation.cinematic;

import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;

/**
 * 电影动作执行上下文，封装所有可用的 ArcartXSuite 桥接。
 */
public record CinematicActionContext(
    AdyeshachNpcBridgeAPI npcBridge,
    PacketBridgeAPI packetBridge,
    SoundPlayerBridgeAPI soundPlayer,
    SubtitlePlayable subtitlePlayable
) {
    /**
     * 执行动作的便捷方法，等价于 {@code action.execute(player, this)}。
     */
    public void execute(CinematicAction action, Player player) {
        action.execute(player, this);
    }
}
