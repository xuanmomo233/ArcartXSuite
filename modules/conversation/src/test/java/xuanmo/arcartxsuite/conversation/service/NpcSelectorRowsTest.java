package xuanmo.arcartxsuite.conversation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.conversation.service.ConversationService.NpcCandidate;
import xuanmo.arcartxsuite.conversation.service.ConversationService.SelectorState;

class NpcSelectorRowsTest {
    @Test
    void newServerHandleWithTheSameRowDoesNotRequireAnotherPacket() {
        var state = new SelectorState();
        var first = new NpcCandidate("guide", "向导", 12, new Object());
        var replacement = new NpcCandidate("guide", "向导", 12, new Object());
        assertTrue(state.replaceCandidates(List.of(first), 100, 1));
        assertFalse(state.replaceCandidates(List.of(replacement), 101, 1));
        assertSame(replacement, state.selectedCandidate());
        assertTrue(state.replaceCandidates(List.of(replacement), 102, 2));
    }

    @Test
    void changedRowsRefreshAndKeepSelectionWhenTheOrderChanges() {
        var state = new SelectorState();
        var guide = new NpcCandidate("guide", "向导", 12, new Object());
        var merchant = new NpcCandidate("merchant", "商人", 16, new Object());
        state.replaceCandidates(List.of(guide, merchant), 100, 1);
        assertTrue(state.selectNpc("merchant"));

        var moved = new NpcCandidate("merchant", "商人", 9, merchant.conversationEntity());
        assertTrue(state.replaceCandidates(List.of(moved, guide), 101, 1));
        assertSame(moved, state.selectedCandidate());
        assertTrue(state.replaceCandidates(List.of(guide), 102, 1));
        assertSame(guide, state.selectedCandidate());
    }

    @Test
    void distancesUseThePublishedRowOrderAndRequireAnOpenMatchingView() {
        var state = new SelectorState();
        var guide = new NpcCandidate("guide", "向导", 12, new Object());
        var merchant = new NpcCandidate("merchant", "商人", 16, new Object());
        state.replaceCandidates(List.of(guide, merchant), 100, 1);
        assertFalse(state.canSendDistances());
        state.markSent();

        var movedGuide = new NpcCandidate("guide", "向导", 13, new Object());
        state.replaceCandidates(List.of(movedGuide, merchant), 101, 1);
        assertTrue(state.canSendDistances());
        assertEquals(Map.of("token", ConversationService.tokenFor(1), "distances", Map.of("0", "1.3m", "1", "1.6m")),
            state.distancePayload());
        assertSame(movedGuide, state.selectedCandidate());

        state.selectNpc("merchant");
        assertFalse(state.canSendDistances());
        state.markSent();
        state.replaceCandidates(List.of(merchant, movedGuide), 102, 1);
        assertFalse(state.canSendDistances());
        state.markSent();
        var renamed = new NpcCandidate("guide", "新向导", 13, movedGuide.conversationEntity());
        state.replaceCandidates(List.of(merchant, renamed), 103, 1);
        assertFalse(state.canSendDistances());
        state.markSent();
        state.replaceCandidates(List.of(merchant, renamed), 104, 2);
        assertFalse(state.canSendDistances());
        state.markSent();
        state.clearSent();
        assertFalse(state.canSendDistances());
    }
}
