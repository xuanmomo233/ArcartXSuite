package xuanmo.arcartxsuite.api.currency;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 货币桥接管理 API。
 * <p>
 * 提供统一的多货币查询、扣款、充值能力。
 */
public interface CurrencyBridgeAPI {

    /** 获取指定货币的桥接实例，不存在返回 null */
    CurrencyBridge bridge(String currencyId);

    /** 获取货币定义 */
    CurrencyDefinition definition(String currencyId);

    /** 获取所有已配置的货币定义 */
    Collection<CurrencyDefinition> definitions();

    /** 获取所有已配置的货币 ID */
    Set<String> currencyIds();

    /** 按货币精度格式化金额 */
    String format(String currencyId, BigDecimal amount);

    /**
     * 单个货币的操作接口。
     */
    interface CurrencyBridge {
        CurrencyDefinition definition();

        boolean available();

        String unavailableReason();

        BigDecimal balance(Player player);

        CurrencyTransactionResult withdraw(Player player, BigDecimal amount);

        CurrencyTransactionResult deposit(Player player, BigDecimal amount);

        /** Checks whether a bounded transfer amount can be represented without rounding. */
        default boolean supportsExactAmount(BigDecimal amount) {
            return amount.signum() > 0 && amount.stripTrailingZeros().scale() <= definition().scale();
        }

        /**
         * Capture a background-safe balance read on the server thread, if supported by the provider.
         * The returned operation must use account identity and must not access the live Player.
         */
        default @Nullable Supplier<BigDecimal> prepareBackgroundBalance(Player player) { return null; }

        /**
         * Capture a one-shot, background-safe debit on the server thread, if supported.
         * The caller must record the debit intent first and must not retry an uncertain result.
         */
        default @Nullable Supplier<CurrencyTransactionResult> prepareBackgroundDebit(Player player, BigDecimal amount) { return null; }

        /** Capture a one-shot credit that can run without the live Player. Record the intent before invoking it. */
        default @Nullable Supplier<CurrencyTransactionResult> prepareBackgroundCredit(Player player, BigDecimal amount) { return null; }

        /**
         * A provider may publish an updated balance before confirming its persistence.
         * Such a provider must not be acknowledged as durably delivered without a separate receipt.
         */
        default boolean creditRequiresReconciliation() { return false; }

        /** True when a debit acknowledgement does not establish durable payment. */
        default boolean debitRequiresReconciliation() { return false; }
    }
}
