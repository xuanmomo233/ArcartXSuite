package xuanmo.arcartxsuite.auth.engine;

import java.sql.*;
import java.util.Locale;
import java.util.UUID;


/** New schema; no reads, upgrades, or password fallbacks against LoginView's historical tables. */
public final class JdbcAuthStore {
    public record Account(UUID id, UUID profile, String hash, long version, int failures, long blockedUntil, boolean firstAdmitted, String provider) {}
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    public record Binding(UUID accountId,UUID profileId,String profileName,long qqId,long boundAt) {}
    private final Connections storage;
    private final String realm;
    public JdbcAuthStore(Connections storage, String realm) { this.storage = storage; this.realm = realm; }
    public void initialize() throws SQLException {
        try (Connection c = storage.open(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_auth_accounts_v1 ("
                + "account_id VARCHAR(36) PRIMARY KEY,realm_id VARCHAR(64) NOT NULL,lower_name VARCHAR(64) NOT NULL,"
                + "profile_id VARCHAR(36) NOT NULL,password_hash VARCHAR(256) NOT NULL,identity_provider VARCHAR(64) NOT NULL,security_version BIGINT NOT NULL DEFAULT 1,"
                + "failures INTEGER NOT NULL DEFAULT 0,blocked_until BIGINT NOT NULL DEFAULT 0,"
                + "first_admitted INTEGER NOT NULL DEFAULT 0,UNIQUE(realm_id,lower_name),UNIQUE(realm_id,profile_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_auth_presence_v1 (realm_id VARCHAR(64) NOT NULL,"
                + "account_id VARCHAR(36) NOT NULL,business_date VARCHAR(10) NOT NULL,delivered INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(realm_id,account_id,business_date))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_auth_bindings_v1 (realm_id VARCHAR(64) NOT NULL,"
                + "account_id VARCHAR(36) NOT NULL,profile_id VARCHAR(36) NOT NULL,profile_name VARCHAR(64) NOT NULL,"
                + "qq_id BIGINT NOT NULL,bound_at BIGINT NOT NULL,PRIMARY KEY(realm_id,account_id),UNIQUE(realm_id,qq_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_auth_terms_v1 (realm_id VARCHAR(64) NOT NULL,account_id VARCHAR(36) NOT NULL,"
                + "terms_version VARCHAR(64) NOT NULL,accepted_at BIGINT NOT NULL,PRIMARY KEY(realm_id,account_id,terms_version))");
        }
    }
    public Account find(String name) throws SQLException { return query("lower_name=?",java.util.List.of(name.toLowerCase(Locale.ROOT))); }
    public Account findById(UUID account) throws SQLException { return query("account_id=?",java.util.List.of(account.toString())); }
    public Account findByProfile(UUID profile) throws SQLException { return query("profile_id=?",java.util.List.of(profile.toString())); }
    public Account findIdentity(UUID profile,String provider) throws SQLException {
        return query("profile_id=? AND identity_provider=?",java.util.List.of(profile.toString(),provider));
    }
    private Account query(String predicate,java.util.List<String> values) throws SQLException {
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement(
            "SELECT * FROM axs_auth_accounts_v1 WHERE realm_id=? AND "+predicate)) {
            s.setString(1,realm);
            for(int i=0;i<values.size();i++) s.setString(i+2,values.get(i));
            try(ResultSet r=s.executeQuery()) {
                if(!r.next()) return null;
                return new Account(UUID.fromString(r.getString("account_id")),UUID.fromString(r.getString("profile_id")),
                    r.getString("password_hash"),r.getLong("security_version"),r.getInt("failures"),r.getLong("blocked_until"),
                    r.getInt("first_admitted")==2,r.getString("identity_provider"));
            }
        }
    }
    public void renameVerifiedProfile(Account account,String name) throws SQLException {
        try(Connection c=storage.open()) {
            boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try(PreparedStatement s=c.prepareStatement("UPDATE axs_auth_accounts_v1 SET lower_name=? WHERE realm_id=? AND account_id=? AND security_version=?")) {
                    s.setString(1,name.toLowerCase(Locale.ROOT)); s.setString(2,realm); s.setString(3,account.id().toString()); s.setLong(4,account.version());
                    if(s.executeUpdate()!=1) throw new SQLException("Credentials changed");
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE axs_auth_bindings_v1 SET profile_name=? WHERE realm_id=? AND account_id=?")) {
                    s.setString(1,name); s.setString(2,realm); s.setString(3,account.id().toString()); s.executeUpdate();
                }
                c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(auto); }
        }
    }
    public void recordTerms(Account account,String version) throws SQLException {
        try(Connection c=storage.open()) { recordTerms(c,account,version); }
    }
    public void recordTerms(Connection c,Account account,String version) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(
            "INSERT INTO axs_auth_terms_v1 (realm_id,account_id,terms_version,accepted_at) SELECT ?,?,?,? WHERE NOT EXISTS"
                + " (SELECT 1 FROM axs_auth_terms_v1 WHERE realm_id=? AND account_id=? AND terms_version=?)"
                + " AND EXISTS (SELECT 1 FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=? AND security_version=?)")) {
            s.setString(1,realm); s.setString(2,account.id().toString()); s.setString(3,version); s.setLong(4,System.currentTimeMillis());
            s.setString(5,realm); s.setString(6,account.id().toString()); s.setString(7,version);
            s.setString(8,realm); s.setString(9,account.id().toString()); s.setLong(10,account.version());
            if(s.executeUpdate()==0) {
                try(PreparedStatement check=c.prepareStatement("SELECT security_version FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=?")) {
                    check.setString(1,realm); check.setString(2,account.id().toString());
                    try(ResultSet row=check.executeQuery()) {
                        if(!row.next() || row.getLong(1)!=account.version()) throw new SQLException("Credentials changed");
                    }
                }
            }
        }
    }
    public java.util.List<Binding> bindings() throws SQLException {
        var result=new java.util.ArrayList<Binding>();
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement("SELECT * FROM axs_auth_bindings_v1 WHERE realm_id=?")) {
            s.setString(1,realm);
            try(ResultSet r=s.executeQuery()) {
                while(r.next()) result.add(new Binding(
                    UUID.fromString(r.getString("account_id")),UUID.fromString(r.getString("profile_id")),
                    r.getString("profile_name"),r.getLong("qq_id"),r.getLong("bound_at")));
            }
        }
        return java.util.List.copyOf(result);
    }
    public boolean isBound(UUID account) throws SQLException {
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement(
            "SELECT 1 FROM axs_auth_bindings_v1 WHERE realm_id=? AND account_id=?")) {
            s.setString(1,realm); s.setString(2,account.toString());
            try(ResultSet r=s.executeQuery()) { return r.next(); }
        }
    }
    public void bind(Account account,String name,long qq) throws SQLException {
        try(Connection c=storage.open()) { bind(c,account,name,qq); }
    }
    public void bind(Connection c,Account account,String name,long qq) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(
            "INSERT INTO axs_auth_bindings_v1 (realm_id,account_id,profile_id,profile_name,qq_id,bound_at) SELECT ?,?,?,?,?,?"
                + " WHERE EXISTS (SELECT 1 FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=? AND security_version=?)")) {
            s.setString(1,realm); s.setString(2,account.id().toString()); s.setString(3,account.profile().toString());
            s.setString(4,name); s.setLong(5,qq); s.setLong(6,System.currentTimeMillis());
            s.setString(7,realm); s.setString(8,account.id().toString()); s.setLong(9,account.version());
            if(s.executeUpdate()!=1) throw new SQLException("Credentials changed during binding");
        }
    }
    public void unbind(UUID account) throws SQLException {
        try(Connection c=storage.open()) {
            boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                unbind(c,account);
                c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(auto); }
        }
    }
    public long unbind(Connection c,UUID account) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_auth_bindings_v1 WHERE realm_id=? AND account_id=?")) {
            s.setString(1,realm); s.setString(2,account.toString()); s.executeUpdate();
        }
        return invalidate(c,account);
    }
    public Account create(String name, UUID profile, String hash) throws SQLException {
        return createIdentity(name,profile,hash,"local-password");
    }
    public Account createIdentity(String name,UUID profile,String hash,String provider) throws SQLException {
        try(Connection c=storage.open()) { return createIdentity(c,name,profile,hash,provider); }
    }
    public Account createIdentity(Connection c,String name,UUID profile,String hash,String provider) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement s = c.prepareStatement(
            "INSERT INTO axs_auth_accounts_v1 (account_id,realm_id,lower_name,profile_id,password_hash,identity_provider) VALUES (?,?,?,?,?,?)")) {
            s.setString(1,id.toString()); s.setString(2,realm); s.setString(3,name.toLowerCase(Locale.ROOT));
            s.setString(4,profile.toString()); s.setString(5,hash); s.setString(6,provider); s.executeUpdate();
        }
        return new Account(id, profile, hash, 1, 0, 0, false, provider);
    }
    public boolean acceptedTerms(UUID account,String version) throws SQLException {
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement(
            "SELECT 1 FROM axs_auth_terms_v1 WHERE realm_id=? AND account_id=? AND terms_version=?")) {
            s.setString(1,realm); s.setString(2,account.toString()); s.setString(3,version);
            try(ResultSet r=s.executeQuery()) { return r.next(); }
        }
    }
    public void failure(Account account, long now, int maxAttempts, long cooldown) throws SQLException {
        try (Connection c = storage.open(); PreparedStatement s = c.prepareStatement(
            "UPDATE axs_auth_accounts_v1 SET failures=?,blocked_until=? WHERE realm_id=? AND account_id=? AND security_version=?")) {
            int attempts = account.blockedUntil() > 0 && account.blockedUntil() <= now ? 1 : account.failures()+1;
            s.setInt(1,attempts); s.setLong(2, attempts >= maxAttempts ? now+cooldown : 0);
            s.setString(3,realm); s.setString(4,account.id().toString()); s.setLong(5,account.version()); s.executeUpdate();
        }
    }
    public boolean admit(Account account) throws SQLException {
        try(Connection c=storage.open()) { return admit(c,account); }
    }
    /** Caller-owned transaction, used to commit ticket redemption and admission facts together. */
    public boolean admit(Connection c,Account account) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
            "UPDATE axs_auth_accounts_v1 SET failures=0,blocked_until=0,first_admitted=CASE WHEN first_admitted=0 THEN 1 ELSE first_admitted END"
                + " WHERE realm_id=? AND account_id=? AND security_version=?")) {
            s.setString(1,realm); s.setString(2,account.id().toString()); s.setLong(3,account.version());
            return s.executeUpdate()==1;
        }
    }
    public void acknowledgeFirst(Account account) throws SQLException {
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement(
            "UPDATE axs_auth_accounts_v1 SET first_admitted=2 WHERE realm_id=? AND account_id=? AND first_admitted=1 AND security_version=?")) {
            s.setString(1,realm); s.setString(2,account.id().toString()); s.setLong(3,account.version()); s.executeUpdate();
        }
    }
    public boolean pendingDay(UUID account,String date) throws SQLException {
        try(Connection c=storage.open()) { return pendingDay(c,account,date); }
    }
    public boolean pendingDay(Connection c,UUID account,String date) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO axs_auth_presence_v1 (realm_id,account_id,business_date) VALUES (?,?,?)")) {
            s.setString(1,realm); s.setString(2,account.toString()); s.setString(3,date); s.executeUpdate();
        } catch(SQLException collision) {
            try(PreparedStatement s=c.prepareStatement("SELECT delivered FROM axs_auth_presence_v1 WHERE realm_id=? AND account_id=? AND business_date=?")) {
                s.setString(1,realm); s.setString(2,account.toString()); s.setString(3,date);
                try(ResultSet r=s.executeQuery()) { if(r.next()) return r.getInt(1)==0; }
            }
            throw collision;
        }
        return true;
    }
    public void acknowledgeDay(UUID account,String date) throws SQLException {
        try(Connection c=storage.open(); PreparedStatement s=c.prepareStatement(
            "UPDATE axs_auth_presence_v1 SET delivered=1 WHERE realm_id=? AND account_id=? AND business_date=?")) {
            s.setString(1,realm); s.setString(2,account.toString()); s.setString(3,date); s.executeUpdate();
        }
    }
    public boolean changePassword(Account account, String hash) throws SQLException {
        try(Connection c=storage.open()) { return changePassword(c,account,hash); }
    }
    public boolean changePassword(Connection c,Account account,String hash) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
            "UPDATE axs_auth_accounts_v1 SET password_hash=?,security_version=security_version+1,failures=0,blocked_until=0"
                + " WHERE realm_id=? AND account_id=? AND security_version=?")) {
            s.setString(1,hash); s.setString(2,realm); s.setString(3,account.id().toString()); s.setLong(4,account.version());
            return s.executeUpdate()==1;
        }
    }
    public long invalidate(UUID account) throws SQLException {
        try(Connection c=storage.open()) { return invalidate(c,account); }
    }
    public long invalidate(Connection c,UUID account) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE axs_auth_accounts_v1 SET security_version=security_version+1 WHERE realm_id=? AND account_id=?")) {
            s.setString(1,realm); s.setString(2,account.toString());
            if(s.executeUpdate()!=1) throw new SQLException("Account not found");
        }
        try(PreparedStatement s=c.prepareStatement("SELECT security_version FROM axs_auth_accounts_v1 WHERE realm_id=? AND account_id=?")) {
            s.setString(1,realm); s.setString(2,account.toString());
            try(ResultSet r=s.executeQuery()) { if(r.next()) return r.getLong(1); }
        }
        throw new SQLException("Account disappeared during revocation");
    }
}
