package xuanmo.arcartxsuite.bridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.potion.PotionAuraService;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * {@link PotionAuraService} 的默认实现。
 * <p>
 * 按「玩家 → 来源 → 效果表」登记持续药水效果，由单个周期任务
 * 为在线玩家重复施加短时长效果，实现光环式 buff（如称号佩戴加成）。
 * <p>
 * 实例由 {@code ModuleRegistry} 持有并在构造时创建，
 * {@code GlobalBridgeFactory} 重建时实例不变，避免重复调度任务。
 * <p>
 * 行为约定：
 * <ul>
 *   <li>施加前按「效果类型 → 最大等级」合并该玩家全部来源，再统一施加；</li>
 *   <li>统一使用 ambient 模式；移除来源时仅当"现存效果为 ambient、
 *       等级一致且剩余时长不超过本服务施加时长"才主动清除；</li>
 *   <li>不会用较弱的光环效果覆盖玩家身上更强的非 ambient 同类效果；</li>
 *   <li>瞬间型效果（瞬间治疗/瞬间伤害）不允许登记为光环，防止误配循环生效；</li>
 *   <li>效果等级按 1 起计（1 = I 级），内部转换为 Bukkit amplifier 并钳制到 256。</li>
 * </ul>
 */
public final class DefaultPotionAuraService implements PotionAuraService, Listener {

    private static final int MIN_REFRESH_INTERVAL_TICKS = 20;
    private static final int MIN_DURATION_MARGIN_TICKS = 40;
    /** Minecraft amplifier 上限 255，对应等级上限 256 */
    private static final int MAX_LEVEL = 256;

    private final JavaPlugin plugin;
    private final Logger logger;
    /** 玩家 UUID → 来源 ID → 效果表（效果类型 → 等级，1 起） */
    private final ConcurrentMap<UUID, EffectSources<PotionEffectType>> tracked =
        new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile int refreshIntervalTicks = 60;
    private volatile int effectDurationTicks = 200;
    private volatile boolean particles = false;
    private volatile boolean icon = true;
    private BukkitTask refreshTask;
    private final Object taskLock = new Object();
    private boolean started;
    private int scheduledIntervalTicks;
    private boolean listenerRegistered;

