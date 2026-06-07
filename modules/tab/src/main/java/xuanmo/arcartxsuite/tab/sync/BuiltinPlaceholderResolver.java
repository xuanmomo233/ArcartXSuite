package xuanmo.arcartxsuite.tab.sync;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

/**
 * 内置占位符解析器。
 *
 * <p>在 PlaceholderAPI 不可用时提供常见 {@code %player_xxx%} / {@code %server_xxx%} 占位符的 fallback 解析，
 * 让 Tab 模块的基本功能不依赖外部插件。
 *
 * <p>支持的 player 占位符（与 PAPI Player 扩展对齐）：
 * <ul>
 *   <li>{@code %player_name%} — 玩家名</li>
 *   <li>{@code %player_displayname%} / {@code %player_display_name%} — 显示名</li>
 *   <li>{@code %player_uuid%} — UUID</li>
 *   <li>{@code %player_world%} — 所在世界名</li>
 *   <li>{@code %player_x%} / {@code %player_y%} / {@code %player_z%} — 整数坐标</li>
 *   <li>{@code %player_health%} — 当前血量</li>
 *   <li>{@code %player_max_health%} — 最大血量</li>
 *   <li>{@code %player_ping%} — 延迟（ms）</li>
 *   <li>{@code %player_gamemode%} — 游戏模式</li>
 *   <li>{@code %player_scoreboardteam%} — 计分板队伍名（无队伍返回空串）</li>
 * </ul>
 *
 * <p>支持的 server 占位符（与 PAPI Server 扩展对齐）：
 * <ul>
 *   <li>{@code %server_online%} — 在线人数</li>
 *   <li>{@code %server_max_players%} — 最大人数</li>
 *   <li>{@code %server_name%} — 服务器名称</li>
 *   <li>{@code %server_version%} — 服务端版本</li>
 *   <li>{@code %server_motd%} — MOTD</li>
 *   <li>{@code %server_tps%} / {@code %server_tps_1%} — 最近 1 分钟 TPS</li>
 *   <li>{@code %server_tps_5%} — 最近 5 分钟 TPS</li>
 *   <li>{@code %server_tps_15%} — 最近 15 分钟 TPS</li>
 * </ul>
 */
public final class BuiltinPlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final double[] EMPTY_TPS = new double[]{20.0, 20.0, 20.0};
    private static volatile double[] cachedTps = null;
    private static volatile long lastTpsFetch = 0L;

    private BuiltinPlaceholderResolver() {
    }

    /**
     * 解析输入字符串中的内置占位符。
     *
     * @param input  原始模板，可能包含 {@code %key%}
     * @param player 用于提供 player 上下文的玩家；可为 null（此时所有 player 占位符保留原样）
     * @return 已替换内置占位符后的字符串；无法识别的占位符原样保留，供后续 PAPI 处理
     */
    public static String resolve(String input, Player player) {
        if (input == null || input.isBlank() || !input.contains("%")) {
            return input == null ? "" : input;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());
            String key = matcher.group(1);
            String replacement = resolveKey(key, player);
            if (replacement == null) {
                result.append(matcher.group());
            } else {
                result.append(replacement);
            }
            lastEnd = matcher.end();
        }
        result.append(input, lastEnd, input.length());
        return result.toString();
    }

    private static String resolveKey(String key, Player player) {
        // player_ 前缀
        if (key.startsWith("player_")) {
            return resolvePlayerKey(key.substring(7), player);
        }
        // server_ 前缀
        if (key.startsWith("server_")) {
            return resolveServerKey(key.substring(7));
        }
        // 无前缀 fallback：优先尝试 player 键名（兼容 PAPI Player 扩展常见写法）
        return resolvePlayerKey(key, player);
    }

    private static String resolvePlayerKey(String key, Player player) {
        if (player == null) {
            return null;
        }
        switch (key) {
            case "name":
                return player.getName();
            case "displayname":
            case "display_name":
                return nullToEmpty(player.getDisplayName());
            case "uuid":
                return player.getUniqueId().toString();
            case "world":
                return player.getWorld().getName();
            case "x":
                return String.valueOf(player.getLocation().getBlockX());
            case "y":
                return String.valueOf(player.getLocation().getBlockY());
            case "z":
                return String.valueOf(player.getLocation().getBlockZ());
            case "health":
                return formatNumber(player.getHealth());
            case "max_health":
                return formatNumber(resolveMaxHealth(player));
            case "ping":
                return String.valueOf(Math.max(0, player.getPing()));
            case "gamemode":
                return player.getGameMode().name();
            case "scoreboardteam":
            case "scoreboard_team":
                Team team = player.getScoreboard().getEntryTeam(player.getName());
                return team != null ? team.getName() : "";
            default:
                return null;
        }
    }

    private static String resolveServerKey(String key) {
        switch (key) {
            case "online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players":
                return String.valueOf(Bukkit.getMaxPlayers());
            case "name":
                return Bukkit.getServer().getName();
            case "version":
                return Bukkit.getVersion();
            case "motd":
                return Bukkit.getServer().getMotd();
            case "tps":
            case "tps_1":
                return formatNumber(fetchTps()[0]);
            case "tps_5":
                return formatNumber(fetchTps()[1]);
            case "tps_15":
                return formatNumber(fetchTps()[2]);
            default:
                return null;
        }
    }

    private static double[] fetchTps() {
        long now = System.currentTimeMillis();
        double[] cached = cachedTps;
        long fetchedAt = lastTpsFetch;
        if (cached != null && now - fetchedAt < 1000L) {
            return cached;
        }
        try {
            Method getTPS = Bukkit.class.getMethod("getTPS");
            double[] fresh = (double[]) getTPS.invoke(null);
            cachedTps = fresh;
            lastTpsFetch = now;
            return fresh;
        } catch (Exception ignored) {
            return EMPTY_TPS;
        }
    }

    private static double resolveMaxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0D;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
