package xuanmo.arcartxsuite.auth.center;

import com.google.gson.JsonElement;
import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import static xuanmo.arcartxsuite.auth.center.CenterConfiguration.*;

record ExternalConfiguration(int port, URI origin, Map<String, Provider> providers, Map<String, Launcher> launchers) {
    static final String PATH = "/v1/external";

    record Provider(URI issuer, URI authorize, URI token, String clientId, String secret, String keyId, RSAPublicKey key) {}
    record Launcher(String id, String realm, URI redirect) {}

    static ExternalConfiguration parse(JsonElement element, Set<String> realms, int nodePort) throws Exception {
        if (element.isJsonNull()) return null;
        var config = element.getAsJsonObject();
        keys(config, Set.of("port", "origin", "providers", "launchers"));
        int port = integer(config, "port");
        if (port < 1024 || port > 65535 || port == nodePort) throw new IllegalArgumentException("Invalid external listener port");
        URI origin = https(string(config, "origin"));
        if (!origin.getPath().isEmpty() || origin.getPort()==443 || !origin.getHost().equals(origin.getHost().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("External origin must use a canonical host and omit the default port");
        Map<String, Provider> providers = new HashMap<>();
        for (JsonElement entry : config.getAsJsonArray("providers")) {
            var provider = entry.getAsJsonObject();
            keys(provider, Set.of("realm", "issuer", "authorize", "token", "clientId", "secretEnvironment", "keyId", "rsaPublicKey"));
            String realm = identifier(string(provider, "realm"));
            String environment = string(provider, "secretEnvironment");
            if (!realms.contains(realm) || !environment.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("Invalid OIDC provider realm or secret environment");
            String secret = System.getenv(environment);
            if (secret == null || secret.isBlank() || secret.length() > 1024) throw new IllegalArgumentException("Missing or invalid OIDC client secret");
            String clientId = string(provider, "clientId"), keyId = string(provider, "keyId");
            if (clientId.isBlank() || clientId.length() > 128 || keyId.isBlank() || keyId.length() > 128) throw new IllegalArgumentException("Invalid OIDC client or key id");
            var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(string(provider, "rsaPublicKey"))));
            if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 4096) throw new IllegalArgumentException("Invalid OIDC RSA key size");
            var value = new Provider(https(string(provider, "issuer")), https(string(provider, "authorize")),
                https(string(provider, "token")), clientId, secret, keyId, key);
            if (providers.putIfAbsent(realm, value) != null) throw new IllegalArgumentException("Duplicate OIDC realm");
        }
        Map<String, Launcher> launchers = new HashMap<>();
        for (JsonElement entry : config.getAsJsonArray("launchers")) {
            var launcher = entry.getAsJsonObject();
            keys(launcher, Set.of("id", "realm", "redirect"));
            String id = identifier(string(launcher, "id")), realm = identifier(string(launcher, "realm"));
            URI redirect = URI.create(string(launcher, "redirect"));
            if (!providers.containsKey(realm) || !redirect.toString().matches("http://127\\.0\\.0\\.1:[0-9]{4,5}/[a-zA-Z0-9/_-]{1,128}")
                    || redirect.getPort() < 1024 || redirect.getPort() > 65535)
                throw new IllegalArgumentException("Launcher redirect must be a registered literal loopback URL");
            if (launchers.putIfAbsent(id, new Launcher(id, realm, redirect)) != null) throw new IllegalArgumentException("Duplicate launcher id");
        }
        if (providers.isEmpty() || launchers.size() > 64) throw new IllegalArgumentException("Invalid external provider or launcher count");
        return new ExternalConfiguration(port, origin, Map.copyOf(providers), Map.copyOf(launchers));
    }

    URI url(String endpoint) { return origin.resolve(PATH + endpoint); }

    static URI https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
            throw new IllegalArgumentException("Expected a fixed HTTPS URL");
        return uri;
    }
}
