package xuanmo.arcartxsuite.abyss.guide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.util.NavigationTarget;
import xuanmo.arcartxsuite.api.util.NavigationTargetService;

/**
 * 副本内引导服务：路标推送 + 路径渲染 + 到达判定（{@link NavigationTargetService} 封装）。
 * <p>
 * 实例级管理：{@code guide_point} 脚本动作对副本全员登记导航目标；
 * {@code guide_clear} 撤下；HUD 的 {@code guideText} 显示最新一条目标标题。
 * 到达时发 {@code axs.abyss.guide_arrive} 事件，并自动运行
 * scripts.yml 自定义段 {@code guide_<navId>}（存在则跑）。
 */
public final class GuideService {

    /** 到达回调（service 注入：发事件 + 跑脚本段）。 */
    public interface ArriveCallback {
        void onArrive(AbyssInstance instance, String navId, Player player);
    }

    private final NavigationTargetService nav;
    private final Logger logger;
    private volatile ArriveCallback arriveCallback;

    /** instanceId → (navId → 目标)——保序，HUD 显示最新一条。 */
    private final Map<UUID, LinkedHashMap<String, NavigationTarget>> active =
        new ConcurrentHashMap<>();
    /** instanceId → navId → 目标位置（新玩家进本补推用）。 */
    private final Map<UUID, Map<String, NavigationTarget>> byInstance =
        new ConcurrentHashMap<>();

    public GuideService(JavaPlugin plugin, WaypointBridgeAPI waypointBridge, Logger logger) {
        this.logger = logger;
        this.nav = new NavigationTargetService(plugin, waypointBridge, null, logger,
            new NavigationTargetService.Config(20, 30_000L, 2.0, 1_500L, true));
    }

    public void setArriveCallback(ArriveCallback cb) {
        this.arriveCallback = cb;
    }

    public void start() {
        nav.setArriveHandler((player, target, resolved) -> {
            Object att = target.attachment();
            if (att instanceof AbyssInstance inst && arriveCallback != null) {
                arriveCallback.onArrive(inst, target.navId(), player);
            }
        });
        nav.start();
    }

    public void shutdown() {
        nav.shutdown();
        active.clear();
        byInstance.clear();
    }

    // ═══ 目标管理 ═══

    /** 给副本全员登记/覆盖导航目标。 */
    public void setGuide(AbyssInstance instance, String navId, Location loc,
                         String title, double finishRange) {
        if (instance == null || navId == null || loc == null) return;
        NavigationTarget target = NavigationTarget.builder(navId)
            .world(loc.getWorld() != null ? loc.getWorld().getName() : "")
            .position(loc.getX(), loc.getY(), loc.getZ())
            .title(title)
            .titleTemplate("%label%  &8· &7%distance%m")
            .finishRange(Math.max(0, finishRange))
            .attachment(instance)
            .build();
        byInstance.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentHashMap<>())
            .put(navId, target);
        active.computeIfAbsent(instance.getInstanceId(), k -> new LinkedHashMap<>())
            .put(navId, target);
        for (Player p : instance.getPlayers()) {
            nav.setTarget(p, target);
        }
    }

    /** 撤下目标（navId 为空时撤本实例全部）。 */
    public void clearGuide(AbyssInstance instance, @Nullable String navId) {
        Map<String, NavigationTarget> map = byInstance.get(instance.getInstanceId());
        if (map == null) return;
        if (navId == null) {
            for (String id : map.keySet()) removeTarget(instance, id);
            return;
        }
        removeTarget(instance, navId);
    }

    private void removeTarget(AbyssInstance instance, String navId) {
        Map<String, NavigationTarget> map = byInstance.get(instance.getInstanceId());
        if (map != null) map.remove(navId);
        LinkedHashMap<String, NavigationTarget> a = active.get(instance.getInstanceId());
        if (a != null) a.remove(navId);
        for (Player p : instance.getPlayers()) {
            nav.removeTarget(p, navId);
        }
    }

    /** 新玩家进本补推全部激活目标。 */
    public void syncPlayer(Player player, AbyssInstance instance) {
        Map<String, NavigationTarget> map = byInstance.get(instance.getInstanceId());
        if (map == null) return;
        for (NavigationTarget t : map.values()) {
            nav.setTarget(player, t);
        }
    }

    /** 玩家退出清理（service quit 钩子调）。 */
    public void handleQuit(Player player) {
        nav.handleQuit(player);
    }

    /** 玩家单独离开实例：撤下其在该实例的全部目标（实例仍在跑）。 */
    public void clearPlayer(Player player, AbyssInstance instance) {
        Map<String, NavigationTarget> map = byInstance.get(instance.getInstanceId());
        if (map == null) return;
        for (String id : map.keySet()) nav.removeTarget(player, id);
    }

    /** 实例结束清理全部目标。 */
    public void cleanup(AbyssInstance instance) {
        Map<String, NavigationTarget> map = byInstance.remove(instance.getInstanceId());
        active.remove(instance.getInstanceId());
        if (map == null) return;
        for (Player p : instance.getPlayers()) {
            for (String id : map.keySet()) nav.removeTarget(p, id);
        }
    }

    // ═══ HUD 目标行 ═══

    /** 当前目标提示文本（最新登记的一条；无则空串）。 */
    public String guideText(AbyssInstance instance) {
        LinkedHashMap<String, NavigationTarget> a = active.get(instance.getInstanceId());
        if (a == null || a.isEmpty()) return "";
        NavigationTarget last = null;
        for (NavigationTarget t : a.values()) last = t;
        return last != null ? last.title() : "";
    }
}
