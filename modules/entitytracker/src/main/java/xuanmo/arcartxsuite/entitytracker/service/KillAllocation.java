package xuanmo.arcartxsuite.entitytracker.service;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.config.DropAllocationSettings;

/** Prepares SQL in the kill transaction, then transfers frozen rewards to the host off Main. */
public final class KillAllocation {
    public record Delivery(UUID player, String name, String itemData, String itemName, String mode, int score) {}
    private final PendingRewardService rewards;
    public KillAllocation(PendingRewardService rewards) { this.rewards = rewards; }

    public UUID receiptStoreId() {
        return rewards == null ? null : rewards.rewardReceiptStoreId();
    }

    static String effectiveMode(DropAllocationSettings settings) {
        if(!settings.enabled())return "manual";
        String mode=switch(settings.defaultMode()) { case "manual","dkp","priority" -> settings.defaultMode();default -> "roll"; };
        if("roll".equals(mode)&&!settings.roll().enabled())return "manual";
        if("dkp".equals(mode)&&!settings.dkp().enabled())return "manual";
        if("priority".equals(mode)&&!settings.priority().enabled())return "manual";
        return mode;
    }

    public static boolean allocatesItems(DropAllocationSettings settings, BossDamageSettlementRecord settlement) {
        return !"manual".equals(effectiveMode(settings))
            && settlement.entriesByPlayer().values().stream()
                .anyMatch(entry -> entry != null && entry.playerUuid() != null && entry.damage() > 0);
    }

