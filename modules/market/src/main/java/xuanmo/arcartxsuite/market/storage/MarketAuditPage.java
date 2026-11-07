package xuanmo.arcartxsuite.market.storage;

import java.util.List;

public record MarketAuditPage(List<MarketAuditRow> rows,int page,int pages,long total) {}
