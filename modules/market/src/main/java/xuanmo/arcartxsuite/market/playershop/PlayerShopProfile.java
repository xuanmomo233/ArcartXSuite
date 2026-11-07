package xuanmo.arcartxsuite.market.playershop;

import java.util.UUID;

/** A shop belongs to its owner's UUID; branding never changes listing ownership. */
public record PlayerShopProfile(UUID owner,String ownerName,String shopName,String description,
                                String announcement,String iconData,long version,boolean published,
                                long createdAt,long updatedAt) {}
