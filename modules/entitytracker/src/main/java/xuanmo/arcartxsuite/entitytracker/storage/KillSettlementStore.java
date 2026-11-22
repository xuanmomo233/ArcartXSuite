package xuanmo.arcartxsuite.entitytracker.storage;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.config.DropAllocationSettings;
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService.DropItemSnapshot;
import xuanmo.arcartxsuite.entitytracker.service.KillAllocation;

/** One receipt transaction owns kill history, statistics, DKP, rolls and a frozen reward outbox. */
public final class KillSettlementStore {
    public record Stored(String key, long killId, List<KillAllocation.Delivery> deliveries, boolean handedOff,
                         boolean created, boolean storeBound, UUID receiptStoreId) {}
    private record DeliveryPlan(UUID receiptStoreId, List<KillAllocation.Delivery> deliveries) {}
    private static final Gson JSON=new Gson();
    private final DataSource source;
    private final BossKillRecordDao kills;
    private final KillAllocation allocation;

    public KillSettlementStore(DataSource source, KillAllocation allocation) {
        this.source=source;this.allocation=allocation;
        kills=new BossKillRecordDao(source,java.util.logging.Logger.getLogger("EntityTracker"));
    }

    public Stored commit(String key,BossKillRecord record,BossDamageSettlementRecord settlement,
            List<DropItemSnapshot> drops,boolean statistics,DropAllocationSettings settings) throws SQLException {
        try(var conn=source.getConnection()) {
            boolean auto=conn.getAutoCommit();conn.setAutoCommit(false);
            try {
                try(var receipt=conn.prepareStatement("INSERT INTO entitytracker_kill_receipts (kill_key,kill_id,delivery_plan,handed_off) VALUES (?,NULL,'[]',0)")) {
                    receipt.setString(1,key);receipt.executeUpdate();
                }catch(SQLException duplicate) {
                    if(duplicate.getErrorCode()!=1062&&duplicate.getErrorCode()!=19)throw duplicate;
                    conn.rollback();return find(conn,key);
                }
                boolean mysql=isMysql(conn);
                if(statistics)ensureTotals(conn,mysql,record.getBossId(),record.getServerName());
                long killId=kills.insert(conn,record);
                if(killId<=0)throw new SQLException("Missing kill ID");
                if(statistics)updateStatistics(conn,mysql,record,drops);
                var deliveries=allocation.prepare(conn,mysql,killId,settlement,drops,settings,record.getServerName());
                boolean storeBound = mysql && !deliveries.isEmpty();
                UUID receiptStore = storeBound ? allocation.receiptStoreId() : null;
                if (storeBound && receiptStore == null) throw new SQLException("待发掉落物的奖励存储尚未就绪");
                String plan = JSON.toJson(storeBound ? new DeliveryPlan(receiptStore, deliveries) : deliveries);
                try(var update=conn.prepareStatement("UPDATE entitytracker_kill_receipts SET kill_id=?,delivery_plan=?,handed_off=? WHERE kill_key=?")) {
                    update.setLong(1,killId);update.setString(2,plan);update.setInt(3,deliveries.isEmpty()?1:0);
                    update.setString(4,key);update.executeUpdate();
                }
                conn.commit();return new Stored(key,killId,deliveries,deliveries.isEmpty(),true,storeBound,receiptStore);
            }catch(SQLException|RuntimeException failure) {
                try{conn.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;
            }finally{conn.setAutoCommit(auto);}
        }
    }

    private static Stored find(Connection conn,String key) throws SQLException {
        try(var query=conn.prepareStatement("SELECT kill_id,delivery_plan,handed_off FROM entitytracker_kill_receipts WHERE kill_key=?")) {
            query.setString(1,key);try(var rows=query.executeQuery()) {
                if(!rows.next()||rows.getObject(1)==null)throw new SQLException("Incomplete kill receipt "+key);
                return stored(key,rows.getLong(1),rows.getString(2),rows.getInt(3)!=0,isMysql(conn));
            }
        }
    }

    public boolean handoff(Stored stored) throws SQLException {
        return handoff(stored, ignored -> {});
    }

    public boolean handoff(Stored stored, Consumer<UUID> accepted) throws SQLException {
        if(stored.handedOff())return true;
        if (stored.storeBound() && (stored.receiptStoreId() == null
            || !stored.receiptStoreId().equals(allocation.receiptStoreId()))) return false;
        if(!allocation.handoff(stored.key(), stored.deliveries(), accepted))return false;
        try(var conn=source.getConnection();var update=conn.prepareStatement("UPDATE entitytracker_kill_receipts SET handed_off=1,delivery_plan='[]' WHERE kill_key=?")) {
            update.setString(1,stored.key());return update.executeUpdate()==1;
        }
    }

    public List<Stored> pending(int limit) throws SQLException {
        return pendingAfter(0L, limit);
    }

    public List<Stored> pendingAfter(long killId, int limit) throws SQLException {
        try(var conn=source.getConnection();var query=conn.prepareStatement(
            "SELECT kill_key,kill_id,delivery_plan FROM entitytracker_kill_receipts WHERE handed_off=0 AND kill_id>? ORDER BY kill_id LIMIT ?")) {
            boolean mysql=isMysql(conn);
            query.setLong(1, killId);
            query.setInt(2, limit);
            try(var rows=query.executeQuery()) {
                var result=new ArrayList<Stored>();while(rows.next())result.add(stored(rows.getString(1),rows.getLong(2),rows.getString(3),false,mysql));return result;
            }
        }
    }

    private static Stored stored(String key,long id,String json,boolean accepted,boolean mysql) {
        var payload = JsonParser.parseString(json);
        if (payload.isJsonObject()) {
            var plan = JSON.fromJson(payload, DeliveryPlan.class);
            return new Stored(key,id,List.copyOf(plan.deliveries()),accepted,false,true,plan.receiptStoreId());
        }
        var values=JSON.fromJson(payload,KillAllocation.Delivery[].class);
        // A plan without its receipt store cannot safely resume in a different MySQL client's queue.
        return new Stored(key,id,List.of(values),accepted,false,mysql,null);
    }

    private static boolean isMysql(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
    }

    private static void ensureTotals(Connection conn,boolean mysql,String boss,String server) throws SQLException {
        try(var query=conn.prepareStatement("SELECT 1 FROM entitytracker_boss_totals WHERE boss_id=? AND server_name=?")) {
            query.setString(1,boss);query.setString(2,server);try(var rows=query.executeQuery()){if(rows.next())return;}
        }
        try(var insert=conn.prepareStatement((mysql?"INSERT IGNORE":"INSERT OR IGNORE")
                +" INTO entitytracker_boss_totals (boss_id,server_name,kill_count) SELECT ?,?,MAX(kills) FROM"
                +" (SELECT COUNT(*) AS kills FROM boss_kill_records WHERE boss_id=? AND COALESCE(server_name,'')=?"
                +" UNION ALL SELECT COALESCE(MAX(kill_count),0) AS kills FROM boss_drop_statistics WHERE boss_id=? AND COALESCE(server_name,'')=?) existing")) {
            insert.setString(1,boss);insert.setString(2,server);insert.setString(3,boss);insert.setString(4,server);
            insert.setString(5,boss);insert.setString(6,server);insert.executeUpdate();
        }
    }

    private static void updateStatistics(Connection conn,boolean mysql,BossKillRecord record,List<DropItemSnapshot> drops) throws SQLException {
        long total;
        try(var update=conn.prepareStatement("UPDATE entitytracker_boss_totals SET kill_count=kill_count+1 WHERE boss_id=? AND server_name=?")) {
            update.setString(1,record.getBossId());update.setString(2,record.getServerName());update.executeUpdate();
        }
        try(var query=conn.prepareStatement("SELECT kill_count FROM entitytracker_boss_totals WHERE boss_id=? AND server_name=?")) {
            query.setString(1,record.getBossId());query.setString(2,record.getServerName());
            try(var rows=query.executeQuery()){if(!rows.next())throw new SQLException("Missing boss total");total=rows.getLong(1);}
        }
        String upsert=mysql
            ? " ON DUPLICATE KEY UPDATE item_name=VALUES(item_name),drop_count=drop_count+1,last_drop_time=VALUES(last_drop_time)"
            : " ON CONFLICT(boss_id,item_id,server_name) DO UPDATE SET item_name=excluded.item_name,drop_count=drop_count+1,last_drop_time=excluded.last_drop_time";
        try(var insert=conn.prepareStatement("INSERT INTO boss_drop_statistics (boss_id,item_id,item_name,drop_count,kill_count,drop_rate,last_drop_time,server_name) VALUES (?,?,?,1,?,0,?,?)"+upsert)) {
            var byItem=new LinkedHashMap<String,DropItemSnapshot>();
            for(var drop:drops)byItem.put(drop.itemId(),drop);
            for(var drop:byItem.values()){insert.setString(1,record.getBossId());insert.setString(2,drop.itemId());insert.setString(3,drop.displayName());
                insert.setLong(4,total);insert.setTimestamp(5,Timestamp.valueOf(record.getKillTime()));insert.setString(6,record.getServerName());insert.addBatch();}
            insert.executeBatch();
        }
        try(var update=conn.prepareStatement("UPDATE boss_drop_statistics SET kill_count=?,drop_rate=CASE WHEN drop_count>? THEN 1.0 ELSE CAST(drop_count AS DOUBLE)/? END WHERE boss_id=? AND server_name=?")) {
            update.setLong(1,total);update.setLong(2,total);update.setLong(3,total);update.setString(4,record.getBossId());update.setString(5,record.getServerName());update.executeUpdate();
        }
    }
}
