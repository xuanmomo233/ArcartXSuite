package xuanmo.arcartxsuite.menu.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class MenuProxyTransfer {
    private static final String BUNGEE_CHANNEL = "BungeeCord";

    static void connect(JavaPlugin plugin, Logger logger, Player player, String serverName, BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) return;
        try {
            var messenger = plugin.getServer().getMessenger();
            if (!messenger.isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                messenger.registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            }
            var buffer = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(buffer)) {
                output.writeUTF("Connect");
                output.writeUTF(serverName);
            }
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
        } catch (IOException exception) {
            logger.warning("Menu connect 动作发送失败: " + exception.getMessage());
        }
    }
}
