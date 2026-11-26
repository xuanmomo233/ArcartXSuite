package xuanmo.arcartxsuite.menu.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.condition.ScriptActionKind;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.menu.config.MenuActionDefinition;
import xuanmo.arcartxsuite.menu.config.MenuActionType;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.api.util.TemporaryOpExecutor;

/**
 * 菜单动作执行器，负责执行按钮点击或菜单开关时触发的各类动作。
 * <p>
 * 支持 {@link xuanmo.arcartxsuite.menu.config.MenuActionType} 定义的所有动作类型，
 * 包括执行命令、临时 OP 执行、控制台命令、发送消息、打开/关闭菜单、翻页、播放音效、
 * 分发信号和运行脚本等。
 */
public final class MenuActionExecutor {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final int MAX_PENDING_PER_PLAYER = 64;
    private static final int MAX_PENDING = 4096;

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final Logger logger;
    private final Supplier<SignalDispatchable> signalProvider;
    private final BooleanSupplier crossServerAllowed;
    // All scheduling and cancellation are on the server thread.
    private final Map<UUID, Set<DeferredAction>> pending = new HashMap<>();
    private int pendingCount;
    private long generation;
    private boolean stopped;

    public MenuActionExecutor(
        JavaPlugin plugin,
        MenuService menuService,
        Logger logger,
        Supplier<SignalDispatchable> signalProvider,
        BooleanSupplier crossServerAllowed
    ) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.logger = logger;
        this.signalProvider = signalProvider;
        this.crossServerAllowed = crossServerAllowed;
    }

    /**
     * 执行单个菜单动作。
     *
     * @param player 目标玩家
     * @param action 动作定义
     * @return {@code true} 表示执行后应关闭菜单
     */
    private boolean execute(ActionContext context, MenuActionDefinition action) {
        Player player = context.player();
        return switch (action.type()) {
            case COMMAND -> {
                String command = normalizeCommand(MenuConditionEvaluator.applyPlaceholders(player, action.value()));
                if (command.isBlank()) {
                    yield false;
                }
                // 占位符展开后统一校验：展开值（称号/displayname 等）可能携带分隔符注入多命令
                try {
                    xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(command);
                } catch (IllegalArgumentException e) {
                    logger.warning("Menu COMMAND 动作命令含分隔符，已拒绝: " + command);
                    yield false;
                }
                yield defer(context, 0L, () -> player.performCommand(command))
                    && menuService.configuration().settings().closeOnAction();
            }
            case CHAT -> {
                String raw = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (raw == null || raw.isBlank()) {
                    yield false;
                }
                final String chatMessage = raw.trim();
                yield defer(context, 0L, () -> player.chat(chatMessage))
                    && menuService.configuration().settings().closeOnAction();
            }
            case CONNECT -> {
                if (!crossServerAllowed.getAsBoolean()) {
                    player.sendMessage(menuService.messages().colorize(menuService.messages().noPermission()));
                    yield false;
                }
                String targetServer = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (targetServer == null || targetServer.isBlank()) {
                    yield false;
                }
                targetServer = targetServer.trim();
                final String serverName = targetServer;
                yield defer(context, 0L, () -> connectToServer(player, serverName))
                    && menuService.configuration().settings().closeOnAction();
            }
            case PLAYER_OP -> {
                String command = normalizeCommand(MenuConditionEvaluator.applyPlaceholders(player, action.value()));
                if (command.isBlank()) {
                    yield false;
                }
                try {
                    xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(command);
                } catch (IllegalArgumentException e) {
                    logger.warning("Menu PLAYER_OP 动作命令含分隔符，已拒绝: " + command);
                    yield false;
                }
                yield defer(context, 0L, () -> TemporaryOpExecutor.execute(player, command))
                    && menuService.configuration().settings().closeOnAction();
            }
            case CONSOLE -> {
                String command = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (command.isBlank()) {
                    yield false;
                }
                // 防命令注入：展开后含分隔符直接拒绝（strip 会把注入语句降级为仍可能带参数的命令继续执行）
                try {
                    command = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(command);
                } catch (IllegalArgumentException e) {
                    logger.warning("Menu CONSOLE 动作命令含分隔符，已拒绝: " + command);
                    yield false;
                }
                yield ThrottledCommandExecutor.dispatchIf(command, () -> allowed(context))
                    && menuService.configuration().settings().closeOnAction();
            }
            case MESSAGE -> {
                player.sendMessage(menuService.messages().colorize(
                    MenuConditionEvaluator.applyPlaceholders(player, action.value())
                ));
                yield false;
            }
            case OPEN -> {
                String menuId = action.value();
                if (menuId.isBlank()) {
                    yield false;
                }
                defer(context, 0L, () -> menuService.openMenu(player, menuId, 0));
                yield false;
            }
            case CLOSE -> {
                closeOrigin(context);
                yield false;
            }
            case PAGE -> {
                if (menuService.isCurrentSession(player, context.origin())) menuService.changePage(player, action.value());
                yield false;
            }
            case SOUND -> {
                playSound(player, action.value());
                yield false;
            }
            case SIGNAL -> {
                String[] segments = action.value() == null
                    ? new String[0]
                    : action.value().split("\\|", -1);
                String signal = segments.length == 0
                    ? ""
                    : MenuConditionEvaluator.applyPlaceholders(player, segments[0].trim()).trim();
                if (signal.isBlank()) {
                    logger.warning("Menu signal 动作解析为空信号，已跳过。");
                    yield false;
                }

                SignalDispatchable dispatcher = signalProvider == null ? null : signalProvider.get();
                if (dispatcher == null) {
                    logger.warning("Menu signal 动作无法执行：EventPacket 能力不可用，已跳过。");
                    yield false;
                }

                Map<String, String> variables = new LinkedHashMap<>();
                for (int index = 1; index < segments.length; index++) {
                    String segment = segments[index];
                    int separator = segment.indexOf('=');
                    if (separator < 0) {
                        continue;
                    }
                    String key = segment.substring(0, separator).trim();
                    if (key.isBlank()) {
                        continue;
                    }
                    String value = MenuConditionEvaluator.applyPlaceholders(
                        player,
                        segment.substring(separator + 1).trim()
                    );
                    variables.put(key, value);
                }

                dispatcher.dispatchSignal(signal, player, variables);
                yield menuService.configuration().settings().closeOnAction();
            }
            case SCRIPT_JS -> runScript(player, ScriptActionKind.JS, action.value());
            case SCRIPT_ARIA -> runScript(player, ScriptActionKind.ARIA, action.value());
            case DELAY -> false; // 延迟由 executeAll 处理，单个 execute 调用时无副作用
            case NONE -> false;
        };
    }

    private boolean runScript(Player player, ScriptActionKind kind, String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        ScriptConditionServices.actionExecutor().execute(player, kind, script);
        return menuService.configuration().settings().closeOnAction();
    }

    /**
     * 依次执行多个菜单动作，若任一动作返回需要关闭菜单则在全部执行后关闭。
     * <p>
     * 支持 {@link MenuActionType#DELAY} 动作：遇到 delay 时会先处理已累积的关闭请求
     * （立即关闭菜单 UI），再按指定 tick 数延迟执行后续所有动作。DELAY 是通用延时，
     * 可延时执行任意后续动作组合，也支持嵌套多个 DELAY（分段延时）。
     *
     * @param player  目标玩家
     * @param actions 动作定义集合
     */
    void executeAll(Player player, MenuSession session, MenuDefinition menu, MenuButtonDefinition button,
                    List<MenuActionDefinition> actions) {
        if (stopped || actions.isEmpty()) return;
        var auth = AuthAccess.snapshot(player.getUniqueId());
        var context = new ActionContext(player, auth, session, menu.permission(),
            button == null ? "" : button.permission(), generation);
        if (authorized(context, auth)) executeAllFrom(context, actions, 0);
    }

    /** 模块卸载/服务停止：取消全部在途延迟动作链，后续调度点不再执行。 */
    public void shutdown() {
        stopped = true;
        invalidatePending();
    }

    void invalidatePending() {
        generation++;
        for (var tasks : pending.values()) for (var task : tasks) task.handle.cancel();
        pending.clear();
        pendingCount = 0;
    }

    void cancelPlayer(UUID playerId) {
        var tasks = pending.remove(playerId);
        if (tasks == null) return;
        pendingCount -= tasks.size();
        for (var task : tasks) task.handle.cancel();
    }

    private boolean allowed(ActionContext context) {
        if (!isActive(context)) return false;
        Player player = context.player();
        UUID id = context.auth().playerId();
        return player.isOnline() && Bukkit.getPlayer(id) == player && authorized(context, AuthAccess.snapshot(id));
    }

    private boolean authorized(ActionContext context, AuthSnapshot current) {
        return current.admitted() && current.generation() == context.auth().generation()
            && Objects.equals(current.connectionId(), context.auth().connectionId())
            && MenuConditionEvaluator.hasPermission(context.player(), context.menuPermission())
            && MenuConditionEvaluator.hasPermission(context.player(), context.buttonPermission());
    }

    private boolean isActive(ActionContext context) {
        return !stopped && generation == context.generation();
    }

    private void closeOrigin(ActionContext context) {
        if (menuService.isCurrentSession(context.player(), context.origin())) menuService.closeMenu(context.player());
    }

    private void executeAllFrom(ActionContext context, List<MenuActionDefinition> actions, int startIndex) {
        boolean shouldClose = false;
        for (int i = startIndex; i < actions.size(); i++) {
            if (!isActive(context)) return;
            MenuActionDefinition action = actions.get(i);
            if (action.type() == MenuActionType.DELAY) {
                // 先把累积的关闭请求处理掉（立即关闭 menu UI），再延迟执行后续动作
                if (shouldClose) {
                    closeOrigin(context);
                    shouldClose = false;
                }
                long delayTicks = parseDelayTicks(action.value());
                if (delayTicks <= 0) {
                    continue;
                }
                int nextIndex = i + 1;
                if (nextIndex < actions.size()) defer(context, delayTicks, () -> executeAllFrom(context, actions, nextIndex));
                return;
            }
            shouldClose |= execute(context, action);
        }
        if (shouldClose) {
            closeOrigin(context);
        }
    }

    private boolean defer(ActionContext context, long delayTicks, Runnable action) {
        if (!isActive(context) || pendingCount >= MAX_PENDING) return false;
        UUID playerId = context.auth().playerId();
        var tasks = pending.get(playerId);
        if (tasks != null && tasks.size() >= MAX_PENDING_PER_PLAYER) return false;
        var task = new DeferredAction(context, action);
        var scheduler = Bukkit.getScheduler();
        task.handle = delayTicks == 0L ? scheduler.runTask(plugin, task) : scheduler.runTaskLater(plugin, task, delayTicks);
        pending.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(task);
        pendingCount++;
        return true;
    }

    private final class DeferredAction implements Runnable {
        private final ActionContext context;
        private final Runnable action;
        private BukkitTask handle;

        private DeferredAction(ActionContext context, Runnable action) {
            this.context = context;
            this.action = action;
        }

        @Override public void run() {
            UUID id = context.auth().playerId();
            var tasks = pending.get(id);
            if (tasks == null || !tasks.remove(this)) return;
            pendingCount--;
            if (tasks.isEmpty()) pending.remove(id);
            if (allowed(context)) action.run();
        }
    }

    private record ActionContext(Player player, AuthSnapshot auth, MenuSession origin, String menuPermission,
                                 String buttonPermission, long generation) {}

    private static long parseDelayTicks(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void connectToServer(Player player, String serverName) {
        if (!crossServerAllowed.getAsBoolean()) return;
        try {
            var messenger = plugin.getServer().getMessenger();
            if (!messenger.isOutgoingChannelRegistered(plugin, BUNGEE_CHANNEL)) {
                messenger.registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeUTF("Connect");
                output.writeUTF(serverName);
            }
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
        } catch (IOException exception) {
            logger.warning("Menu connect 动作发送失败: " + exception.getMessage());
        }
    }

    private static String normalizeCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("/")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private static void playSound(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] parts = raw.split("\\|");
        String soundName = parts[0].trim();
        float volume = parts.length > 1 ? parseFloat(parts[1], 1F) : 1F;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1F) : 1F;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), soundName.toLowerCase(Locale.ROOT), volume, pitch);
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
