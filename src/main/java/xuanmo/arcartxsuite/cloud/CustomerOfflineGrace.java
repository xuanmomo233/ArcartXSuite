package xuanmo.arcartxsuite.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Stores signed evidence of prior authorization, never an unwrapped module key. */
final class CustomerOfflineGrace {
    static final String FILE_NAME = "customer-offline-grace.json";
    static final String PURPOSE = "ArcartXSuite/customer-offline-grace/v1";
    static final long GRACE_MILLIS = Duration.ofDays(7).toMillis();
    private static final long MAX_CLOCK_SKEW = 60_000L;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int MAX_MODULES = 64;
    private static final int MAX_GRANT_SIZE = 32 * 1024;
    private static final int MAX_RESPONSE_SIZE = 4096;
    private static final int MAX_CACHE_SIZE = 512 * 1024;
    private static final Pattern MODULE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> KEY_RESPONSE_FIELDS = Set.of("ok", "runtimeRequestId", "versionId",
        "version", "checksum", "key", "keyBind", "signature", "offlineBinding");

    record Identity(String account, String serverCode, String serverPublicKey, String customerRelease) {}
    private record Module(String checksum, long expiresAt) {}
    private record Grant(long issuedAt, Map<String, Module> modules) {}
    private record WrappedKey(long timestamp, String body, String signature) {}
    private record Entry(Module module, String response) {}
    private record Stored(String grant, long observedAt, Map<String, WrappedKey> keys) {}

    private final Path file;
    private final LongSupplier wallClock;
    private final LongSupplier monotonicClock;
    private boolean cleared;
    private long lastGrantIssuedAt;
    private volatile long generation;

    CustomerOfflineGrace(Path suiteDataFolder) {
        this(suiteDataFolder, System::currentTimeMillis, System::nanoTime);
    }

    CustomerOfflineGrace(Path suiteDataFolder, LongSupplier wallClock, LongSupplier monotonicClock) {
        file = suiteDataFolder.toAbsolutePath().normalize().resolve(FILE_NAME);
        this.wallClock = wallClock;
        this.monotonicClock = monotonicClock;
    }

    synchronized void saveGrant(String envelope, Identity identity, RuntimeAuthorization.Verifier verifier) throws IOException {
        long now = wallClock.getAsLong();
        Grant incoming = parseGrant(envelope, identity, verifier);
        validateIssuance(incoming.issuedAt(), now);
        if (cleared && incoming.issuedAt() <= lastGrantIssuedAt) throw invalid();
        if (incoming.modules().values().stream().noneMatch(module -> module.expiresAt() > now)) throw invalid();

        Stored previous = cleared ? null : readStored();
        Grant previousGrant = previous == null ? null : matchingGrant(previous.grant(), identity, verifier);
        var keys = new LinkedHashMap<String, WrappedKey>();
        long observedAt = now;
        if (previousGrant != null) {
            if (incoming.issuedAt() < previousGrant.issuedAt()
                    || (incoming.issuedAt() == previousGrant.issuedAt() && !envelope.equals(previous.grant()))) throw invalid();
            observedAt = observedNow(previous, now);
            for (var permission : incoming.modules().entrySet()) {
                String moduleId = permission.getKey();
                WrappedKey key = previous.keys().get(moduleId);
                if (key != null && verifiedKey(key, moduleId, permission.getValue(), identity, verifier, observedAt) != null) {
                    keys.put(moduleId, key);
                }
            }
        }
        save(new Stored(envelope, observedAt, keys));
        lastGrantIssuedAt = incoming.issuedAt();
        cleared = false;
        generation++;
    }

    synchronized void saveKeyResponse(String moduleId, byte[] rawBody, String signatureB64, String signatureTs,
                                      Identity identity, RuntimeAuthorization.Verifier verifier) throws IOException {
        if (cleared) return;
        Stored stored = readStored();
        if (stored == null) return;
        Grant grant = matchingGrant(stored.grant(), identity, verifier);
        if (grant == null) return;
        Module permission = grant.modules().get(moduleId);
        long now = observedNow(stored, wallClock.getAsLong());
        if (permission == null || permission.expiresAt() <= now) return;
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_RESPONSE_SIZE) throw invalid();
        var key = new WrappedKey(integer(signatureTs), Base64.getEncoder().encodeToString(rawBody), signatureB64);
        if (verifiedKey(key, moduleId, permission, identity, verifier, now) == null) throw invalid();

