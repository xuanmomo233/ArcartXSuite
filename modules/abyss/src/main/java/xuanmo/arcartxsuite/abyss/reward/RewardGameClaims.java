package xuanmo.arcartxsuite.abyss.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.RewardOffer;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;

/** Persists the choice before delivery. Retries submit the exact original slot and payload. */
final class RewardGameClaims {
    private final JdbcAbyssRepository repository;
    private final PendingRewardService rewards;

    RewardGameClaims(JdbcAbyssRepository repository, PendingRewardService rewards) {
        this.repository = repository;
        this.rewards = rewards;
    }

    RewardOffer choose(RewardOffer offer, int expected, int slot, long now) throws Exception {
        var deck = decode(offer);
        if (slot < 0 || slot >= deck.size()) return offer;
        if (offer.expiresAt() > 0 && offer.expiresAt() <= now) return process(offer, now);
        int bit = 1 << slot;
        if (!offer.closed() && expected == offer.picked() && (offer.picked() & bit) == 0
            && Integer.bitCount(offer.picked()) < offer.limit()) {
            repository.selectRewardSlots(offer, offer.picked() | bit, now, false);
        }
        return process(repository.loadRewardOffer(offer.id(), offer.player()), now);
    }

    RewardOffer process(RewardOffer offer, long now) throws Exception {
        if (offer == null || offer.closed()) return offer;
        var deck = decode(offer);
        if (offer.expiresAt() > 0 && offer.expiresAt() <= now && Integer.bitCount(offer.picked()) < offer.limit()) {
            int picked = offer.picked();
            for (int slot = 0; slot < deck.size() && Integer.bitCount(picked) < offer.limit(); slot++) picked |= 1 << slot;
            repository.selectRewardSlots(offer, picked, now, true);
            offer = repository.loadRewardOffer(offer.id(), offer.player());
            if (offer == null || offer.closed()) return offer;
        }
        int accepted = offer.accepted();
        for (int slot = 0; slot < deck.size(); slot++) {
            int bit = 1 << slot;
            if ((offer.picked() & bit) != 0 && (accepted & bit) == 0
                && rewards.savePendingBatchConfirmed("reward-game:" + offer.id() + ":" + slot, deck.get(slot).rewards())) {
                accepted |= bit;
            }
        }
        if (offer.picked() != 0) repository.acceptRewardSlots(offer, accepted);
        return repository.loadRewardOffer(offer.id(), offer.player());
    }

    static String encode(List<RewardResolver.GamePrize> deck) {
        var yaml = new YamlConfiguration();
        var cards = new ArrayList<Map<String, Object>>();
        for (var prize : deck) {
            var batch = new ArrayList<Map<String, String>>();
            for (var reward : prize.rewards()) {
                var row = new LinkedHashMap<String, String>();
                row.put("type", reward.type().name());
                row.put("payload", reward.payload());
                batch.add(row);
            }
            cards.add(Map.of("label", prize.label(), "rewards", batch));
        }
        yaml.set("cards", cards);
        String snapshot = yaml.saveToString();
        if (snapshot.length() > 512 * 1024) throw new IllegalArgumentException("奖励快照过大");
        return snapshot;
    }

    static List<RewardResolver.GamePrize> decode(RewardOffer offer) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(offer.deck());
        var deck = new ArrayList<RewardResolver.GamePrize>();
        for (var row : yaml.getMapList("cards")) {
            String label = (String) row.get("label");
            var batch = new ArrayList<PendingReward>();
            for (Object item : (List<?>) row.get("rewards")) {
                var raw = (Map<?, ?>) item;
                batch.add(new PendingReward(0, offer.player(), "abyss", PendingReward.Type.valueOf((String)raw.get("type")),
                    (String)raw.get("payload"), label, 0L, 0));
            }
            if (batch.isEmpty()) throw new IllegalStateException("Empty reward slot");
            deck.add(new RewardResolver.GamePrize(label, batch));
        }
        if (deck.size() < offer.limit() || deck.size() > 9 || offer.limit() <= 0)
            throw new IllegalStateException("Invalid reward offer snapshot");
        return List.copyOf(deck);
    }
}
