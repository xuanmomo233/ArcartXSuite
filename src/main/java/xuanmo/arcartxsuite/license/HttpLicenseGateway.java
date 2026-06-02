package xuanmo.arcartxsuite.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HttpLicenseGateway implements LicenseGateway {

    private final Gson gson = new Gson();
    private final LicenseEndpoint endpoint;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final LicenseProxyConfig proxyConfig;

    public HttpLicenseGateway(LicenseEndpoint endpoint, int connectTimeoutMs, int readTimeoutMs, LicenseProxyConfig proxyConfig) {
        this.endpoint = endpoint;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = Math.max(readTimeoutMs, endpoint.timeoutMs());
        this.proxyConfig = proxyConfig == null ? LicenseProxyConfig.disabled() : proxyConfig;
    }

    @Override
    public JsonObject activate(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return post("/v1/activate", request);
    }

    @Override
    public JsonObject verify(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return post("/v1/verify", request);
    }

    @Override
    public JsonObject rebind(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return post("/v1/rebind", request);
    }

    @Override
    public JsonObject cloudChallenge(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return post("/v1/cloud-challenge", request);
    }

    @Override
    public JsonObject time() throws LicenseNetworkException, LicenseAuthException {
        return get("/v1/time");
    }

    @Override
    public JsonObject revokeList() throws LicenseNetworkException, LicenseAuthException {
        return get("/v1/revoke-list");
    }

    @Override
    public JsonObject heartbeat(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return post("/v1/heartbeat", request);
    }

    private JsonObject get(String path) throws LicenseNetworkException, LicenseAuthException {
        return request("GET", path, null);
    }

    private JsonObject post(String path, JsonObject body) throws LicenseNetworkException, LicenseAuthException {
        return request("POST", path, body);
    }

    private JsonObject request(String method, String path, JsonObject body) throws LicenseNetworkException, LicenseAuthException {
        HttpURLConnection connection = null;
        Proxy proxy = Proxy.NO_PROXY;
        try {
            URL url = new URL(endpoint.baseUrl() + path);
            proxy = proxyConfig.toJavaProxy(url);
            connection = (HttpURLConnection) (proxy == Proxy.NO_PROXY ? url.openConnection() : url.openConnection(proxy));
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            String responseText = readResponse(connection, status);
            JsonObject response = gson.fromJson(responseText, JsonObject.class);
            if (response == null) {
                throw new LicenseNetworkException(context(method, path, proxy) + " 返回空 JSON");
            }
            if (status >= 400 || (response.has("ok") && !response.get("ok").getAsBoolean())) {
                String error = response.has("error") ? response.get("error").getAsString() : "HTTP_" + status;
                if (status >= 500) {
                    throw new LicenseNetworkException(context(method, path, proxy) + " 服务端错误: " + error);
                }
                throw new LicenseAuthException(error, responseText);
            }
            return response;
        } catch (LicenseAuthException | LicenseNetworkException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new LicenseNetworkException(context(method, path, proxy) + " 网络错误: " + exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(HttpURLConnection connection, int status) throws IOException {
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) {
            return "{}";
        }
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String endpointName() {
        return endpoint.name();
    }

    public String endpointSummary() {
        return endpoint.name() + "=" + endpoint.baseUrl() + "@" + endpoint.priority();
    }

    private String context(String method, String path, Proxy proxy) {
        return endpoint.name()
            + " " + method + " " + path
            + " endpoint=" + endpoint.baseUrl()
            + " connectTimeoutMs=" + connectTimeoutMs
            + " readTimeoutMs=" + readTimeoutMs
            + " proxy=" + proxyConfig.describe(proxy);
    }
}
