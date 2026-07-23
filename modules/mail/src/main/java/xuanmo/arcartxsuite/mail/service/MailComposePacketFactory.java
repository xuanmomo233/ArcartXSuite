package xuanmo.arcartxsuite.mail.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.MailSendQuote;

public final class MailComposePacketFactory {

    private MailComposePacketFactory() {
    }

    public static Map<String, Object> buildInit(
        UUID sessionId,
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount
    ) {
        return buildInit(
            sessionId,
            configuration,
            currencyBridgeManager,
            null,
            quote,
            maxAttachments,
            attachmentCount,
            false
        );
    }

    public static Map<String, Object> buildInit(
        UUID sessionId,
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount
    ) {
        return buildInit(
            sessionId,
            configuration,
            currencyBridgeManager,
            messages,
            quote,
            maxAttachments,
            attachmentCount,
            false
        );
    }

    public static Map<String, Object> buildInit(
        UUID sessionId,
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount,
        boolean passwordPanelVisible
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", sessionId.toString());
        payload.put("subject_max", configuration.playerSend().subjectMaxLength());
        payload.put("body_max", configuration.playerSend().bodyMaxLength());
        payload.put("max_attachments", Math.max(0, maxAttachments));
        payload.put("attachment_count", Math.max(0, attachmentCount));
        payload.put("fee_currency", configuration.playerSend().feeCurrency());
        payload.put("currencies", currencies(currencyBridgeManager, messages));
        payload.put("vault_tax_rate", trimRate(configuration.playerSend().vaultTaxRate()));
        payload.put("allow_vault", configuration.playerSend().allowVaultAttachment());
        payload.put("password_panel", passwordPanel(passwordPanelVisible));
        applyQuote(payload, configuration, currencyBridgeManager, messages, quote);
        return payload;
    }

    public static Map<String, Object> buildQuote(
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount
    ) {
        return buildQuote(
            configuration,
            currencyBridgeManager,
            null,
            quote,
            maxAttachments,
            attachmentCount,
            false
        );
    }

    public static Map<String, Object> buildQuote(
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount
    ) {
        return buildQuote(
            configuration,
            currencyBridgeManager,
            messages,
            quote,
            maxAttachments,
            attachmentCount,
            false
        );
    }

    public static Map<String, Object> buildQuote(
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount,
        boolean passwordPanelVisible
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("max_attachments", Math.max(0, maxAttachments));
        payload.put("attachment_count", Math.max(0, attachmentCount));
        payload.put("vault_tax_rate", trimRate(configuration.playerSend().vaultTaxRate()));
        payload.put("allow_vault", configuration.playerSend().allowVaultAttachment());
        payload.put("password_panel", passwordPanel(passwordPanelVisible));
        applyQuote(payload, configuration, currencyBridgeManager, messages, quote);
        return payload;
    }

    private static Map<String, Object> passwordPanel(boolean visible) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("visible", visible);
        return panel;
    }

    private static void applyQuote(
        Map<String, Object> payload,
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote
    ) {
        MailSendQuote effectiveQuote = quote == null
            ? MailService.calculateComposeQuote(configuration.playerSend(), Map.of(), 0)
            : quote;
        payload.put("quote_success", effectiveQuote.success());
        payload.put("quote_message", effectiveQuote.message());
        payload.put("base_fee", currencyBridgeManager.format(effectiveQuote.feeCurrencyId(), effectiveQuote.baseFee()));
        payload.put("item_fee", currencyBridgeManager.format(effectiveQuote.feeCurrencyId(), effectiveQuote.itemFee()));
        payload.put("total_fee", currencyBridgeManager.format(effectiveQuote.feeCurrencyId(), effectiveQuote.totalFee()));
        payload.put("total_fee_currency", effectiveQuote.feeCurrencyId());
        payload.put("attachment_amounts", stringMap(effectiveQuote.attachmentAmounts(), currencyBridgeManager));
        payload.put("attachment_taxes", stringMap(effectiveQuote.attachmentTaxes(), currencyBridgeManager));
    }

    private static Map<String, Object> currencies(CurrencyBridgeAPI currencyBridgeManager, MessageProvider messages) {
        Map<String, Object> entries = new LinkedHashMap<>();
        Collection<CurrencyDefinition> definitions = currencyBridgeManager.definitions();
        for (CurrencyDefinition definition : definitions) {
            Map<String, Object> item = new LinkedHashMap<>();
            var bridge = currencyBridgeManager.bridge(definition.id());
            item.put("id", definition.id());
            item.put("display_name", definition.displayName());
            item.put("available", bridge != null && bridge.available());
            item.put("unavailable_reason", bridge == null
                ? (messages == null ? "未配置桥接" : messages.get("ui.currency-bridge-unavailable", definition.id()))
                : bridge.unavailableReason());
            entries.put(definition.id(), item);
        }
        return entries;
    }

    private static Map<String, Object> stringMap(Map<String, BigDecimal> values, CurrencyBridgeAPI currencyBridgeManager) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            result.put(entry.getKey(), currencyBridgeManager.format(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static String trimRate(double rawRate) {
        return BigDecimal.valueOf(Math.max(0.0D, rawRate)).stripTrailingZeros().toPlainString();
    }
}
