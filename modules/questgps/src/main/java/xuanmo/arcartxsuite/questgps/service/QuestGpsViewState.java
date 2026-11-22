package xuanmo.arcartxsuite.questgps.service;

import java.util.Map;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

/**
 * 任务导航 UI 视图状态（包私有），记录玩家当前选中的分类、页码与任务。
 */
final class QuestGpsViewState {

    private QuestGpsCategory category;
    private QuestGpsPage page = QuestGpsPage.AVAILABLE;
    private String selectedQuestId = "";
    private Map<String, Object> lastMenu;
    private Map<String, Object> lastGuide;
    private boolean menuTokenConsumed;

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

    boolean hasMenuToken() { return lastMenu != null && !menuTokenConsumed; }

    boolean menuTokenConsumed() { return menuTokenConsumed; }

    void consumeMenuToken() { menuTokenConsumed = true; }

    boolean sameMenu(Map<String, Object> payload) { return hasMenuToken() && lastMenu.equals(payload); }

    void sentMenu(Map<String, Object> payload) {
        lastMenu = payload;
        menuTokenConsumed = false;
    }

    void invalidateMenu() { lastMenu = null; }

    boolean sameGuide(Map<String, Object> payload) { return payload.equals(lastGuide); }

    void sentGuide(Map<String, Object> payload) { lastGuide = payload; }

    void invalidateGuide() { lastGuide = null; }
}
