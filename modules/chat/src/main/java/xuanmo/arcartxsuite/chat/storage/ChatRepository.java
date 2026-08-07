package xuanmo.arcartxsuite.chat.storage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;

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

    /**
     * 加载玩家聊天状态，不存在时返回默认状态。
     *
     * @param playerUuid       玩家 UUID
     * @param defaultChannelId 默认频道 ID
     * @return 玩家聊天状态
     * @throws SQLException SQL 异常
     */
    ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException;

    /**
     * 保存或更新玩家聊天状态。
     *
     * @param state 玩家聊天状态
     * @throws SQLException SQL 异常
     */
    void saveState(ChatPlayerState state) throws SQLException;

    /**
     * 全量替换保存玩家的屏蔽列表。
     *
     * @param playerUuid      玩家 UUID
     * @param ignoredPlayers  屏蔽的玩家 UUID 集合
     * @throws SQLException SQL 异常
     */
    void saveIgnoredPlayers(UUID playerUuid, Set<UUID> ignoredPlayers) throws SQLException;

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
    void saveMute(ChatMuteRecord muteRecord) throws SQLException;

    /**
     * 删除玩家禁言记录（解除禁言）。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException SQL 异常
     */
    void deleteMute(UUID playerUuid) throws SQLException;

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

    /**
     * 关闭仓储，释放数据库连接池等资源。
     */
    void close();
}
