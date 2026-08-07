package xuanmo.arcartxsuite.extrabackpack.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * ExtraBackpack 模块的持久化仓库接口，定义扩展背包容量与槽位数据的读写操作。
 * <p>
 * 实现类（如 {@link JdbcExtraBackpackRepository}）基于 JDBC 提供具体存储逻辑，
 * 同时实现 {@link xuanmo.arcartxsuite.api.storage.AbstractModuleRepository} 以接入连接池与迁移能力。
 */
public interface ExtraBackpackRepository {

    /**
     * 初始化仓库，包括建表与索引等操作。
     *
     * @throws SQLException 初始化失败时抛出
     */
    void initialize() throws SQLException;

    /**
     * 返回存储玩家数据的所有表名，供宿主统一清除玩家数据使用。
     *
     * @return 玩家数据表名列表
     */
    List<String> playerDataTables();

    /**
     * 返回本仓库负责的所有表名（含业务表），供跨源迁移使用。
     *
     * @return 全部表名列表
     */
    List<String> allTables();

    /**
     * 加载指定玩家在指定分类下的扩展背包容量。
     *
     * @param playerUuid 玩家 UUID
     * @param categoryId 分类标识
     * @return 容量值；不存在时返回 {@code -1}
     * @throws SQLException 查询失败时抛出
     */
    long loadExtraBackpackCapacity(UUID playerUuid, String categoryId) throws SQLException;

    /**
     * 设置或更新指定玩家在指定分类下的扩展背包容量。
     *
     * @param playerUuid 玩家 UUID
     * @param categoryId 分类标识
     * @param capacity   容量值
     * @param updatedAt  更新时间戳（毫秒）
     * @throws SQLException 写入失败时抛出
     */
    void setExtraBackpackCapacity(UUID playerUuid, String categoryId, long capacity, long updatedAt) throws SQLException;

    /**
     * 加载指定玩家的所有扩展背包槽位记录。
     *
     * @param playerUuid 玩家 UUID
     * @return 槽位记录列表，按分类与槽位号升序排列
     * @throws SQLException 查询失败时抛出
     */
    List<ExtraBackpackSlotRecord> loadExtraBackpackSlots(UUID playerUuid) throws SQLException;

    /**
     * 保存指定玩家的全部扩展背包槽位记录，以事务方式先删后插。
     *
     * @param playerUuid 玩家 UUID
     * @param items      槽位记录列表
     * @throws SQLException 写入失败时抛出
     */
    void saveExtraBackpackSlots(UUID playerUuid, List<ExtraBackpackSlotRecord> items) throws SQLException;

    /**
     * 删除指定玩家的所有扩展背包数据。
     *
     * @param playerUuid 玩家 UUID
     * @return 删除的行数
     * @throws SQLException 删除失败时抛出
     */
    int deletePlayerData(UUID playerUuid) throws SQLException;

    /**
     * 删除所有玩家的扩展背包数据。
     *
     * @return 删除的行数
     * @throws SQLException 删除失败时抛出
     */
    int deleteAllPlayerData() throws SQLException;

    /**
     * 关闭仓库，释放连接池等资源。
     */
    void close();

    /**
     * 扩展背包槽位记录 record，对应数据库中的一行槽位数据。
     *
     * @param playerUuid 玩家 UUID
     * @param categoryId 分类标识
     * @param slot       槽位序号
     * @param itemData   物品序列化数据
     * @param updatedAt  更新时间戳（毫秒）
     */
    record ExtraBackpackSlotRecord(UUID playerUuid, String categoryId, int slot, String itemData, long updatedAt) {
    }
}
