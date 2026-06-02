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
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;

/**
 * 反射监听 Chronos 状态/控制器事件，匹配 trigger:state 和 trigger:controller 的包定义并发送 UI 包。
 */
public final class StateTriggerService {

    private static final String CHRONOS_PLUGIN_NAME = "ArcartX_Chronos_Plugin";
    private static final String EVENT_PACKAGE = "priv.seventeen.artist.arcartx.chronos.api.event.";
    private static final String API_CLASS = "priv.seventeen.artist.arcartx.chronos.api.ChronosAPI";

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final ArcartXPacketBridge packetBridge;
    private final Logger logger;
    private final Listener dummyListener = new Listener() {};
    private boolean registered;

    // ChronosAPI 反射缓存
    private Object chronosApi;
    private Method getPlayerControllerId;

    // cooldown
    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public StateTriggerService(
        JavaPlugin plugin,
        CombatEffectPacketConfiguration configuration,
        ArcartXPacketBridge packetBridge,
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
            registerStateEvent(EVENT_PACKAGE + "PlayerEnterStateEvent", true);
            registerStateEvent(EVENT_PACKAGE + "PlayerLeaveStateEvent", false);
            registerControllerEvent(EVENT_PACKAGE + "PlayerControllerChangeEvent");
            registered = true;
            logger.info("StateTriggerService: 已注册 Chronos 状态/控制器事件监听");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warning("StateTriggerService: Chronos 事件类未找到 — " + e.getMessage());
            return false;
        } catch (Exception e) {
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
            Class<?> apiClass = Class.forName(API_CLASS);
            Method getInstance = apiClass.getMethod("getInstanceAPI");
            chronosApi = getInstance.invoke(null);
            getPlayerControllerId = apiClass.getMethod("getPlayerControllerId", Player.class);
        } catch (Exception e) {
            logger.fine("StateTriggerService: ChronosAPI 初始化失败（运行时查询控制器将不可用）— " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerStateEvent(String className, boolean isEnter) throws Exception {
        Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
        Method getPlayer = eventClass.getMethod("getPlayer");
        Method getStateId = eventClass.getMethod("getStateId");

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                Player player = (Player) getPlayer.invoke(event);
                String stateId = (String) getStateId.invoke(event);
                if (stateId == null) return;
                String currentController = resolveControllerId(player);
                handleState(player, stateId, isEnter, currentController);
            } catch (Exception e) {
                if (configuration.debug()) {
                    logger.warning("StateTriggerService 状态事件处理异常: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, dummyListener, EventPriority.MONITOR, executor, plugin, true);
    }

    @SuppressWarnings("unchecked")
    private void registerControllerEvent(String className) throws Exception {
        Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
        Method getPlayer = eventClass.getMethod("getPlayer");
        Method getControllerId = eventClass.getMethod("getControllerId");

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                Player player = (Player) getPlayer.invoke(event);
                String controllerId = (String) getControllerId.invoke(event);
                handleController(player, controllerId);
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
        if (chronosApi == null || getPlayerControllerId == null) return null;
        try {
            return (String) getPlayerControllerId.invoke(chronosApi, player);
        } catch (Exception e) {
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
