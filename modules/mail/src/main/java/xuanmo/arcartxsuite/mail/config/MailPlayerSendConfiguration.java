package xuanmo.arcartxsuite.mail.config;

import java.util.Map;

public record MailPlayerSendConfiguration(
    boolean enabled,
    boolean requirePermission,
    int cooldownSeconds,
    double baseFee,
    double itemFee,
    String feeCurrency,
    Map<String, Double> attachmentTaxRates,
    boolean allowSelfSend,
    boolean allowOfflineSend,
    boolean allowVaultAttachment,
    int maxAttachments,
    int subjectMaxLength,
    int bodyMaxLength
) {
    public double attachmentTaxRate(String currencyId) {
        if (attachmentTaxRates == null || attachmentTaxRates.isEmpty()) {
            return 0.0D;
        }
        Double rate = attachmentTaxRates.get(currencyId == null ? "" : currencyId.trim().toLowerCase(java.util.Locale.ROOT));
        return rate == null ? 0.0D : Math.max(0.0D, rate);
    }

    public double vaultTaxRate() {
        return attachmentTaxRate("money");
    }
}
