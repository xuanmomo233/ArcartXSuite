package xuanmo.arcartxsuite.battlepass.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward.RewardTier;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository.RewardRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BattlePassRewardDispatcherTest {
    private final BattlePassRepository repository = mock(BattlePassRepository.class);
    private final PendingRewardService pending = mock(PendingRewardService.class);
    private final BattlePassRewardDispatcher dispatcher = new BattlePassRewardDispatcher(repository, pending);
    private final UUID player = UUID.randomUUID();
    private final UUID receiptStore = UUID.randomUUID();
    private final BattlePassReward reward = new BattlePassReward(
        "reward", 1, RewardTier.FREE, "command", null, null,
        List.of("new-command"), null, 0, null, null, null, null);

    @Test
    void resumesStoredCustomerRewardsThroughTheirOriginalReceiptBackend() throws Exception {
        when(pending.rewardReceiptStoreId()).thenReturn(receiptStore);
        var batch = List.of(PendingReward.command(player, "battlepass", List.of("old-command"), "original reward"));
        when(repository.stageReward(eq(player), eq("season"), eq("reward"), anyString()))
            .thenReturn(wrappedRequest(batch));
        when(pending.savePendingBatchConfirmed("battlepass:stored-request", batch)).thenReturn(true);
        when(repository.completeReward(player, "season", "reward", "stored-request")).thenReturn(true);

        assertEquals(BattlePassRewardDispatcher.Result.ACCEPTED, dispatcher.accept(player, "season", "reward", reward));
        verify(pending).savePendingBatchConfirmed("battlepass:stored-request", batch);
        verify(repository).completeReward(player, "season", "reward", "stored-request");
    }

    @Test
    void keepsStoredCustomerRewardsPendingIfTheReceiptBackendDoesNotMatch() throws Exception {
        when(pending.rewardReceiptStoreId()).thenReturn(UUID.randomUUID());
        var batch = List.of(PendingReward.command(player, "battlepass", List.of("old-command"), "original reward"));
        when(repository.stageReward(eq(player), eq("season"), eq("reward"), anyString()))
            .thenReturn(wrappedRequest(batch));

        assertEquals(BattlePassRewardDispatcher.Result.RETRY, dispatcher.accept(player, "season", "reward", reward));
        verify(pending, never()).savePendingBatchConfirmed(anyString(), anyList());
        verify(repository, never()).completeReward(any(), anyString(), anyString(), anyString());
    }

    @Test
    void onlyCustomerSharedProgressProducesNewStoreBoundRewardPayloads() throws Exception {
        when(repository.sharedProgress()).thenReturn(true);
        when(pending.rewardReceiptStoreId()).thenReturn(receiptStore);
        var payload = ArgumentCaptor.forClass(String.class);

        assertEquals(BattlePassRewardDispatcher.Result.RETRY, dispatcher.accept(player, "season", "reward", reward));
        verify(repository).stageReward(eq(player), eq("season"), eq("reward"), payload.capture());
        var staged = JsonParser.parseString(payload.getValue());
        if (BuildEdition.CROSS_SERVER_INCLUDED) {
            assertEquals(receiptStore.toString(), staged.getAsJsonObject().get("receiptStoreId").getAsString());
        } else {
            assertTrue(staged.isJsonArray());
            verify(pending, never()).rewardReceiptStoreId();
        }
    }

    private RewardRequest wrappedRequest(List<PendingReward> rewards) {
        String payload = new Gson().toJson(Map.of("receiptStoreId", receiptStore, "rewards", rewards));
        return new RewardRequest("stored-request", payload);
    }
}
