package xuanmo.arcartxsuite.combateffect.combo;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
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
 *   <li>Bukkit: 监听 EntityDamageByEntityEvent，基于时间窗口累加 combo</li>
 * </ul>
 * 当 combo 达到 PacketDefinition 中定义的阈值时，触发 COMBO 类型包。
 */
public final class ComboTrackerService implements Listener {

    private final JavaPlugin plugin;
    private final ComboTrackerConfiguration config;
    private final CombatEffectPacketConfiguration packetConfig;
    private final PacketBridgeAPI packetBridge;
    private final ClientBridgeAPI clientBridge;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, ComboState> comboStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> resetTasks = new ConcurrentHashMap<>();

    private Listener quitCleanupListener;

    // Chronos 反射
    private boolean chronosHooked;
    private Listener chronosListener;
    private Method chronosGetPlayerStateGroupIdMethod;
    private Object chronosApiInstance;

    public ComboTrackerService(
        JavaPlugin plugin,
        ComboTrackerConfiguration config,
        CombatEffectPacketConfiguration packetConfig,
        PacketBridgeAPI packetBridge,
        ClientBridgeAPI clientBridge,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.packetConfig = packetConfig;
        this.packetBridge = packetBridge;
        this.clientBridge = clientBridge;
        this.logger = logger;
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }

        boolean useChronos = config.source() == ComboSource.CHRONOS || config.source() == ComboSource.AUTO;
        boolean useBukkit = config.source() == ComboSource.BUKKIT || config.source() == ComboSource.AUTO;

        if (useChronos) {
            chronosHooked = initChronos();
        }

        // 如果 Chronos 不可用或 source=bukkit，使用 Bukkit 攻击事件
        if (!chronosHooked && useBukkit) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            if (config.debug()) {
                logger.info("ComboTracker 使用 Bukkit 攻击事件作为 combo 来源");
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
        chronosHooked = false;
        for (BukkitTask task : resetTasks.values()) {
            task.cancel();
        }
        resetTasks.clear();
        comboStates.clear();
    }

    public int getComboCount(Player player) {
        ComboState state = comboStates.get(player.getUniqueId());
        return state != null ? state.count : 0;
    }

    // ========== Bukkit 攻击事件监听 ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player attacker = CombatEventSupport.resolvePlayerAttacker(event);
        if (attacker == null) {
            return;
        }
        incrementCombo(attacker, target.getUniqueId());
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
        long now = System.currentTimeMillis();

        ComboState state = comboStates.compute(playerId, (uuid, existing) -> {
            if (existing == null || now - existing.lastHitTime > config.timeoutMs()) {
                return new ComboState(1, now, targetId);
            }
            // per-target 模式: 切换目标则重置 combo
            if (config.perTarget() && targetId != null && existing.targetId != null
                && !targetId.equals(existing.targetId)) {
                return new ComboState(1, now, targetId);
            }
            return new ComboState(existing.count + 1, now, targetId != null ? targetId : existing.targetId);
        });

        // 重置超时任务
        scheduleReset(playerId);

        // 服务器变量同步
        syncVariable(player, state.count);

        // 检查 combo 触发
        dispatchComboPackets(player, state.count);

