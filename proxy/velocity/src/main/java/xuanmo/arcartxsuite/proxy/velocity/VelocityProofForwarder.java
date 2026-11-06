package xuanmo.arcartxsuite.proxy.velocity;

import com.google.gson.*;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.auth.minecraft.*;

/** Only a configured backend connection may request a proof for its actual external player. */
public final class VelocityProofForwarder implements AutoCloseable {
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.create("axs","auth");
    private record Destination(String realm,String audience) {}
    private record Session(Player player,CompletableFuture<ProxyProofs.Identity> identity) {}
    private final Object plugin;
    private final ProxyServer server;
    private final boolean enabled;
    private final String issuer;
    private final LocalHandshakeClient gateway;
    private final KeyPair keys;
    private final Map<String,Destination> targets=new HashMap<>();
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastRequest=new ConcurrentHashMap<>();
    private final Semaphore pending=new Semaphore(32);

    VelocityProofForwarder(Object plugin,ProxyServer server,Path folder,Map<String,Object> config,Logger log) throws Exception {
        this.plugin=plugin; this.server=server;
        if(!Set.of("enabled","issuer","gateway-url","targets").containsAll(config.keySet())) throw new IllegalArgumentException("不支持的 auth-forwarding 字段");
        enabled=Boolean.TRUE.equals(config.get("enabled"));
        if(!enabled) { issuer=""; gateway=null; keys=null; return; }
        issuer=(String)config.get("issuer");
        if(issuer==null || !issuer.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("需要有效代理 issuer");
        String endpoint=(String)config.getOrDefault("gateway-url","http://127.0.0.1:25599");
        LocalAgentBinding.require(endpoint,java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        gateway=new LocalHandshakeClient(endpoint);
        if(!(config.get("targets") instanceof Map<?,?> values) || values.isEmpty() || values.size()>64) throw new IllegalArgumentException("需要 1 至 64 个后端目标");
        for(var entry:values.entrySet()) {
            if(!(entry.getKey() instanceof String name) || name.isBlank() || name.length()>128 || !(entry.getValue() instanceof Map<?,?> target))
                throw new IllegalArgumentException("无效后端映射");
            if(!Set.of("realm","audience").containsAll(target.keySet())) throw new IllegalArgumentException("后端映射含未知字段");
            String realm=(String)target.get("realm"),audience=(String)target.get("audience");
            if(realm==null || audience==null || !realm.matches("[a-zA-Z0-9_-]{1,64}") || !audience.matches("[a-zA-Z0-9_-]{1,64}"))
                throw new IllegalArgumentException("后端 realm 或 audience 无效");
            targets.put(name,new Destination(realm,audience));
        }
        keys=ProxyKeys.loadOrCreate(folder);
        log.info("[AuthForwarding] issuer="+issuer+" public-key="+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        server.getChannelRegistrar().register(CHANNEL);
        server.getEventManager().register(plugin,this);
    }
    @Subscribe public EventTask login(LoginEvent event) {
        if(!enabled || !event.getPlayer().isOnlineMode()) return null;
        Player player=event.getPlayer();
        var future=new CompletableFuture<ProxyProofs.Identity>();
        var session=new Session(player,future); sessions.put(player.getUniqueId(),session);
        if(!pending.tryAcquire()) { future.completeExceptionally(new IllegalStateException("Busy")); return null; }
        return EventTask.async(()->{
            try {
                var claim=gateway.claim(player.getUniqueId(),player.getUsername(),UUID.randomUUID(),issuer);
                var receipt=claim.receipt();
                future.complete(new ProxyProofs.Identity(player.getUniqueId(),player.getUsername(),receipt.provider(),receipt.issuedAt()));
            } catch(Exception e) { future.completeExceptionally(new IllegalStateException("Gateway proof unavailable")); }
            finally { pending.release(); }
        });
    }
    @Subscribe public void message(PluginMessageEvent event) {
        if(!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!(event.getSource() instanceof ServerConnection backend) || event.getData().length>4096) return;
        Player player=backend.getPlayer();
        if(!player.getCurrentServer().filter(current->current==backend).isPresent()) return;
        Destination destination=targets.get(backend.getServerInfo().getName());
        Session session=sessions.get(player.getUniqueId());
        if(destination==null || session==null || session.player()!=player || !session.identity().isDone()
                || session.identity().isCompletedExceptionally()) return;
        long now=System.nanoTime();
        Long previous=lastRequest.put(player.getUniqueId(),now);
        if(previous!=null && now-previous<500_000_000L) return;
        try {
            var body=JsonParser.parseString(new String(event.getData(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(!body.get("type").getAsString().equals("challenge") || !body.get("profile").getAsString().equals(player.getUniqueId().toString())
                    || !body.get("name").getAsString().equalsIgnoreCase(player.getUsername())
                    || !body.get("realm").getAsString().equals(destination.realm())
                    || !body.get("audience").getAsString().equals(destination.audience())) return;
            UUID connection=UUID.fromString(body.get("connection").getAsString()),challenge=UUID.fromString(body.get("challenge").getAsString());
            var target=new ProxyProofs.Target(destination.realm(),destination.audience(),connection,challenge);
            String proof=ProxyProofs.sign(issuer,keys.getPrivate(),session.identity().join(),target,System.currentTimeMillis());
            var response=new JsonObject(); response.addProperty("type","proof"); response.addProperty("connection",connection.toString()); response.addProperty("proof",proof);
            backend.sendPluginMessage(CHANNEL,response.toString().getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored) { }
    }
    @Subscribe public void disconnect(DisconnectEvent event) {
        UUID uuid=event.getPlayer().getUniqueId();
        Session existing=sessions.get(uuid);
        if(existing!=null && existing.player()==event.getPlayer()) { sessions.remove(uuid,existing); lastRequest.remove(uuid); }
    }
    @Override public void close() {
        if(enabled) { server.getEventManager().unregisterListener(plugin,this); server.getChannelRegistrar().unregister(CHANNEL); }
        sessions.clear(); lastRequest.clear();
    }
}
