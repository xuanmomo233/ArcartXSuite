package xuanmo.arcartxsuite.market.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;

class RedisMarketCacheTest {
    @Test
    void lateAuctionCacheWriteCannotRestoreAnInvalidatedPage() throws Exception {
        var cache = new RedisMarketCache(MarketModuleConfiguration.RedisConfiguration.disabled(), Logger.getAnonymousLogger());
        var jedis = mock(redis.clients.jedis.Jedis.class);
        var pool = mock(redis.clients.jedis.JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        var entries = new java.util.HashMap<String, String>();
        when(jedis.get(anyString())).thenAnswer(call -> entries.get(call.getArgument(0)));
        when(jedis.setex(anyString(), anyLong(), anyString())).thenAnswer(call -> {
            entries.put(call.getArgument(0), call.getArgument(2));
            return "OK";
        });
        var poolField = RedisMarketCache.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        poolField.set(cache, pool);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String baseFirst = "market:listings:" + first + ":page";
        String baseSecond = "market:listings:" + second + ":page";
        String stale = cache.versionedListPageKey(first, baseFirst);
        String shared = cache.versionedListPageKey(second, baseSecond);
        cache.invalidateByPrefix("market:listings:" + first + ":");
        cache.cacheListings(stale, "old");
        String fresh = cache.versionedListPageKey(first, baseFirst);
        assertNotEquals(stale, fresh);
        assertNull(cache.getCached(fresh));
        assertEquals(shared, cache.versionedListPageKey(second, baseSecond));
        String shopBase = "market:pshop:" + first + ":page";
        String oldShop = cache.versionedListPageKey(first, shopBase);
        cache.invalidateByPrefix("market:pshop:");
        cache.cacheListings(oldShop, "old");
        assertNull(cache.getCached(cache.versionedListPageKey(first, shopBase)));
        assertEquals(fresh, cache.versionedListPageKey(first, baseFirst));
        cache.invalidateByPrefix("market:listings:");
        assertNotEquals(shared, cache.versionedListPageKey(second, baseSecond));
        verify(jedis, never()).scan(anyString(), any(redis.clients.jedis.params.ScanParams.class));
    }
}
