package xuanmo.arcartxsuite.combateffect.display.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeHealEvent;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayConfiguration;
import java.util.logging.Logger;

public final class CombatDisplayService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CombatDisplayConfiguration configuration;
    private final ClientBridgeAPI clientBridge;
    private final AttributeBridgeRegistry attributeBridge;
    private final List<Listener> registeredListeners = new ArrayList<>();

    private xuanmo.arcartxsuite.api.attribute.AttributeDamageListener attributeDamageListener;
    private xuanmo.arcartxsuite.api.attribute.AttributeHealListener attributeHealListener;
    private boolean mythicHealHooked;
    private CombatDisplayDamageSource activeDamageSource = CombatDisplayDamageSource.NONE;
    private final Map<MergeKey, MergeEntry> damageMergeBuffer = new ConcurrentHashMap<>();
    private final Map<HealMergeKey, HealMergeEntry> healMergeBuffer = new ConcurrentHashMap<>();

    private record MergeKey(UUID attackerId, UUID targetId, String configId) {}
    private record HealMergeKey(UUID targetId, String configId) {}

    private static final class HealMergeEntry {
        final LivingEntity target;
        final String configId;
        double amount;
        final BukkitTask task;

        HealMergeEntry(LivingEntity target, String configId, double amount, BukkitTask task) {
            this.target = target;
            this.configId = configId;
            this.amount = amount;
            this.task = task;
        }
    }

    private static final class MergeEntry {
        final Player attacker;
        final Entity target;
        final String configId;
        double amount;
        final BukkitTask task;

        MergeEntry(Player attacker, Entity target, String configId, double amount, BukkitTask task) {
            this.attacker = attacker;
            this.target = target;
            this.configId = configId;
            this.amount = amount;
            this.task = task;
        }
    }

    public CombatDisplayService(
        JavaPlugin plugin,
        Logger logger,
        CombatDisplayConfiguration configuration,
        ClientBridgeAPI clientBridge,
        AttributeBridgeRegistry attributeBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.clientBridge = clientBridge;
        this.attributeBridge = attributeBridge;
    }

    public void start() {
        registerListener(new CombatDisplayDamageListener(this));
        registerListener(new CombatDisplayHealListener(this));
        registerListener(new CombatDisplayTaczListener(this));
        registerMythicHealListenerIfAvailable();

        if (attributeBridge != null && attributeBridge.hasDamageSource()) {
            activeDamageSource = resolveActiveSource();
            if (activeDamageSource != CombatDisplayDamageSource.NONE
                && activeDamageSource != CombatDisplayDamageSource.BUKKIT) {
                attributeDamageListener = event -> {
                    if (!matchesActiveSource(event.source())) return;
                    handleAttributeDamage(event.attacker(), event.target(), event.damage(), event.source());
                };
                attributeBridge.registerDamageListener(attributeDamageListener);
                debugDamageSource("已注册统一属性伤害监听，active=" + activeDamageSource);
            }
        }

        if (attributeBridge != null && attributeBridge.hasHealSource()) {
            attributeHealListener = event -> handleAttributeHeal(event);
            attributeBridge.registerHealListener(attributeHealListener);
            this.logger.fine("CombatDisplay 已注册统一属性治疗监听");
        }
    }

    public void shutdown() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        if (attributeBridge != null && attributeDamageListener != null) {
            attributeBridge.unregisterDamageListener(attributeDamageListener);
            attributeDamageListener = null;
        }
        for (MergeEntry entry : damageMergeBuffer.values()) {
            if (entry.task != null) entry.task.cancel();
        }
        damageMergeBuffer.clear();
        for (HealMergeEntry entry : healMergeBuffer.values()) {
            if (entry.task != null) entry.task.cancel();
        }
        healMergeBuffer.clear();
        if (attributeBridge != null && attributeHealListener != null) {
            attributeBridge.unregisterHealListener(attributeHealListener);
            attributeHealListener = null;
        }
        mythicHealHooked = false;
        activeDamageSource = CombatDisplayDamageSource.NONE;
    }

    public CombatDisplayConfiguration configuration() {
        return configuration;
    }

    public boolean mythicHealHooked() {
        return mythicHealHooked;
    }

    public CombatDisplayDamageSource activeDamageSource() {
        return activeDamageSource;
    }

    void handleDamage(EntityDamageByEntityEvent event) {
        if (activeDamageSource != CombatDisplayDamageSource.NONE
            && activeDamageSource != CombatDisplayDamageSource.BUKKIT) {
            return;
        }
        handleStandardDamage(resolveBukkitAttacker(event), event.getEntity(), event.getFinalDamage());
    }

    private Player resolveBukkitAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    void handleTaczDamage(xuanmo.arcartxsuite.api.event.TaczGunDamageEvent event) {
        if (activeDamageSource != CombatDisplayDamageSource.NONE
            && activeDamageSource != CombatDisplayDamageSource.BUKKIT) {
            return;
        }
        handleStandardDamage(event.getAttacker(), event.getTarget(), event.getDamage());
    }

    void handleMythicLibDamage(Player attacker, Entity target, double damage) {
        if (attacker == null) {
            return;
        }
        if (activeDamageSource != CombatDisplayDamageSource.MYTHICLIB || !configuration.mythicLibDamageEnabled()) {
            return;
        }
        if (target instanceof Player) {
            if (damage >= configuration.mythicLibPlayerDamageMinAmount()) {
                trySendDamageDisplay(attacker, target, configuration.mythicLibPlayerDamageConfigId(), damage);
            }
            return;
        }
        if (damage < configuration.mythicLibDamageMinAmount()) {
            return;
        }
        trySendDamageDisplay(attacker, target, configuration.mythicLibDamageConfigId(), damage);
    }

    private void handleCraneAttributeDamage(Player attacker, Entity target, double damage) {
        if (attacker == null) {
            return;
        }
        if (activeDamageSource != CombatDisplayDamageSource.CRANEATTRIBUTE || !configuration.craneAttributeDamageEnabled()) {
            return;
        }
        if (target instanceof Player) {
            if (damage >= configuration.craneAttributePlayerDamageMinAmount()) {
                trySendDamageDisplay(attacker, target, configuration.craneAttributePlayerDamageConfigId(), damage);
            }
            return;
        }
        if (damage < configuration.craneAttributeDamageMinAmount()) {
            return;
        }
        trySendDamageDisplay(attacker, target, configuration.craneAttributeDamageConfigId(), damage);
    }

    private void handleAttributeDamage(Player attacker, Entity target, double damage, AttributeDamageEvent.Source source) {
        if (attacker == null) {
            return;
        }
        switch (source) {
            case ATTRIBUTE_PLUS -> handleStandardDamage(attacker, target, damage);
            case CRANE_ATTRIBUTE -> handleCraneAttributeDamage(attacker, target, damage);
            case MYTHIC_LIB -> handleMythicLibDamage(attacker, target, damage);
            case SYMPHONY -> handleSymphonyDamage(attacker, target, damage);
            default -> {}
        }
    }

    private void handleSymphonyDamage(Player attacker, Entity target, double damage) {
        if (attacker == null) {
            return;
        }
        if (activeDamageSource != CombatDisplayDamageSource.SYMPHONY || !configuration.symphonyDamageEnabled()) {
            return;
        }
        if (target instanceof Player) {
            if (damage >= configuration.symphonyPlayerDamageMinAmount()) {
                trySendDamageDisplay(attacker, target, configuration.symphonyPlayerDamageConfigId(), damage);
            }
            return;
        }
        if (damage < configuration.symphonyDamageMinAmount()) {
            return;
        }
        trySendDamageDisplay(attacker, target, configuration.symphonyDamageConfigId(), damage);
    }

    private CombatDisplayDamageSource resolveActiveSource() {
        boolean apAvailable = attributeBridge.attributePlus().available()
            && configuration.damageAttributePlusCompatible();
        boolean caAvailable = attributeBridge.craneAttribute().available()
            && configuration.craneAttributeDamageEnabled();
        boolean mlAvailable = attributeBridge.mythicLib().available()
            && configuration.mythicLibDamageEnabled();
        boolean symAvailable = attributeBridge.symphony().available()
            && configuration.symphonyDamageEnabled();
        boolean bukkitAvailable = configuration.damageEnabled() || configuration.playerDamageEnabled();
        return CombatDisplayDamageSourceResolver.resolve(
            configuration.damageSourceMode(),
            configuration.damageSourceFallback(),
            mlAvailable,
            caAvailable,
            apAvailable,
            symAvailable,
            bukkitAvailable
        );
    }

    private boolean matchesActiveSource(AttributeDamageEvent.Source source) {
        return switch (activeDamageSource) {
            case MYTHICLIB -> source == AttributeDamageEvent.Source.MYTHIC_LIB;
            case CRANEATTRIBUTE -> source == AttributeDamageEvent.Source.CRANE_ATTRIBUTE;
            case ATTRIBUTEPLUS -> source == AttributeDamageEvent.Source.ATTRIBUTE_PLUS;
            case SYMPHONY -> source == AttributeDamageEvent.Source.SYMPHONY;
            default -> false;
        };
    }

    private void handleStandardDamage(Player attacker, Entity target, double damage) {
        if (attacker == null) {
            return;
        }
        if (target instanceof Player) {
            if (configuration.playerDamageEnabled() && damage >= configuration.playerDamageMinAmount()) {
                trySendDamageDisplay(attacker, target, configuration.playerDamageConfigId(), damage);
            }
            return;
        }

        if (!configuration.damageEnabled()) {
            return;
        }
        if (damage < configuration.damageMinAmount()) {
            return;
        }
        trySendDamageDisplay(attacker, target, configuration.damageConfigId(), damage);
    }

    void handleHeal(EntityRegainHealthEvent event) {
        if (!configuration.healEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getAmount() < configuration.healMinAmount()) {
            return;
        }
        trySendHealDisplay(player, configuration.healConfigId(), event.getAmount());
    }

    public void handleMythicHeal(Player player, double healAmount) {
        if (!configuration.mythicHealEnabled() || player == null) {
            return;
        }

        double displayAmount = healAmount;
        if (configuration.mythicHealExactMode()) {
            double currentHealth = player.getHealth();
            double maxHealth = AttributeResolver.getMaxHealth(player);
            displayAmount = Math.min(currentHealth + healAmount, maxHealth) - currentHealth;
        }
        if (displayAmount <= configuration.mythicHealMinAmount()) {
            return;
        }
        trySendHealDisplay(player, configuration.mythicHealConfigId(), displayAmount);
    }

    private void handleAttributeHeal(AttributeHealEvent event) {
        if (!configuration.healEnabled()) {
            return;
        }
        if (!(event.target() instanceof Player player)) {
            return;
        }
        if (event.amount() < configuration.healMinAmount()) {
            return;
        }
        trySendHealDisplay(player, configuration.healConfigId(), event.amount());
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    private void registerMythicHealListenerIfAvailable() {
        if (!configuration.mythicHealEnabled() || !(isAnyPluginEnabled("MythicMobs") || isAnyPluginEnabled("MythicBukkit"))) {
            return;
        }
        try {
            Listener listener = new CombatDisplayMythicHealListener(this);
            registerListener(listener);
            mythicHealHooked = true;
        } catch (NoClassDefFoundError error) {
            this.logger.warning("CombatDisplay 检测到 MythicMobs，但当前服务端未提供 Mythic 治疗事件类: " + error.getMessage());
        }
    }

    private static boolean isAnyPluginEnabled(String name) {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled(name);
    }

    private void debugDamageSource(String message) {
        if (configuration.damageSourceDebug()) {
            this.logger.info(
                "CombatDisplay 伤害来源调试: "
                    + message
                    + " | fallback="
                    + configuration.damageSourceFallback()
                    + " | active="
                    + activeDamageSource
            );
        }
    }

    private void trySendDamageDisplay(Player attacker, Entity target, String configId, double amount) {
        if (target == null || configId == null || configId.isBlank() || !clientBridge.isAvailable()) {
            return;
        }
        if (!configuration.damageMergeEnabled()) {
            sendDamageToViewer(attacker, target, configId, amount);
            return;
        }
        MergeKey key = new MergeKey(
            attacker != null ? attacker.getUniqueId() : null,
            target.getUniqueId(),
            configId
        );
        MergeEntry existing = damageMergeBuffer.get(key);
        if (existing != null) {
            existing.amount += amount;
            return;
        }
        if (damageMergeBuffer.size() >= configuration.damageMergeMaxEntries()) {
            sendDamageToViewer(attacker, target, configId, amount);
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> flushMergedDamage(key), configuration.damageMergeWindowTicks());
        damageMergeBuffer.put(key, new MergeEntry(attacker, target, configId, amount, task));
    }

    private void flushMergedDamage(MergeKey key) {
        MergeEntry entry = damageMergeBuffer.remove(key);
        if (entry == null) return;
        if (!entry.target.isValid() || entry.target.isDead()) return;
        if (entry.amount < configuration.damageMergeMinAmount()) return;
        sendDamageToViewer(entry.attacker, entry.target, entry.configId, entry.amount);
    }

    private void trySendHealDisplay(LivingEntity target, String configId, double amount) {
        if (target == null || configId == null || configId.isBlank() || !clientBridge.isAvailable()) {
            return;
        }
        if (!configuration.healMergeEnabled()) {
            sendToViewers(target, configId, amount);
            return;
        }
        HealMergeKey key = new HealMergeKey(target.getUniqueId(), configId);
        HealMergeEntry existing = healMergeBuffer.get(key);
        if (existing != null) {
            existing.amount += amount;
            return;
        }
        if (healMergeBuffer.size() >= configuration.healMergeMaxEntries()) {
            sendToViewers(target, configId, amount);
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> flushMergedHeal(key), configuration.healMergeWindowTicks());
        healMergeBuffer.put(key, new HealMergeEntry(target, configId, amount, task));
    }

    private void flushMergedHeal(HealMergeKey key) {
        HealMergeEntry entry = healMergeBuffer.remove(key);
        if (entry == null) return;
        if (!entry.target.isValid() || entry.target.isDead()) return;
        if (entry.amount < configuration.healMergeMinAmount()) return;
        sendToViewers(entry.target, entry.configId, entry.amount);
    }

    private void sendDamageToViewer(Player attacker, Entity target, String configId, double amount) {
        if (target == null || configId == null || configId.isBlank() || !clientBridge.isAvailable()) {
            return;
        }
        if (attacker != null && attacker.isOnline()) {
            clientBridge.sendDamageDisplay(attacker, configId, amount, target);
        }
        if (configuration.showOthersDamage()) {
            clientBridge.forEachSeenPlayer(target, viewer -> {
                if (attacker == null || !viewer.equals(attacker)) {
                    clientBridge.sendDamageDisplay(viewer, configId, amount, target);
                }
            });
        }
    }

    private void sendToViewers(Entity target, String configId, double amount) {
        if (target == null || configId == null || configId.isBlank() || !clientBridge.isAvailable()) {
            return;
        }
        clientBridge.forEachSeenPlayer(target, viewer -> clientBridge.sendDamageDisplay(viewer, configId, amount, target));
    }
}




