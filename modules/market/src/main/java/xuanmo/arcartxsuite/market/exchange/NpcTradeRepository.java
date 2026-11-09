package xuanmo.arcartxsuite.market.exchange;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.*;

/** Persistent claims remain blocked until uncertain external effects are reviewed. */
public final class NpcTradeRepository extends AbstractModuleRepository {
    public static final String OPERATIONS_TABLE_SUFFIX = "npc_trade_operations";

    public record Row(String id,String player,String state,String evidence,long updated) { }
    private final String serverId,runId=UUID.randomUUID().toString();
    public NpcTradeRepository(DataSource source,StorageDescriptor descriptor,File folder,Logger logger)throws java.io.IOException{
        super("AXS-NpcTrade",source,descriptor,folder,logger);
        var path=new File(folder,"npc-trade-server.id").toPath();
        if(!java.nio.file.Files.exists(path))java.nio.file.Files.writeString(path,UUID.randomUUID().toString(),java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        serverId=UUID.fromString(java.nio.file.Files.readString(path,java.nio.charset.StandardCharsets.UTF_8).trim()).toString();
    }
    private String ops(){return tablePrefix()+OPERATIONS_TABLE_SUFFIX;}
    public String operationsTable() { return ops(); }
    private String locks(){return tablePrefix()+"npc_trade_claims";}
    protected void onInitialize(Connection c)throws SQLException{
        try(Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS "+ops()+" (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, server_id VARCHAR(36) NOT NULL, run_id VARCHAR(36) NOT NULL, state VARCHAR(24) NOT NULL, blocking INTEGER NOT NULL DEFAULT 0, evidence "+(isMysql()?"LONGTEXT":"TEXT")+" NOT NULL, updated_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS "+locks()+" (player_uuid VARCHAR(36) PRIMARY KEY, operation_id VARCHAR(36) NOT NULL)");
        }
        try(PreparedStatement q=c.prepareStatement("UPDATE "+ops()+" SET blocking=CASE WHEN state='APPLIED' THEN 0 ELSE 1 END,state='REVIEW' WHERE server_id=? AND run_id<>? AND state NOT IN ('CANCELLED','CONFIRMED','REVIEW')")){q.setString(1,serverId);q.setString(2,runId);q.executeUpdate();}
        tryExecute(c,"CREATE INDEX "+ops()+"_state ON "+ops()+" (state,updated_at)");
        tryExecute(c,"CREATE INDEX "+locks()+"_op ON "+locks()+" (operation_id)");
        tryExecute(c,"CREATE INDEX "+ops()+"_player ON "+ops()+" (player_uuid,state,blocking)");
    }
    protected List<String> playerDataTables(){return List.of();}
    public void begin(String id,UUID player,String evidence)throws SQLException{
        try(Connection c=getConnection()){
            c.setAutoCommit(false);try{
                try(PreparedStatement q=c.prepareStatement("SELECT id FROM "+ops()+" WHERE player_uuid=? AND state='REVIEW' AND blocking=1 LIMIT 1")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){if(r.next())throw new SQLException("NPC_REVIEW:"+r.getString(1));}}
                try(PreparedStatement q=c.prepareStatement("SELECT operation_id FROM "+locks()+" WHERE player_uuid=?")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){if(r.next())throw new SQLException("NPC_REVIEW:"+r.getString(1));}}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO "+locks()+" (player_uuid,operation_id) VALUES (?,?)")){q.setString(1,player.toString());q.setString(2,id);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO "+ops()+" (id,player_uuid,server_id,run_id,state,evidence,updated_at) VALUES (?,?,?,?,'PREPARED',?,?)")){q.setString(1,id);q.setString(2,player.toString());q.setString(3,serverId);q.setString(4,runId);q.setString(5,evidence);q.setLong(6,System.currentTimeMillis());q.executeUpdate();}
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
    }
    public boolean state(String id,String expected,String next,String evidence)throws SQLException{
        try(Connection c=getConnection()){
            c.setAutoCommit(false);try{
                int changed;
                try(PreparedStatement q=c.prepareStatement("UPDATE "+ops()+" SET state=?,evidence=?,updated_at=?,blocking=CASE WHEN ? IN ('APPLIED','CANCELLED','CONFIRMED') THEN 0 ELSE blocking END WHERE id=? AND state=?")){q.setString(1,next);q.setString(2,evidence);q.setLong(3,System.currentTimeMillis());q.setString(4,next);q.setString(5,id);q.setString(6,expected);changed=q.executeUpdate();}
                if(changed==1&&Set.of("APPLIED","CANCELLED","CONFIRMED").contains(next))try(PreparedStatement q=c.prepareStatement("DELETE FROM "+locks()+" WHERE operation_id=?")){q.setString(1,id);q.executeUpdate();}
                c.commit();return changed==1;
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
    }
    public List<Row> list(UUID player)throws SQLException{
        List<Row> rows=new ArrayList<>();
        try(Connection c=getConnection();PreparedStatement q=c.prepareStatement("SELECT id,player_uuid,state,evidence,updated_at FROM "+ops()+" WHERE state='REVIEW'"+(player==null?"":" AND player_uuid=?")+" ORDER BY blocking DESC,updated_at DESC LIMIT 50")){
            if(player!=null)q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){while(r.next())rows.add(new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5)));}
        }return rows;
    }
    public boolean review(String id,String evidence)throws SQLException{
        try(Connection c=getConnection();PreparedStatement q=c.prepareStatement("UPDATE "+ops()+" SET state='REVIEW',blocking=1,evidence=?,updated_at=? WHERE id=? AND state NOT IN ('CANCELLED','CONFIRMED')")){
            q.setString(1,evidence);q.setLong(2,System.currentTimeMillis());q.setString(3,id);return q.executeUpdate()==1;
        }
    }
    public Row get(String id)throws SQLException{
        try(Connection c=getConnection();PreparedStatement q=c.prepareStatement("SELECT id,player_uuid,state,evidence,updated_at FROM "+ops()+" WHERE id=?")){q.setString(1,id);try(ResultSet r=q.executeQuery()){return r.next()?new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5)):null;}}
    }
}
