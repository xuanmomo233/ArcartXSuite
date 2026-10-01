package xuanmo.arcartxsuite.abyss.links;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.capability.AnnouncerBroadcastable;
import xuanmo.arcartxsuite.api.capability.BattlePassAccess;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.CombatEffectTriggerable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;

/**
 * 跨模块联动封装：懒解析宿主 capability（正缓存、负结果每次重试——容忍
 * 其它模块在 abyss 之后才注册能力），全部方法在目标模块缺席时静默跳过。
 * <p>
 * 同时承接 attributeBridge（属性修饰/伤害治疗统计桥）与 essentials
 * （出生点兜底/AFK 判定）。
 */
public final class AbyssLinks {

    /** 泛型 capability 解析器（对应 AbstractAXSModule#getCapability 签名）。 */
    @FunctionalInterface
    public interface CapabilityResolver {
        <T> @Nullable T get(Class<T> type);
    }

    private final CapabilityResolver capabilityResolver;
    private final AttributeBridgeRegistry attributeBridge;
    private final Logger logger;
    /** 已解析的 capability 缓存（key=接口类）；解析失败不缓存以便重试。 */
    private final Map<Class<?>, Object> resolved = new ConcurrentHashMap<>();

    public AbyssLinks(CapabilityResolver capabilityResolver,
                      AttributeBridgeRegistry attributeBridge, Logger logger) {
        this.capabilityResolver = capabilityResolver;
        this.attributeBridge = attributeBridge;
        this.logger = logger;
    }

    // ═══ 内部：capability 懒解析 ═══

    private <T> @Nullable T cap(Class<T> type) {
        Object cached = resolved.get(type);
        if (cached != null) {
            return type.cast(cached);
        }
        T found;
        try {
            found = capabilityResolver.get(type);
        } catch (Throwable t) {
            return null;
        }
        if (found == null) {
            return null;
        }
        resolved.put(type, found);
        return found;
    }

    // ═══ EventPacket 信号 ═══

    /** 对玩家触发 EventPacket 规则引擎信号。 */
    public void signal(Player player, String signal, @Nullable Map<String, String> vars) {
        SignalDispatchable c = cap(SignalDispatchable.class);
        if (c != null) {
            safe(() -> c.dispatchSignal(signal, player, vars));
        }
    }

    // ═══ 播报 ═══

    /** 全服公告（forward=true 时跨服）。 */
    public void announce(String text, boolean forward) {
        AnnouncerBroadcastable c = cap(AnnouncerBroadcastable.class);
        if (c != null) {
            safe(() -> c.broadcastNow(text, forward));
        }
    }

    /** QQ 群推送（groupId<=0 时发全部已配置群）。 */
    public void qqNotify(long groupId, String message) {
        QQBotBroadcastable c = cap(QQBotBroadcastable.class);
        if (c == null) return;
        if (groupId > 0) {
            safe(() -> c.sendToGroup(groupId, message));
        } else {
            safe(() -> c.sendToAllGroups(message));
        }
    }

    // ═══ 表现层 ═══

    /** 播放字幕组（Announcer/Subtitle 模块）。 */
    public void subtitle(Player player, String groupId) {
        SubtitlePlayable c = cap(SubtitlePlayable.class);
        if (c != null) {
            safe(() -> c.playGroup(player, groupId));
        }
    }

    /** 发送聊天卡片。 */
    public void chatCard(Player player, String cardId, Map<String, String> data) {
        ChatCardSendable c = cap(ChatCardSendable.class);
        if (c != null) {
            safe(() -> c.sendChatCard(player, cardId, data));
        }
    }

    /** 触发战斗特效包。 */
    public void combatEffect(Player player, String packetId,
                             @Nullable Map<String, String> vars) {
        CombatEffectTriggerable c = cap(CombatEffectTriggerable.class);
        if (c != null) {
            safe(() -> c.triggerPacket(packetId, player, vars));
        }
    }

    // ═══ 战令 ═══

    /** 推进玩家战令任务进度。 */
    public void battlePassTask(Player player, String taskId, int increment) {
        BattlePassAccess c = cap(BattlePassAccess.class);
        if (c != null && increment != 0) {
            safe(() -> c.updateTaskProgress(player, taskId, increment));
        }
    }

    // ═══ 属性桥（宿主已注入，非 capability）═══

