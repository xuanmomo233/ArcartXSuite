package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;

/**
 * 简单内存事件总线实现，支持精确匹配和通配符订阅。
 */
public final class SimpleEventBus implements EventBusCapability {

    private final Logger logger;
    private volatile Map<String, List<Subscription>> subscriptions = Map.of();
    private final Map<String, Subscription> subscriptionById = new HashMap<>();
    private final Set<String> publisherTopics = ConcurrentHashMap.newKeySet();
    private record ConfirmedSubscription(String consumer,String topic,ConfirmedHandler handler) {}
    private final ConcurrentHashMap<String,ConfirmedSubscription> confirmed=new ConcurrentHashMap<>();
    private volatile boolean requireAuctionProgress;
    private volatile BooleanSupplier requireAuctionRules = () -> false;

    public SimpleEventBus(Logger logger) {
        this.logger = logger;
    }

    void requireAuctionProgress(boolean required) {
        requireAuctionProgress = required;
    }

    void requireAuctionRules(BooleanSupplier required) {
        requireAuctionRules = required;
    }

    @Override
    public void publish(@NotNull String topic, @Nullable Player player, @NotNull Object payload) {
        publishSubscribers(topic, player, payload);
        if(payload instanceof java.util.Map<?,?> && isAuthFact(topic)) {
            @SuppressWarnings("unchecked") var fact=(java.util.Map<String,String>)payload;
            publishConfirmed(topic,player,fact);
        }
    }

    /** Host admission emits ordinary subscribers once and retries confirmed consumers separately. */
    public java.util.concurrent.CompletionStage<Boolean> publishAuthFact(String topic,Player player,Map<String,String> payload) {
        if(!isAuthFact(topic) || !payload.containsKey("event_id")) throw new IllegalArgumentException("Invalid authentication fact");
        publishSubscribers(topic, player, payload);
        return publishConfirmed(topic, player, payload);
    }

