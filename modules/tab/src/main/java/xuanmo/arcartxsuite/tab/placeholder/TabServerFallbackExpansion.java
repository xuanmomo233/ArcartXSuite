package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * 服务器级 PlaceholderAPI 兜底扩展。
 * <p>
 * 当服务器未安装 {@code server} 占位符扩展时，由 Tab 模块注册此兜底实现，
 * 提供 {@code %server_online%}、{@code %server_tps%} 等服务器占位符，
 * 确保 Tab 列表渲染不依赖第三方 PlaceholderAPI 扩展。
 * 关联模块：{@code tab}。
 */
public final class TabServerFallbackExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    /**
     * @param plugin 宿主插件实例，用于读取作者与版本信息
     */
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

    /**
     * 处理占位符请求，根据标识符返回对应的服务器属性值。
     *
     * @param player    触发占位符的玩家（本扩展不依赖此参数）
     * @param identifier 占位符标识符（如 {@code online}、{@code tps} 等）
     * @return 占位符对应的值，无法识别时返回空串
     */
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
            Object result = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (result instanceof double[] tps && tps.length > 0) {
                return String.format("%.2f", tps[0]);
            }
        } catch (Exception ignored) {
            // 旧版本或不同服务端不支持 getTPS()
        }
        return "";
    }
}
