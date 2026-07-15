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
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.market.shop.ShopLimitRecord;

/**
 * MySQL / SQLite 双模式市场数据仓库。
 */
public class JdbcMarketRepository extends AbstractModuleRepository implements MarketRepository {

    private final StorageConfiguration config;
    private final Logger logger;
    private boolean sqlite;

    private String tListings;
    private String tBids;
    private String tHistory;
    private String tFavorites;
    private String tShopLimits;
    private String tRecycleStats;
    private String tPending;
    private String tGlobalStock;
    private String tPlayerStock;

    public JdbcMarketRepository(StorageConfiguration config, Logger logger, File dataFolder) {
        super("AXS-Market", dataFolder, config.toDescriptor(), logger);
        this.config = config;
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        sqlite = config.isSqlite();
        String prefix = config.tablePrefix();
        tListings = prefix + "listings";
        tBids = prefix + "bids";
        tHistory = prefix + "history";
        tFavorites = prefix + "favorites";
        tShopLimits = prefix + "shop_limits";
        tRecycleStats = prefix + "recycle_stats";
        tPending = prefix + "pending_deliveries";
        tGlobalStock = prefix + "shop_global_stock";
        tPlayerStock = prefix + "shop_player_stock";
        createTables(conn);
        logger.info("[Market] " + (sqlite ? "SQLite" : "MySQL") + " å­å¨å·²åå§åï¼è¡¨åç¼: " + prefix);
    }

    @Override
    protected List<String> playerDataTables() {
        // å¸åºæ¨¡åä½¿ç¨å¤ç§åå(seller/buyer/player/bidder)ï¼æ æ³ç»ä¸æååå é¤
        return List.of();
    }

