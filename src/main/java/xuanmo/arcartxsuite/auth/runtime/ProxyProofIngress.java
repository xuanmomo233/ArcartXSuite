package xuanmo.arcartxsuite.auth.runtime;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Messages may be submitted by clients; only the signed envelope is authoritative. */
final class ProxyProofIngress implements PluginMessageListener,AutoCloseable {
    static final String CHANNEL="axs:auth";
    private final JavaPlugin plugin;
    private final AuthenticationRuntime runtime;
    private final Map<UUID,Long> nextProof=new HashMap<>();
    ProxyProofIngress(JavaPlugin plugin,AuthenticationRuntime runtime) { this.plugin=plugin; this.runtime=runtime; }
    void start() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,CHANNEL,this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,CHANNEL);
    }
    void tick() {
        Set<UUID> online=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            String challenge=runtime.proxyChallenge(player);
            if(challenge!=null) player.sendPluginMessage(plugin,CHANNEL,challenge.getBytes(StandardCharsets.UTF_8));
        }
        nextProof.keySet().retainAll(online);
    }
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] data) {
        if(!CHANNEL.equals(channel) || data.length>4096 || !runtime.proxyAvailable()) return;
        long now=System.nanoTime(); UUID id=player.getUniqueId();
        Long deadline=nextProof.get(id);
        if(deadline!=null && now-deadline<0) return;
        nextProof.put(id,now+1_000_000_000L);
        try {
            var body=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!body.get("type").getAsString().equals("proof")) return;
            runtime.acceptProxyProof(player,UUID.fromString(body.get("connection").getAsString()),body.get("proof").getAsString());
        } catch(RuntimeException ignored) { }
    }
    @Override public void close() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin,CHANNEL,this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin,CHANNEL);
        nextProof.clear();
    }
}
