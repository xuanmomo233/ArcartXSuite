package xuanmo.arcartxsuite.onlinerewards.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

public record OnlineRewardsSignInHistory(String latestDate, int streak) {
    public static OnlineRewardsSignInHistory summarize(Set<String> dates) {
        LocalDate latest = null;
        for (String date : dates) {
            LocalDate parsed;
            try { parsed = LocalDate.parse(date); }
            catch (DateTimeParseException invalid) { continue; }
            if (latest == null || parsed.isAfter(latest)) latest = parsed;
        }
        return latest == null ? new OnlineRewardsSignInHistory("", 0)
            : new OnlineRewardsSignInHistory(latest.toString(), streakEndingAt(dates, latest));
    }

    public static int streakEndingAt(Set<String> dates, LocalDate end) {
        int streak = 0;
        while (end != null && dates.contains(end.toString())) {
            streak++;
            end = end.minusDays(1);
        }
        return streak;
    }
}
