package xuanmo.arcartxsuite.onlinerewards.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.Set;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsDayOfMonthReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsHolidayReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsMilestoneReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPermissionBonusReward;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

/**
 * 在线奖励菜单数据包工厂。
 * <p>
 * 属于 {@code onlinerewards} 模块，将模块配置、玩家快照与日历视图组装为发送给客户端
 * 的菜单 payload，包含时长统计、进度、阶段奖励行、周期奖励行、签到奖励行与日历行等。
 */
public final class OnlineRewardsMenuPacketFactory {

    private OnlineRewardsMenuPacketFactory() {
    }

    /**
     * 构建包含日历视图的完整菜单 payload。
     *
     * @param configuration           模块配置
     * @param snapshot                玩家快照
     * @param calendarView            签到日历视图
     * @param serverSignInGoalCurrent 当前全服签到人数
     * @param serverSignInGoalNext    下一个全服签到目标所需人数
     * @param player                  玩家（用于解析图标，可为 null）
     * @param iconResolver            图标解析器（用于解析图标，可为 null）
     * @return 菜单 payload 映射
     */
    public static Map<String, Object> build(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        OnlineRewardsCalendarView calendarView,
        int serverSignInGoalCurrent,
        int serverSignInGoalNext,
        @Nullable Player player,
        @Nullable IconResolver iconResolver
    ) {
        OnlineRewardsPlayerState state = snapshot.state();
        OnlineRewardsProgressSnapshot progress = snapshot.progress();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.ui().packetId());
        payload.put("dailyTimeText", OnlineRewardsTextFormats.formatMinutes(state.onlineMinutes()));
        payload.put("weeklyTimeText", OnlineRewardsTextFormats.formatMinutes(state.weekMinutes()));
        payload.put("monthlyTimeText", OnlineRewardsTextFormats.formatMinutes(state.monthMinutes()));
        payload.put("totalTimeText", OnlineRewardsTextFormats.formatMinutes(state.totalMinutes()));
        payload.put("offlineSavingsMinutes", state.offlineSavingsMinutes());
        payload.put("offlineSavingsText", OnlineRewardsTextFormats.formatMinutes(state.offlineSavingsMinutes()));
        payload.put("progress", progress.progress());
        payload.put("progressPercent", Math.round(progress.progress() * 100.0F));
        payload.put("progressPercentText", Integer.toString(Math.round(progress.progress() * 100.0F)));
        payload.put("progressTitle", progress.title());
        payload.put("completed", progress.completed());
        payload.put("rewardStage", state.rewardStage());
        payload.put("rewardCount", configuration.rewards().size());
        payload.put("rewardStageText", state.rewardStage() + "/" + configuration.rewards().size());
        payload.put("signedToday", snapshot.signedToday());
        payload.put("signInStatusText", snapshot.signedToday() ? "今日已签到" : "今日未签到");
        payload.put("signInButtonText", snapshot.signedToday() ? "已签到" : "立即签到");
        payload.put("signInStreak", state.signInStreak());
        payload.put("signInTotal", state.signInTotal());
        payload.put("makeupCardCount", state.makeupCards());
        payload.put("calendarMonthText", calendarView.month().getYear() + "年" + calendarView.month().getMonthValue() + "月");
        payload.put("calendarMonthOffset", calendarView.monthOffset());
        payload.put("selectedDateText", calendarView.selectedDate().toString());
        payload.put("selectedCanMakeup", calendarView.selectedCanMakeup());
        payload.put("selectedAction", selectedAction(configuration, snapshot, calendarView));
        payload.put("selectedActionText", selectedActionText(configuration, snapshot, calendarView));
        payload.put("selectedActionEnabled", selectedActionEnabled(configuration, snapshot, calendarView));
        payload.put("rewardRows", buildRewardRows(configuration, state));
        // 阶段奖励图标（扁平字段 rewardIconJson0, rewardIconJson1, ...）
        appendRewardIconJson(payload, configuration, player, iconResolver);
        LinkedHashMap<String, Object> periodicRows = new LinkedHashMap<>();
        periodicRows.putAll(buildPeriodicRewardRows(configuration.weeklyRewards(), state.weekMinutes(), state.claimedWeeklyRewardIds(), "本周"));
        int weeklySize = periodicRows.size();
        Map<String, Object> monthlyRows = buildPeriodicRewardRows(configuration.monthlyRewards(), state.monthMinutes(), state.claimedMonthlyRewardIds(), "本月");
        int index = 0;
        for (Map.Entry<String, Object> entry : monthlyRows.entrySet()) {
            periodicRows.put(Integer.toString(weeklySize + index), entry.getValue());
            index++;
        }
        payload.put("periodicRewardRows", periodicRows);
        // 周期奖励图标（扁平字段 periodicIconJson0, periodicIconJson1, ...）
        appendPeriodicIconJson(payload, configuration, player, iconResolver);
        payload.put("signInRewardRows", buildSignInRewardRows(configuration));
        // 签到奖励图标（扁平字段 signinIconJson0, signinIconJson1, ...）
        appendSignInIconJson(payload, configuration, player, iconResolver);
        payload.put("serverSignInGoalEnabled", configuration.serverSignInGoal().enabled());
        payload.put("serverSignInGoalCurrent", serverSignInGoalCurrent);
        payload.put("serverSignInGoalNext", serverSignInGoalNext);
        payload.put("calendarCount", calendarView.month().lengthOfMonth());
        payload.put("calendarRows", buildCalendarRows(calendarView));
        payload.put("selectedRewardRows", buildSelectedRewardRows(calendarView));
        payload.put("maxRewardCount", configuration.rewards().size());
        payload.put("maxPeriodicCount", periodicRows.size());
        payload.put("maxSigninRewardCount", ((java.util.Map<?,?>) payload.getOrDefault("signInRewardRows", java.util.Map.of())).size());
        payload.put("maxSelectedRewardCount", ((java.util.Map<?,?>) payload.getOrDefault("selectedRewardRows", java.util.Map.of())).size());
        return payload;
    }

    /**
     * 以当月默认日历视图构建菜单 payload。
     *
     * @param configuration 模块配置
     * @param snapshot      玩家快照
     * @param clock         时钟（用于确定当前日期，支持时区配置）
     * @return 菜单 payload 映射
     */
    public static Map<String, Object> build(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        java.time.Clock clock
    ) {
        LocalDate today = LocalDate.now(clock);
        OnlineRewardsCalendarView calendarView = new OnlineRewardsCalendarView(
            0,
            java.time.YearMonth.from(today),
            today,
            today,
            Set.of(),
            List.of(),
            false
        );
        return build(configuration, snapshot, calendarView, 0, 0, null, null);
    }

    private static Map<String, Object> buildRewardRows(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerState state
    ) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        List<OnlineRewardDefinition> rewards = configuration.rewards();
        for (int index = 0; index < rewards.size(); index++) {
            OnlineRewardDefinition reward = rewards.get(index);
            boolean claimed = index < state.rewardStage();
            boolean current = index == state.rewardStage() && !claimed;
            boolean reached = state.onlineMinutes() >= reward.minutes();
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", Integer.toString(index + 1));
            row.put("index", index + 1);
            row.put("name", reward.name());
            row.put("thresholdText", OnlineRewardsTextFormats.formatMinutes(reward.minutes()));
            row.put("statusText", claimed ? "已领取" : current ? (reached ? "待发放" : "进行中") : "未达成");
            row.put("current", current);
            row.put("claimed", claimed);
            row.put("reached", reached);
            row.put("rewardText", reward.rewardText().isBlank()
                ? rewardSummary(reward.commands().size(), reward.mailPresetIds().size())
                : reward.rewardText());
            rows.put(Integer.toString(index), row);
        }
        return rows;
    }

    private static Map<String, Object> buildPeriodicRewardRows(
        List<OnlineRewardsPeriodicReward> rewards,
        int minutes,
        Set<String> claimedIds,
        String periodLabel
    ) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        for (int index = 0; index < rewards.size(); index++) {
            OnlineRewardsPeriodicReward reward = rewards.get(index);
            boolean claimed = claimedIds.contains(reward.id());
            boolean reached = minutes >= reward.minutes();
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", reward.id());
            row.put("index", index + 1);
            row.put("name", reward.name());
            row.put("periodLabel", periodLabel);
            row.put("thresholdText", OnlineRewardsTextFormats.formatMinutes(reward.minutes()));
            row.put("statusText", claimed ? "已领取" : reached ? "待发放" : "未达成");
            row.put("claimed", claimed);
            row.put("reached", reached);
            row.put("rewardText", reward.rewardText().isBlank()
                ? rewardSummary(reward.commands().size(), reward.mailPresetIds().size())
                : reward.rewardText());
            rows.put(Integer.toString(index), row);
        }
        return rows;
    }

    private static String selectedAction(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        OnlineRewardsCalendarView calendarView
    ) {
        LocalDate selectedDate = calendarView.selectedDate();
        if (isSelectedSigned(calendarView)) {
            return "none";
        }
        if (selectedDate.equals(calendarView.today())) {
            return snapshot.signedToday() ? "none" : "signin";
        }
        if (calendarView.selectedCanMakeup()) {
            return "makeup";
        }
        return "none";
    }

    private static String selectedActionText(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        OnlineRewardsCalendarView calendarView
    ) {
        LocalDate selectedDate = calendarView.selectedDate();
        if (isSelectedSigned(calendarView) || (selectedDate.equals(calendarView.today()) && snapshot.signedToday())) {
            return "已签到";
        }
        if (selectedDate.equals(calendarView.today())) {
            return "立即签到";
        }
        if (selectedDate.isAfter(calendarView.today())) {
            return "未到日期";
        }
        if (!java.time.YearMonth.from(selectedDate).equals(java.time.YearMonth.from(calendarView.today()))) {
            return "不可补签";
        }
        if (!configuration.signIn().makeup().enabled()) {
            return "不可补签";
        }
        if (snapshot.state().makeupCards() <= 0) {
            return "补签卡不足";
        }
        return calendarView.selectedCanMakeup() ? "点击补签" : "不可补签";
    }

    private static boolean selectedActionEnabled(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        OnlineRewardsCalendarView calendarView
    ) {
        String action = selectedAction(configuration, snapshot, calendarView);
        return "signin".equals(action) || "makeup".equals(action);
    }

    private static boolean isSelectedSigned(OnlineRewardsCalendarView calendarView) {
        return calendarView.signedDates().contains(calendarView.selectedDate().toString());
    }

    private static Map<String, Object> buildSignInRewardRows(OnlineRewardsModuleConfiguration configuration) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        int index = 0;
        addSignInRow(
            rows,
            index,
            "每日签到",
            "每次成功签到",
            configuration.signIn().baseRewardText(),
            configuration.signIn().baseCommands().size(),
            configuration.signIn().baseMailPresetIds().size()
        );
        index++;
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().streakRewards()) {
            addSignInRow(rows, index, "连续 " + reward.days() + " 天", "连续签到恰好 " + reward.days() + " 天", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
            index++;
        }
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().totalRewards()) {
            addSignInRow(rows, index, "累计 " + reward.days() + " 天", "累计签到恰好 " + reward.days() + " 天", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
            index++;
        }
        for (OnlineRewardsDayOfMonthReward reward : configuration.signIn().dayOfMonthRewards()) {
            addSignInRow(rows, index, "每月 " + reward.day() + " 日", "每月 " + reward.day() + " 日签到", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
            index++;
        }
        return rows;
    }

    private static Map<String, Object> buildCalendarRows(OnlineRewardsCalendarView calendarView) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        int days = calendarView.month().lengthOfMonth();
        for (int index = 0; index < days; index++) {
            int day = index + 1;
            LocalDate date = calendarView.month().atDay(day);
            String dateText = date.toString();
            boolean signed = calendarView.signedDates().contains(dateText);
            boolean today = date.equals(calendarView.today());
            boolean selected = date.equals(calendarView.selectedDate());
            boolean future = date.isAfter(calendarView.today());
            boolean expired = date.isBefore(calendarView.today()) && !signed;
            String status = signed ? "signed" : future ? "future" : expired ? "expired" : "open";
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", dateText);
            row.put("date", dateText);
            row.put("day", day);
            row.put("label", Integer.toString(day));
            row.put("signed", signed);
            row.put("today", today);
            row.put("selected", selected);
            row.put("future", future);
            row.put("expired", expired);
            row.put("status", status);
            row.put("statusText", signed ? "已签到" : future ? "未到" : expired ? "已过期" : "今日");
            row.put("canMakeup", date.equals(calendarView.selectedDate()) && calendarView.selectedCanMakeup());
            rows.put(Integer.toString(index), row);
        }
        return rows;
    }

    private static Map<String, Object> buildSelectedRewardRows(OnlineRewardsCalendarView calendarView) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        List<OnlineRewardsRewardPreviewRow> rewards = calendarView.selectedRewardRows();
        for (int index = 0; index < rewards.size(); index++) {
            OnlineRewardsRewardPreviewRow reward = rewards.get(index);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", reward.id());
            row.put("title", reward.title());
            row.put("trigger", reward.trigger());
            row.put("rewardText", reward.rewardText());
            rows.put(Integer.toString(index), row);
        }
        return rows;
    }

    private static void addSignInRow(
        LinkedHashMap<String, Object> rows,
        int index,
        String title,
        String trigger,
        String rewardText,
        int commandCount,
        int mailPresetCount
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", Integer.toString(index));
        row.put("title", title);
        row.put("trigger", trigger);
        row.put("rewardText", rewardText.isBlank() ? rewardSummary(commandCount, mailPresetCount) : rewardText);
        rows.put(Integer.toString(index), row);
    }

    private static String rewardSummary(int commandCount, int mailPresetCount) {
        if (commandCount <= 0 && mailPresetCount <= 0) {
            return "无额外奖励";
        }
        List<String> parts = new ArrayList<>();
        if (commandCount > 0) {
            parts.add("命令 " + commandCount);
        }
        if (mailPresetCount > 0) {
            parts.add("邮件 " + mailPresetCount);
        }
        return String.join(" / ", parts);
    }

    /**
     * 解析单个图标定义为 itemJson 字符串。player 或 iconResolver 为 null 时返回空串。
     */
    private static String resolveIconJson(@Nullable Player player, @Nullable IconResolver iconResolver, @Nullable IconDefinition icon) {
        if (player == null || iconResolver == null || icon == null) {
            return "";
        }
        try {
            return iconResolver.resolveItemJson(player, icon);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 为阶段奖励追加 iconJson 扁平字段（rewardIconJson0, rewardIconJson1, ...）。
     */
    private static void appendRewardIconJson(
        Map<String, Object> payload,
        OnlineRewardsModuleConfiguration configuration,
        @Nullable Player player,
        @Nullable IconResolver iconResolver
    ) {
        List<OnlineRewardDefinition> rewards = configuration.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            String json = resolveIconJson(player, iconResolver, rewards.get(i).icon());
            payload.put("rewardIconJson" + i, json);
        }
    }

    /**
     * 为周期奖励追加 iconJson 扁平字段（periodicIconJson0, periodicIconJson1, ...）。
     * 索引顺序：先 weekly 后 monthly，与 periodicRewardRows 一致。
     */
    private static void appendPeriodicIconJson(
        Map<String, Object> payload,
        OnlineRewardsModuleConfiguration configuration,
        @Nullable Player player,
        @Nullable IconResolver iconResolver
    ) {
        int idx = 0;
        for (OnlineRewardsPeriodicReward reward : configuration.weeklyRewards()) {
            payload.put("periodicIconJson" + idx, resolveIconJson(player, iconResolver, reward.icon()));
            idx++;
        }
        for (OnlineRewardsPeriodicReward reward : configuration.monthlyRewards()) {
            payload.put("periodicIconJson" + idx, resolveIconJson(player, iconResolver, reward.icon()));
            idx++;
        }
    }

    /**
     * 为签到奖励追加 iconJson 扁平字段（signinIconJson0, signinIconJson1, ...）。
     * 索引顺序：base → streak → total → dayOfMonth，与 signInRewardRows 一致。
     */
    private static void appendSignInIconJson(
        Map<String, Object> payload,
        OnlineRewardsModuleConfiguration configuration,
        @Nullable Player player,
        @Nullable IconResolver iconResolver
    ) {
        int idx = 0;
        payload.put("signinIconJson" + idx, resolveIconJson(player, iconResolver, configuration.signIn().baseIcon()));
        idx++;
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().streakRewards()) {
            payload.put("signinIconJson" + idx, resolveIconJson(player, iconResolver, reward.icon()));
            idx++;
        }
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().totalRewards()) {
            payload.put("signinIconJson" + idx, resolveIconJson(player, iconResolver, reward.icon()));
            idx++;
        }
        for (OnlineRewardsDayOfMonthReward reward : configuration.signIn().dayOfMonthRewards()) {
            payload.put("signinIconJson" + idx, resolveIconJson(player, iconResolver, reward.icon()));
            idx++;
        }
    }
}