    public DefaultPotionAuraService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = AxsLog.logger();
    }

    /**
     * 应用宿主 config.yml {@code potion-aura} 节的配置。
     * <p>
     * 由 {@code GlobalBridgeFactory} 在每次初始化/重载时调用，
     * 刷新周期变化时重新调度任务；禁用时停止任务并清空登记。
     */
    public void configure(boolean enabled, int refreshIntervalTicks, int effectDurationTicks,
        boolean particles, boolean icon) {
        this.enabled = enabled;
        this.refreshIntervalTicks = Math.max(MIN_REFRESH_INTERVAL_TICKS, refreshIntervalTicks);
        this.effectDurationTicks = Math.max(effectDurationTicks, this.refreshIntervalTicks + MIN_DURATION_MARGIN_TICKS);
        this.particles = particles;
        this.icon = icon;
    }

    /** 初始化：注册退出监听（仅一次）并按当前配置调度刷新任务。 */
    public void initialize() {
        synchronized (taskLock) {
            if (!listenerRegistered) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                listenerRegistered = true;
            }
            started = true;
        }
        if (!enabled) clearAllTracked();
        reconcileRefreshTask();
    }

    /** 停止刷新任务、清空登记并主动移除已施加的效果。 */
    public void shutdown() {
        synchronized (taskLock) {
            started = false;
            cancelRefreshTask();
        }
        clearAllTracked();
    }

    private void reconcileRefreshTask() {
        synchronized (taskLock) {
            if (!started || !enabled || tracked.isEmpty()) {
                cancelRefreshTask();
                return;
            }
            if (refreshTask != null && !refreshTask.isCancelled() && scheduledIntervalTicks == refreshIntervalTicks) return;
            cancelRefreshTask();
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                refreshIntervalTicks, refreshIntervalTicks);
            scheduledIntervalTicks = refreshIntervalTicks;
        }
    }

    private void cancelRefreshTask() {
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public void setEffects(Player player, String sourceId, Map<PotionEffectType, Integer> effects) {
        if (!enabled || player == null || !player.isOnline()
            || sourceId == null || sourceId.isBlank()) {
            return;
        }
        if (effects == null || effects.isEmpty()) {
            clearSource(player, sourceId);
            return;
        }
        Map<PotionEffectType, Integer> sanitized = sanitizeEffects(player, sourceId, effects);
        if (sanitized.isEmpty()) {
            clearSource(player, sourceId);
            return;
        }
        var change = new AtomicReference<EffectSources.Change<PotionEffectType>>();
        tracked.compute(player.getUniqueId(), (id, sources) -> {
            if (sources == null) sources = new EffectSources<>();
            change.set(sources.replace(sourceId, sanitized));
            return sources;
        });
        reconcileRefreshTask();
        if (change.get().changed()) applyEffects(player, change.get().merged());
    }

    @Override
    public void clearSource(Player player, String sourceId) {
        if (player == null || sourceId == null) {
            return;
        }
        var change = new AtomicReference<EffectSources.Change<PotionEffectType>>();
        tracked.computeIfPresent(player.getUniqueId(), (id, sources) -> {
            change.set(sources.remove(sourceId));
            return sources.isEmpty() ? null : sources;
        });
        reconcileRefreshTask();
        if (change.get() == null || !change.get().changed() || !player.isOnline()) {
            return;
        }
        removeAppliedEffects(player, change.get().previous());
        if (!change.get().merged().isEmpty()) {
            // 其他来源仍登记同类型效果时立即恢复，避免等到下个刷新周期才补回
            applyEffects(player, change.get().merged());
        }
    }

    @Override
    public void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        EffectSources<PotionEffectType> removed = tracked.remove(player.getUniqueId());
        reconcileRefreshTask();
        if (removed == null || !player.isOnline()) {
            return;
        }
        removeAppliedEffects(player, removed.snapshot());
    }

    @Override
    public void clearAll(String sourceId) {
        if (sourceId == null) {
            return;
        }
        for (UUID id : tracked.keySet()) {
            var change = new AtomicReference<EffectSources.Change<PotionEffectType>>();
            tracked.computeIfPresent(id, (key, sources) -> {
                change.set(sources.remove(sourceId));
                return sources.isEmpty() ? null : sources;
            });
            if (change.get() == null || !change.get().changed()) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                removeAppliedEffects(player, change.get().previous());
                if (!change.get().merged().isEmpty()) {
                    applyEffects(player, change.get().merged());
                }
            }
        }
        reconcileRefreshTask();
    }

    /** 主动移除全部登记效果：禁用时立即清 buff，而不是等短时长自然衰减。 */
    private void clearAllTracked() {
        for (Map.Entry<UUID, EffectSources<PotionEffectType>> entry : tracked.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                removeAppliedEffects(player, entry.getValue().snapshot());
            }
        }
        tracked.clear();
    }

    /**
     * 玩家退出时解除追踪；剩余效果为短时长，自然衰减即可。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracked.remove(event.getPlayer().getUniqueId());
        reconcileRefreshTask();
    }

    private void tick() {
        if (!enabled) { reconcileRefreshTask(); return; }
        for (Map.Entry<UUID, EffectSources<PotionEffectType>> entry : tracked.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                tracked.remove(entry.getKey(), entry.getValue());
                continue;
            }
            Map<PotionEffectType, Integer> merged = entry.getValue().snapshot();
            if (!merged.isEmpty()) {
                applyEffects(player, merged);
            }
        }
        reconcileRefreshTask();
    }

    /**
     * 校验登记的效果表：过滤瞬间型效果（循环刷新会持续回血/伤害，几乎必为误配）、
     * 非法等级，并把等级钳制到 {@link #MAX_LEVEL}。
     */
    private Map<PotionEffectType, Integer> sanitizeEffects(
        Player player, String sourceId, Map<PotionEffectType, Integer> effects) {
        Map<PotionEffectType, Integer> sanitized = new LinkedHashMap<>();
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (type == null || level <= 0) {
                continue;
            }
            if (isInstant(type)) {
                logger.warning("[PotionAura] 来源 " + sourceId + " 登记的瞬间型效果 "
                    + type.getName() + " 不适合持续生效，已为 " + player.getName() + " 跳过。");
                continue;
            }
            if (level > MAX_LEVEL) {
                logger.warning("[PotionAura] 来源 " + sourceId + " 的效果 " + type.getName()
                    + " 等级 " + level + " 超出上限，已钳制为 " + MAX_LEVEL + "。");
                level = MAX_LEVEL;
            }
            sanitized.merge(type, level, Math::max);
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private void applyEffects(Player player, Map<PotionEffectType, Integer> effects) {
        int duration = effectDurationTicks;
        boolean showParticles = particles;
        boolean showIcon = icon;
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (type == null || level <= 0) {
                continue;
            }
            int amplifier = Math.min(level, MAX_LEVEL) - 1;
            try {
                PotionEffect active = player.getPotionEffect(type);
                // 不用光环覆盖同级或更强的外部（非 ambient）效果：同级覆盖会让
                // 外部效果被误标记为本服务施加（ambient），卸下时被误删；更强覆盖则削弱玩家
                if (active != null && !active.isAmbient() && active.getAmplifier() >= amplifier) {
                    continue;
                }
                player.addPotionEffect(
                    new PotionEffect(type, duration, amplifier, true, showParticles, showIcon));
            } catch (RuntimeException exception) {
                logger.warning("[PotionAura] 施加效果失败 " + player.getName()
                    + " <- " + type.getName() + " : " + exception.getMessage());
            }
        }
    }

    /**
     * 按启发式规则移除仍生效的对应药水效果：
     * 仅当现存效果为 ambient（本服务施加的特征）、amplifier 与登记等级一致
     * 且剩余时长不超过本服务施加时长时才移除，避免误删外部来源的同类效果。
     */
    private void removeAppliedEffects(Player player, Map<PotionEffectType, Integer> effects) {
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (type == null || level <= 0) {
                continue;
            }
            int amplifier = Math.min(level, MAX_LEVEL) - 1;
            try {
                PotionEffect active = player.getPotionEffect(type);
                if (active != null
                    && active.isAmbient()
                    && active.getAmplifier() == amplifier
                    && active.getDuration() <= effectDurationTicks) {
                    player.removePotionEffect(type);
                }
            } catch (RuntimeException exception) {
                logger.fine("[PotionAura] 移除效果跳过 " + player.getName()
                    + " <- " + type.getName() + " : " + exception.getMessage());
            }
        }
    }

    private static boolean isInstant(PotionEffectType type) {
        try {
            return type.isInstant();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
