package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Recognizes the configuration so an unsupported ingress stays closed. */
final class SignedProxyEdition {
    private SignedProxyEdition() {}

    static boolean supported() { return false; }
    static ProxyAdmissionChannel channel(JavaPlugin plugin, AuthenticationRuntime runtime) { return null; }
    static List<AuthenticationSettings.TrustedProxy> readTrust(YamlConfiguration config, String audience) {
        return List.of();
    }
    static String challenge(UUID profile, String name, UUID connection, UUID nonce, String realm, String audience) {
        return null;
    }
    static String verifyProvider(String envelope, List<AuthenticationSettings.TrustedProxy> trustedProxies,
                                 UUID profile, String name, String realm, String audience, UUID connection, UUID nonce) {
        return null;
    }
}
