package xuanmo.arcartxsuite.auth.center;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import xuanmo.arcartxsuite.auth.protocol.MinecraftSessionProtocol;

/** Launcher credentials and accepted Minecraft handshakes are short-lived and single-use. */
final class MinecraftSessions {
    private static final int CAPACITY = 4096;
    private static final long JOIN_MILLIS = 30_000;
    private final URI sessionApi;
    private final Authority authority;
    private final LongSupplier clock;
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<String, Joined> joins = new HashMap<>();

    @FunctionalInterface
    interface Authority {
        boolean current(GameExternalStore.MinecraftIdentity identity) throws SQLException;
    }

    record Credentials(String sessionApi, String profileId, String name, String accessToken, int expiresIn) {
        @Override public String toString() { return "Minecraft credentials for " + name; }
    }
    private record Ticket(GameExternalStore.MinecraftIdentity identity, long expires) {}
    private record Joined(GameExternalStore.MinecraftIdentity identity, String source, long expires, boolean consumed) {}

    MinecraftSessions(URI sessionApi, Authority authority, LongSupplier clock) {
        this.sessionApi = sessionApi;
        this.authority = authority;
        this.clock = clock;
    }

    synchronized Credentials issue(GameExternalStore.MinecraftIdentity identity) {
        long now = clock.getAsLong();
        tickets.values().removeIf(ticket -> ticket.expires() <= now);
        if (tickets.size() >= CAPACITY) throw new IllegalStateException("Minecraft session credential capacity reached");
        String token = OidcLogin.random();
        tickets.put(GameExternalStore.hash(token), new Ticket(identity, now + 1000L * MinecraftSessionProtocol.TOKEN_SECONDS));
        return new Credentials(sessionApi.toString(), MinecraftSessionProtocol.profileId(identity.profile().id()),
            identity.profile().name(), token, MinecraftSessionProtocol.TOKEN_SECONDS);
    }

    synchronized boolean join(String accessToken, String profile, String serverHash, String source) throws SQLException {
        if (accessToken == null || !accessToken.matches("[A-Za-z0-9_-]{43}") ||
                !MinecraftSessionProtocol.isServerHash(serverHash) || source == null) return false;
        String key = GameExternalStore.hash(accessToken);
        long now = clock.getAsLong();
        Ticket ticket = tickets.get(key);
        if (ticket == null || ticket.expires() <= now ||
                !MinecraftSessionProtocol.profileId(ticket.identity().profile().id()).equalsIgnoreCase(profile)) return false;
        joins.values().removeIf(join -> join.expires() <= now);
        if (joins.containsKey(serverHash) || joins.size() >= CAPACITY || !authority.current(ticket.identity())) return false;
        long acceptedAt = clock.getAsLong();
        if (ticket.expires() <= acceptedAt) return false;
        tickets.remove(key);
        joins.put(serverHash, new Joined(ticket.identity(), source, Math.min(ticket.expires(), acceptedAt + JOIN_MILLIS), false));
        return true;
    }

    synchronized GameExternalStore.MinecraftProfile hasJoined(String name, String serverHash, String clientIp) throws SQLException {
        if (!MinecraftSessionProtocol.isCenterName(name) || !MinecraftSessionProtocol.isServerHash(serverHash)) return null;
        Joined join = joins.get(serverHash);
        if (join == null || join.consumed() || join.expires() <= clock.getAsLong() ||
                !join.identity().profile().name().equalsIgnoreCase(name) ||
                clientIp != null && !join.source().equals(clientIp) ||
                !authority.current(join.identity()) || join.expires() <= clock.getAsLong()) return null;
        // Retain a tombstone until expiry so a fresh token cannot re-arm this handshake.
        joins.put(serverHash, new Joined(null, "", join.expires(), true));
        return join.identity().profile();
    }
}
