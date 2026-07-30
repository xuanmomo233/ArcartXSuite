package xuanmo.arcartxsuite.combateffect.trigger;

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
import priv.seventeen.artist.arcartx.chronos.api.ChronosAPI;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerEnterStateEvent;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerLeaveStateEvent;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerControllerChangeEvent;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;

/**
 * 反射监听 Chronos 状态/控制器事件，匹配 trigger:state 和 trigger:controller 的包定义并发送 UI 包。
 */
public final class StateTriggerService {

    private static final String CHRONOS_PLUGIN_NAME = "ArcartX_Chronos_Plugin";

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final Listener dummyListener = new Listener() {};
    private boolean registered;

    // ChronosAPI
    private ChronosAPI chronosApi;

    // cooldown
    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public StateTriggerService(
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
        // Chronos 可能以不同名称加载，尝试多种
        if (Bukkit.getPluginManager().getPlugin(CHRONOS_PLUGIN_NAME) == null
            && Bukkit.getPluginManager().getPlugin("Chronos") == null) {
            logger.info("StateTriggerService: Chronos 未安装，状态/控制器触发器未启用");
            return false;
        }
        try {
            initChronosApi();
            registerStateEvent(PlayerEnterStateEvent.class, true);
            registerStateEvent(PlayerLeaveStateEvent.class, false);
            registerControllerEvent(PlayerControllerChangeEvent.class);
            registered = true;
            logger.info("StateTriggerService: 已注册 Chronos 状态/控制器事件监听");
            return true;
        } catch (Exception | LinkageError e) {
            logger.warning("StateTriggerService: 注册失败 — " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        if (registered) {
            HandlerList.unregisterAll(dummyListener);
            registered = false;
        }
    }

    private void initChronosApi() {
        try {
            chronosApi = ChronosAPI.getInstanceAPI();
        } catch (Exception | LinkageError e) {
            logger.fine("StateTriggerService: ChronosAPI 初始化失败（运行时查询控制器将不可用）— " + e.getMessage());
        }
    }

    private void registerStateEvent(Class<? extends Event> eventClass, boolean isEnter) {
        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                if (event instanceof PlayerEnterStateEvent enterEvent) {
                    handleState(enterEvent.getPlayer(), enterEvent.getStateId(), true, resolveControllerId(enterEvent.getPlayer()));
                } else if (event instanceof PlayerLeaveStateEvent leaveEvent) {
                    handleState(leaveEvent.getPlayer(), leaveEvent.getStateId(), false, resolveControllerId(leaveEvent.getPlayer()));
                }
            } catch (Exception e) {
                if (configuration.debug()) {
                    logger.warning("StateTriggerService 状态事件处理异常: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, dummyListener, EventPriority.MONITOR, executor, plugin, true);
    }

    private void registerControllerEvent(Class<? extends Event> eventClass) {
        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                if (event instanceof PlayerControllerChangeEvent controllerEvent) {
                    handleController(controllerEvent.getPlayer(), controllerEvent.getControllerId());
                }
            } catch (Exception e) {
                if (configuration.debug()) {
                    logger.warning("StateTriggerService 控制器事件处理异常: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, dummyListener, EventPriority.MONITOR, executor, plugin, true);
    }

    private void handleState(Player player, String stateId, boolean isEnter, String currentController) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        long now = System.currentTimeMillis();
        for (PacketDefinition definition : configuration.packetDefinitions()) {
            if (!definition.enabled() || !definition.matchesState(stateId, isEnter, currentController)) {
                continue;
            }
            if (definition.hasCooldown() && isOnCooldown(definition.id(), player.getUniqueId(), now)) {
                continue;
            }
            Object payload = renderPayload(definition, player, stateId, currentController);
            boolean success = packetBridge.sendPacket(
                player, definition.uiId(), definition.packetHandler(), payload
            );
            if (definition.hasCooldown() && success) {
                setCooldown(definition.id(), player.getUniqueId(), now + definition.cooldownMs());
            }
            if (configuration.debug()) {
                logger.info(
                    "状态发包[" + definition.id() + "] -> " + player.getName()
                        + " | state=" + stateId
                        + " | action=" + (isEnter ? "enter" : "leave")
                        + " | controller=" + currentController
                        + " | success=" + success
                );
            }
        }
    }

    private void handleController(Player player, String controllerId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        long now = System.currentTimeMillis();
        for (PacketDefinition definition : configuration.packetDefinitions()) {
            if (!definition.enabled() || !definition.matchesController(controllerId)) {
                continue;
            }
            if (definition.hasCooldown() && isOnCooldown(definition.id(), player.getUniqueId(), now)) {
                continue;
            }
            Object payload = renderPayload(definition, player, null, controllerId);
            boolean success = packetBridge.sendPacket(
                player, definition.uiId(), definition.packetHandler(), payload
            );
            if (definition.hasCooldown() && success) {
                setCooldown(definition.id(), player.getUniqueId(), now + definition.cooldownMs());
            }
            if (configuration.debug()) {
                logger.info(
                    "控制器发包[" + definition.id() + "] -> " + player.getName()
                        + " | controller=" + controllerId
                        + " | success=" + success
                );
            }
        }
    }

    private String resolveControllerId(Player player) {
        if (chronosApi == null) return null;
        try {
            return chronosApi.getPlayerControllerId(player);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private Object renderPayload(PacketDefinition definition, Player player, String stateId, String controllerId) {
        if (definition.packTemplate() == null || definition.packTemplate().isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> rendered = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : definition.packTemplate().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                value = text
                    .replace("{player}", player.getName());
                if (stateId != null) {
                    value = ((String) value).replace("{state_id}", stateId);
                }
                if (controllerId != null) {
                    value = ((String) value).replace("{controller_id}", controllerId);
                }
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
