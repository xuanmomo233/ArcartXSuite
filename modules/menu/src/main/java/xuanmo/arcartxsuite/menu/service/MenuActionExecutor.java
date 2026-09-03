package xuanmo.arcartxsuite.menu.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.condition.ScriptActionKind;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.menu.config.MenuActionDefinition;
import xuanmo.arcartxsuite.menu.config.MenuActionType;
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

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final Logger logger;
    private final Supplier<SignalDispatchable> signalProvider;

    public MenuActionExecutor(
        JavaPlugin plugin,
        MenuService menuService,
        Logger logger,
        Supplier<SignalDispatchable> signalProvider
    ) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.logger = logger;
        this.signalProvider = signalProvider;
        // 确保 BungeeCord outgoing channel 已注册，供 connect: 动作使用
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (IllegalArgumentException ignored) {
            // channel 已注册，忽略
        }
    }

    /**
     * 执行单个菜单动作。
     *
     * @param player 目标玩家
     * @param action 动作定义
     * @return {@code true} 表示执行后应关闭菜单
     */
    public boolean execute(Player player, MenuActionDefinition action) {
        if (player == null || action == null) {
            return false;
        }
        return switch (action.type()) {
            case COMMAND -> {
                String command = normalizeCommand(MenuConditionEvaluator.applyPlaceholders(player, action.value()));
                if (command.isBlank()) {
                    yield false;
                }
                AxsScheduler.runTask(plugin, () -> player.performCommand(command));
                yield menuService.configuration().settings().closeOnAction();
            }
            case CHAT -> {
                String raw = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (raw == null || raw.isBlank()) {
                    yield false;
                }
                final String chatMessage = raw.trim();
                AxsScheduler.runTask(plugin, () -> player.chat(chatMessage));
                yield menuService.configuration().settings().closeOnAction();
            }
            case CONNECT -> {
                String targetServer = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (targetServer == null || targetServer.isBlank()) {
                    yield false;
                }
                targetServer = targetServer.trim();
                final String serverName = targetServer;
                AxsScheduler.runTask(plugin, () -> connectToServer(player, serverName));
                yield menuService.configuration().settings().closeOnAction();
            }
            case PLAYER_OP -> {
                String command = normalizeCommand(MenuConditionEvaluator.applyPlaceholders(player, action.value()));
                if (command.isBlank()) {
                    yield false;
                }
                AxsScheduler.runTask(
                plugin,
                () -> TemporaryOpExecutor.execute(player, command)
            );
            yield menuService.configuration().settings().closeOnAction();
            }
            case CONSOLE -> {
                String command = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (command.isBlank()) {
                    yield false;
                }
                // 防止命令注入：清理命令分隔符
                final String safeCommand = xuanmo.arcartxsuite.api.command.CommandSanitizer.stripCommandSeparators(command);
                AxsScheduler.runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), safeCommand));
                yield menuService.configuration().settings().closeOnAction();
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
                AxsScheduler.runTask(plugin, () -> menuService.openMenu(player, menuId, 0));
                yield false;
            }
            case CLOSE -> {
                menuService.closeMenu(player);
                yield false;
            }
            case PAGE -> {
                menuService.changePage(player, action.value());
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
    public void executeAll(Player player, List<MenuActionDefinition> actions) {
        executeAllFrom(player, actions, 0);
    }

    private void executeAllFrom(Player player, List<MenuActionDefinition> actions, int startIndex) {
        boolean shouldClose = false;
        for (int i = startIndex; i < actions.size(); i++) {
            MenuActionDefinition action = actions.get(i);
            if (action.type() == MenuActionType.DELAY) {
                // 先把累积的关闭请求处理掉（立即关闭 menu UI），再延迟执行后续动作
                if (shouldClose) {
                    menuService.closeMenu(player);
                    shouldClose = false;
                }
                long delayTicks = parseDelayTicks(action.value());
                if (delayTicks <= 0) {
                    continue;
                }
                int nextIndex = i + 1;
                AxsScheduler.runTaskLater(plugin, () -> {
                    if (player.isOnline() && nextIndex < actions.size()) {
                        executeAllFrom(player, actions, nextIndex);
                    }
                }, delayTicks);
                return;
            }
            shouldClose |= execute(player, action);
        }
        if (shouldClose) {
            menuService.closeMenu(player);
        }
    }

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
        try {
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
