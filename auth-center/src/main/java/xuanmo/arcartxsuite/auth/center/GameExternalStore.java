package xuanmo.arcartxsuite.auth.center;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;
import xuanmo.arcartxsuite.auth.protocol.RpcAuthentication;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Only the signed node can read or consume its connection request. Browser clients see a short pairing code. */
final class GameExternalStore {
    record Pairing(UUID id, Context context, UUID account, long version, boolean link, long expires,
                   String status, OidcLogin.Identity identity) {}
    record Ownership(UUID account, long version) {}
    record MinecraftProfile(UUID id, String name) {}
    record MinecraftAlias(UUID account, MinecraftProfile profile) {}
    record MinecraftIdentity(String realm, OidcLogin.Identity identity, Ownership owner, MinecraftProfile profile) {}

    private final JdbcAuthStore.Connections connections;

    GameExternalStore(JdbcAuthStore.Connections connections) throws SQLException {
        this.connections = connections;
        try (var c = connections.open(); var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_external_pairs_v1 (id TEXT PRIMARY KEY,node TEXT NOT NULL,connection_id TEXT NOT NULL,context TEXT NOT NULL,realm TEXT NOT NULL,code_hash TEXT NOT NULL UNIQUE,account TEXT,version BIGINT NOT NULL,link INTEGER NOT NULL,expires BIGINT NOT NULL,status TEXT NOT NULL,issuer TEXT,subject TEXT,UNIQUE(node,connection_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS axs_center_external_identities_v1 (realm TEXT NOT NULL,issuer TEXT NOT NULL,subject TEXT NOT NULL,account TEXT NOT NULL,PRIMARY KEY(realm,issuer,subject),UNIQUE(realm,account))");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_center_minecraft_profiles_v1 (
                    realm TEXT NOT NULL, issuer TEXT NOT NULL, subject TEXT NOT NULL, account TEXT NOT NULL,
                    profile TEXT NOT NULL UNIQUE, name TEXT COLLATE NOCASE NOT NULL UNIQUE,
                    PRIMARY KEY(realm,issuer,subject))
                """);
        }
    }

    Pairing create(CenterConfiguration.Node node, Context context, JdbcAuthStore.Account account, boolean link, long expires) throws SQLException {
        try (var c = connections.open()) {
            CenterLedger.update(c,"DELETE FROM axs_center_external_pairs_v1 WHERE expires<=?",System.currentTimeMillis());
            try (var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM axs_center_external_pairs_v1")) {
                if (rows.next() && rows.getInt(1) >= 4096) throw new IllegalStateException("External pairing capacity reached");
            }
            UUID id = UUID.randomUUID();
            CenterLedger.update(c,"INSERT INTO axs_center_external_pairs_v1 (id,node,connection_id,context,realm,code_hash,account,version,link,expires,status) VALUES (?,?,?,?,?,?,?,?,?,?,'pending')",
                id,node.id(),context.connection(),JSON.toJson(context),context.realm(),hash(code(node,id)),account==null?null:account.id(),
                account==null?0:account.version(),link?1:0,expires);
            return new Pairing(id,context,account==null?null:account.id(),account==null?0:account.version(),link,expires,"pending",null);
        }
    }
    void prune() throws SQLException {
        try (var c=connections.open()) {
            CenterLedger.update(c,"DELETE FROM axs_center_external_pairs_v1 WHERE expires<=?",System.currentTimeMillis());
        }
    }

    static String code(CenterConfiguration.Node node, UUID id) {
        return hash(RpcAuthentication.sign(node.key(),"AXS-GAME-PAIR-V1\n"+id)).substring(0,16).toUpperCase(java.util.Locale.ROOT);
    }
    static String confirmation(Pairing pairing, OidcLogin.Identity identity) {
        return hash(pairing.id()+"\n"+identity.issuer()+"\n"+identity.subject()).substring(0,8).toUpperCase(java.util.Locale.ROOT);
    }

    Pairing byConnection(String node, Context context) throws SQLException {
        return find("SELECT * FROM axs_center_external_pairs_v1 WHERE node=? AND connection_id=? AND context=? AND expires>?",
            node,context.connection().toString(),JSON.toJson(context),System.currentTimeMillis());
    }

    Pairing byCode(String realm, String code) throws SQLException {
        if (!code.matches("[0-9A-F]{16}")) return null;
        return find("SELECT * FROM axs_center_external_pairs_v1 WHERE realm=? AND code_hash=? AND expires>? AND status='pending'",realm,hash(code),System.currentTimeMillis());
    }

    private Pairing find(String sql, Object... values) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(sql)) {
            for (int i=0; i<values.length; i++) s.setObject(i+1,values[i]);
            try (var rows = s.executeQuery()) {
                if (!rows.next()) return null;
                String account = rows.getString("account"), issuer = rows.getString("issuer");
                return new Pairing(UUID.fromString(rows.getString("id")),JSON.fromJson(rows.getString("context"),Context.class),
                    account==null?null:UUID.fromString(account),rows.getLong("version"),rows.getInt("link")==1,rows.getLong("expires"),
                    rows.getString("status"),issuer==null?null:new OidcLogin.Identity(issuer,rows.getString("subject")));
            }
        }
    }

    boolean decide(Pairing pairing, OidcLogin.Identity identity, boolean approved) throws SQLException {
        try (var c = connections.open()) {
            return CenterLedger.update(c,"UPDATE axs_center_external_pairs_v1 SET status=?,issuer=?,subject=? WHERE id=? AND expires>? AND status='pending'",
                approved?"approved":"denied",approved?identity.issuer():null,approved?identity.subject():null,pairing.id(),System.currentTimeMillis())==1;
        }
    }

    void cancel(String node, Context context) throws SQLException {
        try (var c = connections.open()) {
            CenterLedger.update(c,"DELETE FROM axs_center_external_pairs_v1 WHERE node=? AND connection_id=? AND context=?",
                node,context.connection(),JSON.toJson(context));
        }
    }

    void consume(Connection c, Pairing pairing) throws SQLException {
        if (CenterLedger.update(c,"DELETE FROM axs_center_external_pairs_v1 WHERE id=? AND status='approved' AND expires>?",
                pairing.id(),System.currentTimeMillis())!=1) throw new SecurityException("Pairing expired or consumed");
    }

    Ownership ownership(String realm, OidcLogin.Identity identity) throws SQLException {
        try (var c = connections.open()) { return ownership(c, realm, identity); }
    }

    Ownership ownership(Connection c, String realm, OidcLogin.Identity identity) throws SQLException {
        try (var s = c.prepareStatement("SELECT a.account_id,a.security_version FROM axs_center_external_identities_v1 i JOIN axs_auth_accounts_v1 a ON a.realm_id=i.realm AND a.account_id=i.account WHERE i.realm=? AND i.issuer=? AND i.subject=?")) {
            s.setString(1,realm); s.setString(2,identity.issuer()); s.setString(3,identity.subject());
            try (var rows = s.executeQuery()) { return rows.next()?new Ownership(UUID.fromString(rows.getString(1)),rows.getLong(2)):null; }
        }
    }

    MinecraftAlias minecraftAlias(Connection c, String realm, OidcLogin.Identity identity) throws SQLException {
        try (var s = c.prepareStatement("SELECT account,profile,name FROM axs_center_minecraft_profiles_v1 WHERE realm=? AND issuer=? AND subject=?")) {
            s.setString(1,realm); s.setString(2,identity.issuer()); s.setString(3,identity.subject());
            try (var rows = s.executeQuery()) {
                return rows.next() ? new MinecraftAlias(UUID.fromString(rows.getString(1)),
                    new MinecraftProfile(UUID.fromString(rows.getString(2)),rows.getString(3))) : null;
            }
        }
    }

    void registerMinecraftAlias(Connection c, String realm, OidcLogin.Identity identity, UUID account, MinecraftProfile profile) throws SQLException {
        if (CenterLedger.update(c,"INSERT INTO axs_center_minecraft_profiles_v1 (realm,issuer,subject,account,profile,name) VALUES (?,?,?,?,?,?)",
                realm,identity.issuer(),identity.subject(),account,profile.id(),profile.name()) != 1)
            throw new SecurityException("Minecraft profile registration failed");
    }

    MinecraftProfile minecraftProfile(UUID id) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement("SELECT name FROM axs_center_minecraft_profiles_v1 WHERE profile=?")) {
            s.setString(1,id.toString());
            try (var rows = s.executeQuery()) { return rows.next() ? new MinecraftProfile(id,rows.getString(1)) : null; }
        }
    }

    void link(Connection c, String realm, OidcLogin.Identity identity, UUID account) throws SQLException {
        if (CenterLedger.update(c,"INSERT INTO axs_center_external_identities_v1 (realm,issuer,subject,account) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                realm,identity.issuer(),identity.subject(),account)!=1) throw new SecurityException("Identity already linked");
    }

    static String hash(String value) { return RpcAuthentication.digest(value.getBytes(StandardCharsets.UTF_8)); }
}
