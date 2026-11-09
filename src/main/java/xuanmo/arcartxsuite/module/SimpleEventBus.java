package xuanmo.arcartxsuite.module;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subscription> subscriptionById = new ConcurrentHashMap<>();
    private final Set<String> publisherTopics = ConcurrentHashMap.newKeySet();
    private record ConfirmedSubscription(String consumer,String topic,ConfirmedHandler handler) {}
    private final ConcurrentHashMap<String,ConfirmedSubscription> confirmed=new ConcurrentHashMap<>();

    public SimpleEventBus(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void publish(@NotNull String topic, @Nullable Player player, @NotNull Object payload) {
        // 自动追踪已发布的主题（运行时检测补充）
        publisherTopics.add(topic);
        BusEvent event = new BusEvent(topic, player, payload, System.currentTimeMillis());
        // 精确匹配
        CopyOnWriteArrayList<Subscription> exact = subscriptions.get(topic);
        if (exact != null) {
            for (Subscription sub : exact) {
                dispatch(sub, event);
            }
        }
        // 通配符匹配（"axs.market.*" 匹配 "axs.market.purchase"）
        for (var entry : subscriptions.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith(".*") && !pattern.equals(topic)) {
                String prefix = pattern.substring(0, pattern.length() - 1); // "axs.market."
                if (topic.startsWith(prefix)) {
                    for (Subscription sub : entry.getValue()) {
                        dispatch(sub, event);
                    }
                }
            }
        }
        if(payload instanceof java.util.Map<?,?> && isAuthFact(topic)) {
            @SuppressWarnings("unchecked") var fact=(java.util.Map<String,String>)payload;
            publishConfirmed(topic,player,fact);
        }
    }
    private static boolean isAuthFact(String topic) {
        return topic.equals("axs.auth.profile_first_admitted") || topic.equals("axs.auth.presence_day_started");
    }
    @Override public String subscribeConfirmed(String consumer,String topic,ConfirmedHandler handler) {
        if(!isAuthFact(topic) || consumer==null || !consumer.matches("[a-zA-Z0-9_.:/-]{1,192}")) throw new IllegalArgumentException("Invalid confirmed subscription");
        String id="confirmed:"+topic+":"+consumer;
        if(confirmed.putIfAbsent(id,new ConfirmedSubscription(consumer,topic,java.util.Objects.requireNonNull(handler)))!=null)
            throw new IllegalStateException("Duplicate confirmed consumer");
        return id;
    }
    @Override public java.util.concurrent.CompletionStage<Boolean> publishConfirmed(String topic,Player player,java.util.Map<String,String> payload) {
        if(!isAuthFact(topic) || !payload.containsKey("event_id")) throw new IllegalArgumentException("Invalid authentication fact");
        var receipts=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        var event=new BusEvent(topic,player,java.util.Map.copyOf(payload),System.currentTimeMillis());
        confirmed.values().stream().filter(sub->sub.topic().equals(topic)).forEach(sub->{
            try { receipts.add(java.util.Objects.requireNonNull(sub.handler().handle(event)).toCompletableFuture()); }
            catch(Exception | LinkageError e) { receipts.add(java.util.concurrent.CompletableFuture.failedFuture(e)); }
        });
        if(receipts.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(false);
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
        if(confirmed.values().stream().anyMatch(sub->sub.topic().equals(topic))) return true;
        var exact=subscriptions.get(topic);
        if(exact!=null && !exact.isEmpty()) return true;
        return subscriptions.entrySet().stream().anyMatch(entry -> entry.getKey().endsWith(".*")
            && !entry.getValue().isEmpty() && topic.startsWith(entry.getKey().substring(0,entry.getKey().length()-1)));
    }

    @Override
    public String subscribe(@NotNull String topic, @NotNull EventHandler handler) {
        String id = UUID.randomUUID().toString();
        Subscription sub = new Subscription(id, topic, handler);
        subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(sub);
        subscriptionById.put(id, sub);
        return id;
    }

    @Override
    public void unsubscribe(@NotNull String subscriptionId) {
        confirmed.remove(subscriptionId);
        Subscription sub = subscriptionById.remove(subscriptionId);
        if (sub != null) {
            CopyOnWriteArrayList<Subscription> list = subscriptions.get(sub.topic);
            if (list != null) {
                list.remove(sub);
                if (list.isEmpty()) {
                    subscriptions.remove(sub.topic, list);
                }
            }
        }
    }

    public int subscriptionCount() {
        return subscriptionById.size()+confirmed.size();
    }

    public void clear() {
        subscriptions.clear();
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
