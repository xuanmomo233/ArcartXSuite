package xuanmo.arcartxsuite.chat.storage;

import java.sql.SQLException;
import xuanmo.arcartxsuite.api.capability.ChatMutable.MuteInfo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.model.ChatMessageReport;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaim;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery;
import xuanmo.arcartxsuite.chat.model.RedPacketEntryRow;

/**
 * 聊天数据仓储接口，定义玩家状态、禁言记录与玩家档案的持久化操作。
 * <p>
 * 当前实现为 {@link JdbcChatRepository}，支持 SQLite 与 MySQL 两种后端。
 */
public interface ChatRepository {

    /**
     * 初始化数据库表结构。
     *
     * @throws SQLException SQL 异常
     */
    void initialize() throws SQLException;

    /** Database identity shared by Chat nodes using the same MySQL schema. */
    Optional<String> networkId() throws SQLException;

    /** The shared MySQL relay state, or null for local SQLite repositories. */
    default ChatRelayJournal relayJournal() { return null; }

    /**
     * 加载玩家聊天状态，不存在时返回默认状态。
     *
     * @param playerUuid       玩家 UUID
     * @param defaultChannelId 默认频道 ID
     * @return 玩家聊天状态
     * @throws SQLException SQL 异常
     */
    ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException;

    record RouteState(ChatPlayerState player, boolean subscribed, boolean quiet) {}

    Map<UUID, RouteState> loadRouteStates(Set<UUID> players, String channelId, String defaultChannelId) throws SQLException;

    default boolean supportsIgnoreGuards() { return false; }

    /** Acquire off-thread. The caller holds the lease until its bounded delivery callback finishes. */
    default GuardedRoutes lockRouteStates(Set<UUID> actors) throws SQLException {
        Set<UUID> covered = Set.copyOf(actors);
        return new GuardedRoutes() {
            @Override
            public Map<UUID, RouteState> loadRouteStates(Set<UUID> players, String channelId, String defaultChannelId)
                    throws SQLException {
                if (!covered.containsAll(players)) throw new IllegalArgumentException("Chat route actor is not guarded");
                return ChatRepository.this.loadRouteStates(players, channelId, defaultChannelId);
            }

            @Override
            public void close() {}
        };
    }

    interface GuardedRoutes extends AutoCloseable {
        Map<UUID, RouteState> loadRouteStates(Set<UUID> players, String channelId, String defaultChannelId)
            throws SQLException;

        @Override
        void close() throws SQLException;
    }

    void setChannelPreference(UUID player, String channelId, boolean quietField, boolean value) throws SQLException;

    void addUnreadMention(UUID player) throws SQLException;

    int takeUnreadMentions(UUID player) throws SQLException;

    /**
     * 保存或更新玩家聊天状态。
     *
     * @param state 玩家聊天状态
     * @throws SQLException SQL 异常
     */
    enum StateField { CHANNEL, PRIVATE_MESSAGES, MENTIONS, SOCIAL_SPY }

    void saveState(ChatPlayerState state, StateField field) throws SQLException;

    /**
     * 更新一条屏蔽关系。
     *
     * @param playerUuid      玩家 UUID
     * @param ignoredUuid     屏蔽对象
     * @param ignored         是否屏蔽
     * @throws SQLException SQL 异常
     */
    boolean setIgnored(UUID playerUuid, UUID ignoredUuid, boolean ignored) throws SQLException;

    /**
     * 加载玩家禁言记录。
     *
     * @param playerUuid 玩家 UUID
     * @return 禁言记录，不存在时返回 {@link Optional#empty()}
     * @throws SQLException SQL 异常
     */
    Optional<ChatMuteRecord> loadMute(UUID playerUuid) throws SQLException;

    /**
     * 保存或更新玩家禁言记录。
     *
     * @param muteRecord 禁言记录
     * @throws SQLException SQL 异常
     */
    void saveMute(ChatMuteRecord muteRecord, ChatModerationReceipt receipt) throws SQLException;

    /**
     * 删除玩家禁言记录（解除禁言）。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException SQL 异常
     */
    void deleteMute(UUID playerUuid, ChatModerationReceipt receipt) throws SQLException;

    List<MuteInfo> loadActiveMutes(Instant now, int limit, int offset) throws SQLException;

    void deleteExpiredMutes(Instant now) throws SQLException;

    List<ChatModerationReceipt> moderationHistory(String playerName, int limit) throws SQLException;

    Optional<ChatModerationReceipt> findModerationReceipt(UUID id) throws SQLException;

    boolean saveReport(ChatMessageReport report) throws SQLException;

