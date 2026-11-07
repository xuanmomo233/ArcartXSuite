package xuanmo.arcartxsuite.market.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.dynamic.DynamicShopState;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;
import xuanmo.arcartxsuite.market.shop.ShopLimitRecord;

/**
 * MySQL / SQLite 双模式市场数据仓库。
 */
public class JdbcMarketRepository extends AbstractModuleRepository implements MarketRepository {

    private String tOperations;
    private String tOwners;
    private String tStockPolicies;
    private String tShopProfiles;
    private String tShopFollows;
    private String tRecyclePrefs;
    private String tRecycleRules;
    private String tMarketPlayers;
    private String tListings;
    private String tBids;
    private String tHistory;
    private String tFavorites;
    private String tShopLimits;
    private String tRecycleStats;
    private String tPending;
    private String tGlobalStock;
    private String tPlayerStock;
    private String tListingsFts;
    private String tDynamicState;
    private String tPlayerShopListings;
    private String tPlayerShopHistory;

    /** 全文索引是否可用（SQLite FTS5 或 MySQL FULLTEXT+ngram），false 时降级到 LIKE */
    private volatile boolean ftsAvailable = false;

    /** 搜索关键字最大长度，防止超长输入导致性能问题 */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 转义 LIKE 模式中的特殊字符（\、%、_），使关键字按字面量匹配。
     * 配合 SQL 中的 ESCAPE '\\' 子句使用。
     */
    private static String escapeLike(String keyword) {
        if (keyword == null) return "";
        return keyword.replace("\\", "\\\\")
                       .replace("%", "\\%")
                       .replace("_", "\\_");
    }

