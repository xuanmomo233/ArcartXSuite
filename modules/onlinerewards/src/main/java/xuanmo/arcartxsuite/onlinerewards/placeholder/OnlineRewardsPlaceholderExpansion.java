package xuanmo.arcartxsuite.onlinerewards.placeholder;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService.PlaceholderValues;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsTextFormats;

/**
 * 在线奖励 PlaceholderAPI 占位符扩展。
 * <p>
 * 属于 {@code onlinerewards} 模块，提供 {@code %axsonlinerewards_*%} 系列占位符，
 * 涵盖在线时长、签到状态、排行榜、签到历史与全服签到目标等查询。
 */
public final class OnlineRewardsPlaceholderExpansion extends PlaceholderExpansion {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JavaPlugin plugin;
    private final Supplier<OnlineRewardsService> serviceProvider;

    /**
     * 构造占位符扩展。
     *
     * @param plugin          宿主插件实例
     * @param serviceProvider 在线奖励服务提供者
     */
    public OnlineRewardsPlaceholderExpansion(JavaPlugin plugin, Supplier<OnlineRewardsService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsonlinerewards";
    }

    @Override
    public @NotNull String getAuthor() {
        return "墨墨墨";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /**
     * 解析占位符请求，根据参数返回在线时长、签到、排行榜或历史等文本。
     *
     * @param offlinePlayer 离线玩家，可为 {@code null}
     * @param params        占位符参数
     * @return 解析结果，无法识别时返回 {@code null}
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        OnlineRewardsService service = serviceProvider.get();
        if (service == null || service.isStopping()) {
            return "";
        }

        String normalized = params.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("top_")) {
            return resolveLeaderboardPlaceholder(service, normalized);
        }
        if (normalized.startsWith("signin_history_")) {
            return resolveSignInHistoryPlaceholder(service, offlinePlayer, normalized);
        }
        if ("server_signin_count".equals(normalized)) {
            return Integer.toString(service.todaySignInCount());
        }
        if ("server_signin_next_goal".equals(normalized)) {
            return Integer.toString(service.nextServerSignInGoalRequired());
        }

        Function<PlaceholderValues, String> display = switch (normalized) {
            case "daily_minutes" -> state -> Integer.toString(state.today());
            case "weekly_minutes" -> state -> Integer.toString(state.week());
            case "monthly_minutes" -> state -> Integer.toString(state.month());
            case "total_minutes" -> state -> Integer.toString(state.total());
            case "daily_time" -> state -> OnlineRewardsTextFormats.formatMinutes(state.today());
            case "weekly_time" -> state -> OnlineRewardsTextFormats.formatMinutes(state.week());
            case "monthly_time" -> state -> OnlineRewardsTextFormats.formatMinutes(state.month());
            case "total_time" -> state -> OnlineRewardsTextFormats.formatMinutes(state.total());
            case "offline_savings_minutes" -> state -> Integer.toString(state.savings());
            case "offline_savings_time" -> state -> OnlineRewardsTextFormats.formatMinutes(state.savings());
            case "signin_signed_today" -> state -> Boolean.toString(state.signedToday());
            case "signin_streak" -> state -> Integer.toString(state.streak());
            case "signin_total" -> state -> Integer.toString(state.signIns());
            default -> null;
        };
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) return "";
        return display == null ? null : display.apply(service.placeholderValues(
            offlinePlayer.getUniqueId(), "signin_signed_today".equals(normalized)));
    }

    private String resolveSignInHistoryPlaceholder(OnlineRewardsService service, OfflinePlayer offlinePlayer, String normalized) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "0";
        }
        String suffix = normalized.substring("signin_history_".length());
        if (suffix.endsWith("_count")) {
            String monthPart = suffix.substring(0, suffix.length() - "_count".length());
            YearMonth month = parseYearMonth(monthPart);
            if (month == null) {
                return "0";
            }
            return Integer.toString(service.countSignInDaysForMonth(offlinePlayer.getUniqueId(), month));
        }
        return null;
    }

    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value, MONTH_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String resolveLeaderboardPlaceholder(OnlineRewardsService service, String normalized) {
        String[] parts = normalized.split("_", 4);
        if (parts.length != 4) {
            return null;
        }
        OnlineRewardsLeaderboardScope scope = OnlineRewardsLeaderboardScope.parse(parts[1]);
        int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (scope == null || rank <= 0 || rank > 10) {
            return null;
        }

        OnlineRewardsLeaderboardEntry entry = service.leaderboardEntry(scope, rank);
        return switch (parts[3]) {
            case "name" -> entry == null ? "" : entry.playerName();
            case "minutes" -> Integer.toString(entry == null ? 0 : entry.minutes());
            case "time" -> OnlineRewardsTextFormats.formatMinutes(entry == null ? 0 : entry.minutes());
            default -> null;
        };
    }
}
