package xuanmo.arcartxsuite.license;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public record LicenseProxyConfig(boolean enabled, boolean useSystem, String type, String host, int port) {

    public static LicenseProxyConfig disabled() {
        return new LicenseProxyConfig(false, true, "HTTP", "", 0);
    }

    public LicenseProxyConfig {
        type = type == null || type.isBlank() ? "HTTP" : type.trim().toUpperCase();
        host = host == null ? "" : host.trim();
        if (enabled && (host.isBlank() || port <= 0)) {
            enabled = false;
        }
        if (!enabled && !useSystem) {
            host = "";
            port = 0;
        }
        if (!enabled && useSystem) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
    }

    public Proxy toJavaProxy() {
        return toJavaProxy(null);
    }

    public Proxy toJavaProxy(URL url) {
        if (!enabled) {
            return useSystem ? systemProxy(url) : Proxy.NO_PROXY;
        }
        return configuredProxy(type, host, port);
    }

    public String describe(Proxy proxy) {
        if (proxy == null || proxy == Proxy.NO_PROXY || proxy.address() == null) {
            return useSystem && !enabled ? "system:none" : summary();
        }
        return proxy.type() + " " + proxy.address();
    }

    private Proxy configuredProxy(String proxyTypeName, String proxyHost, int proxyPort) {
        Proxy.Type proxyType = "SOCKS".equals(proxyTypeName) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));
    }

    private Proxy systemProxy(URL url) {
        Proxy selectorProxy = proxySelectorProxy(url);
        if (selectorProxy != Proxy.NO_PROXY) {
            return selectorProxy;
        }

        Proxy envProxy = envProxy(url);
        if (envProxy != Proxy.NO_PROXY) {
            return envProxy;
        }

        Proxy windowsProxy = windowsRegistryProxy(url);
        if (windowsProxy != Proxy.NO_PROXY) {
            return windowsProxy;
        }

        return Proxy.NO_PROXY;
    }

    private Proxy proxySelectorProxy(URL url) {
        if (url == null) {
            return Proxy.NO_PROXY;
        }
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(url.toURI());
            for (Proxy proxy : proxies) {
                if (proxy != null && proxy != Proxy.NO_PROXY && proxy.address() != null) {
                    return proxy;
                }
            }
        } catch (Exception exception) {
            return Proxy.NO_PROXY;
        }
        return Proxy.NO_PROXY;
    }

    private Proxy envProxy(URL url) {
        String protocol = url == null ? "https" : url.getProtocol().toLowerCase(Locale.ROOT);
        String raw = firstNonBlank(
            System.getenv(protocol.toUpperCase(Locale.ROOT) + "_PROXY"),
            System.getenv(protocol.toLowerCase(Locale.ROOT) + "_proxy"),
            System.getenv("ALL_PROXY"),
            System.getenv("all_proxy")
        );
        return parseProxySpec(raw);
    }

    private Proxy windowsRegistryProxy(URL url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return Proxy.NO_PROXY;
        }
        try {
            Process process = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v",
                "ProxyEnable"
            ).redirectErrorStream(true).start();
            String enableOutput = readProcess(process);
            if (!enableOutput.contains("0x1")) {
                return Proxy.NO_PROXY;
            }

            Process serverProcess = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v",
                "ProxyServer"
            ).redirectErrorStream(true).start();
            String serverOutput = readProcess(serverProcess);
            String proxyServer = parseRegValue(serverOutput, "ProxyServer");
            return parseProxySpec(selectWindowsProxySpec(proxyServer, url));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Proxy.NO_PROXY;
        } catch (IOException exception) {
            return Proxy.NO_PROXY;
        }
    }

    private String readProcess(Process process) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        process.waitFor();
        return output.toString();
    }

    private String parseRegValue(String output, String name) {
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(name)) {
                String[] parts = trimmed.split("\\s+", 3);
                if (parts.length >= 3) {
                    return parts[2].trim();
                }
            }
        }
        return "";
    }

    private String selectWindowsProxySpec(String proxyServer, URL url) {
        if (proxyServer == null || proxyServer.isBlank()) {
            return "";
        }
        String protocol = url == null ? "https" : url.getProtocol().toLowerCase(Locale.ROOT);
        for (String part : proxyServer.split(";")) {
            String trimmed = part.trim();
            int equals = trimmed.indexOf('=');
            if (equals > 0 && trimmed.substring(0, equals).equalsIgnoreCase(protocol)) {
                return trimmed.substring(equals + 1);
            }
        }
        return proxyServer.contains("=") ? "" : proxyServer;
    }

    private Proxy parseProxySpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return Proxy.NO_PROXY;
        }
        String spec = raw.trim();
        if (spec.contains("://")) {
            try {
                URI uri = URI.create(spec);
                String uriHost = uri.getHost();
                int uriPort = uri.getPort();
                if (uriHost != null && uriPort > 0) {
                    Proxy.Type proxyType = "socks".equalsIgnoreCase(uri.getScheme()) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                    return new Proxy(proxyType, new InetSocketAddress(uriHost, uriPort));
                }
            } catch (IllegalArgumentException ignored) {
                return Proxy.NO_PROXY;
            }
        }
        int colon = spec.lastIndexOf(':');
        if (colon <= 0 || colon >= spec.length() - 1) {
            return Proxy.NO_PROXY;
        }
        try {
            String specHost = spec.substring(0, colon).trim();
            int specPort = Integer.parseInt(spec.substring(colon + 1).trim());
            if (!specHost.isBlank() && specPort > 0) {
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(specHost, specPort));
            }
        } catch (NumberFormatException ignored) {
            return Proxy.NO_PROXY;
        }
        return Proxy.NO_PROXY;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public String summary() {
        if (enabled) {
            return type + " " + host + ":" + port;
        }
        return useSystem ? "system" : "disabled";
    }

    public String diagnosticSummary() {
        if (enabled) {
            return type + " " + host + ":" + port + "（显式代理）";
        }
        if (useSystem) {
            return "system（自动读取系统/环境变量代理；实际结果见失败汇总 proxy=...）";
        }
        return "disabled（直连）";
    }
}
