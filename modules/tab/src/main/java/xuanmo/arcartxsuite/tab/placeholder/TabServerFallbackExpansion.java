package xuanmo.arcartxsuite.tab.placeholder;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PAPI {@code server} 扩展的 fallback 实现。
 * <p>
 * 当 PlaceholderAPI 未加载原生 Expansion-server.jar 时，由 Tab 模块注入，
 * 提供基础 server 占位符（server_online、server_tps_1 等）。
 */
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
            case "tps_1" -> resolveTps1();
            case "tps_5" -> resolveTps5();
            case "tps_15" -> resolveTps15();
            case "name" -> Bukkit.getServer().getName();
            case "version" -> Bukkit.getVersion();
            default -> "";
        };
    }

    private String resolveTps1() {
        return resolveTpsIndex(0);
    }

    private String resolveTps5() {
        return resolveTpsIndex(1);
    }

    private String resolveTps15() {
        return resolveTpsIndex(2);
    }

    private String resolveTpsIndex(int index) {
        try {
            Method getTPS = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(Bukkit.getServer());
            if (index < tps.length) {
                return String.format("%.2f", tps[index]);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
