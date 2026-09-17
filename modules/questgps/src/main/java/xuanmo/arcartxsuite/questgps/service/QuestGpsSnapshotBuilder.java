package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

/**
 * 任务导航快照构造器，按分类与页码对任务列表分桶、排序并生成分页快照。
 * <p>
 * 输出 {@link BuildResult} 供 UI 数据包发送。
 */
public final class QuestGpsSnapshotBuilder {

    public BuildResult build(
        Collection<QuestDescriptor> descriptors,
        QuestGpsCategory requestedCategory,
        QuestGpsPage requestedPage,
        String preferredQuestId,
        Collection<QuestGpsNavigationService.TrackingState> trackingStates
    ) {
        Objects.requireNonNull(descriptors, "descriptors");
        QuestGpsCategory category = requestedCategory;
        QuestGpsPage page = requestedPage == null ? QuestGpsPage.AVAILABLE : requestedPage;
        Collection<QuestGpsNavigationService.TrackingState> safeTracking = trackingStates == null
            ? List.of()
            : trackingStates;

        Map<QuestGpsPage, List<QuestDescriptor>> buckets = new HashMap<>();
        Map<QuestGpsPage, Integer> counts = new HashMap<>();
        for (QuestGpsPage value : QuestGpsPage.values()) {
            buckets.put(value, new ArrayList<>());
            counts.put(value, 0);
        }

        for (QuestDescriptor descriptor : descriptors) {
            if (category == null || descriptor.category() == null || !descriptor.category().equals(category)) {
                continue;
            }
            buckets.get(descriptor.page()).add(descriptor);
            counts.put(descriptor.page(), counts.get(descriptor.page()) + 1);
        }
        for (List<QuestDescriptor> bucket : buckets.values()) {
            bucket.sort(Comparator
                .comparingInt(QuestDescriptor::sortOrder)
                .thenComparing(QuestDescriptor::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(QuestDescriptor::questId, String.CASE_INSENSITIVE_ORDER));
        }

        List<QuestDescriptor> currentPage = List.copyOf(buckets.get(page));
        String selectedQuestId = chooseSelectedQuestId(currentPage, preferredQuestId);
        QuestDescriptor selectedDescriptor = findById(currentPage, selectedQuestId);

        List<ListRow> rows = new ArrayList<>(currentPage.size());
        for (QuestDescriptor descriptor : currentPage) {
            rows.add(
                new ListRow(
                    descriptor.questId(),
                    descriptor.displayName(),
                    descriptor.summaryText(),
                    descriptor.stateText(),
                    descriptor.questTrackAvailable(),
                    descriptor.questId().equalsIgnoreCase(selectedQuestId)
                )
            );
        }

        DetailSnapshot detail = selectedDescriptor == null
            ? DetailSnapshot.empty(safeTracking)
            : buildDetail(selectedDescriptor, safeTracking);
        return new BuildResult(category, page, Map.copyOf(counts), List.copyOf(rows), detail);
    }

    private DetailSnapshot buildDetail(QuestDescriptor descriptor, Collection<QuestGpsNavigationService.TrackingState> trackingStates) {
        List<TaskRow> taskRows = new ArrayList<>(descriptor.tasks().size());
        for (TaskDescriptor task : descriptor.tasks()) {
            boolean taskTracked = false;
            for (QuestGpsNavigationService.TrackingState state : trackingStates) {
                if (state.matchesTask(descriptor.questId(), task.taskId())) {
                    taskTracked = true;
                    break;
                }
            }
            taskRows.add(
                new TaskRow(
                    task.taskId(),
                    task.text(),
                    task.descriptionText(),
                    task.statusText(),
                    task.completed(),
                    task.trackAvailable(),
                    taskTracked
                )
            );
        }

        List<RewardRow> rewardRows = new ArrayList<>(descriptor.rewards().size());
        for (QuestGpsRewardPreviewResolver.ResolvedRewardPreview reward : descriptor.rewards()) {
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

        boolean questTracked = false;
        for (QuestGpsNavigationService.TrackingState state : trackingStates) {
            if (state.active() && state.questId().equalsIgnoreCase(descriptor.questId())) {
                questTracked = true;
                break;
            }
        }

        return new DetailSnapshot(
            descriptor.questId(),
            descriptor.displayName(),
            descriptor.stateText(),
            descriptor.path(),
            descriptor.descriptionLines(),
            List.copyOf(taskRows),
            List.copyOf(rewardRows),
            descriptor.canAccept(),
            descriptor.canAbandon(),
            descriptor.questTrackAvailable(),
            descriptor.page() == QuestGpsPage.ACTIVE,
            !trackingStates.isEmpty(),
            questTracked,
            buildTrackingText(trackingStates)
        );
    }

    static String chooseSelectedQuestId(List<QuestDescriptor> descriptors, String preferredQuestId) {
        if (descriptors.isEmpty()) {
            return "";
        }
        if (preferredQuestId != null && !preferredQuestId.isBlank()) {
            for (QuestDescriptor descriptor : descriptors) {
                if (descriptor.questId().equalsIgnoreCase(preferredQuestId.trim())) {
                    return descriptor.questId();
                }
            }
        }
        return descriptors.get(0).questId();
    }

    private static QuestDescriptor findById(List<QuestDescriptor> descriptors, String questId) {
        if (questId == null || questId.isBlank()) {
            return null;
        }
        for (QuestDescriptor descriptor : descriptors) {
            if (descriptor.questId().equalsIgnoreCase(questId.trim())) {
                return descriptor;
            }
        }
        return null;
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

    public record QuestDescriptor(
        String questId,
        QuestGpsCategory category,
        QuestGpsPage page,
        String displayName,
        String summaryText,
        String stateText,
        String path,
        List<String> descriptionLines,
        List<TaskDescriptor> tasks,
        List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> rewards,
        boolean canAccept,
        boolean canAbandon,
        boolean questTrackAvailable,
        int sortOrder
    ) {
    }

    public record TaskDescriptor(
        String taskId,
        String text,
        String descriptionText,
        String statusText,
        boolean completed,
        boolean trackAvailable,
        int sortOrder
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
