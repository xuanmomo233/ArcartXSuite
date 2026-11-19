package xuanmo.arcartxsuite.combateffect.combo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.combateffect.chronos.ChronosHook;
import xuanmo.arcartxsuite.combateffect.combo.ComboTrackerConfiguration.ComboSource;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketRecipient;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;

/**
 * 连击追踪服务。
 * <p>
 * 支持两种连击来源：
 * <ul>
 *   <li>Chronos: 监听 PlayerEnterStateEvent，当状态组匹配时累加 combo</li>
 *   <li>有效伤害: 监听宿主结算的 CombatDamageEvent，基于时间窗口累加 combo</li>
 * </ul>
 * 当 combo 达到 PacketDefinition 中定义的阈值时，触发 COMBO 类型包。
 */
public final class ComboTrackerService implements Listener {

    public static final String HUD_ID = "AXS:combo_effect";
    public static final String HUD_SYNC_HANDLER = "combo_sync";

    private final JavaPlugin plugin;
    private final ComboTrackerConfiguration config;
    private final List<PacketDefinition> comboPackets;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final long timeoutNanos;
    private final boolean pushHud;
    private final LongSupplier nanoTime;

    // Combat events and the flush task run on the server thread. A hit puts the player at the end.
    private final Map<UUID, ComboState> comboStates = new LinkedHashMap<>();
    private final Map<UUID, Player> pendingVariables = new HashMap<>();
    private final Set<CombatDamageEvent.GunHit> countedGunHits = Collections.newSetFromMap(new WeakHashMap<>());
    private BukkitTask updateTask;

    private Listener quitCleanupListener;

    private Listener chronosListener;

    public ComboTrackerService(
        JavaPlugin plugin,
        ComboTrackerConfiguration config,
        CombatEffectPacketConfiguration packetConfig,
        PacketBridgeAPI packetBridge,
        Logger logger
    ) {
        this(plugin, config, packetConfig, packetBridge, logger, System::nanoTime);
    }

    ComboTrackerService(JavaPlugin plugin, ComboTrackerConfiguration config, CombatEffectPacketConfiguration packetConfig,
                        PacketBridgeAPI packetBridge, Logger logger, LongSupplier nanoTime) {
        this.plugin = plugin;
        this.config = config;
        this.comboPackets = packetConfig.enabledPackets(PacketTrigger.COMBO);
        this.packetBridge = packetBridge;
        this.logger = logger;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(config.timeoutMs());
        this.pushHud = config.syncVariable();
        this.nanoTime = nanoTime;
    }

