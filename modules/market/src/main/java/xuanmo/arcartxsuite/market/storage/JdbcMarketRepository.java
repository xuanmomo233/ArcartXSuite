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
        createTables(conn);
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
            prefix + "listings",
            prefix + "bids",
            prefix + "history",
            prefix + "favorites",
            prefix + "shop_limits",
            prefix + "recycle_stats",
            prefix + "pending_deliveries",
            prefix + "shop_global_stock",
            prefix + "shop_player_stock",
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
            + " (seller, seller_name, item_data, item_display_name, pinyin, initials, category, buy_now_price, starting_bid, current_bid, highest_bidder, currency, type, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, listing.getSeller().toString());
            ps.setString(2, listing.getSellerName());
            ps.setString(3, listing.getItemData());
            ps.setString(4, listing.getItemDisplayName());
            ps.setString(5, listing.getPinyin());
            ps.setString(6, listing.getInitials());
            ps.setString(7, listing.getCategory());
            ps.setDouble(8, listing.getBuyNowPrice());
            ps.setDouble(9, listing.getStartingBid());
            ps.setDouble(10, listing.getCurrentBid());
            ps.setString(11, listing.getHighestBidder() == null ? null : listing.getHighestBidder().toString());
            ps.setString(12, listing.getCurrency());
            ps.setString(13, listing.getType().name());
            ps.setString(14, listing.getStatus().name());
            ps.setLong(15, listing.getCreatedAt());
            ps.setLong(16, listing.getExpiresAt());
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
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)";
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
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入交易历史失败", e);
        }
    }

    @Override
    public List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit) {
        List<AuctionHistory> results = new ArrayList<>();
        String sql = "SELECT * FROM " + tHistory
            + " WHERE seller=? OR buyer=? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String uuid = player.toString();
            ps.setString(1, uuid);
            ps.setString(2, uuid);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
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
        String sql = "SELECT COUNT(*) FROM " + tHistory
            + " WHERE seller=? OR buyer=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String uuid = player.toString();
            ps.setString(1, uuid);
            ps.setString(2, uuid);
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
        exec(sql, player.toString(), listingId);
    }

    @Override
    public void removeFavorite(UUID player, long listingId) {
        exec("DELETE FROM " + tFavorites + " WHERE player=? AND listing_id=?",
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
                    insert.setInt(4, defaultMax);
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
        exec("INSERT INTO " + tPending
                + " (player, type, item_data, currency, amount, reason, created_at) VALUES (?,?,?,?,?,?,?)",
            player.toString(), PendingDelivery.TYPE_ITEM, itemData, "", 0.0D,
            reason == null ? "" : reason, System.currentTimeMillis());
    }

    @Override
    public void addPendingCurrency(UUID player, String currency, double amount, String reason) {
        exec("INSERT INTO " + tPending
                + " (player, type, item_data, currency, amount, reason, created_at) VALUES (?,?,?,?,?,?,?)",
            player.toString(), PendingDelivery.TYPE_CURRENCY, "", currency == null ? "" : currency, amount,
            reason == null ? "" : reason, System.currentTimeMillis());
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
        return new AuctionListing(
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
    }

    private AuctionBid mapBid(ResultSet rs) throws SQLException {
        return new AuctionBid(
            rs.getLong("id"),
            rs.getLong("listing_id"),
            parseUuidSafe(rs.getString("bidder"), "bid.bidder"),
            rs.getString("bidder_name"),
            rs.getDouble("amount"),
            rs.getLong("timestamp")
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
            + " (listing_id, seller, buyer, item_data, item_display_name, price, currency, tax_amount, transaction_type, timestamp)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)";
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
}
