package xuanmo.arcartxsuite.combateffect.packet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.api.event.TaczGunKillEvent;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatPacketContext;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketRecipient;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;

public final class CombatEffectPacketService implements Listener {

    private static final String DAMAGE_TOPIC = "axs.combateffect.damage_dealt";

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final List<PacketDefinition> attackPackets;
    private final List<PacketDefinition> killPackets;
    private Supplier<EventBusCapability> eventBusProvider;
    private Function<Player, Player> deferredKiller = player -> null;
    private final Set<UUID> notifiedDeaths = new HashSet<>();
    private BukkitTask clearNotifiedDeaths;

    // cooldown: key = "packetId:playerUUID", value = expiry timestamp
    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public CombatEffectPacketService(
        JavaPlugin plugin,
        CombatEffectPacketConfiguration configuration,
        PacketBridgeAPI packetBridge,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.packetBridge = packetBridge;
        this.logger = logger;
        this.attackPackets = configuration.enabledPackets(PacketTrigger.ATTACK);
        this.killPackets = configuration.enabledPackets(PacketTrigger.KILL);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void setDeferredKiller(Function<Player, Player> deferredKiller) {
        this.deferredKiller = deferredKiller;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        cooldownMap.clear();
        if (clearNotifiedDeaths != null) {
            clearNotifiedDeaths.cancel();
            clearNotifiedDeaths = null;
        }
        notifiedDeaths.clear();
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String suffix = ":" + playerId;
        cooldownMap.keySet().removeIf(key -> key.endsWith(suffix));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim instanceof Player player ? deferredKiller.apply(player) : null;
        if (killer == null) killer = victim.getKiller();
        String deathMessage = event instanceof PlayerDeathEvent pde ? pde.getDeathMessage() : "";
        handleKill(killer, victim, deathMessage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatDamage(CombatDamageEvent event) {
        publishDamageEvent(event);
        if (!attackPackets.isEmpty() && configuration.entityCombatEnabled()) {
            dispatchPackets(PacketTrigger.ATTACK, attackPackets,
                CombatPacketContext.fromAttack(event.getAttacker(), event.getTarget(), event.getDamage()));
        }
    }

    /**
     * 监听 TACZ 枪械击杀事件，发布到 EventBus。
     * <p>
     * TACZ 击杀不保证 {@code LivingEntity.getKiller()} 返回枪手，
     * 必须通过此事件准确获取击杀者。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaczGunKill(TaczGunKillEvent event) {
        handleKill(event.getAttacker(), event.getKilledEntity(), "");
    }

    private void handleKill(Player killer, LivingEntity victim, String deathMessage) {
        if (killer == null || killer.equals(victim) || !notifiedDeaths.add(victim.getUniqueId())) {
            return;
        }
        // TaCZ can emit its kill event after Bukkit has already reported the same death.
        if (clearNotifiedDeaths == null) {
            clearNotifiedDeaths = Bukkit.getScheduler().runTask(plugin, () -> {
                notifiedDeaths.clear();
                clearNotifiedDeaths = null;
            });
        }
        if (!killPackets.isEmpty()) {
            dispatchPackets(PacketTrigger.KILL, killPackets, CombatPacketContext.fromKill(killer, victim, deathMessage));
        }
        publishKillEvent(killer, victim, victim instanceof Player);
    }

    private void dispatchPackets(PacketTrigger trigger, List<PacketDefinition> definitions, CombatPacketContext context) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }
        if (!configuration.shouldProcessTarget(
            context.target() instanceof Player,
            context.targetMythicMobId(),
            context.targetEntityType()
        )) {
            if (configuration.debug()) {
                logger.info(
                    "CombatEffect 已跳过目标 -> trigger=" + trigger.configValue()
                        + " | target=" + EntityCombatMetadata.resolveDisplayName(context.target(), context.targetMythicMobId())
                        + " | mythicMobId=" + context.targetMythicMobId()
                        + " | entityType=" + context.targetEntityType()
                );
            }
            return;
        }

        long now = System.currentTimeMillis();
        for (PacketDefinition definition : definitions) {
            for (PacketRecipient recipientType : definition.recipients()) {
                Player recipient = recipientType.resolve(context.attacker(), context.target());
                if (recipient == null || !recipient.isOnline()) {
                    continue;
                }
                // 冷却检查
                if (definition.hasCooldown() && isOnCooldown(definition.id(), recipient.getUniqueId(), now)) {
                    continue;
                }
                Object payload = context.renderPayload(definition.packTemplate(), recipientType, recipient);
                boolean success = packetBridge.sendPacket(
                    recipient, definition.uiId(), definition.packetHandler(), payload
                );
                // 设置冷却
                if (definition.hasCooldown() && success) {
                    setCooldown(definition.id(), recipient.getUniqueId(), now + definition.cooldownMs());
                }
                if (configuration.debug()) {
                    logger.info(
                        "发包[" + definition.id() + "] -> " + recipient.getName()
                            + " | ui=" + definition.uiId()
                            + " | handler=" + definition.packetHandler()
                            + " | trigger=" + trigger.configValue()
                            + " | success=" + success
                            + " | payload=" + payload
                    );
                }
            }
        }
    }

    private void publishKillEvent(Player killer, LivingEntity victim, boolean isPlayerKill) {
        if (eventBusProvider == null) {
            return;
        }
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) {
            return;
        }
        try {
            String topic = isPlayerKill ? "axs.combateffect.player_death" : "axs.combateffect.kill_entity";
            Map<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("entityType", victim.getType().name());
            payload.put("entityName", victim.getName());
            if (isPlayerKill) {
                payload.put("victim", victim.getName());
            }
            eventBus.publish(topic, killer, payload);
        } catch (Exception e) {
            logger.warning("CombatEffect EventBus 发布失败: " + e.getMessage());
        }
    }

    private void publishDamageEvent(CombatDamageEvent event) {
        EventBusCapability eventBus = eventBusProvider == null ? null : eventBusProvider.get();
        if (eventBus == null) return;
        try {
            if (eventBus.hasSubscribers(DAMAGE_TOPIC)) {
                eventBus.publish(DAMAGE_TOPIC, event.getAttacker(), event.payload());
            }
        } catch (RuntimeException e) {
            logger.warning("CombatEffect EventBus 发布伤害失败: " + e.getMessage());
        }
    }

    // ─── 手动触发 API ─────────────────────────────────────────

    /**
     * 按包 ID 触发已注册的包定义，绕过事件匹配，直接发给指定玩家。
     *
     * @param packetId  包定义 ID
     * @param recipient 目标玩家
     * @param variables 额外变量（合并到 pack 模板渲染中），可为 null
     * @return true 表示发送成功
     */
    public boolean triggerPacketById(String packetId, Player recipient, Map<String, String> variables) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return false;
        }
        PacketDefinition definition = configuration.findPacketById(packetId);
        if (definition == null) {
            logger.warning("手动触发失败: 未找到包定义 '" + packetId + "'");
            return false;
        }
        Object payload;
        if (variables != null && !variables.isEmpty()) {
            payload = new java.util.LinkedHashMap<>(variables);
        } else {
            payload = renderTemplate(definition, recipient, Map.of());
        }
        boolean success = packetBridge.sendPacket(
            recipient, definition.uiId(), definition.packetHandler(), payload
        );
        if (configuration.debug()) {
            logger.info(
                "手动发包[" + packetId + "] -> " + recipient.getName()
                    + " | ui=" + definition.uiId()
                    + " | handler=" + definition.packetHandler()
                    + " | success=" + success
                    + " | payload=" + payload
            );
        }
        return success;
    }

    public boolean triggerConfiguredPacket(String packetId, Player recipient, Map<String, String> variables) {
        if (packetBridge == null || !packetBridge.isAvailable()) return false;
        PacketDefinition definition = configuration.findPacketById(packetId);
        if (definition == null || !definition.enabled()) return false;
        return packetBridge.sendPacket(recipient, definition.uiId(), definition.packetHandler(),
            renderTemplate(definition, recipient, variables));
    }

    private Map<String, Object> renderTemplate(PacketDefinition definition, Player recipient, Map<String, String> variables) {
        Map<String, Object> rendered = new java.util.LinkedHashMap<>();
        if (definition.packTemplate() == null) return rendered;
        definition.packTemplate().forEach((key, value) -> {
            if (value instanceof String text) {
                text = text.replace("{recipient}", recipient.getName()).replace("{player}", recipient.getName());
                for (Map.Entry<String, String> variable : variables.entrySet()) {
                    text = text.replace("{" + variable.getKey() + "}", variable.getValue());
                }
                value = text;
            }
            rendered.put(key, value);
        });
        return rendered;
    }

    /**
     * 直接向玩家发送 UI 包（绕过包定义系统）。
     */
    public boolean triggerDirect(String uiId, String packetHandler, Player recipient, Object payload) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return false;
        }
        boolean success = packetBridge.sendPacket(recipient, uiId, packetHandler, payload != null ? payload : Map.of());
        if (configuration.debug()) {
            logger.info(
                "直接发包 -> " + recipient.getName()
                    + " | ui=" + uiId
                    + " | handler=" + packetHandler
                    + " | success=" + success
            );
        }
        return success;
    }

    /**
     * 获取所有已注册的包定义 ID（供 Tab 补全使用）。
     */
    public List<String> packetIds() {
        return configuration.packetDefinitions().stream()
            .map(PacketDefinition::id)
            .toList();
    }

    // ─── 内部方法 ────────────────────────────────────────────

    private boolean isOnCooldown(String packetId, UUID playerId, long now) {
        String key = packetId + ":" + playerId;
        Long expiry = cooldownMap.get(key);
        if (expiry == null) {
            return false;
        }
        if (now >= expiry) {
            cooldownMap.remove(key);
            return false;
        }
        return true;
    }

    private void setCooldown(String packetId, UUID playerId, long expiryTime) {
        cooldownMap.put(packetId + ":" + playerId, expiryTime);
    }
}



