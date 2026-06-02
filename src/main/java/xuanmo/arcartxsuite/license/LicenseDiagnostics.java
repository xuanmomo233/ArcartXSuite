package xuanmo.arcartxsuite.license;

public record LicenseDiagnostics(
    LicenseDecision.State state,
    String reason,
    String licenseId,
    String activationId,
    String ownerQq,
    String subjectId,
    String modules,
    String keyResults,
    long expiresAt,
    long refreshAfter,
    boolean rollbackDetected,
    String fingerprintHash,
    String endpoints,
    String proxy,
    String lastOperation,
    String lastSuccessfulEndpoint,
    String lastFailureSummary,
    String preflightSummary,
    boolean usingCache
) {
}
