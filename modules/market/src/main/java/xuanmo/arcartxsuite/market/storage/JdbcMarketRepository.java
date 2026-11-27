package xuanmo.arcartxsuite.market.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.BuildEdition;
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
    private String tShopReservations;
    private String tAuctionPublications;
    private String tAuctionRevision;
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
        tShopReservations = prefix + "shop_order_reservations";
        tAuctionPublications = prefix + "auction_publications";
        if (BuildEdition.CROSS_SERVER_INCLUDED) tAuctionRevision = prefix + "auction_revision";
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
                total += execUpdate(conn, "DELETE FROM " + tShopReservations + " WHERE player=?", uuid);
                total += execUpdate(conn, "DELETE FROM " + tAuctionPublications + " WHERE seller=? OR buyer=?", uuid, uuid);
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
            prefix + "shop_order_reservations",
            prefix + "auction_publications",
            prefix + "auction_revision",
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
        try (Connection conn = getConnection()) {
            insertListing(conn, listing);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入拍卖物品失败", e);
            return false;
        }
    }

    private void insertListing(Connection conn, AuctionListing listing) throws SQLException {
        String sql = "INSERT INTO " + tListings
            + " (seller, seller_name, item_data, item_display_name, pinyin, initials, message, category, buy_now_price, starting_bid, current_bid, highest_bidder, currency, type, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
                if (!rs.next()) throw new SQLException("拍卖挂单编号未返回");
                listing.setId(rs.getLong(1));
            }
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
    public List<AuctionListing> getExpiredListings(int limit) {
        String sql = "SELECT * FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at <= ? ORDER BY expires_at,id LIMIT ?";
        return queryListings(sql, System.currentTimeMillis(), limit);
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
        try (Connection conn = getConnection()) {
            insertHistory(conn, history, java.util.Objects.toString(xuanmo.arcartxsuite.market.MarketLedger.currentTradeId(), ""),
                xuanmo.arcartxsuite.market.MarketLedger.currentItemMaterial());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入交易历史失败", e);
        }
    }

    private void insertHistory(Connection conn, AuctionHistory history, String trade, String material) throws SQLException {
        execUpdate(conn, "INSERT INTO " + tHistory
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp, trade_id, material)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", history.listingId(), history.seller().toString(),
            history.buyer() == null ? null : history.buyer().toString(), history.itemData(), history.itemDisplayName(),
            history.price(), history.currency(), history.taxAmount(), history.transactionType(), history.timestamp(), trade, material);
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
    private static final int AUCTION_FAVORITE_LIMIT = 100;

    @Override
    public FavoriteChange toggleAuctionFavorite(UUID player, long listingId) {
        for (int attempt = 0; ; attempt++) {
            try {
                return writeAuctionFavorite(player, listingId);
            } catch (SQLException failure) {
                int code = failure.getErrorCode();
                boolean contention = isMysql() ? code == 1213 : (code & 0xff) == 5;
                if (attempt >= 2 || !contention) throw new IllegalStateException("无法保存拍卖关注状态", failure);
                try {
                    Thread.sleep(10L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("拍卖关注操作已中断", interrupted);
                }
            }
        }
    }

    private FavoriteChange writeAuctionFavorite(UUID player, long listingId) throws SQLException {
        try (Connection conn = getConnection()) {
            int previousIsolation = conn.getTransactionIsolation();
            boolean lockRows = isMysql();
            if (lockRows) conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                String id = player.toString();
                // SQLite takes its write lock here. On MySQL, the range read below also locks gaps for this player's keys.
                if (execUpdate(conn, "DELETE FROM " + tFavorites + " WHERE player=? AND listing_id=?", id, listingId) != 0) {
                    conn.commit();
                    return FavoriteChange.REMOVED;
                }
                try (PreparedStatement lookup = conn.prepareStatement("SELECT 1 FROM " + tListings + " WHERE id=?")) {
                    lookup.setLong(1, listingId);
                    try (ResultSet rows = lookup.executeQuery()) {
                        if (!rows.next()) { conn.rollback(); return FavoriteChange.MISSING; }
                    }
                }
                try (PreparedStatement favorites = conn.prepareStatement("SELECT listing_id FROM " + tFavorites
                        + " WHERE player=? ORDER BY listing_id LIMIT " + AUCTION_FAVORITE_LIMIT + (lockRows ? " FOR UPDATE" : ""))) {
                    favorites.setString(1, id);
                    try (ResultSet rows = favorites.executeQuery()) {
                        int count = 0;
                        while (rows.next()) count++;
                        if (count == AUCTION_FAVORITE_LIMIT) { conn.rollback(); return FavoriteChange.LIMIT_REACHED; }
                    }
                }
                execUpdate(conn, "INSERT INTO " + tFavorites + " (player,listing_id) VALUES (?,?)", id, listingId);
                conn.commit();
                return FavoriteChange.ADDED;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally {
                conn.setAutoCommit(true);
                if (lockRows) conn.setTransactionIsolation(previousIsolation);
            }
        }
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

    @Override
    public @Nullable xuanmo.arcartxsuite.market.auction.AuctionBidTerms readAuctionBidTerms(long listingId) {
        String sql = "SELECT l.id,l.seller,l.starting_bid,l.current_bid,l.highest_bidder,l.currency,l.type,l.status,l.expires_at,"
            + " (SELECT b.bidder_name FROM " + tBids + " b WHERE b.listing_id=l.id AND b.bidder=l.highest_bidder"
            + " ORDER BY b.timestamp DESC,b.id DESC LIMIT 1) AS highest_bidder_name"
            + " FROM " + tListings + " l WHERE l.id=?";
        try (Connection conn = getConnection(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, listingId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new xuanmo.arcartxsuite.market.auction.AuctionBidTerms(rows.getLong("id"),
                    parseUuidSafe(rows.getString("seller"), "listing.seller"), rows.getDouble("starting_bid"),
                    rows.getDouble("current_bid"), parseUuidSafe(rows.getString("highest_bidder"), "listing.highest_bidder"),
                    rows.getString("highest_bidder_name"), rows.getString("currency"),
                    AuctionListing.ListingType.valueOf(rows.getString("type")),
                    AuctionListing.ListingStatus.valueOf(rows.getString("status")), rows.getLong("expires_at"));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("无法读取拍卖报价 " + listingId, failure);
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
        try (Connection conn = getConnection()) {
            insertPlayerShopListing(conn, listing);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入玩家商店物品失败", e);
            return false;
        }
    }

    private void insertPlayerShopListing(Connection conn, PlayerShopListing listing) throws SQLException {
        String sql = "INSERT INTO " + tPlayerShopListings
            + " (seller, seller_name, item_data, item_display_name, pinyin, initials, message, price, currency, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
                if (!rs.next()) throw new SQLException("玩家商品编号未返回");
                listing.setId(rs.getLong(1));
            }
        }
    }


    @Override
    public @Nullable PlayerShopListing getPlayerShopListing(long listingId) {
        return readPlayerShopListing(listingId, "SELECT * FROM " + tPlayerShopListings + " WHERE id=?");
    }

    @Override
    public MarketListingDraft.Reservation reserveListingDraft(MarketListingDraft draft, int maximum) {
        return commitListingDraft(conn -> {
            UUID seller = draft.seller();
            // The upsert takes the seller's row lock before counting either type of draft.
            noteMarketPlayer(conn, seller, draft.sellerName());
            try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*),"
                    + "COALESCE(SUM(CASE WHEN status='PREPARING' THEN 1 ELSE 0 END),0) FROM " + listingTable(draft.kind())
                    + " WHERE seller=? AND (status IN ('PREPARING','RESERVED') OR (status='ACTIVE' AND expires_at>?))")) {
                count.setString(1, seller.toString());
                count.setLong(2, System.currentTimeMillis());
                try (ResultSet row = count.executeQuery()) {
                    if (!row.next()) throw new SQLException("无法计算已占用上架名额");
                    if (row.getInt(2) != 0) return MarketListingDraft.Reservation.ALREADY_PENDING;
                    if (row.getLong(1) >= maximum) return MarketListingDraft.Reservation.LIMIT_REACHED;
                }
            }
            if (draft instanceof AuctionListingDraft auction) {
                insertListing(conn, auction.listing());
            } else if (draft instanceof PlayerShopListingDraft playerShop) {
                ensurePlayerShopProfile(conn, seller, draft.sellerName(), false);
                insertPlayerShopListing(conn, playerShop.listing());
            }
            insertOperation(conn, listingDraftOperation(draft, false), false);
            insertOperation(conn, listingDraftOperation(draft, true), false);
            return MarketListingDraft.Reservation.RESERVED;
        });
    }

    private String listingTable(MarketListingDraft.Kind kind) {
        return kind == MarketListingDraft.Kind.AUCTION ? tListings : tPlayerShopListings;
    }

    @Override
    public void recordListingFee(MarketListingDraft draft, xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment) {
        commitListingDraft(conn -> {
            var fee = listingDraftOperation(draft, true);
            var order = listingDraftOperation(draft, false);
            if (payment.uncertain()) {
                finishRunningOperation(conn, fee, "REVIEW", "上架费结果不明");
                finishRunningOperation(conn, order, "REVIEW", "物品未扣除，请核对上架费");
            } else if (!payment.success()) {
                finishRunningOperation(conn, fee, "FAILED", "未扣上架费");
                finishRunningOperation(conn, order, "FAILED", "上架未提交，物品未扣除");
                cancelListingDraftRow(conn, draft.kind(), draft.listingId(), draft.seller());
            } else {
                finishRunningOperation(conn, fee, "DONE", "上架费已确认");
                if (execUpdate(conn, "UPDATE " + tOperations
                        + " SET state='LISTING_SAVING',owner=?,note=?,updated_at=?"
                        + " WHERE id=? AND kind=? AND state='RUNNING' AND owner=?",
                        draft.sourceNode(), "等待玩家自然存档确认物品扣除", System.currentTimeMillis(), order.id(),
                        draft.kind().operation(), order.owner()) != 1) {
                    throw new SQLException("上架扣除意图状态冲突 " + draft.id());
                }
            }
            return null;
        });
    }

    @Override
    public boolean abortListingDraft(MarketListingDraft.Save save, String sourceNode, String note) {
        return commitListingDraft(conn -> {
            long now = System.currentTimeMillis();
            if (execUpdate(conn, "UPDATE " + tOperations + " SET state='CANCELLED',note=?,updated_at=?"
                    + " WHERE id=? AND player=? AND kind=? AND state='LISTING_SAVING' AND owner=? AND reference=?",
                    note, now, save.id(), save.seller().toString(), save.kind().operation(), sourceNode, save.reference()) != 1) {
                return false;
            }
            cancelListingDraftRow(conn, save.kind(), save.listingId(), save.seller());
            var fee = reviewChild(conn, save.id(), "DEBIT");
            if (fee == null || !"DONE".equals(fee.state())) throw new SQLException("上架费尚未确认 " + save.id());
            if (new java.math.BigDecimal(fee.amount()).signum() > 0) {
                insertOperation(conn, new MarketOperation(save.id() + ":refund", save.id(), save.seller(), "CURRENCY",
                    save.kind().prefix() + "-fee-refund", "READY", fee.currency(), fee.amount(), "",
                    "等待退还上架费", "", now, now, fee.itemName(), fee.material()), false);
            }
            return true;
        });
    }

    @Override
    public List<MarketListingDraft.Save> listingSaves(String sourceNode, String after, int limit) {
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement("SELECT id,player,kind,reference FROM " + tOperations
                + " WHERE state='LISTING_SAVING' AND kind IN ('AUCTION_LIST','PSHOP_LIST') AND owner=? AND id>? ORDER BY id LIMIT ?")) {
            query.setString(1, sourceNode);
            query.setString(2, after);
            query.setInt(3, limit);
            List<MarketListingDraft.Save> result = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String[] ref = rows.getString("reference").split(":");
                    var kind = "AUCTION_LIST".equals(rows.getString("kind")) ? MarketListingDraft.Kind.AUCTION : MarketListingDraft.Kind.PLAYER_SHOP;
                    if (ref.length != 4 || !kind.prefix().equals(ref[0])) {
                        throw new SQLException("上架暂存快照无效 " + rows.getString("id"));
                    }
                    result.add(new MarketListingDraft.Save(kind, rows.getString("id"), UUID.fromString(rows.getString("player")),
                        Long.parseLong(ref[1]), Long.parseLong(ref[2]), Long.parseLong(ref[3])));
                }
            }
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("无法读取等待存档的上架记录", failure);
        }
    }

    @Override
    public boolean publishListingDraft(MarketListingDraft.Save save, String sourceNode) {
        return commitListingDraft(conn -> {
            long now = System.currentTimeMillis();
            int updated = execUpdate(conn, "UPDATE " + tOperations
                    + " SET state='DONE',note=?,updated_at=? WHERE id=? AND player=?"
                    + " AND kind=? AND state='LISTING_SAVING' AND owner=? AND reference=?",
                "玩家存档已确认扣除，商品已发布", now, save.id(), save.seller().toString(),
                save.kind().operation(), sourceNode, save.reference());
            if (updated != 1) return false;
            long expires = Math.addExact(now, save.durationMillis());
            if (execUpdate(conn, "UPDATE " + listingTable(save.kind())
                    + " SET status='ACTIVE',created_at=?,expires_at=? WHERE id=? AND seller=? AND status='PREPARING'",
                    now, expires, save.listingId(), save.seller().toString()) != 1) {
                throw new SQLException("商品上架预留状态冲突 " + save.id());
            }
            if (save.kind() == MarketListingDraft.Kind.AUCTION) {
                execUpdate(conn, "UPDATE " + tListings + " SET original_expires_at=? WHERE id=?", expires, save.listingId());
            } else {
                execUpdate(conn, "UPDATE " + tShopProfiles
                    + " SET published=1,version=version+1,created_at=?,updated_at=? WHERE owner=? AND published=0",
                    now, now, save.seller().toString());
            }
            return true;
        });
    }

    @Override
    public boolean isListingDraftPublished(MarketListingDraft.Save save, String sourceNode) {
        String sql = "SELECT 1 FROM " + tOperations
            + " WHERE id=? AND player=? AND kind=? AND state='DONE' AND owner=? AND reference=?";
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, save.id());
            query.setString(2, save.seller().toString());
            query.setString(3, save.kind().operation());
            query.setString(4, sourceNode);
            query.setString(5, save.reference());
            try (ResultSet row = query.executeQuery()) {
                return row.next();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("无法核对上架提交结果 " + save.id(), failure);
        }
    }

    private MarketOperation listingDraftOperation(MarketListingDraft draft, boolean debit) {
        long now = System.currentTimeMillis();
        return new MarketOperation(debit ? draft.id() + ":fee" : draft.id(), draft.id(), draft.seller(),
            debit ? "DEBIT" : draft.kind().operation(), debit ? draft.kind().prefix() + "-fee" : draft.reference(), "RUNNING",
            draft.feeCurrency(), draft.fee().toPlainString(), debit ? "" : draft.itemData(),
            debit ? "等待上架费" : "暂存商品，物品尚未扣除", draft.worker(), now, now, draft.itemName(), draft.material());
    }

    private void cancelListingDraftRow(Connection conn, MarketListingDraft.Kind kind, long listingId, UUID seller) throws SQLException {
        if (execUpdate(conn, "UPDATE " + listingTable(kind)
                + " SET status='CANCELLED' WHERE id=? AND seller=? AND status='PREPARING'", listingId, seller.toString()) != 1) {
            throw new SQLException("暂存商品状态冲突 #" + listingId);
        }
    }

    @FunctionalInterface
    private interface ListingWrite<T> {
        T write(Connection conn) throws SQLException;
    }

    private <T> T commitListingDraft(ListingWrite<T> action) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.write(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException failure) {
            throw new IllegalStateException("玩家商店上架事务失败", failure);
        }
    }

    @Override
    public @Nullable PlayerShopListing getPublishedPlayerShopListing(long listingId) {
        String sql = "SELECT l.* FROM " + tPlayerShopListings + " l JOIN " + tShopProfiles
            + " p ON p.owner=l.seller WHERE l.id=? AND p.published=1";
        return readPlayerShopListing(listingId, sql);
    }

    private @Nullable PlayerShopListing readPlayerShopListing(long listingId, String sql) {
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
            + " WHERE status='ACTIVE' AND expires_at > ? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), limit, offset);
    }

    @Override
    public List<PlayerShopListing> getActivePlayerShopListingsBySeller(UUID seller, int offset, int limit) {
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), seller.toString(), limit, offset);
    }

    private static final String PLAYER_SHOP_SEARCH = " AND (item_display_name LIKE ? ESCAPE '!'"
        + " OR pinyin LIKE ? ESCAPE '!' OR initials LIKE ? ESCAPE '!')";

    @Override
    public List<PlayerShopListing> searchPlayerShopListings(String keyword, int offset, int limit) {
        String like = shopLike(keyword);
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?" + PLAYER_SHOP_SEARCH
            + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), like, like, like, limit, offset);
    }

    @Override
    public List<MarketRepository.PlayerShopSellerInfo> getPlayerShopSellers(int offset, int limit) {
        String sql = "SELECT l.seller, COALESCE(NULLIF(p.owner_name,''),MAX(l.seller_name)) AS seller_name, COUNT(*) AS active_count"
            + " FROM " + tPlayerShopListings + " l LEFT JOIN " + tShopProfiles + " p ON p.owner=l.seller"
            + " WHERE l.status='ACTIVE' AND l.expires_at > ?"
            + " GROUP BY l.seller,p.owner_name ORDER BY active_count DESC,l.seller LIMIT ? OFFSET ?";
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
        String like = shopLike(keyword);
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ?" + PLAYER_SHOP_SEARCH,
            System.currentTimeMillis(), like, like, like);
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
        String like = shopLike(keyword);
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=?" + PLAYER_SHOP_SEARCH
            + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            System.currentTimeMillis(), seller.toString(), like, like, like, limit, offset);
    }

    @Override
    public int countSearchPlayerShopListingsBySeller(UUID seller, String keyword) {
        String like = shopLike(keyword);
        return countQuery("SELECT COUNT(*) FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND seller=?" + PLAYER_SHOP_SEARCH,
            System.currentTimeMillis(), seller.toString(), like, like, like);
    }

    @Override
    public List<PlayerShopListing> getExpiredPlayerShopListings(int limit) {
        return queryPlayerShopListings("SELECT * FROM " + tPlayerShopListings
            + " WHERE status='ACTIVE' AND expires_at <= ? ORDER BY expires_at ASC,id ASC LIMIT ?",
            System.currentTimeMillis(), limit);
    }

    @Override
    public boolean terminatePlayerShopListing(PlayerShopTermination termination) {
        return retryTerminalSettlement(() -> commitPlayerShopTermination(termination),
            "玩家商店终止结算 #" + termination.expected().getId());
    }

    private boolean commitPlayerShopTermination(PlayerShopTermination termination) throws SQLException {
        var expected = termination.expected();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed = execUpdate(conn, "UPDATE " + tPlayerShopListings
                    + " SET status=? WHERE id=? AND seller=? AND status='ACTIVE' AND expires_at=? AND (?=0 OR expires_at<=?)",
                    termination.expired() ? "EXPIRED" : "CANCELLED", expected.getId(), expected.getSeller().toString(),
                    expected.getExpiresAt(), termination.expired() ? 1 : 0, System.currentTimeMillis());
                if (changed != 1) {
                    conn.rollback();
                    return false;
                }
                var receipt = termination.receipt();
                insertOperation(conn, receipt, false);
                insertPlayerShopHistory(conn, termination.history(), receipt.id(), receipt.material());
                for (var delivery : termination.deliveries()) insertOperation(conn, delivery, false);
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


    private void insertPlayerShopHistory(Connection conn, MarketRepository.PlayerShopHistory history,
                                         String tradeId, String material) throws SQLException {
        String sql = "INSERT INTO " + tPlayerShopHistory
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp, trade_id, material)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            ps.setString(11, tradeId);
            ps.setString(12, material);
            ps.executeUpdate();
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tShopReservations
                + " (order_id VARCHAR(64) PRIMARY KEY, player VARCHAR(36) NOT NULL, shop_id VARCHAR(64) NOT NULL,"
                + " item_id VARCHAR(64) NOT NULL, quantity INTEGER NOT NULL, stock_mode VARCHAR(16) NOT NULL,"
                + " stock_maximum INTEGER NOT NULL, stock_revision BIGINT NOT NULL, quota_reset BIGINT NOT NULL,"
                + " quota_epoch VARCHAR(36) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAuctionPublications
                + " (id VARCHAR(64) PRIMARY KEY, listing_id BIGINT NOT NULL, seller VARCHAR(36) NOT NULL, buyer VARCHAR(36),"
                + " item VARCHAR(255) NOT NULL, price DOUBLE NOT NULL, currency VARCHAR(64) NOT NULL, tax DOUBLE NOT NULL,"
                + " buyer_name VARCHAR(64) NOT NULL, event_pending INTEGER NOT NULL, refresh_pending INTEGER NOT NULL,"
                + " lease_until BIGINT NOT NULL, lease_token VARCHAR(36) NOT NULL)");
            if (BuildEdition.CROSS_SERVER_INCLUDED) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAuctionRevision
                    + " (id INTEGER PRIMARY KEY, revision BIGINT NOT NULL)");
                statement.executeUpdate("INSERT INTO " + tAuctionRevision + " (id,revision) VALUES (1,0)"
                    + (isMysql() ? " ON DUPLICATE KEY UPDATE id=id" : " ON CONFLICT(id) DO NOTHING"));
            }
        }
        ensureManagementColumn(conn, tStockPolicies, "revision", "BIGINT NOT NULL DEFAULT 0");
        ensureManagementColumn(conn, tShopLimits, "quota_epoch", "VARCHAR(36) NOT NULL DEFAULT ''");
        ensureTableIndex(conn, tShopReservations, "shop_reservations_player", "player");
        ensureTableIndex(conn, tAuctionPublications, "auction_publish_lease", "lease_until,id");
        // Metadata avoids swallowing DDL errors while remaining restart-safe on both dialects.
        ensureOperationIndex(conn, "op_state_player", "state,player,created_at");
        ensureOperationIndex(conn, "op_trade", "trade_id");
        ensureOperationIndex(conn, "op_owner_state", "owner,state");
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
        try (Connection conn = getConnection()) {
            insertOperation(conn, operation, false);
        } catch (SQLException exception) { throw new IllegalStateException("Market 持久化失败", exception); }
    }

    private int insertOperation(Connection conn, MarketOperation operation, boolean ignoreDuplicate) throws SQLException {
        String duplicate = ignoreDuplicate && !isMysql() ? " ON CONFLICT(id) DO NOTHING" : "";
        String insert = ignoreDuplicate && isMysql() ? "INSERT IGNORE INTO " : "INSERT INTO ";
        return execUpdate(conn, insert + tOperations
            + " (id,trade_id,player,kind,reference,state,currency,amount,item_data,note,owner,created_at,updated_at,item_name,material) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" + duplicate,
            operation.id(), operation.tradeId(), operation.player().toString(), operation.kind(), operation.reference(), operation.state(),
            operation.currency(), operation.amount(), operation.itemData(), operation.note(), operation.owner(), operation.createdAt(), operation.updatedAt(),operation.itemName(),operation.material());
    }

    @Override
    public @Nullable MarketOperation shopOrder(String requestId) {
        var orders = readOperations("SELECT * FROM " + tOperations + " WHERE id=? AND kind='SHOP_BUY'"
            + " AND reference LIKE 'shop-order:%'", List.of(requestId));
        return orders.isEmpty() ? null : orders.get(0);
    }

    @Override
    public Map<String, ShopCheckout.Availability> shopAvailability(UUID player, String shop, List<ShopCheckout.Product> products) {
        if (products.isEmpty()) return Map.of();
        String requested = String.join(" UNION ALL ", java.util.Collections.nCopies(products.size(),
            "SELECT ? AS product_id, ? AS stock_mode, ? AS max_stock, ? AS quota"));
        String sql = "WITH requested AS (" + requested + ") SELECT r.product_id,"
            + " CASE WHEN r.stock_mode='global' THEN COALESCE(g.remaining,r.max_stock)"
            + " WHEN r.stock_mode='per-player' THEN COALESCE(p.remaining,CASE WHEN b.default_stock<r.max_stock"
            + " THEN b.default_stock ELSE r.max_stock END) ELSE r.max_stock END AS remaining,"
            + " CASE WHEN r.quota<=0 THEN 2147483647 ELSE r.quota-CASE"
            + " WHEN l.reset_time=0 OR l.reset_time>? THEN COALESCE(l.purchased_count,0) ELSE 0 END END AS quota_left"
            + " FROM requested r LEFT JOIN " + tGlobalStock + " g ON g.shop_id=? AND g.item_id=r.product_id"
            + " LEFT JOIN " + tPlayerStock + " p ON p.shop_id=? AND p.player=? AND p.item_id=r.product_id"
            + " LEFT JOIN " + tStockPolicies + " b ON b.shop_id=? AND b.item_id=r.product_id"
            + " LEFT JOIN " + tShopLimits + " l ON l.shop_id=? AND l.player=? AND l.item_id=r.product_id";
        Map<String, ShopCheckout.Availability> result = new java.util.HashMap<>();
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            int index = 1;
            for (var product : products) {
                query.setString(index++, product.item());
                query.setString(index++, product.stockMode());
                query.setInt(index++, product.stockMaximum());
                query.setInt(index++, product.limit());
            }
            query.setLong(index++, System.currentTimeMillis());
            query.setString(index++, shop);
            query.setString(index++, shop);
            query.setString(index++, player.toString());
            query.setString(index++, shop);
            query.setString(index++, shop);
            query.setString(index, player.toString());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), new ShopCheckout.Availability(
                    Math.max(0, rows.getInt(2)), Math.max(0, rows.getInt(3))));
            }
        } catch (SQLException exception) { throw new IllegalStateException("批量读取系统商店库存失败", exception); }
        return result;
    }

    @Override
    public ShopCheckout.Reservation reserveShopOrder(MarketOperation order, ShopCheckout checkout, List<MarketOperation> rewards) {
        for (int attempt = 0; ; attempt++) {
            try {
                return reserveShopOrderTransaction(order, checkout, rewards);
            } catch (SQLException exception) {
                // MySQL 1213 has already rolled the entire transaction back. No debit has started.
                if (attempt >= 2 || !isMysql() || exception.getErrorCode() != 1213) {
                    throw new IllegalStateException("系统商店订单预留失败", exception);
                }
            }
        }
    }

    private ShopCheckout.Reservation reserveShopOrderTransaction(MarketOperation order, ShopCheckout checkout,
                                                                List<MarketOperation> rewards) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (insertOperation(conn, order, true) != 1) {
                    conn.rollback();
                    return new ShopCheckout.Reservation(false);
                }
                var reservation = conn.setSavepoint();
                var quota = reserveShopQuota(conn, order, checkout);
                long stockRevision = quota.reset() < 0 || !tracksStock(checkout) ? -1
                    : lockStockRevision(conn, checkout.shop(), checkout.item());
                String reason = quota.reset() < 0 ? "购买数量超过限购"
                    : !reserveShopStock(conn, order.player(), checkout) ? "商品库存不足" : null;
                if (reason != null) {
                    conn.rollback(reservation);
                    setShopOrderState(conn, order, "FAILED", reason);
                    conn.commit();
                    return new ShopCheckout.Reservation(false);
                }
                execUpdate(conn, "INSERT INTO " + tShopReservations + " (order_id,player,shop_id,item_id,quantity,"
                    + "stock_mode,stock_maximum,stock_revision,quota_reset,quota_epoch) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    order.id(), order.player().toString(), checkout.shop(), checkout.item(), checkout.quantity(),
                    checkout.stockMode(), checkout.stockMaximum(), stockRevision, quota.reset(), quota.epoch());
                for (var reward : rewards) insertOperation(conn, reward, false);
                conn.commit();
                return new ShopCheckout.Reservation(true);
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        }
    }

    private record QuotaClaim(long reset, String epoch) { }

    private QuotaClaim reserveShopQuota(Connection conn, MarketOperation order, ShopCheckout checkout) throws SQLException {
        if (checkout.limit() <= 0) return new QuotaClaim(0, "");
        String player = order.player().toString();
        String key = " WHERE player=? AND shop_id=? AND item_id=?";
        execUpdate(conn, stockRowInsert() + tShopLimits
            + " (player,shop_id,item_id,purchased_count,last_purchase_time,reset_time) VALUES (?,?,?,0,0,0)"
            + stockRowLock("purchased_count"),
            player, checkout.shop(), checkout.item());
        var calendar = java.time.ZonedDateTime.now();
        long now = calendar.toInstant().toEpochMilli();
        String expired = "reset_time>0 AND reset_time<=?";
        int updated = execUpdate(conn, "UPDATE " + tShopLimits
            + " SET purchased_count=CASE WHEN " + expired + " THEN ? ELSE purchased_count+? END,"
            + " quota_epoch=CASE WHEN (" + expired + ") OR quota_epoch='' THEN ? ELSE quota_epoch END,"
            + " last_purchase_time=?,reset_time=?" + key
            + " AND (? >= 0 AND ((" + expired + ") OR purchased_count<=?))",
            now, checkout.quantity(), checkout.quantity(), now, UUID.randomUUID().toString(), now, checkout.nextLimitReset(calendar),
            player, checkout.shop(), checkout.item(), checkout.limit()-checkout.quantity(), now, checkout.limit()-checkout.quantity());
        if (updated != 1) return new QuotaClaim(-1, "");
        try (PreparedStatement query = conn.prepareStatement("SELECT reset_time,quota_epoch FROM " + tShopLimits + key)) {
            query.setString(1, player);
            query.setString(2, checkout.shop());
            query.setString(3, checkout.item());
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) return new QuotaClaim(rows.getLong(1), rows.getString(2));
                throw new SQLException("限购预留记录缺失");
            }
        }
    }

    private boolean reserveShopStock(Connection conn, UUID player, ShopCheckout checkout) throws SQLException {
        if (!tracksStock(checkout)) return true;
        String insert = stockRowInsert();
        String shop = checkout.shop(), item = checkout.item();
        int amount = checkout.quantity();
        if ("global".equals(checkout.stockMode())) {
            execUpdate(conn, insert + tGlobalStock + " (shop_id,item_id,remaining) VALUES (?,?,?)" + stockRowLock("remaining"),
                shop, item, checkout.stockMaximum());
            return execUpdate(conn, "UPDATE " + tGlobalStock
                + " SET remaining=remaining-? WHERE shop_id=? AND item_id=? AND remaining>=?", amount, shop, item, amount) == 1;
        }
        int initial = lockedPlayerStockDefault(conn, shop, item, checkout.stockMaximum());
        execUpdate(conn, insert + tPlayerStock + " (player,shop_id,item_id,remaining) VALUES (?,?,?,?)" + stockRowLock("remaining"),
            player.toString(), shop, item, initial);
        return execUpdate(conn, "UPDATE " + tPlayerStock
            + " SET remaining=remaining-? WHERE player=? AND shop_id=? AND item_id=? AND remaining>=?",
            amount, player.toString(), shop, item, amount) == 1;
    }

    private static boolean tracksStock(ShopCheckout checkout) {
        return checkout.stockMaximum() > 0 && !"unlimited".equals(checkout.stockMode());
    }

    private long lockStockRevision(Connection conn, String shop, String item) throws SQLException {
        execUpdate(conn, stockRowInsert() + tStockPolicies
            + " (shop_id,item_id,next_at,default_stock) VALUES (?,?,0,NULL)" + stockRowLock("revision"), shop, item);
        try (PreparedStatement query = conn.prepareStatement("SELECT revision FROM " + tStockPolicies
            + " WHERE shop_id=? AND item_id=?" + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, shop);
            query.setString(2, item);
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) return rows.getLong(1);
                throw new SQLException("库存版本缺失");
            }
        }
    }

    private String stockRowInsert() { return isMysql() ? "INSERT INTO " : "INSERT OR IGNORE INTO "; }

    private String stockRowLock(String column) {
        // Acquire the existing row exclusively rather than upgrading INSERT IGNORE's shared lock.
        return isMysql() ? " ON DUPLICATE KEY UPDATE " + column + "=" + column : "";
    }

    @Override
    public void finishShopOrder(MarketOperation order, boolean paid, String note) {
        for (int attempt = 0; ; attempt++) {
            try {
                finishShopOrderTransaction(order, paid, note);
                return;
            } catch (SQLException exception) {
                // The debit receipt already exists. Only replay this rolled-back storage transaction.
                if (attempt >= 2 || !isMysql() || exception.getErrorCode() != 1213) {
                    throw new IllegalStateException("系统商店订单结算失败", exception);
                }
            }
        }
    }

    private void finishShopOrderTransaction(MarketOperation order, boolean paid, String note) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                setShopOrderState(conn, order, paid ? "DONE" : "FAILED", note);
                if (!paid) {
                    var reservation = loadShopReservation(conn, order.id());
                    if (reservation == null) throw new SQLException("扣款失败的订单缺少预留快照");
                    releaseShopReservation(conn, order.player(), reservation);
                }
                execUpdate(conn, "UPDATE " + tOperations + " SET state=?,note=?,updated_at=?"
                    + " WHERE trade_id=? AND owner=? AND state='PREPARED' AND kind IN ('ITEM','COMMAND')",
                    paid ? "READY" : "CANCELLED", paid ? "扣款成功，等待交付" : "未扣款，取消奖励快照",
                    System.currentTimeMillis(), order.id(), order.owner());
                conn.commit();
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        }
    }

    private void restoreReservedStock(Connection conn, UUID player, String mode, int maximum, String shop, String item, int amount)
        throws SQLException {
        String cap = isMysql() ? "LEAST" : "MIN";
        if ("global".equals(mode)) {
            execUpdate(conn, "UPDATE " + tGlobalStock + " SET remaining=" + cap
                + "(remaining+?,?) WHERE shop_id=? AND item_id=?", amount, maximum, shop, item);
        } else {
            execUpdate(conn, "UPDATE " + tPlayerStock + " SET remaining=" + cap
                + "(remaining+?,?) WHERE player=? AND shop_id=? AND item_id=?",
                amount, maximum, player.toString(), shop, item);
        }
    }

    private void setShopOrderState(Connection conn, MarketOperation order, String state, String note) throws SQLException {
        if (execUpdate(conn, "UPDATE " + tOperations
            + " SET state=?,note=?,updated_at=? WHERE id=? AND owner=? AND state='RUNNING'",
            state, note, System.currentTimeMillis(), order.id(), order.owner()) != 1) {
            throw new SQLException("系统商店订单状态冲突 " + order.id());
        }
    }

    @Override
    public boolean transitionOperation(String id, String expected, String state, String note, String owner) {
        String sql = "UPDATE " + tOperations + " SET state=?,note=?,owner=?,"
            + "updated_at=CASE WHEN updated_at>=? THEN updated_at+1 ELSE ? END WHERE id=? AND state=?";
        long now = System.currentTimeMillis();
        if ("RUNNING".equals(expected) || "PREPARED".equals(expected) || "ITEM_SAVING".equals(expected)) {
            return durableUpdate(sql + " AND owner=?", state, note, owner, now, now, id, expected, owner) == 1;
        }
        return durableUpdate(sql, state, note, owner, now, now, id, expected) == 1;
    }

    @Override
    public boolean transitionItemBatch(List<String> ids, String owner, boolean claim) {
        if (ids.isEmpty()) return true;
        String markers = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var values = new ArrayList<Object>();
        values.add(claim ? "ITEM_SAVING" : "DONE");
        values.add(claim ? "等待原子服保存玩家物品和交付凭证" : "物品和交付凭证已写入玩家存档");
        values.add(owner);
        values.add(System.currentTimeMillis());
        values.add(claim ? "READY" : "ITEM_SAVING");
        if (!claim) values.add(owner);
        values.addAll(ids);
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed = execUpdate(conn, "UPDATE " + tOperations
                    + " SET state=?,note=?,owner=?,updated_at=? WHERE kind='ITEM' AND state=?"
                    + (claim ? "" : " AND owner=?") + " AND id IN (" + markers + ")", values.toArray());
                if (changed != ids.size()) { conn.rollback(); return false; }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IllegalStateException("批量写入物品交付状态失败", exception); }
    }

    @Override
    public List<MarketOperation> operations(String state, UUID player, int offset, int limit) {
        String sql = "SELECT * FROM " + tOperations + " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (state != null) { sql += " AND state=?"; args.add(state); }
        if (player != null) { sql += " AND player=?"; args.add(player.toString()); }
        sql += "READY".equals(state) ? " ORDER BY created_at ASC,id ASC LIMIT ? OFFSET ?" : " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?";
        args.add(Math.max(1, Math.min(200, limit))); args.add(Math.max(0, offset));
        return readOperations(sql, args);
    }

    @Override
    public List<MarketOperation> pendingDeliveries(UUID player, String inventoryNode, int limit) {
        String sql = "SELECT * FROM " + tOperations + " WHERE player=? AND "
            + "(state='READY' OR (state='ITEM_SAVING' AND kind='ITEM' AND owner=?))"
            + " ORDER BY CASE WHEN state='ITEM_SAVING' THEN 0 WHEN kind='ITEM' THEN 2 ELSE 1 END,created_at,id LIMIT ?";
        return readOperations(sql, List.of(player.toString(), inventoryNode, Math.max(1, Math.min(200, limit))));
    }

    private List<MarketOperation> readOperations(String sql, List<Object> args) {
        List<MarketOperation> result = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) query.setObject(i + 1, args.get(i));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(marketOperation(rows));
            }
        } catch (SQLException exception) { throw new IllegalStateException("无法读取交易流水", exception); }
        return result;
    }

    private static MarketOperation marketOperation(ResultSet rows) throws SQLException {
        return new MarketOperation(rows.getString("id"), rows.getString("trade_id"), UUID.fromString(rows.getString("player")),
            rows.getString("kind"), rows.getString("reference"), rows.getString("state"), rows.getString("currency"), rows.getString("amount"),
            rows.getString("item_data"), rows.getString("note"), rows.getString("owner"), rows.getLong("created_at"),
            rows.getLong("updated_at"), rows.getString("item_name"), rows.getString("material"));
    }

    @Override
    public void heartbeatMarketOwner(String owner) {
        String sql = "INSERT INTO " + tOwners + " (owner,last_seen) VALUES (?,?)"
            + (isMysql() ? " ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen)" : " ON CONFLICT(owner) DO UPDATE SET last_seen=excluded.last_seen");
        durableUpdate(sql, owner, System.currentTimeMillis());
    }

    @Override
    public void recoverAbandonedOperations() {
        long now = System.currentTimeMillis();
        long cutoff = now - 120000L;
        List<String> owners = abandonedOperationOwners(cutoff);
        if (owners.isEmpty()) return;
        String stale = " AND owner IN (" + String.join(",", java.util.Collections.nCopies(owners.size(), "?")) + ")"
            + " AND owner NOT IN (SELECT owner FROM " + tOwners + " WHERE last_seen>=?)";
        durableUpdate("UPDATE " + tOperations + " SET state='READY',note=?,updated_at=? WHERE state='PREPARED' AND kind IN ('ITEM','COMMAND')"
            + stale
            + " AND trade_id IN (SELECT paid.trade_id FROM (SELECT DISTINCT trade_id FROM " + tOperations + " WHERE kind='DEBIT' AND state='DONE') paid)",
            recoveryArguments("已确认扣款成功，恢复持久奖励快照", now, owners, cutoff));
        durableUpdate("UPDATE " + tOperations + " SET state='DONE',note=?,updated_at=? WHERE state='RUNNING'"
            + " AND kind='SHOP_BUY' AND reference LIKE 'shop-order:%'"
            + stale
            + " AND id IN (SELECT paid.trade_id FROM (SELECT DISTINCT trade_id FROM " + tOperations
            + " WHERE kind='DEBIT' AND state='DONE') paid)",
            recoveryArguments("扣款已确认，奖励已进入交付队列", now, owners, cutoff));
        durableUpdate("UPDATE " + tOperations + " SET state='REVIEW',note=?,updated_at=? WHERE state='PREPARED'" + stale,
            recoveryArguments("进程已离线，扣款或收取物品等前置结果未确认，请核对父交易", now, owners, cutoff));
        String nextVersion = isMysql() ? "GREATEST(updated_at+1,?)" : "MAX(updated_at+1,?)";
        durableUpdate("UPDATE " + tOperations + " SET state='REVIEW',note=?,updated_at=" + nextVersion
            + " WHERE state='RUNNING'" + stale,
            recoveryArguments("所属进程已离线，外部结果需核对", now, owners, cutoff));
    }

    private List<String> abandonedOperationOwners(long cutoff) {
        String sql = "SELECT DISTINCT o.owner FROM " + tOperations + " o LEFT JOIN " + tOwners + " live"
            + " ON live.owner=o.owner AND live.last_seen>=? WHERE o.state IN ('RUNNING','PREPARED')"
            + " AND live.owner IS NULL LIMIT 128";
        List<String> owners = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            query.setLong(1, cutoff);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) owners.add(rows.getString(1));
            }
            return owners;
        } catch (SQLException exception) { throw new IllegalStateException("无法查询已离线交易", exception); }
    }

    private static Object[] recoveryArguments(String note, long now, List<String> owners, long cutoff) {
        List<Object> values = new ArrayList<>(owners.size() + 3);
        values.add(note);
        values.add(now);
        values.addAll(owners);
        values.add(cutoff);
        return values.toArray();
    }

    @Override
    public boolean resolveOperation(String id, String action, String note) {
        String state = switch (action) { case "delivered" -> "DONE"; case "cancel" -> "CANCELLED"; case "retry-confirmed" -> "READY"; default -> null; };
        if (state == null) return false;
        int separator = id.indexOf('@');
        Long expectedVersion = null;
        if (separator >= 0) {
            try { expectedVersion = Long.parseLong(id.substring(separator + 1)); }
            catch (NumberFormatException failure) { return false; }
            id = id.substring(0, separator);
        }
        var target = readOperations("SELECT * FROM " + tOperations + " WHERE id=?", List.of(id));
        if (target.isEmpty()) return false;
        MarketOperation task = target.get(0);
        if ("CURRENCY".equals(task.kind()) && expectedVersion == null) return false;
        if (expectedVersion != null && expectedVersion != task.updatedAt()) return false;
        var parent = task.tradeId() == null ? null : shopOrder(task.tradeId());
        if (parent != null && !("DONE".equals(parent.state()) && List.of("ITEM", "COMMAND").contains(task.kind()))) return false;
        var playerShop = task.tradeId() == null ? null : playerShopOrder(task.tradeId());
        if (playerShop != null && !("DONE".equals(playerShop.state()) && List.of("ITEM", "CURRENCY", "NOTICE").contains(task.kind()))
                && !("CANCELLED".equals(playerShop.state()) && "CURRENCY".equals(task.kind()) && "pshop-refund".equals(task.reference()))) {
            return false;
        }
        if (task.tradeId() != null && List.of("PSHOP_LIST", "AUCTION_LIST", "DEBIT").contains(task.kind())
                && !readOperations("SELECT * FROM " + tOperations
                    + " WHERE id=? AND ((kind='PSHOP_LIST' AND reference LIKE 'pshop-listing:%')"
                    + " OR (kind='AUCTION_LIST' AND reference LIKE 'auction-listing:%'))", List.of(task.tradeId())).isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        return durableUpdate("UPDATE " + tOperations + " SET state=?,note=?,"
            + "updated_at=CASE WHEN updated_at>=? THEN updated_at+1 ELSE ? END WHERE id=? AND state='REVIEW' AND updated_at=?"
            + ("READY".equals(state) ? " AND kind IN ('ITEM','CURRENCY','COMMAND','NOTICE')" : ""),
            state, note, now, now, id, task.updatedAt()) == 1;
    }

    private record ShopReservation(String shop, String item, int quantity, String stockMode, int stockMaximum,
                                     long stockRevision, long quotaReset, String quotaEpoch) { }

    @Override
    public String reviewShopOrder(String id, String action, String audit) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                MarketOperation order = lockShopOrder(conn, id);
                String result = order == null ? "系统商店订单不存在"
                    : "REFUND_PENDING".equals(order.state()) ? reviewPendingRefund(conn, order, action, audit)
                    : "REVIEW".equals(order.state()) ? reviewUncertainDebit(conn, order, action, audit)
                    : "当前状态 " + order.state() + "，无需重复处理";
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IllegalStateException("人工核对订单失败", exception); }
    }

    private String reviewUncertainDebit(Connection conn, MarketOperation order, String action, String audit) throws SQLException {
        if (!List.of("fulfill-paid", "refund-paid", "cancel-unpaid").contains(action)) return "该操作要求退款已进入待处理状态";
        var debit = reviewChild(conn, order.id(), "DEBIT");
        if (debit == null && !"cancel-unpaid".equals(action) || debit != null && !"REVIEW".equals(debit.state())) {
            return "扣款回执还未进入待核对状态";
        }
        int rewards = 0;
        try (PreparedStatement query = conn.prepareStatement("SELECT state FROM " + tOperations
            + " WHERE trade_id=? AND kind IN ('ITEM','COMMAND')")) {
            query.setString(1, order.id());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (!List.of("PREPARED", "REVIEW").contains(rows.getString(1))) return "奖励已发生状态变化，请先核对交付";
                    rewards++;
                }
            }
        }
        if (rewards == 0) return "订单缺少可核对的奖励快照";
        var reservation = loadShopReservation(conn, order.id());
        if (!"fulfill-paid".equals(action) && reservation == null) return "旧订单缺少预留快照，库存与限购需单独核对";
        if ("refund-paid".equals(action) && new java.math.BigDecimal(order.amount()).signum() <= 0) return "退款金额无效";

        boolean fulfilled = "fulfill-paid".equals(action);
        boolean unpaid = "cancel-unpaid".equals(action);
        setReviewState(conn, order.id(), "REVIEW", fulfilled ? "DONE" : unpaid ? "CANCELLED" : "REFUND_PENDING", audit);
        if (debit != null) setReviewState(conn, debit.id(), "REVIEW", unpaid ? "FAILED" : "DONE", audit);
        execUpdate(conn, "UPDATE " + tOperations + " SET state=?,note=?,updated_at=? WHERE trade_id=?"
            + " AND kind IN ('ITEM','COMMAND') AND state IN ('PREPARED','REVIEW')",
            fulfilled ? "READY" : "CANCELLED", audit, System.currentTimeMillis(), order.id());
        if (fulfilled) return "已确认扣款，按原快照补发";
        if (unpaid) {
            releaseShopReservation(conn, order.player(), reservation);
            return "已确认未扣款，已取消订单";
        }
        long now = System.currentTimeMillis();
        String refundId = UUID.nameUUIDFromBytes(("market-shop-refund:" + order.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        insertOperation(conn, new MarketOperation(refundId, order.id(), order.player(), "CURRENCY", "shop-refund",
            "READY", order.currency(), order.amount(), "", audit, "", now, now, order.itemName(), order.material()), false);
        return "已登记退款，退款回执确认后释放本周期库存和限购预留";
    }

    private String reviewPendingRefund(Connection conn, MarketOperation order, String action, String audit) throws SQLException {
        var refund = reviewChild(conn, order.id(), "CURRENCY");
        if (refund == null || !"shop-refund".equals(refund.reference())) return "订单缺少退款回执";
        if (!"REVIEW".equals(refund.state())) return "当前退款状态 " + refund.state() + "，无需重复处理";
        if ("retry-refund-uncredited".equals(action)) {
            setReviewState(conn, refund.id(), "REVIEW", "READY", audit);
            return "已确认未入账，原退款请求重新入队";
        }
        if (!"confirm-refunded".equals(action)) return "请核对退款流水后确认已入账，或确认未入账后重试";
        setReviewState(conn, refund.id(), "REVIEW", "DONE", audit);
        finishReviewedRefund(conn, order);
        return "已登记人工确认的退款回执";
    }

    @Override
    public void finishConfirmedShopRefunds() {
        var orders = readOperations("SELECT o.* FROM " + tOperations + " o JOIN " + tOperations
            + " r ON r.trade_id=o.id AND r.kind='CURRENCY' AND r.reference='shop-refund' AND r.state='DONE'"
            + " WHERE o.kind='SHOP_BUY' AND o.state='REFUND_PENDING' ORDER BY o.updated_at LIMIT 64", List.of());
        for (var candidate : orders) {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    var locked = lockShopOrder(conn, candidate.id());
                    if (locked != null && "REFUND_PENDING".equals(locked.state())) finishReviewedRefund(conn, locked);
                    conn.commit();
                } catch (SQLException | RuntimeException exception) {
                    conn.rollback();
                    throw exception;
                } finally { conn.setAutoCommit(true); }
            } catch (SQLException exception) { throw new IllegalStateException("退款预留结算失败", exception); }
        }
    }

    private void finishReviewedRefund(Connection conn, MarketOperation order) throws SQLException {
        var reservation = loadShopReservation(conn, order.id());
        if (reservation == null) throw new SQLException("退款缺少预留快照");
        setReviewState(conn, order.id(), "REFUND_PENDING", "REFUNDED", "退款已确认，已核对库存和限购预留");
        releaseShopReservation(conn, order.player(), reservation);
    }

    private void releaseShopReservation(Connection conn, UUID player, ShopReservation reservation) throws SQLException {
        if (!reservation.quotaEpoch().isEmpty()) {
            execUpdate(conn, "UPDATE " + tShopLimits
                + " SET purchased_count=purchased_count-? WHERE player=? AND shop_id=? AND item_id=? AND quota_epoch=?"
                + " AND reset_time=? AND (reset_time=0 OR reset_time>?) AND purchased_count>=?",
                reservation.quantity(), player.toString(), reservation.shop(), reservation.item(), reservation.quotaEpoch(),
                reservation.quotaReset(), System.currentTimeMillis(), reservation.quantity());
        }
        if (reservation.stockRevision() >= 0
            && lockStockRevision(conn, reservation.shop(), reservation.item()) == reservation.stockRevision()) {
            restoreReservedStock(conn, player, reservation.stockMode(), reservation.stockMaximum(),
                reservation.shop(), reservation.item(), reservation.quantity());
        }
    }

    private @Nullable ShopReservation loadShopReservation(Connection conn, String id) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tShopReservations + " WHERE order_id=?")) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return null;
                return new ShopReservation(rows.getString("shop_id"), rows.getString("item_id"), rows.getInt("quantity"),
                    rows.getString("stock_mode"), rows.getInt("stock_maximum"), rows.getLong("stock_revision"),
                    rows.getLong("quota_reset"), rows.getString("quota_epoch"));
            }
        }
    }

    private @Nullable MarketOperation lockShopOrder(Connection conn, String id) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tOperations
            + " WHERE id=? AND kind='SHOP_BUY' AND reference LIKE 'shop-order:%'" + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? marketOperation(rows) : null; }
        }
    }

    private @Nullable MarketOperation reviewChild(Connection conn, String trade, String kind) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tOperations + " WHERE trade_id=? AND kind=?")) {
            query.setString(1, trade);
            query.setString(2, kind);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return null;
                var child = marketOperation(rows);
                if (rows.next()) throw new SQLException("人工核对发现重复子回执 " + trade);
                return child;
            }
        }
    }

    private void setReviewState(Connection conn, String id, String previous, String state, String note) throws SQLException {
        if (execUpdate(conn, "UPDATE " + tOperations + " SET state=?,note=?,updated_at=? WHERE id=? AND state=?",
            state, note, System.currentTimeMillis(), id, previous) != 1) throw new SQLException("人工核对状态冲突 " + id);
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
        execUpdate(conn,stockRowInsert()+tStockPolicies
            +" (shop_id,item_id,next_at,default_stock) VALUES (?,?,0,NULL)"+stockRowLock("next_at"),shop,item);
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
                lockStockRevision(conn, shop, item);
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
                execUpdate(conn, "UPDATE " + tStockPolicies + " SET revision=revision+1 WHERE shop_id=? AND item_id=?", shop, item);
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
                int claimed=execUpdate(conn,"UPDATE "+tStockPolicies+" SET next_at=?,default_stock=?,revision=revision+1 WHERE shop_id=? AND item_id=? AND next_at<=?",now+intervalMillis,maximum,shop,item,now);
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
    public boolean reserveAuctionBid(AuctionBidOrder bid) {
        AuctionListing expected = bid.expected();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement query = conn.prepareStatement("SELECT 1 FROM " + tListings + auctionBidCondition())) {
                Object[] parameters = auctionBidKey(expected);
                for (int i = 0; i < parameters.length; i++) setParam(query, i + 1, parameters[i]);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) { conn.rollback(); return false; }
                }
                insertOperation(conn, bid.order(), false);
                insertOperation(conn, bid.debit(), false);
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException failure) {
            throw new IllegalStateException("保存竞价扣款意图失败", failure);
        }
    }

    @Override
    public AuctionBidOrder.Outcome finishAuctionBid(AuctionBidOrder bid,
            xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment, long newExpiresAt) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                AuctionBidOrder.Outcome outcome;
                if (payment.uncertain()) {
                    finishRunningOperation(conn, bid.debit(), "REVIEW", "扣款结果不明");
                    finishRunningOperation(conn, bid.order(), "REVIEW", "扣款结果不明，需核对流水");
                    outcome = AuctionBidOrder.Outcome.REVIEW;
                } else if (!payment.success()) {
                    finishRunningOperation(conn, bid.debit(), "FAILED", "未扣款");
                    finishRunningOperation(conn, bid.order(), "FAILED", "竞价未扣款");
                    outcome = AuctionBidOrder.Outcome.DECLINED;
                } else {
                    finishRunningOperation(conn, bid.debit(), "DONE", "扣款已确认");
                    outcome = commitPaidBid(conn, bid, newExpiresAt);
                }
                conn.commit();
                return outcome;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException failure) {
            throw new IllegalStateException("提交竞价和押金退款失败", failure);
        }
    }

    private AuctionBidOrder.Outcome commitPaidBid(Connection conn, AuctionBidOrder bid, long newExpiresAt) throws SQLException {
        AuctionListing expected = bid.expected();
        MarketOperation order = bid.order();
        var parameters = new ArrayList<Object>();
        parameters.add(bid.amount());
        parameters.add(order.player().toString());
        parameters.add(newExpiresAt);
        java.util.Collections.addAll(parameters, auctionBidKey(expected));
        int changed = execUpdate(conn, "UPDATE " + tListings + " SET current_bid=?,highest_bidder=?,"
            + "original_expires_at=CASE WHEN original_expires_at=0 THEN expires_at ELSE original_expires_at END,expires_at=?"
            + auctionBidCondition(), parameters.toArray());
        if (changed != 1) {
            insertBidRefund(conn, order, order.player(), bid.debit().amount(), "auction_bid_race_refund");
            finishRunningOperation(conn, order, "FAILED", "拍卖状态变化，扣款已进入退款队列");
            return AuctionBidOrder.Outcome.REFUND_QUEUED;
        }
        execUpdate(conn, "INSERT INTO " + tBids
            + " (listing_id,bidder,bidder_name,amount,timestamp,previous_expires_at,bid_expires_at) VALUES (?,?,?,?,?,?,?)",
            expected.getId(), order.player().toString(), bid.bidderName(), bid.amount(), System.currentTimeMillis(),
            expected.getExpiresAt(), newExpiresAt);
        UUID previous = expected.getHighestBidder();
        if (previous != null && !previous.equals(order.player()) && expected.getCurrentBid() > 0) {
            insertBidRefund(conn, order, previous, java.math.BigDecimal.valueOf(expected.getCurrentBid()).toPlainString(),
                "auction_outbid_refund");
        }
        finishRunningOperation(conn, order, "DONE", "竞价与押金退款已提交");
        return AuctionBidOrder.Outcome.PLACED;
    }

    private String auctionBidCondition() {
        return " WHERE id=? AND status='ACTIVE' AND type<>'BUY_NOW' AND expires_at>? AND expires_at=? AND current_bid=?"
            + " AND ((highest_bidder IS NULL AND ? IS NULL) OR highest_bidder=?)";
    }

    private Object[] auctionBidKey(AuctionListing expected) {
        String bidder = expected.getHighestBidder() == null ? null : expected.getHighestBidder().toString();
        return new Object[]{expected.getId(), System.currentTimeMillis(), expected.getExpiresAt(),
            expected.getCurrentBid(), bidder, bidder};
    }

    private void finishRunningOperation(Connection conn, MarketOperation operation, String state, String note) throws SQLException {
        long now = System.currentTimeMillis();
        int changed = execUpdate(conn, "UPDATE " + tOperations
            + " SET state=?,note=?,updated_at=CASE WHEN updated_at>=? THEN updated_at+1 ELSE ? END"
            + " WHERE id=? AND trade_id=? AND kind=? AND state='RUNNING' AND owner=?",
            state, note, now, now, operation.id(), operation.tradeId(), operation.kind(), operation.owner());
        if (changed != 1) throw new IllegalStateException("交易流水状态不一致: " + operation.id());
    }

    private void insertBidRefund(Connection conn, MarketOperation order, UUID recipient, String amount, String reason) throws SQLException {
        long now = System.currentTimeMillis();
        insertOperation(conn, new MarketOperation(order.id() + ":refund", order.id(), recipient, "CURRENCY",
            reason, "READY", order.currency(), amount, order.itemData(), "等待退还押金", "", now, now,
            order.itemName(), order.material()), false);
    }

    @Override
    public boolean settleExpiredAuction(AuctionSettlement settlement) {
        var status = settlement.history().buyer() == null ? AuctionListing.ListingStatus.EXPIRED : AuctionListing.ListingStatus.SOLD;
        return finishAuction(settlement, status, false);
    }

    @Override
    public boolean cancelAuction(AuctionSettlement settlement, @Nullable UUID seller, boolean allowBids) {
        var expected = settlement.expected();
        if ((seller != null && !seller.equals(expected.getSeller())) || (!allowBids && expected.getHighestBidder() != null)) {
            return false;
        }
        return finishAuction(settlement, AuctionListing.ListingStatus.CANCELLED, seller == null);
    }

    private boolean finishAuction(AuctionSettlement settlement, AuctionListing.ListingStatus status, boolean forced) {
        return retryTerminalSettlement(() -> commitAuctionSettlement(settlement, status, forced),
            "拍卖终止结算 #" + settlement.expected().getId());
    }

    @FunctionalInterface
    private interface TerminalSettlement {
        boolean commit() throws SQLException;
    }

    private boolean retryTerminalSettlement(TerminalSettlement settlement, String operation) {
        for (int attempt = 0; ; attempt++) {
            try {
                return settlement.commit();
            } catch (SQLException exception) {
                int code = exception.getErrorCode();
                boolean contention = isMysql() ? code == 1213 : (code & 0xff) == 5;
                if (attempt >= 2 || !contention) {
                    throw new IllegalStateException(operation + "失败", exception);
                }
                try {
                    Thread.sleep(10L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(operation + "已中断", interrupted);
                }
            }
        }
    }

    private boolean commitAuctionSettlement(AuctionSettlement settlement, AuctionListing.ListingStatus status,
                                            boolean forced) throws SQLException {
        var expected = settlement.expected();
        var receipt = settlement.receipt();
        String bidder = expected.getHighestBidder() == null ? null : expected.getHighestBidder().toString();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                boolean cancel = status == AuctionListing.ListingStatus.CANCELLED;
                long earliest = cancel && !forced ? now + 1 : Long.MIN_VALUE;
                long latest = cancel ? Long.MAX_VALUE : now;
                int changed = execUpdate(conn, "UPDATE " + tListings
                    + " SET status=? WHERE id=? AND status='ACTIVE' AND expires_at BETWEEN ? AND ? AND expires_at=?"
                    + " AND seller=? AND current_bid=? AND ((highest_bidder IS NULL AND ? IS NULL) OR highest_bidder=?)",
                    status.name(), expected.getId(), earliest, latest, expected.getExpiresAt(),
                    expected.getSeller().toString(), expected.getCurrentBid(), bidder, bidder);
                if (changed != 1) {
                    conn.rollback();
                    return false;
                }
                insertOperation(conn, receipt, false);
                insertHistory(conn, settlement.history(), receipt.id(), receipt.material());
                for (var delivery : settlement.deliveries()) insertOperation(conn, delivery, false);
                var publication = settlement.publication();
                boolean refreshPending = BuildEdition.CROSS_SERVER_INCLUDED && publication != null && publication.refreshPending();
                if (publication != null && (publication.eventPending() || refreshPending)) {
                    execUpdate(conn, "INSERT INTO " + tAuctionPublications + " (id,listing_id,seller,buyer,item,price,"
                        + "currency,tax,buyer_name,event_pending,refresh_pending,lease_until,lease_token) VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'')",
                        publication.id(), publication.listingId(), publication.seller().toString(),
                        publication.buyer() == null ? null : publication.buyer().toString(), publication.item(),
                        publication.price(), publication.currency(), publication.tax(), publication.buyerName(),
                        publication.eventPending() ? 1 : 0, refreshPending ? 1 : 0);
                }
                if (BuildEdition.CROSS_SERVER_INCLUDED && refreshPending) {
                    execUpdate(conn, "UPDATE " + tAuctionRevision + " SET revision=revision+1 WHERE id=1");
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        }
    }

    @Override
    public long auctionSettlementRevision() {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return 0L;
        try (Connection conn = getConnection(); Statement query = conn.createStatement();
             ResultSet rows = query.executeQuery("SELECT revision FROM " + tAuctionRevision + " WHERE id=1")) {
            if (rows.next()) return rows.getLong(1);
            throw new IllegalStateException("拍卖结算版本记录缺失");
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取拍卖结算版本", exception);
        }
    }

    @Override
    public List<AuctionPublication> claimAuctionPublications(String token, long now, int limit, boolean includeRefreshOnly) {
        int claimRefreshOnly = BuildEdition.CROSS_SERVER_INCLUDED && includeRefreshOnly ? 1 : 0;
        var selected = new ArrayList<AuctionPublication>();
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(
            "SELECT p.*,o.created_at AS settled_at FROM " + tAuctionPublications + " p LEFT JOIN "
                + tOperations + " o ON o.id=p.id WHERE p.lease_until<=? AND (?=1 OR p.event_pending<>0)"
                + " ORDER BY p.lease_until,p.id LIMIT ?")) {
            query.setLong(1, now);
            query.setInt(2, claimRefreshOnly);
            query.setInt(3, limit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) selected.add(new AuctionPublication(rows.getString("id"), rows.getLong("listing_id"),
                    UUID.fromString(rows.getString("seller")), parseUuidSafe(rows.getString("buyer"), "publication.buyer"),
                    rows.getString("item"), rows.getDouble("price"), rows.getString("currency"), rows.getDouble("tax"),
                    rows.getString("buyer_name"), rows.getInt("event_pending") != 0, rows.getInt("refresh_pending") != 0,
                    rows.getLong("settled_at")));
            }
            var claimed = new ArrayList<AuctionPublication>();
            for (var entry : selected) {
                if (execUpdate(conn, "UPDATE " + tAuctionPublications + " SET lease_token=?,lease_until=?"
                    + " WHERE id=? AND lease_until<=? AND (?=1 OR event_pending<>0)",
                    token, now + 30_000, entry.id(), now, claimRefreshOnly) == 1) claimed.add(entry);
            }
            return claimed;
        } catch (SQLException exception) { throw new IllegalStateException("认领拍卖通知失败", exception); }
    }

    @Override
    public boolean finishAuctionPublication(String id, String token, boolean event, boolean refresh, long retryAt) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed = execUpdate(conn, "UPDATE " + tAuctionPublications + " SET event_pending=CASE WHEN ?=1 THEN 0 ELSE event_pending END,"
                    + "refresh_pending=CASE WHEN ?=1 THEN 0 ELSE refresh_pending END,lease_until=? WHERE id=? AND lease_token=?",
                    event ? 1 : 0, refresh ? 1 : 0, retryAt, id, token);
                execUpdate(conn, "DELETE FROM " + tAuctionPublications
                    + " WHERE id=? AND lease_token=? AND event_pending=0 AND refresh_pending=0", id, token);
                conn.commit();
                return changed == 1;
            } catch (SQLException | RuntimeException exception) {
                conn.rollback();
                throw exception;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IllegalStateException("写入拍卖通知回执失败", exception); }
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

    @Override
    public String latestAuctionBidderName(long listingId, UUID bidder) {
        String sql = "SELECT bidder_name FROM " + tBids + " WHERE listing_id=? AND bidder=? ORDER BY id DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement query = conn.prepareStatement(sql)) {
            query.setLong(1, listingId);
            query.setString(2, bidder.toString());
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) return rows.getString(1);
            }
        } catch (SQLException exception) { throw new IllegalStateException("成交买家读取失败", exception); }
        return "未知";
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
        try (Connection conn = getConnection()) {
            ensurePlayerShopProfile(conn, owner, ownerName, publish);
        } catch (SQLException failure) {
            throw new IllegalStateException("无法准备玩家店铺档案", failure);
        }
    }

    private void ensurePlayerShopProfile(Connection conn, UUID owner, String ownerName, boolean publish) throws SQLException {
        long now=System.currentTimeMillis();
        execUpdate(conn, (isMysql()?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+tShopProfiles
            +" (owner,owner_name,shop_name,description,announcement,icon_data,version,published,created_at,updated_at) VALUES (?,?,?,?,?,?,1,?,?,?)",
            owner.toString(),ownerName,ownerName+"的小店","","","",publish?1:0,now,now);
        execUpdate(conn, "UPDATE "+tShopProfiles+" SET owner_name=? WHERE owner=? AND owner_name<>?",ownerName,owner.toString(),ownerName);
        if(publish)execUpdate(conn, "UPDATE "+tShopProfiles+" SET published=1,version=version+1,created_at=?,updated_at=? WHERE owner=? AND published=0",now,now,owner.toString());
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

    @Override
    public boolean reservePlayerShopPurchase(PlayerShopOrder plan) {
        return retryTerminalSettlement(() -> reservePlayerShopPurchaseTransaction(plan), "预留玩家商品");
    }

    private boolean reservePlayerShopPurchaseTransaction(PlayerShopOrder plan) throws SQLException {
        var listing = plan.listing();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed = execUpdate(conn, "UPDATE " + tPlayerShopListings + " SET status='RESERVED'"
                    + " WHERE id=? AND seller=? AND status='ACTIVE' AND expires_at>? AND expires_at=? AND price=? AND currency=?"
                    + (plan.storefront() ? " AND EXISTS (SELECT 1 FROM " + tShopProfiles + " WHERE owner=? AND published=1)" : ""),
                    playerShopReservationArgs(listing, plan.storefront()));
                if (changed != 1) { conn.rollback(); return false; }
                insertOperation(conn, plan.order(), false);
                insertOperation(conn, plan.debit(), false);
                for (var delivery : plan.deliveries()) insertOperation(conn, delivery, false);
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally { conn.setAutoCommit(true); }
        }
    }

    private Object[] playerShopReservationArgs(PlayerShopListing listing, boolean storefront) {
        Object[] values = {listing.getId(), listing.getSeller().toString(), System.currentTimeMillis(),
            listing.getExpiresAt(), listing.getPrice(), listing.getCurrency()};
        if (!storefront) return values;
        values = java.util.Arrays.copyOf(values, values.length + 1);
        values[values.length - 1] = listing.getSeller().toString();
        return values;
    }

    @Override
    public void finishPlayerShopPurchase(PlayerShopOrder plan, xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment) {
        retryTerminalSettlement(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (payment.uncertain()) {
                        finishRunningOperation(conn, plan.debit(), "REVIEW", "扣款结果不明");
                        finishRunningOperation(conn, plan.order(), "REVIEW", "商品已预留，等待人工核对扣款");
                    } else {
                        boolean paid = payment.success();
                        finishRunningOperation(conn, plan.debit(), paid ? "DONE" : "FAILED", paid ? "扣款已确认" : "未扣款");
                        finishRunningOperation(conn, plan.order(), paid ? "DONE" : "FAILED",
                            paid ? "成交及交付任务已提交" : "未扣款，已释放商品");
                        settlePlayerShopSale(conn, plan.order(), plan.listing(), plan.tax(), plan.deliveries().size(), paid);
                    }
                    conn.commit();
                    return true;
                } catch (SQLException | RuntimeException failure) {
                    conn.rollback();
                    throw failure;
                } finally { conn.setAutoCommit(true); }
            }
        }, "提交玩家商店成交 " + plan.order().id());
    }

    private void settlePlayerShopSale(Connection conn, MarketOperation order, PlayerShopListing listing,
                                      java.math.BigDecimal tax, int deliveryCount, boolean sold) throws SQLException {
        if (execUpdate(conn, "UPDATE " + tPlayerShopListings
                + " SET status=? WHERE id=? AND seller=? AND status='RESERVED' AND expires_at=? AND price=? AND currency=?",
                sold ? "SOLD" : "ACTIVE", listing.getId(), listing.getSeller().toString(), listing.getExpiresAt(),
                listing.getPrice(), listing.getCurrency()) != 1) {
            throw new SQLException("玩家商品预留状态冲突 #" + listing.getId());
        }
        int changed = execUpdate(conn, "UPDATE " + tOperations + " SET state=?,note=?,updated_at=?"
                + " WHERE trade_id=? AND kind IN ('ITEM','CURRENCY','NOTICE') AND state IN ('PREPARED','REVIEW')",
            sold ? "READY" : "CANCELLED", sold ? "等待交付" : "成交未提交，取消交付", System.currentTimeMillis(), order.id());
        if (changed != deliveryCount || deliveryCount < 1) throw new SQLException("玩家商店交付快照状态冲突 " + order.id());
        if (sold) {
            insertPlayerShopHistory(conn, new MarketRepository.PlayerShopHistory(0, listing.getId(), listing.getSeller(), order.player(),
                listing.getItemData(), listing.getItemDisplayName(), listing.getPrice(), listing.getCurrency(),
                tax.doubleValue(), "PURCHASE", System.currentTimeMillis()), order.id(), order.material());
        }
    }

    private @Nullable MarketOperation playerShopOrder(String id) {
        var rows = readOperations("SELECT * FROM " + tOperations
            + " WHERE id=? AND kind='PSHOP_BUY' AND reference LIKE 'pshop-order:%'", List.of(id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public String reviewPlayerShopPurchase(String id, String action, String audit) {
        if (!List.of("fulfill-paid", "refund-paid", "cancel-unpaid").contains(action)) return "不支持的核对操作";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String message = reviewPlayerShopPurchase(conn, id, action, audit);
                conn.commit();
                return message;
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException failure) {
            throw new IllegalStateException("人工核对玩家商店订单失败", failure);
        }
    }

    private String reviewPlayerShopPurchase(Connection conn, String id, String action, String audit) throws SQLException {
        MarketOperation order;
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tOperations
                + " WHERE id=? AND kind='PSHOP_BUY' AND reference LIKE 'pshop-order:%'" + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return reviewListingFee(conn, id, action, audit);
                order = marketOperation(rows);
            }
        }
        if (!"REVIEW".equals(order.state())) return "当前状态 " + order.state() + "，无需重复核对";
        var debit = reviewChild(conn, order.id(), "DEBIT");
        if (debit == null || !"REVIEW".equals(debit.state())) return "扣款回执还未进入待核对状态";
        String[] reference = order.reference().split(":");
        if (reference.length != 4) throw new SQLException("玩家商店订单预留快照无效 " + id);
        long listingId = Long.parseLong(reference[1]);
        var tax = new java.math.BigDecimal(reference[2]);
        int deliveries = Integer.parseInt(reference[3]);
        PlayerShopListing listing;
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tPlayerShopListings + " WHERE id=? AND status='RESERVED'")) {
            query.setLong(1, listingId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return "商品预留不存在，请核对交易流水";
                listing = mapPlayerShopListing(rows);
            }
        }
        boolean sold = "fulfill-paid".equals(action);
        boolean refund = "refund-paid".equals(action);
        var amount = new java.math.BigDecimal(debit.amount());
        if (refund && amount.signum() <= 0) return "退款金额无效";
        setReviewState(conn, order.id(), "REVIEW", sold ? "DONE" : "CANCELLED", audit);
        setReviewState(conn, debit.id(), "REVIEW", sold || refund ? "DONE" : "FAILED", audit);
        settlePlayerShopSale(conn, order, listing, tax, deliveries, sold);
        if (refund) {
            long now = System.currentTimeMillis();
            insertOperation(conn, new MarketOperation(order.id() + ":refund", order.id(), order.player(), "CURRENCY",
                "pshop-refund", "READY", order.currency(), amount.toPlainString(), "", "等待退款", "", now, now,
                order.itemName(), order.material()), false);
        }
        return sold ? "已确认扣款，成交及交付已入队" : refund ? "已取消订单，退款已入队" : "已确认未扣款，商品已释放";
    }

    private String reviewListingFee(Connection conn, String id, String action, String audit) throws SQLException {
        MarketOperation order;
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM " + tOperations
                + " WHERE id=? AND ((kind='PSHOP_LIST' AND reference LIKE 'pshop-listing:%')"
                + " OR (kind='AUCTION_LIST' AND reference LIKE 'auction-listing:%'))" + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return "玩家商店订单不存在";
                order = marketOperation(rows);
            }
        }
        if (!"REVIEW".equals(order.state())) return "当前状态 " + order.state() + "，无法核对上架费";
        var debit = reviewChild(conn, id, "DEBIT");
        if (debit == null || !"REVIEW".equals(debit.state())) return "上架费回执还未进入待核对状态";
        boolean refund = "refund-paid".equals(action);
        if (!refund && !"cancel-unpaid".equals(action)) return "物品尚未扣除，请选择退款或确认未扣上架费";
        if (refund && new java.math.BigDecimal(debit.amount()).signum() <= 0) return "退款金额无效";
        String[] reference = order.reference().split(":");
        if (reference.length != 4) throw new SQLException("上架暂存快照无效 " + id);
        setReviewState(conn, order.id(), "REVIEW", "CANCELLED", audit);
        setReviewState(conn, debit.id(), "REVIEW", refund ? "DONE" : "FAILED", audit);
        var kind = "AUCTION_LIST".equals(order.kind()) ? MarketListingDraft.Kind.AUCTION : MarketListingDraft.Kind.PLAYER_SHOP;
        cancelListingDraftRow(conn, kind, Long.parseLong(reference[1]), order.player());
        if (refund) {
            long now = System.currentTimeMillis();
            insertOperation(conn, new MarketOperation(id + ":refund", id, order.player(), "CURRENCY",
                kind.prefix() + "-fee-refund", "READY", debit.currency(), debit.amount(), "",
                "等待退还上架费", "", now, now, order.itemName(), order.material()), false);
        }
        return refund ? "暂存已释放，上架费退款已入队" : "已确认未扣上架费，暂存已释放";
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
        try (Connection conn = getConnection()) {
            noteMarketPlayer(conn, player, name);
        } catch (SQLException failure) {
            throw new IllegalStateException("市场玩家档案写入失败", failure);
        }
    }

    private void noteMarketPlayer(Connection conn, UUID player, String name) throws SQLException {
        execUpdate(conn, "INSERT INTO " + tMarketPlayers + " (player,name,updated_at) VALUES (?,?,?)"
            + (isMysql() ? " ON DUPLICATE KEY UPDATE name=VALUES(name),updated_at=VALUES(updated_at)"
                : " ON CONFLICT(player) DO UPDATE SET name=excluded.name,updated_at=excluded.updated_at"),
            player.toString(), name, System.currentTimeMillis());
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
    private String exchangeOperationsTable;

    public void includeExchangeOperations(String table) {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("兑换账本表名无效");
        exchangeOperationsTable = table;
    }

    public void includeExchangeOperationsIfPresent(String table) throws SQLException {
        try (Connection connection = getConnection();
             ResultSet rows = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (rows.next()) {
                if (table.equals(rows.getString("TABLE_NAME"))) {
                    includeExchangeOperations(table);
                    return;
                }
            }
        }
    }

    private String auditSource(String scope){
        String history=auditHistorySql(tHistory,"history:a:",false)+" UNION ALL "+auditHistorySql(tPlayerShopHistory,"history:p:",true);
        if ("history".equals(scope)) return history;
        String operations = auditOperationsSql();
        if (exchangeOperationsTable != null) {
            operations += " UNION ALL SELECT " + auditConcat("'op:exchange:'", "e.id")
                + " AS id,e.id AS trade_id,'OPERATION' AS source,'NPC_EXCHANGE' AS kind,'NPC_EXCHANGE' AS business_type,"
                + "e.state,e.player_uuid AS player,NULL AS other_player,COALESCE(p.name,'') AS player_name,"
                + "'' AS other_name,'NPC 兑换' AS item_name,'' AS material,'' AS currency,'' AS amount,"
                + "'完整凭证可由兑换管理员导出' AS note,'' AS item_data,e.updated_at AS created_at,e.updated_at"
                + " FROM " + exchangeOperationsTable + " e LEFT JOIN " + tMarketPlayers + " p ON p.player=e.player_uuid";
        }
        return "operations".equals(scope) ? operations : operations + " UNION ALL " + history;
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
            +"WHEN business_type LIKE 'SHOP%' THEN 'SHOP' WHEN business_type LIKE 'RECYCLE%' THEN 'RECYCLE' WHEN business_type='NPC_EXCHANGE' THEN 'EXCHANGE' ELSE 'OTHER' END";
        if(!"ALL".equals(filter.type())){where.append(" AND (").append(category).append(")=?");args.add(filter.type());}
        if("PENDING".equals(filter.state()))where.append(" AND state IN ('READY','PREPARED','RUNNING','ITEM_SAVING') AND kind IN ('ITEM','CURRENCY','COMMAND')");
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
