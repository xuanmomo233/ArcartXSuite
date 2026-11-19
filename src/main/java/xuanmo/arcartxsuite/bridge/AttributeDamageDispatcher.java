package xuanmo.arcartxsuite.bridge;

import io.lumine.mythic.lib.api.event.AttackUnregisteredEvent;
import io.lumine.mythic.lib.damage.DamageMetadata;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import priv.seventeen.artist.symphony.api.damage.DamageResultState;
import priv.seventeen.artist.symphony.api.event.SymphonyDamageConfirmedEvent;
import priv.seventeen.artist.symphony.api.event.SymphonyDamagePrepareEvent;
import priv.seventeen.artist.symphony.api.event.SymphonyHitCheckEvent;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageListener;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent.Source;
import xuanmo.arcartxsuite.api.attribute.AttributeHealEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeHealListener;
import xuanmo.arcartxsuite.api.attribute.AttributeHealEvent.SourceType;

/**
 * 统一属性伤害事件分发器。
 * <p>
 * 为所有已对接的属性插件注册底层 Bukkit 事件监听，将伤害事件归一化为
 * {@link AttributeDamageEvent} 后分发给已注册的 {@link AttributeDamageListener}。
 * 新增属性插件时只需在此类中增加对应的注册逻辑，各模块无需改动。
 */
final class AttributeDamageDispatcher {

    private final JavaPlugin plugin;
    private final List<AttributeDamageListener> listeners = new CopyOnWriteArrayList<>();
    private final List<AttributeHealListener> healListeners = new CopyOnWriteArrayList<>();
    private final List<Listener> registeredListeners = new CopyOnWriteArrayList<>();
    private final CombatDamageRouter combat = new CombatDamageRouter(event -> Bukkit.getPluginManager().callEvent(event));
    private BukkitTask combatClock;
    private Plugin symphony;

    CombatDamageRouter combatRouter() { return combat; }
    boolean symphonyDamageAvailable() { return symphony != null && symphony.isEnabled(); }

