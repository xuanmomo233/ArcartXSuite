package xuanmo.arcartxsuite.questgps.service;

import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

/**
 * 任务导航 UI 视图状态（包私有），记录玩家当前选中的分类、页码与任务。
 */
final class QuestGpsViewState {

    private QuestGpsCategory category;
    private QuestGpsPage page = QuestGpsPage.AVAILABLE;
    private String selectedQuestId = "";

    QuestGpsCategory category() {
        return category;
    }

    void setCategory(QuestGpsCategory category) {
        this.category = category;
    }

    QuestGpsPage page() {
        return page;
    }

    void setPage(QuestGpsPage page) {
        this.page = page == null ? QuestGpsPage.AVAILABLE : page;
    }

    String selectedQuestId() {
        return selectedQuestId;
    }

    void setSelectedQuestId(String selectedQuestId) {
        this.selectedQuestId = selectedQuestId == null ? "" : selectedQuestId.trim();
    }
}
