package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * 签到日历视图数据。
 * <p>
 * 属于 {@code onlinerewards} 模块，描述玩家在菜单中查看的某月签到日历：
 * 当前展示月份偏移、已签到日期集合、选中日期及其奖励预览行与是否可补签。
 *
 * @param monthOffset        相对当月的偏移量
 * @param month              展示的年月
 * @param today              今日日期
 * @param selectedDate       当前选中的日期
 * @param signedDates        已签到日期集合
 * @param selectedRewardRows 选中日期的奖励预览行列表
 * @param selectedCanMakeup  选中日期是否可补签
 */
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
