package xuanmo.arcartxsuite.license;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.security.MohistCompat;

public final class LicenseConfig {

    private final File file;
    private final String qq;
    private final List<String> licenseKeys;
    private final String installId;
    private final List<LicenseEndpoint> endpoints;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxRetries;
    private final boolean preflightTimeCheck;
    private final LicenseProxyConfig proxyConfig;

    private LicenseConfig(
        File file,
        String qq,
        List<String> licenseKeys,
        String installId,
        List<LicenseEndpoint> endpoints,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetries,
        boolean preflightTimeCheck,
        LicenseProxyConfig proxyConfig
    ) {
        this.file = file;
        this.qq = qq == null ? "" : qq.trim();
        this.licenseKeys = List.copyOf(licenseKeys == null ? List.of() : licenseKeys);
        this.installId = installId;
        this.endpoints = List.copyOf(endpoints);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxRetries = maxRetries;
        this.preflightTimeCheck = preflightTimeCheck;
        this.proxyConfig = proxyConfig == null ? LicenseProxyConfig.disabled() : proxyConfig;
    }

    public static LicenseConfig load(JavaPlugin plugin, Logger logger) {
        File file = new File(plugin.getDataFolder(), "license.yml");
        if (!file.exists()) {
            MohistCompat.saveResourceSafe(plugin, "license.yml", file);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String installId = yaml.getString("license.install_id", "auto");
        if (installId == null || installId.isBlank() || "auto".equalsIgnoreCase(installId.trim())) {
            installId = UUID.randomUUID().toString();
            yaml.set("license.install_id", installId);
            try {
                yaml.save(file);
            } catch (IOException exception) {
                logger.warning("保存 license.yml install_id 失败: " + exception.getMessage());
            }
        }

        List<LicenseEndpoint> endpoints = new ArrayList<>();
        for (ConfigurationSection section : endpointSections(yaml)) {
            endpoints.add(new LicenseEndpoint(
                section.getString("name", "endpoint"),
                section.getString("base_url", ""),
                section.getInt("priority", 100),
                section.getInt("timeout_ms", yaml.getInt("license.network.read_timeout_ms", 5000))
            ));
        }
        endpoints.removeIf(endpoint -> endpoint.baseUrl().isBlank());
        endpoints.sort(Comparator.comparingInt(LicenseEndpoint::priority));
        if (endpoints.isEmpty()) {
            endpoints.add(new LicenseEndpoint("official-domain", "https://axs.021209.xyz", 10, 4500));
        }

        List<String> licenseKeys = licenseKeys(yaml);

        return new LicenseConfig(
            file,
            yaml.getString("license.qq", ""),
            licenseKeys,
            installId,
            endpoints,
            yaml.getInt("license.network.connect_timeout_ms", 3000),
            yaml.getInt("license.network.read_timeout_ms", 5000),
            yaml.getInt("license.network.max_retries", 2),
            yaml.getBoolean("license.network.preflight_time_check", true),
            proxyConfig(yaml)
        );
    }

    static LicenseProxyConfig proxyConfig(YamlConfiguration yaml) {
        return new LicenseProxyConfig(
            yaml.getBoolean("license.network.proxy.enabled", false),
            yaml.getBoolean("license.network.proxy.use_system", true),
            yaml.getString("license.network.proxy.type", "HTTP"),
            yaml.getString("license.network.proxy.host", ""),
            yaml.getInt("license.network.proxy.port", 0)
        );
    }

    static List<String> licenseKeys(YamlConfiguration yaml) {
        List<String> licenseKeys = new ArrayList<>();
        for (String key : yaml.getStringList("license.keys")) {
            if (key != null && !key.isBlank()) {
                licenseKeys.add(key.trim());
            }
        }
        return licenseKeys;
    }

    private static List<ConfigurationSection> endpointSections(YamlConfiguration yaml) {
        List<ConfigurationSection> sections = new ArrayList<>();
        List<?> raw = yaml.getList("license.endpoints", List.of());
        for (int index = 0; index < raw.size(); index++) {
            ConfigurationSection section = yaml.getConfigurationSection("license.endpoints." + index);
            if (section != null) {
                sections.add(section);
            }
        }
        return sections;
    }

    public File file() {
        return file;
    }

    public String qq() {
        return qq;
    }

    public List<String> licenseKeys() {
        return licenseKeys;
    }

    public boolean hasLicenseIdentity() {
        return !qq.isBlank() && !licenseKeys.isEmpty();
    }

    public String installId() {
        return installId;
    }

    public List<LicenseEndpoint> endpoints() {
        return endpoints;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public boolean preflightTimeCheck() {
        return preflightTimeCheck;
    }

    public LicenseProxyConfig proxyConfig() {
        return proxyConfig;
    }

    public String endpointSummary() {
        List<String> parts = new ArrayList<>();
        for (LicenseEndpoint endpoint : endpoints) {
            parts.add(endpoint.name() + "=" + endpoint.baseUrl() + "@" + endpoint.priority());
        }
        return String.join(", ", parts);
    }
}
