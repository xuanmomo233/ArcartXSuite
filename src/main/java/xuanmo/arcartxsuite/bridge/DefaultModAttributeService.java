package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.modattr.ModAttributeModifier;
import xuanmo.arcartxsuite.api.modattr.ModAttributeService;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * {@link ModAttributeService} 的默认实现，仅在混合核心（Mohist/Arclight 等
 * Forge+Bukkit 环境）上生效。
 * <p>
 * 通过服务端类加载器反射访问 Forge 属性注册表与 NMS 属性实例，
 * 为玩家挂载/移除 {@code AttributeModifier}，向任意 Mod 注册的实体属性
 * （如 {@code gunsmithlib:bullet_damage}）写入持续加成。
 * <p>
 * 实例由 {@code ModuleRegistry} 持有并在构造时创建，
 * {@code GlobalBridgeFactory} 重建时实例不变。
 * <p>
 * 行为约定：
 * <ul>
 *   <li>修饰器 UUID 由「来源 + 属性 ID」确定生成，重复登记为整体替换；</li>
 *   <li>不同来源对同一属性的修饰器独立共存，移除时互不影响；</li>
 *   <li>属性未注册（Mod 未安装）或该实体类型不持有该属性时记日志跳过；</li>
 *   <li>玩家退出时精确移除本服务挂载的修饰器并解除追踪，重新上线需由模块侧重新登记。</li>
 * </ul>
 */
public final class DefaultModAttributeService implements ModAttributeService, Listener {

    private static final String FORGE_REGISTRIES_CLASS = "net.minecraftforge.registries.ForgeRegistries";
    private static final String RESOURCE_LOCATION_CLASS = "net.minecraft.resources.ResourceLocation";
    private static final String LIVING_ENTITY_CLASS = "net.minecraft.world.entity.LivingEntity";
    private static final String ATTRIBUTE_CLASS = "net.minecraft.world.entity.ai.attributes.Attribute";
    private static final String ATTRIBUTE_INSTANCE_CLASS = "net.minecraft.world.entity.ai.attributes.AttributeInstance";
    private static final String ATTRIBUTE_MODIFIER_CLASS = "net.minecraft.world.entity.ai.attributes.AttributeModifier";

    private final JavaPlugin plugin;
    private final Logger logger;
    /** 玩家 UUID → 来源 ID → (修饰器键 → 描述) */
    private final ConcurrentMap<UUID, ConcurrentMap<String, Map<String, ModAttributeModifier>>> tracked =
        new ConcurrentHashMap<>();
    /** 已警告过的缺失属性 ID（避免日志刷屏） */
    private final ConcurrentMap<String, Boolean> warnedAttributes = new ConcurrentHashMap<>();
    /** 本次登录已记过首次挂载成功日志的来源，按玩家分组。 */
    private final ConcurrentMap<UUID, Set<String>> appliedOnce = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile boolean forgeAvailable;
    private boolean listenerRegistered;
    /** 服务不可用时已告警过一次（模块登记时提示，避免静默跳过） */
    private volatile boolean warnedUnavailable;

    // ── Forge 反射缓存 ──
    private Object attributesRegistry;
    private Method registryGetValue;
    private Constructor<?> resourceLocationCtor;
    private Method livingGetAttribute;
    private Method instanceGetModifier;
    private Method instanceAddTransientModifier;
    private Method instanceRemoveModifier;
    private Constructor<?> modifierCtor;
    private Object[] operationConstants;
    private Method craftGetHandle;