    public void start(ChronosAccess chronos, boolean symphonyDamageAvailable) {
        if (!config.enabled()) {
            return;
        }

        boolean useChronos = config.source() == ComboSource.CHRONOS
            || config.source() == ComboSource.AUTO && !symphonyDamageAvailable;
        boolean useDamage = config.source() != ComboSource.CHRONOS;

        boolean chronosHooked = useChronos && chronos != null && initChronos(chronos);

        if (!chronosHooked && useDamage) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            if (config.debug()) {
                logger.info("ComboTracker 使用有效伤害作为 combo 来源");
            }
        } else if (chronosHooked) {
            quitCleanupListener = new QuitCleanupListener();
            Bukkit.getPluginManager().registerEvents(quitCleanupListener, plugin);
            if (config.debug()) {
                logger.info("ComboTracker 使用 Chronos 状态事件作为 combo 来源 | groups=" + config.chronosGroups());
            }
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (quitCleanupListener != null) {
            HandlerList.unregisterAll(quitCleanupListener);
            quitCleanupListener = null;
        }
        if (chronosListener != null) {
            HandlerList.unregisterAll(chronosListener);
            chronosListener = null;
        }
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (UUID playerId : comboStates.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                syncCount(player, 0);
            }
        }
        pendingVariables.clear();
        comboStates.clear();
        countedGunHits.clear();
    }

    int getComboCount(Player player) {
        ComboState state = comboStates.get(player.getUniqueId());
        return state != null && nanoTime.getAsLong() - state.lastHitTime < timeoutNanos ? state.count : 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatDamage(CombatDamageEvent event) {
        Player attacker = event.getAttacker();
        if (!attacker.isOnline()) return;
        var gun = event.getGunHit();
        if (gun != null && !countedGunHits.add(gun)) return;
        incrementCombo(attacker, config.perTarget() ? event.getTarget().getUniqueId() : null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    // ========== 核心 combo 逻辑 ==========

    private void incrementCombo(Player player) {
        incrementCombo(player, null);
    }

    private void incrementCombo(Player player, UUID targetId) {
        UUID playerId = player.getUniqueId();
        long now = nanoTime.getAsLong();
        ComboState state = comboStates.remove(playerId);
        int count = 1;
        if (state == null) {
            state = new ComboState();
        } else if (now - state.lastHitTime < timeoutNanos
                && (!config.perTarget() || targetId == null || state.targetId == null || targetId.equals(state.targetId))) {
            count = state.count + 1;
            if (targetId == null) targetId = state.targetId;
        }
        state.lastHitTime = now;
        state.count = count;
        state.targetId = targetId;
        comboStates.put(playerId, state);

        if (pushHud) {
            if (count == 1) {
                pendingVariables.remove(playerId);
                syncCount(player, 1);
            } else {
                pendingVariables.put(playerId, player);
            }
        }
        if (updateTask == null) {
            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushVariables, 1L, 1L);
        }

        // 检查 combo 触发
        dispatchComboPackets(player, count);

        if (config.debug()) {
            logger.info("ComboTracker | " + player.getName() + " combo=" + count
                + (config.perTarget() && targetId != null ? " target=" + targetId : ""));
        }
    }

    private void flushVariables() {
        if (updateTask == null) return;
        long now = nanoTime.getAsLong();
        var states = comboStates.entrySet().iterator();
        List<ExpiredCombo> expired = null;
        while (states.hasNext()) {
            var entry = states.next();
            ComboState state = entry.getValue();
            if (now - state.lastHitTime < timeoutNanos) break;
            UUID playerId = entry.getKey();
            states.remove();
            pendingVariables.remove(playerId);
            if (!pushHud && !config.debug()) continue;
            if (expired == null) expired = new ArrayList<>();
            expired.add(new ExpiredCombo(playerId, state.count));
        }
        if (expired != null) {
            for (var entry : expired) {
                Player player = Bukkit.getPlayer(entry.playerId());
                if (player != null && player.isOnline()) syncCount(player, 0);
                if (config.debug()) {
                    String name = player != null ? player.getName() : entry.playerId().toString();
                    logger.info("ComboTracker | " + name + " combo 超时重置 (was " + entry.count() + ")");
                }
            }
        }
        pendingVariables.forEach((id, player) -> {
            ComboState state = comboStates.get(id);
            if (state != null && player.isOnline()) {
                syncCount(player, state.count);
            }
        });
        pendingVariables.clear();
        if (comboStates.isEmpty()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void syncCount(Player player, int comboCount) {
        if (pushHud && packetBridge != null && packetBridge.isAvailable()) {
            packetBridge.sendPacket(player, HUD_ID, HUD_SYNC_HANDLER, comboCount);
        }
    }

    private void dispatchComboPackets(Player player, int comboCount) {
        if (comboPackets.isEmpty() || packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }

        for (PacketDefinition definition : comboPackets) {
            if (!definition.matchesCombo(comboCount)) {
                continue;
            }
            Object renderedPayload = renderComboPayload(definition.packTemplate(), player, comboCount);
            for (PacketRecipient recipientType : definition.recipients()) {
                // combo 场景: attacker=player, target=player (自身)
                Player recipient = recipientType.resolve(player, player);
                if (recipient == null || !recipient.isOnline()) {
                    continue;
                }
                boolean carriesHudCount = pushHud && recipient.equals(player)
                    && HUD_ID.equals(definition.uiId())
                    && ("combo".equals(definition.packetHandler()) || "combo_milestone".equals(definition.packetHandler()))
                    && renderedPayload instanceof Map<?, ?> payload
                    && Integer.toString(comboCount).equals(String.valueOf(payload.get("combo_count")));
                if (!carriesHudCount && pendingVariables.remove(player.getUniqueId()) != null) {
                    syncCount(player, comboCount);
                }
                boolean success = packetBridge.sendPacket(
                    recipient, definition.uiId(), definition.packetHandler(), renderedPayload
                );
                if (success && carriesHudCount) pendingVariables.remove(player.getUniqueId());
                if (config.debug()) {
                    logger.info("ComboTracker 发包[" + definition.id() + "] -> " + recipient.getName()
                        + " | combo=" + comboCount
                        + " | success=" + success);
                }
            }
        }
    }

    private Map<String, Object> renderComboPayload(Map<String, Object> template, Player player, int comboCount) {
        if (template == null || template.isEmpty()) {
            return Map.of("combo_count", comboCount);
        }
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                value = text
                    .replace("{combo_count}", String.valueOf(comboCount))
                    .replace("{player}", player.getName())
                    .replace("{attacker}", player.getName());
            }
            payload.put(entry.getKey(), value);
        }
        return payload;
    }

    private void cleanupPlayer(UUID playerId) {
        comboStates.remove(playerId);
        pendingVariables.remove(playerId);
    }

    // ========== Chronos 集成（通过 ChronosHook 隔离）==========

    private boolean initChronos(ChronosAccess chronos) {
        Listener listener = new Listener() {};
        try {
            ChronosHook.listen(plugin, chronos, listener, "PlayerEnterStateEvent", "getStateId", (player, state) -> {
                String group = chronos.stateGroupId(player);
                if (group != null && config.chronosGroups().contains(group)) incrementCombo(player);
            });
            chronosListener = listener;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HandlerList.unregisterAll(listener);
            logger.warning("ComboTracker 无法注册 Chronos 事件: " + e);
            return false;
        }
    }

    // ========== 内部类 ==========

    private class QuitCleanupListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            cleanupPlayer(event.getPlayer().getUniqueId());
        }
    }

    private static final class ComboState {
        int count;
        long lastHitTime;
        UUID targetId;
    }
    private record ExpiredCombo(UUID playerId, int count) {}
}
