package xuanmo.arcartxsuite.auth.center;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalRequestBudgetTest {
    @Test void startFloodCannotSpendAnotherSourceOrCallbackBudget() {
        var now=new AtomicLong(); var budget=new ExternalRequestBudget(now::get);
        for(int n=0;n<60;n++) assertTrue(budget.acquire("192.0.2.1","start",60));
        assertFalse(budget.acquire("192.0.2.1","start",60));
        assertTrue(budget.acquire("192.0.2.2","start",60));
        assertTrue(budget.acquire("192.0.2.1","complete",60));
        now.set(60_000_000_000L);
        assertTrue(budget.acquire("192.0.2.1","start",60));
    }
    @Test void proxyHeaderCannotTriggerDnsOrAcceptAForwardingChain() {
        assertEquals("192.0.2.1",ExternalRequestBudget.source("192.0.2.1"));
        for(String invalid:new String[]{"example.com","127.0.0.1,192.0.2.1","999.0.0.1","192.000.2.1","fe80::1%eth0"})
            assertThrows(SecurityException.class,()->ExternalRequestBudget.source(invalid));
        assertThrows(SecurityException.class,()->ExternalRequestBudget.source(null));
    }
}
