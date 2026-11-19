package xuanmo.arcartxsuite.combateffect.trigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.chronos.ChronosHook;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;

/**
 * 监听 Chronos 状态/控制器事件，匹配 trigger:state 和 trigger:controller 的包定义并发送 UI 包。
 */
public final class StateTriggerService {

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final Listener listener = new Listener() {};

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

    public boolean start(ChronosAccess chronos) {
        if (chronos == null) return false;
        try {
            ChronosHook.listen(plugin, chronos, listener, "PlayerEnterStateEvent", "getStateId",
                (player, stateId) -> handleState(player, stateId, true, chronos.controllerId(player)));
            ChronosHook.listen(plugin, chronos, listener, "PlayerLeaveStateEvent", "getStateId",
                (player, stateId) -> handleState(player, stateId, false, chronos.controllerId(player)));
            ChronosHook.listen(plugin, chronos, listener, "PlayerControllerChangeEvent", "getControllerId",
                this::handleController);
            logger.info("StateTriggerService: 已注册 Chronos 状态/控制器事件监听");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            shutdown();
            logger.warning("StateTriggerService: 注册失败 — " + e);
            return false;
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(listener);
        cooldownMap.clear();
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
