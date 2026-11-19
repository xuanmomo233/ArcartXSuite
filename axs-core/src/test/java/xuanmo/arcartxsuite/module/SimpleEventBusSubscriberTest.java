package xuanmo.arcartxsuite.module;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import static org.junit.jupiter.api.Assertions.*;

class SimpleEventBusSubscriberTest {
    @Test void auctionWaitsForAnInstalledEventPacketToResume() {
        var bus = new SimpleEventBus(Logger.getAnonymousLogger());
        var expected = new java.util.concurrent.atomic.AtomicBoolean();
        bus.requireAuctionRules(expected::get);
        String topic = EventBusCapability.MARKET_AUCTION_PURCHASED;
        var fact = java.util.Map.of("event_id", "auction-expiry:1");
        assertTrue(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        expected.set(true);
        bus.subscribeConfirmed(EventBusCapability.BATTLEPASS_AUCTION_CONSUMER, topic,
            ignored -> java.util.concurrent.CompletableFuture.completedFuture(null));
        assertFalse(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        var rules = bus.subscribeConfirmed(EventBusCapability.EVENTPACKET_AUCTION_CONSUMER, topic,
            ignored -> java.util.concurrent.CompletableFuture.completedFuture(null));
        assertTrue(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        bus.unsubscribe(rules);
        assertFalse(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        expected.set(false);
        assertTrue(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
    }

    @Test void auctionConfirmationWaitsForConsumersAndCanBeRetried() {
        var bus = new SimpleEventBus(Logger.getAnonymousLogger());
        String topic = xuanmo.arcartxsuite.api.capability.EventBusCapability.MARKET_AUCTION_PURCHASED;
        var fact = java.util.Map.of("event_id", "auction-expiry:1");
        assertTrue(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        var committed = new java.util.concurrent.CompletableFuture<Void>();
        var subscription = bus.subscribeConfirmed("eventpacket/auction", topic, event -> committed);
        var pending = bus.publishConfirmed(topic, null, fact).toCompletableFuture();
        assertFalse(pending.isDone());
        committed.completeExceptionally(new IllegalStateException("Database unavailable"));
        assertFalse(pending.join());
        bus.unsubscribe(subscription);
        bus.subscribeConfirmed("eventpacket/auction", topic,
            event -> java.util.concurrent.CompletableFuture.completedFuture(null));
        assertTrue(bus.publishConfirmed(topic, null, fact).toCompletableFuture().join());
        assertFalse(bus.publishConfirmed("axs.auth.profile_first_admitted", null, fact).toCompletableFuture().join());
    }
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

    @Test void concurrentRouteUpdatesDoNotLoseSubscribers() throws Exception {
        var bus = new SimpleEventBus(Logger.getAnonymousLogger());
        String topic = "axs.fishing.success";
        var events = new java.util.concurrent.atomic.AtomicInteger();
        bus.subscribe("axs.fishing.*", ignored -> events.incrementAndGet());
        bus.subscribe("axs.*", ignored -> events.incrementAndGet());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int n = 0; n < 4; n++) {
                jobs.add(executor.submit(() -> {
                    for (int i = 0; i < 1000; i++) {
                        String temporary = bus.subscribe(topic, ignored -> {});
                        bus.publish(topic, null, java.util.Map.of());
                        bus.unsubscribe(temporary);
                    }
                }));
            }
            for (var job : jobs) job.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(8000, events.get());
        assertEquals(2, bus.subscriptionCount());
    }
}
