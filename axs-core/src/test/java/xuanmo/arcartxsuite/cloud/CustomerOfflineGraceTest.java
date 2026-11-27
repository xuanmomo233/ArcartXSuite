package xuanmo.arcartxsuite.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CustomerOfflineGraceTest {
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long NOW = 1_800_000_000_000L;
    private static final String RELEASE = "3.3.7-local." + "a".repeat(40) + "-suite.0.0-ci.aaaaaaa";
    private static final String PUBLIC_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final CustomerOfflineGrace.Identity IDENTITY =
        new CustomerOfflineGrace.Identity("123456789", "SERVER_A", PUBLIC_KEY, RELEASE);
    private static final byte[] AXB = {1, 2, 3};
    private static final String DIGEST = RuntimeAuthorization.checksum(AXB);
    private static final String WRAPPED_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @TempDir Path folder;
    private final AtomicLong wall = new AtomicLong(NOW);
    private final AtomicLong nanos = new AtomicLong();
    private final java.security.KeyPair signingKey;

    CustomerOfflineGraceTest() throws GeneralSecurityException {
        signingKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void restartRequiresBothSignedGrantAndMatchingWrappedKey() throws Exception {
        assertNull(cache().restore(IDENTITY, this::verify));
        saveGrant();
        assertNull(cache().restore(IDENTITY, this::verify));

        String response = keyResponse("market", DIGEST, IDENTITY);
        saveKey(response);
        var offline = cache().restore(IDENTITY, this::verify);
        assertNotNull(offline);
        assertEquals(Set.of("market"), offline.moduleIds());
        assertEquals(response, offline.keyResponse("market", AXB));
        assertNull(offline.keyResponse("market", new byte[]{0}));
        assertNull(offline.keyResponse("fishing", AXB));
        assertNull(cache().restore(new CustomerOfflineGrace.Identity("123456780", "SERVER_A", PUBLIC_KEY, RELEASE),
            this::verify));
    }

    @Test
    void forbidsBackdatingAndUsesMonotonicTimeForAnActiveOfflineSession() throws Exception {
        saveGrant();
        saveKey(keyResponse("market", DIGEST, IDENTITY));
        wall.set(NOW + 6 * DAY);
        var offline = cache().restore(IDENTITY, this::verify);
        assertNotNull(offline);
        assertEquals(Set.of("market"), offline.moduleIds());

        wall.set(NOW + 5 * DAY);
        assertThrows(IOException.class, () -> cache().restore(IDENTITY, this::verify));
        nanos.set(Duration.ofDays(1).toNanos());
        assertTrue(offline.moduleIds().isEmpty());
        assertNull(offline.keyResponse("market", AXB));
        wall.set(NOW + 7 * DAY);
        assertNull(cache().restore(IDENTITY, this::verify));
        wall.set(NOW + 6 * DAY);
        assertThrows(IOException.class, () -> cache().restore(IDENTITY, this::verify));
    }

    @Test
    void cleanShutdownKeepsTimeAlreadySpentOffline() throws Exception {
        saveGrant();
        saveKey(keyResponse("market", DIGEST, IDENTITY));
        var cache = cache();
        var offline = cache.restore(IDENTITY, this::verify);
        assertNotNull(offline);
        nanos.set(Duration.ofDays(6).toNanos());
        cache.checkpoint(offline);

        nanos.set(0);
        wall.set(NOW + 5 * DAY);
        assertThrows(IOException.class, () -> cache().restore(IDENTITY, this::verify));
        wall.set(NOW + 7 * DAY);
        assertNull(cache().restore(IDENTITY, this::verify));
    }

    @Test
    void renewalReusesOnlyKeysForTheSameAuthorizedDigest() throws Exception {
        saveGrant();
        String response = keyResponse("market", DIGEST, IDENTITY);
        saveKey(response);

        wall.set(NOW + 6 * DAY);
        saveGrant();
        wall.set(NOW + 8 * DAY);
        assertEquals(response, cache().restore(IDENTITY, this::verify).keyResponse("market", AXB));

        String replacement = RuntimeAuthorization.checksum(new byte[]{9});
        cache().saveGrant(grant(wall.get(), wall.get() + DAY, replacement, "axs-primary", false), IDENTITY, this::verify);
        assertNull(cache().restore(IDENTITY, this::verify));
    }

    @Test
    void cannotExtendExistingGrantOrReplaceItWithAnAmbiguousOrStandbyGrant() throws Exception {
        saveGrant();
        saveKey(keyResponse("market", DIGEST, IDENTITY));
        Path file = folder.resolve(CustomerOfflineGrace.FILE_NAME);
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> cache().saveGrant(
            grant(NOW, NOW + 7 * DAY + 1, DIGEST, "axs-primary", false), IDENTITY, this::verify));
        assertThrows(IOException.class, () -> cache().saveGrant(
            grant(NOW, NOW + DAY, DIGEST, "axs-standby", false), IDENTITY, this::verify));
        assertThrows(IOException.class, () -> cache().saveGrant(
            grant(NOW, NOW + DAY, DIGEST, "axs-primary", true), IDENTITY, this::verify));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void rejectsOtherInstallationResponsesAndSignatureTampering() throws Exception {
        saveGrant();
        String otherServer = keyResponse("market", DIGEST,
            new CustomerOfflineGrace.Identity("123456789", "SERVER_B", PUBLIC_KEY, RELEASE));
        assertThrows(IOException.class, () -> saveKey(otherServer));
        String response = keyResponse("market", DIGEST, IDENTITY);
        byte[] raw = response.getBytes(StandardCharsets.UTF_8);
        String signature = Base64.getEncoder().encodeToString(sign(wall.get(), raw));
        raw[2] ^= 1;
        assertThrows(IOException.class, () -> cache().saveKeyResponse(
            "market", raw, signature, Long.toString(wall.get()), IDENTITY, this::verify));
        JsonObject unsafeResponse = JsonParser.parseString(response).getAsJsonObject();
        unsafeResponse.addProperty("apiKey", "must-not-persist");
        assertThrows(IOException.class, () -> saveKey(unsafeResponse.toString()));
        assertNull(cache().restore(IDENTITY, this::verify));
    }

    @Test
    void clearRequiresANewSignedGrantBeforeCachingAnotherKey() throws Exception {
        var cache = cache();
        saveGrant();
        saveKey(keyResponse("market", DIGEST, IDENTITY));
        var session = cache.restore(IDENTITY, this::verify);
        assertNotNull(session);
        cache.clear();
        assertTrue(session.moduleIds().isEmpty());
        assertNull(session.keyResponse("market", AXB));
        assertNull(cache.restore(IDENTITY, this::verify));
        assertNull(cache().restore(IDENTITY, this::verify));
        assertThrows(IOException.class, () -> cache.saveGrant(
            grant(NOW, NOW + 7 * DAY, DIGEST, "axs-primary", false), IDENTITY, this::verify));
        saveKey(keyResponse("market", DIGEST, IDENTITY));
        assertFalse(Files.exists(folder.resolve(CustomerOfflineGrace.FILE_NAME)));
    }

    private CustomerOfflineGrace cache() { return new CustomerOfflineGrace(folder, wall::get, nanos::get); }

    private void saveGrant() throws Exception {
        cache().saveGrant(grant(wall.get(), wall.get() + 7 * DAY, DIGEST, "axs-primary", false), IDENTITY, this::verify);
    }

    private void saveKey(String response) throws Exception {
        byte[] raw = response.getBytes(StandardCharsets.UTF_8);
        cache().saveKeyResponse("market", raw, Base64.getEncoder().encodeToString(sign(wall.get(), raw)),
            Long.toString(wall.get()), IDENTITY, this::verify);
    }

    private String grant(long issuedAt, long expiry, String checksum, String issuer, boolean duplicate)
            throws GeneralSecurityException {
        var grant = new JsonObject();
        grant.addProperty("purpose", CustomerOfflineGrace.PURPOSE);
        grant.addProperty("issuer", issuer);
        grant.addProperty("issuedAt", issuedAt);
        grant.addProperty("account", IDENTITY.account());
        grant.addProperty("serverCode", IDENTITY.serverCode());
        grant.addProperty("serverPublicKey", IDENTITY.serverPublicKey());
        grant.addProperty("customerRelease", IDENTITY.customerRelease());
        var module = new JsonObject();
        module.addProperty("moduleId", "market");
        module.addProperty("checksum", checksum);
        module.addProperty("expiresAt", expiry);
        var modules = new JsonArray();
        modules.add(module);
        if (duplicate) modules.add(module.deepCopy());
        grant.add("modules", modules);
        String raw = grant.toString();
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(raw.getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(sign(issuedAt, (CustomerOfflineGrace.PURPOSE + "\n" + raw).getBytes(StandardCharsets.UTF_8)));
    }

    private static String keyResponse(String moduleId, String checksum, CustomerOfflineGrace.Identity identity) {
        var response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("version", identity.customerRelease());
        response.addProperty("checksum", checksum);
        response.addProperty("keyBind", "anchor-hkdf-v1");
        response.addProperty("key", WRAPPED_KEY);
        response.addProperty("signature", Base64.getEncoder().encodeToString(new byte[64]));
        var binding = new JsonObject();
        binding.addProperty("account", identity.account());
        binding.addProperty("serverCode", identity.serverCode());
        binding.addProperty("serverPublicKey", identity.serverPublicKey());
        binding.addProperty("moduleId", moduleId);
        response.add("offlineBinding", binding);
        return response.toString();
    }

    private byte[] sign(long timestamp, byte[] body) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(signingKey.getPrivate());
        signature.update((timestamp + "\n").getBytes(StandardCharsets.UTF_8));
        signature.update(body);
        return signature.sign();
    }

    private boolean verify(long timestamp, byte[] body, byte[] signed) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(signingKey.getPublic());
            signature.update((timestamp + "\n").getBytes(StandardCharsets.UTF_8));
            signature.update(body);
            return signature.verify(signed);
        } catch (GeneralSecurityException failure) {
            return false;
        }
    }
}
