package xuanmo.arcartxsuite.auth.runtime;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;

final class SignedProxyEdition {
    private SignedProxyEdition() {}

    static boolean supported() { return true; }

    static ProxyAdmissionChannel channel(JavaPlugin plugin, AuthenticationRuntime runtime) {
        return new ProxyProofIngress(plugin, runtime);
    }

    static List<AuthenticationSettings.TrustedProxy> readTrust(YamlConfiguration config, String audience) {
        if (!audience.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("signed-proxy 需要有效 audience");
        if (!(config.get("ingress.trusted-proxies") instanceof List<?> configured)
                || configured.stream().anyMatch(value -> !(value instanceof Map<?,?>))) {
            throw new IllegalArgumentException("可信代理必须为对象列表");
        }
        var values = config.getMapList("ingress.trusted-proxies");
        if (values.isEmpty() || values.size() > 8) throw new IllegalArgumentException("需要 1 至 8 个可信代理公钥");
        var trusts = new ArrayList<AuthenticationSettings.TrustedProxy>();
        for (var value : values) {
            try {
                if (!Set.of("issuer", "public-key", "providers").containsAll(value.keySet())) throw new IllegalArgumentException();
                String issuer = (String) value.get("issuer");
                if (issuer == null || !issuer.matches("[a-zA-Z0-9_-]{1,64}")
                        || !(value.get("providers") instanceof List<?> providers)) throw new IllegalArgumentException();
                Set<String> permitted = new HashSet<>();
                for (Object provider : providers) permitted.add((String) provider);
                if (permitted.isEmpty() || !Set.of("microsoft", "littleskin", "center").containsAll(permitted)) {
                    throw new IllegalArgumentException();
                }
                trusts.add(new AuthenticationSettings.TrustedProxy(issuer,
                    ProxyProofs.publicKey((String) value.get("public-key")), Set.copyOf(permitted)));
            } catch (Exception failure) {
                throw new IllegalArgumentException("可信代理配置无效", failure);
            }
        }
        return List.copyOf(trusts);
    }

    static String challenge(UUID profile, String name, UUID connection, UUID nonce, String realm, String audience) {
        var payload = new JsonObject();
        payload.addProperty("type", "challenge");
        payload.addProperty("profile", profile.toString());
        payload.addProperty("name", name);
        payload.addProperty("connection", connection.toString());
        payload.addProperty("challenge", nonce.toString());
        payload.addProperty("realm", realm);
        payload.addProperty("audience", audience);
        return payload.toString();
    }

    static String verifyProvider(String envelope, List<AuthenticationSettings.TrustedProxy> trustedProxies,
                                 UUID profile, String name, String realm, String audience, UUID connection, UUID nonce)
            throws Exception {
        var trusts = trustedProxies.stream()
            .map(trust -> new ProxyProofs.Trust(trust.issuer(), trust.key(), trust.providers())).toList();
        return ProxyProofs.verify(envelope, trusts, profile, name,
            new ProxyProofs.Target(realm, audience, connection, nonce), System.currentTimeMillis()).provider();
    }
}
