package xuanmo.arcartxsuite.module;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleEventBusSubscriberTest {
    @Test void authenticationFactWaitsForEveryDurableConsumerAndRetriesFailures() {
        var bus=new SimpleEventBus(Logger.getAnonymousLogger());
        String topic="axs.auth.presence_day_started";
        assertFalse(bus.publishConfirmed(topic,null,java.util.Map.of("event_id","day")).toCompletableFuture().join());
        var stored=new java.util.concurrent.CompletableFuture<Void>();
        bus.subscribeConfirmed("battlepass/daily",topic,event->stored);
        String failing=bus.subscribeConfirmed("external/consumer",topic,event->java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Storage unavailable")));
        var receipt=bus.publishConfirmed(topic,null,java.util.Map.of("event_id","day")).toCompletableFuture();
        assertFalse(receipt.isDone());
        stored.complete(null);
        assertFalse(receipt.join());
        bus.unsubscribe(failing);
        assertTrue(bus.publishConfirmed(topic,null,java.util.Map.of("event_id","day")).toCompletableFuture().join());
    }
    @Test void legacyVoidHandlerCannotAcknowledgeAuthenticationPersistence() {
        var bus=new SimpleEventBus(Logger.getAnonymousLogger());
        String topic="axs.auth.profile_first_admitted";
        bus.subscribe(topic,event->{});
        assertFalse(bus.publishConfirmed(topic,null,java.util.Map.of("event_id","first")).toCompletableFuture().join());
    }
    @Test void distinguishesPublishersFromActualConsumersAndHonorsUnsubscribe() {
        var bus=new SimpleEventBus(Logger.getAnonymousLogger());
        bus.registerPublisher("axs.auth.presence_day_started");
        assertFalse(bus.hasSubscribers("axs.auth.presence_day_started"));
        String id=bus.subscribe("axs.auth.*",ignored->{});
        assertTrue(bus.hasSubscribers("axs.auth.presence_day_started"));
        assertFalse(bus.hasSubscribers("axs.other.event"));
        bus.unsubscribe(id);
        assertFalse(bus.hasSubscribers("axs.auth.presence_day_started"));
    }
}
