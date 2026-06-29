package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahCategoryResolver;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahMetadataReader;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahQuestDiscovery;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahRewardReader;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 任务描述符收集与展示字段组装。
 */
public final class QuestGpsPresentationService {

    private final QuestGpsModuleConfiguration configuration;
    private final ChemdahQuestDiscovery questDiscovery;
    private final ChemdahCategoryResolver categoryResolver;
    private final ChemdahMetadataReader metadataReader;
    private final ChemdahRewardReader rewardReader;
    private final QuestGpsNavigationService navigationService;

    public QuestGpsPresentationService(
        QuestGpsModuleConfiguration configuration,
        ChemdahQuestDiscovery questDiscovery,
        ChemdahCategoryResolver categoryResolver,
        ChemdahMetadataReader metadataReader,
        ChemdahRewardReader rewardReader,
        QuestGpsNavigationService navigationService
    ) {
        this.configuration = configuration;
        this.questDiscovery = questDiscovery;
        this.categoryResolver = categoryResolver;
        this.metadataReader = metadataReader;
        this.rewardReader = rewardReader;
        this.navigationService = navigationService;
    }

    public QuestGpsCategory effectiveCategory(
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition
    ) {
        return categoryResolver.resolveEffectiveCategory(template, definition);
    }

    public List<QuestGpsSnapshotBuilder.QuestDescriptor> collectDescriptors(
        PlayerProfile profile,
        CategoryLockChecker categoryLockChecker
    ) {
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = new ArrayList<>();
        Player questPlayer = profile.getPlayer();
        for (QuestGpsModuleConfiguration.QuestDefinition definition : questDiscovery.resolveDefinitions(configuration)) {
            Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
            if (template == null) {
                continue;
            }
            QuestGpsCategory category = categoryResolver.resolveEffectiveCategory(template, definition);
            if (category == null) {
                continue;
            }
            Quest activeQuest = profile.getQuestById(template.getId(), false);
            boolean active = activeQuest != null && !activeQuest.isCompleted();
            boolean completed = !active && profile.isQuestCompleted(template);
            QuestGpsPage page = active ? QuestGpsPage.ACTIVE : (completed ? QuestGpsPage.COMPLETED : QuestGpsPage.AVAILABLE);
            List<QuestGpsSnapshotBuilder.TaskDescriptor> taskDescriptors = buildTaskDescriptors(profile, template, definition, page);
            boolean canAccept = page == QuestGpsPage.AVAILABLE
                && hasCompletedRequiredMainline(profile, definition.requiredMainline())
                && !categoryLockChecker.isCategoryLocked(questPlayer, category);
            descriptors.add(
                new QuestGpsSnapshotBuilder.QuestDescriptor(
                    template.getId(),
                    category,
                    page,
                    metadataReader.questDisplayName(template, definition, configuration.presentation()),
                    buildQuestSummary(page, taskDescriptors),
                    page.displayName(),
                    safe(template.getPath()),
                    metadataReader.questDescription(template, definition, configuration.presentation()),
                    taskDescriptors,
                    rewardReader.resolve(configuration, definition, template),
                    canAccept,
                    page == QuestGpsPage.ACTIVE && definition.allowAbandon(),
                    page == QuestGpsPage.ACTIVE
                        && navigationService.hasQuestPoint(questPlayer, template.getId(), prioritizedTaskIds(taskDescriptors)),
                    definition.sortOrder()
                )
            );
        }
        return List.copyOf(descriptors);
    }

    public List<QuestGpsSnapshotBuilder.TaskDescriptor> buildTaskDescriptors(
        PlayerProfile profile,
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition,
        QuestGpsPage page
    ) {
        List<Task> tasks = new ArrayList<>(template.getTaskMap().values());
        tasks.sort(
            Comparator
                .comparingInt((Task task) -> taskSortOrder(definition, task.getId()))
                .thenComparing(Task::getId, String.CASE_INSENSITIVE_ORDER)
        );

        List<QuestGpsSnapshotBuilder.TaskDescriptor> descriptors = new ArrayList<>(tasks.size());
        Player questPlayer = profile.getPlayer();
        for (Task task : tasks) {
            boolean completed = switch (page) {
                case ACTIVE -> task.isCompleted(profile);
                case COMPLETED -> profile.getQuestTaskCompleteDate(template.getId(), task.getId()) > 0L || task.isCompleted(profile);
                case AVAILABLE -> false;
            };
            List<String> descriptionLines = metadataReader.taskDescription(task, definition, configuration.presentation());
            String descriptionText = descriptionLines.isEmpty() ? "" : String.join("\n", descriptionLines);
            descriptors.add(
                new QuestGpsSnapshotBuilder.TaskDescriptor(
                    task.getId(),
                    metadataReader.taskDisplayText(profile, template, task, definition, configuration.presentation()),
                    descriptionText,
                    metadataReader.taskStatusText(profile, task, completed, page == QuestGpsPage.AVAILABLE),
                    completed,
                    page == QuestGpsPage.ACTIVE && navigationService.hasTaskPoint(questPlayer, template.getId(), task.getId()),
                    taskSortOrder(definition, task.getId())
                )
            );
        }
        return List.copyOf(descriptors);
    }

    public QuestGpsSnapshotBuilder.QuestDescriptor findDescriptor(
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors,
        String questId
    ) {
        if (descriptors == null || questId == null || questId.isBlank()) {
            return null;
        }
        for (QuestGpsSnapshotBuilder.QuestDescriptor descriptor : descriptors) {
            if (descriptor.questId().equalsIgnoreCase(questId.trim())) {
                return descriptor;
            }
        }
        return null;
    }

    public QuestGpsSnapshotBuilder.TaskDescriptor findTask(
        List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks,
        String taskId
    ) {
        if (tasks == null || taskId == null || taskId.isBlank()) {
            return null;
        }
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.taskId().equalsIgnoreCase(taskId.trim())) {
                return task;
            }
        }
        return null;
    }

    public List<String> prioritizedTaskIds(List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<String> prioritized = new ArrayList<>(tasks.size());
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (!task.completed()) {
                prioritized.add(task.taskId());
            }
        }
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.completed()) {
                prioritized.add(task.taskId());
            }
        }
        return List.copyOf(prioritized);
    }

    public boolean hasCompletedRequiredMainline(PlayerProfile profile, List<String> questIds) {
        if (profile == null || questIds == null || questIds.isEmpty()) {
            return true;
        }
        for (String questId : questIds) {
            Template required = ChemdahAPI.INSTANCE.getQuestTemplate(questId);
            if (required == null || !profile.isQuestCompleted(required)) {
                return false;
            }
        }
        return true;
    }

    private int taskSortOrder(QuestGpsModuleConfiguration.QuestDefinition definition, String taskId) {
        QuestGpsModuleConfiguration.TaskDefinition taskDefinition = definition.task(taskId);
        return taskDefinition == null ? 0 : taskDefinition.sortOrder();
    }

    private String buildQuestSummary(QuestGpsPage page, List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks) {
        if (page == QuestGpsPage.COMPLETED) {
            return "任务已完成";
        }
        if (page == QuestGpsPage.AVAILABLE) {
            return "等待接取";
        }
        int completedCount = 0;
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.completed()) {
                completedCount++;
            }
        }
        return completedCount + "/" + tasks.size() + " 目标完成";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public interface CategoryLockChecker {
        boolean isCategoryLocked(Player player, QuestGpsCategory category);
    }
}