    public DefaultModAttributeService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = AxsLog.logger();
    }

    /** 应用宿主 config.yml {@code mod-attributes} 节的配置。 */
    public void configure(boolean enabled) {
        this.enabled = enabled;
    }

    /** 初始化：探测 Forge 环境并解析反射句柄；不可用时不产生任何副作用。 */
    public synchronized void initialize() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
        warnedUnavailable = false;
        if (!enabled) {
            forgeAvailable = false;
            clearAllTracked();
            return;
        }
        forgeAvailable = resolveForgeHandles();
        if (!forgeAvailable) {
            clearAllTracked();
            logger.fine("[ModAttribute] 未检测到 Forge 环境（纯 Bukkit 核心），Mod 属性服务不可用。");
        }
    }

    /** 清空全部登记并移除已施加的修饰器。 */
    public synchronized void shutdown() {
        clearAllTracked();
    }

    @Override
    public boolean available() {
        return enabled && forgeAvailable;
    }

    @Override
    public boolean supportsAttribute(Player player, String attributeId) {
        if (!available() || player == null || !player.isOnline() || attributeId == null || attributeId.isBlank()) {
            return false;
        }
        Object attribute = resolveAttribute(attributeId);
        if (attribute == null) {
            return false;
        }
        Object handle = nmsHandle(player);
        if (handle == null) {
            return false;
        }
        try {
            return livingGetAttribute.invoke(handle, attribute) != null;
        } catch (ReflectiveOperationException exception) {
            warnMissing(player, attributeId);
            return false;
        }
    }

    @Override
    public void setModifiers(Player player, String sourceId, Map<String, ModAttributeModifier> modifiers) {
        if (player == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        if (!available()) {
            warnUnavailableOnce();
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (modifiers == null || modifiers.isEmpty()) {
            clearSource(player, sourceId);
            return;
        }
        Map<String, ModAttributeModifier> sanitized = sanitize(modifiers);
        ConcurrentMap<String, Map<String, ModAttributeModifier>> sources =
            tracked.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        Map<String, ModAttributeModifier> previous = sources.put(sourceId, sanitized);
        if (sanitized.equals(previous)) {
            return;
        }
        applySource(player, sourceId, previous, sanitized);
    }

    @Override
    public void clearSource(Player player, String sourceId) {
        if (player == null || sourceId == null) {
            return;
        }
        ConcurrentMap<String, Map<String, ModAttributeModifier>> sources = tracked.get(player.getUniqueId());
        if (sources == null) {
            return;
        }
        Map<String, ModAttributeModifier> removed = sources.remove(sourceId);
        if (sources.isEmpty()) {
            tracked.remove(player.getUniqueId(), sources);
        }
        if (removed == null || !player.isOnline() || !forgeAvailable) {
            return;
        }
        removeSource(player, sourceId, removed);
    }

    @Override
    public void clearPlayer(Player player) {
        if (player != null) clearTrackedPlayer(player, player.isOnline());
    }

    private void clearTrackedPlayer(Player player, boolean entityAvailable) {
        var removed = tracked.remove(player.getUniqueId());
        if (removed == null || !entityAvailable || !forgeAvailable) {
            return;
        }
        for (Map.Entry<String, Map<String, ModAttributeModifier>> entry : removed.entrySet()) {
            removeSource(player, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clearAll(String sourceId) {
        if (sourceId == null) {
            return;
        }
        for (Map.Entry<UUID, ConcurrentMap<String, Map<String, ModAttributeModifier>>> entry : tracked.entrySet()) {
            Map<String, ModAttributeModifier> removed = entry.getValue().remove(sourceId);
            if (entry.getValue().isEmpty()) {
                tracked.remove(entry.getKey(), entry.getValue());
            }
            if (removed == null || !forgeAvailable) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                removeSource(player, sourceId, removed);
            }
        }
    }

    private void clearAllTracked() {
        for (Map.Entry<UUID, ConcurrentMap<String, Map<String, ModAttributeModifier>>> entry : tracked.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && forgeAvailable) {
                for (Map.Entry<String, Map<String, ModAttributeModifier>> source : entry.getValue().entrySet()) {
                    removeSource(player, source.getKey(), source.getValue());
                }
            }
        }
        tracked.clear();
    }

    /** 在离服实体仍可访问时移除修饰器，兼容会保存或复用属性实例的混合核心。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearTrackedPlayer(player, true);
        appliedOnce.remove(player.getUniqueId());
    }

    // ── Forge 反射解析 ──

    private boolean resolveForgeHandles() {
        try {
            ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
            Class<?> registriesClass = Class.forName(FORGE_REGISTRIES_CLASS, true, loader);
            attributesRegistry = registriesClass.getField("ATTRIBUTES").get(null);
            registryGetValue = attributesRegistry.getClass().getMethod(
                "getValue", Class.forName(RESOURCE_LOCATION_CLASS, false, loader));

            resourceLocationCtor = Class.forName(RESOURCE_LOCATION_CLASS, true, loader)
                .getConstructor(String.class);

            Class<?> attributeClass = Class.forName(ATTRIBUTE_CLASS, true, loader);
            livingGetAttribute = resolveMethod(
                Class.forName(LIVING_ENTITY_CLASS, true, loader), attributeClass,
                "m_21051_", "getAttribute");

            Class<?> modifierClass = Class.forName(ATTRIBUTE_MODIFIER_CLASS, true, loader);
            Class<?> instanceClass = Class.forName(ATTRIBUTE_INSTANCE_CLASS, true, loader);
            instanceGetModifier = resolveMethod(instanceClass, UUID.class, "m_22111_", "getModifier");
            // Forge 1.20.1 的 m_22125_ 会将修饰器加入持久化集合。
            instanceAddTransientModifier = resolveMethod(
                instanceClass, modifierClass, "m_22118_", "addTransientModifier");
            instanceRemoveModifier = resolveMethod(instanceClass, UUID.class, "m_22120_", "removeModifier");

            Class<?> operationEnum = Class.forName(ATTRIBUTE_MODIFIER_CLASS + "$Operation", true, loader);
            modifierCtor = modifierClass.getConstructor(UUID.class, String.class, double.class, operationEnum);
            operationConstants = operationEnum.getEnumConstants();
            return true;
        } catch (Throwable throwable) {
            logger.fine("[ModAttribute] Forge 属性反射解析失败: " + throwable);
            return false;
        }
    }

    /**
     * 按候选名解析方法：Forge 生产环境 net.minecraft 方法为 SRG 名（m_xxxxx_），
     * MojMap 名为兜底（开发环境/非常规核心）。
     */
    private static Method resolveMethod(Class<?> owner, Class<?> paramType, String... names)
        throws NoSuchMethodException {
        return resolveMethod(owner, new Class<?>[]{paramType}, names);
    }

    private static Method resolveMethod(Class<?> owner, Class<?>[] parameters, String... names)
        throws NoSuchMethodException {
        for (String name : names) {
            try {
                return owner.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + " 缺少方法 " + String.join("/", names));
    }

    // ── 修饰器施加/移除 ──

    /**
     * 按「旧登记 → 新登记」增量更新：移除不再存在或数值变化的修饰器，
     * 追加新增/变化的，未变化的不动。
     */
    private void applySource(Player player, String sourceId,
        Map<String, ModAttributeModifier> previous, Map<String, ModAttributeModifier> current) {
        Object handle = nmsHandle(player);
        if (handle == null) {
            return;
        }
        if (previous != null) {
            for (Map.Entry<String, ModAttributeModifier> entry : previous.entrySet()) {
                ModAttributeModifier next = current.get(entry.getKey());
                if (next == null || !next.equals(entry.getValue())) {
                    removeModifier(handle, sourceId, entry.getValue());
                }
            }
        }
        int applied = 0;
        for (Map.Entry<String, ModAttributeModifier> entry : current.entrySet()) {
            ModAttributeModifier modifier = entry.getValue();
            if (previous != null && modifier.equals(previous.get(entry.getKey()))) {
                continue;
            }
            if (addModifier(player, handle, sourceId, modifier)) {
                applied++;
            }
        }
        if (applied > 0 && appliedOnce.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(sourceId)) {
            logger.info("[ModAttribute] 已为 " + player.getName() + " 挂载 " + applied
                + " 个属性修饰器（来源 " + sourceId + "）");
        }
    }

    private void removeSource(Player player, String sourceId, Map<String, ModAttributeModifier> modifiers) {
        Object handle = nmsHandle(player);
        if (handle == null) {
            return;
        }
        for (ModAttributeModifier modifier : modifiers.values()) {
            removeModifier(handle, sourceId, modifier);
        }
    }

    private boolean addModifier(Player player, Object handle, String sourceId, ModAttributeModifier modifier) {
        try {
            Object attribute = resolveAttribute(modifier.attributeId());
            if (attribute == null) {
                warnMissing(player, modifier.attributeId());
                return false;
            }
            Object instance = livingGetAttribute.invoke(handle, attribute);
            if (instance == null) {
                warnMissing(player, modifier.attributeId());
                return false;
            }
            UUID modifierId = modifierUuid(sourceId, modifier.attributeId());
            Object operation = operationConstants[modifier.operation().ordinal()];
            Object nmsModifier = modifierCtor.newInstance(
                modifierId, "axs." + sourceId, modifier.amount(), operation);
            if (instanceGetModifier.invoke(instance, modifierId) != null) {
                instanceRemoveModifier.invoke(instance, modifierId);
            }
            instanceAddTransientModifier.invoke(instance, nmsModifier);
            return true;
        } catch (Throwable throwable) {
            logger.warning("[ModAttribute] 挂载属性失败 " + player.getName()
                + " <- " + modifier.attributeId() + " : " + throwable.getMessage());
            return false;
        }
    }

    private void removeModifier(Object handle, String sourceId, ModAttributeModifier modifier) {
        try {
            Object attribute = resolveAttribute(modifier.attributeId());
            if (attribute == null) {
                return;
            }
            Object instance = livingGetAttribute.invoke(handle, attribute);
            if (instance == null) {
                return;
            }
            instanceRemoveModifier.invoke(instance, modifierUuid(sourceId, modifier.attributeId()));
        } catch (Throwable ignored) {
        }
    }

    private Object resolveAttribute(String attributeId) {
        try {
            String id = attributeId.contains(":") ? attributeId : "minecraft:" + attributeId;
            return registryGetValue.invoke(attributesRegistry, resourceLocationCtor.newInstance(id));
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** 模块登记时服务不可用：每次 initialize 只告警一次，给出具体原因便于排查。 */
    private void warnUnavailableOnce() {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        String reason = !enabled
            ? "已在 config.yml mod-attributes.enabled 禁用"
            : "未检测到 Forge 属性环境（纯 Bukkit 核心或反射初始化失败）";
        logger.warning("[ModAttribute] 模块尝试登记属性加成但服务不可用（" + reason + "），本次加成已跳过。");
    }

    private void warnMissing(Player player, String attributeId) {
        if (warnedAttributes.putIfAbsent(attributeId, Boolean.TRUE) == null) {
            logger.warning("[ModAttribute] 属性 " + attributeId
                + " 未注册或该实体不持有（对应 Mod 未安装？），已为 " + player.getName() + " 跳过。");
        }
    }

    private Object nmsHandle(Player player) {
        try {
            if (craftGetHandle == null) {
                craftGetHandle = player.getClass().getMethod("getHandle");
            }
            return craftGetHandle.invoke(player);
        } catch (Throwable throwable) {
            logger.fine("[ModAttribute] 获取 NMS 实体句柄失败 " + player.getName()
                + " : " + throwable.getMessage());
            return null;
        }
    }

    /** 修饰器 UUID 由「来源 + 属性 ID」确定生成，保证幂等覆盖与精确移除。 */
    private static UUID modifierUuid(String sourceId, String attributeId) {
        return UUID.nameUUIDFromBytes(
            ("axs:" + sourceId + ":" + attributeId).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, ModAttributeModifier> sanitize(Map<String, ModAttributeModifier> modifiers) {
        Map<String, ModAttributeModifier> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, ModAttributeModifier> entry : modifiers.entrySet()) {
            ModAttributeModifier modifier = entry.getValue();
            if (modifier == null || modifier.attributeId() == null || modifier.attributeId().isBlank()) {
                continue;
            }
            sanitized.put(entry.getKey(), modifier);
        }
        return sanitized;
    }
}
