package xuanmo.arcartxsuite.combateffect.trigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.event.client.ClientKeyGroupPressEvent;
import priv.seventeen.artist.arcartx.event.client.ClientKeyPressEvent;
import priv.seventeen.artist.arcartx.event.client.ClientKeyReleaseEvent;
import priv.seventeen.artist.arcartx.event.client.ClientSimpleKeyPressEvent;
import priv.seventeen.artist.arcartx.event.client.ClientSimpleKeyReleaseEvent;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;

/**
 * 监听 ArcartX 按键事件，匹配 trigger:keybind 的包定义并发送 UI 包。
 */
public final class KeybindTriggerService implements Listener {

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private boolean registered;

    // cooldown
    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public KeybindTriggerService(
        JavaPlugin plugin,
        CombatEffectPacketConfiguration configuration,
        PacketBridgeAPI packetBridge,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.packetBridge = packetBridge;
        this.logger = logger;
    }

    public boolean start() {
        if (Bukkit.getPluginManager().getPlugin("ArcartX") == null) {
            logger.info("KeybindTriggerService: ArcartX 未安装，按键触发器未启用");
            return false;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
        logger.info("KeybindTriggerService: 已注册 ArcartX 按键事件监听");
        return true;
    }

    public void shutdown() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKeyPress(ClientKeyPressEvent event) {
        handleKeybind(event.getPlayer(), event.getKeyName(), true, "client");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKeyRelease(ClientKeyReleaseEvent event) {
        handleKeybind(event.getPlayer(), event.getKeyName(), false, "client");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSimpleKeyPress(ClientSimpleKeyPressEvent event) {
        handleKeybind(event.getPlayer(), event.getKeyName(), true, "simple");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSimpleKeyRelease(ClientSimpleKeyReleaseEvent event) {
        handleKeybind(event.getPlayer(), event.getKeyName(), false, "simple");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKeyGroupPress(ClientKeyGroupPressEvent event) {
        handleKeybind(event.getPlayer(), event.getGroupID(), true, "group");
    }

    private void handleKeybind(Player player, String keyName, boolean isPress, String keyType) {
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        long now = System.currentTimeMillis();
        for (PacketDefinition definition : configuration.packetDefinitions()) {
            if (!definition.enabled() || !definition.matchesKeybind(keyName, isPress, keyType)) {
                continue;
            }
            // 冷却检查
            if (definition.hasCooldown() && isOnCooldown(definition.id(), player.getUniqueId(), now)) {
                continue;
            }
            Object payload = renderSimplePayload(definition, player, keyName);
            boolean success = packetBridge.sendPacket(
                player, definition.uiId(), definition.packetHandler(), payload
            );
            if (definition.hasCooldown() && success) {
                setCooldown(definition.id(), player.getUniqueId(), now + definition.cooldownMs());
            }
            if (configuration.debug()) {
                logger.info(
                    "按键发包[" + definition.id() + "] -> " + player.getName()
                        + " | key=" + keyName + " | type=" + keyType
                        + " | action=" + (isPress ? "press" : "release")
                        + " | success=" + success
                );
            }
        }
    }

    private Object renderSimplePayload(PacketDefinition definition, Player player, String keyName) {
        if (definition.packTemplate() == null || definition.packTemplate().isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> rendered = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : definition.packTemplate().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                value = text
                    .replace("{player}", player.getName())
                    .replace("{key_name}", keyName);
            }
            rendered.put(entry.getKey(), value);
        }
        return rendered;
    }

    private boolean isOnCooldown(String packetId, UUID playerId, long now) {
        String key = packetId + ":" + playerId;
        Long expiry = cooldownMap.get(key);
        if (expiry == null) return false;
        if (now >= expiry) { cooldownMap.remove(key); return false; }
        return true;
    }

    private void setCooldown(String packetId, UUID playerId, long expiryTime) {
        cooldownMap.put(packetId + ":" + playerId, expiryTime);
    }
}
