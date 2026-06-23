package xuanmo.arcartxsuite.onlinerewards.placeholder;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsPlayerSnapshot;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsTextFormats;

public final class OnlineRewardsPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<OnlineRewardsService> serviceProvider;

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

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        OnlineRewardsService service = serviceProvider.get();
        if (service == null) {
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

        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "";
        }

        OnlineRewardsPlayerSnapshot snapshot = service.loadSnapshot(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        return switch (normalized) {
            case "daily_minutes" -> Integer.toString(snapshot.state().onlineMinutes());
            case "weekly_minutes" -> Integer.toString(snapshot.state().weekMinutes());
            case "monthly_minutes" -> Integer.toString(snapshot.state().monthMinutes());
            case "total_minutes" -> Integer.toString(snapshot.state().totalMinutes());
            case "daily_time" -> OnlineRewardsTextFormats.formatMinutes(snapshot.state().onlineMinutes());
            case "weekly_time" -> OnlineRewardsTextFormats.formatMinutes(snapshot.state().weekMinutes());
            case "monthly_time" -> OnlineRewardsTextFormats.formatMinutes(snapshot.state().monthMinutes());
            case "total_time" -> OnlineRewardsTextFormats.formatMinutes(snapshot.state().totalMinutes());
            case "offline_savings_minutes" -> Integer.toString(snapshot.state().offlineSavingsMinutes());
            case "offline_savings_time" -> OnlineRewardsTextFormats.formatMinutes(snapshot.state().offlineSavingsMinutes());
            case "signin_signed_today" -> Boolean.toString(snapshot.signedToday());
            case "signin_streak" -> Integer.toString(snapshot.state().signInStreak());
            case "signin_total" -> Integer.toString(snapshot.state().signInTotal());
            default -> null;
        };
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
            return YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM"));
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
