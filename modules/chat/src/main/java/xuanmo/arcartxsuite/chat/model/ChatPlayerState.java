package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家聊天状态记录，封装当前频道、私聊/提及开关、监听开关与屏蔽列表。
 * <p>
 * 不可变值对象，通过 {@code with*} 方法派生新实例，{@code ignoredPlayers} 在构造时防御性拷贝并冻结。
 *
 * @param playerUuid            玩家 UUID
 * @param currentChannelId      当前所在频道 ID
 * @param acceptsPrivateMessages 是否接受私聊
 * @param acceptsMentions       是否接受 @提及
 * @param socialSpyEnabled      是否启用监听（SocialSpy）
 * @param ignoredPlayers        被屏蔽的玩家 UUID 集合（不可变）
 * @param updatedAt             状态最后更新时间
 */
public record ChatPlayerState(
    UUID playerUuid,
    String currentChannelId,
    boolean acceptsPrivateMessages,
    boolean acceptsMentions,
    boolean socialSpyEnabled,
    Set<UUID> ignoredPlayers,
    Instant updatedAt
) {

    public ChatPlayerState {
        ignoredPlayers = Collections.unmodifiableSet(new LinkedHashSet<>(ignoredPlayers == null ? Set.of() : ignoredPlayers));
    }

    /**
     * 创建玩家默认状态：接受私聊、接受提及、关闭监听、无屏蔽、默认频道。
     *
     * @param playerUuid       玩家 UUID
     * @param defaultChannelId 默认频道 ID
     * @return 默认状态实例
     */
    public static ChatPlayerState createDefault(UUID playerUuid, String defaultChannelId) {
        return new ChatPlayerState(playerUuid, defaultChannelId, true, true, false, Set.of(), Instant.now());
    }

    /**
     * 派生一个仅修改当前频道的新状态。
     *
     * @param currentChannelId 新频道 ID
     * @param updatedAt        更新时间
     * @return 新状态实例
     */
    public ChatPlayerState withCurrentChannel(String currentChannelId, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    /**
     * 派生一个仅修改私聊接受开关的新状态。
     *
     * @param value     是否接受私聊
     * @param updatedAt 更新时间
     * @return 新状态实例
     */
    public ChatPlayerState withAcceptsPrivateMessages(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, value, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    /**
     * 派生一个仅修改提及接受开关的新状态。
     *
     * @param value     是否接受提及
     * @param updatedAt 更新时间
     * @return 新状态实例
     */
    public ChatPlayerState withAcceptsMentions(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, value, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    /**
     * 派生一个仅修改监听开关的新状态。
     *
     * @param value     是否启用监听
     * @param updatedAt 更新时间
     * @return 新状态实例
     */
    public ChatPlayerState withSocialSpyEnabled(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, value, ignoredPlayers, updatedAt);
    }

    /**
     * 派生一个仅修改屏蔽列表的新状态。
     *
     * @param ignoredPlayers 新的屏蔽列表
     * @param updatedAt      更新时间
     * @return 新状态实例
     */
    public ChatPlayerState withIgnoredPlayers(Set<UUID> ignoredPlayers, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    /**
     * 判断是否屏蔽了指定玩家。
     *
     * @param targetUuid 目标玩家 UUID
     * @return {@code true} 表示已屏蔽
     */
    public boolean ignores(UUID targetUuid) {
        return targetUuid != null && ignoredPlayers.contains(targetUuid);
    }
}