    /**
     * 副本内属性修饰（Symphony 路径；其它属性桥无 setAttribute 语义的自动跳过）。
     * sourceKey 建议 {@code "abyss"}，出本/结束统一 removeAttribute 清理。
     */
    public void applyAttribute(Player player, String attributeId, boolean percent,
                               double value, String sourceKey) {
        if (attributeBridge == null) return;
        var symphony = attributeBridge.symphony();
        if (symphony != null && symphony.available()) {
            safe(() -> symphony.setAttribute(player, attributeId, percent, value, sourceKey));
            safe(symphony::recalculate, player);
        }
    }

    /** 移除属性修饰源。 */
    public void removeAttribute(Player player, String sourceKey) {
        if (attributeBridge == null) return;
        var symphony = attributeBridge.symphony();
        if (symphony != null && symphony.available()) {
            safe(() -> symphony.removeAttribute(player, sourceKey));
            safe(symphony::recalculate, player);
        }
    }

    /**
     * 属性插件伤害/治疗事件接入副本战斗统计。
     * <p>
     * 伤害：攻击者须在副本内且目标不是玩家（副本内 PVP 伤害不算输出）。
     * 治疗：记录治疗者（source），source 为空（自回）时记被治疗者。
     * 原版 Bukkit 事件已由监听器统计，故跳过 {@code Source.BUKKIT} /
     * {@code SourceType.BUKKIT} 来源防重复计数。
     */
    public void wireAttributeStats(xuanmo.arcartxsuite.abyss.stats.CombatStatisticsService stats,
                                   java.util.function.Function<Player, AbyssInstance> instanceLookup) {
        if (attributeBridge == null || stats == null || !stats.enabled()) {
            return;
        }
        if (attributeBridge.hasDamageSource()) {
            attributeBridge.registerDamageListener(e -> {
                if (e.source() == xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent.Source.BUKKIT
                    || e.dodged() || e.damage() <= 0) {
                    return;
                }
                if (e.target() instanceof Player) return;
                Player attacker = e.attacker();
                if (attacker == null) return;
                AbyssInstance inst = instanceLookup.apply(attacker);
                if (inst != null) {
                    stats.recordDamageDealt(inst, attacker.getUniqueId(), e.damage());
                }
            });
        }
        if (attributeBridge.hasHealSource()) {
            attributeBridge.registerHealListener(e -> {
                if (e.sourceType() == xuanmo.arcartxsuite.api.attribute.AttributeHealEvent.SourceType.BUKKIT
                    || e.amount() <= 0) {
                    return;
                }
                Player healer = e.source() != null ? e.source()
                    : (e.target() instanceof Player t ? t : null);
                if (healer == null) return;
                AbyssInstance inst = instanceLookup.apply(healer);
                if (inst != null) {
                    stats.recordHealing(inst, healer.getUniqueId(), e.amount());
                }
            });
        }
    }

    // ═══ Essentials ═══

    /** Essentials 出生点（含 Multiverse 世界 spawn），缺席返回 null。 */
    public @Nullable Location essentialsSpawn(Player player) {
        EssentialsQueryable c = cap(EssentialsQueryable.class);
        if (c == null) return null;
        try {
            return c.getSpawn(player.getWorld());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Essentials AFK 状态。 */
    public boolean isAfk(Player player) {
        EssentialsQueryable c = cap(EssentialsQueryable.class);
        if (c == null) return false;
        try {
            return c.isAfk(player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══ 地图 ═══

    /** 推送/更新地牢入口外部标记到玩家地图。 */
    public void pushEntrance(Player player, MapExternalTargetSpec spec) {
        MapNavigable c = cap(MapNavigable.class);
        if (c != null) {
            safe(() -> c.upsertExternalTarget(player, spec, false));
        }
    }

    /** 清除 abyss 来源的全部外部目标。 */
    public void clearEntrances(Player player) {
        MapNavigable c = cap(MapNavigable.class);
        if (c != null) {
            safe(() -> c.clearExternalTargets(player, "abyss", true));
        }
    }

    /** MapNavigable 本体（需要 subscribe ready 等高级用法时直接取）。 */
    public @Nullable MapNavigable map() {
        return cap(MapNavigable.class);
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            logger.fine("[abyss] 联动调用失败: " + t.getMessage());
        }
    }

    private void safe(java.util.function.Consumer<Player> r, Player p) {
        try {
            r.accept(p);
        } catch (Throwable t) {
            logger.fine("[abyss] 联动调用失败: " + t.getMessage());
        }
    }
}
