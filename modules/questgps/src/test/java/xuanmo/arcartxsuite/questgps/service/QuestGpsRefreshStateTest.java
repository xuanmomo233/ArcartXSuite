package xuanmo.arcartxsuite.questgps.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestGpsRefreshStateTest {
    @Test
    void unchangedMenuDoesNotNeedSendingUntilTheCurrentTokenIsConsumed() {
        var state = new QuestGpsViewState();
        var initial = Map.<String, Object>of("page", "active");
        assertFalse(state.hasMenuToken());
        state.sentMenu(initial);
        assertTrue(state.sameMenu(Map.of("page", "active")));

        state.consumeMenuToken();
        assertFalse(state.hasMenuToken());
        assertFalse(state.sameMenu(initial));
        state.invalidateMenu();
        assertTrue(state.menuTokenConsumed());
        assertFalse(state.hasMenuToken());

        state.sentMenu(initial);
        assertFalse(state.menuTokenConsumed());
        assertTrue(state.hasMenuToken());
        assertFalse(state.sameMenu(Map.of("page", "completed")));
    }

    @Test
    void identicalGuideCanBeSkippedOnlyWhileItsPublicationIsRetained() {
        var state = new QuestGpsViewState();
        var guide = Map.<String, Object>of("quests", Map.of("0", Map.of("progressText", "1/3")));
        assertFalse(state.sameGuide(guide));
        state.sentGuide(guide);
        assertTrue(state.sameGuide(Map.of("quests", Map.of("0", Map.of("progressText", "1/3")))));
        assertFalse(state.sameGuide(Map.of("quests", Map.of("0", Map.of("progressText", "2/3")))));
        state.invalidateGuide();
        assertFalse(state.sameGuide(guide));
    }
}
