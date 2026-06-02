package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record OnlineRewardsCalendarView(
    int monthOffset,
    YearMonth month,
    LocalDate today,
    LocalDate selectedDate,
    Set<String> signedDates,
    List<OnlineRewardsRewardPreviewRow> selectedRewardRows,
    boolean selectedCanMakeup
) {
}
