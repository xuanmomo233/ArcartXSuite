package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingSettings;

final class BossDamageRankingTracker {

    private static final Comparator<Contribution> CONTRIBUTION_ORDER = Comparator
        .comparingDouble(Contribution::damage)
        .reversed()
        .thenComparingLong(Contribution::firstContributionOrder)
        .thenComparing(contribution -> contribution.playerName().toLowerCase(Locale.ROOT))
        .thenComparing(contribution -> contribution.playerUuid().toString());

    private final BossDamageRankingSettings settings;
    private final Map<UUID, Contribution> contributions = new LinkedHashMap<>();

    private BossDamageRankingSnapshot cachedSnapshot;
    private boolean dirty = true;
    private long nextContributionOrder = 0L;
    private double lastReferenceHealth = Double.NaN;

    BossDamageRankingTracker(BossDamageRankingSettings settings) {
        this.settings = settings == null ? BossDamageRankingSettings.defaults() : settings;
        this.cachedSnapshot = BossDamageRankingSnapshot.empty(this.settings.enabled(), this.settings.maxEntries());
    }

    public BossDamageRankingSettings settings() {
        return settings;
    }

    public void recordDamage(UUID playerUuid, String playerName, double amount) {
        if (!settings.enabled() || playerUuid == null || amount <= 0.0D) {
            return;
        }
        Contribution contribution = contributions.computeIfAbsent(
            playerUuid,
            key -> new Contribution(key, safePlayerName(playerName), nextContributionOrder++)
        );
        contribution.updateName(playerName);
        contribution.addDamage(amount);
        dirty = true;
    }

    public void recordTakenDamage(UUID playerUuid, String playerName, double amount) {
        if (!settings.enabled() || playerUuid == null || amount <= 0.0D) {
            return;
        }
        Contribution contribution = contributions.computeIfAbsent(
            playerUuid,
            key -> new Contribution(key, safePlayerName(playerName), nextContributionOrder++)
        );
        contribution.updateName(playerName);
        contribution.addTakenDamage(amount);
        dirty = true;
    }

    public BossDamageRankingSnapshot snapshot(double referenceHealth) {
        if (!settings.enabled()) {
            cachedSnapshot = BossDamageRankingSnapshot.empty(false, settings.maxEntries());
            dirty = false;
            lastReferenceHealth = Double.NaN;
            return cachedSnapshot;
        }
        if (!dirty && Double.compare(lastReferenceHealth, referenceHealth) == 0) {
            return cachedSnapshot;
        }

        double totalDamage = 0.0D;
        for (Contribution contribution : contributions.values()) {
            totalDamage += Math.max(0.0D, contribution.damage());
        }

        List<Contribution> orderedContributions = new ArrayList<>(contributions.values());
        orderedContributions.sort(CONTRIBUTION_ORDER);

        List<Contribution> qualified = new ArrayList<>();
        for (Contribution contribution : orderedContributions) {
            if (settings.minDamageThreshold().passes(contribution.damage(), referenceHealth)) {
                qualified.add(contribution);
            }
        }

        Map<UUID, BossDamageRankingEntry> entriesByPlayer = new LinkedHashMap<>();
        List<BossDamageRankingEntry> rankedEntries = new ArrayList<>(qualified.size());
        for (int index = 0; index < qualified.size(); index++) {
            Contribution contribution = qualified.get(index);
            BossDamageRankingEntry entry = createEntry(contribution, totalDamage, index + 1, true);
            entriesByPlayer.put(contribution.playerUuid(), entry);
            rankedEntries.add(entry);
        }

        List<BossDamageRankingEntry> trackedEntries = new ArrayList<>(orderedContributions.size());
        for (Contribution contribution : orderedContributions) {
            BossDamageRankingEntry entry = entriesByPlayer.get(contribution.playerUuid());
            if (entry == null) {
                entry = createEntry(contribution, totalDamage, 0, false);
                entriesByPlayer.put(contribution.playerUuid(), entry);
            }
            trackedEntries.add(entry);
        }

        cachedSnapshot = new BossDamageRankingSnapshot(
            true,
            settings.maxEntries(),
            qualified.size(),
            contributions.size(),
            totalDamage,
            List.copyOf(rankedEntries),
            List.copyOf(trackedEntries),
            Map.copyOf(entriesByPlayer)
        );
        dirty = false;
        lastReferenceHealth = referenceHealth;
        return cachedSnapshot;
    }

    private static BossDamageRankingEntry createEntry(Contribution contribution, double totalDamage, int rank, boolean qualified) {
        double damage = Math.max(0.0D, contribution.damage());
        double damagePercent = totalDamage <= 0.0D ? 0.0D : (damage / totalDamage) * 100.0D;
        return new BossDamageRankingEntry(
            contribution.playerUuid(),
            contribution.playerName(),
            rank,
            qualified,
            damage,
            damagePercent,
            Math.max(0.0D, contribution.takenDamage())
        );
    }

    private static String safePlayerName(String playerName) {
        return playerName == null || playerName.isBlank() ? "unknown" : playerName;
    }

    private static final class Contribution {

        private final UUID playerUuid;
        private final long firstContributionOrder;
        private String playerName;
        private double damage;
        private double takenDamage;

        private Contribution(UUID playerUuid, String playerName, long firstContributionOrder) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.firstContributionOrder = firstContributionOrder;
        }

        private UUID playerUuid() {
            return playerUuid;
        }

        private String playerName() {
            return playerName;
        }

        private double damage() {
            return damage;
        }

        private double takenDamage() {
            return takenDamage;
        }

        private long firstContributionOrder() {
            return firstContributionOrder;
        }

        private void updateName(String playerName) {
            if (playerName != null && !playerName.isBlank()) {
                this.playerName = playerName;
            }
        }

        private void addDamage(double amount) {
            damage += amount;
        }

        private void addTakenDamage(double amount) {
            takenDamage += amount;
        }
    }
}

