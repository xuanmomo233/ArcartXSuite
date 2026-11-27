package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RedisConfiguration;

/** The free edition loads listing pages from its normal repository. */
public class RedisMarketCache {
    public RedisMarketCache(RedisConfiguration config, Logger logger) {}

    public void initialize() {}

    public void shutdown() {}

    public boolean isAvailable() { return false; }

    public void cacheListings(String key, String json) {}

    public @Nullable String getCached(String key) { return null; }

    public @Nullable String versionedListPageKey(UUID viewer, String baseKey) { return null; }

    public void invalidateByPrefix(String prefix) {}
}
