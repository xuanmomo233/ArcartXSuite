package xuanmo.arcartxsuite.tab.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * 玩家级 PlaceholderAPI 兜底扩展。
 * <p>
 * 当服务器未安装 {@code player} 占位符扩展时，由 Tab 模块注册此兜底实现，
 * 提供 {@code %player_name%}、{@code %player_ping%} 等常用玩家占位符，
 * 确保 Tab 列表渲染不依赖第三方 PlaceholderAPI 扩展。
 * 关联模块：{@code tab}。
 */
public final class TabPlayerFallbackExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    /**
     * @param plugin 宿主插件实例，用于读取作者与版本信息
     */
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

    /**
     * 处理占位符请求，根据标识符返回对应的玩家属性值。
     *
     * @param player    触发占位符的玩家，{@code null} 时返回空串
     * @param identifier 占位符标识符（如 {@code name}、{@code ping} 等）
     * @return 占位符对应的值，无法识别时返回空串
     */
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
