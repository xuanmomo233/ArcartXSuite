package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingSettings;

/**
 * Boss 伤害排名追踪器，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 维护单个 Boss 会话中所有玩家的伤害贡献记录，按伤害降序排列，
 * 并根据最低伤害门槛筛选入榜玩家，生成不可变的排名快照。
 * 内部使用脏标记和参考血量缓存机制避免重复计算。
 */
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

    /**
     * 返回当前排名设置。
     *
     * @return 排名设置
     */
    public BossDamageRankingSettings settings() {
        return settings;
    }

    /**
     * 记录玩家对 Boss 造成的伤害。
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @param amount     伤害量（须大于 0）
     */
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

    /**
     * 记录玩家承受 Boss 的伤害。
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @param amount     承伤量（须大于 0）
     */
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

    /**
     * 生成当前排名快照，使用参考血量进行门槛判断。
     * <p>
     * 当数据未变更且参考血量未变化时返回缓存的快照。
     *
     * @param referenceHealth 参考血量（通常为 Boss 最大血量）
     * @return 排名快照
     */
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

