package xuanmo.arcartxsuite.abyss.links;

import java.util.Map;
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
 * 跨模块联动封装：实时解析宿主 capability（不缓存——目标模块 reload 后
 * 实现对象整体更换，缓存会持陈旧引用静默失效），全部方法在目标模块缺席时静默跳过。
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

    public AbyssLinks(CapabilityResolver capabilityResolver,
                      AttributeBridgeRegistry attributeBridge, Logger logger) {
        this.capabilityResolver = capabilityResolver;
        this.attributeBridge = attributeBridge;
        this.logger = logger;
    }

    // ═══ 内部：capability 懒解析 ═══

    /**
     * 每次调用实时解析——不缓存。
     * capability 实现对象在目标模块 reload 时整体更换（如 DungeonExternalInstaller
     * 部署配套文件后 reloadModule(questgps)），正缓存会持有陈旧实现导致静默失效；
     * 解析本身只是注册表查表，开销可忽略。
     */
    private <T> @Nullable T cap(Class<T> type) {
        try {
            return capabilityResolver.get(type);
        } catch (Throwable t) {
            return null;
        }
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

    // ═══ Conversation ═══

    /**
     * 为玩家强制打开绑定到指定 Adyeshach NPC 的 Chemdah 会话（ArcartX 对话 UI）。
     * conversation 模块未装载 / NPC 未绑定会话时返回 false，调用方可降级处理。
     */
    public boolean conversationOpen(Player player, String npcId) {
        xuanmo.arcartxsuite.api.capability.ConversationPlayable c =
            cap(xuanmo.arcartxsuite.api.capability.ConversationPlayable.class);
        if (c == null) return false;
        try {
            return c.openConversation(player, npcId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 指定 NPC id 是否绑定了至少一个 Chemdah 会话。 */
    public boolean hasConversation(String npcId) {
        xuanmo.arcartxsuite.api.capability.ConversationPlayable c =
            cap(xuanmo.arcartxsuite.api.capability.ConversationPlayable.class);
        if (c == null) return false;
        try {
            return c.hasConversation(npcId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 播放电影序列（conversation 模块 cinematics），返回是否成功开始。 */
    public boolean playCinematic(Player player, String sequenceId) {
        xuanmo.arcartxsuite.api.capability.ConversationPlayable c =
            cap(xuanmo.arcartxsuite.api.capability.ConversationPlayable.class);
        if (c == null) return false;
        try {
            return c.playCinematic(player, sequenceId);
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══ QuestGPS ═══

    /**
     * 任务追踪联动：questgps 引导 HUD 卡片显示任务目标。
     * {@code taskId} 为空时追踪整个任务，否则追踪指定步骤。
     * questgps 未装载时静默跳过。
     */
    public void questTrack(Player player, String questId, @Nullable String taskId) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c == null) return;
        if (taskId != null && !taskId.isEmpty()) {
            safe(() -> c.trackTask(player, questId, taskId));
        } else {
            safe(() -> c.trackQuest(player, questId));
        }
    }

    /** 推送任务供玩家选择（questgps offer）。 */
    public void questOffer(Player player, String questId, boolean openMenu) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c != null) {
            safe(() -> c.offerQuest(player, questId, openMenu));
        }
    }

    /** 让玩家接取 Chemdah 任务（questgps acceptQuest，玩家级）。 */
    public void questAccept(Player player, String questId) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c != null) {
            safe(() -> c.acceptQuest(player, questId));
        }
    }

    /** 打开 questgps 任务菜单。 */
    public void questMenu(Player player) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c != null) {
            safe(() -> c.openMenu(player));
        }
    }

    /**
     * 查询玩家任务档案状态（Chemdah 视角）。
     * questgps 缺席返回 {@link xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState#NONE}。
     */
    public xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState questState(
            Player player, String questId) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c == null) {
            return xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState.NONE;
        }
        try {
            var s = c.questState(player, questId);
            return s != null ? s
                : xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState.NONE;
        } catch (Throwable t) {
            return xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState.NONE;
        }
    }

    /** 其他模块入口是否被 questgps 主线门禁锁定（如 "abyss.&lt;副本id&gt;"）。 */
    public boolean moduleEntryLocked(Player player, String moduleEntryId) {
        xuanmo.arcartxsuite.api.capability.QuestGpsNavigable c =
            cap(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class);
        if (c == null) return false;
        try {
            return c.moduleEntryLocked(player, moduleEntryId);
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══ 宿主模块管理（external 部署后重载目标模块用） ═══

    /** 模块管理 capability（宿主注册；缺席返回 null）。 */
    public @Nullable xuanmo.arcartxsuite.api.capability.ModuleAdminCapability moduleAdmin() {
        try {
            return cap(xuanmo.arcartxsuite.api.capability.ModuleAdminCapability.class);
        } catch (Throwable t) {
            return null;
        }
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
