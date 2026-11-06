package xuanmo.arcartxsuite.auth.center;

import com.google.gson.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static xuanmo.arcartxsuite.auth.center.CenterConfiguration.string;

/** The configured provider owns the browser login. Only the validated stable subject leaves this adapter. */
final class OidcLogin {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL64 = Base64.getUrlEncoder().withoutPadding();
    record Identity(String issuer, String subject) {}

    private OidcLogin() {}

    static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL64.encodeToString(bytes);
    }

    static String challenge(String verifier) {
        try { return URL64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String encode(String value) { return URLEncoder.encode(value, UTF_8); }

    static URI authorize(ExternalConfiguration.Provider provider, URI callback, String state, String nonce, String verifier) {
        return URI.create(provider.authorize() + "?response_type=code&scope=openid&max_age=300&client_id=" + encode(provider.clientId())
            + "&redirect_uri=" + encode(callback.toString()) + "&state=" + state + "&nonce=" + nonce
            + "&code_challenge_method=S256&code_challenge=" + challenge(verifier));
    }

    static Identity exchange(ExternalConfiguration.Provider provider, URI callback, String code, String nonce, String verifier) throws Exception {
        var connection = (HttpURLConnection) provider.token().toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String basic = encode(provider.clientId()) + ":" + encode(provider.secret());
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(UTF_8)));
            byte[] request = ("grant_type=authorization_code&code=" + encode(code) + "&redirect_uri=" + encode(callback.toString())
                + "&code_verifier=" + verifier).getBytes(UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            long deadline=System.nanoTime()+5_000_000_000L;
            try (var output = connection.getOutputStream()) { output.write(request); }
            if (connection.getResponseCode() != 200) throw new IOException("OIDC token exchange rejected");
            try (var input = connection.getInputStream(); var response = new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];
                int count;
                while ((count=input.read(buffer))!=-1) {
                    if (count>32768-response.size() || System.nanoTime()-deadline>=0) throw new IOException("OIDC response exceeded limits");
                    response.write(buffer,0,count);
                }
                JsonObject tokens = JsonParser.parseString(response.toString(UTF_8)).getAsJsonObject();
                return validate(provider, string(tokens, "id_token"), nonce, Instant.now().getEpochSecond());
            }
        } finally { connection.disconnect(); }
    }

    static Identity validate(ExternalConfiguration.Provider provider, String jwt, String nonce, long now) throws Exception {
        if (jwt.length() > 16384) throw new SecurityException("ID token too large");
        String[] parts = jwt.split("\\.", -1);
        if (parts.length != 3 || !Arrays.stream(parts).allMatch(part -> part.matches("[A-Za-z0-9_-]+")))
            throw new SecurityException("Invalid ID token");
        var decoder = Base64.getUrlDecoder();
        var header = JsonParser.parseString(new String(decoder.decode(parts[0]), UTF_8)).getAsJsonObject();
        if (!"RS256".equals(string(header, "alg")) || !provider.keyId().equals(string(header, "kid"))
                || header.has("crit") || header.has("b64")) throw new SecurityException("Invalid ID token header");
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(provider.key());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(decoder.decode(parts[2]))) throw new SecurityException("Invalid ID token signature");

        var claims = JsonParser.parseString(new String(decoder.decode(parts[1]), UTF_8)).getAsJsonObject();
        String issuer = string(claims, "iss"), subject = string(claims, "sub");
        var aud = claims.get("aud");
        // The center registers a single audience with the provider.
        boolean audience = aud != null && (aud.isJsonArray() && aud.getAsJsonArray().size() == 1
            ? audience(aud.getAsJsonArray().get(0), provider.clientId()) : audience(aud, provider.clientId()));
        long issued = timestamp(claims, "iat"), authenticated = timestamp(claims, "auth_time");
        if (!issuer.equals(provider.issuer().toString()) || !subject.matches("[!-~]{1,255}") || !audience
                || (claims.has("azp") && !provider.clientId().equals(string(claims, "azp")))
                || !nonce.equals(string(claims, "nonce")) || timestamp(claims, "exp") <= now
                || issued > now + 30 || issued < now - 300 || authenticated > now + 30 || authenticated < now - 330
                || (claims.has("nbf") && timestamp(claims, "nbf") > now + 30))
            throw new SecurityException("Invalid ID token claims");
        return new Identity(issuer, subject);
    }

    private static boolean audience(JsonElement value, String client) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && client.equals(value.getAsString());
    }

    private static long timestamp(JsonObject claims, String field) {
        var value = claims.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]{1,12}")) throw new SecurityException("Invalid ID token timestamp");
        return value.getAsLong();
    }
}
