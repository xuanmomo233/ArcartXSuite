package xuanmo.arcartxsuite.abyss.reward;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.RewardOffer;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardGameClaimsTest {
    @TempDir Path directory;

    @Test void reopeningCannotReplaceTheDeckAndStaleOrRepeatedClicksCannotSpendAnotherPick() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        var offer = offer(id, player, 0, "original");
        repository.insertRewardOffer(offer);
        repository.insertRewardOffer(offer(id, player, 0, "reroll"));
        var stored = repository.loadRewardOffer(id, player);
        assertEquals(offer.deck(), stored.deck());
        assertNull(repository.loadRewardOffer(id, UUID.randomUUID()));
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        var claims = new RewardGameClaims(repository, host);
        var chosen = claims.choose(stored, 0, 1, 1);
        assertEquals(2, chosen.picked());
        assertTrue(chosen.closed());
        assertEquals(2, claims.choose(chosen, 0, 0, 2).picked());
        assertEquals(2, claims.choose(chosen, 2, 1, 3).picked());
        verify(host, times(1)).savePendingBatchConfirmed(eq("reward-game:" + id + ":1"), anyList());
    }

    @Test void confirmedSelectionSurvivesRestartAndRetriesTheSameHostBatch() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        repository.insertRewardOffer(offer(id, player, 0, "original"));
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(false, true);
        var first = new RewardGameClaims(repository, host).choose(repository.loadRewardOffer(id, player), 0, 0, 1);
        assertEquals(1, first.picked());
        assertEquals(0, first.accepted());
        assertFalse(first.closed());
        var reopened = repository();
        var restored = new RewardGameClaims(reopened, host).process(reopened.pendingRewardOffers(player, 16).get(0), 2);
        assertEquals(1, restored.accepted());
        assertTrue(restored.closed());
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(host, times(2)).savePendingBatchConfirmed(eq("reward-game:" + id + ":0"), captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        assertTrue(reopened.pendingRewardOffers(player, 16).isEmpty());
    }

    @Test void deadlineAutoSelectsTheStoredFirstCardInsteadOfHonoringALateClick() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        repository.insertRewardOffer(offer(id, player, 100, "original"));
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        var row = repository.expiredRewardOffers(100, 8).get(0);
        var accepted = new RewardGameClaims(repository, host).choose(row, 0, 1, 100);
        assertEquals(1, accepted.picked());
        assertTrue(accepted.closed());
        verify(host).savePendingBatchConfirmed(eq("reward-game:" + id + ":0"), anyList());
        assertTrue(repository.expiredRewardOffers(101, 8).isEmpty());
    }

    private JdbcAbyssRepository repository() throws Exception {
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("games.db")));
        var repository = new JdbcAbyssRepository(ds, StorageDescriptor.sqlite("games.db"), directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        return repository;
    }

    private RewardOffer offer(UUID id, UUID player, long expires, String label) {
        var deck = RewardGameClaims.encode(List.of(
            new RewardResolver.GamePrize(label, List.of(PendingReward.currency(player, "abyss", "money", 1, label))),
            new RewardResolver.GamePrize(label + "2", List.of(PendingReward.currency(player, "abyss", "money", 2, label)))));
        return new RewardOffer(id, player, "flip", "Test", deck, 1, expires, 0, 0, false);
    }
}
