package xuanmo.arcartxsuite.tab.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.tab.config.TabProxyConfiguration;

public final class ProxyTabTransport implements TabTransport, PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final TabProxyConfiguration configuration;
    private final Consumer<TabServerSnapshot> consumer;
    private boolean active;

    public ProxyTabTransport(JavaPlugin plugin, TabProxyConfiguration configuration, Consumer<TabServerSnapshot> consumer) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public boolean start() {
        if (!configuration.enabled()) {
            return false;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        active = true;
        return true;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void send(TabServerSnapshot snapshot) {
        if (!active || snapshot == null) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }
        try {
            byte[] payloadBytes = TabSnapshotCodec.encode(snapshot).getBytes(StandardCharsets.UTF_8);
            if (payloadBytes.length > Short.MAX_VALUE) {
                plugin.getLogger().warning("Tab Proxy 广播负载超过 32KB 限制（当前 " + payloadBytes.length + " 字节），已跳过。");
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeUTF("Forward");
                output.writeUTF(configuration.forwardTarget());
                output.writeUTF(configuration.messengerChannel());
                output.writeShort(payloadBytes.length);
                output.write(payloadBytes);
            }
            carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
        } catch (Exception exception) {
            plugin.getLogger().warning("Proxy Tab 广播失败: " + exception.getMessage());
        }
    }

    @Override
    public void shutdown() {
        active = false;
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (Exception ignored) {
        }
    }

    @Override
    public String name() {
        return "proxy";
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!active || !BUNGEE_CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = input.readUTF();
            if (!"Forward".equalsIgnoreCase(subChannel)) {
                return;
            }
            input.readUTF();
            String actualChannel = input.readUTF();
            if (!configuration.messengerChannel().equalsIgnoreCase(actualChannel)) {
                return;
            }
            short length = input.readShort();
            byte[] payload = new byte[length];
            input.readFully(payload);
            consumer.accept(TabSnapshotCodec.decode(new String(payload, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            plugin.getLogger().warning("解析 Proxy Tab 广播失败: " + exception.getMessage());
        }
    }
}
