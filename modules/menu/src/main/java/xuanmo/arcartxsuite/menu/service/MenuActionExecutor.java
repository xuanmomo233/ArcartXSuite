package xuanmo.arcartxsuite.menu.service;

import java.util.LinkedHashMap;
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
import xuanmo.arcartxsuite.util.TemporaryOpExecutor;

public final class MenuActionExecutor {

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
    }

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
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
                yield menuService.configuration().settings().closeOnAction();
            }
            case PLAYER_OP -> {
                String command = normalizeCommand(MenuConditionEvaluator.applyPlaceholders(player, action.value()));
                if (command.isBlank()) {
                    yield false;
                }
                Bukkit.getScheduler().runTask(
                    plugin,
                    () -> TemporaryOpExecutor.execute(
                        player,
                        () -> {
                            player.performCommand(command);
                            return null;
                        }
                    )
                );
                yield menuService.configuration().settings().closeOnAction();
            }
            case CONSOLE -> {
                String command = MenuConditionEvaluator.applyPlaceholders(player, action.value());
                if (command.isBlank()) {
                    yield false;
                }
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
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
                Bukkit.getScheduler().runTask(plugin, () -> menuService.openMenu(player, menuId, 0));
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

    public void executeAll(Player player, Iterable<MenuActionDefinition> actions) {
        boolean shouldClose = false;
        for (MenuActionDefinition action : actions) {
            shouldClose |= execute(player, action);
        }
        if (shouldClose) {
            menuService.closeMenu(player);
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
