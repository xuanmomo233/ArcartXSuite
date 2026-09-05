package xuanmo.arcartxsuite.mail.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.MailSendQuote;

/**
 * 写信界面数据包工厂，构建发送给客户端的写信初始化与报价数据包。
 * <p>
 * 关联模块：mail。所有方法为静态工具方法，封装手续费、货币列表、附件税率与密码面板等 UI 数据。
 */
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
            false,
            null
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
            false,
            null
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
        boolean passwordPanelVisible,
        String actionToken
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", sessionId.toString());
        payload.put("action_token", actionToken == null ? "" : actionToken);
        payload.put("subject_max", configuration.playerSend().subjectMaxLength());
        payload.put("body_max", configuration.playerSend().bodyMaxLength());
        payload.put("max_attachments", Math.max(0, maxAttachments));
        payload.put("attachment_count", Math.max(0, attachmentCount));
        payload.put("fee_currency", configuration.playerSend().feeCurrency());
        payload.put("allow_vault", configuration.playerSend().allowVaultAttachment());
        payload.put("password_panel", passwordPanel(passwordPanelVisible));
        applyQuote(payload, configuration, currencyBridgeManager, messages, quote);
        return payload;
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
        return buildInit(
            sessionId,
            configuration,
            currencyBridgeManager,
            messages,
            quote,
            maxAttachments,
            attachmentCount,
            passwordPanelVisible,
            null
        );
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
            false,
            null
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
            false,
            null
        );
    }

    public static Map<String, Object> buildQuote(
        MailModuleConfiguration configuration,
        CurrencyBridgeAPI currencyBridgeManager,
        MessageProvider messages,
        MailSendQuote quote,
        int maxAttachments,
        int attachmentCount,
        boolean passwordPanelVisible,
        String actionToken
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action_token", actionToken == null ? "" : actionToken);
        payload.put("max_attachments", Math.max(0, maxAttachments));
        payload.put("attachment_count", Math.max(0, attachmentCount));
        payload.put("allow_vault", configuration.playerSend().allowVaultAttachment());
        payload.put("password_panel", passwordPanel(passwordPanelVisible));
        applyQuote(payload, configuration, currencyBridgeManager, messages, quote);
        return payload;
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
        return buildQuote(
            configuration,
            currencyBridgeManager,
            messages,
            quote,
            maxAttachments,
            attachmentCount,
            passwordPanelVisible,
            null
        );
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
            ? MailService.calculateComposeQuote(configuration.playerSend(), Map.of(), 0, configuration.playerSend().feeCurrency())
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
}
