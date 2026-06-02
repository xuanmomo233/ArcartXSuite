package xuanmo.arcartxsuite.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LicenseConfigTest {

    @Test
    void readsProxyConfigWhenEnabled() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.network.proxy.enabled", true);
        yaml.set("license.network.proxy.type", "http");
        yaml.set("license.network.proxy.host", "127.0.0.1");
        yaml.set("license.network.proxy.port", 7897);

        LicenseProxyConfig proxy = LicenseConfig.proxyConfig(yaml);

        assertTrue(proxy.enabled());
        assertEquals("HTTP", proxy.type());
        assertEquals("127.0.0.1", proxy.host());
        assertEquals(7897, proxy.port());
        assertEquals("HTTP 127.0.0.1:7897", proxy.summary());
    }

    @Test
    void disablesIncompleteProxyConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.network.proxy.enabled", true);
        yaml.set("license.network.proxy.port", 7897);

        LicenseProxyConfig proxy = LicenseConfig.proxyConfig(yaml);

        assertFalse(proxy.enabled());
        assertTrue(proxy.useSystem());
        assertEquals("system", proxy.summary());
    }

    @Test
    void canDisableSystemProxyFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.network.proxy.enabled", false);
        yaml.set("license.network.proxy.use_system", false);

        LicenseProxyConfig proxy = LicenseConfig.proxyConfig(yaml);

        assertFalse(proxy.enabled());
        assertFalse(proxy.useSystem());
        assertEquals("disabled", proxy.summary());
    }

    @Test
    void readsOnlyLicenseKeysListWithoutLegacyKeyFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.key", "AXS-LEGACY-SHOULD-NOT-BE-USED");
        yaml.set("license.keys", java.util.List.of("AXS-MOD-WAREHOUSE-TEST", "  AXS-SUITE-TEST  "));

        assertEquals(
            java.util.List.of("AXS-MOD-WAREHOUSE-TEST", "AXS-SUITE-TEST"),
            LicenseConfig.licenseKeys(yaml)
        );
    }

    @Test
    void ignoresLegacyKeyWhenKeysListIsMissing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.key", "AXS-LEGACY-SHOULD-NOT-BE-USED");

        assertTrue(LicenseConfig.licenseKeys(yaml).isEmpty());
    }
}
