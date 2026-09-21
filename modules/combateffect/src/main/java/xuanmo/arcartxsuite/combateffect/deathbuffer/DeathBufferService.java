package xuanmo.arcartxsuite.combateffect.deathbuffer;

import xuanmo.arcartxsuite.combateffect.chronos.ChronosHook;
import xuanmo.arcartxsuite.combateffect.chronos.ChronosHookFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatPacketContext;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketRecipient;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;

/**
 * 死亡缓冲服务。
 * <p>
 * 拦截致死伤害，在真正死亡前给玩家一个可视化缓冲期，
 * 期间播放 ArcartX Shader / 相机 / Chronos 死亡动作，
 * 并触发 DEATH 类型的 CombatEffect 包。
 */
public final class DeathBufferService implements Listener {

    private final JavaPlugin plugin;
    private final DeathBufferConfiguration config;
    private final CombatEffectPacketConfiguration packetConfig;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, BufferState> bufferedPlayers = new ConcurrentHashMap<>();

    // ArcartX 视觉桥接（ArcartX 是硬依赖，可直接引用）
    private boolean arcartxVisualAvailable;
    private ArcartXEntityManager entityManager;

    // Chronos（通过 ChronosHook 隔离，未装时为 null）
    private boolean chronosAvailable;
    private ChronosHook chronosHook;

    public DeathBufferService(
        JavaPlugin plugin,
        DeathBufferConfiguration config,
        CombatEffectPacketConfiguration packetConfig,
        PacketBridgeAPI packetBridge,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.packetConfig = packetConfig;
        this.packetBridge = packetBridge;
        this.logger = logger;
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        initArcartXVisuals();
        initChronos();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (config.debug()) {
            logger.info("DeathBuffer 已启动 | shader=" + config.shader()
                + " | camera=" + config.cameraPreset()
                + " | duration=" + config.durationMs() + "ms"
                + " | chronos=" + chronosAvailable);
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        // 清理所有正在缓冲的玩家
        for (var entry : bufferedPlayers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                forceExitBuffer(player, entry.getValue(), false);
            }
        }
        bufferedPlayers.clear();
    }

    public boolean isInBuffer(Player player) {
        return bufferedPlayers.containsKey(player.getUniqueId());
    }

    // ========== 事件监听 ==========

