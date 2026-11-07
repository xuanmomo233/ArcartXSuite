package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;

public record MarketAuditRow(String id,String tradeId,String source,String kind,String businessType,String state,
                              UUID player,UUID otherPlayer,String playerName,String otherName,String itemName,String material,
                              String currency,String amount,String note,String itemData,long createdAt,long updatedAt) {}
