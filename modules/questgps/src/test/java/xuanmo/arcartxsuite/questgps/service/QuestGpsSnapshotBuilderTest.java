package xuanmo.arcartxsuite.questgps.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

import static org.junit.jupiter.api.Assertions.*;

class QuestGpsSnapshotBuilderTest {
    private final QuestGpsCategory category = new QuestGpsCategory("side", "支线", 0);
    private final QuestGpsSnapshotBuilder builder = new QuestGpsSnapshotBuilder();

    @Test
    void resolvesOnlyTheSelectedDetailAndRetainsThePageCountsAndOrder() {
        AtomicInteger detailReads = new AtomicInteger();
        var selected = new QuestGpsSnapshotBuilder.QuestDescriptor("beta", "Beta", "0/1 目标完成", true, 0, () -> {
            detailReads.incrementAndGet();
            return new QuestGpsSnapshotBuilder.QuestDetail(
                "beta.yml",
                List.of("任务描述"),
                List.of(new QuestGpsSnapshotBuilder.TaskDescriptor("target", "进度", "目标描述", "进行中", false, true),
                    new QuestGpsSnapshotBuilder.TaskDescriptor("other", "其他目标", "", "进行中", false, true)),
                List.of(new QuestGpsRewardPreviewResolver.ResolvedRewardPreview(
                    "reward", "material", "奖励", "", "{}", "STONE", "石头", 2)),
                false, true
            );
        });
        var counts = Map.of(QuestGpsPage.AVAILABLE, 6, QuestGpsPage.ACTIVE, 3, QuestGpsPage.COMPLETED, 8);
        var page = new QuestGpsPresentationService.MenuPage(counts, List.of(
            hiddenDetail("later", "Aardvark", 1), selected, hiddenDetail("alpha", "Alpha", 0)));
        var mode = QuestGpsNavigationService.TrackingMode.TASK;
        var tracking = List.of(new QuestGpsNavigationService.TrackingState(true, mode, "BETA", "TARGET", "a", "目标", "Beta"),
            new QuestGpsNavigationService.TrackingState(true, mode, "alpha", "other", "b", "其他任务", "Alpha"),
            new QuestGpsNavigationService.TrackingState(false, mode, "beta", "other", "c", "已停止", "Beta"));

        var snapshot = builder.build(page, category, QuestGpsPage.ACTIVE, " BETA ", tracking);

        assertEquals(1, detailReads.get());
        assertEquals(counts, snapshot.counts());
        assertEquals(List.of("alpha", "beta", "later"),
            snapshot.questRows().stream().map(QuestGpsSnapshotBuilder.ListRow::questId).toList());
        assertEquals(List.of("beta"), snapshot.questRows().stream().filter(QuestGpsSnapshotBuilder.ListRow::selected)
            .map(QuestGpsSnapshotBuilder.ListRow::questId).toList());
        assertEquals("beta", snapshot.detail().questId());
        assertTrue(snapshot.detail().canAbandon());
        assertTrue(snapshot.detail().taskRows().get(0).tracked());
        assertFalse(snapshot.detail().taskRows().get(1).tracked());
        assertEquals("目标描述", snapshot.detail().taskRows().get(0).descriptionText());
        assertEquals(2, snapshot.detail().rewardRows().get(0).amount());
    }

    @Test
    void fallsBackToTheFirstSortedQuestOrShowsAnEmptyDetail() {
        var first = new QuestGpsSnapshotBuilder.QuestDescriptor("first", "First", "等待接取", false, 0,
            () -> new QuestGpsSnapshotBuilder.QuestDetail("", List.of(), List.of(), List.of(), true, false));
        var page = new QuestGpsPresentationService.MenuPage(Map.of(QuestGpsPage.AVAILABLE, 2),
            List.of(hiddenDetail("later", "Later", 0), first));

        var snapshot = builder.build(page, category, QuestGpsPage.AVAILABLE, "previous-page", List.of());
        assertEquals("first", snapshot.detail().questId());
        assertTrue(snapshot.detail().canAccept());

        var empty = builder.build(new QuestGpsPresentationService.MenuPage(Map.of(), List.of()),
            category, QuestGpsPage.COMPLETED, "first", List.of());
        assertTrue(empty.questRows().isEmpty());
        assertEquals("", empty.detail().questId());
        assertFalse(empty.detail().canAccept());
    }

    private static QuestGpsSnapshotBuilder.QuestDescriptor hiddenDetail(String id, String name, int order) {
        return new QuestGpsSnapshotBuilder.QuestDescriptor(id, name, "", false, order,
            () -> fail("详情不应加载: " + id));
    }
}
