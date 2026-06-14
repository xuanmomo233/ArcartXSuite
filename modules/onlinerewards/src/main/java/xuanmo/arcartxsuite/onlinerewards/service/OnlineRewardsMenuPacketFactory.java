package xuanmo.arcartxsuite.onlinerewards.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.Set;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsDayOfMonthReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsMilestoneReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public final class OnlineRewardsMenuPacketFactory {

    private OnlineRewardsMenuPacketFactory() {
    }

    public static Map<String, Object> build(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot,
        OnlineRewardsCalendarView calendarView
    ) {
        OnlineRewardsPlayerState state = snapshot.state();
        OnlineRewardsProgressSnapshot progress = snapshot.progress();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.ui().packetId());
        payload.put("dailyTimeText", OnlineRewardsTextFormats.formatMinutes(state.onlineMinutes()));
        payload.put("weeklyTimeText", OnlineRewardsTextFormats.formatMinutes(state.weekMinutes()));
        payload.put("monthlyTimeText", OnlineRewardsTextFormats.formatMinutes(state.monthMinutes()));
        payload.put("totalTimeText", OnlineRewardsTextFormats.formatMinutes(state.totalMinutes()));
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
        payload.put("signInRewardRows", buildSignInRewardRows(configuration));
        payload.put("calendarCount", calendarView.month().lengthOfMonth());
        payload.put("calendarRows", buildCalendarRows(calendarView));
        payload.put("selectedRewardRows", buildSelectedRewardRows(calendarView));
        return payload;
    }

    public static Map<String, Object> build(
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsPlayerSnapshot snapshot
    ) {
        LocalDate today = LocalDate.now();
        OnlineRewardsCalendarView calendarView = new OnlineRewardsCalendarView(
            0,
            java.time.YearMonth.from(today),
            today,
            today,
            Set.of(),
            List.of(),
            false
        );
        return build(configuration, snapshot, calendarView);
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
        addSignInRow(
            rows,
            "base",
            "每日签到",
            "每次成功签到",
            configuration.signIn().baseRewardText(),
            configuration.signIn().baseCommands().size(),
            configuration.signIn().baseMailPresetIds().size()
        );
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().streakRewards()) {
            addSignInRow(rows, "streak-" + reward.days(), "连续 " + reward.days() + " 天", "连续签到恰好 " + reward.days() + " 天", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
        }
        for (OnlineRewardsMilestoneReward reward : configuration.signIn().totalRewards()) {
            addSignInRow(rows, "total-" + reward.days(), "累计 " + reward.days() + " 天", "累计签到恰好 " + reward.days() + " 天", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
        }
        for (OnlineRewardsDayOfMonthReward reward : configuration.signIn().dayOfMonthRewards()) {
            addSignInRow(rows, "month-" + reward.day(), "每月 " + reward.day() + " 日", "每月 " + reward.day() + " 日签到", reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size());
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
        String id,
        String title,
        String trigger,
        String rewardText,
        int commandCount,
        int mailPresetCount
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("trigger", trigger);
        row.put("rewardText", rewardText.isBlank() ? rewardSummary(commandCount, mailPresetCount) : rewardText);
        rows.put(id, row);
    }

    private static String rowKey(int index) {
        return Integer.toString(index + 1);
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
}
