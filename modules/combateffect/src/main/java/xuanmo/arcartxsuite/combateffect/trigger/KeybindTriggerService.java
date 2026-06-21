package xuanmo.arcartxsuite.combateffect.trigger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;

/**
 * 反射监听 ArcartX 按键事件，匹配 trigger:keybind 的包定义并发送 UI 包。
 */
public final class KeybindTriggerService {

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final Listener dummyListener = new Listener() {};
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
        try {
            registerKeyEvent("priv.seventeen.artist.arcartx.event.client.ClientKeyPressEvent", "client", true);
            registerKeyEvent("priv.seventeen.artist.arcartx.event.client.ClientKeyReleaseEvent", "client", false);
            registerKeyEvent("priv.seventeen.artist.arcartx.event.client.ClientSimpleKeyPressEvent", "simple", true);
            registerKeyEvent("priv.seventeen.artist.arcartx.event.client.ClientSimpleKeyReleaseEvent", "simple", false);
            registerGroupEvent("priv.seventeen.artist.arcartx.event.client.ClientKeyGroupPressEvent");
            registered = true;
            logger.info("KeybindTriggerService: 已注册 ArcartX 按键事件监听");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warning("KeybindTriggerService: ArcartX 按键事件类未找到 — " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warning("KeybindTriggerService: 注册失败 — " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        if (registered) {
            HandlerList.unregisterAll(dummyListener);
            registered = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void registerKeyEvent(String className, String keyType, boolean isPress) throws Exception {
        Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
        Method getPlayer = eventClass.getMethod("getPlayer");
        Method getKeyName = eventClass.getMethod("getKeyName");

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                Player player = (Player) getPlayer.invoke(event);
                String keyName = (String) getKeyName.invoke(event);
                handleKeybind(player, keyName, isPress, keyType);
            } catch (Exception e) {
                if (configuration.debug()) {
                    logger.warning("KeybindTriggerService 事件处理异常: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, dummyListener, EventPriority.MONITOR, executor, plugin, true);
    }

    @SuppressWarnings("unchecked")
    private void registerGroupEvent(String className) throws Exception {
        Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
        Method getPlayer = eventClass.getMethod("getPlayer");
        Method getGroupId = eventClass.getMethod("getGroupID");

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                Player player = (Player) getPlayer.invoke(event);
                String groupId = (String) getGroupId.invoke(event);
                handleKeybind(player, groupId, true, "group");
            } catch (Exception e) {
                if (configuration.debug()) {
                    logger.warning("KeybindTriggerService 按键组处理异常: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, dummyListener, EventPriority.MONITOR, executor, plugin, true);
    }

    private void handleKeybind(Player player, String keyName, boolean isPress, String keyType) {
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
