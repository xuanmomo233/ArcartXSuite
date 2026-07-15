package xuanmo.arcartxsuite.market.auction;

import java.util.UUID;

/**
 * 拍卖行上架物品。
 */
public class AuctionListing {

    private long id;
    private final UUID seller;
    private final String sellerName;
    private final String itemData;
    private final String itemDisplayName;
    private String message;
    private final String category;
    private final double buyNowPrice;
    private final double startingBid;
    private double currentBid;
    private UUID highestBidder;
    private final String currency;
    private final ListingType type;
    private ListingStatus status;
    private final long createdAt;
    private final long expiresAt;

    public AuctionListing(long id, UUID seller, String sellerName, String itemData,
                          String itemDisplayName, String category, double buyNowPrice,
                          double startingBid, double currentBid, UUID highestBidder,
                          String currency, ListingType type, ListingStatus status,
                          long createdAt, long expiresAt) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.itemDisplayName = itemDisplayName;
        this.message = "";
        this.category = category;
        this.buyNowPrice = buyNowPrice;
        this.startingBid = startingBid;
        this.currentBid = currentBid;
        this.highestBidder = highestBidder;
        this.currency = currency;
        this.type = type;
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
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }
    public String getCategory() { return category; }
    public double getBuyNowPrice() { return buyNowPrice; }
    public double getStartingBid() { return startingBid; }
    public double getCurrentBid() { return currentBid; }
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
    public UUID getHighestBidder() { return highestBidder; }
    public void setHighestBidder(UUID highestBidder) { this.highestBidder = highestBidder; }
    public String getCurrency() { return currency; }
    public ListingType getType() { return type; }
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

    public enum ListingType {
        BUY_NOW, AUCTION, BOTH
    }

    public enum ListingStatus {
        ACTIVE, SOLD, EXPIRED, CANCELLED
    }
}