        var keys = new LinkedHashMap<>(stored.keys());
        keys.put(moduleId, key);
        save(new Stored(stored.grant(), now, keys));
    }

    synchronized Session restore(Identity identity, RuntimeAuthorization.Verifier verifier) throws IOException {
        if (cleared) return null;
        Stored stored = readStored();
        if (stored == null) return null;
        Grant grant = matchingGrant(stored.grant(), identity, verifier);
        if (grant == null) return null;
        long startedAtNanos = monotonicClock.getAsLong();
        long now = observedNow(stored, wallClock.getAsLong());
        validateIssuance(grant.issuedAt(), now);
        // A signed server clock ahead of the local clock must not lengthen the grace interval.
        long startedAt = Math.max(now, grant.issuedAt());
        var entries = new LinkedHashMap<String, Entry>();
        for (var permission : grant.modules().entrySet()) {
            String moduleId = permission.getKey();
            Module module = permission.getValue();
            WrappedKey key = stored.keys().get(moduleId);
            if (key == null || module.expiresAt() <= startedAt) continue;
            String response = verifiedKey(key, moduleId, module, identity, verifier, now);
            if (response != null) entries.put(moduleId, new Entry(module, response));
        }
        if (now > stored.observedAt()) save(new Stored(stored.grant(), now, stored.keys()));
        if (entries.isEmpty()) return null;
        lastGrantIssuedAt = grant.issuedAt();
        return new Session(Map.copyOf(entries), startedAt, startedAtNanos);
    }

    synchronized void clear() throws IOException {
        cleared = true;
        generation++;
        Files.deleteIfExists(file);
    }

    synchronized void checkpoint(Session session) throws IOException {
        if (cleared || session.issuedGeneration != generation) return;
        Stored stored = readStored();
        if (stored == null) return;
        long observedAt = session.now();
        if (observedAt > stored.observedAt()) {
            save(new Stored(stored.grant(), observedAt, stored.keys()));
        }
    }

    final class Session {
        private final Map<String, Entry> entries;
        private final long startedAt;
        private final long startedAtNanos;
        private final long issuedGeneration = generation;
        private final AtomicLong observedAt;

        private Session(Map<String, Entry> entries, long startedAt, long startedAtNanos) {
            this.entries = entries;
            this.startedAt = startedAt;
            this.startedAtNanos = startedAtNanos;
            observedAt = new AtomicLong(startedAt);
        }

        Set<String> moduleIds() {
            if (issuedGeneration != generation) return Set.of();
            long now = now();
            return entries.entrySet().stream()
                .filter(entry -> entry.getValue().module().expiresAt() > now)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        String keyResponse(String moduleId, byte[] axb) {
            Entry entry = entries.get(moduleId);
            if (issuedGeneration != generation || entry == null || axb == null || entry.module().expiresAt() <= now()
                    || !entry.module().checksum().equals(RuntimeAuthorization.checksum(axb))
                    || entry.module().expiresAt() <= now() || issuedGeneration != generation) return null;
            return entry.response();
        }

        private long now() {
            long elapsedMillis = Math.max(0, monotonicClock.getAsLong() - startedAtNanos) / 1_000_000;
            return observedAt.accumulateAndGet(Math.max(wallClock.getAsLong(), startedAt + elapsedMillis), Math::max);
        }
    }

    private static Grant matchingGrant(String envelope, Identity identity, RuntimeAuthorization.Verifier verifier) {
        try {
            return parseGrant(envelope, identity, verifier);
        } catch (IOException invalid) {
            return null;
        }
    }

    private static Grant parseGrant(String envelope, Identity identity, RuntimeAuthorization.Verifier verifier) throws IOException {
        if (envelope == null || envelope.length() > MAX_GRANT_SIZE || identity == null) throw invalid();
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 2) throw invalid();
        byte[] raw = decode(parts[0], true);
        byte[] signature = decode(parts[1], true);
        JsonObject grant = parseObject(raw);
        long issuedAt = integer(grant, "issuedAt");
        if (signature.length != 64
                || !validIdentity(identity)
                || !grant.keySet().equals(Set.of("purpose", "issuer", "issuedAt", "account", "serverCode",
                    "serverPublicKey", "customerRelease", "modules"))
                || !PURPOSE.equals(text(grant, "purpose")) || !"axs-primary".equals(text(grant, "issuer"))
                || !identity.account().equals(text(grant, "account")) || !identity.serverCode().equals(text(grant, "serverCode"))
                || !identity.serverPublicKey().equals(text(grant, "serverPublicKey"))
                || !identity.customerRelease().equals(text(grant, "customerRelease"))) throw invalid();
        byte[] prefix = (PURPOSE + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(raw, 0, payload, prefix.length, raw.length);
        if (!verifies(verifier, issuedAt, payload, signature)) throw invalid();

        var entries = grant.get("modules");
        if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().isEmpty()
                || entries.getAsJsonArray().size() > MAX_MODULES) throw invalid();
        var modules = new LinkedHashMap<String, Module>();
        for (var value : entries.getAsJsonArray()) {
            if (!value.isJsonObject()) throw invalid();
            JsonObject entry = value.getAsJsonObject();
            if (!entry.keySet().equals(Set.of("moduleId", "checksum", "expiresAt"))) throw invalid();
            String moduleId = text(entry, "moduleId");
            String checksum = text(entry, "checksum");
            long expiresAt = integer(entry, "expiresAt");
            if (!MODULE_ID.matcher(moduleId).matches() || !CHECKSUM.matcher(checksum).matches()
                    || expiresAt <= issuedAt || expiresAt - issuedAt > GRACE_MILLIS
                    || modules.putIfAbsent(moduleId, new Module(checksum, expiresAt)) != null) throw invalid();
        }
        return new Grant(issuedAt, Map.copyOf(modules));
    }

    private static boolean validIdentity(Identity identity) throws IOException {
        return identity.account() != null && identity.account().matches("[0-9]{5,20}")
            && identity.serverCode() != null && identity.serverCode().matches("[A-Za-z0-9_-]{1,128}")
            && identity.serverPublicKey() != null && identity.serverPublicKey().length() == 44
            && decode(identity.serverPublicKey(), false).length == 32
            && RuntimeAuthorization.isCustomerRelease(identity.customerRelease());
    }

    private static String verifiedKey(WrappedKey key, String moduleId, Module permission, Identity identity,
                                      RuntimeAuthorization.Verifier verifier, long now) {
        try {
            validateIssuance(key.timestamp(), now);
            if (key.body().length() > ((MAX_RESPONSE_SIZE + 2) / 3) * 4
                    || key.signature() == null || key.signature().length() != 88) return null;
            byte[] body = decode(key.body(), false);
            byte[] signature = decode(key.signature(), false);
            if (body.length > MAX_RESPONSE_SIZE || signature.length != 64
                    || !verifies(verifier, key.timestamp(), body, signature)) return null;
            JsonObject response = parseObject(body);
            var ok = response.get("ok");
            var binding = response.get("offlineBinding");
            if (!KEY_RESPONSE_FIELDS.containsAll(response.keySet())
                    || ok == null || !ok.isJsonPrimitive() || !ok.getAsJsonPrimitive().isBoolean() || !ok.getAsBoolean()
                    || !"anchor-hkdf-v1".equals(text(response, "keyBind"))
                    || !identity.customerRelease().equals(text(response, "version"))
                    || !permission.checksum().equals(text(response, "checksum"))
                    || !isBase64Field(response, "key", 32) || !isBase64Field(response, "signature", 64)
                    || binding == null || !binding.isJsonObject()) return null;
            JsonObject bound = binding.getAsJsonObject();
            if (!bound.keySet().equals(Set.of("account", "serverCode", "serverPublicKey", "moduleId"))
                    || !identity.account().equals(text(bound, "account"))
                    || !identity.serverCode().equals(text(bound, "serverCode"))
                    || !identity.serverPublicKey().equals(text(bound, "serverPublicKey"))
                    || !moduleId.equals(text(bound, "moduleId"))) return null;
            return utf8(body);
        } catch (IOException invalid) {
            return null;
        }
    }

    private Stored readStored() throws IOException {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_CACHE_SIZE + 1);
        } catch (NoSuchFileException missing) {
            return null;
        }
        if (bytes.length > MAX_CACHE_SIZE) return null;
        try {
            JsonObject document = parseObject(bytes);
            var entries = document.get("keys");
            if (!document.keySet().equals(Set.of("grant", "observedAt", "keys")) || !entries.isJsonObject()
                    || entries.getAsJsonObject().size() > MAX_MODULES) return null;
            var keys = new LinkedHashMap<String, WrappedKey>();
            for (var member : entries.getAsJsonObject().entrySet()) {
                if (!MODULE_ID.matcher(member.getKey()).matches() || !member.getValue().isJsonObject()) return null;
                JsonObject entry = member.getValue().getAsJsonObject();
                if (!entry.keySet().equals(Set.of("timestamp", "body", "signature"))) return null;
                keys.put(member.getKey(), new WrappedKey(integer(entry, "timestamp"), text(entry, "body"), text(entry, "signature")));
            }
            return new Stored(text(document, "grant"), integer(document, "observedAt"), keys);
        } catch (IOException invalid) {
            return null;
        }
    }

    private void save(Stored stored) throws IOException {
        var document = new JsonObject();
        document.addProperty("grant", stored.grant());
        document.addProperty("observedAt", stored.observedAt());
        var keys = new JsonObject();
        stored.keys().forEach((moduleId, key) -> {
            var entry = new JsonObject();
            entry.addProperty("timestamp", key.timestamp());
            entry.addProperty("body", key.body());
            entry.addProperty("signature", key.signature());
            keys.add(moduleId, entry);
        });
        document.add("keys", keys);
        byte[] data = document.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_CACHE_SIZE
                || (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
            throw invalid();
        }
        Path directory = file.getParent();
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        Path temp = Files.createTempFile(directory, ".offline-grace-", ".tmp");
        try {
            try (var output = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static long observedNow(Stored stored, long now) throws IOException {
        if (stored.observedAt() - now > MAX_CLOCK_SKEW) throw invalid();
        return Math.max(stored.observedAt(), now);
    }

    private static void validateIssuance(long issuedAt, long now) throws IOException {
        if (issuedAt > now + MAX_CLOCK_SKEW) throw invalid();
    }

    private static boolean verifies(RuntimeAuthorization.Verifier verifier, long timestamp, byte[] body, byte[] signature) {
        try {
            return verifier.verify(timestamp, body, signature);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static JsonObject parseObject(byte[] bytes) throws IOException {
        try (var reader = new JsonReader(new StringReader(utf8(bytes)))) {
            reader.setStrictness(Strictness.STRICT);
            var value = JsonParser.parseReader(reader);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            return value.getAsJsonObject();
        } catch (JsonParseException invalid) {
            throw invalid();
        }
    }

    private static String utf8(byte[] data) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
    }

    private static String text(JsonObject object, String field) throws IOException {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid();
        return value.getAsString();
    }

    private static long integer(JsonObject object, String field) throws IOException {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        return integer(value.getAsString());
    }

    private static long integer(String raw) throws IOException {
        if (raw == null || !raw.matches("[1-9][0-9]{0,15}")) throw invalid();
        long value = Long.parseLong(raw);
        if (value > MAX_SAFE_INTEGER) throw invalid();
        return value;
    }

    private static boolean isBase64Field(JsonObject object, String field, int length) throws IOException {
        String encoded = text(object, field);
        return encoded.length() == ((length + 2) / 3) * 4 && decode(encoded, false).length == length;
    }

    private static byte[] decode(String encoded, boolean url) throws IOException {
        try {
            byte[] bytes = (url ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(encoded);
            var encoder = url ? Base64.getUrlEncoder().withoutPadding() : Base64.getEncoder();
            if (!encoder.encodeToString(bytes).equals(encoded)) throw invalid();
            return bytes;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    private static IOException invalid() { return new IOException("Invalid customer offline authorization cache"); }
}
