package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;

public record MarketAuditFilter(UUID player,String item,long from,long until,String type,String state,String scope,int page,String group) {}
