package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * Tab 模块内置的 PAPI player 占位符回退扩展。
 * <p>
 * 当 PlaceholderAPI 未安装 {@code Expansion-player.jar} 时，由 Tab 模块主动注入，
 * 提供与 PAPI Player 扩展对齐的 {@code %player_xxx%} 占位符，保证 Tab 基础功能可用。
 * <p>
 * 支持的占位符：
 * <ul>
 *   <li>{@code %player_name%}</li>
 *   <li>{@code %player_displayname%} / {@code %player_display_name%}</li>
 *   <li>{@code %player_uuid%}</li>
 *   <li>{@code %player_world%}</li>
 *   <li>{@code %player_x%} / {@code %player_y%} / {@code %player_z%}</li>
 *   <li>{@code %player_health%}</li>
 *   <li>{@code %player_max_health%}</li>
 *   <li>{@code %player_ping%}</li>
 *   <li>{@code %player_gamemode%}</li>
 *   <li>{@code %player_scoreboardteam%} / {@code %player_scoreboard_team%}</li>
 * </ul>
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
        return "ArcartXSuite";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        return switch (identifier) {
            case "name" -> player.getName();
            case "displayname", "display_name" -> nullToEmpty(player.getDisplayName());
            case "uuid" -> player.getUniqueId().toString();
            case "world" -> player.getWorld().getName();
            case "x" -> String.valueOf(player.getLocation().getBlockX());
            case "y" -> String.valueOf(player.getLocation().getBlockY());
            case "z" -> String.valueOf(player.getLocation().getBlockZ());
            case "health" -> formatNumber(player.getHealth());
            case "max_health" -> formatNumber(resolveMaxHealth(player));
            case "ping" -> String.valueOf(Math.max(0, player.getPing()));
            case "gamemode" -> player.getGameMode().name();
            case "scoreboardteam", "scoreboard_team" -> {
                var team = player.getScoreboard().getEntryTeam(player.getName());
                yield team != null ? team.getName() : "";
            }
            default -> null;
        };
    }

    private static double resolveMaxHealth(Player player) {
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0D;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