    /**
     * 最高优先级拦截所有伤害事件（包括非实体伤害：摔落、岩浆等）。
     * 如果伤害会致死，进入缓冲状态。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (bufferedPlayers.containsKey(victim.getUniqueId())) {
            // 已在缓冲中，阻止一切伤害
            event.setCancelled(true);
            return;
        }
        if (!shouldApply(victim)) {
            return;
        }

        double healthAfter = victim.getHealth() - event.getFinalDamage();
        if (healthAfter > 0) {
            return; // 非致死伤害
        }

        // 确定击杀者
        Player killer = null;
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            killer = CombatEventSupport.resolvePlayerAttacker(entityEvent);
        }

        // 阻止本次伤害，进入缓冲
        event.setCancelled(true);
        enterBuffer(victim, killer);
    }

    /**
     * 拦截 PlayerDeathEvent 作为安全网 —— 正常情况下不应触发，
     * 因为我们已在 EntityDamageEvent 阶段拦截。
     * 但如果有其他插件在 MONITOR 设置了血量或有 instakill，这里做兜底。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (bufferedPlayers.containsKey(player.getUniqueId())) {
            // 异常: 已在缓冲但仍然死了，清理状态
            BufferState state = bufferedPlayers.remove(player.getUniqueId());
            if (state != null && state.task != null) {
                state.task.cancel();
            }
            clearVisuals(player);
        }
    }

    /**
     * 阻止自动复活（如果配置开启）。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        // 正常缓冲流程不应到达这里，但做防御
        if (config.blockAutoRespawn() && bufferedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            // Bukkit 的 PlayerRespawnEvent 不可取消，但我们不应该到这里
            // 因为伤害已被拦截，玩家不会真正死亡
        }
    }

    /**
     * 玩家退出时清理缓冲状态。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BufferState state = bufferedPlayers.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            if (state.task != null) {
                state.task.cancel();
            }
            clearVisuals(event.getPlayer());
        }
    }

    // ========== 核心流程 ==========

    private void enterBuffer(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();

        // 冻结玩家
        victim.setInvulnerable(true);
        victim.setWalkSpeed(0f);
        victim.setFlySpeed(0f);
        // 设为极小血量（避免触发原版死亡）
        victim.setHealth(0.001);

        // 应用视觉效果
        applyVisuals(victim);

        // 发送 DEATH 触发器的包
        dispatchDeathPackets(victim, killer);

        // 调度延迟任务：缓冲结束后执行真正死亡
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            exitBuffer(victim);
        }, config.durationTicks());

        bufferedPlayers.put(victimId, new BufferState(killer, System.currentTimeMillis(), task));

        if (config.debug()) {
            logger.info("DeathBuffer 进入缓冲 | victim=" + victim.getName()
                + " | killer=" + (killer != null ? killer.getName() : "null")
                + " | duration=" + config.durationMs() + "ms");
        }
    }

    private void exitBuffer(Player victim) {
        BufferState state = bufferedPlayers.remove(victim.getUniqueId());
        if (state == null) {
            return;
        }
        if (!victim.isOnline()) {
            return;
        }

        // 清除视觉效果
        clearVisuals(victim);

        // 恢复移动
        victim.setInvulnerable(false);
        victim.setWalkSpeed(0.2f);
        victim.setFlySpeed(0.1f);

        // 执行真正的死亡
        victim.setHealth(0);

        if (config.debug()) {
            logger.info("DeathBuffer 缓冲结束 | victim=" + victim.getName() + " | 执行真正死亡");
        }
    }

    private void forceExitBuffer(Player player, BufferState state, boolean kill) {
        if (state.task != null) {
            state.task.cancel();
        }
        clearVisuals(player);
        player.setInvulnerable(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        if (kill && player.isOnline()) {
            player.setHealth(0);
        }
    }

    // ========== 视觉效果 ==========

    private void applyVisuals(Player player) {
        if (!arcartxVisualAvailable) {
            return;
        }
        try {
            ArcartXPlayer axPlayer = getArcartXPlayer(player);
            if (axPlayer == null) {
                return;
            }

            // Shader 效果（灰度/模糊）
            if (!config.shader().isBlank()) {
                axPlayer.enableShader(config.shader());
            }

            // 第三人称相机
            if (config.thirdPersonCamera()) {
                axPlayer.setThirdPerson(true);
            }

            // 预设相机
            if (!config.cameraPreset().isBlank()) {
                axPlayer.setCameraFromPreset(config.cameraPreset());
            }
        } catch (RuntimeException exception) {
            if (config.debug()) {
                logger.warning("DeathBuffer ArcartX 视觉效果应用失败: " + exception.getMessage());
            }
        }

        // Chronos 死亡状态
        if (chronosAvailable && config.chronosEnabled()) {
            applyChronosDeathState(player);
        }
    }

    private void clearVisuals(Player player) {
        if (!arcartxVisualAvailable) {
            return;
        }
        try {
            ArcartXPlayer axPlayer = getArcartXPlayer(player);
            if (axPlayer == null) {
                return;
            }
            if (!config.shader().isBlank()) {
                axPlayer.disableShader();
            }
            if (config.thirdPersonCamera()) {
                axPlayer.setThirdPerson(false);
            }
            if (!config.cameraPreset().isBlank()) {
                axPlayer.stopSceneCamera();
            }
        } catch (RuntimeException exception) {
            if (config.debug()) {
                logger.warning("DeathBuffer ArcartX 视觉清除失败: " + exception.getMessage());
            }
        }
    }

    private void applyChronosDeathState(Player player) {
        if (chronosHook == null) return;
        chronosHook.tryEnterControlledState(player, config.chronosStateId(), config.durationMs());
    }

    // ========== 包分发 ==========

    private void dispatchDeathPackets(Player victim, Player killer) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }

        // 构建死亡上下文（attacker=killer, target=victim）
        CombatPacketContext context = CombatPacketContext.fromDeath(victim, killer);

        for (PacketDefinition definition : packetConfig.packetDefinitions()) {
            if (!definition.enabled() || !definition.triggers().contains(PacketTrigger.DEATH)) {
                continue;
            }
            for (PacketRecipient recipientType : definition.recipients()) {
                // 在 DEATH 场景下: attacker=killer, target=victim
                Player recipient = recipientType.resolve(killer, victim);
                if (recipient == null || !recipient.isOnline()) {
                    continue;
                }
                Object payload = context.renderPayload(definition.packTemplate(), recipientType, recipient);
                boolean success = packetBridge.sendPacket(
                    recipient, definition.uiId(), definition.packetHandler(), payload
                );
                if (config.debug()) {
                    logger.info("DeathBuffer 发包[" + definition.id() + "] -> " + recipient.getName()
                        + " | ui=" + definition.uiId()
                        + " | success=" + success);
                }
            }
        }
    }

    // ========== 判断逻辑 ==========

    private boolean shouldApply(Player player) {
        if (!config.enabled()) {
            return false;
        }
        if (config.worldBlacklist().contains(player.getWorld().getName())) {
            return false;
        }
        return true;
    }

    // ========== 反射初始化 ==========

    private void initArcartXVisuals() {
        arcartxVisualAvailable = false;
        try {
            org.bukkit.plugin.Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
            if (arcartX == null || !arcartX.isEnabled()) {
                return;
            }
            entityManager = ArcartXAPI.getEntityManager();
            if (entityManager == null) {
                return;
            }
            arcartxVisualAvailable = true;
        } catch (RuntimeException exception) {
            logger.warning("DeathBuffer 初始化 ArcartX 视觉桥接失败: " + exception.getMessage());
        }
    }

    private void initChronos() {
        chronosAvailable = false;
        if (!config.chronosEnabled()) {
            return;
        }
        // 通过工厂延迟加载 ChronosHook，Chronos 未装时返回 null（不触发 ChronosAPI 类加载）
        chronosHook = ChronosHookFactory.tryCreate(plugin, logger, config.debug());
        chronosAvailable = chronosHook != null;
    }

    private ArcartXPlayer getArcartXPlayer(Player player) {
        if (entityManager == null) {
            return null;
        }
        try {
            return entityManager.getPlayer(player);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // ========== 内部状态 ==========

    private record BufferState(Player killer, long startTime, BukkitTask task) {}
}
