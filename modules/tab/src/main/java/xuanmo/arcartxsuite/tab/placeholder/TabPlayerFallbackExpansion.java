package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
            case "displayname", "display_name" -> player.getDisplayName();
            case "uuid" -> player.getUniqueId().toString();
            case "world" -> player.getWorld().getName();
            case "x" -> String.valueOf(player.getLocation().getBlockX());
            case "y" -> String.valueOf(player.getLocation().getBlockY());
            case "z" -> String.valueOf(player.getLocation().getBlockZ());
            case "health" -> String.valueOf(player.getHealth());
            case "max_health" -> String.valueOf(AttributeResolver.getMaxHealth(player));
            case "ping" -> String.valueOf(player.getPing());
            case "gamemode" -> player.getGameMode().name().toLowerCase();
            case "scoreboardteam", "scoreboard_team" -> player.getScoreboard().getEntryTeam(player.getName()) != null
                ? player.getScoreboard().getEntryTeam(player.getName()).getName() : "";
            default -> "";
        };
    }
}
