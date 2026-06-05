package xuanmo.arcartxsuite.combateffect.display.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.bridge.ArcartXClientBridge;
import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayConfiguration;

public final class CombatDisplayService {

    private final JavaPlugin plugin;
    private final CombatDisplayConfiguration configuration;
    private final ArcartXClientBridge clientBridge;
    private final AttributeBridgeRegistry attributeBridge;
    private final List<Listener> registeredListeners = new ArrayList<>();

    private xuanmo.arcartxsuite.api.attribute.AttributeDamageListener attributeDamageListener;
    private boolean mythicHealHooked;
    private CombatDisplayDamageSource activeDamageSource = CombatDisplayDamageSource.NONE;

    public CombatDisplayService(
        JavaPlugin plugin,
        CombatDisplayConfiguration configuration,
        ArcartXClientBridge clientBridge,
        AttributeBridgeRegistry attributeBridge
    ) {
        this.plugin = plugin;
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
                    handleAttributeDamage(event.target(), event.damage(), event.source());
                };
                attributeBridge.registerDamageListener(attributeDamageListener);
                debugDamageSource("已注册统一属性伤害监听，active=" + activeDamageSource);
            }
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
        handleStandardDamage(event.getEntity(), event.getFinalDamage());
    }

    void handleTaczDamage(xuanmo.arcartxsuite.api.event.TaczGunDamageEvent event) {
        handleStandardDamage(event.getTarget(), event.getDamage());
    }

    void handleMythicLibDamage(Entity target, double damage) {
        if (activeDamageSource != CombatDisplayDamageSource.MYTHICLIB || !configuration.mythicLibDamageEnabled()) {
            return;
        }
        if (target instanceof Player) {
            if (damage >= configuration.mythicLibPlayerDamageMinAmount()) {
                sendToViewers(target, configuration.mythicLibPlayerDamageConfigId(), damage);
            }
            return;
        }
        if (damage < configuration.mythicLibDamageMinAmount()) {
            return;
        }
        sendToViewers(target, configuration.mythicLibDamageConfigId(), damage);
    }

    private void handleCraneAttributeDamage(Entity target, double damage) {
        if (activeDamageSource != CombatDisplayDamageSource.CRANEATTRIBUTE || !configuration.craneAttributeDamageEnabled()) {
            return;
        }
        if (target instanceof Player) {
            if (damage >= configuration.craneAttributePlayerDamageMinAmount()) {
                sendToViewers(target, configuration.craneAttributePlayerDamageConfigId(), damage);
            }
            return;
        }
        if (damage < configuration.craneAttributeDamageMinAmount()) {
            return;
        }
        sendToViewers(target, configuration.craneAttributeDamageConfigId(), damage);
    }

    private void handleAttributeDamage(Entity target, double damage, AttributeDamageEvent.Source source) {
        switch (source) {
            case ATTRIBUTE_PLUS -> handleStandardDamage(target, damage);
            case CRANE_ATTRIBUTE -> handleCraneAttributeDamage(target, damage);
            case MYTHIC_LIB -> handleMythicLibDamage(target, damage);
            default -> {}
        }
    }

    private CombatDisplayDamageSource resolveActiveSource() {
        boolean apAvailable = attributeBridge.attributePlus().available()
            && configuration.damageAttributePlusCompatible();
        boolean caAvailable = attributeBridge.craneAttribute().available()
            && configuration.craneAttributeDamageEnabled();
        boolean mlAvailable = attributeBridge.mythicLib().available()
            && configuration.mythicLibDamageEnabled();
        boolean bukkitAvailable = configuration.damageEnabled() || configuration.playerDamageEnabled();
        return CombatDisplayDamageSourceResolver.resolve(
            configuration.damageSourceMode(),
            configuration.damageSourceFallback(),
            mlAvailable,
            caAvailable,
            apAvailable,
            bukkitAvailable
        );
    }

    private boolean matchesActiveSource(AttributeDamageEvent.Source source) {
        return switch (activeDamageSource) {
            case MYTHICLIB -> source == AttributeDamageEvent.Source.MYTHIC_LIB;
            case CRANEATTRIBUTE -> source == AttributeDamageEvent.Source.CRANE_ATTRIBUTE;
            case ATTRIBUTEPLUS -> source == AttributeDamageEvent.Source.ATTRIBUTE_PLUS;
            default -> false;
        };
    }

    private void handleStandardDamage(Entity target, double damage) {
        if (target instanceof Player) {
            if (configuration.playerDamageEnabled() && damage >= configuration.playerDamageMinAmount()) {
                sendToViewers(target, configuration.playerDamageConfigId(), damage);
            }
            return;
        }

        if (!configuration.damageEnabled()) {
            return;
        }
        if (damage < configuration.damageMinAmount()) {
            return;
        }
        sendToViewers(target, configuration.damageConfigId(), damage);
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
        sendToViewers(player, configuration.healConfigId(), event.getAmount());
    }

    public void handleMythicHeal(Player player, double healAmount) {
        if (!configuration.mythicHealEnabled() || player == null) {
            return;
        }

        double displayAmount = healAmount;
        if (configuration.mythicHealExactMode()) {
            double currentHealth = player.getHealth();
            double maxHealth = Objects.requireNonNull(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)).getValue();
            displayAmount = Math.min(currentHealth + healAmount, maxHealth) - currentHealth;
        }
        if (displayAmount <= configuration.mythicHealMinAmount()) {
            return;
        }
        sendToViewers(player, configuration.mythicHealConfigId(), displayAmount);
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
            plugin.getLogger().warning("CombatDisplay 检测到 MythicMobs，但当前服务端未提供 Mythic 治疗事件类: " + error.getMessage());
        }
    }

    private static boolean isAnyPluginEnabled(String name) {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled(name);
    }

    private void debugDamageSource(String message) {
        if (configuration.damageSourceDebug()) {
            plugin.getLogger().info(
                "CombatDisplay 伤害来源调试: "
                    + message
                    + " | fallback="
                    + configuration.damageSourceFallback()
                    + " | active="
                    + activeDamageSource
            );
        }
    }

    private void sendToViewers(Entity target, String configId, double amount) {
        if (target == null || configId == null || configId.isBlank() || !clientBridge.isAvailable()) {
            return;
        }
        clientBridge.forEachSeenPlayer(target, viewer -> clientBridge.sendDamageDisplay(viewer, configId, amount, target));
    }
}


