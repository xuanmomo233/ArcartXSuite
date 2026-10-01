package xuanmo.arcartxsuite.abyss.links;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigable;

/**
 * 地牢入口地图标记：把模板 {@code entrance} 配置推送为地图外部目标
 * （紫色标记，useMapNavigation=true → 玩家点"导航"由地图内置导航栈执行，
 * 无需 abyss 侧回调）。
 * <p>
 * 刷新时机：玩家进服、{@code axs.map.ready} 事件（地图重载后逐玩家发布）、
 * 模板 reload——统一走 {@link #pushAll} 全量 reconcile。
 * 模块停用时清掉 abyss 来源的全部目标。
 */
public final class EntranceMarkerService {

    public static final String SOURCE = "abyss";

    private final AbyssLinks links;
    private final java.util.function.Supplier<java.util.Collection<AbyssTemplate>> templates;
    private final java.util.function.Supplier<EventBusCapability> eventBus;
    private final Logger logger;
    private volatile @Nullable String mapReadySub;
    private volatile boolean subscribed;

    public EntranceMarkerService(AbyssLinks links,
                                 java.util.function.Supplier<java.util.Collection<AbyssTemplate>> templates,
                                 java.util.function.Supplier<EventBusCapability> eventBus,
                                 Logger logger) {
        this.links = links;
        this.templates = templates;
        this.eventBus = eventBus;
        this.logger = logger;
    }

    /**
     * 订阅 axs.map.ready（地图重载后重推）。
     * EventBus 在模块启动早期可能未就绪——做成幂等重试：
     * 每次推送前尝试一次，成功订阅后不再重复。
     */
    public void start() {
        ensureSubscribed();
    }

    private void ensureSubscribed() {
        if (subscribed) return;
        EventBusCapability bus = eventBus.get();
        if (bus == null) return;
        try {
            mapReadySub = bus.subscribe("axs.map.ready", ev -> {
                Player p = ev.player();
                if (p != null) push(p);
            });
            subscribed = true;
        } catch (Throwable t) {
            logger.fine("[abyss] 订阅 axs.map.ready 失败: " + t.getMessage());
        }
    }

    public void shutdown() {
        EventBusCapability bus = eventBus.get();
        if (bus != null && mapReadySub != null) {
            try { bus.unsubscribe(mapReadySub); } catch (Throwable ignored) { }
        }
        // 清掉所有在线玩家的 abyss 来源标记
        for (Player p : Bukkit.getOnlinePlayers()) {
            links.clearEntrances(p);
        }
    }

    /** 模板 reload 后调用：向所有在线玩家重推入口集合。 */
    public void pushAll() {
        ensureSubscribed();
        MapNavigable map = links.map();
        if (map == null) return;
        List<MapExternalTargetSpec> specs = buildSpecs();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                map.reconcileExternalTargets(p, SOURCE, specs);
            } catch (Throwable t) {
                logger.fine("[abyss] 推送入口标记失败 " + p.getName() + ": " + t.getMessage());
            }
        }
    }

    /** 单玩家推送（进服/map.ready 回调）。 */
    public void push(Player player) {
        ensureSubscribed();
        MapNavigable map = links.map();
        if (map == null) return;
        try {
            map.reconcileExternalTargets(player, SOURCE, buildSpecs());
        } catch (Throwable t) {
            logger.fine("[abyss] 推送入口标记失败 " + player.getName() + ": " + t.getMessage());
        }
    }

    private List<MapExternalTargetSpec> buildSpecs() {
        List<MapExternalTargetSpec> specs = new ArrayList<>();
        for (AbyssTemplate t : templates.get()) {
            var dl = t.links();
            if (dl == null || dl.entrance() == null) continue;
            String[] v = dl.entrance().split(";", 2);
            if (v.length != 2) continue;
            String[] c = v[1].split(",");
            if (c.length < 3) continue;
            try {
                specs.add(new MapExternalTargetSpec(
                    "dungeon:" + t.id(), SOURCE, v[0].trim(),
                    t.name(), t.difficulty(),
                    Double.parseDouble(c[0].trim()),
                    Double.parseDouble(c[1].trim()),
                    Double.parseDouble(c[2].trim()),
                    "dungeon", false, 0, true));
            } catch (NumberFormatException ignored) { }
        }
        return specs;
    }
}
