package xuanmo.arcartxsuite.combateffect.display.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ArcartXClientBridge;
import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayConfiguration;

public final class CombatDisplayService {

    private final JavaPlugin plugin;
    private final CombatDisplayConfiguration configuration;
    private final ArcartXClientBridge clientBridge;
    private final List<Listener> registeredListeners = new ArrayList<>();

    private Listener attributePlusListener;
    private Listener craneAttributeListener;
    private boolean attributePlusHooked;
    private boolean craneAttributeHooked;
    private boolean mythicLibDamageHooked;
    private boolean mythicHealHooked;
    private CombatDisplayDamageSource activeDamageSource = CombatDisplayDamageSource.NONE;

    public CombatDisplayService(
        JavaPlugin plugin,
        CombatDisplayConfiguration configuration,
        ArcartXClientBridge clientBridge
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.clientBridge = clientBridge;
    }

    public void start() {
        registerDamageSourceListener();
        registerListener(new CombatDisplayHealListener(this));
        registerMythicHealListenerIfAvailable();
    }

    public void shutdown() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        attributePlusListener = null;
        craneAttributeListener = null;
        attributePlusHooked = false;
        craneAttributeHooked = false;
        mythicLibDamageHooked = false;
        mythicHealHooked = false;
        activeDamageSource = CombatDisplayDamageSource.NONE;
    }

    public CombatDisplayConfiguration configuration() {
        return configuration;
    }

    public boolean attributePlusHooked() {
        return attributePlusHooked;
    }

    public boolean craneAttributeHooked() {
        return craneAttributeHooked;
    }

    public boolean mythicLibDamageHooked() {
        return mythicLibDamageHooked;
    }

    public boolean mythicHealHooked() {
        return mythicHealHooked;
    }

    public CombatDisplayDamageSource activeDamageSource() {
        return activeDamageSource;
    }

    void handleDamage(EntityDamageByEntityEvent event) {
        if (activeDamageSource != CombatDisplayDamageSource.BUKKIT) {
            return;
        }
        handleStandardDamage(event.getEntity(), event.getFinalDamage());
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

    private void registerDamageSourceListener() {
        boolean mythicLibAvailable = configuration.mythicLibDamageEnabled() && isAnyPluginEnabled("MythicLib");
        boolean craneAttributeAvailable = configuration.craneAttributeDamageEnabled() && isAnyPluginEnabled("CraneAttribute");
        boolean attributePlusAvailable = isAttributePlusDamageConfigured() && isAnyPluginEnabled("AttributePlus");
        boolean bukkitAvailable = configuration.damageEnabled() || configuration.playerDamageEnabled();

        for (int attempt = 0; attempt < 4; attempt++) {
            CombatDisplayDamageSource selected = CombatDisplayDamageSourceResolver.resolve(
                configuration.damageSourceMode(),
                configuration.damageSourceFallback(),
                mythicLibAvailable,
                craneAttributeAvailable,
                attributePlusAvailable,
                bukkitAvailable
            );
            if (selected == CombatDisplayDamageSource.NONE) {
                activeDamageSource = CombatDisplayDamageSource.NONE;
                debugDamageSource("未启用伤害来源。mode=" + configuration.damageSourceMode());
                return;
            }
            if (selected == CombatDisplayDamageSource.MYTHICLIB) {
                if (registerMythicLibDamageListenerIfAvailable()) {
                    activeDamageSource = selected;
                    debugDamageSource("已启用 MythicLib/MMOItems 伤害来源。");
                    return;
                }
                mythicLibAvailable = false;
                continue;
            }
            if (selected == CombatDisplayDamageSource.CRANEATTRIBUTE) {
                if (registerCraneAttributeListenerIfAvailable()) {
                    activeDamageSource = selected;
                    debugDamageSource("已启用 CraneAttribute 伤害来源。");
                    return;
                }
                craneAttributeAvailable = false;
                continue;
            }
            if (selected == CombatDisplayDamageSource.ATTRIBUTEPLUS) {
                if (registerAttributePlusListenerIfAvailable()) {
                    activeDamageSource = selected;
                    debugDamageSource("已启用 AttributePlus 伤害来源。");
                    return;
                }
                attributePlusAvailable = false;
                continue;
            }

            registerListener(new CombatDisplayDamageListener(this));
            activeDamageSource = selected;
            debugDamageSource("已启用 Bukkit 原版伤害来源。");
            return;
        }

        activeDamageSource = CombatDisplayDamageSource.NONE;
        debugDamageSource("伤害来源注册失败，已关闭 CombatDisplay 伤害显示。");
    }

    private boolean isAttributePlusDamageConfigured() {
        return configuration.damageAttributePlusCompatible()
            && (configuration.damageEnabled() || configuration.playerDamageEnabled());
    }

    private static boolean isAnyPluginEnabled(String name) {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled(name);
    }

    private boolean registerMythicLibDamageListenerIfAvailable() {
        mythicLibDamageHooked = false;
        if (!configuration.mythicLibDamageEnabled() || !isAnyPluginEnabled("MythicLib")) {
            return false;
        }
        try {
            Listener listener = new CombatDisplayMythicLibDamageListener(this);
            registerListener(listener);
            mythicLibDamageHooked = true;
            return true;
        } catch (NoClassDefFoundError error) {
            plugin.getLogger().warning("CombatDisplay 检测到 MythicLib，但当前服务端未提供 MythicLib 伤害事件类: " + error.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean registerCraneAttributeListenerIfAvailable() {
        craneAttributeHooked = false;
        if (!configuration.craneAttributeDamageEnabled() || !isAnyPluginEnabled("CraneAttribute")) {
            return false;
        }

        Plugin craneAttribute = Bukkit.getPluginManager().getPlugin("CraneAttribute");
        if (craneAttribute == null || !craneAttribute.isEnabled()) {
            return false;
        }

        try {
            ClassLoader classLoader = craneAttribute.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName(
                "cn.org.bukkit.craneattribute.api.event.trigger.AttackAndDefenseTriggerEvent$After",
                true,
                classLoader
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("CraneAttribute 伤害事件不是 Bukkit Event，已跳过 CombatDisplay Crane 兼容监听。");
                return false;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getHandlerMethod = rawEventClass.getMethod("getHandler");
            craneAttributeListener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                craneAttributeListener,
                EventPriority.MONITOR,
                (listener, event) -> handleCraneAttributeDamage(event, rawEventClass, getHandlerMethod),
                plugin,
                true
            );
            registeredListeners.add(craneAttributeListener);
            craneAttributeHooked = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注册 CombatDisplay CraneAttribute 兼容监听失败: " + exception.getMessage());
            return false;
        }
    }

    private void handleCraneAttributeDamage(Event event, Class<?> eventClass, Method getHandlerMethod) {
        if (activeDamageSource != CombatDisplayDamageSource.CRANEATTRIBUTE
            || !eventClass.isInstance(event)
            || !configuration.craneAttributeDamageEnabled()) {
            return;
        }

        try {
            Object handler = getHandlerMethod.invoke(event);
            if (handler == null) {
                return;
            }

            Entity target = readCraneTarget(handler);
            if (target == null) {
                return;
            }

            double damage = readCraneDamage(handler, target);
            handleCraneAttributeDamage(target, damage);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("处理 CombatDisplay CraneAttribute 伤害事件失败: " + exception.getMessage());
        }
    }

    private Entity readCraneTarget(Object handler) throws ReflectiveOperationException {
        Method getEntityMethod = handler.getClass().getMethod("getEntity");
        Object rawTarget = getEntityMethod.invoke(handler);
        return rawTarget instanceof Entity target ? target : null;
    }

    private double readCraneDamage(Object handler, Entity target) throws ReflectiveOperationException {
        try {
            Method getDamageMethod = handler.getClass().getMethod("getDamage", org.bukkit.entity.LivingEntity.class);
            if (target instanceof org.bukkit.entity.LivingEntity livingTarget) {
                Object rawDamage = getDamageMethod.invoke(handler, livingTarget);
                if (rawDamage instanceof Number number) {
                    return number.doubleValue();
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to the wrapped Bukkit damage event.
        }

        try {
            Method getEventMethod = handler.getClass().getMethod("getEvent");
            Object rawEvent = getEventMethod.invoke(handler);
            if (rawEvent instanceof org.bukkit.event.entity.EntityDamageEvent damageEvent) {
                return damageEvent.getFinalDamage();
            }
        } catch (NoSuchMethodException ignored) {
            // No compatible damage accessor.
        }

        return 0.0D;
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

    @SuppressWarnings("unchecked")
    private boolean registerAttributePlusListenerIfAvailable() {
        attributePlusHooked = false;
        if (!isAttributePlusDamageConfigured()) {
            return false;
        }
        Plugin attributePlus = Bukkit.getPluginManager().getPlugin("AttributePlus");
        if (attributePlus == null || !attributePlus.isEnabled()) {
            return false;
        }

        try {
            ClassLoader classLoader = attributePlus.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName(
                "org.serverct.ersha.api.event.AttrEntityDamageEvent",
                true,
                classLoader
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("AttributePlus 伤害事件不是 Bukkit Event，已跳过 CombatDisplay AP 兼容监听。");
                return false;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getAttackerMethod = rawEventClass.getMethod("getAttacker");
            Method getAttackDamageMethod = rawEventClass.getMethod("getAttackDamage");
            Method getTargetMethod = rawEventClass.getMethod("getTarget");
            attributePlusListener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                attributePlusListener,
                EventPriority.MONITOR,
                (listener, event) -> handleAttributePlusDamage(event, rawEventClass, getAttackerMethod, getAttackDamageMethod, getTargetMethod),
                plugin,
                true
            );
            registeredListeners.add(attributePlusListener);
            attributePlusHooked = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注册 CombatDisplay AttributePlus 兼容监听失败: " + exception.getMessage());
            return false;
        }
    }

    private void handleAttributePlusDamage(
        Event event,
        Class<?> eventClass,
        Method getAttackerMethod,
        Method getAttackDamageMethod,
        Method getTargetMethod
    ) {
        if (activeDamageSource != CombatDisplayDamageSource.ATTRIBUTEPLUS
            || !eventClass.isInstance(event)
            || !isAttributePlusDamageConfigured()) {
            return;
        }

        try {
            Object rawAttacker = getAttackerMethod.invoke(event);
            Object rawDamage = getAttackDamageMethod.invoke(event);
            Object rawTarget = getTargetMethod.invoke(event);
            if (!(rawAttacker instanceof Player) || !(rawTarget instanceof Entity target)) {
                return;
            }

            double damage = rawDamage instanceof Number number ? number.doubleValue() : 0.0D;
            handleStandardDamage(target, damage);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("处理 CombatDisplay AttributePlus 伤害事件失败: " + exception.getMessage());
        }
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