    @Override
    protected List<String> allTables() {
        String prefix = config.tablePrefix();
        return List.of(
            prefix + "listings",
            prefix + "bids",
            prefix + "history",
            prefix + "favorites",
            prefix + "shop_limits",
            prefix + "recycle_stats",
            prefix + "pending_deliveries",
            prefix + "shop_global_stock",
            prefix + "shop_player_stock"
        );
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (sqlite) {
                createTablesSqlite(stmt);
            } else {
                createTablesMysql(stmt);
            }
        }
    }

    private void createTablesSqlite(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tListings + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "seller TEXT NOT NULL,"
            + "seller_name TEXT NOT NULL,"
            + "item_data TEXT NOT NULL,"
            + "item_display_name TEXT NOT NULL,"
            + "category TEXT NOT NULL DEFAULT 'other',"
            + "buy_now_price REAL NOT NULL DEFAULT 0,"
            + "starting_bid REAL NOT NULL DEFAULT 0,"
            + "current_bid REAL NOT NULL DEFAULT 0,"
            + "highest_bidder TEXT,"
            + "currency TEXT NOT NULL DEFAULT 'money',"
            + "type TEXT NOT NULL DEFAULT 'BUY_NOW',"
            + "status TEXT NOT NULL DEFAULT 'ACTIVE',"
            + "created_at INTEGER NOT NULL,"
            + "expires_at INTEGER NOT NULL)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_status ON " + tListings + "(status)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_seller ON " + tListings + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_category ON " + tListings + "(category)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_market_expires ON " + tListings + "(expires_at)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tBids + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "listing_id INTEGER NOT NULL,"
            + "bidder TEXT NOT NULL,"
            + "bidder_name TEXT NOT NULL,"
            + "amount REAL NOT NULL,"
            + "timestamp INTEGER NOT NULL)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_listing ON " + tBids + "(listing_id)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_bidder ON " + tBids + "(bidder)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tHistory + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "listing_id INTEGER NOT NULL,"
            + "seller TEXT NOT NULL,"
            + "buyer TEXT,"
            + "item_data TEXT NOT NULL,"
            + "item_display_name TEXT NOT NULL,"
            + "price REAL NOT NULL,"
            + "currency TEXT NOT NULL,"
            + "tax_amount REAL NOT NULL DEFAULT 0,"
            + "transaction_type TEXT NOT NULL,"
            + "timestamp INTEGER NOT NULL)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_seller ON " + tHistory + "(seller)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_buyer ON " + tHistory + "(buyer)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tFavorites + " ("
            + "player TEXT NOT NULL,"
            + "listing_id INTEGER NOT NULL,"
            + "PRIMARY KEY (player, listing_id))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tShopLimits + " ("
            + "player TEXT NOT NULL,"
            + "shop_id TEXT NOT NULL,"
            + "item_id TEXT NOT NULL,"
            + "purchased_count INTEGER NOT NULL DEFAULT 0,"
            + "last_purchase_time INTEGER NOT NULL,"
            + "reset_time INTEGER NOT NULL,"
            + "PRIMARY KEY (player, shop_id, item_id))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecycleStats + " ("
            + "player TEXT NOT NULL,"
            + "currency TEXT NOT NULL,"
            + "total_amount REAL NOT NULL DEFAULT 0,"
            + "total_items INTEGER NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (player, currency))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "player TEXT NOT NULL,"
            + "type TEXT NOT NULL,"
            + "item_data TEXT,"
            + "currency TEXT,"
            + "amount REAL NOT NULL DEFAULT 0,"
            + "reason TEXT,"
            + "created_at INTEGER NOT NULL)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON " + tPending + "(player)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobalStock + " ("
            + "shop_id TEXT NOT NULL,"
            + "item_id TEXT NOT NULL,"
            + "remaining INTEGER NOT NULL,"
            + "PRIMARY KEY (shop_id, item_id))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayerStock + " ("
            + "player TEXT NOT NULL,"
            + "shop_id TEXT NOT NULL,"
            + "item_id TEXT NOT NULL,"
            + "remaining INTEGER NOT NULL,"
            + "PRIMARY KEY (player, shop_id, item_id))");
    }

    private void createTablesMysql(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tListings + " ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "seller CHAR(36) NOT NULL,"
            + "seller_name VARCHAR(64) NOT NULL,"
            + "item_data MEDIUMTEXT NOT NULL,"
            + "item_display_name VARCHAR(256) NOT NULL,"
            + "category VARCHAR(64) NOT NULL DEFAULT 'other',"
            + "buy_now_price DOUBLE NOT NULL DEFAULT 0,"
            + "starting_bid DOUBLE NOT NULL DEFAULT 0,"
            + "current_bid DOUBLE NOT NULL DEFAULT 0,"
            + "highest_bidder CHAR(36),"
            + "currency VARCHAR(32) NOT NULL DEFAULT 'money',"
            + "type VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW',"
            + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',"
            + "created_at BIGINT NOT NULL,"
            + "expires_at BIGINT NOT NULL,"
            + "INDEX idx_status (status),"
            + "INDEX idx_seller (seller),"
            + "INDEX idx_category (category),"
            + "INDEX idx_expires (expires_at)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tBids + " ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "listing_id BIGINT NOT NULL,"
            + "bidder CHAR(36) NOT NULL,"
            + "bidder_name VARCHAR(64) NOT NULL,"
            + "amount DOUBLE NOT NULL,"
            + "timestamp BIGINT NOT NULL,"
            + "INDEX idx_listing (listing_id),"
            + "INDEX idx_bidder (bidder)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tHistory + " ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "listing_id BIGINT NOT NULL,"
            + "seller CHAR(36) NOT NULL,"
            + "buyer CHAR(36),"
            + "item_data MEDIUMTEXT NOT NULL,"
            + "item_display_name VARCHAR(256) NOT NULL,"
            + "price DOUBLE NOT NULL,"
            + "currency VARCHAR(32) NOT NULL,"
            + "tax_amount DOUBLE NOT NULL DEFAULT 0,"
            + "transaction_type VARCHAR(16) NOT NULL,"
            + "timestamp BIGINT NOT NULL,"
            + "INDEX idx_seller (seller),"
            + "INDEX idx_buyer (buyer)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tFavorites + " ("
            + "player CHAR(36) NOT NULL,"
            + "listing_id BIGINT NOT NULL,"
            + "PRIMARY KEY (player, listing_id),"
            + "INDEX idx_player (player)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tShopLimits + " ("
            + "player CHAR(36) NOT NULL,"
            + "shop_id VARCHAR(64) NOT NULL,"
            + "item_id VARCHAR(64) NOT NULL,"
            + "purchased_count INT NOT NULL DEFAULT 0,"
            + "last_purchase_time BIGINT NOT NULL,"
            + "reset_time BIGINT NOT NULL,"
            + "PRIMARY KEY (player, shop_id, item_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecycleStats + " ("
            + "player CHAR(36) NOT NULL,"
            + "currency VARCHAR(32) NOT NULL,"
            + "total_amount DOUBLE NOT NULL DEFAULT 0,"
            + "total_items INT NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (player, currency)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "player CHAR(36) NOT NULL,"
            + "type VARCHAR(16) NOT NULL,"
            + "item_data MEDIUMTEXT,"
            + "currency VARCHAR(32),"
            + "amount DOUBLE NOT NULL DEFAULT 0,"
            + "reason VARCHAR(128),"
            + "created_at BIGINT NOT NULL,"
            + "INDEX idx_pending_player (player)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobalStock + " ("
            + "shop_id VARCHAR(64) NOT NULL,"
            + "item_id VARCHAR(64) NOT NULL,"
            + "remaining INT NOT NULL,"
            + "PRIMARY KEY (shop_id, item_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayerStock + " ("
            + "player CHAR(36) NOT NULL,"
            + "shop_id VARCHAR(64) NOT NULL,"
            + "item_id VARCHAR(64) NOT NULL,"
            + "remaining INT NOT NULL,"
            + "PRIMARY KEY (player, shop_id, item_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    // ─── 拍卖行 CRUD ────────────────────────────────────────

    @Override
    public boolean insertListing(AuctionListing listing) {
        String sql = "INSERT INTO " + tListings
            + " (seller, seller_name, item_data, item_display_name, category, buy_now_price, starting_bid, current_bid, highest_bidder, currency, type, status, created_at, expires_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, listing.getSeller().toString());
            ps.setString(2, listing.getSellerName());
            ps.setString(3, listing.getItemData());
            ps.setString(4, listing.getItemDisplayName());
            ps.setString(5, listing.getCategory());
            ps.setDouble(6, listing.getBuyNowPrice());
            ps.setDouble(7, listing.getStartingBid());
            ps.setDouble(8, listing.getCurrentBid());
            ps.setString(9, listing.getHighestBidder() == null ? null : listing.getHighestBidder().toString());
            ps.setString(10, listing.getCurrency());
            ps.setString(11, listing.getType().name());
            ps.setString(12, listing.getStatus().name());
            ps.setLong(13, listing.getCreatedAt());
            ps.setLong(14, listing.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) listing.setId(rs.getLong(1));
            }
            return listing.getId() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Market] æå¥æåç©åå¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ´æ°æåç©åå¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æåç¶æ CAS å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] bid CAS update failed", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢æåç©åå¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æåç±»æ¥è¯¢å¤±è´¥", e);
        }
        return results;
    }

    @Override
    public List<AuctionListing> searchListings(String keyword, int offset, int limit) {
        String sql = "SELECT * FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ? AND item_display_name LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, "%" + keyword + "%");
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Market] æç´¢æåç©åå¤±è´¥", e);
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
            + " WHERE seller=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
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
        String sql = "SELECT COUNT(*) FROM " + tListings
            + " WHERE status='ACTIVE' AND expires_at > ?"
            + " AND item_display_name LIKE ?";
        return countQuery(sql, System.currentTimeMillis(), "%" + keyword + "%");
    }
    @Override
    public List<AuctionListing> getListingsBySeller(UUID seller) {
        String sql = "SELECT * FROM " + tListings + " WHERE seller=? ORDER BY created_at DESC";
        List<AuctionListing> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Market] æåå®¶æ¥è¯¢å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] ç»è®¡åå®¶ä¸æ¶æ°å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æå¥ç«ä»·å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢ç«ä»·å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢æé«ç«ä»·å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æå¥äº¤æåå²å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢äº¤æåå²å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] history count failed", e);
            return 0;
        }
    }
    @Override
    public void addFavorite(UUID player, long listingId) {
        String sql = sqlite
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢æ¶èå¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢æ¶èç¶æå¤±è´¥", e);
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
                        UUID.fromString(rs.getString("player")),
                        rs.getString("shop_id"),
                        rs.getString("item_id"),
                        rs.getInt("purchased_count"),
                        rs.getLong("last_purchase_time"),
                        rs.getLong("reset_time")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢éè´­è®°å½å¤±è´¥", e);
        }
        return null;
    }

    @Override
    public void upsertShopLimit(ShopLimitRecord record) {
        String sql = sqlite
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
            logger.log(Level.SEVERE, "[Market] æ´æ°éè´­è®°å½å¤±è´¥", e);
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
        String sql = sqlite
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
            logger.log(Level.SEVERE, "[Market] æ´æ°åæ¶ç»è®¡å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢åæ¶ç»è®¡å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢å¨å±åºå­å¤±è´¥", e);
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
                String insertSql = sqlite
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
            logger.log(Level.SEVERE, "[Market] æ£åå¨å±åºå­å¤±è´¥", e);
            return false;
        }
    }

    @Override
    public void restoreGlobalShopStock(String shopId, String itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        exec("UPDATE " + tGlobalStock + " SET remaining = remaining + ? WHERE shop_id=? AND item_id=?",
            amount, shopId, itemId);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢ç©å®¶åºå­å¤±è´¥", e);
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
                String insertSql = sqlite
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
            logger.log(Level.SEVERE, "[Market] æ£åç©å®¶åºå­å¤±è´¥", e);
            return false;
        }
    }

    @Override
    public void restorePlayerShopStock(UUID player, String shopId, String itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        exec("UPDATE " + tPlayerStock + " SET remaining = remaining + ? WHERE player=? AND shop_id=? AND item_id=?",
            amount, player.toString(), shopId, itemId);
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢å¾åæ¾è®°å½å¤±è´¥", e);
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
            UUID.fromString(rs.getString("player")),
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
            logger.log(Level.SEVERE, "[Market] æ¥è¯¢æååè¡¨å¤±è´¥", e);
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
            logger.log(Level.SEVERE, "[Market] æ§è¡SQLå¤±è´¥: " + sql, e);
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
            logger.log(Level.SEVERE, "[Market] ç»è®¡æ¥è¯¢å¤±è´¥", e);
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

    private AuctionListing mapListing(ResultSet rs) throws SQLException {
        String bidderStr = rs.getString("highest_bidder");
        UUID bidder = bidderStr == null || bidderStr.isEmpty() ? null : UUID.fromString(bidderStr);
        return new AuctionListing(
            rs.getLong("id"),
            UUID.fromString(rs.getString("seller")),
            rs.getString("seller_name"),
            rs.getString("item_data"),
            rs.getString("item_display_name"),
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
            UUID.fromString(rs.getString("bidder")),
            rs.getString("bidder_name"),
            rs.getDouble("amount"),
            rs.getLong("timestamp")
        );
    }

    private AuctionHistory mapHistory(ResultSet rs) throws SQLException {
        String buyerStr = rs.getString("buyer");
        UUID buyer = buyerStr == null || buyerStr.isEmpty() ? null : UUID.fromString(buyerStr);
        return new AuctionHistory(
            rs.getLong("id"),
            rs.getLong("listing_id"),
            UUID.fromString(rs.getString("seller")),
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
