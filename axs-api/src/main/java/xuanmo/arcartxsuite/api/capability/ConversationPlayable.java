package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 对话/电影序列播放能力接口（公开 capability）。
 * <p>
 * 由 Conversation 模块实现，供其他模块以脚本方式触发 Chemdah 会话与电影序列；
 * 外部 Bukkit 插件经 {@code AxsCapabilities.get(ConversationPlayable.class)} 获取。
 * <p>
 * 会话开启需要目标 NPC 是 Adyeshach 实体且被某个 Chemdah 会话
 * （{@code npc id: 'adyeshach <npcId>'}）绑定；不满足时返回 {@code false}。
 */
@PublicCapability
public interface ConversationPlayable {

    /**
     * 为玩家强制打开绑定到指定 NPC 的 Chemdah 会话（ArcartX 对话 UI）。
     *
     * @param player 目标玩家
     * @param npcId  Adyeshach NPC id（{@code entity.getId()}；也可按 displayName/customName 匹配）
     * @return 是否成功发起（NPC 不存在/未绑定会话/玩家已在会话中返回 false）
     */
    boolean openConversation(@NotNull Player player, @NotNull String npcId);

    /**
     * 查询指定 NPC 是否绑定了至少一个 Chemdah 会话。
     *
     * @param npcId Adyeshach NPC id
     */
    boolean hasConversation(@NotNull String npcId);

    /**
     * 为玩家播放电影序列（data/conversation/cinematics/&lt;id&gt;.yml 定义）。
     *
     * @param player     目标玩家
     * @param sequenceId 序列 id
     * @return 是否成功开始播放
     */
    boolean playCinematic(@NotNull Player player, @NotNull String sequenceId);

    /**
     * 停止玩家当前播放的电影序列。
     *
     * @return 是否有正在播放的序列被停止
     */
    boolean stopCinematic(@NotNull Player player);
}