    // CraneAttribute handler 方法缓存（首次事件触发时初始化，避免每次事件都做 getMethod 查找）
    private volatile Method craneHandlerGetEntity;
    private volatile Method craneHandlerGetDamage;
    private volatile Method craneHandlerGetEvent;
    private volatile Method craneHandlerGetAttacker;
    // CraneAttribute heal handler 方法缓存
    private volatile Method craneHealHandlerGetEntity;
    private volatile Method craneHealHandlerGetHeal;
    private volatile Method craneHealHandlerGetEvent;
    private volatile Method craneHealHandlerGetSource;
    private final Object methodCacheLock = new Object();
    AttributeDamageDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void registerListener(AttributeDamageListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    void unregisterListener(AttributeDamageListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    boolean hasAnyListener() {
        return !listeners.isEmpty();
    }

    void registerHealListener(AttributeHealListener listener) {
        if (listener != null) {
            healListeners.add(listener);
        }
    }

    void unregisterHealListener(AttributeHealListener listener) {
        if (listener != null) {
            healListeners.remove(listener);
        }
    }

    /** 初始化所有可用属性插件的伤害事件监听 */
    void initialize(boolean attributePlusEnabled, boolean craneAttributeEnabled, boolean mythicLibEnabled, boolean symphonyEnabled) {
        unregisterProviders();
        if (attributePlusEnabled) registerAttributePlus();
        if (craneAttributeEnabled) {
            registerCraneAttribute();
            registerCraneAttributeHeal();
        }
        if (mythicLibEnabled) registerMythicLib();
        if (symphonyEnabled) {
            registerSymphony();
        }
        if (combatClock == null) {
            Bukkit.getPluginManager().registerEvents(combat, plugin);
            combatClock = Bukkit.getScheduler().runTaskTimer(plugin, combat::nextTick, 1L, 1L);
        }
    }

    /** 清理所有 Bukkit 监听器 */
    void shutdown() {
        unregisterProviders();
        xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(combat, "CombatDamageRouter");
        if (combatClock != null) {
            combatClock.cancel();
            combatClock = null;
        }
        combat.clear();
        healListeners.clear();
        listeners.clear();
    }

    private void unregisterProviders() {
        xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(registeredListeners, "AttributeDamageDispatcher");
        symphony = null;
    }

    // ─── AttributePlus ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerAttributePlus() {
        Plugin attributePlus = Bukkit.getPluginManager().getPlugin("AttributePlus");
        if (attributePlus == null || !attributePlus.isEnabled()) {
            return;
        }
        try {
            ClassLoader cl = attributePlus.getClass().getClassLoader();
            // 优先尝试 AttrEntityDamageEvent（较新版本）
            final Class<?> damageEventClass;
            Class<?> candidateClass = tryLoadClass(
                "org.serverct.ersha.api.event.AttrEntityDamageEvent", cl
            );
            if (candidateClass == null) {
                // 旧版本回退到 AttrEntityAttackEvent
                candidateClass = tryLoadClass(
                    "org.serverct.ersha.api.event.AttrEntityAttackEvent", cl
                );
            }
            if (candidateClass == null || !Event.class.isAssignableFrom(candidateClass)) {
                return;
            }
            damageEventClass = candidateClass;

            Class<? extends Event> eventClass = (Class<? extends Event>) damageEventClass;
            Listener listener;
            if (damageEventClass.getName().contains("AttrEntityDamageEvent")) {
                Method getAttacker = damageEventClass.getMethod("getAttacker");
                Method getAttackDamage = damageEventClass.getMethod("getAttackDamage");
                Method getTarget = damageEventClass.getMethod("getTarget");
                listener = new Listener() {};
                Bukkit.getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.MONITOR,
                    (l, e) -> dispatchAttributePlusDamage(e, damageEventClass, getAttacker, getAttackDamage, getTarget),
                    plugin, true
                );
            } else {
                // AttrEntityAttackEvent 旧版路径
                Method getAttackerOrKiller = damageEventClass.getMethod("getAttackerOrKiller");
                Method getEntity = damageEventClass.getMethod("getEntity");
                Method getAttributeHandle = damageEventClass.getMethod("getAttributeHandle");
                listener = new Listener() {};
                Bukkit.getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.MONITOR,
                    (l, e) -> dispatchAttributePlusAttack(e, damageEventClass, getAttackerOrKiller, getEntity, getAttributeHandle),
                    plugin, true
                );
            }
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchAttributePlusDamage(
        Event event, Class<?> eventClass,
        Method getAttacker, Method getAttackDamage, Method getTarget
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawAttacker = getAttacker.invoke(event);
            Object rawDamage = getAttackDamage.invoke(event);
            Object rawTarget = getTarget.invoke(event);
            if (!(rawAttacker instanceof Player attacker) || !(rawTarget instanceof Entity target)) return;
            double damage = rawDamage instanceof Number n ? n.doubleValue() : 0.0D;
            if (damage <= 0.0D) return;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.ATTRIBUTE_PLUS));
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] AttributePlus damage dispatch failed: " + e.getMessage());
        }
    }

    private void dispatchAttributePlusAttack(
        Event event, Class<?> eventClass,
        Method getAttackerOrKiller, Method getEntity, Method getAttributeHandle
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawAttacker = getAttackerOrKiller.invoke(event);
            Object rawTarget = getEntity.invoke(event);
            if (!(rawAttacker instanceof Player attacker) || !(rawTarget instanceof LivingEntity target)) return;
            Object handle = getAttributeHandle.invoke(event);
            if (handle == null) return;
            if (apAttributeHandleGetDamage == null) {
                synchronized (methodCacheLock) {
                    if (apAttributeHandleGetDamage == null) {
                        apAttributeHandleGetDamage = handle.getClass().getMethod("getDamage", LivingEntity.class);
                    }
                }
            }
            Object rawDamage = apAttributeHandleGetDamage.invoke(handle, attacker);
            double damage = rawDamage instanceof Number n ? n.doubleValue() : 0.0D;
            if (damage <= 0.0D) return;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.ATTRIBUTE_PLUS));
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] AttributePlus attack dispatch failed: " + e.getMessage());
        }
    }

    // ─── CraneAttribute ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerCraneAttribute() {
        Plugin crane = Bukkit.getPluginManager().getPlugin("CraneAttribute");
        if (crane == null || !crane.isEnabled()) return;
        try {
            ClassLoader cl = crane.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "cn.org.bukkit.craneattribute.api.event.trigger.AttackAndDefenseTriggerEvent$After",
                true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getHandler = rawClass.getMethod("getHandler");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchCraneAttribute(e, rawClass, getHandler),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchCraneAttribute(Event event, Class<?> eventClass, Method getHandler) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object handler = getHandler.invoke(event);
            if (handler == null) return;
            if (craneHandlerGetEntity == null) {
                synchronized (methodCacheLock) {
                    if (craneHandlerGetEntity == null) {
                        craneHandlerGetEntity = handler.getClass().getMethod("getEntity");
                    }
                }
            }
            Object rawTarget = craneHandlerGetEntity.invoke(handler);
            if (!(rawTarget instanceof LivingEntity target)) return;

            double damage = 0.0D;
            if (craneHandlerGetDamage == null) {
                synchronized (methodCacheLock) {
                    if (craneHandlerGetDamage == null) {
                        try {
                            craneHandlerGetDamage = handler.getClass().getMethod("getDamage", LivingEntity.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
            if (craneHandlerGetDamage != null) {
                Object rawDamage = craneHandlerGetDamage.invoke(handler, target);
                if (rawDamage instanceof Number n) damage = n.doubleValue();
            } else {
                if (craneHandlerGetEvent == null) {
                    synchronized (methodCacheLock) {
                        if (craneHandlerGetEvent == null) {
                            craneHandlerGetEvent = handler.getClass().getMethod("getEvent");
                        }
                    }
                }
                Object rawEvent = craneHandlerGetEvent.invoke(handler);
                if (rawEvent instanceof org.bukkit.event.entity.EntityDamageEvent de) {
                    damage = de.getFinalDamage();
                }
            }
            if (damage <= 0.0D) return;

            Player attacker = null;
            if (craneHandlerGetAttacker == null) {
                synchronized (methodCacheLock) {
                    if (craneHandlerGetAttacker == null) {
                        try {
                            craneHandlerGetAttacker = handler.getClass().getMethod("getAttacker");
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
            if (craneHandlerGetAttacker != null) {
                Object rawAttacker = craneHandlerGetAttacker.invoke(handler);
                if (rawAttacker instanceof Player p) attacker = p;
            }
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.CRANE_ATTRIBUTE));
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] CraneAttribute dispatch failed: " + e.getMessage());
        }
    }

    // ─── CraneAttribute Heal ───────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerCraneAttributeHeal() {
        Plugin crane = Bukkit.getPluginManager().getPlugin("CraneAttribute");
        if (crane == null || !crane.isEnabled()) return;
        try {
            ClassLoader cl = crane.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "cn.org.bukkit.craneattribute.api.event.trigger.RegainHealthTriggerEvent$After",
                true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getHandler = rawClass.getMethod("getHandler");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchCraneAttributeHeal(e, rawClass, getHandler),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchCraneAttributeHeal(Event event, Class<?> eventClass, Method getHandler) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object handler = getHandler.invoke(event);
            if (handler == null) return;
            if (craneHealHandlerGetEntity == null) {
                synchronized (methodCacheLock) {
                    if (craneHealHandlerGetEntity == null) {
                        craneHealHandlerGetEntity = handler.getClass().getMethod("getEntity");
                    }
                }
            }
            Object rawTarget = craneHealHandlerGetEntity.invoke(handler);
            if (!(rawTarget instanceof LivingEntity target)) return;

            double heal = 0.0D;
            if (craneHealHandlerGetHeal == null) {
                synchronized (methodCacheLock) {
                    if (craneHealHandlerGetHeal == null) {
                        try {
                            craneHealHandlerGetHeal = handler.getClass().getMethod("getHeal", LivingEntity.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
            if (craneHealHandlerGetHeal != null) {
                Object rawHeal = craneHealHandlerGetHeal.invoke(handler, target);
                if (rawHeal instanceof Number n) heal = n.doubleValue();
            } else {
                if (craneHealHandlerGetEvent == null) {
                    synchronized (methodCacheLock) {
                        if (craneHealHandlerGetEvent == null) {
                            craneHealHandlerGetEvent = handler.getClass().getMethod("getEvent");
                        }
                    }
                }
                Object rawEvent = craneHealHandlerGetEvent.invoke(handler);
                if (rawEvent instanceof EntityRegainHealthEvent he) {
                    heal = he.getAmount();
                }
            }
            if (heal <= 0.0D) return;

            Player source = null;
            if (craneHealHandlerGetSource == null) {
                synchronized (methodCacheLock) {
                    if (craneHealHandlerGetSource == null) {
                        try {
                            craneHealHandlerGetSource = handler.getClass().getMethod("getSource");
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
            if (craneHealHandlerGetSource != null) {
                Object rawSource = craneHealHandlerGetSource.invoke(handler);
                if (rawSource instanceof Player p) source = p;
            }
            dispatchHeal(new AttributeHealEvent(source, target, heal, SourceType.CRANE_ATTRIBUTE));
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] CraneAttribute heal dispatch failed: " + e.getMessage());
        }
    }

    // ─── MythicLib ─────────────────────────────────────────────

    private void registerMythicLib() {
        Plugin mythicLib = Bukkit.getPluginManager().getPlugin("MythicLib");
        if (mythicLib == null || !mythicLib.isEnabled()) return;
        Listener listener = new MythicLibDamageListener();
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    private final class MythicLibDamageListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onAttackUnregistered(AttackUnregisteredEvent event) {
            try {
                Entity target = event.getEntity();
                if (target == null) return;
                var original = event.toBukkit();
                if (original != null && original.isCancelled()) return;

                Player attacker = original instanceof EntityDamageByEntityEvent attack
                    ? CombatEventSupport.resolvePlayerAttacker(attack) : null;
                var metadata = event.getAttack();
                if (attacker == null && metadata != null && metadata.isPlayer()) {
                    attacker = metadata.getPlayer();
                }
                if (attacker == null) return;

                double damage;
                if (original != null) {
                    damage = original.getFinalDamage();
                } else {
                    DamageMetadata meta = event.getDamage();
                    damage = meta != null ? meta.getDamage() : 0.0D;
                }
                if (damage <= 0.0D) return;
                dispatch(new AttributeDamageEvent(attacker, target, damage, Source.MYTHIC_LIB));
            } catch (RuntimeException e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] MythicLib dispatch failed: " + e.getMessage());
            }
        }
    }

    // ─── Symphony ──────────────────────────────────────────────

    private void registerSymphony() {
        Plugin provider = Bukkit.getPluginManager().getPlugin("Symphony");
        if (provider == null || !provider.isEnabled()) return;
        Listener listener = null;
        try {
            SymphonyDamageConfirmedEvent.getHandlerList();
            SymphonyDamagePrepareEvent.getHandlerList();
            SymphonyHitCheckEvent.getHandlerList();
            listener = new SymphonyDamageListener();
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            registeredListeners.add(listener);
            symphony = provider;
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(listener, "SymphonyDamage");
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[SymphonyDamage] 确认伤害桥接不可用: " + e);
        }
    }

    private final class SymphonyDamageListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onSymphonyPrepare(SymphonyDamagePrepareEvent event) {
            var request = event.getRequest();
            Player attacker = request.getAttacker() instanceof Player player ? player : null;
            combat.beginSymphony(event.getTransactionId(), attacker, request.getVictim());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onSymphonyConfirmed(SymphonyDamageConfirmedEvent event) {
            var result = event.getResult();
            var hit = combat.consumeSymphony(result.getTransactionId());
            double damage = result.getFinalDamage();
            if (hit == null || result.getState() != DamageResultState.CONFIRMED
                    || !result.getHit() || !CombatDamageRouter.positive(damage)) return;
            var breakdown = result.getBreakdown();
            var relation = resolveRelation(breakdown.getAdvantagedDamage(), breakdown.getNeutralDamage(),
                breakdown.getDisadvantagedDamage());
            dispatch(new AttributeDamageEvent(hit.attacker(), hit.target(), damage, Source.SYMPHONY,
                result.getCritical(), false, relation));
            combat.publish(hit, damage, CombatDamageEvent.Source.SYMPHONY, result.getTransactionId());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onSymphonyHitCheck(SymphonyHitCheckEvent event) {
            if (event.getHit()) return;
            var hit = combat.consumeSymphony(event.getTransactionId());
            if (hit != null) {
                dispatch(new AttributeDamageEvent(hit.attacker(), hit.target(), 0.0, Source.SYMPHONY, false, true, null));
            }
        }
    }

    private static AttributeDamageEvent.DamageRelation resolveRelation(double advantaged, double neutral, double disadvantaged) {
        if (advantaged > neutral && advantaged > disadvantaged) {
            return AttributeDamageEvent.DamageRelation.ADVANTAGED;
        }
        if (disadvantaged > neutral && disadvantaged > advantaged) {
            return AttributeDamageEvent.DamageRelation.DISADVANTAGED;
        }
        return AttributeDamageEvent.DamageRelation.NEUTRAL;
    }

    // ─── Dispatch ──────────────────────────────────────────────

    private void dispatch(AttributeDamageEvent event) {
        for (AttributeDamageListener listener : listeners) {
            try {
                listener.onAttributeDamage(event);
            } catch (Exception e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] Listener " + listener.getClass().getName() + " threw: " + e.getMessage());
            }
        }
    }

    private void dispatchHeal(AttributeHealEvent event) {
        for (AttributeHealListener listener : healListeners) {
            try {
                listener.onAttributeHeal(event);
            } catch (Exception e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributeDamageDispatcher] Heal listener " + listener.getClass().getName() + " threw: " + e.getMessage());
            }
        }
    }

    // AttributePlus AttributeHandle.getDamage 缓存
    private volatile Method apAttributeHandleGetDamage;

    private static Class<?> tryLoadClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, true, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

}
