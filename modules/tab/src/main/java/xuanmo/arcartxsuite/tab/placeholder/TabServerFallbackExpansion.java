package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class TabServerFallbackExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    public TabServerFallbackExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "server";
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
        return switch (identifier) {
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
            case "name" -> Bukkit.getServer().getName();
            case "version" -> Bukkit.getVersion();
            case "motd" -> Bukkit.getMotd();
            case "tps", "tps_1", "tps_5", "tps_15" -> getTps();
            default -> "";
        };
    }

    private String getTps() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            if (tps != null && tps.length > 0) {
                return String.format("%.2f", tps[0]);
            }
        } catch (NoSuchMethodError ignored) {
            // 旧版本不支持 getTPS()
        }
        return "";
    }
}
