package xuanmo.arcartxsuite.auth.center;

import java.sql.*;
import java.util.*;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Account facts commit with their event. Per-node cursor acknowledgements are durable. */
final class CenterOutbox {
    private final JdbcAuthStore.Connections connections;
    CenterOutbox(JdbcAuthStore.Connections connections) throws SQLException {
        this.connections=connections;
        try(Connection c=connections.open(); Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_outbox_v1 (seq INTEGER PRIMARY KEY AUTOINCREMENT,realm TEXT NOT NULL,event_id TEXT NOT NULL UNIQUE,type TEXT NOT NULL,account TEXT NOT NULL,profile TEXT NOT NULL,security_version BIGINT NOT NULL,connection_id TEXT,detail TEXT NOT NULL,created BIGINT NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_outbox_realm_seq_v1 ON axs_center_outbox_v1(realm,seq)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_outbox_created_v1 ON axs_center_outbox_v1(created)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_outbox_floor_v1 (realm TEXT PRIMARY KEY,removed_through BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_outbox_cursors_v1 (node TEXT NOT NULL,realm TEXT NOT NULL,cursor BIGINT NOT NULL,PRIMARY KEY(node,realm))");
        }
    }
    @FunctionalInterface interface Work<T> { T run(Connection connection) throws SQLException; }
    <T> T transaction(Work<T> work) throws SQLException {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try { T result=work.run(c); c.commit(); return result; }
            catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    static void append(Connection c,String realm,String eventId,EventType type,JdbcAuthStore.Account account,
            long version,UUID connection,String detail) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO axs_center_outbox_v1 (realm,event_id,type,account,profile,security_version,connection_id,detail,created) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(event_id) DO NOTHING")) {
            s.setString(1,realm); s.setString(2,eventId); s.setString(3,type.name()); s.setString(4,account.id().toString()); s.setString(5,account.profile().toString());
            s.setLong(6,version); s.setString(7,connection==null?null:connection.toString()); s.setString(8,detail); s.setLong(9,System.currentTimeMillis()); s.executeUpdate();
        }
    }
    private static long readLong(Connection c,String sql,String... values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++) s.setString(i+1,values[i]);
            try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new SQLException("Missing cursor state"); return r.getLong(1); }
        }
    }
    OutboxPage page(String node,String realm,long requested) throws SQLException {
        try(Connection c=connections.open()) {
            long floor=readLong(c,"SELECT COALESCE((SELECT removed_through FROM axs_center_outbox_floor_v1 WHERE realm=?),0)",realm);
            long tail=Math.max(floor,readLong(c,"SELECT COALESCE(MAX(seq),0) FROM axs_center_outbox_v1 WHERE realm=?",realm));
            long after=requested==-1?readLong(c,"SELECT COALESCE((SELECT cursor FROM axs_center_outbox_cursors_v1 WHERE node=? AND realm=?),-1)",node,realm):requested;
            if(after<floor || after>tail) return new OutboxPage(tail,true,List.of());
            if(requested>=0) {
                try(PreparedStatement s=c.prepareStatement("INSERT INTO axs_center_outbox_cursors_v1 (node,realm,cursor) VALUES (?,?,?) ON CONFLICT(node,realm) DO UPDATE SET cursor=excluded.cursor WHERE cursor<>excluded.cursor")) {
                    s.setString(1,node); s.setString(2,realm); s.setLong(3,after); s.executeUpdate();
                }
            }
            List<OutboxEvent> events=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT seq,event_id,type,account,profile,security_version,connection_id,detail FROM axs_center_outbox_v1 WHERE realm=? AND seq>? ORDER BY seq LIMIT 128")) {
                s.setString(1,realm); s.setLong(2,after);
                try(ResultSet r=s.executeQuery()) {
                    while(r.next()) {
                        String connection=r.getString(7);
                        events.add(new OutboxEvent(r.getLong(1),r.getString(2),EventType.valueOf(r.getString(3)),UUID.fromString(r.getString(4)),
                            UUID.fromString(r.getString(5)),r.getLong(6),connection==null?null:UUID.fromString(connection),r.getString(8)));
                    }
                }
            }
            return new OutboxPage(events.isEmpty()?after:events.get(events.size()-1).sequence(),false,events);
        }
    }
    void prune() throws SQLException {
        long cutoff=System.currentTimeMillis()-604_800_000L;
        transaction(c->{
            try(PreparedStatement s=c.prepareStatement("INSERT INTO axs_center_outbox_floor_v1 (realm,removed_through) SELECT realm,MAX(seq) FROM axs_center_outbox_v1 WHERE created<? GROUP BY realm ON CONFLICT(realm) DO UPDATE SET removed_through=MAX(removed_through,excluded.removed_through)")) {
                s.setLong(1,cutoff); s.executeUpdate();
            }
            try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_center_outbox_v1 WHERE created<?")) { s.setLong(1,cutoff); s.executeUpdate(); }
            return null;
        });
    }
}
