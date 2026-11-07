package xuanmo.arcartxsuite.market.recycle;

import java.util.UUID;

public record RecycleProtectionRule(String id,UUID player,String kind,String matchKey,String material,
                                     String displayName,String itemData,long createdAt) {}
