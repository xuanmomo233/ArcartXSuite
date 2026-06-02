package xuanmo.arcartxsuite.license;

public record LicenseEndpoint(String name, String baseUrl, int priority, int timeoutMs) {

    public LicenseEndpoint {
        name = blank(name) ? "endpoint" : name.trim();
        baseUrl = trimTrailingSlash(baseUrl);
        timeoutMs = timeoutMs <= 0 ? 5000 : timeoutMs;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String normalized = blank(value) ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
