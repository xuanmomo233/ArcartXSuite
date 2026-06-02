package xuanmo.arcartxsuite.combateffect.packet;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatPacketContext;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketDefinition;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketRecipient;
import xuanmo.arcartxsuite.combateffect.packet.config.PacketTrigger;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;

public final class CombatEffectPacketService implements Listener {

    private final JavaPlugin plugin;
    private final CombatEffectPacketConfiguration configuration;
    private final ArcartXPacketBridge packetBridge;
    private final Logger logger;

    // cooldown: key = "packetId:playerUUID", value = expiry timestamp
    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public CombatEffectPacketService(
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

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        String deathMessage = event instanceof PlayerDeathEvent pde ? pde.getDeathMessage() : "";
        dispatchPackets(PacketTrigger.KILL, CombatPacketContext.fromKill(killer, victim, deathMessage));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player attacker = CombatEventSupport.resolvePlayerAttacker(event);
        if (attacker == null || attacker.equals(target)) {
            return;
        }
        dispatchPackets(PacketTrigger.ATTACK, CombatPacketContext.fromAttack(attacker, target, event));
    }

    private void dispatchPackets(PacketTrigger trigger, CombatPacketContext context) {
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
        for (PacketDefinition definition : configuration.packetDefinitions()) {
            if (!definition.enabled() || !definition.triggers().contains(trigger)) {
                continue;
            }
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
        } else if (definition.packTemplate() != null && !definition.packTemplate().isEmpty()) {
            // 使用模板但无上下文，只做简单变量替换
            Map<String, Object> rendered = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : definition.packTemplate().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text) {
                    value = text
                        .replace("{recipient}", recipient.getName())
                        .replace("{player}", recipient.getName());
                    if (variables != null) {
                        for (Map.Entry<String, String> var : variables.entrySet()) {
                            value = ((String) value).replace("{" + var.getKey() + "}", var.getValue());
                        }
                    }
                }
                rendered.put(entry.getKey(), value);
            }
            payload = rendered;
        } else {
            payload = Map.of();
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



