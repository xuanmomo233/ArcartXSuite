package xuanmo.arcartxsuite.combateffect.deathbuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AbyssQueryable;
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
    public static final String BUFFER_UI_ID = "AXS:death_buffer";

    private final JavaPlugin plugin;
    private final DeathBufferConfiguration config;
    private final CombatEffectPacketConfiguration packetConfig;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final Supplier<AbyssQueryable> abyss;

    private final Map<UUID, BufferState> bufferedPlayers = new HashMap<>();
    private final Map<UUID, Player> settlingKillers = new HashMap<>();

    // ArcartX 视觉桥接（ArcartX 是硬依赖，可直接引用）
    private boolean arcartxVisualAvailable;
    private ArcartXEntityManager entityManager;

    public DeathBufferService(
        JavaPlugin plugin,
        DeathBufferConfiguration config,
        CombatEffectPacketConfiguration packetConfig,
        PacketBridgeAPI packetBridge,
        Logger logger,
        Supplier<AbyssQueryable> abyss
    ) {
        this.plugin = plugin;
        this.config = config;
        this.packetConfig = packetConfig;
        this.packetBridge = packetBridge;
        this.logger = logger;
        this.abyss = abyss;
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        initArcartXVisuals();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (config.debug()) {
            logger.info("DeathBuffer 已启动 | shader=" + config.shader()
                + " | camera=" + config.cameraPreset()
                + " | duration=" + config.durationMs() + "ms");
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (BufferState state : List.copyOf(bufferedPlayers.values())) {
            finish(state.victim, false);
        }
    }

    public boolean isInBuffer(Player player) {
        return bufferedPlayers.containsKey(player.getUniqueId());
    }

    public Player settlingKiller(Player victim) {
        return settlingKillers.get(victim.getUniqueId());
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
        if (inAbyss(victim)) {
            finish(victim, false);
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
        if (healthAfter > 0 || canUseTotem(victim, event.getCause())) {
            return; // 非致死伤害
        }

        // 确定击杀者
        Player killer = victim.getKiller();
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            Player attacker = CombatEventSupport.resolvePlayerAttacker(entityEvent);
            if (attacker != null) killer = attacker;
        }

        enterBuffer(victim, killer, event);
    }

    /**
     * 拦截 PlayerDeathEvent 作为安全网 —— 正常情况下不应触发，
     * 因为我们已在 EntityDamageEvent 阶段拦截。
     * 但如果有其他插件在 MONITOR 设置了血量或有 instakill，这里做兜底。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        finish(event.getEntity(), false);
    }

    /**
     * 玩家退出时清理缓冲状态。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        finish(event.getPlayer(), true);
    }

    // ========== 核心流程 ==========

    private void enterBuffer(Player victim, Player killer, EntityDamageEvent damage) {
        UUID victimId = victim.getUniqueId();
        BufferState state = new BufferState(victim, killer, damage);
        bufferedPlayers.put(victimId, state);
        try {
            state.task = Bukkit.getScheduler().runTaskLater(plugin, () -> finish(victim, true), config.durationTicks());
            victim.setInvulnerable(true);
            victim.setWalkSpeed(0f);
            victim.setFlySpeed(0f);
        } catch (RuntimeException e) {
            finish(victim, false);
            logger.warning("DeathBuffer 无法开始缓冲: " + e);
            return;
        }
        damage.setCancelled(true);
        applyVisuals(state);
        applyChronosDeathState(victim);
        if (bufferedPlayers.get(victimId) == state) visual(() -> dispatchDeathPackets(state));
        if (config.debug()) {
            logger.info("DeathBuffer 进入缓冲 | victim=" + victim.getName()
                + " | killer=" + (killer != null ? killer.getName() : "null")
                + " | duration=" + config.durationMs() + "ms");
        }
    }

    private void finish(Player victim, boolean settleDeath) {
        BufferState state = bufferedPlayers.remove(victim.getUniqueId());
        if (state == null) return;
        if (state.task != null) state.task.cancel();
        if (victim.isInvulnerable()) victim.setInvulnerable(state.invulnerable);
        if (victim.getWalkSpeed() == 0f) victim.setWalkSpeed(state.walkSpeed);
        if (victim.getFlySpeed() == 0f) victim.setFlySpeed(state.flySpeed);
        clearVisuals(state);
        if (!settleDeath || victim.isDead() || !shouldApply(victim) || inAbyss(victim)) return;

        victim.setLastDamageCause(state.damage);
        if (state.killer != null) {
            settlingKillers.put(victim.getUniqueId(), state.killer);
            setPaperKiller(victim, state.killer);
        }
        try {
            victim.setHealth(0);
        } finally {
            settlingKillers.remove(victim.getUniqueId());
        }
    }

    private void setPaperKiller(Player victim, Player killer) {
        try {
            victim.getClass().getMethod("setKiller", Player.class).invoke(victim, killer);
        } catch (NoSuchMethodException ignored) {
            // Spigot uses the captured killer for CombatEffect dispatch.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.fine("DeathBuffer 无法写入 Paper 击杀者: " + e);
        }
    }

    // ========== 视觉效果 ==========

    private void applyVisuals(BufferState state) {
        ArcartXPlayer player = getArcartXPlayer(state.victim);
        if (player == null) return;
        if (!config.shader().isBlank()) {
            state.shader = visual(() -> player.enableShader(config.shader()));
        }
        if (config.thirdPersonCamera()) {
            state.thirdPerson = visual(() -> player.setThirdPerson(true));
        }
        if (!config.cameraPreset().isBlank()) {
            state.camera = visual(() -> player.setCameraFromPreset(config.cameraPreset()));
        }
    }

    private void clearVisuals(BufferState state) {
        if (state.bufferUi) visual(() -> packetBridge.closeUi(state.victim, BUFFER_UI_ID));
        ArcartXPlayer player = getArcartXPlayer(state.victim);
        if (player == null) return;
        if (state.shader) visual(player::disableShader);
        if (state.thirdPerson) visual(() -> player.setThirdPerson(false));
        if (state.camera) visual(player::stopSceneCamera);
    }

    private boolean visual(Runnable effect) {
        try {
            effect.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (config.debug()) {
                logger.warning("DeathBuffer 视觉效果失败: " + e);
            }
            return false;
        }
    }

    private void applyChronosDeathState(Player player) {
        if (!config.chronosEnabled() || config.chronosStateId().isBlank()) return;
        ChronosAccess access = ChronosAccess.probe().access();
        if (access == null) return;
        try {
            access.enterControlledState(player, config.chronosStateId(), config.durationMs());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (config.debug()) logger.warning("DeathBuffer Chronos 状态调用失败: " + e);
        }
    }

    // ========== 包分发 ==========

    private void dispatchDeathPackets(BufferState state) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }

        // 构建死亡上下文（attacker=killer, target=victim）
        Player victim = state.victim;
        Player killer = state.killer;
        CombatPacketContext context = CombatPacketContext.fromDeath(victim, killer, state.damage.getFinalDamage());

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
                boolean success;
                try {
                    success = packetBridge.sendPacket(recipient, definition.uiId(), definition.packetHandler(), payload);
                } catch (RuntimeException | LinkageError e) {
                    logger.fine("DeathBuffer 发包失败: " + e);
                    continue;
                }
                if (success && victim.equals(recipient) && BUFFER_UI_ID.equals(definition.uiId())) {
                    state.bufferUi = true;
                }
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
        return player.getGameMode() != GameMode.SPECTATOR
            && !config.worldBlacklist().contains(player.getWorld().getName());
    }

    private boolean inAbyss(Player player) {
        AbyssQueryable query = abyss.get();
        return query != null && query.isInDungeon(player.getUniqueId());
    }

    private static boolean canUseTotem(Player player, EntityDamageEvent.DamageCause cause) {
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause.name().equals("KILL")) return false;
        var inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || inventory.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
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
        } catch (RuntimeException | LinkageError exception) {
            logger.warning("DeathBuffer 初始化 ArcartX 视觉桥接失败: " + exception.getMessage());
        }
    }

    private ArcartXPlayer getArcartXPlayer(Player player) {
        if (!arcartxVisualAvailable || entityManager == null) {
            return null;
        }
        try {
            return entityManager.getPlayer(player);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    // ========== 内部状态 ==========

    private static final class BufferState {
        private final Player victim;
        private final Player killer;
        private final EntityDamageEvent damage;
        private final boolean invulnerable;
        private final float walkSpeed;
        private final float flySpeed;
        private BukkitTask task;
        private boolean shader;
        private boolean thirdPerson;
        private boolean camera;
        private boolean bufferUi;

        private BufferState(Player victim, Player killer, EntityDamageEvent damage) {
            this.victim = victim;
            this.killer = killer;
            this.damage = damage;
            this.invulnerable = victim.isInvulnerable();
            this.walkSpeed = victim.getWalkSpeed();
            this.flySpeed = victim.getFlySpeed();
        }
    }
}
