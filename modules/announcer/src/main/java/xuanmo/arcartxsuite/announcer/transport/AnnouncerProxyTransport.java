package xuanmo.arcartxsuite.announcer.transport;

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
import xuanmo.arcartxsuite.announcer.config.AnnouncerProxyConfiguration;

/**
 * 通过 BungeeCord Forward 子频道跨服转发公告。
 */
public final class AnnouncerProxyTransport implements PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final AnnouncerProxyConfiguration configuration;
    private final Consumer<AnnouncerEnvelope> consumer;
    private boolean active;

    public AnnouncerProxyTransport(
        JavaPlugin plugin,
        AnnouncerProxyConfiguration configuration,
        Consumer<AnnouncerEnvelope> consumer
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.consumer = Objects.requireNonNull(consumer);
    }

    public boolean start() {
        if (!configuration.enabled()) {
            return false;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        active = true;
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void send(AnnouncerEnvelope envelope) {
        if (!active || envelope == null) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }
        try {
            byte[] payloadBytes = AnnouncerEnvelopeCodec.encode(envelope).getBytes(StandardCharsets.UTF_8);
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
            plugin.getLogger().warning("Proxy 公告广播失败: " + exception.getMessage());
        }
    }

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
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!active || !BUNGEE_CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = input.readUTF();
            if (!configuration.messengerChannel().equalsIgnoreCase(subChannel)) {
                return;
            }
            short length = input.readShort();
            byte[] payload = new byte[length];
            input.readFully(payload);
            consumer.accept(AnnouncerEnvelopeCodec.decode(new String(payload, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            plugin.getLogger().warning("解析 Proxy 公告广播失败: " + exception.getMessage());
        }
    }
}