    public List<Delivery> prepare(Connection conn, boolean mysql, long killId, BossDamageSettlementRecord settlement,
            List<BossKillRecordingService.DropItemSnapshot> drops, DropAllocationSettings settings, String server) throws SQLException {
        if (!settings.enabled()) return List.of();
        var participants=settlement.entriesByPlayer().values().stream()
            .filter(entry -> entry != null && entry.playerUuid() != null && entry.damage() > 0).toList();
        if (participants.isEmpty()) return List.of();
        if (settings.dkp().enabled()) {
            String update=mysql
                ? " ON DUPLICATE KEY UPDATE player_name=VALUES(player_name),total_points=total_points+VALUES(total_points),earned_points=earned_points+VALUES(earned_points),last_update=VALUES(last_update)"
                : " ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name,total_points=total_points+excluded.total_points,earned_points=earned_points+excluded.earned_points,last_update=excluded.last_update";
            try(var balance=conn.prepareStatement("INSERT INTO player_dkp (player_uuid,player_name,total_points,earned_points,spent_points,last_update,server_name) VALUES (?,?,?,?,0,?,?)"+update);
                var history=conn.prepareStatement("INSERT INTO dkp_transaction_records (player_uuid,player_name,transaction_type,points,reason,boss_kill_id,server_name) VALUES (?,?,'earn',?,?,?,?)")) {
                var now=Timestamp.valueOf(java.time.LocalDateTime.now());
                // Consistent balance lock order across simultaneous settlements.
                for(var p:participants.stream().sorted(Comparator.comparing(p->p.playerUuid().toString())).toList()) {
                    int points=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,(long)settings.dkp().baseEarnPoints()+settings.dkp().bonusForRank(p.rank())));
                    if(points==0)continue;
                    balance.setString(1,p.playerUuid().toString());balance.setString(2,p.playerName());balance.setInt(3,points);balance.setInt(4,points);
                    balance.setTimestamp(5,now);balance.setString(6,server);balance.addBatch();
                    history.setString(1,p.playerUuid().toString());history.setString(2,p.playerName());history.setInt(3,points);
                    history.setString(4,"Boss击杀: "+settlement.bossDisplayName()+" (排名#"+p.rank()+")");history.setLong(5,killId);history.setString(6,server);history.addBatch();
                }
                balance.executeBatch();history.executeBatch();
            }
        }
        Map<UUID,Integer> points=new HashMap<>();
        if("dkp".equals(effectiveMode(settings))&&!drops.isEmpty()) {
            String placeholders=String.join(",",Collections.nCopies(participants.size(),"?"));
            try(var query=conn.prepareStatement("SELECT player_uuid,total_points FROM player_dkp WHERE player_uuid IN ("+placeholders+")")) {
                int index=1;for(var p:participants)query.setString(index++,p.playerUuid().toString());
                try(var rows=query.executeQuery()){while(rows.next())points.put(UUID.fromString(rows.getString(1)),rows.getInt(2));}
            }
        }
        var deliveries=new ArrayList<Delivery>();
        for(var drop:drops) {
            String mode=effectiveMode(settings);int winner=0,score=0;int[] rolls=new int[participants.size()];
            if("roll".equals(mode)) {
                score=Integer.MIN_VALUE;
                for(int i=0;i<participants.size();i++) {
                    var roll=settings.roll();rolls[i]=(int)java.util.concurrent.ThreadLocalRandom.current().nextLong(roll.rollMin(),(long)Math.max(roll.rollMin(),roll.rollMax())+1);
                    if(rolls[i]>score){score=rolls[i];winner=i;}
                }
            }else if("dkp".equals(mode)) {
                score=Integer.MIN_VALUE;
                for(int i=0;i<participants.size();i++){int value=points.getOrDefault(participants.get(i).playerUuid(),0);if(value>score){score=value;winner=i;}}
            }else if("priority".equals(mode))score=settings.priority().classPriority().getOrDefault("dps",99);
            var recipient=participants.get(winner);boolean manual="manual".equals(mode);long allocation;
            try(var insert=conn.prepareStatement("INSERT INTO drop_allocation_records (boss_kill_id,item_id,item_name,item_amount,allocation_type,winner_uuid,winner_name,points_cost,roll_value,priority_score,server_name) VALUES (?,?,?,?,?,?,?,0,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1,killId);insert.setString(2,drop.itemId());insert.setString(3,drop.displayName());insert.setInt(4,drop.amount());insert.setString(5,mode);
                insert.setString(6,manual?null:recipient.playerUuid().toString());insert.setString(7,manual?null:recipient.playerName());insert.setObject(8,"roll".equals(mode)?score:null);
                insert.setObject(9,"dkp".equals(mode)||"priority".equals(mode)?score:null);insert.setString(10,server);insert.executeUpdate();
                try(var keys=insert.getGeneratedKeys()){if(!keys.next())throw new SQLException("Missing allocation ID");allocation=keys.getLong(1);}
            }
            if("roll".equals(mode))try(var insert=conn.prepareStatement("INSERT INTO roll_participation_records (allocation_record_id,player_uuid,player_name,roll_type,roll_value,server_name) VALUES (?,?,?,?,?,?)")) {
                for(int i=0;i<participants.size();i++) {
                    insert.setLong(1,allocation);insert.setString(2,participants.get(i).playerUuid().toString());insert.setString(3,participants.get(i).playerName());
                    insert.setString(4,i==winner?"win":"roll");insert.setInt(5,rolls[i]);insert.setString(6,server);insert.addBatch();
                }
                insert.executeBatch();
            }
            if(!manual){
                if(drop.itemData()==null||drop.itemData().isBlank())throw new SQLException("Missing original item data");
                deliveries.add(new Delivery(recipient.playerUuid(),recipient.playerName(),drop.itemData(),drop.displayName(),mode,score));
            }
        }
        return List.copyOf(deliveries);
    }

    public boolean handoff(String key, List<Delivery> deliveries, Consumer<UUID> accepted) {
        if(deliveries.isEmpty())return true;
        if(rewards==null)return false;
        var batches=new LinkedHashMap<UUID,List<PendingRewardService.PendingReward>>();
        for(var delivery:deliveries)batches.computeIfAbsent(delivery.player(),ignored->new ArrayList<>()).add(
            PendingRewardService.PendingReward.itemSerialized(delivery.player(),"entitytracker",delivery.itemData(),"Boss 掉落: "+delivery.itemName()));
        for (var batch : batches.entrySet()) {
            if (!rewards.savePendingBatchConfirmed("kill:" + key + ":" + batch.getKey(), batch.getValue())) return false;
            accepted.accept(batch.getKey());
        }
        return true;
    }
}
