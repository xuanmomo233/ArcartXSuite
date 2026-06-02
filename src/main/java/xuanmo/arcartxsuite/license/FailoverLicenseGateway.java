package xuanmo.arcartxsuite.license;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FailoverLicenseGateway implements LicenseGateway {

    private final List<LicenseGateway> gateways;
    private final String endpointSummary;
    private final String proxySummary;
    private String lastOperation = "";
    private String lastSuccessfulEndpoint = "";
    private String lastFailureSummary = "";

    public FailoverLicenseGateway(List<LicenseGateway> gateways, String endpointSummary, String proxySummary) {
        this.gateways = List.copyOf(gateways);
        this.endpointSummary = endpointSummary == null ? "" : endpointSummary;
        this.proxySummary = proxySummary == null ? "" : proxySummary;
    }

    public static FailoverLicenseGateway fromConfig(LicenseConfig config) {
        List<LicenseGateway> gateways = new ArrayList<>();
        config.endpoints().stream()
            .sorted(Comparator.comparingInt(LicenseEndpoint::priority))
            .forEach(endpoint -> gateways.add(new HttpLicenseGateway(endpoint, config.connectTimeoutMs(), config.readTimeoutMs(), config.proxyConfig())));
        return new FailoverLicenseGateway(gateways, config.endpointSummary(), config.proxyConfig().summary());
    }

    @Override
    public JsonObject activate(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("POST /v1/activate", gateway -> gateway.activate(request));
    }

    @Override
    public JsonObject verify(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("POST /v1/verify", gateway -> gateway.verify(request));
    }

    @Override
    public JsonObject rebind(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("POST /v1/rebind", gateway -> gateway.rebind(request));
    }

    @Override
    public JsonObject cloudChallenge(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("POST /v1/cloud-challenge", gateway -> gateway.cloudChallenge(request));
    }

    @Override
    public JsonObject time() throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("GET /v1/time", LicenseGateway::time);
    }

    @Override
    public JsonObject revokeList() throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("GET /v1/revoke-list", LicenseGateway::revokeList);
    }

    @Override
    public JsonObject heartbeat(JsonObject request) throws LicenseNetworkException, LicenseAuthException {
        return tryGateways("POST /v1/heartbeat", gateway -> gateway.heartbeat(request));
    }

    private JsonObject tryGateways(String operationName, Operation operation) throws LicenseNetworkException, LicenseAuthException {
        lastOperation = operationName;
        lastSuccessfulEndpoint = "";
        List<String> failures = new ArrayList<>();
        for (LicenseGateway gateway : gateways) {
            try {
                JsonObject result = operation.call(gateway);
                lastSuccessfulEndpoint = describe(gateway);
                lastFailureSummary = "";
                return result;
            } catch (LicenseAuthException auth) {
                lastFailureSummary = describe(gateway) + " 授权拒绝=" + LicenseMessages.authError(auth.errorCode());
                throw auth;
            } catch (LicenseNetworkException network) {
                failures.add(describe(gateway) + " -> " + network.getMessage());
            }
        }
        lastFailureSummary = failures.isEmpty() ? "没有可用授权入口" : String.join(" || ", failures);
        throw new LicenseNetworkException(operationName + " 所有授权入口均不可用: " + lastFailureSummary);
    }

    public String endpointSummary() {
        return endpointSummary;
    }

    public String proxySummary() {
        return proxySummary;
    }

    public String lastOperation() {
        return lastOperation;
    }

    public String lastSuccessfulEndpoint() {
        return lastSuccessfulEndpoint;
    }

    public String lastFailureSummary() {
        return lastFailureSummary;
    }

    private String describe(LicenseGateway gateway) {
        if (gateway instanceof HttpLicenseGateway http) {
            return http.endpointName();
        }
        return gateway.getClass().getSimpleName();
    }

    private interface Operation {
        JsonObject call(LicenseGateway gateway) throws LicenseNetworkException, LicenseAuthException;
    }
}
