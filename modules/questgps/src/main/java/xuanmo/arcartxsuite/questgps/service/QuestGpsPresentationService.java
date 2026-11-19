package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahCategoryResolver;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahMetadataReader;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahRewardReader;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 任务描述符收集与展示字段组装。
 */
public final class QuestGpsPresentationService {

    private final QuestGpsModuleConfiguration configuration;
    private final ChemdahCategoryResolver categoryResolver;
    private final ChemdahMetadataReader metadataReader;
    private final ChemdahRewardReader rewardReader;
    private final QuestGpsNavigationService navigationService;

    public QuestGpsPresentationService(
        QuestGpsModuleConfiguration configuration,
        ChemdahCategoryResolver categoryResolver,
        ChemdahMetadataReader metadataReader,
        ChemdahRewardReader rewardReader,
        QuestGpsNavigationService navigationService
    ) {
        this.configuration = configuration;
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

    MenuPage collectMenuPage(
        PlayerProfile profile,
        QuestGpsCategory viewCategory,
        QuestGpsPage viewPage,
        CategoryLockChecker categoryLockChecker
    ) {
        Map<QuestGpsPage, Integer> counts = new EnumMap<>(QuestGpsPage.class);
        for (QuestGpsPage page : QuestGpsPage.values()) {
            counts.put(page, 0);
        }
        if (viewCategory == null) {
            return new MenuPage(counts, List.of());
        }

        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = new ArrayList<>();
        Player questPlayer = profile.getPlayer();
        for (var definition : configuration.definitions().snapshot().quests().values()) {
            if (!definition.enabled()) {
                continue;
            }
            Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
            if (template == null) {
                continue;
            }
            QuestGpsCategory category = categoryResolver.resolveEffectiveCategory(template, definition);
            if (!viewCategory.equals(category)) {
                continue;
            }
            Quest activeQuest = profile.getQuestById(template.getId(), false);
            boolean active = activeQuest != null && !activeQuest.isCompleted();
            boolean completed = !active && profile.isQuestCompleted(template);
            QuestGpsPage page = active ? QuestGpsPage.ACTIVE : (completed ? QuestGpsPage.COMPLETED : QuestGpsPage.AVAILABLE);
            counts.put(page, counts.get(page) + 1);
            if (page != viewPage) {
                continue;
            }

            String summary = completed ? "任务已完成" : "等待接取";
            boolean trackable = false;
            if (active) {
                var tasks = template.getTaskMap().values();
                var taskIds = new ArrayList<String>(tasks.size());
                int completedTasks = 0;
                for (Task task : tasks) {
                    taskIds.add(task.getId());
                    if (task.isCompleted(profile)) {
                        completedTasks++;
                    }
                }
                summary = completedTasks + "/" + tasks.size() + " 目标完成";
                // Only existence matters for this list button. The selected detail still resolves in priority order.
                trackable = navigationService.questTrackable(questPlayer, template.getId(), taskIds);
            }

            descriptors.add(
                new QuestGpsSnapshotBuilder.QuestDescriptor(
                    template.getId(),
                    metadataReader.questDisplayName(template, definition),
                    summary,
                    trackable,
                    definition.sortOrder(),
                    () -> describeQuest(profile, template, definition, page, category, categoryLockChecker)
                )
            );
        }
        return new MenuPage(counts, descriptors);
    }

    record MenuPage(Map<QuestGpsPage, Integer> counts, List<QuestGpsSnapshotBuilder.QuestDescriptor> quests) {}

    private QuestGpsSnapshotBuilder.QuestDetail describeQuest(
        PlayerProfile profile,
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition,
        QuestGpsPage page,
        QuestGpsCategory category,
        CategoryLockChecker categoryLockChecker
    ) {
        Player player = profile.getPlayer();
        return new QuestGpsSnapshotBuilder.QuestDetail(
            safe(template.getPath()),
            metadataReader.questDescription(profile, template, definition),
            buildTaskDescriptors(profile, template, definition, page),
            rewardReader.resolve(configuration, definition, player),
            page == QuestGpsPage.AVAILABLE
                && hasCompletedRequiredMainline(profile, definition.requiredMainline())
                && !categoryLockChecker.isCategoryLocked(player, category),
            page == QuestGpsPage.ACTIVE && definition.allowAbandon()
        );
    }

    private List<QuestGpsSnapshotBuilder.TaskDescriptor> buildTaskDescriptors(
        PlayerProfile profile,
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition,
        QuestGpsPage page
    ) {
        List<Task> tasks = orderedTasks(template, definition);
        List<QuestGpsSnapshotBuilder.TaskDescriptor> descriptors = new ArrayList<>(tasks.size());
        Player questPlayer = profile.getPlayer();
        for (Task task : tasks) {
            boolean completed = isTaskCompleted(profile, template, task, page);
            List<String> descriptionLines = metadataReader.taskDescription(profile, task, definition);
            String descriptionText = descriptionLines.isEmpty() ? "" : String.join("\n", descriptionLines);
            descriptors.add(
                new QuestGpsSnapshotBuilder.TaskDescriptor(
                    task.getId(),
                    metadataReader.taskDisplayText(profile, template, task, definition),
                    descriptionText,
                    metadataReader.taskStatusText(profile, task, completed, page),
                    completed,
                    page == QuestGpsPage.ACTIVE && navigationService.taskTrackable(questPlayer, template.getId(), task.getId())
                )
            );
        }
        return List.copyOf(descriptors);
    }

    GuideProgress guideProgress(
        PlayerProfile profile,
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition
    ) {
        var tasks = template.getTaskMap().values();
        Comparator<Task> order = taskOrder(definition);
        int completed = 0;
        Task firstPending = null;
        for (Task task : tasks) {
            if (task.isCompleted(profile)) {
                completed++;
            } else if (firstPending == null || order.compare(task, firstPending) < 0) {
                firstPending = task;
            }
        }
        if (firstPending == null) {
            return new GuideProgress(completed, tasks.size(), tasks.isEmpty() ? "" : "全部完成", "");
        }
        return new GuideProgress(
            completed,
            tasks.size(),
            metadataReader.taskDisplayText(profile, template, firstPending, definition),
            String.join("\n", metadataReader.taskDescription(profile, firstPending, definition))
        );
    }

    record GuideProgress(int completed, int total, String taskText, String taskDescription) {}

    NavigationQuest navigationQuest(PlayerProfile profile, String questId) {
        QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(questId);
        if (definition == null || !definition.enabled()) {
            return null;
        }
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        if (template == null || effectiveCategory(template, definition) == null) {
            return null;
        }
        Quest quest = profile.getQuestById(template.getId(), false);
        return new NavigationQuest(template, definition, template.getId(),
            metadataReader.questDisplayName(template, definition), quest != null && !quest.isCompleted());
    }

    List<String> prioritizedTaskIds(PlayerProfile profile, NavigationQuest quest) {
        List<Task> tasks = orderedTasks(quest.template(), quest.definition());
        QuestGpsPage page = quest.active() ? QuestGpsPage.ACTIVE
            : profile.isQuestCompleted(quest.template()) ? QuestGpsPage.COMPLETED : QuestGpsPage.AVAILABLE;
        List<String> pending = new ArrayList<>(tasks.size());
        List<String> completed = new ArrayList<>();
        for (Task task : tasks) {
            if (isTaskCompleted(profile, quest.template(), task, page)) {
                completed.add(task.getId());
            } else {
                pending.add(task.getId());
            }
        }
        pending.addAll(completed);
        return List.copyOf(pending);
    }

    NavigationTask navigationTask(PlayerProfile profile, NavigationQuest quest, String taskId) {
        String selectedId = safe(taskId);
        if (selectedId.isEmpty()) {
            return null;
        }
        for (Task task : orderedTasks(quest.template(), quest.definition())) {
            if (task.getId().equalsIgnoreCase(selectedId)) {
                boolean trackAvailable = quest.active()
                    && navigationService.taskTrackable(profile.getPlayer(), quest.questId(), task.getId());
                String text = trackAvailable
                    ? metadataReader.taskDisplayText(profile, quest.template(), task, quest.definition()) : "";
                return new NavigationTask(task.getId(), text, trackAvailable);
            }
        }
        return null;
    }

    record NavigationQuest(
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition,
        String questId,
        String displayName,
        boolean active
    ) {}

    record NavigationTask(String taskId, String text, boolean trackAvailable) {}

    List<String> prioritizedTaskRows(List<QuestGpsSnapshotBuilder.TaskRow> tasks) {
        List<String> prioritized = new ArrayList<>(tasks.size());
        for (QuestGpsSnapshotBuilder.TaskRow task : tasks) {
            if (!task.completed()) {
                prioritized.add(task.taskId());
            }
        }
        for (QuestGpsSnapshotBuilder.TaskRow task : tasks) {
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

    private List<Task> orderedTasks(Template template, QuestGpsModuleConfiguration.QuestDefinition definition) {
        List<Task> tasks = new ArrayList<>(template.getTaskMap().values());
        tasks.sort(taskOrder(definition));
        return tasks;
    }

    private Comparator<Task> taskOrder(QuestGpsModuleConfiguration.QuestDefinition definition) {
        return Comparator.comparingInt((Task task) -> taskSortOrder(definition, task.getId()))
            .thenComparing(Task::getId, String.CASE_INSENSITIVE_ORDER);
    }

    private static boolean isTaskCompleted(PlayerProfile profile, Template template, Task task, QuestGpsPage page) {
        return switch (page) {
            case ACTIVE -> task.isCompleted(profile);
            case COMPLETED -> profile.getQuestTaskCompleteDate(template.getId(), task.getId()) > 0L || task.isCompleted(profile);
            case AVAILABLE -> false;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public interface CategoryLockChecker {
        boolean isCategoryLocked(Player player, QuestGpsCategory category);
    }
}