        if (config.debug()) {
            logger.info("ComboTracker | " + player.getName() + " combo=" + state.count
                + (config.perTarget() && targetId != null ? " target=" + targetId : ""));
        }
    }

    private void scheduleReset(UUID playerId) {
        BukkitTask oldTask = resetTasks.remove(playerId);
        if (oldTask != null) {
            oldTask.cancel();
        }
        long timeoutTicks = Math.max(1, config.timeoutMs() / 50);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ComboState removed = comboStates.remove(playerId);
            resetTasks.remove(playerId);
            // 重置时同步变量为 0
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                syncVariable(player, 0);
            }
            if (config.debug() && removed != null) {
                String name = player != null ? player.getName() : playerId.toString();
                logger.info("ComboTracker | " + name + " combo 超时重置 (was " + removed.count + ")");
            }
        }, timeoutTicks);
        resetTasks.put(playerId, task);
    }

    private void syncVariable(Player player, int comboCount) {
        if (!config.syncVariable() || clientBridge == null || !clientBridge.isAvailable()) {
            return;
        }
        clientBridge.sendServerVariable(player, config.variableName(), comboCount);
    }

    private void dispatchComboPackets(Player player, int comboCount) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }

        for (PacketDefinition definition : packetConfig.packetDefinitions()) {
            if (!definition.enabled() || !definition.matchesCombo(comboCount)) {
                continue;
            }
            for (PacketRecipient recipientType : definition.recipients()) {
                // combo 场景: attacker=player, target=player (自身)
                Player recipient = recipientType.resolve(player, player);
                if (recipient == null || !recipient.isOnline()) {
                    continue;
                }
                // 构建 combo payload，替换 {combo_count} 变量
                Map<String, Object> renderedPayload = renderComboPayload(definition.packTemplate(), player, comboCount);
                boolean success = packetBridge.sendPacket(
                    recipient, definition.uiId(), definition.packetHandler(), renderedPayload
                );
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
        BukkitTask task = resetTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    // ========== Chronos 集成 ==========

    @SuppressWarnings("unchecked")
    private boolean initChronos() {
        try {
            org.bukkit.plugin.Plugin chronos = Bukkit.getPluginManager().getPlugin("Chronos");
            if (chronos == null || !chronos.isEnabled()) {
                return false;
            }
            ClassLoader cl = chronos.getClass().getClassLoader();
            Class<?> chronosApiClass = Class.forName("priv.seventeen.artist.chronos.api.ChronosAPI", true, cl);
            Method getInstanceMethod = chronosApiClass.getMethod("getInstanceAPI");
            chronosApiInstance = getInstanceMethod.invoke(null);
            if (chronosApiInstance == null) {
                return false;
            }
            chronosGetPlayerStateGroupIdMethod = chronosApiClass.getMethod("getPlayerStateGroupId", Player.class);

            // 注册 PlayerEnterStateEvent 监听
            Class<?> eventClass = Class.forName(
                "priv.seventeen.artist.chronos.api.event.PlayerEnterStateEvent", true, cl
            );
            if (!Event.class.isAssignableFrom(eventClass)) {
                return false;
            }

            Method getPlayerMethod = eventClass.getMethod("getPlayer");
            chronosListener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                (Class<? extends Event>) eventClass,
                chronosListener,
                EventPriority.MONITOR,
                (listener, event) -> handleChronosStateEnter(event, eventClass, getPlayerMethod),
                plugin,
                true
            );
            return true;
        } catch (Exception exception) {
            if (config.debug()) {
                logger.warning("ComboTracker Chronos 初始化失败: " + exception.getMessage());
            }
            return false;
        }
    }

    private void handleChronosStateEnter(Event event, Class<?> eventClass, Method getPlayerMethod) {
        if (!eventClass.isInstance(event)) {
            return;
        }
        try {
            Object rawPlayer = getPlayerMethod.invoke(event);
            if (!(rawPlayer instanceof Player player)) {
                return;
            }
            // 检查状态组是否在配置的 combo 组中
            String group = (String) chronosGetPlayerStateGroupIdMethod.invoke(chronosApiInstance, player);
            if (group == null || !config.chronosGroups().contains(group)) {
                return;
            }
            incrementCombo(player);
        } catch (Exception exception) {
            if (config.debug()) {
                logger.warning("ComboTracker 处理 Chronos 事件失败: " + exception.getMessage());
            }
        }
    }

    // ========== 内部类 ==========

    private class QuitCleanupListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            cleanupPlayer(event.getPlayer().getUniqueId());
        }
    }

    private record ComboState(int count, long lastHitTime, UUID targetId) {}
}
