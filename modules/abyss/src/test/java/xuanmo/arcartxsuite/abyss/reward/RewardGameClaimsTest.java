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

    @Test void reservedModesAndRegistrationOwnershipAreEnforced() throws Exception {
        var field = org.bukkit.Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        var server = mock(org.bukkit.Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        var service = mock(xuanmo.arcartxsuite.abyss.service.AbyssService.class,
            withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(service.running()).thenReturn(true);
        try {
            field.set(null, server);
            var games = new RewardGames(service, null, null, null);
            xuanmo.arcartxsuite.api.capability.AbyssRewardGameAccess.Strategy strategy = (player, offer, pick) -> { };
            assertThrows(IllegalArgumentException.class, () -> games.register("wheel", false, strategy));
            var old = games.register("custom", false, strategy);
            old.close();
            try (var current = games.register("custom", false, strategy)) {
                old.close();
                assertThrows(IllegalStateException.class, () -> games.register("custom", false, strategy));
            }
        } finally {
            field.set(null, previous);
        }
    }

    @Test void hiddenStrategiesExposeOnlyRevealedLabels() {
        var row = new RewardOffer(UUID.randomUUID(), UUID.randomUUID(), "flip", "Test", "", 2, 0, 2, 2, false);
        var cards = RewardGames.cardViews(row, List.of("secret", "shown", "other"), false);
        assertNull(cards.get(0).label());
        assertEquals("shown", cards.get(1).label());
        assertTrue(cards.get(1).accepted());
        assertNull(cards.get(2).label());
        assertEquals("secret", RewardGames.cardViews(row, List.of("secret", "shown", "other"), true).get(0).label());
    }

    @Test void reopeningCannotReplaceTheDeckAndStaleOrRepeatedClicksCannotSpendAnotherPick() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        var original = offer(id, player, 0, "original");
        var offer = new RewardOffer(id, player, original.strategy(), original.title(), original.deck(), 2, 0, 0, 0, false);
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
        assertFalse(chosen.closed());
        assertEquals(2, claims.choose(chosen, 0, 0, 2).picked());
        assertEquals(2, claims.choose(chosen, 2, 1, 3).picked());
        var completed = claims.choose(chosen, 2, 0, 4);
        assertEquals(3, completed.picked());
        assertTrue(completed.closed());
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

    @Test void hostAcceptanceBeforeLocalAcknowledgementIsNotGrantedTwice() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        var original = offer(id, player, 0, "fixed");
        repository.insertRewardOffer(original);
        assertTrue(repository.selectRewardSlots(original, 1, 1, false));
        var ledger = new java.util.HashMap<String, List<PendingReward>>();
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            String key = call.getArgument(0);
            List<PendingReward> batch = call.getArgument(1);
            var accepted = ledger.putIfAbsent(key, batch);
            return accepted == null || accepted.equals(batch);
        });
        var batch = RewardGameClaims.decode(original).get(0).rewards();
        assertTrue(host.savePendingBatchConfirmed("reward-game:" + id + ":0", batch));
        // Simulate termination after host acceptance and before the offer acknowledgement.
        var reopened = repository();
        var recovered = new RewardGameClaims(reopened, host).process(reopened.loadRewardOffer(id, player), 2);
        assertTrue(recovered.closed());
        assertEquals(1, ledger.size());
        assertEquals(batch, ledger.get("reward-game:" + id + ":0"));
        verify(host, times(2)).savePendingBatchConfirmed("reward-game:" + id + ":0", batch);
    }

    @Test void documentedThreeChoiceConfigurationReusesThePoolWithoutRepeatingPackIds() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString("""
            rewards:
              first: {money: 10}
              second: {money: 20}
              third: {money: 30}
              pool:
                type: random
                pool:
                  - {reward: first, weight: 10}
                  - {reward: second, weight: 60}
                  - {reward: third, weight: 30}
            reward-games:
              clear_choice:
                strategy: choose-three
                pool: pool
                allow-repeat: false
            """);
        var config = xuanmo.arcartxsuite.abyss.template.RewardConfig.load(yaml);
        var game = config.games().get("clear_choice");
        assertEquals(3, game.slots());
        assertEquals(1, game.picks());
        var template = mock(xuanmo.arcartxsuite.abyss.template.AbyssTemplate.class,
            withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(template.rewards()).thenReturn(config);
        var instance = new xuanmo.arcartxsuite.abyss.instance.AbyssInstance(UUID.randomUUID(), template, null);
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var resolver = new RewardResolver(null, null, null, null, Logger.getAnonymousLogger(), null);
        var deck = resolver.rollGame(player, instance, game);
        assertEquals(java.util.Set.of("first", "second", "third"),
            deck.stream().map(RewardResolver.GamePrize::label).collect(java.util.stream.Collectors.toSet()));
        assertTrue(deck.stream().allMatch(prize -> prize.rewards().size() == 1));
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
