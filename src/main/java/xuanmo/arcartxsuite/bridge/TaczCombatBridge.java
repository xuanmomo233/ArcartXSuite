package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.event.TaczGunDamageEvent;
import xuanmo.arcartxsuite.api.event.TaczGunKillEvent;

/**
 * TACZ（创世战术武器）兼容桥接，仅在混合核心（Mohist/Arclight 等）上启用。
 * <p>
 * 当 TACZ Mod 存在时，注册 Forge/NeoForge 事件监听器，将以下 Forge 事件转换为 Bukkit 事件并广播：
 * <ul>
 *   <li>{@code EntityHurtByGunEvent.Pre} → {@link TaczGunDamageEvent}（枪械伤害）</li>
 *   <li>{@code EntityKillByGunEvent} → {@link TaczGunKillEvent}（枪械击杀）</li>
 * </ul>
 * AXS 模块可通过标准 {@code @EventHandler} 监听这些事件获取枪械战斗信息，
 * 而无需关心 Forge 反射细节，也不会与 {@link org.bukkit.event.entity.EntityDamageByEntityEvent} 混淆。
 */
public final class TaczCombatBridge {

    private static final String TACZ_HURT_EVENT_CLASS = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";
    private static final String TACZ_HURT_POST_CLASS = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post";
    private static final String TACZ_KILL_EVENT_CLASS = "com.tacz.guns.api.event.common.EntityKillByGunEvent";
    private static final String FORGE_CLASS = "net.minecraftforge.common.MinecraftForge";
    private static final String FORGE_PRIORITY_CLASS = "net.minecraftforge.eventbus.api.EventPriority";
    private static final String NMS_ENTITY_CLASS = "net.minecraft.world.entity.Entity";

    private final JavaPlugin plugin;
    private final boolean debug;
    private final ClassLoader modClassLoader;
    private boolean active;
    private CombatDamageRouter damageRouter;
    private boolean hitTracking;

    // Forge 反射缓存（伤害事件）
    private volatile Method getHurtEntityMethod;
    private volatile Method getAttackerMethod;
    private volatile Method getBaseAmountMethod;
    private volatile Method isHeadShotMethod;
    private volatile Method getGunIdMethod;
    private volatile Method getBukkitEntityMethod;
    private Method getBulletMethod;
    private Method isCancelledMethod;

    // Forge 反射缓存（击杀事件）
    private volatile Method killGetKilledEntityMethod;
    private volatile Method killGetAttackerMethod;
    private volatile Method killGetBaseDamageMethod;
    private volatile Method killIsHeadShotMethod;
    private volatile Method killGetGunIdMethod;
    private Method killGetBulletMethod;

    private TaczCombatBridge(JavaPlugin plugin, boolean debug, ClassLoader modClassLoader) {
        this.plugin = plugin;
        this.debug = debug;
        this.modClassLoader = modClassLoader;
    }

    /**
     * 解析能看到 TACZ Mod / Forge / NMS 类的类加载器。
     * <p>这些类由服务端（混合核心）类加载器加载，不属于任何 Bukkit 插件加载器。
     * 薄壳重构后插件类由 ProtectedClassLoader 定义，单参 {@code Class.forName} 的
     * 调用者加载器够不到 Mod/NMS 类，因此必须显式使用服务端类加载器。
     */
    private static ClassLoader resolveModClassLoader() {
        return Bukkit.getServer().getClass().getClassLoader();
    }

