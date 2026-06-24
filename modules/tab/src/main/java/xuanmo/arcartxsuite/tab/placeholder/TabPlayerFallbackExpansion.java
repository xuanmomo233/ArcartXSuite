package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PAPI {@code player} 扩展的 fallback 实现。
 * <p>
 * 当 PlaceholderAPI 未加载原生 Expansion-player.jar 时，由 Tab 模块注入，
 * 提供基础 player 占位符（player_name、player_health、player_ping 等）。
 */
public final class TabPlayerFallbackExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    public TabPlayerFallbackExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "player";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        return switch (identifier) {
            case "name" -> player.getName();
            case "displayname" -> player.getDisplayName();
            case "uuid" -> player.getUniqueId().toString();
            case "health" -> String.valueOf((int) player.getHealth());
            case "max_health" -> String.valueOf((int) player.getMaxHealth());
            case "ping" -> String.valueOf(player.getPing());
            case "world" -> player.getWorld().getName();
            case "x" -> String.valueOf((int) player.getLocation().getX());
            case "y" -> String.valueOf((int) player.getLocation().getY());
            case "z" -> String.valueOf((int) player.getLocation().getZ());
            default -> "";
        };
    }
}
