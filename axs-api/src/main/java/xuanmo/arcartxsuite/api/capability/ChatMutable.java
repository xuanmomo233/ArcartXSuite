package xuanmo.arcartxsuite.api.capability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 聊天禁言能力接口。
 * <p>
 * 由 Chat 模块实现并注册，供 Essentials 等模块通过 capability 调用以执行禁言/解禁操作。
 * 当 Chat 模块未启用时，调用方应做降级处理。
 * <p>
 * 公开 capability：外部插件可经 {@code AxsCapabilities.get(ChatMutable.class)} 获取。
 */
@PublicCapability
public interface ChatMutable {

    /**
     * 禁言指定玩家。
     *
     * @param playerName 目标玩家名称
     * @param expiresAt  过期时间，null 表示永久禁言
     * @param reason     禁言原因，可为 null
     * @param mutedBy    执行者名称，可为 null
     * 在服务器线程提交。异步结果表示数据库操作已完成；后续 Bukkit 操作需回到服务器线程。
     * @return 操作结果消息（成功或失败原因）
     */
    @NotNull
    CompletionStage<String> mutePlayer(@NotNull String playerName, @Nullable Instant expiresAt, @Nullable String reason, @Nullable String mutedBy);

    /** Unmutes a player and attributes the operation to its operator. */
    @NotNull CompletionStage<String> unmutePlayer(@NotNull String playerName, @NotNull String operator);

    /**
     * 查询玩家是否当前处于禁言状态。
     *
     * @param playerUuid 目标玩家 UUID
     * @return 后台读取的禁言状态。读取失败时异步结果异常完成。
     */
    @NotNull CompletionStage<Boolean> isMuted(@NotNull UUID playerUuid);

    /**
     * 按玩家 UUID 分页查询生效的持久化禁言记录，含离线玩家。
     *
     * @param limit 每页条数，1–100
     * @param offset 跳过的记录数，从 0 开始
     * @return 后台读取的禁言列表。读取失败时异步结果异常完成。
     */
    @NotNull
    CompletionStage<List<MuteInfo>> listMutes(int limit, int offset);

    /**
     * 禁言信息摘要，用于跨模块传递禁言列表数据。
     *
     * @param playerUuid 被禁言玩家 UUID
     * @param playerName 被禁言玩家名称（可能为 null 如果无法解析）
     * @param mutedBy    执行禁言的操作者名称
     * @param reason     禁言原因
     * @param createdAt  禁言创建时间
     * @param expiresAt  禁言过期时间，null 表示永久禁言
     */
    record MuteInfo(
        @NotNull UUID playerUuid,
        @Nullable String playerName,
        @Nullable String mutedBy,
        @Nullable String reason,
        @Nullable Instant createdAt,
        @Nullable Instant expiresAt
    ) {
        /**
         * 判断禁言是否为永久禁言。
         *
         * @return {@code true} 表示永久禁言
         */
        public boolean isPermanent() {
            return expiresAt == null;
        }
    }
}