    public static TaczCombatBridge tryInitialize(JavaPlugin plugin, boolean enabled, boolean debug) {
        if (!enabled) {
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[TaczCombat] TACZ 兼容已在 config.yml 中关闭。");
            return null;
        }
        ClassLoader modClassLoader = resolveModClassLoader();
        try {
            Class.forName("com.tacz.guns.GunMod", false, modClassLoader);
        } catch (ClassNotFoundException ignored) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().fine("[TaczCombat] TACZ Mod 未检测到，跳过桥接初始化。");
            }
            return null;
        }
        try {
            TaczCombatBridge bridge = new TaczCombatBridge(plugin, debug, modClassLoader);
            if (!bridge.registerForgeEventListener()) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 无法注册 Forge 事件监听器，TACZ 伤害桥接未启用。");
                return null;
            }
            bridge.active = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().info("[TaczCombat] TACZ 伤害桥接已启用。");
            return bridge;
        } catch (Exception exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().log(Level.WARNING, "[TaczCombat] TACZ 桥接初始化失败。", exception);
            return null;
        }
    }

    public void shutdown() {
        if (active) {
            active = false;
            xuanmo.arcartxsuite.module.AxsLog.logger().info("[TaczCombat] TACZ 伤害桥接已关闭。");
        }
    }

    public boolean isActive() {
        return active;
    }

    void setDamageRouter(CombatDamageRouter router) {
        damageRouter = router;
    }

    // ---- Forge 事件注册 ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean registerForgeEventListener() {
        try {
            // 1. 加载 TACZ 伤害事件类
            Class<?> eventPreClass = Class.forName(TACZ_HURT_EVENT_CLASS, true, modClassLoader);

            // 2. 缓存伤害事件方法（尝试多种可能的方法名）
            getHurtEntityMethod = findMethodByNames(eventPreClass, "getHurtEntity", "getEntity", "getTarget");
            getAttackerMethod = findMethodByNames(eventPreClass, "getAttacker", "getShooter", "getPlayer");
            getBaseAmountMethod = findMethodByNames(eventPreClass, "getBaseAmount", "getAmount", "getDamage");
            isHeadShotMethod = findMethodByNames(eventPreClass, "isHeadShot", "isHeadshot");
            getGunIdMethod = findMethodByNames(eventPreClass, "getGunId", "getGunID");
            getBulletMethod = findMethodByNames(eventPreClass, "getBullet");
            isCancelledMethod = findMethodByNames(eventPreClass, "isCanceled");

            if (getHurtEntityMethod == null || getAttackerMethod == null || getBaseAmountMethod == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 无法找到 TACZ 伤害事件所需方法: hurtEntity="
                    + (getHurtEntityMethod != null) + ", attacker=" + (getAttackerMethod != null)
                    + ", baseAmount=" + (getBaseAmountMethod != null));
                logAvailableMethods(eventPreClass);
                return false;
            }

            // 3. NMS Entity -> Bukkit Entity 转换
            Class<?> nmsEntityClass = Class.forName(NMS_ENTITY_CLASS, true, modClassLoader);
            getBukkitEntityMethod = nmsEntityClass.getMethod("getBukkitEntity");

            // 4. 获取 Forge 事件总线
            Class<?> forgeClass = Class.forName(FORGE_CLASS, true, modClassLoader);
            Field eventBusField = forgeClass.getDeclaredField("EVENT_BUS");
            Object eventBus = eventBusField.get(null);

            // 5. 获取 EventPriority.NORMAL
            Class<?> priorityClass = Class.forName(FORGE_PRIORITY_CLASS, true, modClassLoader);
            Object normalPriority = Enum.valueOf((Class<Enum>) priorityClass, "NORMAL");
            Object lowestPriority = Enum.valueOf((Class<Enum>) priorityClass, "LOWEST");

            // 6. 查找 addListener 方法
            Method addListenerMethod = findAddListenerMethod(eventBus, priorityClass);
            if (addListenerMethod == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 无法找到 IEventBus.addListener 方法。");
                return false;
            }

            // 7. 注册伤害事件监听器
            Consumer<Object> hurtListener = this::handleGunDamageEvent;
            addListenerMethod.invoke(eventBus, lowestPriority, false, eventPreClass, hurtListener);
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("[TaczCombat] Forge 伤害事件监听器注册成功 -> " + TACZ_HURT_EVENT_CLASS);
            }

            // 8. 注册击杀事件监听器（可选，失败不影响伤害监听）
            try {
                Class<?> killEventClass = Class.forName(TACZ_KILL_EVENT_CLASS, true, modClassLoader);
                killGetKilledEntityMethod = findMethodByNames(killEventClass, "getKilledEntity", "getKilled", "getEntity");
                killGetAttackerMethod = findMethodByNames(killEventClass, "getAttacker", "getShooter", "getPlayer");
                killGetBaseDamageMethod = findMethodByNames(killEventClass, "getBaseDamage", "getBaseAmount", "getDamage");
                killIsHeadShotMethod = findMethodByNames(killEventClass, "isHeadShot", "isHeadshot");
                killGetGunIdMethod = findMethodByNames(killEventClass, "getGunId", "getGunID");

                if (killGetKilledEntityMethod != null && killGetAttackerMethod != null) {
                    Consumer<Object> killListener = this::handleGunKillEvent;
                    addListenerMethod.invoke(eventBus, normalPriority, false, killEventClass, killListener);
                    killGetBulletMethod = findMethodByNames(killEventClass, "getBullet");
                    if (debug) {
                        xuanmo.arcartxsuite.module.AxsLog.logger().info("[TaczCombat] Forge 击杀事件监听器注册成功 -> " + TACZ_KILL_EVENT_CLASS);
                    }
                } else {
                    xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 无法找到 TACZ 击杀事件所需方法，击杀桥接未启用。");
                }
            } catch (ClassNotFoundException e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] TACZ 击杀事件类未找到，击杀桥接未启用: " + TACZ_KILL_EVENT_CLASS);
            } catch (Exception e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 注册 TACZ 击杀事件监听器失败: " + e.getMessage());
            }

            if (getBulletMethod != null && isCancelledMethod != null && killGetBulletMethod != null) {
                try {
                    Class<?> post = Class.forName(TACZ_HURT_POST_CLASS, false, modClassLoader);
                    Consumer<Object> postListener = this::handleGunPostEvent;
                    addListenerMethod.invoke(eventBus, normalPriority, false, post, postListener);
                    hitTracking = true;
                } catch (ReflectiveOperationException | LinkageError e) {
                    xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 枪械结算只使用 Bukkit 伤害事件: " + e);
                }
            }

            return true;
        } catch (ClassNotFoundException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 未找到所需类: " + e.getMessage());
            return false;
        } catch (Exception e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().log(Level.WARNING, "[TaczCombat] 注册 Forge 事件监听器失败", e);
            return false;
        }
    }

    // ---- Forge 事件回调 ----

    private void handleGunDamageEvent(Object event) {
        if (!active) {
            return;
        }
        try {
            // 1. 提取攻击者和目标（NMS Entity）
            Object nmsHurtEntity = getHurtEntityMethod.invoke(event);
            Object nmsAttacker = getAttackerMethod.invoke(event);
            Object rawAmount = getBaseAmountMethod.invoke(event);

            if (nmsHurtEntity == null || nmsAttacker == null || rawAmount == null) {
                return;
            }

            // 2. 获取伤害值
            float damage = rawAmount instanceof Number number ? number.floatValue() : 0.0F;
            if (damage <= 0.0F) {
                return;
            }

            // 3. NMS -> Bukkit Entity
            Entity bukkitTarget = (Entity) getBukkitEntityMethod.invoke(nmsHurtEntity);
            Entity bukkitAttacker = (Entity) getBukkitEntityMethod.invoke(nmsAttacker);

            if (!(bukkitAttacker instanceof Player player)) {
                return;
            }
            if (!(bukkitTarget instanceof LivingEntity livingTarget)) {
                return;
            }
            if (player.equals(livingTarget)) {
                return;
            }

            // 4. 提取额外信息
            boolean headShot = false;
            if (isHeadShotMethod != null) {
                try {
                    Object headShotResult = isHeadShotMethod.invoke(event);
                    if (headShotResult instanceof Boolean b) {
                        headShot = b;
                    }
                } catch (Exception ignored) {
                }
            }
            String gunId = "";
            if (getGunIdMethod != null) {
                try {
                    Object gunIdResult = getGunIdMethod.invoke(event);
                    if (gunIdResult != null) {
                        gunId = gunIdResult.toString();
                    }
                } catch (Exception ignored) {
                }
            }

            // 5. 在主线程广播为 TaczGunDamageEvent
            final double finalDamage = damage;
            final boolean finalHeadShot = headShot;
            final String finalGunId = gunId;
            Runnable fireEvent = () -> {
                if (!livingTarget.isDead() && livingTarget.isValid()) {
                    Bukkit.getPluginManager().callEvent(
                        new TaczGunDamageEvent(player, livingTarget, finalDamage, finalHeadShot, finalGunId)
                    );
                }
            };
            if (Bukkit.isPrimaryThread()) {
                fireEvent.run();
                if (hitTracking && damageRouter != null) {
                    Entity bullet = bukkitEntity(getBulletMethod.invoke(event));
                    if (bullet != null) {
                        damageRouter.beginGun(bullet.getUniqueId(), player, livingTarget, gunId, headShot,
                            () -> preCancelled(event));
                    }
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, fireEvent);
            }
        } catch (Exception e) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().log(Level.WARNING, "[TaczCombat] 处理 TACZ 伤害事件异常", e);
            }
        }
    }

    private void handleGunPostEvent(Object event) {
        if (!active || !hitTracking || damageRouter == null || !Bukkit.isPrimaryThread()) return;
        try {
            if (bukkitEntity(getHurtEntityMethod.invoke(event)) instanceof LivingEntity target) {
                finishGunHit(event, getBulletMethod, target);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (debug) xuanmo.arcartxsuite.module.AxsLog.logger().fine("[TaczCombat] 枪械结算失败: " + e);
        }
    }

    private void finishGunHit(Object event, Method bulletGetter, LivingEntity target) {
        try {
            Entity bullet = bukkitEntity(bulletGetter.invoke(event));
            if (bullet != null) damageRouter.endGun(bullet.getUniqueId(), target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (debug) xuanmo.arcartxsuite.module.AxsLog.logger().fine("[TaczCombat] 枪械结算失败: " + e);
        }
    }

    private Entity bukkitEntity(Object nmsEntity) throws ReflectiveOperationException {
        return nmsEntity == null ? null : (Entity) getBukkitEntityMethod.invoke(nmsEntity);
    }

    private boolean preCancelled(Object event) {
        try {
            return Boolean.TRUE.equals(isCancelledMethod.invoke(event));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    // ---- Forge 击杀事件回调 ----

    private void handleGunKillEvent(Object event) {
        if (!active) {
            return;
        }
        try {
            // 1. 提取被击杀实体和攻击者（NMS Entity）
            Object nmsKilledEntity = killGetKilledEntityMethod.invoke(event);
            Object nmsAttacker = killGetAttackerMethod.invoke(event);
            if (nmsKilledEntity == null || nmsAttacker == null) {
                return;
            }

            // 2. NMS -> Bukkit Entity
            Entity bukkitKilled = (Entity) getBukkitEntityMethod.invoke(nmsKilledEntity);
            Entity bukkitAttacker = (Entity) getBukkitEntityMethod.invoke(nmsAttacker);
            if (!(bukkitAttacker instanceof Player player)) {
                return;
            }
            if (!(bukkitKilled instanceof LivingEntity killedEntity)) {
                return;
            }
            if (player.equals(killedEntity)) {
                return;
            }
            if (hitTracking && damageRouter != null && Bukkit.isPrimaryThread()) {
                finishGunHit(event, killGetBulletMethod, killedEntity);
            }

            // 3. 提取额外信息
            double damage = 0;
            if (killGetBaseDamageMethod != null) {
                Object rawDamage = killGetBaseDamageMethod.invoke(event);
                if (rawDamage instanceof Number n) damage = n.doubleValue();
            }
            boolean headShot = false;
            if (killIsHeadShotMethod != null) {
                try {
                    Object headShotResult = killIsHeadShotMethod.invoke(event);
                    if (headShotResult instanceof Boolean b) headShot = b;
                } catch (Exception ignored) {}
            }
            String gunId = "";
            if (killGetGunIdMethod != null) {
                try {
                    Object gunIdResult = killGetGunIdMethod.invoke(event);
                    if (gunIdResult != null) gunId = gunIdResult.toString();
                } catch (Exception ignored) {}
            }

            // 4. 在主线程广播为 TaczGunKillEvent
            final double finalDamage = damage;
            final boolean finalHeadShot = headShot;
            final String finalGunId = gunId;
            Runnable fireEvent = () -> {
                try {
                    Bukkit.getPluginManager().callEvent(
                        new TaczGunKillEvent(player, killedEntity, finalDamage, finalHeadShot, finalGunId)
                    );
                } catch (Exception e) {
                    if (debug) {
                        xuanmo.arcartxsuite.module.AxsLog.logger().warning("[TaczCombat] 广播 TaczGunKillEvent 失败: " + e.getMessage());
                    }
                }
            };
            if (Bukkit.isPrimaryThread()) {
                fireEvent.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, fireEvent);
            }

            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("[TaczCombat] 广播 TACZ 击杀 -> attacker=" + player.getName()
                    + ", killed=" + killedEntity.getType() + "(" + killedEntity.getUniqueId() + ")"
                    + ", damage=" + String.format("%.2f", damage)
                    + ", headshot=" + headShot);
            }
        } catch (Exception e) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().log(Level.WARNING, "[TaczCombat] 处理 TACZ 击杀事件异常", e);
            }
        }
    }

    // ---- 工具方法 ----

    private static Method findMethodByNames(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Method method = clazz.getMethod(name);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        // 尝试在父类中查找
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            for (String name : names) {
                try {
                    return superClass.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            superClass = superClass.getSuperclass();
        }
        return null;
    }

    private static Method findAddListenerMethod(Object eventBus, Class<?> priorityClass) {
        for (Method method : eventBus.getClass().getMethods()) {
            if (!"addListener".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 4
                && params[0] == priorityClass
                && params[1] == boolean.class
                && params[2] == Class.class
                && Consumer.class.isAssignableFrom(params[3])) {
                return method;
            }
        }
        return null;
    }

    private void logAvailableMethods(Class<?> eventClass) {
        if (!debug) {
            return;
        }
        StringBuilder sb = new StringBuilder("[TaczCombat] 可用方法列表 (").append(eventClass.getSimpleName()).append("):");
        Class<?> current = eventClass;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    sb.append("\n  - ").append(current.getSimpleName()).append(".").append(m.getName())
                        .append("() -> ").append(m.getReturnType().getSimpleName());
                }
            }
            current = current.getSuperclass();
        }
        xuanmo.arcartxsuite.module.AxsLog.logger().info(sb.toString());
    }
}
