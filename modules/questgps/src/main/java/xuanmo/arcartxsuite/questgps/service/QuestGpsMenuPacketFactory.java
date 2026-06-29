package xuanmo.arcartxsuite.questgps.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

public final class QuestGpsMenuPacketFactory {

    private QuestGpsMenuPacketFactory() {
    }

    public static Map<String, Object> build(
        QuestGpsModuleConfiguration configuration,
        QuestGpsSnapshotBuilder.BuildResult snapshot,
        boolean navigationReady
    ) {
        Map<String, Object> categories = buildCategoriesPacket(configuration, snapshot.category());
        Map<String, Object> pages = buildPagesPacket(snapshot);
        Map<String, Object> quests = buildQuestsPacket(snapshot);
        Map<String, Object> tasks = buildTasksPacket(snapshot.detail());
        Map<String, Object> rewards = buildRewardsPacket(snapshot.detail());

        QuestGpsSnapshotBuilder.DetailSnapshot detail = snapshot.detail();

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", configuration.client().packetId());
        packet.put("categories", categories);
        packet.put("pages", pages);
        packet.put("quests", quests);
        packet.put("tasks", tasks);
        packet.put("rewards", rewards);
        packet.put("categoryId", snapshot.category() == null ? "" : snapshot.category().id());
        packet.put("pageId", snapshot.page().id());
        packet.put("categoryName", snapshot.category() == null ? "" : snapshot.category().displayName());
        packet.put("pageName", snapshot.page().displayName());
        packet.put("availableCount", snapshot.counts().getOrDefault(QuestGpsPage.AVAILABLE, 0));
        packet.put("activeCount", snapshot.counts().getOrDefault(QuestGpsPage.ACTIVE, 0));
        packet.put("completedCount", snapshot.counts().getOrDefault(QuestGpsPage.COMPLETED, 0));
        packet.put("questCount", snapshot.questRows().size());
        packet.put("navigationReady", navigationReady);
        packet.put("selected_quest_id", detail.questId());
        packet.put("selectedQuestId", detail.questId());
        packet.put("selectedQuestName", detail.displayName());
        packet.put("selectedQuestState", detail.stateText());
        packet.put("selectedQuestPath", detail.path());
        packet.put("selectedQuestDescriptionText", String.join("\n", detail.descriptionLines()));
        packet.put("trackSummary", detail.trackingText());
        packet.put("canAccept", detail.canAccept());
        packet.put("canAbandon", detail.canAbandon());
        packet.put("canTrackQuest", detail.canTrackQuest());
        packet.put("canTrackTask", detail.canTrackTask());
        packet.put("canClearTrack", detail.canClearTrack());
        packet.put("questTracked", detail.questTracked());
        packet.put("taskCount", detail.taskRows().size());
        packet.put("rewardCount", detail.rewardRows().size());
        return packet;
    }

    private static Map<String, Object> buildCategoriesPacket(
        QuestGpsModuleConfiguration configuration,
        QuestGpsCategory selectedCategory
    ) {
        Map<String, Object> categories = new LinkedHashMap<>();
        for (QuestGpsCategory category : QuestGpsCategory.sorted(configuration.categoryRegistry())) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", category.id());
            entry.put("name", category.displayName());
            entry.put("sort_order", category.sortOrder());
            entry.put("selected", category.equals(selectedCategory));
            categories.put(category.id(), entry);
        }
        return categories;
    }

    private static Map<String, Object> buildPagesPacket(QuestGpsSnapshotBuilder.BuildResult snapshot) {
        Map<String, Object> pages = new LinkedHashMap<>();
        for (QuestGpsPage page : QuestGpsPage.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", page.id());
            entry.put("name", page.displayName());
            entry.put("count", snapshot.counts().getOrDefault(page, 0));
            entry.put("selected", page.equals(snapshot.page()));
            pages.put(page.id(), entry);
        }
        return pages;
    }

    private static Map<String, Object> buildQuestsPacket(QuestGpsSnapshotBuilder.BuildResult snapshot) {
        Map<String, Object> quests = new LinkedHashMap<>();
        for (QuestGpsSnapshotBuilder.ListRow row : snapshot.questRows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.questId());
            entry.put("name", row.displayName());
            entry.put("summary", row.summaryText());
            entry.put("state", row.stateText());
            entry.put("trackable", row.trackAvailable());
            entry.put("selected", row.selected());
            quests.put(row.questId(), entry);
        }
        return quests;
    }

    private static Map<String, Object> buildTasksPacket(QuestGpsSnapshotBuilder.DetailSnapshot detail) {
        Map<String, Object> tasks = new LinkedHashMap<>();
        for (QuestGpsSnapshotBuilder.TaskRow row : detail.taskRows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.taskId());
            entry.put("text", row.text());
            entry.put("description", row.descriptionText());
            entry.put("status", row.statusText());
            entry.put("completed", row.completed());
            entry.put("trackable", row.trackAvailable());
            entry.put("tracked", row.tracked());
            tasks.put(row.taskId(), entry);
        }
        return tasks;
    }

    private static Map<String, Object> buildRewardsPacket(QuestGpsSnapshotBuilder.DetailSnapshot detail) {
        Map<String, Object> rewards = new LinkedHashMap<>();
        for (QuestGpsSnapshotBuilder.RewardRow row : detail.rewardRows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.rewardId());
            entry.put("title", row.title());
            entry.put("description", row.description());
            entry.put("type", row.type());
            entry.put("amount", row.amount());
            entry.put("itemJson", row.itemJson());
            entry.put("material", row.materialId());
            rewards.put(row.rewardId(), entry);
        }
        return rewards;
    }
}
