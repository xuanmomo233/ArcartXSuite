package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

/**
 * 排序当前任务列表，生成选中任务的详情和菜单快照。
 * <p>
 * 输出 {@link BuildResult} 供 UI 数据包发送。
 */
public final class QuestGpsSnapshotBuilder {

    BuildResult build(
        QuestGpsPresentationService.MenuPage menuPage,
        QuestGpsCategory category,
        QuestGpsPage page,
        String preferredQuestId,
        Collection<QuestGpsNavigationService.TrackingState> trackingStates
    ) {
        Collection<QuestGpsNavigationService.TrackingState> safeTracking = trackingStates == null
            ? List.of()
            : trackingStates;

        List<QuestDescriptor> currentPage = new ArrayList<>(menuPage.quests());
        currentPage.sort(Comparator
            .comparingInt(QuestDescriptor::sortOrder)
            .thenComparing(QuestDescriptor::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(QuestDescriptor::questId, String.CASE_INSENSITIVE_ORDER));
        QuestDescriptor selected = selectQuest(currentPage, preferredQuestId);

        List<ListRow> rows = new ArrayList<>(currentPage.size());
        for (QuestDescriptor descriptor : currentPage) {
            rows.add(
                new ListRow(
                    descriptor.questId(),
                    descriptor.displayName(),
                    descriptor.summaryText(),
                    page.displayName(),
                    descriptor.questTrackAvailable(),
                    selected != null && descriptor.questId().equalsIgnoreCase(selected.questId())
                )
            );
        }

        DetailSnapshot detail = selected == null
            ? DetailSnapshot.empty(safeTracking)
            : buildDetail(selected, page, safeTracking);
        return new BuildResult(category, page, Map.copyOf(menuPage.counts()), List.copyOf(rows), detail);
    }

    private DetailSnapshot buildDetail(
        QuestDescriptor descriptor,
        QuestGpsPage page,
        Collection<QuestGpsNavigationService.TrackingState> trackingStates
    ) {
        QuestDetail detail = descriptor.detail().get();
        Set<String> trackedTasks = trackingStates.isEmpty() ? Set.of() : new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean questTracked = false;
        String questId = descriptor.questId();
        String taskQuestId = questId == null ? "" : questId.trim();
        for (var state : trackingStates) {
            if (!state.active()) continue;
            if (state.questId().equalsIgnoreCase(questId)) questTracked = true;
            if (state.mode() == QuestGpsNavigationService.TrackingMode.TASK && state.questId().equalsIgnoreCase(taskQuestId)) {
                trackedTasks.add(state.taskId());
            }
        }
        List<TaskRow> taskRows = new ArrayList<>(detail.tasks().size());
        for (TaskDescriptor task : detail.tasks()) {
            taskRows.add(
                new TaskRow(
                    task.taskId(),
                    task.text(),
                    task.descriptionText(),
                    task.statusText(),
                    task.completed(),
                    task.trackAvailable(),
                    trackedTasks.contains(task.taskId() == null ? "" : task.taskId().trim())
                )
            );
        }

        List<RewardRow> rewardRows = new ArrayList<>(detail.rewards().size());
        for (QuestGpsRewardPreviewResolver.ResolvedRewardPreview reward : detail.rewards()) {
            rewardRows.add(
                new RewardRow(
                    reward.rewardId(),
                    reward.title(),
                    reward.description(),
                    reward.type(),
                    reward.amount(),
                    reward.itemJson(),
                    reward.materialId()
                )
            );
        }

        return new DetailSnapshot(
            descriptor.questId(),
            descriptor.displayName(),
            page.displayName(),
            detail.path(),
            detail.descriptionLines(),
            List.copyOf(taskRows),
            List.copyOf(rewardRows),
            detail.canAccept(),
            detail.canAbandon(),
            descriptor.questTrackAvailable(),
            page == QuestGpsPage.ACTIVE,
            !trackingStates.isEmpty(),
            questTracked,
            buildTrackingText(trackingStates)
        );
    }

    private static QuestDescriptor selectQuest(List<QuestDescriptor> descriptors, String preferredQuestId) {
        if (descriptors.isEmpty()) {
            return null;
        }
        if (preferredQuestId != null && !preferredQuestId.isBlank()) {
            String preferredId = preferredQuestId.trim();
            for (QuestDescriptor descriptor : descriptors) {
                if (descriptor.questId().equalsIgnoreCase(preferredId)) {
                    return descriptor;
                }
            }
        }
        return descriptors.get(0);
    }

    private static String buildTrackingText(Collection<QuestGpsNavigationService.TrackingState> trackingStates) {
        if (trackingStates == null || trackingStates.isEmpty()) {
            return "当前未设置导航";
        }
        if (trackingStates.size() > 1) {
            return "正在追踪 " + trackingStates.size() + " 个目标";
        }
        QuestGpsNavigationService.TrackingState single = trackingStates.iterator().next();
        return single.taskId().isBlank()
            ? "当前追踪任务: " + single.label()
            : "当前追踪目标: " + single.label();
    }

    public record BuildResult(
        QuestGpsCategory category,
        QuestGpsPage page,
        Map<QuestGpsPage, Integer> counts,
        List<ListRow> questRows,
        DetailSnapshot detail
    ) {
    }

    record QuestDescriptor(
        String questId,
        String displayName,
        String summaryText,
        boolean questTrackAvailable,
        int sortOrder,
        Supplier<QuestDetail> detail
    ) {}

    record QuestDetail(
        String path,
        List<String> descriptionLines,
        List<TaskDescriptor> tasks,
        List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> rewards,
        boolean canAccept,
        boolean canAbandon
    ) {}

    record TaskDescriptor(
        String taskId,
        String text,
        String descriptionText,
        String statusText,
        boolean completed,
        boolean trackAvailable
    ) {
    }

    public record ListRow(
        String questId,
        String displayName,
        String summaryText,
        String stateText,
        boolean trackAvailable,
        boolean selected
    ) {
    }

    public record DetailSnapshot(
        String questId,
        String displayName,
        String stateText,
        String path,
        List<String> descriptionLines,
        List<TaskRow> taskRows,
        List<RewardRow> rewardRows,
        boolean canAccept,
        boolean canAbandon,
        boolean canTrackQuest,
        boolean canTrackTask,
        boolean canClearTrack,
        boolean questTracked,
        String trackingText
    ) {

        private static DetailSnapshot empty(Collection<QuestGpsNavigationService.TrackingState> trackingStates) {
            return new DetailSnapshot(
                "",
                "暂无任务",
                "",
                "",
                List.of("当前分类下没有可展示的任务。"),
                List.of(),
                List.of(),
                false,
                false,
                false,
                false,
                trackingStates != null && !trackingStates.isEmpty(),
                false,
                buildTrackingText(trackingStates)
            );
        }
    }

    public record TaskRow(
        String taskId,
        String text,
        String descriptionText,
        String statusText,
        boolean completed,
        boolean trackAvailable,
        boolean tracked
    ) {
    }

    public record RewardRow(
        String rewardId,
        String title,
        String description,
        String type,
        int amount,
        String itemJson,
        String materialId
    ) {
    }
}