    /**
     * 清洗全文搜索关键字，移除 FTS 语法特殊字符（SQLite FTS5 / MySQL BOOLEAN MODE）。
     * 仅保留字母、数字、中文及空格，避免语法错误或注入。
     */
    private static String sanitizeFtsKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return "";
        // 截断超长关键字
        String truncated = keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH) : keyword;
        // 移除 FTS 特殊字符，仅保留字母数字中文和空格
        return truncated.replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").trim();
    }

    /** 截断搜索关键字到最大长度 */
    private static String truncateKeyword(String keyword) {
        if (keyword == null) return "";
        return keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH) : keyword;
    }

    /**
     * 构造市场数据仓库。
     *
     * @param dataSource 数据源（由 {@link xuanmo.arcartxsuite.api.storage.StorageManager#resolveModuleDataSource} 解析）
     * @param descriptor 存储描述符（提供方言信息和表前缀）
     * @param dataFolder 模块数据目录（迁移 SQLite 目标文件时使用）
     * @param logger     日志记录器
     * @since 1.5.0
     */
    public JdbcMarketRepository(DataSource dataSource, StorageDescriptor descriptor,
                                File dataFolder, Logger logger) {
        super("AXS-Market", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        String prefix = tablePrefix();
        tListings = prefix + "listings";
        tBids = prefix + "bids";
        tHistory = prefix + "history";
        tFavorites = prefix + "favorites";
        tShopLimits = prefix + "shop_limits";
        tRecycleStats = prefix + "recycle_stats";
        tPending = prefix + "pending_deliveries";
        tGlobalStock = prefix + "shop_global_stock";
        tPlayerStock = prefix + "shop_player_stock";
        tListingsFts = prefix + "listings_fts";
        tDynamicState = prefix + "dynamic_shop_state";
        tPlayerShopListings = prefix + "player_shop_listings";
        tPlayerShopHistory = prefix + "player_shop_history";
        // 防御性校验：表前缀只能包含字母数字下划线，防止拼接 SQL 时注入
        if (!prefix.matches("[a-zA-Z0-9_]*")) {
            throw new IllegalStateException("非法表前缀（仅允许字母数字下划线）: " + prefix);
        }
        tOperations = prefix + "operations";
        tOwners = prefix + "operation_owners";
        tStockPolicies = prefix + "stock_policies";
        tShopProfiles=prefix+"player_shop_profiles";
        tShopFollows=prefix+"player_shop_follows";
        createTables(conn);
        initializePlayerShops(conn);
        initializeOperations(conn);
        tRecyclePrefs=prefix+"recycle_preferences";tRecycleRules=prefix+"recycle_protection";tMarketPlayers=prefix+"market_players";
        initializeMarketManagement(conn);
        migrateListingMessage(conn);
        boolean originalColumn;
        try (ResultSet columns=conn.getMetaData().getColumns(conn.getCatalog(),null,tListings,"original_expires_at")) {
            originalColumn=columns.next();
        }
        if(!originalColumn) try(Statement statement=conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE "+tListings+" ADD COLUMN original_expires_at BIGINT NOT NULL DEFAULT 0");
        } catch(SQLException exception) {
            // Another server may have added the shared column while this node initialized.
            try(ResultSet columns=conn.getMetaData().getColumns(conn.getCatalog(),null,tListings,"original_expires_at")) {
                if(!columns.next())throw exception;
            }
        }
        try(Statement statement=conn.createStatement()) {
            statement.executeUpdate("UPDATE "+tListings+" SET original_expires_at=expires_at WHERE original_expires_at=0");
        }
        ensureAuctionBidColumn(conn,"previous_expires_at");
        ensureAuctionBidColumn(conn,"bid_expires_at");
        ensureTableIndex(conn,tBids,"bids_room","listing_id,timestamp,id");
        ensureTableIndex(conn,tFavorites,"favorites_room","listing_id,player");
        initFullTextIndex(conn);
        logger.info("" + (!isMysql() ? "SQLite" : "MySQL") + " 存储已初始化，表前缀: " + prefix
            + (ftsAvailable ? "，全文索引: 已启用" : "，全文索引: 不可用（降级 LIKE）"));
    }

    @Override
    protected List<String> playerDataTables() {
        // 市场模块使用多种列名(seller/buyer/player/bidder)，无法统一按单列删除，
        // 实际删除逻辑见 onPurgePlayerData。
        return List.of();
    }

    @Override
    protected int onPurgePlayerData(UUID playerUuid) throws SQLException {
        String uuid = playerUuid.toString();
        int total = 0;
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                total += execUpdate(conn, "DELETE FROM " + tOperations + " WHERE player=?", uuid);
                total += execUpdate(conn,"DELETE FROM "+tRecyclePrefs+" WHERE player=?",uuid);
                total += execUpdate(conn,"DELETE FROM "+tRecycleRules+" WHERE player=?",uuid);
                total += execUpdate(conn,"DELETE FROM "+tMarketPlayers+" WHERE player=?",uuid);
                // 拍卖列表：卖家或最高竞拍者
                total += execUpdate(conn, "DELETE FROM " + tListings + " WHERE seller=? OR highest_bidder=?", uuid, uuid);
                // 竞价记录：竞拍者
                total += execUpdate(conn, "DELETE FROM " + tBids + " WHERE bidder=?", uuid);
                // 交易历史：卖家或买家
                total += execUpdate(conn, "DELETE FROM " + tHistory + " WHERE seller=? OR buyer=?", uuid, uuid);
                // 收藏列表
                total += execUpdate(conn, "DELETE FROM " + tFavorites + " WHERE player=?", uuid);
                // 商店限购记录
                total += execUpdate(conn, "DELETE FROM " + tShopLimits + " WHERE player=?", uuid);
                // 回收统计
                total += execUpdate(conn, "DELETE FROM " + tRecycleStats + " WHERE player=?", uuid);
                // 待发放物品/货币
                total += execUpdate(conn, "DELETE FROM " + tPending + " WHERE player=?", uuid);
                // 玩家商店库存
                total += execUpdate(conn, "DELETE FROM " + tPlayerStock + " WHERE player=?", uuid);
                total += execUpdate(conn,"DELETE FROM "+tShopFollows+" WHERE viewer=? OR owner=?",uuid,uuid);
                total += execUpdate(conn,"DELETE FROM "+tShopProfiles+" WHERE owner=?",uuid);
                // 玩家商店上架记录：卖家或买家
                total += execUpdate(conn, "DELETE FROM " + tPlayerShopListings + " WHERE seller=?", uuid);
                // 玩家商店交易历史：卖家或买家
                total += execUpdate(conn, "DELETE FROM " + tPlayerShopHistory + " WHERE seller=? OR buyer=?", uuid, uuid);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
        return total;
    }

    private int execUpdate(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }

    @Override
    protected List<String> allTables() {
        String prefix = tablePrefix();
        List<String> tables = new ArrayList<>(java.util.List.of(
            prefix + "player_shop_profiles",
            prefix + "player_shop_follows",
            prefix + "recycle_preferences",
            prefix + "recycle_protection",
            prefix + "market_players",
            prefix + "operations",
            prefix + "operation_owners",
            prefix + "stock_policies",
            prefix + "listings",
            prefix + "bids",
            prefix + "history",
            prefix + "favorites",
            prefix + "shop_limits",
            prefix + "recycle_stats",
            prefix + "pending_deliveries",
            prefix + "shop_global_stock",
            prefix + "shop_player_stock",
            prefix + "dynamic_shop_state",
            prefix + "player_shop_listings",
            prefix + "player_shop_history"
        ));
        // SQLite FTS5 虚拟表（迁移时需要一并处理）
        if (!isMysql() && tListingsFts != null) {
            tables.add(tListingsFts);
        }
        return List.copyOf(tables);
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                createTablesSqlite(stmt);
            } else {
                createTablesMysql(stmt);
            }
        }
    }

    /** 为已有的拍卖表添加 message 列（旧版本建表时无此列）。 */
    private void migrateListingMessage(Connection conn) throws SQLException {
        // MySQL 的 TEXT 列不允许 DEFAULT；SQLite 的 ALTER ADD COLUMN NOT NULL 又必须带默认值——按方言分开
        String columnDef = isMysql() ? "TEXT NOT NULL" : "TEXT NOT NULL DEFAULT ''";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + tListings + " ADD COLUMN message " + columnDef);
            logger.info("[Market] 拍卖表已迁移：添加 message 列");
        } catch (SQLException e) {
            // 列已存在则忽略
        }
    }

    private void createTablesSqlite(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller TEXT NOT NULL,
                seller_name TEXT NOT NULL,
                item_data TEXT NOT NULL,
                item_display_name TEXT NOT NULL,
                pinyin TEXT NOT NULL DEFAULT '',
                initials TEXT NOT NULL DEFAULT '',
                message TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'other',
                buy_now_price REAL NOT NULL DEFAULT 0,
                starting_bid REAL NOT NULL DEFAULT 0,
                current_bid REAL NOT NULL DEFAULT 0,
                highest_bidder TEXT,
                currency TEXT NOT NULL DEFAULT 'money',
                type TEXT NOT NULL DEFAULT 'BUY_NOW',
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL)
            """.formatted(tListings));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_status ON " + tListings + "(status)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_seller ON " + tListings + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_category ON " + tListings + "(category)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_expires ON " + tListings + "(expires_at)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listing_id INTEGER NOT NULL,
                bidder TEXT NOT NULL,
                bidder_name TEXT NOT NULL,
                amount REAL NOT NULL,
                timestamp INTEGER NOT NULL)
            """.formatted(tBids));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_listing ON " + tBids + "(listing_id)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_bidder ON " + tBids + "(bidder)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listing_id INTEGER NOT NULL,
                seller TEXT NOT NULL,
                buyer TEXT,
                item_data TEXT NOT NULL,
                item_display_name TEXT NOT NULL,
                price REAL NOT NULL,
                currency TEXT NOT NULL,
                tax_amount REAL NOT NULL DEFAULT 0,
                transaction_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL)
            """.formatted(tHistory));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_seller ON " + tHistory + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_buyer ON " + tHistory + "(buyer)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player TEXT NOT NULL,
                listing_id INTEGER NOT NULL,
                PRIMARY KEY (player, listing_id))
            """.formatted(tFavorites));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player TEXT NOT NULL,
                shop_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                purchased_count INTEGER NOT NULL DEFAULT 0,
                last_purchase_time INTEGER NOT NULL,
                reset_time INTEGER NOT NULL,
                PRIMARY KEY (player, shop_id, item_id))
            """.formatted(tShopLimits));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player TEXT NOT NULL,
                currency TEXT NOT NULL,
                total_amount REAL NOT NULL DEFAULT 0,
                total_items INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player, currency))
            """.formatted(tRecycleStats));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player TEXT NOT NULL,
                type TEXT NOT NULL,
                item_data TEXT,
                currency TEXT,
                amount REAL NOT NULL DEFAULT 0,
                reason TEXT,
                created_at INTEGER NOT NULL)
            """.formatted(tPending));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON " + tPending + "(player)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                shop_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                remaining INTEGER NOT NULL,
                PRIMARY KEY (shop_id, item_id))
            """.formatted(tGlobalStock));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player TEXT NOT NULL,
                shop_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                remaining INTEGER NOT NULL,
                PRIMARY KEY (player, shop_id, item_id))
            """.formatted(tPlayerStock));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                strategy_key TEXT NOT NULL PRIMARY KEY,
                shop_id TEXT NOT NULL,
                strategy_type TEXT NOT NULL,
                state_json TEXT NOT NULL DEFAULT '',
                last_trigger_time INTEGER NOT NULL DEFAULT 0,
                next_trigger_time INTEGER NOT NULL DEFAULT 0)
            """.formatted(tDynamicState));

        // ── 玩家商店 ──────────────────────────────────────
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller TEXT NOT NULL,
                seller_name TEXT NOT NULL,
                item_data TEXT NOT NULL,
                item_display_name TEXT NOT NULL,
                pinyin TEXT NOT NULL DEFAULT '',
                initials TEXT NOT NULL DEFAULT '',
                message TEXT NOT NULL DEFAULT '',
                price REAL NOT NULL DEFAULT 0,
                currency TEXT NOT NULL DEFAULT 'money',
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL)
            """.formatted(tPlayerShopListings));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pshop_status ON " + tPlayerShopListings + "(status)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pshop_seller ON " + tPlayerShopListings + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pshop_expires ON " + tPlayerShopListings + "(expires_at)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listing_id INTEGER NOT NULL,
                seller TEXT NOT NULL,
                buyer TEXT,
                item_data TEXT NOT NULL,
                item_display_name TEXT NOT NULL,
                price REAL NOT NULL,
                currency TEXT NOT NULL,
                tax_amount REAL NOT NULL DEFAULT 0,
                transaction_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL)
            """.formatted(tPlayerShopHistory));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pshop_history_seller ON " + tPlayerShopHistory + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pshop_history_buyer ON " + tPlayerShopHistory + "(buyer)");
    }

    private void createTablesMysql(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                seller CHAR(36) NOT NULL,
                seller_name VARCHAR(64) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                item_display_name VARCHAR(256) NOT NULL,
                pinyin VARCHAR(512) NOT NULL DEFAULT '',
                initials VARCHAR(512) NOT NULL DEFAULT '',
                message VARCHAR(256) NOT NULL DEFAULT '',
                category VARCHAR(64) NOT NULL DEFAULT 'other',
                buy_now_price DOUBLE NOT NULL DEFAULT 0,
                starting_bid DOUBLE NOT NULL DEFAULT 0,
                current_bid DOUBLE NOT NULL DEFAULT 0,
                highest_bidder CHAR(36),
                currency VARCHAR(32) NOT NULL DEFAULT 'money',
                type VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW',
                status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                INDEX idx_status (status),
                INDEX idx_seller (seller),
                INDEX idx_category (category),
                INDEX idx_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tListings));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                listing_id BIGINT NOT NULL,
                bidder CHAR(36) NOT NULL,
                bidder_name VARCHAR(64) NOT NULL,
                amount DOUBLE NOT NULL,
                timestamp BIGINT NOT NULL,
                INDEX idx_listing (listing_id),
                INDEX idx_bidder (bidder)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tBids));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                listing_id BIGINT NOT NULL,
                seller CHAR(36) NOT NULL,
                buyer CHAR(36),
                item_data MEDIUMTEXT NOT NULL,
                item_display_name VARCHAR(256) NOT NULL,
                price DOUBLE NOT NULL,
                currency VARCHAR(32) NOT NULL,
                tax_amount DOUBLE NOT NULL DEFAULT 0,
                transaction_type VARCHAR(16) NOT NULL,
                timestamp BIGINT NOT NULL,
                INDEX idx_seller (seller),
                INDEX idx_buyer (buyer)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tHistory));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player CHAR(36) NOT NULL,
                listing_id BIGINT NOT NULL,
                PRIMARY KEY (player, listing_id),
                INDEX idx_player (player)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tFavorites));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player CHAR(36) NOT NULL,
                shop_id VARCHAR(64) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                purchased_count INT NOT NULL DEFAULT 0,
                last_purchase_time BIGINT NOT NULL,
                reset_time BIGINT NOT NULL,
                PRIMARY KEY (player, shop_id, item_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tShopLimits));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player CHAR(36) NOT NULL,
                currency VARCHAR(32) NOT NULL,
                total_amount DOUBLE NOT NULL DEFAULT 0,
                total_items INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player, currency)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tRecycleStats));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player CHAR(36) NOT NULL,
                type VARCHAR(16) NOT NULL,
                item_data MEDIUMTEXT,
                currency VARCHAR(32),
                amount DOUBLE NOT NULL DEFAULT 0,
                reason VARCHAR(128),
                created_at BIGINT NOT NULL,
                INDEX idx_pending_player (player)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPending));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                shop_id VARCHAR(64) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                remaining INT NOT NULL,
                PRIMARY KEY (shop_id, item_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tGlobalStock));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player CHAR(36) NOT NULL,
                shop_id VARCHAR(64) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                remaining INT NOT NULL,
                PRIMARY KEY (player, shop_id, item_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPlayerStock));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                strategy_key VARCHAR(128) NOT NULL PRIMARY KEY,
                shop_id VARCHAR(64) NOT NULL,
                strategy_type VARCHAR(32) NOT NULL,
                state_json TEXT NOT NULL,
                last_trigger_time BIGINT NOT NULL DEFAULT 0,
                next_trigger_time BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tDynamicState));

        // ── 玩家商店 ──────────────────────────────────────
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                seller CHAR(36) NOT NULL,
                seller_name VARCHAR(64) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                item_display_name VARCHAR(256) NOT NULL,
                pinyin VARCHAR(512) NOT NULL DEFAULT '',
                initials VARCHAR(512) NOT NULL DEFAULT '',
                message VARCHAR(256) NOT NULL DEFAULT '',
                price DOUBLE NOT NULL DEFAULT 0,
                currency VARCHAR(32) NOT NULL DEFAULT 'money',
                status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                INDEX idx_pshop_status (status),
                INDEX idx_pshop_seller (seller),
                INDEX idx_pshop_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPlayerShopListings));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                listing_id BIGINT NOT NULL,
                seller CHAR(36) NOT NULL,
                buyer CHAR(36),
                item_data MEDIUMTEXT NOT NULL,
                item_display_name VARCHAR(256) NOT NULL,
                price DOUBLE NOT NULL,
                currency VARCHAR(32) NOT NULL,
                tax_amount DOUBLE NOT NULL DEFAULT 0,
                transaction_type VARCHAR(16) NOT NULL,
                timestamp BIGINT NOT NULL,
                INDEX idx_pshop_history_seller (seller),
                INDEX idx_pshop_history_buyer (buyer)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPlayerShopHistory));
    }

    /**
     * 初始化全文索引：
     * <ul>
     *   <li>SQLite：检测 FTS5 支持 → 建 FTS5 虚拟表（external content 模式）+ 触发器 + 回填旧数据</li>
     *   <li>MySQL：检测 ngram 分词器 → 加 FULLTEXT INDEX</li>
     *   <li>不支持时降级到 LIKE，ftsAvailable 保持 false</li>
     * </ul>
     * 旧数据安全：原表结构不变，FTS 索引/虚拟表是增量添加，回填是一次性操作。
     */
    private void initFullTextIndex(Connection conn) throws SQLException {
        if (isMysql()) {
            initFullTextIndexMysql(conn);
        } else {
            initFullTextIndexSqlite(conn);
        }
    }

    private void initFullTextIndexSqlite(Connection conn) {
        // 1. 检测 FTS5 是否可用
        boolean fts5Supported;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT fts5(1)");
            fts5Supported = true;
        } catch (SQLException e) {
            fts5Supported = false;
            logger.warning("[Market] SQLite 不支持 FTS5，搜索将使用 LIKE 降级模式: " + e.getMessage());
        }
        if (!fts5Supported) return;

        try (Statement stmt = conn.createStatement()) {
            // 2. 检测 FTS 虚拟表是否已存在（避免重复建表和回填）
            boolean ftsTableExists;
            try (ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tListingsFts + "'")) {
                ftsTableExists = rs.next();
            }

            if (!ftsTableExists) {
                // 3. 建 FTS5 虚拟表（external content 模式，数据不重复存储）
                stmt.executeUpdate("""
                    CREATE VIRTUAL TABLE %s USING fts5(
                        item_display_name, pinyin, initials,
                        content='%s', content_rowid='id',
                        tokenize='unicode61'
                    )
                    """.formatted(tListingsFts, tListings));

                // 4. 回填旧数据（一次性，把现有 listings 的文本灌入 FTS 表）
                stmt.executeUpdate("""
                    INSERT INTO %s(rowid, item_display_name, pinyin, initials)
                    SELECT id, item_display_name, pinyin, initials FROM %s
                    """.formatted(tListingsFts, tListings));

                // 5. 建触发器自动同步（INSERT / DELETE / UPDATE）
                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS %s_ai AFTER INSERT ON %s BEGIN
                        INSERT INTO %s(rowid, item_display_name, pinyin, initials)
                        VALUES (new.id, new.item_display_name, new.pinyin, new.initials);
                    END
                    """.formatted(tListingsFts, tListings, tListingsFts));

                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS %s_ad AFTER DELETE ON %s BEGIN
                        INSERT INTO %s(%s, rowid, item_display_name, pinyin, initials)
                        VALUES ('delete', old.id, old.item_display_name, old.pinyin, old.initials);
                    END
                    """.formatted(tListingsFts, tListings, tListingsFts, tListingsFts));

                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS %s_au AFTER UPDATE ON %s BEGIN
                        INSERT INTO %s(%s, rowid, item_display_name, pinyin, initials)
                        VALUES ('delete', old.id, old.item_display_name, old.pinyin, old.initials);
                        INSERT INTO %s(rowid, item_display_name, pinyin, initials)
                        VALUES (new.id, new.item_display_name, new.pinyin, new.initials);
                    END
                    """.formatted(tListingsFts, tListings, tListingsFts, tListingsFts, tListingsFts));

                logger.info("[Market] SQLite FTS5 全文索引已创建并回填旧数据");
            }
            ftsAvailable = true;
        } catch (SQLException e) {
            logger.warning("[Market] 创建 SQLite FTS5 索引失败，降级到 LIKE: " + e.getMessage());
            ftsAvailable = false;
        }
    }

    private void initFullTextIndexMysql(Connection conn) {
        // 1. 检测是否已有 FULLTEXT INDEX（避免重复添加导致表重建）
        boolean hasFullTextIndex = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW INDEX FROM " + tListings + " WHERE Index_type = 'FULLTEXT'")) {
            hasFullTextIndex = rs.next();
        } catch (SQLException e) {
            logger.warning("[Market] 检测 MySQL FULLTEXT 索引失败: " + e.getMessage());
        }
        if (hasFullTextIndex) {
            ftsAvailable = true;
            return;
        }

        // 2. 尝试添加 FULLTEXT INDEX（使用 ngram 分词器支持中文）
        // ngram 分词器将中文按 N-gram 切分，默认 token_size=2，可搜两字及以上
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "ALTER TABLE " + tListings
                + " ADD FULLTEXT INDEX idx_fts_search(item_display_name, pinyin, initials) WITH PARSER ngram");
            ftsAvailable = true;
            logger.info("[Market] MySQL FULLTEXT 索引已创建（ngram 分词器）");
        } catch (SQLException e) {
            // ngram 分词器可能不可用（MySQL < 5.7.6 或未启用），尝试不加 parser
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                    "ALTER TABLE " + tListings
                    + " ADD FULLTEXT INDEX idx_fts_search(item_display_name, pinyin, initials)");
                ftsAvailable = true;
                logger.info("[Market] MySQL FULLTEXT 索引已创建（默认分词器，中文搜索可能不完整）");
            } catch (SQLException e2) {
                logger.warning("[Market] 创建 MySQL FULLTEXT 索引失败，降级到 LIKE: " + e2.getMessage());
                ftsAvailable = false;
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    // ─── 拍卖行 CRUD ────────────────────────────────────────

    @Override
    public boolean insertListing(AuctionListing listing) {
        String sql = "INSERT INTO " + tListings
            + " (seller, seller_name, item_data, item_display_name, pinyin, initials, message, category, buy_now_price, starting_bid, current_bid, highest_bidder, currency, type, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, listing.getSeller().toString());
            ps.setString(2, listing.getSellerName());
            ps.setString(3, listing.getItemData());
            ps.setString(4, listing.getItemDisplayName());
            ps.setString(5, listing.getPinyin());
            ps.setString(6, listing.getInitials());
            ps.setString(7, listing.getMessage());
            ps.setString(8, listing.getCategory());
            ps.setDouble(9, listing.getBuyNowPrice());
            ps.setDouble(10, listing.getStartingBid());
            ps.setDouble(11, listing.getCurrentBid());
            ps.setString(12, listing.getHighestBidder() == null ? null : listing.getHighestBidder().toString());
            ps.setString(13, listing.getCurrency());
            ps.setString(14, listing.getType().name());
            ps.setString(15, listing.getStatus().name());
            ps.setLong(16, listing.getCreatedAt());
            ps.setLong(17, listing.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) listing.setId(rs.getLong(1));
            }
            return listing.getId() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入拍卖物品失败", e);
            return false;
        }
    }

    @Override
    public void updateListing(AuctionListing listing) {
        String sql = "UPDATE " + tListings
            + " SET current_bid=?, highest_bidder=?, status=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, listing.getCurrentBid());
            ps.setString(2, listing.getHighestBidder() == null ? null : listing.getHighestBidder().toString());
            ps.setString(3, listing.getStatus().name());
            ps.setLong(4, listing.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新拍卖物品失败", e);
        }
    }

    @Override
    public boolean compareAndSetListingStatus(long listingId,
                                              AuctionListing.ListingStatus expect,
                                              AuctionListing.ListingStatus update) {
        String sql = "UPDATE " + tListings + " SET status=? WHERE id=? AND status=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, update.name());
            ps.setLong(2, listingId);
            ps.setString(3, expect.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "拍卖状态 CAS 失败", e);
            return false;
        }
    }

    @Override
    public boolean updateListingBidIfHigher(long listingId, double amount, UUID bidder) {
        String sql = "UPDATE " + tListings
            + " SET current_bid=?, highest_bidder=?"
            + " WHERE id=? AND status=?"
            + " AND (highest_bidder IS NULL OR current_bid < ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, bidder == null ? null : bidder.toString());
            ps.setLong(3, listingId);
            ps.setString(4, AuctionListing.ListingStatus.ACTIVE.name());
            ps.setDouble(5, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "bid CAS update failed", e);
            return false;
        }
    }
    @Override
    public void deleteListing(long listingId) {
        exec("DELETE FROM " + tListings + " WHERE id=?", listingId);
    }

    @Override
    public @Nullable AuctionListing getListing(long listingId) {
        String sql = "SELECT * FROM " + tListings + " WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapListing(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询拍卖物品失败", e);
        }
        return null;
    }

    @Override
    public List<AuctionListing> getActiveListings(int offset, int limit) {
        return queryListings("SELECT * FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), limit, offset);
    }

    @Override
    public List<AuctionListing> getActiveListingsByCategory(String category, int offset, int limit) {
        String sql = "SELECT * FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND category=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, category);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "按分类查询失败", e);
        }
        return results;
    }

    @Override
    public List<AuctionListing> searchListings(String keyword, int offset, int limit) {
        if (ftsAvailable) {
            return searchListingsFts(keyword, offset, limit);
        }
        // 降级：LIKE 全表扫描
        String sql = "SELECT * FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')"
            + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "搜索拍卖物品失败", e);
        }
        return results;
    }

    /**
     * 全文索引搜索：
     * <ul>
     *   <li>SQLite FTS5：用 {@code listings_fts MATCH ?} 子查询定位 rowid，再回主表取完整行</li>
     *   <li>MySQL FULLTEXT：用 {@code MATCH(...) AGAINST(? IN BOOLEAN MODE)} 直接在主表上搜索</li>
     * </ul>
     */
    private List<AuctionListing> searchListingsFts(String keyword, int offset, int limit) {
        String sql;
        if (isMysql()) {
            // MySQL：MATCH ... AGAINST IN BOOLEAN MODE
            sql = "SELECT * FROM " + tListings
                + " WHERE status='ACTIVE' AND expires_at > ?"
                + " AND MATCH(item_display_name, pinyin, initials) AGAINST(? IN BOOLEAN MODE)"
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        } else {
            // SQLite FTS5：子查询定位 rowid，再回主表
            sql = "SELECT * FROM " + tListings
                + " WHERE status='ACTIVE' AND expires_at > ?"
                + " AND id IN (SELECT rowid FROM " + tListingsFts + " WHERE " + tListingsFts + " MATCH ?)"
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        }
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, sanitizeFtsKeyword(keyword));
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "全文索引搜索失败，降级到 LIKE: " + e.getMessage(), e);
            // FTS 查询失败时降级到 LIKE（防止索引损坏导致搜索完全不可用）
            ftsAvailable = false;
            return searchListings(keyword, offset, limit);
        }
        return results;
    }

    @Override
    public List<AuctionListing> getListingsBySeller(
            UUID seller,
            int offset,
            int limit
    ) {
        String sql = "SELECT * FROM " + tListings
            + " WHERE seller=? AND status='ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryListings(sql, seller.toString(), limit, offset);
    }
    @Override
    public int countActiveListingsByCategory(String category) {
        String sql = "SELECT COUNT(*) FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND category=?";
        return countQuery(sql, System.currentTimeMillis(), category);
    }

    @Override
    public int countSearchListings(String keyword) {
        if (ftsAvailable) {
            return countSearchListingsFts(keyword);
        }
        // 降级：LIKE 全表扫描
        String sql = "SELECT COUNT(*) FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')";
        String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
        return countQuery(sql, System.currentTimeMillis(), like, like, like);
    }

    private int countSearchListingsFts(String keyword) {
        String sql;
        if (isMysql()) {
            sql = "SELECT COUNT(*) FROM " + tListings
                + " WHERE status='ACTIVE' AND expires_at > ?"
                + " AND MATCH(item_display_name, pinyin, initials) AGAINST(? IN BOOLEAN MODE)";
        } else {
            sql = "SELECT COUNT(*) FROM " + tListings
                + " WHERE status='ACTIVE' AND expires_at > ?"
                + " AND id IN (SELECT rowid FROM " + tListingsFts + " WHERE " + tListingsFts + " MATCH ?)";
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, sanitizeFtsKeyword(keyword));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "全文索引计数失败，降级到 LIKE: " + e.getMessage(), e);
            ftsAvailable = false;
            return countSearchListings(keyword);
        }
        return 0;
    }
    @Override
    public List<AuctionListing> getListingsBySeller(UUID seller) {
        String sql = "SELECT * FROM " + tListings + " WHERE seller=? AND status='ACTIVE' ORDER BY created_at DESC";
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "按卖家查询失败", e);
        }
        return results;
    }

    @Override
    public int countActiveListings() {
        return countQuery("SELECT COUNT(*) FROM " + tListings + " WHERE status='ACTIVE' AND expires_at > ?",
            System.currentTimeMillis());
    }

    @Override
    public int countListingsBySeller(UUID seller) {
        String sql = "SELECT COUNT(*) FROM " + tListings + " WHERE seller=? AND status='ACTIVE'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "统计卖家上架数失败", e);
        }
        return 0;
    }

    @Override
    public List<AuctionListing> getExpiredListings() {
        String sql = "SELECT * FROM " + tListings + " WHERE status='ACTIVE' AND expires_at <= ?";
        return queryListings(sql, System.currentTimeMillis());
    }

    // ─── 竞价 ───────────────────────────────────────────────

    @Override
    public void insertBid(AuctionBid bid) {
        String sql = "INSERT INTO " + tBids + " (listing_id, bidder, bidder_name, amount, timestamp) VALUES (?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bid.listingId());
            ps.setString(2, bid.bidder().toString());
            ps.setString(3, bid.bidderName());
            ps.setDouble(4, bid.amount());
            ps.setLong(5, bid.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入竞价失败", e);
        }
    }

    @Override
    public List<AuctionBid> getBidsForListing(long listingId) {
        List<AuctionBid> bids = new ArrayList<>();
        String sql = "SELECT * FROM " + tBids + " WHERE listing_id=? ORDER BY amount DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bids.add(mapBid(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询竞价失败", e);
        }
        return bids;
    }

    @Override
    public @Nullable AuctionBid getHighestBid(long listingId) {
        String sql = "SELECT * FROM " + tBids + " WHERE listing_id=? ORDER BY amount DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapBid(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询最高竞价失败", e);
        }
        return null;
    }

    // ─── 交易历史 ────────────────────────────────────────────

    @Override
    public void insertHistory(AuctionHistory history) {
        String sql = "INSERT INTO " + tHistory
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp, trade_id, material)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, history.listingId());
            ps.setString(2, history.seller().toString());
            ps.setString(3, history.buyer() == null ? null : history.buyer().toString());
            ps.setString(4, history.itemData());
            ps.setString(5, history.itemDisplayName());
            ps.setDouble(6, history.price());
            ps.setString(7, history.currency());
            ps.setDouble(8, history.taxAmount());
            ps.setString(9, history.transactionType());
            ps.setLong(10, history.timestamp());
            ps.setString(11,java.util.Objects.toString(xuanmo.arcartxsuite.market.MarketLedger.currentTradeId(),""));
            ps.setString(12,xuanmo.arcartxsuite.market.MarketLedger.currentItemMaterial());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入交易历史失败", e);
        }
    }

    @Override
    public List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit) {
        List<AuctionHistory> results = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT id, listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp FROM " + tHistory
            + " WHERE seller=? OR buyer=? UNION ALL SELECT id, listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, "
            + (isMysql() ? "CONCAT('PSHOP_', transaction_type)" : "'PSHOP_' || transaction_type")
            + " AS transaction_type, timestamp FROM " + tPlayerShopHistory
            + " WHERE seller=? OR buyer=?) market_history ORDER BY timestamp DESC, transaction_type, id DESC LIMIT ? OFFSET ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String uuid = player.toString();
            ps.setString(1, uuid);
            ps.setString(2, uuid);
            ps.setString(3, uuid);
            ps.setString(4, uuid);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapHistory(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询交易历史失败", e);
        }
        return results;
    }

    // ─── 收藏 ───────────────────────────────────────────────

    @Override
    public int countHistoryByPlayer(UUID player) {
        String sql = "SELECT (SELECT COUNT(*) FROM " + tHistory
            + " WHERE seller=? OR buyer=?) + (SELECT COUNT(*) FROM " + tPlayerShopHistory
            + " WHERE seller=? OR buyer=?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String uuid = player.toString();
            ps.setString(1, uuid);
            ps.setString(2, uuid);
            ps.setString(3, uuid);
            ps.setString(4, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "history count failed", e);
            return 0;
        }
    }
    @Override
    public void addFavorite(UUID player, long listingId) {
        String sql = (!isMysql())
            ? "INSERT OR IGNORE INTO " + tFavorites + " (player, listing_id) VALUES (?,?)"
            : "INSERT IGNORE INTO " + tFavorites + " (player, listing_id) VALUES (?,?)";
        durableUpdate(sql, player.toString(), listingId);
    }

    @Override
    public void removeFavorite(UUID player, long listingId) {
        durableUpdate("DELETE FROM " + tFavorites + " WHERE player=? AND listing_id=?",
            player.toString(), listingId);
    }

    @Override
    public List<Long> getFavorites(UUID player) {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT listing_id FROM " + tFavorites + " WHERE player=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询收藏失败", e);
        }
        return ids;
    }

    @Override
    public boolean isFavorite(UUID player, long listingId) {
        String sql = "SELECT 1 FROM " + tFavorites + " WHERE player=? AND listing_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setLong(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询收藏状态失败", e);
        }
        return false;
    }

    // ─── 系统商店限购 ────────────────────────────────────────

    @Override
    public @Nullable ShopLimitRecord getShopLimit(UUID player, String shopId, String itemId) {
        String sql = "SELECT * FROM " + tShopLimits + " WHERE player=? AND shop_id=? AND item_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, shopId);
            ps.setString(3, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ShopLimitRecord(
                        parseUuidSafe(rs.getString("player"), "shop_limit.player"),
                        rs.getString("shop_id"),
                        rs.getString("item_id"),
                        rs.getInt("purchased_count"),
                        rs.getLong("last_purchase_time"),
                        rs.getLong("reset_time")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询限购记录失败", e);
        }
        return null;
    }

    @Override
    public void upsertShopLimit(ShopLimitRecord record) {
        String sql = (!isMysql())
            ? "INSERT INTO " + tShopLimits
                + " (player, shop_id, item_id, purchased_count, last_purchase_time, reset_time)"
                + " VALUES (?,?,?,?,?,?)"
                + " ON CONFLICT(player, shop_id, item_id) DO UPDATE SET"
                + " purchased_count=excluded.purchased_count,"
                + " last_purchase_time=excluded.last_purchase_time, reset_time=excluded.reset_time"
            : "INSERT INTO " + tShopLimits
                + " (player, shop_id, item_id, purchased_count, last_purchase_time, reset_time)"
                + " VALUES (?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE purchased_count=VALUES(purchased_count),"
                + " last_purchase_time=VALUES(last_purchase_time), reset_time=VALUES(reset_time)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.player().toString());
            ps.setString(2, record.shopId());
            ps.setString(3, record.itemId());
            ps.setInt(4, record.purchasedCount());
            ps.setLong(5, record.lastPurchaseTime());
            ps.setLong(6, record.resetTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新限购记录失败", e);
        }
    }

    @Override
    public void resetExpiredShopLimits(String resetType) {
        exec("DELETE FROM " + tShopLimits + " WHERE reset_time > 0 AND reset_time <= ?",
            System.currentTimeMillis());
    }

    // ─── 回收统计 ───────────────────────────────────────────

    @Override
    public void addRecycleStats(UUID player, String currency, double amount, int itemCount) {
        String sql = (!isMysql())
            ? "INSERT INTO " + tRecycleStats
                + " (player, currency, total_amount, total_items) VALUES (?,?,?,?)"
                + " ON CONFLICT(player, currency) DO UPDATE SET"
                + " total_amount=total_amount+excluded.total_amount,"
                + " total_items=total_items+excluded.total_items"
            : "INSERT INTO " + tRecycleStats
                + " (player, currency, total_amount, total_items) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE total_amount=total_amount+VALUES(total_amount),"
                + " total_items=total_items+VALUES(total_items)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, currency);
            ps.setDouble(3, amount);
            ps.setInt(4, itemCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新回收统计失败", e);
        }
    }

    @Override
    public double getRecycleTotal(UUID player, String currency) {
        String sql = "SELECT total_amount FROM " + tRecycleStats + " WHERE player=? AND currency=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询回收统计失败", e);
        }
        return 0.0;
    }

    // ─── 系统商店全局库存 ─────────────────────────────────────

    @Override
    public int getGlobalShopStock(String shopId, String itemId, int defaultMax) {
        String sql = "SELECT remaining FROM " + tGlobalStock + " WHERE shop_id=? AND item_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询全局库存失败", e);
        }
        return defaultMax;
    }

    @Override
    public boolean tryConsumeGlobalShopStock(String shopId, String itemId, int amount, int defaultMax) {
        if (amount <= 0) {
            return false;
        }
        String updateSql = "UPDATE " + tGlobalStock
            + " SET remaining = remaining - ? WHERE shop_id=? AND item_id=? AND remaining >= ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setString(2, shopId);
                    ps.setString(3, itemId);
                    ps.setInt(4, amount);
                    if (ps.executeUpdate() > 0) {
                        conn.commit();
                        return true;
                    }
                }
                String insertSql = (!isMysql())
                    ? "INSERT OR IGNORE INTO " + tGlobalStock + " (shop_id, item_id, remaining) VALUES (?,?,?)"
                    : "INSERT IGNORE INTO " + tGlobalStock + " (shop_id, item_id, remaining) VALUES (?,?,?)";
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setString(1, shopId);
                    insert.setString(2, itemId);
                    insert.setInt(3, defaultMax);
                    insert.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setString(2, shopId);
                    ps.setString(3, itemId);
                    ps.setInt(4, amount);
                    boolean ok = ps.executeUpdate() > 0;
                    conn.commit();
                    return ok;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "扣减全局库存失败", e);
            return false;
        }
    }

    @Override
    public void restoreGlobalShopStock(String shopId, String itemId, int amount, int defaultMax) {
        if (amount <= 0) {
            return;
        }
        // 恢复库存但不超过初始上限，防止异常重试导致库存溢出
        String func = isMysql() ? "LEAST" : "MIN";
        exec("UPDATE " + tGlobalStock + " SET remaining = " + func + "(remaining + ?, ?) WHERE shop_id=? AND item_id=?",
            amount, defaultMax, shopId, itemId);
    }

    // ─── 系统商店玩家独立库存 ─────────────────────────────────

    @Override
    public int getPlayerShopStock(UUID player, String shopId, String itemId, int defaultMax) {
        defaultMax = playerStockDefault(shopId, itemId, defaultMax);
        String sql = "SELECT remaining FROM " + tPlayerStock + " WHERE player=? AND shop_id=? AND item_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, shopId);
            ps.setString(3, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家库存失败", e);
        }
        return defaultMax;
    }

    @Override
    public boolean tryConsumePlayerShopStock(UUID player, String shopId, String itemId, int amount, int defaultMax) {
        if (amount <= 0) {
            return false;
        }
        String playerId = player.toString();
        String updateSql = "UPDATE " + tPlayerStock
            + " SET remaining = remaining - ? WHERE player=? AND shop_id=? AND item_id=? AND remaining >= ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int initialStock = lockedPlayerStockDefault(conn,shopId,itemId,defaultMax);
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setString(2, playerId);
                    ps.setString(3, shopId);
                    ps.setString(4, itemId);
                    ps.setInt(5, amount);
                    if (ps.executeUpdate() > 0) {
                        conn.commit();
                        return true;
                    }
                }
                String insertSql = (!isMysql())
                    ? "INSERT OR IGNORE INTO " + tPlayerStock + " (player, shop_id, item_id, remaining) VALUES (?,?,?,?)"
                    : "INSERT IGNORE INTO " + tPlayerStock + " (player, shop_id, item_id, remaining) VALUES (?,?,?,?)";
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setString(1, playerId);
                    insert.setString(2, shopId);
                    insert.setString(3, itemId);
                    insert.setInt(4, initialStock);
                    insert.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setString(2, playerId);
                    ps.setString(3, shopId);
                    ps.setString(4, itemId);
                    ps.setInt(5, amount);
                    boolean ok = ps.executeUpdate() > 0;
                    conn.commit();
                    return ok;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "扣减玩家库存失败", e);
            return false;
        }
    }

    @Override
    public void restorePlayerShopStock(UUID player, String shopId, String itemId, int amount, int defaultMax) {
        if (amount <= 0) {
            return;
        }
        // 恢复库存但不超过初始上限，防止异常重试导致库存溢出
        String func = isMysql() ? "LEAST" : "MIN";
        exec("UPDATE " + tPlayerStock + " SET remaining = " + func + "(remaining + ?, ?) WHERE player=? AND shop_id=? AND item_id=?",
            amount, defaultMax, player.toString(), shopId, itemId);
    }

    // ─── 待发放队列 ─────────────────────────────────────────

    @Override
    public void addPendingItem(UUID player, String itemData, String reason) {
        insertOperation(xuanmo.arcartxsuite.market.MarketLedger.delivery(player, "ITEM", "", "0", itemData, reason));
    }

    @Override
    public void addPendingCurrency(UUID player, String currency, double amount, String reason) {
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("无效交付金额");
        insertOperation(xuanmo.arcartxsuite.market.MarketLedger.delivery(player, "CURRENCY", currency,
            java.math.BigDecimal.valueOf(amount).toPlainString(), "", reason));
    }

    @Override
    public List<PendingDelivery> getPendingDeliveries(UUID player) {
        List<PendingDelivery> results = new ArrayList<>();
        String sql = "SELECT * FROM " + tPending + " WHERE player=? ORDER BY id ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapPending(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询待发放记录失败", e);
        }
        return results;
    }

    @Override
    public void deletePendingDelivery(long id) {
        exec("DELETE FROM " + tPending + " WHERE id=?", id);
    }

    private PendingDelivery mapPending(ResultSet rs) throws SQLException {
        return new PendingDelivery(
            rs.getLong("id"),
            parseUuidSafe(rs.getString("player"), "pending.player"),
            rs.getString("type"),
            rs.getString("item_data"),
            rs.getString("currency"),
            rs.getDouble("amount"),
            rs.getString("reason"),
            rs.getLong("created_at")
        );
    }

    // ─── 工具方法 ───────────────────────────────────────────

    private List<AuctionListing> queryListings(String sql, Object... params) {
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(ps, i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询拍卖列表失败", e);
        }
        return results;
    }

    private void exec(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(ps, i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "执行SQL失败: " + sql, e);
        }
    }

    private int countQuery(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(ps, i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "统计查询失败", e);
        }
        return 0;
    }

    private void setParam(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof String s) ps.setString(index, s);
        else if (value instanceof Long l) ps.setLong(index, l);
        else if (value instanceof Integer i) ps.setInt(index, i);
        else if (value instanceof Double d) ps.setDouble(index, d);
        else ps.setObject(index, value);
    }

    /** 安全解析 UUID，脏数据时记录警告并返回 null */
    private @Nullable UUID parseUuidSafe(String value, String context) {
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            logger.warning("数据库中存在无效 UUID: " + value + " (" + context + ")");
            return null;
        }
    }

    private AuctionListing mapListing(ResultSet rs) throws SQLException {
        String bidderStr = rs.getString("highest_bidder");
        UUID bidder = parseUuidSafe(bidderStr, "listing.highest_bidder");
        AuctionListing listing = new AuctionListing(
            rs.getLong("id"),
            parseUuidSafe(rs.getString("seller"), "listing.seller"),
            rs.getString("seller_name"),
            rs.getString("item_data"),
            rs.getString("item_display_name"),
            rs.getString("pinyin"),
            rs.getString("initials"),
            rs.getString("category"),
            rs.getDouble("buy_now_price"),
            rs.getDouble("starting_bid"),
            rs.getDouble("current_bid"),
            bidder,
            rs.getString("currency"),
            AuctionListing.ListingType.valueOf(rs.getString("type")),
            AuctionListing.ListingStatus.valueOf(rs.getString("status")),
            rs.getLong("created_at"),
            rs.getLong("expires_at")
        );
        listing.setMessage(rs.getString("message"));
        listing.setOriginalExpiresAt(rs.getLong("original_expires_at"));
        return listing;
    }

    private AuctionBid mapBid(ResultSet rs) throws SQLException {
        return new AuctionBid(
            rs.getLong("id"),
            rs.getLong("listing_id"),
            parseUuidSafe(rs.getString("bidder"), "bid.bidder"),
            rs.getString("bidder_name"),
            rs.getDouble("amount"),
            rs.getLong("timestamp"),
            rs.getLong("previous_expires_at"),
            rs.getLong("bid_expires_at")
        );
    }

    private AuctionHistory mapHistory(ResultSet rs) throws SQLException {
        String buyerStr = rs.getString("buyer");
        UUID buyer = parseUuidSafe(buyerStr, "history.buyer");
        return new AuctionHistory(
            rs.getLong("id"),
            rs.getLong("listing_id"),
            parseUuidSafe(rs.getString("seller"), "history.seller"),
            buyer,
            rs.getString("item_data"),
            rs.getString("item_display_name"),
            rs.getDouble("price"),
            rs.getString("currency"),
            rs.getDouble("tax_amount"),
            rs.getString("transaction_type"),
            rs.getLong("timestamp")
        );
    }

    // ─── 动态商品策略状态 ───────────────────────────────────

    @Override
    public @Nullable DynamicShopState getDynamicShopState(String strategyKey) {
        String sql = "SELECT strategy_key, shop_id, strategy_type, state_json, last_trigger_time, next_trigger_time FROM "
            + tDynamicState + " WHERE strategy_key=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, strategyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DynamicShopState(
                        rs.getString("strategy_key"),
                        rs.getString("shop_id"),
                        rs.getString("strategy_type"),
                        rs.getString("state_json"),
                        rs.getLong("last_trigger_time"),
                        rs.getLong("next_trigger_time")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询动态策略状态失败: " + strategyKey, e);
        }
        return null;
    }

    @Override
    public void upsertDynamicShopState(DynamicShopState state) {
        if (isMysql()) {
            exec("INSERT INTO " + tDynamicState
                + " (strategy_key, shop_id, strategy_type, state_json, last_trigger_time, next_trigger_time) VALUES (?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE shop_id=VALUES(shop_id), strategy_type=VALUES(strategy_type),"
                + " state_json=VALUES(state_json), last_trigger_time=VALUES(last_trigger_time), next_trigger_time=VALUES(next_trigger_time)",
                state.strategyKey(), state.shopId(), state.strategyType(),
                state.stateJson(), state.lastTriggerTime(), state.nextTriggerTime());
        } else {
            exec("INSERT OR REPLACE INTO " + tDynamicState
                + " (strategy_key, shop_id, strategy_type, state_json, last_trigger_time, next_trigger_time) VALUES (?,?,?,?,?,?)",
                state.strategyKey(), state.shopId(), state.strategyType(),
                state.stateJson(), state.lastTriggerTime(), state.nextTriggerTime());
        }
    }

    // ─── 玩家商店 ─────────────────────────────────────────────

    @Override
    public boolean insertPlayerShopListing(PlayerShopListing listing) {
        String sql = "INSERT INTO " + tPlayerShopListings
            + " (seller, seller_name, item_data, item_display_name, pinyin, initials, message, price, currency, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, listing.getSeller().toString());
            ps.setString(2, listing.getSellerName());
            ps.setString(3, listing.getItemData());
            ps.setString(4, listing.getItemDisplayName());
            ps.setString(5, listing.getPinyin());
            ps.setString(6, listing.getInitials());
            ps.setString(7, listing.getMessage());
            ps.setDouble(8, listing.getPrice());
            ps.setString(9, listing.getCurrency());
            ps.setString(10, listing.getStatus().name());
            ps.setLong(11, listing.getCreatedAt());
            ps.setLong(12, listing.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) listing.setId(rs.getLong(1));
            }
            return listing.getId() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入玩家商店物品失败", e);
            return false;
        }
    }

    @Override
    public void updatePlayerShopListing(PlayerShopListing listing) {
        String sql = "UPDATE " + tPlayerShopListings + " SET status=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getStatus().name());
            ps.setLong(2, listing.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新玩家商店物品失败", e);
        }
    }

    @Override
    public boolean compareAndSetPlayerShopListingStatus(long listingId,
                                                          PlayerShopListing.ListingStatus expect,
                                                          PlayerShopListing.ListingStatus update) {
        String sql = "UPDATE " + tPlayerShopListings + " SET status=? WHERE id=? AND status=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, update.name());
            ps.setLong(2, listingId);
            ps.setString(3, expect.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "玩家商店状态 CAS 失败", e);
            return false;
        }
    }

    @Override
    public @Nullable PlayerShopListing getPlayerShopListing(long listingId) {
        String sql = "SELECT * FROM " + tPlayerShopListings + " WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPlayerShopListing(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家商店物品失败: " + listingId, e);
        }
        return null;
    }

    @Override
    public List<PlayerShopListing> getActivePlayerShopListings(int offset, int limit) {
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), limit, offset);
    }

    @Override
    public List<PlayerShopListing> getActivePlayerShopListingsBySeller(UUID seller, int offset, int limit) {
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), seller.toString(), limit, offset);
    }

    @Override
    public List<PlayerShopListing> searchPlayerShopListings(String keyword, int offset, int limit) {
        String sql = "SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')"
            + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<PlayerShopListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapPlayerShopListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "搜索玩家商店物品失败", e);
        }
        return results;
    }

    @Override
    public List<MarketRepository.PlayerShopSellerInfo> getPlayerShopSellers(int offset, int limit) {
        String sql = "SELECT seller, seller_name, COUNT(*) AS active_count FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " GROUP BY seller, seller_name ORDER BY active_count DESC LIMIT ? OFFSET ?";
        List<MarketRepository.PlayerShopSellerInfo> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID seller = parseUuidSafe(rs.getString("seller"), "pshop.seller");
                    if (seller != null) {
                        results.add(new MarketRepository.PlayerShopSellerInfo(
                            seller, rs.getString("seller_name"), rs.getInt("active_count")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家商店卖家列表失败", e);
        }
        return results;
    }

    @Override
    public int countActivePlayerShopListings() {
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?", System.currentTimeMillis());
    }

    @Override
    public int countActivePlayerShopListingsBySeller(UUID seller) {
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=?",
            System.currentTimeMillis(), seller.toString());
    }

    @Override
    public int countSearchPlayerShopListings(String keyword) {
        String sql = "SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "统计搜索玩家商店物品失败", e);
        }
        return 0;
    }

    @Override
    public int countPlayerShopSellers() {
        String sql = "SELECT COUNT(DISTINCT seller) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "统计玩家商店卖家数失败", e);
        }
        return 0;
    }

    @Override
    public List<PlayerShopListing> searchPlayerShopListingsBySeller(UUID seller, String keyword, int offset, int limit) {
        String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')"
            + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), seller.toString(), like, like, like, limit, offset);
    }

    @Override
    public int countSearchPlayerShopListingsBySeller(UUID seller, String keyword) {
        String like = "%" + escapeLike(truncateKeyword(keyword)) + "%";
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=?"
            + " AND (item_display_name LIKE ? ESCAPE '\\' OR pinyin LIKE ? ESCAPE '\\' OR initials LIKE ? ESCAPE '\\')",
            System.currentTimeMillis(), seller.toString(), like, like, like);
    }

    @Override
    public int countListingsByPlayerShopSeller(UUID seller) {
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings + " WHERE seller=?", seller.toString());
    }

    @Override
    public List<PlayerShopListing> getExpiredPlayerShopListings() {
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at <= ? ORDER BY expires_at ASC",
            System.currentTimeMillis());
    }

    @Override
    public void insertPlayerShopHistory(MarketRepository.PlayerShopHistory history) {
        String sql = "INSERT INTO " + tPlayerShopHistory
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp, trade_id, material)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, history.listingId());
            ps.setString(2, history.seller().toString());
            ps.setString(3, history.buyer() == null ? null : history.buyer().toString());
            ps.setString(4, history.itemData());
            ps.setString(5, history.itemDisplayName());
            ps.setDouble(6, history.price());
            ps.setString(7, history.currency());
            ps.setDouble(8, history.taxAmount());
            ps.setString(9, history.transactionType());
            ps.setLong(10, history.timestamp());
            ps.setString(11,java.util.Objects.toString(xuanmo.arcartxsuite.market.MarketLedger.currentTradeId(),""));
            ps.setString(12,xuanmo.arcartxsuite.market.MarketLedger.currentItemMaterial());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入玩家商店历史失败", e);
        }
    }

    @Override
    public List<MarketRepository.PlayerShopHistory> getPlayerShopHistoryByPlayer(UUID player, int offset, int limit) {
        String sql = "SELECT * FROM " + tPlayerShopHistory
            + " WHERE seller=? OR buyer=? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        List<MarketRepository.PlayerShopHistory> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, player.toString());
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapPlayerShopHistory(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家商店历史失败", e);
        }
        return results;
    }

    @Override
    public int countPlayerShopHistoryByPlayer(UUID player) {
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopHistory
            + " WHERE seller=? OR buyer=?", player.toString(), player.toString());
    }

    // ─── 玩家商店映射工具 ─────────────────────────────────────

    private List<PlayerShopListing> queryPlayerShopListings(String sql, Object... params) {
        List<PlayerShopListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(ps, i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapPlayerShopListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家商店列表失败", e);
        }
        return results;
    }

    private PlayerShopListing mapPlayerShopListing(ResultSet rs) throws SQLException {
        return new PlayerShopListing(
            rs.getLong("id"),
            parseUuidSafe(rs.getString("seller"), "pshop.seller"),
            rs.getString("seller_name"),
            rs.getString("item_data"),
            rs.getString("item_display_name"),
            rs.getString("pinyin"),
            rs.getString("initials"),
            rs.getString("message"),
            rs.getDouble("price"),
            rs.getString("currency"),
            PlayerShopListing.ListingStatus.valueOf(rs.getString("status")),
            rs.getLong("created_at"),
            rs.getLong("expires_at")
        );
    }

    private MarketRepository.PlayerShopHistory mapPlayerShopHistory(ResultSet rs) throws SQLException {
        String buyerStr = rs.getString("buyer");
        UUID buyer = parseUuidSafe(buyerStr, "pshop_history.buyer");
        return new MarketRepository.PlayerShopHistory(
            rs.getLong("id"),
            rs.getLong("listing_id"),
            parseUuidSafe(rs.getString("seller"), "pshop_history.seller"),
            buyer,
            rs.getString("item_data"),
            rs.getString("item_display_name"),
            rs.getDouble("price"),
            rs.getString("currency"),
            rs.getDouble("tax_amount"),
            rs.getString("transaction_type"),
            rs.getLong("timestamp")
        );
    }

    private void initializeOperations(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tOperations + " ("
                + "id VARCHAR(64) PRIMARY KEY, trade_id VARCHAR(64), player VARCHAR(36) NOT NULL, kind VARCHAR(32) NOT NULL, "
                + "reference VARCHAR(255) NOT NULL, state VARCHAR(24) NOT NULL, currency VARCHAR(64) NOT NULL, "
                + "amount VARCHAR(64) NOT NULL, item_data " + (isMysql() ? "LONGTEXT" : "TEXT") + " NOT NULL, "
                + "note TEXT NOT NULL, owner VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tOwners + " (owner VARCHAR(64) PRIMARY KEY, last_seen BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tStockPolicies + " (shop_id VARCHAR(64) NOT NULL, item_id VARCHAR(64) NOT NULL, "
                + "next_at BIGINT NOT NULL, default_stock INTEGER, PRIMARY KEY(shop_id,item_id))");
        }
        // Metadata avoids swallowing DDL errors while remaining restart-safe on both dialects.
        ensureOperationIndex(conn, "op_state_player", "state,player,created_at");
        ensureOperationIndex(conn, "op_trade", "trade_id");
        ensureOperationIndex(conn,"op_player_kind","player,kind,trade_id,state");
        ensureOperationIndex(conn,"op_reference",isMysql()?"reference(96),kind":"reference,kind");
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement select = conn.prepareStatement("SELECT * FROM " + tPending);
                 ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String id = "legacy-" + rows.getLong("id");
                    String sql = (isMysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ") + tOperations
                        + " (id,trade_id,player,kind,reference,state,currency,amount,item_data,note,owner,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement insert = conn.prepareStatement(sql)) {
                        Object[] values = {id, id, rows.getString("player"), rows.getString("type"), rows.getString("reason"), "READY",
                            rows.getString("currency"), String.valueOf(rows.getDouble("amount")), rows.getString("item_data"),
                            "旧补发队列迁入", "", rows.getLong("created_at"), System.currentTimeMillis()};
                        for (int i = 0; i < values.length; i++) insert.setObject(i + 1, values[i]);
                        insert.executeUpdate();
                    }
                }
            }
            try (Statement statement = conn.createStatement()) { statement.executeUpdate("DELETE FROM " + tPending); }
            conn.commit();
        } catch (SQLException exception) { conn.rollback(); throw exception; }
        finally { conn.setAutoCommit(auto); }
    }

    private void ensureOperationIndex(Connection conn,String suffix,String columns) throws SQLException {
        ensureTableIndex(conn,tOperations,suffix,columns);
    }

    private boolean hasTableIndex(Connection conn,String table,String name) throws SQLException {
        try(ResultSet rows=conn.getMetaData().getIndexInfo(conn.getCatalog(),null,table,false,false)) {
            while(rows.next())if(name.equalsIgnoreCase(rows.getString("INDEX_NAME")))return true;
        }
        return false;
    }

    private void ensureTableIndex(Connection conn,String table,String suffix,String columns) throws SQLException {
        String name=tablePrefix()+suffix;
        if(hasTableIndex(conn,table,name))return;
        try(Statement statement=conn.createStatement()) {statement.executeUpdate("CREATE INDEX "+name+" ON "+table+" ("+columns+")");}
        catch(SQLException exception){if(!hasTableIndex(conn,table,name))throw exception;}
    }

    private int durableUpdate(String sql, Object... values) {
        try (Connection conn = getConnection()) { return execUpdate(conn, sql, values); }
        catch (SQLException exception) { throw new IllegalStateException("Market 持久化失败", exception); }
    }

    @Override
    public void insertOperation(MarketOperation operation) {
        durableUpdate("INSERT INTO " + tOperations
            + " (id,trade_id,player,kind,reference,state,currency,amount,item_data,note,owner,created_at,updated_at,item_name,material) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            operation.id(), operation.tradeId(), operation.player().toString(), operation.kind(), operation.reference(), operation.state(),
            operation.currency(), operation.amount(), operation.itemData(), operation.note(), operation.owner(), operation.createdAt(), operation.updatedAt(),operation.itemName(),operation.material());
    }

    @Override
    public boolean transitionOperation(String id, String expected, String state, String note, String owner) {
        String sql = "UPDATE " + tOperations + " SET state=?,note=?,owner=?,updated_at=? WHERE id=? AND state=?";
        long now = System.currentTimeMillis();
        if ("RUNNING".equals(expected) || "PREPARED".equals(expected)) {
            return durableUpdate(sql + " AND owner=?", state, note, owner, now, id, expected, owner) == 1;
        }
        return durableUpdate(sql, state, note, owner, now, id, expected) == 1;
    }

    @Override
    public List<MarketOperation> operations(String state, UUID player, int offset, int limit) {
        String sql = "SELECT * FROM " + tOperations + " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (state != null) { sql += " AND state=?"; args.add(state); }
        if (player != null) { sql += " AND player=?"; args.add(player.toString()); }
        sql += "READY".equals(state) ? " ORDER BY created_at ASC,id ASC LIMIT ? OFFSET ?" : " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?";
        args.add(Math.max(1, Math.min(200, limit))); args.add(Math.max(0, offset));
        List<MarketOperation> result = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) query.setObject(i + 1, args.get(i));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new MarketOperation(rows.getString("id"), rows.getString("trade_id"), UUID.fromString(rows.getString("player")),
                    rows.getString("kind"), rows.getString("reference"), rows.getString("state"), rows.getString("currency"), rows.getString("amount"),
                    rows.getString("item_data"), rows.getString("note"), rows.getString("owner"), rows.getLong("created_at"), rows.getLong("updated_at"),rows.getString("item_name"),rows.getString("material")));
            }
        } catch (SQLException exception) { throw new IllegalStateException("无法读取交易流水", exception); }
        return result;
    }

    @Override
    public void heartbeatMarketOwner(String owner) {
        String sql = "INSERT INTO " + tOwners + " (owner,last_seen) VALUES (?,?)"
            + (isMysql() ? " ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen)" : " ON CONFLICT(owner) DO UPDATE SET last_seen=excluded.last_seen");
        durableUpdate(sql, owner, System.currentTimeMillis());
    }

    @Override
    public void recoverAbandonedOperations() {
        durableUpdate("UPDATE " + tOperations + " SET state='READY',note=?,updated_at=? WHERE state='PREPARED' AND kind IN ('ITEM','COMMAND')"
            + " AND owner NOT IN (SELECT owner FROM " + tOwners + " WHERE last_seen>=?)"
            + " AND trade_id IN (SELECT paid.trade_id FROM (SELECT DISTINCT trade_id FROM " + tOperations + " WHERE kind='DEBIT' AND state='DONE') paid)",
            "已确认扣款成功，恢复持久奖励快照",System.currentTimeMillis(),System.currentTimeMillis()-120000L);
        durableUpdate("UPDATE " + tOperations + " SET state='REVIEW',note=?,updated_at=? WHERE state='PREPARED'"
            + " AND owner NOT IN (SELECT owner FROM " + tOwners + " WHERE last_seen>=?)",
            "进程已离线，扣款或收取物品等前置结果未确认，请核对父交易",System.currentTimeMillis(),System.currentTimeMillis()-120000L);
        durableUpdate("UPDATE " + tOperations + " SET state='REVIEW',note=?,updated_at=? WHERE state='RUNNING' AND owner NOT IN "
            + "(SELECT owner FROM " + tOwners + " WHERE last_seen>=?)", "所属进程已离线，外部结果需核对", System.currentTimeMillis(), System.currentTimeMillis() - 120000L);
    }

    @Override
    public boolean resolveOperation(String id, String action, String note) {
        String state = switch (action) { case "delivered" -> "DONE"; case "cancel" -> "CANCELLED"; case "retry-confirmed" -> "READY"; default -> null; };
        if (state == null) return false;
        return durableUpdate("UPDATE " + tOperations + " SET state=?,note=?,updated_at=? WHERE id=? AND state='REVIEW'"
            + ("READY".equals(state) ? " AND kind IN ('ITEM','CURRENCY','COMMAND','NOTICE')" : ""), state, note, System.currentTimeMillis(), id) == 1;
    }

    private int playerStockDefault(String shop, String item, int maximum) {
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement("SELECT default_stock FROM " + tStockPolicies + " WHERE shop_id=? AND item_id=?")) {
            query.setString(1, shop); query.setString(2, item);
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) { int value = rows.getInt(1); if (!rows.wasNull()) return Math.max(0, Math.min(maximum, value)); }
            }
        } catch (SQLException exception) { throw new IllegalStateException("库存基线读取失败", exception); }
        return maximum;
    }

    private int lockedPlayerStockDefault(Connection conn,String shop,String item,int maximum) throws SQLException {
        execUpdate(conn,(isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tStockPolicies
            +" (shop_id,item_id,next_at,default_stock) VALUES (?,?,0,NULL)",shop,item);
        try(PreparedStatement query=conn.prepareStatement("SELECT default_stock FROM "+tStockPolicies
            +" WHERE shop_id=? AND item_id=?"+(isMysql()?" FOR UPDATE":""))) {
            query.setString(1,shop);query.setString(2,item);
            try(ResultSet rows=query.executeQuery()) {
                if(rows.next()) {int value=rows.getInt(1);if(!rows.wasNull())return Math.max(0,Math.min(maximum,value));}
            }
        }
        return maximum;
    }

    @Override
    public boolean adjustShopStock(String shop, String item, String mode, int maximum, String action, int amount, UUID player) {
        if (maximum < 1 || amount < 0 || amount > maximum || !List.of("set","add","reset").contains(action)) return false;
        if (!List.of("global","per-player").contains(mode)) return false;
        // Initialize the row outside the management transaction using the same business defaults.
        if ("global".equals(mode)) getGlobalShopStock(shop, item, maximum);
        else if (player != null) getPlayerShopStock(player, shop, item, maximum);
        int initialStock = "per-player".equals(mode) ? playerStockDefault(shop,item,maximum) : maximum;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if("per-player".equals(mode)) initialStock=lockedPlayerStockDefault(conn,shop,item,maximum);
                int value = "reset".equals(action) ? maximum : amount;
                String table = "global".equals(mode) ? tGlobalStock : tPlayerStock;
                String insertPrefix = isMysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ";
                if ("global".equals(mode)) execUpdate(conn, insertPrefix + tGlobalStock
                    + " (shop_id,item_id,remaining) VALUES (?,?,?)", shop, item, maximum);
                else if (player != null) execUpdate(conn, insertPrefix + tPlayerStock
                    + " (player,shop_id,item_id,remaining) VALUES (?,?,?,?)", player.toString(), shop, item, initialStock);
                String where = " WHERE shop_id=? AND item_id=?" + (player != null && "per-player".equals(mode) ? " AND player=?" : "");
                String update = "add".equals(action) ? "CASE WHEN remaining+? > " + maximum + " THEN " + maximum + " ELSE remaining+? END" : "?";
                List<Object> args = new ArrayList<>(); args.add(value);
                if ("add".equals(action)) args.add(value);
                args.add(shop); args.add(item); if (player != null && "per-player".equals(mode)) args.add(player.toString());
                execUpdate(conn, "UPDATE " + table + " SET remaining=" + update + where, args.toArray());
                if ("per-player".equals(mode) && player == null) {
                    String sql = (isMysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ") + tStockPolicies
                        + " (shop_id,item_id,next_at,default_stock) VALUES (?,?,0,NULL)";
                    execUpdate(conn, sql, shop, item);
                    String expression = "add".equals(action) ? "CASE WHEN COALESCE(default_stock," + maximum + ")+? > " + maximum
                        + " THEN " + maximum + " ELSE COALESCE(default_stock," + maximum + ")+? END" : "?";
                    if ("add".equals(action)) execUpdate(conn, "UPDATE " + tStockPolicies + " SET default_stock=" + expression + " WHERE shop_id=? AND item_id=?", value, value, shop, item);
                    else execUpdate(conn, "UPDATE " + tStockPolicies + " SET default_stock=? WHERE shop_id=? AND item_id=?", value, shop, item);
                }
                conn.commit(); return true;
            } catch (SQLException exception) { conn.rollback(); throw exception; }
            finally { conn.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IllegalStateException("库存管理失败", exception); }
    }

    @Override
    public String shopStockSummary(String shop, String item, String mode, int maximum) {
        if ("global".equals(mode)) return "全服剩余 " + getGlobalShopStock(shop,item,maximum) + "/" + maximum;
        if (!"per-player".equals(mode)) return "无限库存";
        int baseline = playerStockDefault(shop,item,maximum);
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement("SELECT COUNT(*),COALESCE(SUM(remaining),0) FROM " + tPlayerStock + " WHERE shop_id=? AND item_id=?")) {
            query.setString(1,shop); query.setString(2,item);
            try (ResultSet rows = query.executeQuery()) {
                rows.next(); return "每人上限 " + maximum + "；未建记录玩家 " + baseline + "；已有 " + rows.getInt(1) + " 人，总剩余 " + rows.getLong(2);
            }
        } catch (SQLException exception) { throw new IllegalStateException("库存汇总失败",exception); }
    }

    @Override
    public void resetShopLimits(String shop,String item,UUID player) {
        if (player == null) durableUpdate("DELETE FROM " + tShopLimits + " WHERE shop_id=? AND item_id=?",shop,item);
        else durableUpdate("DELETE FROM " + tShopLimits + " WHERE shop_id=? AND item_id=? AND player=?",shop,item,player.toString());
    }

    @Override
    public boolean restockShopIfDue(String shop,String item,String mode,int maximum,long intervalMillis) {
        if (intervalMillis < 1000 || maximum < 1 || !List.of("global","per-player").contains(mode)) return false;
        long now=System.currentTimeMillis();
        durableUpdate((isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tStockPolicies+" (shop_id,item_id,next_at,default_stock) VALUES (?,?,?,NULL)",shop,item,now+intervalMillis);
        durableUpdate("UPDATE "+tStockPolicies+" SET next_at=? WHERE shop_id=? AND item_id=? AND next_at=0",now+intervalMillis,shop,item);
        try(Connection conn=getConnection()) {
            conn.setAutoCommit(false);
            try {
                int claimed=execUpdate(conn,"UPDATE "+tStockPolicies+" SET next_at=?,default_stock=? WHERE shop_id=? AND item_id=? AND next_at<=?",now+intervalMillis,maximum,shop,item,now);
                if(claimed==1) {
                    if("global".equals(mode)) execUpdate(conn,"UPDATE "+tGlobalStock+" SET remaining=? WHERE shop_id=? AND item_id=?",maximum,shop,item);
                    else execUpdate(conn,"DELETE FROM "+tPlayerStock+" WHERE shop_id=? AND item_id=?",shop,item);
                }
                conn.commit(); return claimed==1;
            } catch(SQLException exception) {conn.rollback();throw exception;}
            finally {conn.setAutoCommit(true);}
        } catch(SQLException exception) {throw new IllegalStateException("周期补货失败",exception);}
    }


    @Override
    public boolean placeAuctionBid(AuctionListing expected,double amount,UUID bidder,String bidderName,long newExpiresAt) {
        try(Connection conn=getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed=execUpdate(conn,"UPDATE "+tListings+" SET current_bid=?,highest_bidder=?,"
                    +"original_expires_at=CASE WHEN original_expires_at=0 THEN expires_at ELSE original_expires_at END,expires_at=?"
                    +" WHERE id=? AND status='ACTIVE' AND expires_at>? AND expires_at=? AND current_bid=?"
                    +" AND ((highest_bidder IS NULL AND ? IS NULL) OR highest_bidder=?)",
                    amount,bidder.toString(),newExpiresAt,expected.getId(),System.currentTimeMillis(),expected.getExpiresAt(),expected.getCurrentBid(),
                    expected.getHighestBidder()==null?null:expected.getHighestBidder().toString(),expected.getHighestBidder()==null?null:expected.getHighestBidder().toString());
                if(changed!=1) {conn.rollback();return false;}
                execUpdate(conn,"INSERT INTO "+tBids+" (listing_id,bidder,bidder_name,amount,timestamp,previous_expires_at,bid_expires_at) VALUES (?,?,?,?,?,?,?)",
                    expected.getId(),bidder.toString(),bidderName,amount,System.currentTimeMillis(),expected.getExpiresAt(),newExpiresAt);
                conn.commit();return true;
            } catch(SQLException exception) {conn.rollback();throw exception;}
            finally {conn.setAutoCommit(true);}
        } catch(SQLException exception) {throw new IllegalStateException("竞价和竞价记录提交失败",exception);}
    }

    @Override
    public AuctionListing claimAuctionSettlement(long listingId) {return claimAuction(listingId,false,null,true);}

    @Override
    public AuctionListing claimAuctionCancellation(long listingId,UUID seller,boolean allowBids) {return claimAuction(listingId,true,seller,allowBids);}

    private AuctionListing claimAuction(long id,boolean cancellation,UUID seller,boolean allowBids) {
        try(Connection conn=getConnection()) {
            conn.setAutoCommit(false);
            try {
                AuctionListing listing=null;
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tListings+" WHERE id=?"+(isMysql()?" FOR UPDATE":""))) {
                    query.setLong(1,id);
                    try(ResultSet rows=query.executeQuery()) {if(rows.next())listing=mapListing(rows);}
                }
                long now=System.currentTimeMillis();
                if(listing==null || listing.getStatus()!=AuctionListing.ListingStatus.ACTIVE
                    || (!cancellation && listing.getExpiresAt()>now)
                    || (cancellation && seller!=null && (!seller.equals(listing.getSeller()) || listing.getExpiresAt()<=now))
                    || (cancellation && !allowBids && listing.getHighestBidder()!=null)) {conn.rollback();return null;}
                AuctionListing.ListingStatus state=cancellation?AuctionListing.ListingStatus.CANCELLED
                    :listing.getHighestBidder()!=null && listing.getCurrentBid()>0?AuctionListing.ListingStatus.SOLD:AuctionListing.ListingStatus.EXPIRED;
                String condition=cancellation?(seller==null?"":" AND expires_at>?"):" AND expires_at<=?";
                int changed=condition.isEmpty()?execUpdate(conn,"UPDATE "+tListings+" SET status=? WHERE id=? AND status='ACTIVE'",state.name(),id)
                    :execUpdate(conn,"UPDATE "+tListings+" SET status=? WHERE id=? AND status='ACTIVE'"+condition,state.name(),id,now);
                if(changed!=1) {conn.rollback();return null;}
                conn.commit();listing.setStatus(state);return listing;
            } catch(SQLException exception) {conn.rollback();throw exception;}
            finally {conn.setAutoCommit(true);}
        } catch(SQLException exception) {throw new IllegalStateException("拍卖状态抢占失败",exception);}
    }

    @Override
    public List<AuctionListing> favoriteListings(UUID player,String keyword,int offset,int limit) {
        String like="%"+escapeLike(truncateKeyword(keyword))+"%";
        return queryListings("SELECT l.* FROM "+tListings+" l JOIN "+tFavorites+" f ON f.listing_id=l.id WHERE f.player=?"
            +" AND (l.item_display_name LIKE ? ESCAPE '\\' OR l.pinyin LIKE ? ESCAPE '\\' OR l.initials LIKE ? ESCAPE '\\')"
            +" ORDER BY l.created_at DESC LIMIT ? OFFSET ?",player.toString(),like,like,like,limit,offset);
    }

    @Override
    public int countFavoriteListings(UUID player,String keyword) {
        String like="%"+escapeLike(truncateKeyword(keyword))+"%";
        return countQuery("SELECT COUNT(*) FROM "+tListings+" l JOIN "+tFavorites+" f ON f.listing_id=l.id WHERE f.player=?"
            +" AND (l.item_display_name LIKE ? ESCAPE '\\' OR l.pinyin LIKE ? ESCAPE '\\' OR l.initials LIKE ? ESCAPE '\\')",player.toString(),like,like,like);
    }

    @Override
    public List<UUID> auctionWatchers(long listingId) {
        List<UUID> result=new ArrayList<>();
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement("SELECT player FROM "+tFavorites+" WHERE listing_id=?")) {
            query.setLong(1,listingId);
            try(ResultSet rows=query.executeQuery()) {while(rows.next()) result.add(UUID.fromString(rows.getString(1)));}
        } catch(SQLException exception) {throw new IllegalStateException("关注者读取失败",exception);}
        return result;
    }


    private void ensureAuctionBidColumn(Connection conn,String column) throws SQLException {
        try(ResultSet rows=conn.getMetaData().getColumns(conn.getCatalog(),null,tBids,column)) {if(rows.next())return;}
        try(Statement statement=conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE "+tBids+" ADD COLUMN "+column+" BIGINT NOT NULL DEFAULT 0");
        } catch(SQLException exception) {
            try(ResultSet rows=conn.getMetaData().getColumns(conn.getCatalog(),null,tBids,column)) {if(!rows.next())throw exception;}
        }
    }

    @Override
    public xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot readAuctionRoom(long id,UUID viewer) {
        try(Connection conn=getConnection()) {
            if(isMysql())conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                AuctionListing listing=null;
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tListings+" WHERE id=?")) {
                    query.setLong(1,id);
                    try(ResultSet rows=query.executeQuery()) {if(rows.next())listing=mapListing(rows);}
                }
                if(listing==null){conn.rollback();return null;}
                List<AuctionBid> bids=new ArrayList<>();
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tBids+" WHERE listing_id=? ORDER BY timestamp DESC,id DESC LIMIT 20")) {
                    query.setLong(1,id);try(ResultSet rows=query.executeQuery()){while(rows.next())bids.add(mapBid(rows));}
                }
                int bidCount=0,bidderCount=0,watchCount=0;double myBid=0;boolean followed=false;
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*),COUNT(DISTINCT bidder),COALESCE(MAX(CASE WHEN bidder=? THEN amount ELSE 0 END),0) FROM "+tBids+" WHERE listing_id=?")) {
                    query.setString(1,viewer.toString());query.setLong(2,id);
                    try(ResultSet rows=query.executeQuery()){if(rows.next()){bidCount=rows.getInt(1);bidderCount=rows.getInt(2);myBid=rows.getDouble(3);}}
                }
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*),COALESCE(MAX(CASE WHEN player=? THEN 1 ELSE 0 END),0) FROM "+tFavorites+" WHERE listing_id=?")) {
                    query.setString(1,viewer.toString());query.setLong(2,id);
                    try(ResultSet rows=query.executeQuery()){if(rows.next()){watchCount=rows.getInt(1);followed=rows.getInt(2)>0;}}
                }
                long ended=0;String endType="";
                try(PreparedStatement query=conn.prepareStatement("SELECT timestamp,transaction_type FROM "+tHistory+" WHERE listing_id=? ORDER BY timestamp DESC,id DESC LIMIT 1")) {
                    query.setLong(1,id);try(ResultSet rows=query.executeQuery()){if(rows.next()){ended=rows.getLong(1);endType=rows.getString(2);}}
                }
                int pending=0,review=0,done=0;
                try(PreparedStatement query=conn.prepareStatement("SELECT state,COUNT(*) FROM "+tOperations+" WHERE player=? AND kind IN ('ITEM','CURRENCY')"
                    +" AND trade_id IN (SELECT id FROM "+tOperations+" WHERE kind LIKE 'AUCTION_%' AND (reference=? OR reference LIKE ?)) GROUP BY state")) {
                    query.setString(1,viewer.toString());query.setString(2,"listing="+id);query.setString(3,"listing="+id+",%");
                    try(ResultSet rows=query.executeQuery()){while(rows.next()){
                        String state=rows.getString(1);int count=rows.getInt(2);
                        if("DONE".equals(state))done+=count;else if("REVIEW".equals(state))review+=count;
                        else if(!"CANCELLED".equals(state) && !"FAILED".equals(state))pending+=count;
                    }}
                }
                conn.commit();
                return new xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot(listing,List.copyOf(bids),bidCount,bidderCount,watchCount,followed,myBid,ended,endType,pending,review,done);
            }catch(SQLException exception){conn.rollback();throw exception;}
            finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("无法读取拍品详情",exception);}
    }

    private String auctionBrowseWhere(String mode) {
        return "participating".equals(mode)?" WHERE l.id IN (SELECT listing_id FROM "+tBids+" WHERE bidder=?)"
            :" WHERE l.status='ACTIVE' AND l.expires_at>?";
    }

    @Override
    public List<AuctionListing> browseAuction(String mode,UUID viewer,String keyword,int offset,int limit) {
        String like="%"+escapeLike(truncateKeyword(keyword))+"%";
        String order="ending".equals(mode)?"l.expires_at ASC,l.id ASC":"l.created_at DESC,l.id DESC";
        return queryListings("SELECT l.* FROM "+tListings+" l"+auctionBrowseWhere(mode)
            +" AND (l.item_display_name LIKE ? ESCAPE '\\' OR l.pinyin LIKE ? ESCAPE '\\' OR l.initials LIKE ? ESCAPE '\\') ORDER BY "+order+" LIMIT ? OFFSET ?",
            "participating".equals(mode)?viewer.toString():System.currentTimeMillis(),like,like,like,limit,offset);
    }

    @Override
    public int countBrowseAuction(String mode,UUID viewer,String keyword) {
        String like="%"+escapeLike(truncateKeyword(keyword))+"%";
        return countQuery("SELECT COUNT(*) FROM "+tListings+" l"+auctionBrowseWhere(mode)
            +" AND (l.item_display_name LIKE ? ESCAPE '\\' OR l.pinyin LIKE ? ESCAPE '\\' OR l.initials LIKE ? ESCAPE '\\')",
            "participating".equals(mode)?viewer.toString():System.currentTimeMillis(),like,like,like);
    }


    private void initializePlayerShops(Connection conn) throws SQLException {
        try(Statement statement=conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS "+tShopProfiles+" (owner VARCHAR(36) PRIMARY KEY,owner_name VARCHAR(64) NOT NULL,"
                +"shop_name VARCHAR(128) NOT NULL,description TEXT NOT NULL,announcement TEXT NOT NULL,icon_data "+(isMysql()?"LONGTEXT":"TEXT")
                +" NOT NULL,version BIGINT NOT NULL,published INTEGER NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS "+tShopFollows+" (viewer VARCHAR(36) NOT NULL,owner VARCHAR(36) NOT NULL,created_at BIGINT NOT NULL,PRIMARY KEY(viewer,owner))");
        }
        ensureTableIndex(conn,tShopFollows,"store_fans","owner,viewer");
        ensureTableIndex(conn,tShopProfiles,"stores_updated","published,updated_at,owner");
        String defaultName=isMysql()?"CONCAT(MAX(seller_name),'的小店')":"MAX(seller_name) || '的小店'";
        execUpdate(conn,(isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tShopProfiles
            +" (owner,owner_name,shop_name,description,announcement,icon_data,version,published,created_at,updated_at)"
            +" SELECT seller,MAX(seller_name),"+defaultName+",'','','',1,1,MIN(created_at),MAX(created_at) FROM "+tPlayerShopListings+" GROUP BY seller");
    }

    @Override public void ensurePlayerShopProfile(UUID owner,String ownerName,boolean publish) {
        long now=System.currentTimeMillis();
        durableUpdate((isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tShopProfiles
            +" (owner,owner_name,shop_name,description,announcement,icon_data,version,published,created_at,updated_at) VALUES (?,?,?,?,?,?,1,?,?,?)",
            owner.toString(),ownerName,ownerName+"的小店","","","",publish?1:0,now,now);
        durableUpdate("UPDATE "+tShopProfiles+" SET owner_name=? WHERE owner=? AND owner_name<>?",ownerName,owner.toString(),ownerName);
        if(publish)durableUpdate("UPDATE "+tShopProfiles+" SET published=1,version=version+1,created_at=?,updated_at=? WHERE owner=? AND published=0",now,now,owner.toString());
    }

    private xuanmo.arcartxsuite.market.playershop.PlayerShopProfile mapShopProfile(ResultSet rows) throws SQLException {
        return new xuanmo.arcartxsuite.market.playershop.PlayerShopProfile(UUID.fromString(rows.getString("owner")),rows.getString("owner_name"),
            rows.getString("shop_name"),rows.getString("description"),rows.getString("announcement"),rows.getString("icon_data"),rows.getLong("version"),
            rows.getInt("published")!=0,rows.getLong("created_at"),rows.getLong("updated_at"));
    }

    @Override public xuanmo.arcartxsuite.market.playershop.PlayerShopProfile playerShopProfile(UUID owner) {
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tShopProfiles+" WHERE owner=?")) {
            query.setString(1,owner.toString());try(ResultSet rows=query.executeQuery()){return rows.next()?mapShopProfile(rows):null;}
        }catch(SQLException exception){throw new IllegalStateException("无法读取店铺资料",exception);}
    }

    @Override public boolean savePlayerShopProfile(xuanmo.arcartxsuite.market.playershop.PlayerShopProfile profile,long expectedVersion,boolean keepIcon) {
        return durableUpdate("UPDATE "+tShopProfiles+" SET owner_name=?,shop_name=?,description=?,announcement=?,icon_data=CASE WHEN ?=1 THEN icon_data ELSE ? END,"
            +"version=version+1,published=1,updated_at=? WHERE owner=? AND version=?",profile.ownerName(),profile.shopName(),profile.description(),profile.announcement(),
            keepIcon?1:0,profile.iconData(),System.currentTimeMillis(),profile.owner().toString(),expectedVersion)==1;
    }

    private String shopLike(String keyword) {return "%"+(keyword==null?"":keyword.substring(0,Math.min(100,keyword.length()))).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private String shopDirectoryWhere(boolean favorites) {
        return " WHERE p.published=1 AND (p.shop_name LIKE ? ESCAPE '!' OR p.owner_name LIKE ? ESCAPE '!' OR p.description LIKE ? ESCAPE '!')"
            +(favorites?" AND EXISTS (SELECT 1 FROM "+tShopFollows+" f WHERE f.owner=p.owner AND f.viewer=?)":"");
    }

    @Override public List<xuanmo.arcartxsuite.market.playershop.PlayerShopCard> playerShopDirectory(UUID viewer,String keyword,boolean favorites,int offset,int limit) {
        String like=shopLike(keyword);List<Object> args=new ArrayList<>();
        args.add(System.currentTimeMillis());args.add(viewer.toString());args.add(like);args.add(like);args.add(like);if(favorites)args.add(viewer.toString());
        args.add(Math.max(1,Math.min(24,limit)));args.add(Math.max(0,offset));
        String sql="SELECT p.*,COALESCE(g.active_count,0) AS active_count,COALESCE(f.followers,0) AS followers,CASE WHEN mine.viewer IS NULL THEN 0 ELSE 1 END AS followed"
            +" FROM "+tShopProfiles+" p LEFT JOIN (SELECT seller,COUNT(*) AS active_count FROM "+tPlayerShopListings+" WHERE status='ACTIVE' AND expires_at>? GROUP BY seller) g ON g.seller=p.owner"
            +" LEFT JOIN (SELECT owner,COUNT(*) AS followers FROM "+tShopFollows+" GROUP BY owner) f ON f.owner=p.owner"
            +" LEFT JOIN "+tShopFollows+" mine ON mine.owner=p.owner AND mine.viewer=?"+shopDirectoryWhere(favorites)+" ORDER BY p.updated_at DESC,p.owner LIMIT ? OFFSET ?";
        List<xuanmo.arcartxsuite.market.playershop.PlayerShopCard> result=new ArrayList<>();
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement(sql)) {
            for(int i=0;i<args.size();i++)query.setObject(i+1,args.get(i));
            try(ResultSet rows=query.executeQuery()){while(rows.next())result.add(new xuanmo.arcartxsuite.market.playershop.PlayerShopCard(mapShopProfile(rows),rows.getInt("active_count"),rows.getInt("followers"),rows.getInt("followed")!=0));}
        }catch(SQLException exception){throw new IllegalStateException("店铺目录读取失败",exception);}
        return result;
    }

    @Override public int countPlayerShopDirectory(UUID viewer,String keyword,boolean favorites) {
        String like=shopLike(keyword);List<Object> args=new ArrayList<>(List.of(like,like,like));if(favorites)args.add(viewer.toString());
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement("SELECT COUNT(*) FROM "+tShopProfiles+" p"+shopDirectoryWhere(favorites))) {
            for(int i=0;i<args.size();i++)query.setObject(i+1,args.get(i));
            try(ResultSet rows=query.executeQuery()){return rows.next()?rows.getInt(1):0;}
        }catch(SQLException exception){throw new IllegalStateException("店铺目录计数失败",exception);}
    }

    @Override public xuanmo.arcartxsuite.market.playershop.PlayerShopView playerShopView(UUID owner,UUID viewer,String keyword,int requestedPage,int pageSize) {
        int size=Math.max(1,Math.min(24,pageSize));String like=shopLike(keyword);
        try(Connection conn=getConnection()) {
            if(isMysql())conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                xuanmo.arcartxsuite.market.playershop.PlayerShopProfile profile=null;
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tShopProfiles+" WHERE owner=?")) {
                    query.setString(1,owner.toString());try(ResultSet rows=query.executeQuery()){if(rows.next())profile=mapShopProfile(rows);}
                }
                if(profile==null || (!profile.published() && !owner.equals(viewer))){conn.rollback();return null;}
                int followers=0,active=0,total=0,sales=0;boolean followed=false;
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*),COALESCE(MAX(CASE WHEN viewer=? THEN 1 ELSE 0 END),0) FROM "+tShopFollows+" WHERE owner=?")) {
                    query.setString(1,viewer.toString());query.setString(2,owner.toString());try(ResultSet rows=query.executeQuery()){if(rows.next()){followers=rows.getInt(1);followed=rows.getInt(2)>0;}}
                }
                String where=" WHERE seller=? AND status='ACTIVE' AND expires_at>?";
                long now=System.currentTimeMillis();
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*) FROM "+tPlayerShopListings+where)) {
                    query.setString(1,owner.toString());query.setLong(2,now);try(ResultSet rows=query.executeQuery()){if(rows.next())active=rows.getInt(1);}
                }
                String search=" AND (item_display_name LIKE ? ESCAPE '!' OR pinyin LIKE ? ESCAPE '!' OR initials LIKE ? ESCAPE '!')";
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*) FROM "+tPlayerShopListings+where+search)) {
                    query.setString(1,owner.toString());query.setLong(2,now);for(int i=3;i<=5;i++)query.setString(i,like);
                    try(ResultSet rows=query.executeQuery()){if(rows.next())total=rows.getInt(1);}
                }
                int pages=Math.max(1,(total+size-1)/size),page=Math.max(0,Math.min(requestedPage,pages-1));
                List<PlayerShopListing> listings=new ArrayList<>();
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tPlayerShopListings+where+search+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?")) {
                    query.setString(1,owner.toString());query.setLong(2,now);for(int i=3;i<=5;i++)query.setString(i,like);query.setInt(6,size);query.setInt(7,page*size);
                    try(ResultSet rows=query.executeQuery()){while(rows.next())listings.add(mapPlayerShopListing(rows));}
                }
                try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*) FROM "+tPlayerShopHistory+" WHERE seller=? AND transaction_type='PURCHASE'")) {
                    query.setString(1,owner.toString());try(ResultSet rows=query.executeQuery()){if(rows.next())sales=rows.getInt(1);}
                }
                conn.commit();return new xuanmo.arcartxsuite.market.playershop.PlayerShopView(
                    new xuanmo.arcartxsuite.market.playershop.PlayerShopCard(profile,active,followers,followed),List.copyOf(listings),page,pages,total,sales,keyword==null?"":keyword);
            }catch(SQLException exception){conn.rollback();throw exception;}finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("店铺页面读取失败",exception);}
    }

    @Override public int setPlayerShopFollow(UUID viewer,UUID owner,boolean follow,int maximum) {
        if(viewer.equals(owner))return -1;
        try(Connection conn=getConnection()) {
            conn.setAutoCommit(false);
            try {
                execUpdate(conn,"UPDATE "+tShopProfiles+" SET owner_name=owner_name WHERE owner=?",viewer.toString());
                try(PreparedStatement lock=conn.prepareStatement("SELECT owner FROM "+tShopProfiles+" WHERE owner=?"+(isMysql()?" FOR UPDATE":""))) {
                    lock.setString(1,viewer.toString());try(ResultSet rows=lock.executeQuery()){if(!rows.next()){conn.rollback();return -1;}}
                }
                if(!follow){
                    execUpdate(conn,"DELETE FROM "+tShopFollows+" WHERE viewer=? AND owner=?",viewer.toString(),owner.toString());
                    conn.commit();return 0;
                }
                try(PreparedStatement exists=conn.prepareStatement("SELECT owner FROM "+tShopProfiles+" WHERE owner=? AND published=1"+(isMysql()?" FOR UPDATE":""))) {
                    exists.setString(1,owner.toString());try(ResultSet rows=exists.executeQuery()){if(!rows.next()){conn.rollback();return -1;}}
                }
                try(PreparedStatement exists=conn.prepareStatement("SELECT owner FROM "+tShopFollows+" WHERE viewer=? AND owner=?")) {
                    exists.setString(1,viewer.toString());exists.setString(2,owner.toString());
                    try(ResultSet rows=exists.executeQuery()){if(rows.next()){conn.commit();return 1;}}
                }
                try(PreparedStatement count=conn.prepareStatement("SELECT COUNT(*) FROM "+tShopFollows+" WHERE viewer=?")) {
                    count.setString(1,viewer.toString());try(ResultSet rows=count.executeQuery()){if(rows.next() && rows.getInt(1)>=maximum){conn.rollback();return -2;}}
                }
                execUpdate(conn,"INSERT INTO "+tShopFollows+" (viewer,owner,created_at) VALUES (?,?,?)",viewer.toString(),owner.toString(),System.currentTimeMillis());
                conn.commit();return 1;
            }catch(SQLException exception){conn.rollback();throw exception;}finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("店铺收藏更新失败",exception);}
    }

    @Override public boolean claimPlayerShopPurchase(long listingId) {
        return durableUpdate("UPDATE "+tPlayerShopListings+" SET status='SOLD' WHERE id=? AND status='ACTIVE' AND expires_at>?",
            listingId,System.currentTimeMillis())==1;
    }


    private void ensureManagementColumn(Connection conn,String table,String column,String definition) throws SQLException {
        try(ResultSet rows=conn.getMetaData().getColumns(conn.getCatalog(),null,table,column)){if(rows.next())return;}
        try(Statement statement=conn.createStatement()){statement.executeUpdate("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
        catch(SQLException exception){try(ResultSet rows=conn.getMetaData().getColumns(conn.getCatalog(),null,table,column)){if(!rows.next())throw exception;}}
    }
    private void initializeMarketManagement(Connection conn) throws SQLException {
        try(Statement statement=conn.createStatement()){
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS "+tRecyclePrefs+" (player VARCHAR(36) PRIMARY KEY,auto_enabled INTEGER NOT NULL,version BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS "+tRecycleRules+" (id VARCHAR(36) PRIMARY KEY,player VARCHAR(36) NOT NULL,kind VARCHAR(16) NOT NULL,match_key VARCHAR(64) NOT NULL,"
                +"material VARCHAR(64) NOT NULL,display_name VARCHAR(255) NOT NULL,item_data "+(isMysql()?"LONGTEXT":"TEXT")+" NOT NULL,created_at BIGINT NOT NULL,UNIQUE(player,kind,match_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS "+tMarketPlayers+" (player VARCHAR(36) PRIMARY KEY,name VARCHAR(64) NOT NULL,updated_at BIGINT NOT NULL)");
        }
        ensureManagementColumn(conn,tOperations,"item_name","VARCHAR(255) NOT NULL DEFAULT ''");
        ensureManagementColumn(conn,tOperations,"material","VARCHAR(64) NOT NULL DEFAULT ''");
        ensureManagementColumn(conn,tHistory,"trade_id","VARCHAR(64) NOT NULL DEFAULT ''");
        ensureManagementColumn(conn,tPlayerShopHistory,"trade_id","VARCHAR(64) NOT NULL DEFAULT ''");
        ensureManagementColumn(conn,tHistory,"material","VARCHAR(64) NOT NULL DEFAULT ''");
        ensureManagementColumn(conn,tPlayerShopHistory,"material","VARCHAR(64) NOT NULL DEFAULT ''");
        ensureTableIndex(conn,tRecycleRules,"recycle_rules_player","player,created_at");
        ensureTableIndex(conn,tOperations,"op_audit_time","created_at,kind,state");
        ensureTableIndex(conn,tHistory,"history_trade","trade_id");ensureTableIndex(conn,tPlayerShopHistory,"pshop_history_trade","trade_id");
        ensureTableIndex(conn,tMarketPlayers,"market_player_names","name");
    }

    @Override public xuanmo.arcartxsuite.market.recycle.RecyclePreferences recyclePreferences(UUID player,boolean defaultAuto){
        durableUpdate((isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tRecyclePrefs+" (player,auto_enabled,version) VALUES (?,?,1)",player.toString(),defaultAuto?1:0);
        try(Connection conn=getConnection()){
            if(isMysql())conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);conn.setAutoCommit(false);
            try{
                long version;boolean enabled;
                try(PreparedStatement query=conn.prepareStatement("SELECT auto_enabled,version FROM "+tRecyclePrefs+" WHERE player=?")){
                    query.setString(1,player.toString());try(ResultSet rows=query.executeQuery()){if(!rows.next())throw new SQLException("保护设置不存在");enabled=rows.getInt(1)!=0;version=rows.getLong(2);}
                }
                List<xuanmo.arcartxsuite.market.recycle.RecycleProtectionRule> rules=new ArrayList<>();
                try(PreparedStatement query=conn.prepareStatement("SELECT * FROM "+tRecycleRules+" WHERE player=? ORDER BY created_at,id")){
                    query.setString(1,player.toString());try(ResultSet rows=query.executeQuery()){while(rows.next())rules.add(new xuanmo.arcartxsuite.market.recycle.RecycleProtectionRule(
                        rows.getString("id"),player,rows.getString("kind"),rows.getString("match_key"),rows.getString("material"),rows.getString("display_name"),rows.getString("item_data"),rows.getLong("created_at")));}
                }
                conn.commit();return new xuanmo.arcartxsuite.market.recycle.RecyclePreferences(player,enabled,version,List.copyOf(rules));
            }catch(SQLException exception){conn.rollback();throw exception;}finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("回收保护资料读取失败",exception);}
    }
    @Override public long recyclePreferencesVersion(UUID player){
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement("SELECT version FROM "+tRecyclePrefs+" WHERE player=?")){
            query.setString(1,player.toString());try(ResultSet rows=query.executeQuery()){return rows.next()?rows.getLong(1):-1;}
        }catch(SQLException exception){throw new IllegalStateException("回收保护版本读取失败",exception);}
    }
    @Override public boolean updateRecyclePreferences(UUID player,long version,String action,boolean enabled,xuanmo.arcartxsuite.market.recycle.RecycleProtectionRule rule,int maximum){
        try(Connection conn=getConnection()){
            conn.setAutoCommit(false);
            try{
                if(execUpdate(conn,"UPDATE "+tRecyclePrefs+" SET version=version+1 WHERE player=? AND version=?",player.toString(),version)!=1){conn.rollback();return false;}
                if("auto".equals(action))execUpdate(conn,"UPDATE "+tRecyclePrefs+" SET auto_enabled=? WHERE player=?",enabled?1:0,player.toString());
                else if("remove".equals(action)){
                    if(rule==null || execUpdate(conn,"DELETE FROM "+tRecycleRules+" WHERE player=? AND id=?",player.toString(),rule.id())!=1){conn.rollback();return false;}
                }else if("add".equals(action) && rule!=null && player.equals(rule.player())){
                    boolean exists;
                    try(PreparedStatement query=conn.prepareStatement("SELECT id FROM "+tRecycleRules+" WHERE player=? AND kind=? AND match_key=?")){
                        query.setString(1,player.toString());query.setString(2,rule.kind());query.setString(3,rule.matchKey());try(ResultSet rows=query.executeQuery()){exists=rows.next();}
                    }
                    if(!exists){
                        try(PreparedStatement query=conn.prepareStatement("SELECT COUNT(*) FROM "+tRecycleRules+" WHERE player=?")){
                            query.setString(1,player.toString());try(ResultSet rows=query.executeQuery()){if(rows.next() && rows.getInt(1)>=maximum)throw new IllegalArgumentException("回收保护清单已达上限");}
                        }
                        execUpdate(conn,"INSERT INTO "+tRecycleRules+" (id,player,kind,match_key,material,display_name,item_data,created_at) VALUES (?,?,?,?,?,?,?,?)",
                            rule.id(),player.toString(),rule.kind(),rule.matchKey(),rule.material(),rule.displayName(),rule.itemData(),rule.createdAt());
                    }
                }else throw new IllegalArgumentException("未知回收保护操作");
                conn.commit();return true;
            }catch(SQLException|RuntimeException exception){conn.rollback();throw exception;}finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("回收保护资料保存失败",exception);}
    }

    @Override public void noteMarketPlayer(UUID player,String name){
        durableUpdate("INSERT INTO "+tMarketPlayers+" (player,name,updated_at) VALUES (?,?,?)"+(isMysql()?" ON DUPLICATE KEY UPDATE name=VALUES(name),updated_at=VALUES(updated_at)":" ON CONFLICT(player) DO UPDATE SET name=excluded.name,updated_at=excluded.updated_at"),player.toString(),name,System.currentTimeMillis());
    }
    @Override public UUID resolveMarketPlayer(String name){
        try{return UUID.fromString(name);}catch(IllegalArgumentException ignored){}
        String sql="SELECT player FROM "+tMarketPlayers+" WHERE LOWER(name)=LOWER(?) UNION SELECT seller FROM "+tListings+" WHERE LOWER(seller_name)=LOWER(?)"
            +" UNION SELECT seller FROM "+tPlayerShopListings+" WHERE LOWER(seller_name)=LOWER(?) UNION SELECT bidder FROM "+tBids+" WHERE LOWER(bidder_name)=LOWER(?)"
            +" UNION SELECT owner FROM "+tShopProfiles+" WHERE LOWER(owner_name)=LOWER(?) LIMIT 2";
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement(sql)){
            for(int index=1;index<=5;index++)query.setString(index,name);
            try(ResultSet rows=query.executeQuery()){if(!rows.next())throw new IllegalArgumentException("没有找到已记录的玩家名，请使用UUID");UUID id=UUID.fromString(rows.getString(1));if(rows.next())throw new IllegalArgumentException("玩家名对应多个历史身份，请使用UUID");return id;}
        }catch(SQLException exception){throw new IllegalStateException("玩家身份查询失败",exception);}
    }
    @Override public void describeOperation(String id,String name,String material,String data){
        if(id==null)return;
        durableUpdate("UPDATE "+tOperations+" SET item_name=CASE WHEN kind='RECYCLE_BATCH' THEN '批量回收' ELSE ? END,"
                +"material=CASE WHEN kind='RECYCLE_BATCH' THEN '' ELSE ? END,"
                +"item_data=CASE WHEN kind='RECYCLE_BATCH' THEN '' WHEN item_data='' THEN ? ELSE item_data END WHERE id=?",
            name.length()>255?name.substring(0,255):name,material,data==null?"":data,id);
    }


    private String auditConcat(String... parts){return isMysql()?"CONCAT("+String.join(",",parts)+")":"("+String.join(" || ",parts)+")";}
    private String auditListingId(String reference){
        String tail=isMysql()?"SUBSTRING("+reference+",9)":"substr("+reference+",9)";
        String id=isMysql()?"SUBSTRING_INDEX("+tail+",',',1)":"CASE WHEN instr("+tail+",',')>0 THEN substr("+tail+",1,instr("+tail+",',')-1) ELSE "+tail+" END";
        return "CASE WHEN "+reference+" LIKE 'listing=%' THEN CAST("+id+" AS "+(isMysql()?"UNSIGNED":"INTEGER")+") ELSE 0 END";
    }
    private String auditOperationsSql(){
        String kind="COALESCE(p.kind,o.kind)",reference="COALESCE(p.reference,o.reference)",id=auditListingId(reference);
        String item="CASE WHEN o.kind='RECYCLE_BATCH' THEN '批量回收（见关联子流水）' ELSE COALESCE(NULLIF(o.item_name,''),NULLIF(p.item_name,''),a.item_display_name,l.item_display_name,'') END";
        String data="CASE WHEN o.kind IN ('NOTICE','COMMAND','RECYCLE_BATCH') THEN '' ELSE COALESCE(NULLIF(o.item_data,''),CASE WHEN p.kind<>'RECYCLE_BATCH' THEN NULLIF(p.item_data,'') ELSE NULL END,a.item_data,l.item_data,'') END";
        String other="CASE WHEN p.player<>o.player THEN p.player WHEN a.seller<>o.player THEN a.seller WHEN a.highest_bidder<>o.player THEN a.highest_bidder WHEN l.seller<>o.player THEN l.seller ELSE NULL END";
        String note=auditConcat("o.note","' | 引用: '","o.reference","CASE WHEN o.kind='COMMAND' THEN "+auditConcat("' | 指令: '","o.item_data")+" ELSE '' END");
        return "SELECT "+auditConcat("'op:'","o.id")+" AS id,COALESCE(o.trade_id,'') AS trade_id,'OPERATION' AS source,o.kind AS kind,"+kind+" AS business_type,o.state AS state,"
            +"o.player AS player,"+other+" AS other_player,COALESCE(n.name,'') AS player_name,COALESCE(peer.name,CASE WHEN ("+other+")=a.seller THEN a.seller_name WHEN ("+other+")=l.seller THEN l.seller_name ELSE '' END,'') AS other_name,"
            +item+" AS item_name,CASE WHEN o.kind='RECYCLE_BATCH' THEN '' ELSE COALESCE(NULLIF(o.material,''),CASE WHEN p.kind<>'RECYCLE_BATCH' THEN p.material ELSE '' END,'') END AS material,o.currency AS currency,o.amount AS amount,"+note+" AS note,"+data+" AS item_data,o.created_at AS created_at,o.updated_at AS updated_at"
            +" FROM "+tOperations+" o LEFT JOIN "+tOperations+" p ON p.id=o.trade_id LEFT JOIN "+tMarketPlayers+" n ON n.player=o.player"
            +" LEFT JOIN "+tListings+" a ON "+kind+" LIKE 'AUCTION_%' AND a.id="+id+" LEFT JOIN "+tPlayerShopListings+" l ON "+kind+" LIKE 'PSHOP_%' AND l.id="+id
            +" LEFT JOIN "+tMarketPlayers+" peer ON peer.player=("+other+")";
    }
    private String auditHistorySql(String table,String prefix,boolean playerShop){
        String id="CAST(h.id AS "+(isMysql()?"CHAR":"TEXT")+")";
        String kind=playerShop?auditConcat("'PSHOP_'","h.transaction_type"):"h.transaction_type";
        String business=playerShop?"'PSHOP'":"CASE WHEN h.transaction_type='SHOP_BUY' THEN 'SHOP' WHEN h.transaction_type='RECYCLE' THEN 'RECYCLE' ELSE 'AUCTION' END";
        String state="CASE WHEN h.transaction_type IN ('CANCELLED','ADMIN_CANCELLED') THEN 'CANCELLED' WHEN h.transaction_type='EXPIRED' THEN 'EXPIRED' ELSE 'DONE' END";
        String amount="CAST(h.price AS "+(isMysql()?"CHAR":"TEXT")+")";
        return "SELECT "+auditConcat("'"+prefix+"'",id)+" AS id,COALESCE(h.trade_id,'') AS trade_id,'HISTORY' AS source,"+kind+" AS kind,"+business+" AS business_type,"+state+" AS state,"
            +"h.seller AS player,h.buyer AS other_player,COALESCE(seller.name,'') AS player_name,COALESCE(buyer.name,'') AS other_name,"
            +"h.item_display_name AS item_name,COALESCE(NULLIF(h.material,''),CASE WHEN root.kind<>'RECYCLE_BATCH' AND root.item_data=h.item_data THEN root.material ELSE '' END,'') AS material,h.currency AS currency,"+amount+" AS amount,"
            +"'业务历史记录；发放结果请查看关联处理流水' AS note,h.item_data AS item_data,h.timestamp AS created_at,h.timestamp AS updated_at FROM "+table+" h"
            +" LEFT JOIN "+tMarketPlayers+" seller ON seller.player=h.seller LEFT JOIN "+tMarketPlayers+" buyer ON buyer.player=h.buyer LEFT JOIN "+tOperations+" root ON root.id=h.trade_id";
    }
    private String auditSource(String scope){
        String history=auditHistorySql(tHistory,"history:a:",false)+" UNION ALL "+auditHistorySql(tPlayerShopHistory,"history:p:",true);
        if("history".equals(scope))return history;if("operations".equals(scope))return auditOperationsSql();return auditOperationsSql()+" UNION ALL "+history;
    }
    private List<String> auditItemTerms(String item){
        List<String> terms=new ArrayList<>();terms.add(item);
        String normalized=item.trim().replace(' ','_').toUpperCase(java.util.Locale.ROOT);
        if(normalized.startsWith("MINECRAFT:"))normalized=normalized.substring("MINECRAFT:".length());
        for(org.bukkit.Material material:org.bukkit.Material.values()){
            if(material.name().startsWith("LEGACY_"))continue;
            String zh=xuanmo.arcartxsuite.api.item.VanillaItemNameTable.translate(material);
            if(material.name().equals(normalized) || zh.equals(item)){
                for(String value:List.of(zh,material.name(),material.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')))if(!terms.contains(value))terms.add(value);
            }
            if(terms.size()>=24)break;
        }
        return terms;
    }
    private String auditWhere(MarketAuditFilter filter,List<Object> args){
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        if(filter.group()!=null && !filter.group().isBlank()){
            args.add(filter.group());args.add(filter.group());return where+" AND (id=? OR trade_id=?)";
        }
        if(filter.player()!=null){where.append(" AND (player=? OR other_player=?)");args.add(filter.player().toString());args.add(filter.player().toString());}
        where.append(" AND created_at>=? AND created_at<?");args.add(filter.from());args.add(filter.until());
        if(!filter.item().isBlank()){
            where.append(" AND (");boolean first=true;
            for(String term:auditItemTerms(filter.item())){
                if(!first)where.append(" OR ");first=false;where.append("item_name LIKE ? ESCAPE '!' OR material LIKE ? ESCAPE '!'");String like=shopLike(term);args.add(like);args.add(like);
            }
            where.append(')');
        }
        String category="CASE WHEN business_type LIKE 'AUCTION%' OR business_type IN ('BID_WIN','BUY_NOW') THEN 'AUCTION' WHEN business_type LIKE 'PSHOP%' THEN 'PSHOP' "
            +"WHEN business_type LIKE 'SHOP%' THEN 'SHOP' WHEN business_type LIKE 'RECYCLE%' THEN 'RECYCLE' ELSE 'OTHER' END";
        if(!"ALL".equals(filter.type())){where.append(" AND (").append(category).append(")=?");args.add(filter.type());}
        if("PENDING".equals(filter.state()))where.append(" AND state IN ('READY','PREPARED','RUNNING') AND kind IN ('ITEM','CURRENCY','COMMAND')");
        else if("ISSUES".equals(filter.state()))where.append(" AND state IN ('FAILED','REVIEW')");
        else if(!"ALL".equals(filter.state())){where.append(" AND state=?");args.add(filter.state());}
        return where.toString();
    }
    private MarketAuditRow mapAuditRow(ResultSet rows) throws SQLException{
        return new MarketAuditRow(rows.getString("id"),rows.getString("trade_id"),rows.getString("source"),rows.getString("kind"),rows.getString("business_type"),rows.getString("state"),
            parseUuidSafe(rows.getString("player"),"audit.player"),parseUuidSafe(rows.getString("other_player"),"audit.other"),rows.getString("player_name"),rows.getString("other_name"),
            rows.getString("item_name"),rows.getString("material"),rows.getString("currency"),rows.getString("amount"),rows.getString("note"),rows.getString("item_data"),rows.getLong("created_at"),rows.getLong("updated_at"));
    }
    @Override public MarketAuditPage searchMarketAudit(MarketAuditFilter filter){
        String scope=filter.group()!=null && !filter.group().isBlank()?"all":filter.scope();
        String source=" FROM ("+auditSource(scope)+") audit";
        List<Object> args=new ArrayList<>();String where=auditWhere(filter,args);
        try(Connection conn=getConnection()){
            if(isMysql())conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try{
                long total;
                try(PreparedStatement count=conn.prepareStatement("SELECT COUNT(*)"+source+where)){
                    for(int i=0;i<args.size();i++)count.setObject(i+1,args.get(i));
                    try(ResultSet rows=count.executeQuery()){total=rows.next()?rows.getLong(1):0;}
                }
                int pages=(int)Math.max(1,Math.min(Integer.MAX_VALUE,(total+11)/12));
                int page=Math.max(0,Math.min(filter.page(),pages-1));
                List<MarketAuditRow> result=new ArrayList<>();
                try(PreparedStatement query=conn.prepareStatement("SELECT *"+source+where+" ORDER BY created_at DESC,id DESC LIMIT 12 OFFSET ?")){
                    for(int i=0;i<args.size();i++)query.setObject(i+1,args.get(i));
                    query.setLong(args.size()+1,page*12L);
                    try(ResultSet rows=query.executeQuery()){while(rows.next())result.add(mapAuditRow(rows));}
                }
                conn.commit();return new MarketAuditPage(List.copyOf(result),page,pages,total);
            }catch(SQLException|RuntimeException exception){conn.rollback();throw exception;}
            finally{conn.setAutoCommit(true);}
        }catch(SQLException exception){throw new IllegalStateException("交易管理查询失败",exception);}
    }
    @Override public MarketAuditRow marketAuditRow(String id){
        if(id==null || id.length()>100)return null;
        String scope=id.startsWith("op:")?"operations":"history";
        try(Connection conn=getConnection();PreparedStatement query=conn.prepareStatement("SELECT * FROM ("+auditSource(scope)+") audit WHERE id=?")){
            query.setString(1,id);try(ResultSet rows=query.executeQuery()){return rows.next()?mapAuditRow(rows):null;}
        }catch(SQLException exception){throw new IllegalStateException("交易详情读取失败",exception);}
    }

}
