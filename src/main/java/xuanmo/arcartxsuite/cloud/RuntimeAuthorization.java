package xuanmo.arcartxsuite.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** The boot private key is deliberately never persisted with the installation keystore. */
final class RuntimeAuthorization {
    static final String PURPOSE = "ArcartXSuite/runtime-lease/v1";
    static final long LEASE_MILLIS = 180_000L;
    static final long MAX_LEASE_MILLIS = 1_800_000L;
    private static final Pattern CUSTOMER_RELEASE = Pattern.compile("[A-Za-z0-9._-]{1,40}-local\\.([0-9a-f]{40})-suite\\.0\\.0-ci\\.([0-9a-f]{7})");
    private final KeyPair instance;
    private final String instancePublicKey;
    private final AtomicLong lastTimestamp = new AtomicLong();
    private volatile Lease lease;

    @FunctionalInterface interface Verifier {
        boolean verify(long timestamp, byte[] body, byte[] signature);
    }
    private record Lease(String envelope, String id, String issuer, long deadlineNanos, long renewAfterNanos,
                         java.util.Map<String, Long> moduleDeadlines, String customerRelease) {}
    record KeyProof(String envelope, String signature, long timestamp, String requestId, String checksum) {}
    record ReleaseProof(String envelope, String signature, long timestamp, String requestId, String issuer) {}

