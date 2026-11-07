package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotResult;

class DeliveryPolicyTest {
    @Test void rollingBudgetDoesNotResetAtAnArbitraryMinuteBoundary() {
        var limiter = new SlidingWindowRateLimiter<String>(2, 60000, 2);
        assertTrue(limiter.allow("a", 59999)); assertTrue(limiter.allow("a", 60001));
        assertFalse(limiter.allow("a", 60002));
        assertTrue(limiter.allow("b", 60002)); assertFalse(limiter.allow("c", 60002));
        assertTrue(limiter.allow("a", 120000));
        assertTrue(limiter.allow("c", 120002));
    }
    @Test void aggregatePreservesPartialSuccessAndAnyUnknownOutcome() {
        var result = QQBotMessageGateway.aggregate(List.of(
            CompletableFuture.completedFuture(OneBotResult.success(new JsonObject())),
            CompletableFuture.completedFuture(OneBotResult.failure(-1, "rejected")),
            CompletableFuture.completedFuture(OneBotResult.failure(-2, "unknown")))).join();
        assertFalse(result.success()); assertEquals(-2, result.retcode());
        assertEquals(1, result.data().get("sentGroups").getAsInt());
        assertEquals(3, result.data().get("totalGroups").getAsInt());
    }
}
