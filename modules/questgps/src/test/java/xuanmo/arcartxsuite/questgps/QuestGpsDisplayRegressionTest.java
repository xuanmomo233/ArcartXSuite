package xuanmo.arcartxsuite.questgps;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahMetadataReader;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.service.QuestGpsMenuPacketFactory;
import xuanmo.arcartxsuite.questgps.service.QuestGpsSnapshotBuilder;

import static org.junit.jupiter.api.Assertions.*;

class QuestGpsDisplayRegressionTest {
    private final ChemdahMetadataReader reader = new ChemdahMetadataReader(Logger.getAnonymousLogger());

    @Test
    void completedQuestWithoutTaskHistoryDoesNotClaimTaskIsStillActiveOrCompleted() {
        assertEquals("任务已结束（无历史进度）",
            reader.taskStatusText(null, null, false, QuestGpsPage.COMPLETED));
    }

    @Test
    void recordedCompletionAndCurrentQuestStatesRemainDistinct() {
        assertEquals("已完成", reader.taskStatusText(null, null, true, QuestGpsPage.COMPLETED));
        assertEquals("已完成", reader.taskStatusText(null, null, true, QuestGpsPage.ACTIVE));
        assertEquals("进行中", reader.taskStatusText(null, null, false, QuestGpsPage.ACTIVE));
        assertEquals("未开始", reader.taskStatusText(null, null, false, QuestGpsPage.AVAILABLE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void menuNamesUseUiColorsWithoutChangingSourceNamesOrTaskFlags() {
        var configuration = QuestGpsModuleConfiguration.load(new YamlConfiguration(), Logger.getAnonymousLogger());
        var category = new QuestGpsCategory("side", "支线", 0);
        var detail = new QuestGpsSnapshotBuilder.DetailSnapshot(
            "quest", "&f§b讨伐魔物", "已完成", "sk_slayer", List.of(),
            List.of(new QuestGpsSnapshotBuilder.TaskRow("kills", "&#ffffff击败魔物", "",
                "任务已结束（无历史进度）", false, false, false)),
            List.of(), false, false, false, false, false, false, "当前未设置导航"
        );
        var snapshot = new QuestGpsSnapshotBuilder.BuildResult(category, QuestGpsPage.COMPLETED,
            Map.of(QuestGpsPage.COMPLETED, 1),
            List.of(new QuestGpsSnapshotBuilder.ListRow("quest", "&f§b讨伐魔物", "任务已完成", "已完成", false, true)), detail);

        Map<String, Object> packet = QuestGpsMenuPacketFactory.build(configuration, snapshot, true);
        assertEquals("讨伐魔物", packet.get("selectedQuestName"));
        var quests = (Map<String, Map<String, Object>>) packet.get("quests");
        assertEquals("讨伐魔物", quests.get("quest").get("name"));
        var tasks = (Map<String, Map<String, Object>>) packet.get("tasks");
        assertEquals("击败魔物", tasks.get("kills").get("text"));
        assertEquals("任务已结束（无历史进度）", tasks.get("kills").get("status"));
        assertEquals(false, tasks.get("kills").get("completed"));
        assertEquals(false, tasks.get("kills").get("trackable"));
        assertEquals("&f§b讨伐魔物", detail.displayName());
    }
}