    RuntimeAuthorization() {
        try { instance = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
        catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("Ed25519 unavailable", failure); }
        byte[] encoded = instance.getPublic().getEncoded();
        instancePublicKey = Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }
    String instancePublicKey() { return instancePublicKey; }
    long timestamp() { return lastTimestamp.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1)); }
    static String bindingMessage(String qq, String publicKey, String anchor, String fingerprint, String name, long at, String id) {
        return String.join("\n", "ArcartXSuite/bind/v1", qq, publicKey, anchor, fingerprint,
            Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8)), Long.toString(at), id);
    }
    String leaseRequestMessage(String serverCode, String nonce, long at, String id) {
        return leaseRequestMessage(serverCode, nonce, at, id, null);
    }
    String leaseRequestMessage(String serverCode, String nonce, long at, String id, String customerRelease) {
        String message = String.join("\n", "ArcartXSuite/runtime-lease-request/v1", serverCode,
            instancePublicKey, nonce, Long.toString(at), id);
        return customerRelease == null ? message : message + "\n" + customerRelease;
    }
    static boolean isCustomerRelease(String release) {
        if (release == null) return false;
        var match = CUSTOMER_RELEASE.matcher(release);
        return match.matches() && match.group(1).startsWith(match.group(2));
    }
    String signInstance(String message) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(instance.getPrivate());
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("Cannot prove runtime identity", failure); }
    }
    static String keyMessage(String serverCode, String moduleId, String version, String checksum, String leaseId, long at, String id) {
        return String.join("\n", "ArcartXSuite/module-key/v1", serverCode, moduleId, version, checksum, leaseId, Long.toString(at), id);
    }
    void accept(String envelope, String serverCode, String installationPublicKey, Verifier verifier) {
        long nanos = System.nanoTime();
        accept(envelope, serverCode, installationPublicKey, verifier, System.currentTimeMillis(), nanos, nanos);
    }
    void accept(String envelope, String serverCode, String installationPublicKey, Verifier verifier, long now, long nanos) {
        accept(envelope, serverCode, installationPublicKey, verifier, now, nanos, nanos);
    }
    void accept(String envelope, String serverCode, String installationPublicKey, Verifier verifier, long now, long nanos, long requestStarted) {
        try {
            if (envelope == null || envelope.length() > 4096) throw new IllegalArgumentException("Invalid runtime lease");
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]+") || !parts[1].matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException("Invalid runtime lease");
            byte[] raw = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            JsonObject body = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            long issuedAt = integer(body, "issuedAt");
            long expiresAt = integer(body, "expiresAt");
            String id = body.get("leaseId").getAsString();
            String issuer = body.get("issuer").getAsString();
            if (!PURPOSE.equals(body.get("purpose").getAsString()) || !java.util.Set.of("axs-primary", "axs-standby").contains(issuer)
                || !serverCode.equals(body.get("serverCode").getAsString())
                || !installationPublicKey.equals(body.get("serverPublicKey").getAsString())
                || !instancePublicKey.equals(body.get("instancePublicKey").getAsString())
                || !id.matches("[A-Za-z0-9_-]{16,128}") || issuedAt > now + 60_000L || expiresAt <= now
                || expiresAt <= issuedAt || expiresAt - issuedAt > MAX_LEASE_MILLIS
                || signature.length != 64 || !verifier.verify(issuedAt, (PURPOSE + "\n" + new String(raw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8), signature))
                throw new IllegalArgumentException("Runtime lease rejected");
            String customerRelease = null;
            if (body.has("customerRelease")) {
                var claim = body.getAsJsonPrimitive("customerRelease");
                if (claim == null || !claim.isString() || !isCustomerRelease(claim.getAsString()))
                    throw new IllegalArgumentException("Invalid customer release");
                customerRelease = claim.getAsString();
            }
            var moduleIds = new java.util.HashSet<String>();
            var permissions = body.getAsJsonArray("moduleIds");
            if (permissions == null || permissions.size() > 128) throw new IllegalArgumentException("Invalid runtime permissions");
            for (var permission : permissions) {
                if (!permission.isJsonPrimitive() || !permission.getAsJsonPrimitive().isString()
                    || !permission.getAsString().matches("[a-z][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid runtime permissions");
                moduleIds.add(permission.getAsString());
            }
            // Anchor expiry before issuance: local clock skew cannot extend a primary lease.
            long elapsed = Math.max(0L, (nanos - requestStarted) / 1_000_000L);
            var moduleDeadlines = new java.util.HashMap<String, Long>();
            JsonObject moduleExpiries = body.getAsJsonObject("moduleExpiresAt");
            if (moduleExpiries == null) throw new IllegalArgumentException("Missing module expiry bounds");
            for (String moduleId : moduleIds) {
                long expiry = integer(moduleExpiries, moduleId);
                if (expiry <= issuedAt || expiry > expiresAt) throw new IllegalArgumentException("Invalid module expiry bound");
                long remainingForModule = Math.min(expiry - now, expiry - issuedAt - elapsed);
                if (remainingForModule > 0) moduleDeadlines.put(moduleId, nanos + remainingForModule * 1_000_000L);
            }
            long remaining = Math.min(expiresAt - now, expiresAt - issuedAt - elapsed);
            if (remaining <= 0) throw new IllegalArgumentException("Runtime lease expired in transit");
            long renewAfter = requestStarted + Math.min(120_000L, Math.max(0L, expiresAt - issuedAt - 60_000L)) * 1_000_000L;
            lease = new Lease(envelope, id, issuer, nanos + remaining * 1_000_000L, renewAfter,
                java.util.Map.copyOf(moduleDeadlines), customerRelease);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Runtime lease rejected", failure);
        }
    }
    private static long integer(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        if (value == null || !value.isNumber() || !value.getAsString().matches("[0-9]{1,16}"))
            throw new IllegalArgumentException("Invalid lease timestamp");
        long number = value.getAsLong();
        if (number > 9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid lease timestamp");
        return number;
    }
    boolean valid() { return valid(System.nanoTime()); }
    boolean valid(long nanos) { Lease current = lease; return current != null && current.deadlineNanos() - nanos > 0; }
    boolean hasLease() { return lease != null; }
    String currentEnvelope() { Lease current = lease; return current != null && valid() ? current.envelope() : null; }
    boolean permits(String moduleId) {
        Lease current = lease;
        long nanos = System.nanoTime();
        Long deadline = current == null ? null : current.moduleDeadlines().get(moduleId);
        return current != null && current.deadlineNanos() - nanos > 0 && deadline != null && deadline - nanos > 0;
    }
    boolean permitsCustomerFeatures(String release, String moduleId, long nanos) {
        Lease current = lease;
        if (current == null || release == null || !release.equals(current.customerRelease())
            || current.deadlineNanos() - nanos <= 0) return false;
        if (moduleId == null) return current.moduleDeadlines().values().stream().anyMatch(deadline -> deadline - nanos > 0);
        Long deadline = current.moduleDeadlines().get(moduleId);
        return deadline != null && deadline - nanos > 0;
    }
    java.util.Set<String> permittedModules() { return permittedModules(System.nanoTime()); }
    java.util.Set<String> permittedModules(long nanos) {
        Lease current = lease;
        if (current == null || current.deadlineNanos() - nanos <= 0) return java.util.Set.of();
        return current.moduleDeadlines().entrySet().stream().filter(entry -> entry.getValue() - nanos > 0)
            .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    boolean needsRenewal() { Lease current = lease; return current == null || current.renewAfterNanos() - System.nanoTime() <= 0; }
    void invalidate() { lease = null; }
    KeyProof keyProof(String serverCode, String moduleId, String version, byte[] axb) {
        Lease current = lease;
        if (current == null || current.deadlineNanos() - System.nanoTime() <= 0) throw new IllegalStateException("Runtime lease expired");
        Long moduleDeadline = current.moduleDeadlines().get(moduleId);
        if (moduleDeadline == null || moduleDeadline - System.nanoTime() <= 0)
            throw new IllegalStateException("Module not authorized");
        long at = timestamp();
        String id = UUID.randomUUID().toString();
        String checksum = checksum(axb);
        return new KeyProof(current.envelope(), signInstance(keyMessage(serverCode, moduleId, version, checksum, current.id(), at, id)),
            at, id, checksum);
    }
    static String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    ReleaseProof releaseProof(String serverCode) {
        Lease current = lease;
        if (current == null || current.deadlineNanos() - System.nanoTime() <= 0) return null;
        long at = timestamp();
        String id = UUID.randomUUID().toString();
        String message = String.join("\n", "ArcartXSuite/runtime-release/v1", serverCode, current.id(), Long.toString(at), id);
        return new ReleaseProof(current.envelope(), signInstance(message), at, id, current.issuer());
    }
    static String normalizeChecksum(String value) {
        if (value == null) throw new IllegalArgumentException("Missing checksum");
        if (value.matches("[a-fA-F0-9]{64}")) return value.toLowerCase(java.util.Locale.ROOT);
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))
            throw new IllegalArgumentException("Invalid checksum");
        return HexFormat.of().formatHex(bytes);
    }
}
