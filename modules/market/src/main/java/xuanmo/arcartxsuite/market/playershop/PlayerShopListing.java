package xuanmo.arcartxsuite.market.playershop;

import java.util.UUID;

/**
 * 玩家商店上架物品。
 * <p>
 * 与拍卖行 {@link xuanmo.arcartxsuite.market.auction.AuctionListing} 类似，
 * 但仅支持固定价格直接购买，不支持竞价。
 */
public class PlayerShopListing {

    private long id;
    private final UUID seller;
    private final String sellerName;
    private final String itemData;
    private final String itemDisplayName;
    private final String pinyin;
    private final String initials;
    private String message;
    private final double price;
    private final String currency;
    private ListingStatus status;
    private final long createdAt;
    private final long expiresAt;

    public PlayerShopListing(long id, UUID seller, String sellerName, String itemData,
                             String itemDisplayName, String pinyin, String initials,
                             String message, double price, String currency,
                             ListingStatus status, long createdAt, long expiresAt) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.itemDisplayName = itemDisplayName;
        this.pinyin = pinyin == null ? "" : pinyin;
        this.initials = initials == null ? "" : initials;
        this.message = message == null ? "" : message;
        this.price = price;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public UUID getSeller() { return seller; }
    public String getSellerName() { return sellerName; }
    public String getItemData() { return itemData; }
    public String getItemDisplayName() { return itemDisplayName; }
    public String getPinyin() { return pinyin; }
    public String getInitials() { return initials; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }
    public double getPrice() { return price; }
    public String getCurrency() { return currency; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE && !isExpired();
    }

    public enum ListingStatus {
        ACTIVE, SOLD, EXPIRED, CANCELLED
    }
}