    private void publishSubscribers(String topic, Player player, Object payload) {
        // 自动追踪已发布的主题（运行时检测补充）
        publisherTopics.add(topic);
        var routes = subscriptions;
        var exact = routes.get(topic);
        BusEvent event = null;
        if (exact != null) {
            event = new BusEvent(topic, player, payload, System.currentTimeMillis());
            for (Subscription sub : exact) {
                dispatch(sub, event);
            }
        }
        // Walk the topic's own namespaces, independent of the number of subscribers.
        for (int dot = topic.lastIndexOf('.'); dot >= 0; dot = topic.lastIndexOf('.', dot - 1)) {
            String pattern = topic.substring(0, dot + 1) + "*";
            var matches = pattern.equals(topic) ? null : routes.get(pattern);
            if (matches != null) {
                if (event == null) {
                    event = new BusEvent(topic, player, payload, System.currentTimeMillis());
                }
                for (Subscription sub : matches) {
                    dispatch(sub, event);
                }
            }
        }
    }
    private static boolean isAuthFact(String topic) {
        return topic.equals("axs.auth.profile_first_admitted") || topic.equals("axs.auth.presence_day_started");
    }
    private static boolean isConfirmedTopic(String topic) {
        return isAuthFact(topic) || MARKET_AUCTION_PURCHASED.equals(topic);
    }
    @Override public String subscribeConfirmed(String consumer,String topic,ConfirmedHandler handler) {
        if(!isConfirmedTopic(topic) || consumer==null || !consumer.matches("[a-zA-Z0-9_.:/-]{1,192}")) throw new IllegalArgumentException("Invalid confirmed subscription");
        String id="confirmed:"+topic+":"+consumer;
        if(confirmed.putIfAbsent(id,new ConfirmedSubscription(consumer,topic,java.util.Objects.requireNonNull(handler)))!=null)
            throw new IllegalStateException("Duplicate confirmed consumer");
        return id;
    }
    @Override public java.util.concurrent.CompletionStage<Boolean> publishConfirmed(String topic,Player player,java.util.Map<String,String> payload) {
        if(!isConfirmedTopic(topic) || !payload.containsKey("event_id")) throw new IllegalArgumentException("Invalid confirmed event");
        boolean auction = MARKET_AUCTION_PURCHASED.equals(topic);
        var consumers = confirmed.values().stream().filter(sub -> sub.topic().equals(topic)).toList();
        if (auction) {
            boolean progressMissing = requireAuctionProgress
                && consumers.stream().noneMatch(sub -> BATTLEPASS_AUCTION_CONSUMER.equals(sub.consumer()));
            boolean rulesMissing = requireAuctionRules.getAsBoolean()
                && consumers.stream().noneMatch(sub -> EVENTPACKET_AUCTION_CONSUMER.equals(sub.consumer()));
            if (progressMissing || rulesMissing) return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        var receipts=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        var event=new BusEvent(topic,player,java.util.Map.copyOf(payload),System.currentTimeMillis());
        consumers.forEach(sub->{
            try { receipts.add(java.util.Objects.requireNonNull(sub.handler().handle(event)).toCompletableFuture()); }
            catch(Exception | LinkageError e) { receipts.add(java.util.concurrent.CompletableFuture.failedFuture(e)); }
        });
        if(receipts.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(auction);
        return java.util.concurrent.CompletableFuture.allOf(receipts.toArray(java.util.concurrent.CompletableFuture[]::new))
            .handle((ignored,error)->error==null);
    }

    @Override
    public void registerPublisher(@NotNull String topic) {
        publisherTopics.add(topic);
    }

    @Override
    public boolean hasPublisher(@NotNull String topic) {
        return publisherTopics.contains(topic);
    }
    @Override
    public boolean hasSubscribers(@NotNull String topic) {
        var routes = subscriptions;
        if (routes.containsKey(topic)) return true;
        for (int dot = topic.lastIndexOf('.'); dot >= 0; dot = topic.lastIndexOf('.', dot - 1)) {
            if (routes.containsKey(topic.substring(0, dot + 1) + "*")) return true;
        }
        return isConfirmedTopic(topic) && hasConfirmedSubscriber(topic);
    }

    public boolean hasConfirmedSubscriber(String topic) {
        return confirmed.values().stream().anyMatch(sub -> sub.topic().equals(topic));
    }

    @Override
    public synchronized String subscribe(@NotNull String topic, @NotNull EventHandler handler) {
        String id = UUID.randomUUID().toString();
        Subscription sub = new Subscription(id, topic, handler);
        var next = new HashMap<>(subscriptions);
        var entries = new ArrayList<>(next.getOrDefault(topic, List.of()));
        entries.add(sub);
        next.put(topic, List.copyOf(entries));
        subscriptionById.put(id, sub);
        subscriptions = Map.copyOf(next);
        return id;
    }

    @Override
    public synchronized void unsubscribe(@NotNull String subscriptionId) {
        confirmed.remove(subscriptionId);
        Subscription sub = subscriptionById.remove(subscriptionId);
        if (sub != null) {
            var next = new HashMap<>(subscriptions);
            var entries = new ArrayList<>(next.get(sub.topic));
            entries.remove(sub);
            if (entries.isEmpty()) {
                next.remove(sub.topic);
            } else {
                next.put(sub.topic, List.copyOf(entries));
            }
            subscriptions = Map.copyOf(next);
        }
    }

    public synchronized int subscriptionCount() {
        return subscriptionById.size()+confirmed.size();
    }

    public synchronized void clear() {
        subscriptions = Map.of();
        subscriptionById.clear();
        publisherTopics.clear();
        confirmed.clear();
    }

    private void dispatch(Subscription sub, BusEvent event) {
        try {
            sub.handler.handle(event);
        } catch (Exception e) {
            logger.warning("[EventBus] 处理器异常 topic=" + event.topic() + " sub=" + sub.id + " : " + e.getMessage());
        }
    }

    private record Subscription(String id, String topic, EventHandler handler) {}
}
