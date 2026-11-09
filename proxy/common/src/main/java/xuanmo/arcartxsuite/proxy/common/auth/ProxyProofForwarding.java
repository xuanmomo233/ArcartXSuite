package xuanmo.arcartxsuite.proxy.common.auth;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.*;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.auth.minecraft.LocalAgentBinding;
import xuanmo.arcartxsuite.auth.minecraft.LocalHandshakeClient;
import xuanmo.arcartxsuite.auth.minecraft.ProxyKeys;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;

/** Shared authority for proxy adapters. The adapter supplies the actual player and backend connection. */
public final class ProxyProofForwarding {
    public static final String CHANNEL = "axs:auth";
    public static final int MAX_FRAME_BYTES = 4096;
    private static final Set<String> CHALLENGE_FIELDS = Set.of(
        "type", "profile", "name", "realm", "audience", "connection", "challenge");

    record Destination(String realm, String audience) {}
    record Configuration(boolean enabled, String issuer, String gateway, Map<String, Destination> targets) {
        static Configuration parse(Map<String, Object> values) {
            if (!Set.of("enabled", "issuer", "gateway-url", "targets").containsAll(values.keySet()))
                throw new IllegalArgumentException("不支持的 auth-forwarding 字段");
            Object enabled = values.get("enabled");
            if (enabled != null && !(enabled instanceof Boolean))
                throw new IllegalArgumentException("auth-forwarding.enabled 必须为布尔值");
            if (!Boolean.TRUE.equals(enabled)) return new Configuration(false, "", "", Map.of());

            Object issuer = values.get("issuer");
            Object gateway = values.getOrDefault("gateway-url", "http://127.0.0.1:25599");
            if (!(issuer instanceof String id) || !identifier(id) || !(gateway instanceof String endpoint))
                throw new IllegalArgumentException("代理 issuer 或 gateway-url 无效");
            // Validates literal loopback, path and port without issuing a network request.
            new LocalHandshakeClient(endpoint);
            if (!(values.get("targets") instanceof Map<?, ?> entries) || entries.isEmpty() || entries.size() > 64)
                throw new IllegalArgumentException("需要 1 至 64 个后端目标");

            Map<String, Destination> targets = new HashMap<>();
            for (var entry : entries.entrySet()) {
                if (!(entry.getKey() instanceof String server) || server.isBlank() || server.length() > 128
                        || !(entry.getValue() instanceof Map<?, ?> target)
                        || !Set.of("realm", "audience").equals(target.keySet())
                        || !(target.get("realm") instanceof String realm) || !identifier(realm)
                        || !(target.get("audience") instanceof String audience) || !identifier(audience))
                    throw new IllegalArgumentException("后端映射需要有效的服务器名称、realm 和 audience");
                targets.put(server, new Destination(realm, audience));
            }
            return new Configuration(true, id, endpoint, Map.copyOf(targets));
        }

        private static boolean identifier(String value) { return value.matches("[a-zA-Z0-9_-]{1,64}"); }
    }

    private final Configuration config;
    private final LocalHandshakeClient gateway;
    private final KeyPair keys;

    public ProxyProofForwarding(Path folder, Map<String, Object> values, Logger logger) throws Exception {
        config = Configuration.parse(values);
        if (!config.enabled()) {
            gateway = null;
            keys = null;
            return;
        }
        LocalAgentBinding.require(config.gateway(), ManagementFactory.getRuntimeMXBean().getInputArguments());
        gateway = new LocalHandshakeClient(config.gateway());
        keys = ProxyKeys.loadOrCreate(folder);
        logger.info("[AuthForwarding] issuer=" + config.issuer() + " public-key="
            + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
    }

    public boolean enabled() { return config.enabled(); }

    /** Blocking receipt lookup. Called only after the platform's successful online handshake, off its event loop. */
    public ProxyProofs.Identity claim(UUID profile, String name) throws Exception {
        if (!enabled()) throw new IllegalStateException("Proxy authentication forwarding is disabled");
        var receipt = gateway.claim(profile, name, UUID.randomUUID(), config.issuer()).receipt();
        return new ProxyProofs.Identity(profile, name, receipt.provider(), receipt.issuedAt());
    }

    /** No I/O. Returns a frame only for a challenge matching the configured backend and actual player. */
    public byte[] response(String server, UUID profile, String name, ProxyProofs.Identity identity, byte[] frame) throws Exception {
        Destination destination = config.targets().get(server);
        if (!enabled() || destination == null || !identity.profile().equals(profile) || !identity.name().equalsIgnoreCase(name))
            return null;
        ProxyProofs.Target target = parseChallenge(frame, profile, name, destination);
        if (target == null) return null;

        String proof = ProxyProofs.sign(config.issuer(), keys.getPrivate(), identity, target, System.currentTimeMillis());
        JsonObject response = new JsonObject();
        response.addProperty("type", "proof");
        response.addProperty("connection", target.connection().toString());
        response.addProperty("proof", proof);
        byte[] encoded = response.toString().getBytes(StandardCharsets.UTF_8);
        return encoded.length <= MAX_FRAME_BYTES ? encoded : null;
    }

    static ProxyProofs.Target parseChallenge(byte[] frame, UUID profile, String name, Destination expected) {
        if (frame == null || frame.length > MAX_FRAME_BYTES) return null;
        try {
            var body = JsonParser.parseString(new String(frame, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!CHALLENGE_FIELDS.equals(body.keySet())) return null;
            for (String field : CHALLENGE_FIELDS) {
                JsonElement value = body.get(field);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            }
            if (!"challenge".equals(body.get("type").getAsString())
                    || !profile.toString().equals(body.get("profile").getAsString())
                    || !name.equalsIgnoreCase(body.get("name").getAsString())
                    || !expected.realm().equals(body.get("realm").getAsString())
                    || !expected.audience().equals(body.get("audience").getAsString())) return null;
            return new ProxyProofs.Target(expected.realm(), expected.audience(),
                UUID.fromString(body.get("connection").getAsString()), UUID.fromString(body.get("challenge").getAsString()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
