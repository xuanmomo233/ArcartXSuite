package xuanmo.arcartxsuite.auth.center;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;
import xuanmo.arcartxsuite.auth.protocol.*;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Durable center-only flows, hashed single-use tickets, leases, and unknown-outcome fencing. */
final class CenterLedger {
    static final long LEASE_MILLIS=15_000;
    record Flow(UUID id,String node,Context context,UUID account,long version,long expires,String method) {}
    record Lease(UUID id,Flow flow,long expires) {}
    private final JdbcAuthStore.Connections connections;
    private final java.util.function.LongSupplier nonceClock;
    private final int commandCapacity;
    private record NonceCount(long checked,int count) {}
    private final Map<String,NonceCount> nonceCounts=new HashMap<>();
    private final Map<String,NonceCount> commandCounts=new HashMap<>();
    enum CommandStatus { STARTED,REPLAYED,FULL }
    CenterLedger(JdbcAuthStore.Connections connections) { this(connections,System::currentTimeMillis); }
    CenterLedger(JdbcAuthStore.Connections connections,java.util.function.LongSupplier nonceClock) { this(connections,nonceClock,1_000_000); }
    CenterLedger(JdbcAuthStore.Connections connections,java.util.function.LongSupplier nonceClock,int commandCapacity) {
        this.connections=connections; this.nonceClock=nonceClock; this.commandCapacity=commandCapacity;
    }
    void initialize() throws SQLException {
        try(Connection c=connections.open(); Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_flows_v2 (id TEXT PRIMARY KEY,node TEXT NOT NULL,realm TEXT NOT NULL,audience TEXT NOT NULL,profile TEXT NOT NULL,name TEXT NOT NULL,connection_id TEXT NOT NULL,account TEXT NOT NULL,version BIGINT NOT NULL,expires BIGINT NOT NULL,method TEXT NOT NULL,UNIQUE(node,connection_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_tickets_v1 (hash TEXT PRIMARY KEY,flow TEXT NOT NULL,expires BIGINT NOT NULL,redeemed_request TEXT,session_id TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_sessions_v1 (id TEXT PRIMARY KEY,flow TEXT NOT NULL UNIQUE,expires BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_commands_v1 (id TEXT PRIMARY KEY,node TEXT NOT NULL,operation TEXT NOT NULL,fingerprint TEXT NOT NULL,created BIGINT NOT NULL,result TEXT NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_commands_node_created_v1 ON axs_center_commands_v1(node,created)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_commands_created_v1 ON axs_center_commands_v1(created)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_nonces_v2 (node TEXT NOT NULL,id TEXT NOT NULL,expires BIGINT NOT NULL,PRIMARY KEY(node,id))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_nonces_expiry_v2 ON axs_center_nonces_v2(node,expires)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS axs_center_nonces_all_expiry_v2 ON axs_center_nonces_v2(expires)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_binding_codes_v1 (hash TEXT PRIMARY KEY,realm TEXT NOT NULL,account TEXT NOT NULL,qq BIGINT NOT NULL,expires BIGINT NOT NULL,UNIQUE(realm,account),UNIQUE(realm,qq))");
        }
    }
    private int update(String sql,Object... values) throws SQLException {
        try(Connection c=connections.open()) { return update(c,sql,values); }
    }
    static int update(Connection c,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) { bind(s,values); return s.executeUpdate(); }
    }
    private static void bind(PreparedStatement s,Object... values) throws SQLException {
        for(int index=0;index<values.length;index++) s.setObject(index+1,values[index] instanceof UUID?values[index].toString():values[index]);
    }
    boolean claimNonce(String node,UUID nonce,int capacity) throws SQLException {
        long now=nonceClock.getAsLong();
        NonceCount budget=nonceCounts.get(node);
        if(budget==null || now-budget.checked()>=1000 || now<budget.checked()) {
            update("DELETE FROM axs_center_nonces_v2 WHERE node=? AND expires<=?",node,now);
            try(Connection c=connections.open(); PreparedStatement count=c.prepareStatement("SELECT COUNT(*) FROM axs_center_nonces_v2 WHERE node=?")) {
                count.setString(1,node);
                try(ResultSet r=count.executeQuery()) { r.next(); budget=new NonceCount(now,r.getInt(1)); }
            }
        }
        nonceCounts.put(node,budget);
        if(budget.count()>=capacity) return false;
        // Requests accept +/-30 seconds, inclusive. Retain beyond the entire 60-second boundary.
        boolean accepted=update("INSERT INTO axs_center_nonces_v2 (node,id,expires) VALUES (?,?,?) ON CONFLICT(node,id) DO NOTHING",node,nonce,now+61_000)==1;
        if(accepted) nonceCounts.put(node,new NonceCount(budget.checked(),budget.count()+1));
        return accepted;
    }
    CommandStatus beginCommand(CenterConfiguration.Node node,Request request) throws SQLException {
        String fingerprint=RpcAuthentication.sign(node.key(),"AXS-COMMAND-FINGERPRINT-V1\n"+JSON.toJson(request));
        try(Connection c=connections.open(); PreparedStatement find=c.prepareStatement("SELECT node,fingerprint FROM axs_center_commands_v1 WHERE id=?")) {
            find.setString(1,request.requestId().toString());
            try(ResultSet r=find.executeQuery()) {
                if(r.next()) {
                    if(!node.id().equals(r.getString(1)) || !fingerprint.equals(r.getString(2))) throw new SecurityException("Request identity collision");
                    return CommandStatus.REPLAYED;
                }
            }
        }
        long now=System.currentTimeMillis(); NonceCount budget=commandCounts.get(node.id());
        if(budget==null || now-budget.checked()>=1000 || now<budget.checked()) {
            try(Connection c=connections.open(); PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM axs_center_commands_v1 WHERE node=? AND created>=?")) {
                bind(s,node.id(),now-86_400_000L);
                try(ResultSet r=s.executeQuery()) { r.next(); budget=new NonceCount(now,r.getInt(1)); }
            }
        }
        commandCounts.put(node.id(),budget);
        if(budget.count()>=commandCapacity) return CommandStatus.FULL;
        update("INSERT INTO axs_center_commands_v1 (id,node,operation,fingerprint,created,result) VALUES (?,?,?,?,?,?)",
            request.requestId(),node.id(),request.operation().name(),fingerprint,System.currentTimeMillis(),"OUTCOME_UNKNOWN");
        commandCounts.put(node.id(),new NonceCount(budget.checked(),budget.count()+1));
        return CommandStatus.STARTED;
    }
    void finishCommand(Request request,String code) throws SQLException { update("UPDATE axs_center_commands_v1 SET result=? WHERE id=?",code,request.requestId()); }
    Flow find(String node,Context context) throws SQLException {
        try(Connection c=connections.open(); PreparedStatement s=c.prepareStatement("SELECT * FROM axs_center_flows_v2 WHERE node=? AND connection_id=?")) {
            bind(s,node,context.connection());
            try(ResultSet r=s.executeQuery()) { return r.next()?flow(r):null; }
        }
    }
    private static Flow flow(ResultSet r) throws SQLException {
        Context context=new Context(r.getString("realm"),r.getString("audience"),UUID.fromString(r.getString("profile")),r.getString("name"),UUID.fromString(r.getString("connection_id")));
        return new Flow(UUID.fromString(r.getString("id")),r.getString("node"),context,UUID.fromString(r.getString("account")),r.getLong("version"),r.getLong("expires"),r.getString("method"));
    }
    Flow create(String node,Context context,JdbcAuthStore.Account account) throws SQLException {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try {
                Flow flow=create(c,node,context,account.id(),account.version(),"local-password");
                c.commit();
                return flow;
            } catch(SQLException e) { c.rollback(); throw e; }
        }
    }
    Flow create(Connection c,String node,Context context,UUID account,long version,String method) throws SQLException {
        close(c,node,context);
        Flow flow=new Flow(UUID.randomUUID(),node,context,account,version,System.currentTimeMillis()+300_000,method);
        update(c,"INSERT INTO axs_center_flows_v2 (id,node,realm,audience,profile,name,connection_id,account,version,expires,method) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            flow.id(),node,context.realm(),context.audience(),context.profile(),context.name(),context.connection(),account,version,flow.expires(),method);
        return flow;
    }
    String ticket(Flow flow) throws SQLException {
        update("DELETE FROM axs_center_tickets_v1 WHERE flow=? AND redeemed_request IS NULL",flow.id());
        String token=random();
        update("INSERT INTO axs_center_tickets_v1 (hash,flow,expires) VALUES (?,?,?)",hash(token),flow.id(),System.currentTimeMillis()+30_000);
        return token;
    }
    Lease session(Flow flow) throws SQLException {
        try(Connection c=connections.open(); PreparedStatement s=c.prepareStatement("SELECT id,expires FROM axs_center_sessions_v1 WHERE flow=?")) {
            bind(s,flow.id()); try(ResultSet r=s.executeQuery()) { return r.next()?new Lease(UUID.fromString(r.getString(1)),flow,r.getLong(2)):null; }
        }
    }
    Lease exchange(Flow flow,String ticket,UUID request,JdbcAuthStore store,JdbcAuthStore.Account account) throws SQLException {
        long now=System.currentTimeMillis();
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try {
                String redeemed=null,session=null;
                try(PreparedStatement s=c.prepareStatement("SELECT redeemed_request,session_id FROM axs_center_tickets_v1 WHERE hash=? AND flow=? AND expires>?")) {
                    bind(s,hash(ticket),flow.id(),now);
                    try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new SecurityException("Ticket expired"); redeemed=r.getString(1); session=r.getString(2); }
                }
                if(redeemed!=null) {
                    if(!redeemed.equals(request.toString())) throw new SecurityException("Ticket already consumed");
                    c.rollback();
                    Lease existing=session(flow);
                    if(existing==null || !existing.id().toString().equals(session) || existing.expires()<=now) throw new SecurityException("Session expired");
                    return existing;
                }
                UUID id=UUID.randomUUID();
                try(PreparedStatement s=c.prepareStatement("UPDATE axs_center_tickets_v1 SET redeemed_request=?,session_id=? WHERE hash=? AND redeemed_request IS NULL AND expires>? AND EXISTS (SELECT 1 FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=? AND security_version=?)")) {
                    bind(s,request,id,hash(ticket),now,flow.context().realm(),flow.account(),flow.version());
                    if(s.executeUpdate()!=1) throw new SecurityException("Stale ticket");
                }
                if(!store.admit(c,account)) throw new SecurityException("Credentials changed during exchange");
                try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_center_sessions_v1 WHERE flow=?")) { bind(s,flow.id()); s.executeUpdate(); }
                try(PreparedStatement s=c.prepareStatement("INSERT INTO axs_center_sessions_v1 (id,flow,expires) VALUES (?,?,?)")) { bind(s,id,flow.id(),now+LEASE_MILLIS); s.executeUpdate(); }
                CenterOutbox.append(c,flow.context().realm(),id.toString(),EventType.ADMISSION_GRANTED,account,flow.version(),flow.context().connection(),"");
                if(!account.firstAdmitted()) CenterOutbox.append(c,flow.context().realm(),account.id()+":first",EventType.FIRST_ADMISSION,account,flow.version(),flow.context().connection(),"");
                c.commit(); return new Lease(id,flow,now+LEASE_MILLIS);
            } catch(Exception e) { c.rollback(); throw e; }
        }
    }
    Lease renew(Flow flow,UUID session) throws SQLException {
        long now=System.currentTimeMillis();
        if(update("UPDATE axs_center_sessions_v1 SET expires=? WHERE id=? AND flow=? AND expires>? AND EXISTS (SELECT 1 FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=? AND security_version=?)",
                now+LEASE_MILLIS,session,flow.id(),now,flow.context().realm(),flow.account(),flow.version())!=1) throw new SecurityException("Session revoked or expired");
        return new Lease(session,flow,now+LEASE_MILLIS);
    }
    void close(String node,Context context) throws SQLException {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try { close(c,node,context); c.commit(); }
            catch(SQLException e) { c.rollback(); throw e; }
        }
    }
    private static void close(Connection c,String node,Context context) throws SQLException {
        update(c,"DELETE FROM axs_center_sessions_v1 WHERE flow IN (SELECT id FROM axs_center_flows_v2 WHERE node=? AND connection_id=?)",node,context.connection());
        update(c,"DELETE FROM axs_center_tickets_v1 WHERE flow IN (SELECT id FROM axs_center_flows_v2 WHERE node=? AND connection_id=?)",node,context.connection());
        update(c,"DELETE FROM axs_center_flows_v2 WHERE node=? AND connection_id=?",node,context.connection());
    }
    String bindingCode(String realm,UUID account,long qq) throws SQLException {
        String code=random();
        long now=System.currentTimeMillis();
        update("DELETE FROM axs_center_binding_codes_v1 WHERE realm=? AND (account=? OR qq=?) AND expires<=?",realm,account,qq,now);
        return update("INSERT INTO axs_center_binding_codes_v1 (hash,realm,account,qq,expires) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",hash(code),realm,account,qq,now+300_000)==1?code:null;
    }
    long bindingQq(String realm,UUID account,String code) throws SQLException {
        try(Connection c=connections.open(); PreparedStatement s=c.prepareStatement("SELECT qq FROM axs_center_binding_codes_v1 WHERE hash=? AND realm=? AND account=? AND expires>?")) {
            bind(s,hash(code),realm,account,System.currentTimeMillis());
            try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new SecurityException("Invalid binding code"); return r.getLong(1); }
        }
    }
    void clearBindingCodes(Connection c,String realm,UUID account) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_center_binding_codes_v1 WHERE realm=? AND account=?")) { bind(s,realm,account); s.executeUpdate(); }
    }
    void cleanup() throws SQLException {
        long now=System.currentTimeMillis();
        update("DELETE FROM axs_center_nonces_v2 WHERE expires<=?",now);
        update("DELETE FROM axs_center_binding_codes_v1 WHERE expires<=?",now);
        update("DELETE FROM axs_center_tickets_v1 WHERE expires<=?",now);
        update("DELETE FROM axs_center_sessions_v1 WHERE expires<=?",now);
        update("DELETE FROM axs_center_flows_v2 WHERE expires<=? AND NOT EXISTS (SELECT 1 FROM axs_center_sessions_v1 WHERE flow=axs_center_flows_v2.id)",now);
        update("DELETE FROM axs_center_commands_v1 WHERE created<?",now-86_400_000);
    }
    private static String hash(String value) {
        if(value==null || value.length()>128) throw new SecurityException("Invalid secret");
        return RpcAuthentication.digest(value.getBytes(StandardCharsets.US_ASCII));
    }
    private static String random() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
