package xuanmo.arcartxsuite.menu.service;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.menu.config.MenuActionDefinition;
import xuanmo.arcartxsuite.menu.config.MenuActionType;

public final class MenuActionExecutor {

    private final JavaPlugin plugin;
    private final MenuService menuService;

    public MenuActionExecutor(JavaPlugin plugin, MenuService menuService) {
        this.plugin = plugin;
        this.menuService = menuService;
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
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean wasOp = player.isOp();
                    try {
                        player.setOp(true);
                        player.performCommand(command);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                });
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
            case NONE -> false;
        };
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
