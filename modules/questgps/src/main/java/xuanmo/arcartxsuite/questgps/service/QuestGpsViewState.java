package xuanmo.arcartxsuite.questgps.service;

import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

final class QuestGpsViewState {

    private QuestGpsCategory category = QuestGpsCategory.MAINLINE;
    private QuestGpsPage page = QuestGpsPage.AVAILABLE;
    private String selectedQuestId = "";

    QuestGpsCategory category() {
        return category;
    }

    void setCategory(QuestGpsCategory category) {
        this.category = category == null ? QuestGpsCategory.MAINLINE : category;
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
