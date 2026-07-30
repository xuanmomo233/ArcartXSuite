package xuanmo.arcartxsuite.combateffect.chronos;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.chronos.api.ChronosAPI;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerControllerChangeEvent;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerEnterStateEvent;
import priv.seventeen.artist.arcartx.chronos.api.event.PlayerLeaveStateEvent;

/**
 * Chronos 集成隔离层。
 * <p>
 * 本类是 combateffect 模块中<b>唯一</b>直接引用 ChronosAPI / Chronos 事件类的类。
 * ComboTrackerService 和 StateTriggerService 通过回调接口与本类交互，
 * 不再直接 import Chronos 类，从而在 Chronos 未安装时也能正常类加载。
 * <p>
 * 本类只在 Chronos 可用时才被 {@code Class.forName} 加载（由调用方负责），
 * 因此其直接 import 不会在 Chronos 缺失时触发 NoClassDefFoundError。
 */
public final class ChronosHook {

    private static final String CHRONOS_PLUGIN_NAME = "ArcartX_Chronos_Plugin";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final boolean debug;
    private final ChronosAPI chronosApi;
    private final Listener stateListener = new Listener() {};
    private final Listener comboListener = new Listener() {};

    /**
     * 尝试创建 ChronosHook。Chronos 未安装或 API 不可用时返回 null。
     * <p>
     * 本方法是本类的唯一入口，调用方应通过 {@code Class.forName} 延迟加载本类，
     * 确保只在 Chronos 可用时才触发本类的类加载（及其对 ChronosAPI 的引用）。
     */
    public static ChronosHook tryCreate(JavaPlugin plugin, Logger logger, boolean debug) {
        if (Bukkit.getPluginManager().getPlugin(CHRONOS_PLUGIN_NAME) == null
            && Bukkit.getPluginManager().getPlugin("Chronos") == null) {
            return null;
        }
        try {
            ChronosAPI api = ChronosAPI.getInstanceAPI();
            if (api == null) return null;
            return new ChronosHook(plugin, logger, debug, api);
        } catch (Exception | LinkageError e) {
            if (debug) {
                logger.warning("ChronosHook 初始化失败: " + e.getMessage());
            }
            return null;
        }
    }

    private ChronosHook(JavaPlugin plugin, Logger logger, boolean debug, ChronosAPI api) {
        this.plugin = plugin;
        this.logger = logger;
        this.debug = debug;
        this.chronosApi = api;
    }

    /** 查询玩家当前状态组 ID */
    public String getPlayerStateGroupId(Player player) {
        try {
            return chronosApi.getPlayerStateGroupId(player);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** 查询玩家当前控制器 ID */
    public String getPlayerControllerId(Player player) {
        try {
            return chronosApi.getPlayerControllerId(player);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /**
     * 尝试让玩家进入受控状态（用于死亡缓冲等场景）。
     *
     * @param player    玩家
     * @param stateId   状态 ID
     * @param durationMs 持续时间（毫秒）
     * @return true 表示调用成功（不代表状态一定生效）
     */
    public boolean tryEnterControlledState(Player player, String stateId, long durationMs) {
        try {
            chronosApi.tryEnterControlledState(player, stateId, durationMs);
            return true;
        } catch (Exception | LinkageError e) {
            if (debug) {
                logger.warning("ChronosHook tryEnterControlledState 失败: " + e.getMessage());
            }
            return false;
        }
    }

    // ─── Combo 集成：监听 PlayerEnterStateEvent ────────────────

    /**
     * 注册 PlayerEnterStateEvent 监听，当玩家进入状态且状态组在 {@code groups} 中时，
     * 调用 {@code onStateEnter}。
     *
     * @param groups       匹配的状态组集合
     * @param onStateEnter 回调（参数：玩家）
     * @return 注册的 Listener，用于 shutdown 时注销
     */
    public Listener registerComboListener(Set<String> groups, Consumer<Player> onStateEnter) {
        EventExecutor executor = (listener, event) -> {
            if (!(event instanceof PlayerEnterStateEvent enterEvent)) return;
            try {
                Player player = enterEvent.getPlayer();
                String group = chronosApi.getPlayerStateGroupId(player);
                if (group != null && groups.contains(group)) {
                    onStateEnter.accept(player);
                }
            } catch (Exception | LinkageError e) {
                if (debug) {
                    logger.warning("ChronosHook combo 事件处理失败: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(
            PlayerEnterStateEvent.class, comboListener, EventPriority.MONITOR, executor, plugin, true
        );
        return comboListener;
    }

    // ─── State/Controller 集成：监听状态进入/离开/控制器变更 ────

    /**
     * 注册状态进入/离开事件监听。
     *
     * @param onStateEnter 回调（参数：玩家、状态ID、true=进入）
     */
    public void registerStateListeners(BiConsumer<Player, StateChange> onStateChange) {
        // PlayerEnterStateEvent
        EventExecutor enterExecutor = (listener, event) -> {
            if (!(event instanceof PlayerEnterStateEvent enterEvent)) return;
            try {
                onStateChange.accept(enterEvent.getPlayer(),
                    new StateChange(enterEvent.getStateId(), true, getPlayerControllerId(enterEvent.getPlayer())));
            } catch (Exception e) {
                if (debug) {
                    logger.warning("ChronosHook 状态进入事件处理失败: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(
            PlayerEnterStateEvent.class, stateListener, EventPriority.MONITOR, enterExecutor, plugin, true
        );

        // PlayerLeaveStateEvent
        EventExecutor leaveExecutor = (listener, event) -> {
            if (!(event instanceof PlayerLeaveStateEvent leaveEvent)) return;
            try {
                onStateChange.accept(leaveEvent.getPlayer(),
                    new StateChange(leaveEvent.getStateId(), false, getPlayerControllerId(leaveEvent.getPlayer())));
            } catch (Exception e) {
                if (debug) {
                    logger.warning("ChronosHook 状态离开事件处理失败: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(
            PlayerLeaveStateEvent.class, stateListener, EventPriority.MONITOR, leaveExecutor, plugin, true
        );
    }

    /**
     * 注册控制器变更事件监听。
     *
     * @param onControllerChange 回调（参数：玩家、控制器ID）
     */
    public void registerControllerListener(BiConsumer<Player, String> onControllerChange) {
        EventExecutor executor = (listener, event) -> {
            if (!(event instanceof PlayerControllerChangeEvent controllerEvent)) return;
            try {
                onControllerChange.accept(controllerEvent.getPlayer(), controllerEvent.getControllerId());
            } catch (Exception e) {
                if (debug) {
                    logger.warning("ChronosHook 控制器事件处理失败: " + e.getMessage());
                }
            }
        };
        Bukkit.getPluginManager().registerEvent(
            PlayerControllerChangeEvent.class, stateListener, EventPriority.MONITOR, executor, plugin, true
        );
    }

    /** 注销所有监听器 */
    public void shutdown() {
        HandlerList.unregisterAll(stateListener);
        HandlerList.unregisterAll(comboListener);
    }

    /** 状态变更数据载体 */
    public record StateChange(String stateId, boolean enter, String controllerId) {}
}
