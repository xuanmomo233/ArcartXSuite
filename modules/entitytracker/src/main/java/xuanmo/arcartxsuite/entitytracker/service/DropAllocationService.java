package xuanmo.arcartxsuite.entitytracker.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.config.DropAllocationSettings;
import xuanmo.arcartxsuite.entitytracker.dao.DropAllocationRecordDao;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerDkpDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;

/**
 * Boss 掉落分配：支持 roll / dkp / priority / manual 四种模式。
 */
public final class DropAllocationService {

    private final JavaPlugin plugin;
    private final DropAllocationSettings settings;
    private final DropAllocationRecordDao allocationDao;
    private final PlayerDkpDao dkpDao;
    private final Random random = new Random();

    public DropAllocationService(JavaPlugin plugin, DropAllocationSettings settings, DataSource dataSource) {
        this.plugin = plugin;
        this.settings = settings;
        this.allocationDao = new DropAllocationRecordDao(dataSource, plugin);
        this.dkpDao = new PlayerDkpDao(dataSource, plugin);
    }

    public void allocateAfterKill(
        BossKillRecord killRecord,
        BossDamageSettlementRecord settlement,
        List<BossKillRecordingService.DropItemSnapshot> drops,
        String serverName
    ) {
        if (!settings.enabled() || killRecord == null || killRecord.getId() == null) {
            return;
        }
        List<Participant> participants = buildParticipants(settlement);
        if (participants.isEmpty()) {
            return;
        }

        earnDkpIfEnabled(participants, settlement, killRecord.getBossDisplayName());

        if (drops == null || drops.isEmpty()) {
            return;
        }

        String mode = settings.defaultMode();
        for (BossKillRecordingService.DropItemSnapshot drop : drops) {
            try {
                switch (mode) {
                    case "dkp" -> allocateByDkp(killRecord.getId(), drop, participants, serverName);
                    case "priority" -> allocateByPriority(killRecord.getId(), drop, participants, serverName);
                    case "manual" -> recordManual(killRecord.getId(), drop, serverName);
                    default -> allocateByRoll(killRecord.getId(), drop, participants, serverName);
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("[EntityTracker] 掉落分配失败: " + exception.getMessage());
            }
        }
    }

    private void earnDkpIfEnabled(
        List<Participant> participants,
        BossDamageSettlementRecord settlement,
        String bossName
    ) {
        DropAllocationSettings.DkpSettings dkp = settings.dkp();
        if (!dkp.enabled()) {
            return;
        }
        for (Participant participant : participants) {
            int bonus = dkp.bonusForRank(participant.rank());
            int total = dkp.baseEarnPoints() + bonus;
            if (total <= 0) {
                continue;
            }
            try {
                dkpDao.addPoints(
                    participant.uuid().toString(),
                    participant.name(),
                    total,
                    "Boss击杀: " + bossName + " (排名#" + participant.rank() + ")"
                );
            } catch (SQLException exception) {
                plugin.getLogger().warning("[EntityTracker] DKP 发放失败: " + exception.getMessage());
            }
        }
    }

    private void allocateByRoll(
        long killId,
        BossKillRecordingService.DropItemSnapshot drop,
        List<Participant> participants,
        String serverName
    ) throws SQLException {
        if (!settings.roll().enabled()) {
            recordManual(killId, drop, serverName);
            return;
        }
        DropAllocationSettings.RollSettings roll = settings.roll();
        Participant winner = null;
        int bestRoll = Integer.MIN_VALUE;
        long allocationId = -1L;

        for (Participant participant : participants) {
            int value = roll.rollMin() + random.nextInt(Math.max(1, roll.rollMax() - roll.rollMin() + 1));
            if (value > bestRoll) {
                bestRoll = value;
                winner = participant;
            }
        }
        if (winner == null) {
            return;
        }

        allocationId = allocationDao.insert(
            killId, drop.itemId(), drop.displayName(), drop.amount(),
            "roll", winner.uuid().toString(), winner.name(), 0, bestRoll, null, serverName
        );
        for (Participant participant : participants) {
            int value = participant == winner ? bestRoll : roll.rollMin();
            allocationDao.insertRollParticipation(
                allocationId, participant.uuid().toString(), participant.name(),
                participant == winner ? "win" : "roll", value, serverName
            );
        }
        deliverItem(winner.uuid(), drop);
        broadcastAllocation("ROLL", winner.name(), drop.displayName(), bestRoll);
    }

    private void allocateByDkp(
        long killId,
        BossKillRecordingService.DropItemSnapshot drop,
        List<Participant> participants,
        String serverName
    ) throws SQLException {
        Participant winner = null;
        int bestPoints = Integer.MIN_VALUE;
        for (Participant participant : participants) {
            int points = dkpDao.getPoints(participant.uuid().toString());
            if (points > bestPoints) {
                bestPoints = points;
                winner = participant;
            }
        }
        if (winner == null) {
            return;
        }
        allocationDao.insert(
            killId, drop.itemId(), drop.displayName(), drop.amount(),
            "dkp", winner.uuid().toString(), winner.name(), 0, null, bestPoints, serverName
        );
        deliverItem(winner.uuid(), drop);
        broadcastAllocation("DKP", winner.name(), drop.displayName(), bestPoints);
    }

    private void allocateByPriority(
        long killId,
        BossKillRecordingService.DropItemSnapshot drop,
        List<Participant> participants,
        String serverName
    ) throws SQLException {
        Map<String, Integer> classPriority = settings.priority().classPriority();
        Participant winner = participants.stream()
            .min(Comparator.comparingInt(participant -> classPriority.getOrDefault("dps", 99)))
            .orElse(participants.get(0));
        int score = classPriority.getOrDefault("dps", 99);
        allocationDao.insert(
            killId, drop.itemId(), drop.displayName(), drop.amount(),
            "priority", winner.uuid().toString(), winner.name(), 0, null, score, serverName
        );
        deliverItem(winner.uuid(), drop);
        broadcastAllocation("PRIORITY", winner.name(), drop.displayName(), score);
    }

    private void recordManual(long killId, BossKillRecordingService.DropItemSnapshot drop, String serverName)
        throws SQLException {
        allocationDao.insert(
            killId, drop.itemId(), drop.displayName(), drop.amount(),
            "manual", null, null, 0, null, null, serverName
        );
    }

    private void deliverItem(UUID winnerUuid, BossKillRecordingService.DropItemSnapshot drop) {
        Player player = Bukkit.getPlayer(winnerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack stack = new ItemStack(
            org.bukkit.Material.matchMaterial(drop.itemId().toUpperCase(Locale.ROOT)),
            drop.amount()
        );
        if (stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void broadcastAllocation(String mode, String winnerName, String itemName, int score) {
        Bukkit.broadcastMessage("§6[EntityTracker] §e" + mode + " 分配: §f" + itemName
            + " §7→ §a" + winnerName + " §7(" + score + ")");
    }

    private static List<Participant> buildParticipants(BossDamageSettlementRecord settlement) {
        List<Participant> participants = new ArrayList<>();
        for (BossDamageSettlementEntry entry : settlement.entriesByPlayer().values()) {
            if (entry == null || entry.playerUuid() == null || entry.damage() <= 0.0D) {
                continue;
            }
            participants.add(new Participant(entry.playerUuid(), entry.playerName(), entry.rank()));
        }
        return participants;
    }

    private record Participant(UUID uuid, String name, int rank) {
    }
}