    List<ChatMessageReport> loadOpenReports(int limit) throws SQLException;

    Optional<ChatMessageReport> findReport(UUID id) throws SQLException;

    boolean closeReport(UUID id, String operator) throws SQLException;

    /**
     * 保存或更新玩家档案。
     *
     * @param profile 玩家档案
     * @throws SQLException SQL 异常
     */
    void upsertProfile(ChatPlayerProfile profile) throws SQLException;

    /**
     * 按 UUID 加载玩家档案。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家档案，不存在时返回 {@link Optional#empty()}
     * @throws SQLException SQL 异常
     */
    Optional<ChatPlayerProfile> loadProfile(UUID playerUuid) throws SQLException;

    /**
     * 按名称查找玩家档案（不区分大小写）。
     *
     * @param playerName 玩家名称
     * @return 玩家档案，不存在时返回 {@link Optional#empty()}
     * @throws SQLException SQL 异常
     */
    Optional<ChatPlayerProfile> findProfileByName(String playerName) throws SQLException;

    // ── 红包 ─────────────────────────────────────────────

    /**
     * 写入新红包及其奖励池条目（如有）。
     *
     * @param packet  红包记录
     * @param entries 奖励池条目（普通/拼手气红包传空列表）
     * @throws SQLException SQL 异常
     */
    void insertRedPacket(RedPacket packet, List<RedPacketEntryRow> entries) throws SQLException;

    /**
     * 按 ID 查询红包。
     *
     * @param packetId 红包 ID
     * @return 红包记录，不存在时返回 {@link Optional#empty()}
     * @throws SQLException SQL 异常
     */
    Optional<RedPacket> findRedPacket(String packetId) throws SQLException;

    /**
     * 加载所有处于 active 状态的红包（用于重启后重播）。
     *
     * @return 未结束的红包列表
     * @throws SQLException SQL 异常
     */
    List<RedPacket> loadActiveRedPackets() throws SQLException;

    /**
     * 在单个事务中尝试领取红包。
     * <p>
     * 校验红包存在、未过期、未抢完、玩家未重复领取后，原子递增已领份数并写入领取记录。
     * 奖励池红包在同一事务内扣减条目剩余量。MySQL 使用行锁保证跨服并发安全；
     * SQLite 由单连接串行化天然保证。
     *
     * @param packetId       红包 ID
     * @param playerUuid     领取者 UUID
     * @param playerName     领取者名
     * @param senderCanClaim 是否允许发送者领取自己的红包
     * @param now            当前时间
     * @return 领取结果（含抽中的奖励快照）
     * @throws SQLException SQL 异常
     */
    RedPacketClaimOutcome claimRedPacket(RedPacket authorizedPacket, UUID playerUuid, String playerName,
                                         boolean senderCanClaim) throws SQLException;

    /**
     * 将红包原子标记为指定状态（仅当当前状态为 active 时生效）。
     * <p>
     * 共享 MySQL 下多个节点同时清理过期红包时，只有成功执行状态翻转的节点
     * 才负责后续退款，避免重复退款。
     *
     * @param packetId 红包 ID
     * @param status   目标状态（exhausted / expired）
     * @return {@code true} 表示本次调用完成了状态翻转
     * @throws SQLException SQL 异常
     */
    boolean expireRedPacket(String packetId) throws SQLException;

    /**
     * 扣减红包剩余金额（退款结算用）。
     *
     * @param packetId        红包 ID
     * @param remainingAmount 新的剩余金额
     * @throws SQLException SQL 异常
     */
    boolean completeDebit(String packetId, boolean charged) throws SQLException;

    Optional<RedPacket> reconcileDebit(String packetId, boolean charged, Instant before) throws SQLException;

    List<RedPacket> loadUncertainDebits(Instant before) throws SQLException;

    List<RedPacketDelivery> loadReadyDeliveries(Set<UUID> players, int limit) throws SQLException;

    List<RedPacketDelivery> loadUncertainDeliveries(Instant before) throws SQLException;

    boolean transitionDelivery(RedPacketDelivery delivery, RedPacketDelivery.Status next, Instant now)
        throws SQLException;

    boolean reconcileDelivery(String id, Instant before, RedPacketDelivery.Status next) throws SQLException;

    /**
     * 查询红包的全部领取记录（按领取时间升序）。
     *
     * @param packetId 红包 ID
     * @return 领取记录列表
     * @throws SQLException SQL 异常
     */
    List<RedPacketClaim> listRedPacketClaims(String packetId) throws SQLException;

    /**
     * 关闭仓储，释放数据库连接池等资源。
     */
    void close();
}
