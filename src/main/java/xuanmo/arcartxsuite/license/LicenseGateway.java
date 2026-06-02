package xuanmo.arcartxsuite.license;

import com.google.gson.JsonObject;

public interface LicenseGateway {

    JsonObject activate(JsonObject request) throws LicenseNetworkException, LicenseAuthException;

    JsonObject verify(JsonObject request) throws LicenseNetworkException, LicenseAuthException;

    JsonObject rebind(JsonObject request) throws LicenseNetworkException, LicenseAuthException;

    JsonObject cloudChallenge(JsonObject request) throws LicenseNetworkException, LicenseAuthException;

    JsonObject time() throws LicenseNetworkException, LicenseAuthException;

    JsonObject revokeList() throws LicenseNetworkException, LicenseAuthException;

    JsonObject heartbeat(JsonObject request) throws LicenseNetworkException, LicenseAuthException;

    final class LicenseNetworkException extends Exception {
        public LicenseNetworkException(String message) {
            super(message);
        }

        public LicenseNetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    final class LicenseAuthException extends Exception {
        private final String errorCode;

        public LicenseAuthException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
