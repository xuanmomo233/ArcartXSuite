package xuanmo.arcartxsuite.api.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

public final class CurrencyBridgeManager implements CurrencyBridgeAPI {

    private static final Logger LOGGER = Logger.getLogger("ArcartXSuite");
    private static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{1,16}");
    private static final String RANGE_FAILURE = "金额超出该货币可处理范围。";

    private final JavaPlugin plugin;
    private final Map<String, CurrencyDefinition> registeredDefinitions;
    private volatile Map<String, CurrencyDefinition> definitions;
    private volatile Map<String, CurrencyBridge> bridges;
    private final PlaceholderResolverAPI placeholderResolver;
    private final Map<UUID, Object> customOperationLocks = new ConcurrentHashMap<>();
    private volatile Supplier<EventBusCapability> eventBusProvider;

    public CurrencyBridgeManager(JavaPlugin plugin) {
        this(plugin, Map.of(), null);
    }

    public CurrencyBridgeManager(JavaPlugin plugin, Map<String, CurrencyDefinition> definitions) {
        this(plugin, definitions, null);
    }

    public CurrencyBridgeManager(JavaPlugin plugin, Map<String, CurrencyDefinition> definitions, PlaceholderResolverAPI placeholderResolver) {
        this.plugin = plugin;
        this.registeredDefinitions = definitions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(definitions);
        this.definitions = immutableCopy(this.registeredDefinitions);
        this.bridges = Map.of();
        this.placeholderResolver = placeholderResolver;
    }

    /**
     * 注入事件总线提供者，用于在货币变动时分发 {@code axs.currency.spent} / {@code axs.currency.earned} 事件。
     * <p>
     * 事件 payload 包含：
     * <ul>
     *   <li>{@code currency_id} — 货币 ID</li>
     *   <li>{@code amount} — 变动金额（字符串形式）</li>
     *   <li>{@code action} — {@code "withdraw"} 或 {@code "deposit"}</li>
     *   <li>{@code balance_after} — 变动后余额（字符串形式，可能为 "0"）</li>
     * </ul>
     * 设置为 {@code null} 可关闭事件分发。
     *
     * @param eventBusProvider 事件总线提供者
     */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public synchronized void initialize() {
        LinkedHashMap<String, CurrencyDefinition> definitionSnapshot = new LinkedHashMap<>(registeredDefinitions);
        LinkedHashMap<String, CurrencyBridge> bridgeSnapshot = new LinkedHashMap<>();
        for (CurrencyDefinition definition : definitionSnapshot.values()) {
            bridgeSnapshot.put(definition.id(), createBridge(definition));
        }
        definitions = immutableCopy(definitionSnapshot);
        bridges = immutableCopy(bridgeSnapshot);
    }

    /**
     * 动态注册额外的货币定义（不覆盖已有）。
     * 注册后需调用 {@link #initialize()} 生效。
     */
    public synchronized void registerCurrencies(Map<String, CurrencyDefinition> additional) {
        if (additional == null || additional.isEmpty()) {
            return;
        }
        for (Map.Entry<String, CurrencyDefinition> entry : additional.entrySet()) {
            registeredDefinitions.putIfAbsent(normalizeId(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public CurrencyBridge bridge(String currencyId) {
        return bridges.get(normalizeId(currencyId));
    }

    @Override
    public CurrencyDefinition definition(String currencyId) {
        return definitions.get(normalizeId(currencyId));
    }

    @Override
    public Collection<CurrencyDefinition> definitions() {
        return definitions.values();
    }

    @Override
    public Set<String> currencyIds() {
        return bridges.keySet();
    }

    @Override
    public String format(String currencyId, BigDecimal amount) {
        CurrencyBridge bridge = bridge(currencyId);
        CurrencyDefinition definition = bridge == null ? definitions.get(normalizeId(currencyId)) : bridge.definition();
        if (definition == null) {
            return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
        }
        BigDecimal scaled = (amount == null ? BigDecimal.ZERO : amount).setScale(definition.scale(), roundingMode(definition.rounding()));
        return scaled.stripTrailingZeros().toPlainString();
    }

    private CurrencyBridge createBridge(CurrencyDefinition definition) {
        CurrencyBridge delegate = switch (normalizeId(definition.provider())) {
            case "playerpoints" -> new PlayerPointsBridge(definition);
            case "xconomy" -> new XConomyCurrencyBridge(definition);
            case "placeholder-command", "command", "custom" -> new CommandCurrencyBridge(definition, placeholderResolver);
            case "rondo" -> new RondoCurrencyBridge(definition);
            default -> new VaultCurrencyBridge(definition);
        };
        return new EventDispatchingBridge(delegate, definition.id());
    }

    private abstract class AbstractCurrencyBridge implements CurrencyBridge {

        private final CurrencyDefinition definition;

        private AbstractCurrencyBridge(CurrencyDefinition definition) {
            this.definition = definition;
        }

        @Override
        public CurrencyDefinition definition() {
            return definition;
        }

        protected BigDecimal normalize(BigDecimal amount) {
            if (amount == null) {
                return BigDecimal.ZERO;
            }
            return amount.max(BigDecimal.ZERO).setScale(definition.scale(), roundingMode(definition.rounding()));
        }

        protected String formatAmount(BigDecimal amount) {
            return normalize(amount).stripTrailingZeros().toPlainString();
        }
    }

    private final class VaultCurrencyBridge extends AbstractCurrencyBridge {

        private net.milkbowl.vault.economy.Economy economy;
        private String unavailableReason = "";

        private record VaultOperation(CurrencyTransactionResult result, BigDecimal balanceAfter) { }

        private VaultCurrencyBridge(CurrencyDefinition definition) {
            super(definition);
            initializeVault();
        }

        @Override
        public boolean available() {
            if (economy == null) {
                initializeVault();
            }
            return economy != null;
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            try {
                return BigDecimal.valueOf(economy.getBalance(player));
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] Vault balance 查询失败: " + exception.getMessage());
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokeEconomyTransaction(player, amount, "扣款失败。", true).result();
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokeEconomyTransaction(player, amount, "入账失败。", false).result();
        }

        @Override
        public Supplier<BigDecimal> prepareBackgroundBalance(Player player) {
            if (player == null) return null;
            String currency = rondoVaultCurrency();
            if (currency == null) return null;
            UUID account = player.getUniqueId();
            return () -> priv.seventeen.artist.rondo.api.RondoAPI.INSTANCE.getBalance(account, currency);
        }

        @Override
        public Supplier<CurrencyTransactionResult> prepareBackgroundCredit(Player player, BigDecimal amount) {
            if (player == null || amount == null || amount.signum() <= 0) return null;
            String currency = rondoVaultCurrency();
            if (currency == null) return null;
            // Vault rounds through double and its Rondo adapter treats zero as a successful no-op.
            double value = normalize(amount).doubleValue();
            if (!Double.isFinite(value)) return () -> CurrencyTransactionResult.failure(RANGE_FAILURE);
            if (value == 0) return CurrencyTransactionResult::ok;
            return rondoCredit(player.getUniqueId(), currency, BigDecimal.valueOf(value), "vault");
        }

        private String rondoVaultCurrency() {
            if (!available()) return null;
            try {
                Plugin rondo = Bukkit.getPluginManager().getPlugin("Rondo");
                var registration = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rondo == null || !rondo.isEnabled() || registration == null
                    || registration.getProvider() != economy || registration.getPlugin() != rondo
                    || !economy.getClass().getName().equals("priv.seventeen.artist.rondo.vault.RondoEconomy")) return null;
                var currency = priv.seventeen.artist.rondo.currency.CurrencyRegistry.INSTANCE.getVaultPrimary();
                return currency == null ? null : currency.getId();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        private void initializeVault() {
            if (economy != null) {
                return;
            }
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault == null) {
                unavailableReason = "Vault 未安装";
                return;
            }
            try {
                RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (registration == null || registration.getProvider() == null) {
                    unavailableReason = "Vault Economy 服务未注册";
                    return;
                }
                economy = registration.getProvider();
                unavailableReason = "";
            } catch (Exception | LinkageError exception) {
                unavailableReason = "Vault API 初始化失败: " + exception.getMessage();
            }
        }

        private VaultOperation invokeEconomyTransaction(Player player, BigDecimal amount, String defaultMessage, boolean isWithdraw) {
            if (!available()) {
                return new VaultOperation(CurrencyTransactionResult.failure(unavailableReason()), null);
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return new VaultOperation(CurrencyTransactionResult.failure("金额必须大于 0。"), null);
            }
            try {
                double value = normalize(amount).doubleValue();
                net.milkbowl.vault.economy.EconomyResponse response = isWithdraw
                    ? economy.withdrawPlayer(player, value)
                    : economy.depositPlayer(player, value);
                if (response.transactionSuccess()) {
                    return new VaultOperation(CurrencyTransactionResult.ok(),
                        reportedVaultBalance(response.balance));
                }
                return new VaultOperation(CurrencyTransactionResult.failure(response.errorMessage != null
                    && !response.errorMessage.isBlank() ? response.errorMessage : defaultMessage), null);
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] Vault " + (isWithdraw ? "withdraw" : "deposit") + " 失败: " + exception.getMessage());
                return new VaultOperation(CurrencyTransactionResult.unknown(defaultMessage), null);
            }
        }

        private BigDecimal reportedVaultBalance(double balance) {
            // XConomy returns the pre-credit balance even on success.
            if (isXConomy()) return null;
            return Double.isFinite(balance) ? BigDecimal.valueOf(balance) : null;
        }

        @Override
        public boolean creditRequiresReconciliation() {
            return reconciliationRequired(economy);
        }

        @Override
        public boolean debitRequiresReconciliation() {
            return isXConomy();
        }

        @Override
        public boolean supportsExactAmount(BigDecimal amount) {
            if (!super.supportsExactAmount(amount) || !available()) return false;
            double number = amount.doubleValue();
            if (!Double.isFinite(number) || BigDecimal.valueOf(number).compareTo(amount) != 0) return false;
            try {
                int digits = economy.fractionalDigits();
                return digits < 0 || amount.stripTrailingZeros().scale() <= digits;
            } catch (RuntimeException | LinkageError failure) {
                return false;
            }
        }

        private boolean isXConomy() {
            return economy != null && economy.getClass().getName().equals("me.yic.xconomy.depend.economy.Vault");
        }
    }

    private final class PlayerPointsBridge extends AbstractCurrencyBridge {
        @Override
        public boolean supportsExactAmount(BigDecimal amount) {
            return super.supportsExactAmount(amount) && amount.stripTrailingZeros().scale() <= 0
                && amount.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        }


        private org.black_ixx.playerpoints.PlayerPointsAPI api;
        private String unavailableReason = "";

        private PlayerPointsBridge(CurrencyDefinition definition) {
            super(definition);
            initializePlayerPoints();
        }

        @Override
        public boolean available() {
            return api != null;
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            try {
                return BigDecimal.valueOf(api.look(player.getUniqueId()));
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] PlayerPoints balance 查询失败: " + exception.getMessage());
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokePointsTransaction(player, amount, "扣除点券失败。", true);
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokePointsTransaction(player, amount, "发放点券失败。", false);
        }

        private void initializePlayerPoints() {
            Plugin playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (playerPoints == null) {
                unavailableReason = "PlayerPoints 未安装";
                return;
            }
            try {
                if (playerPoints instanceof org.black_ixx.playerpoints.PlayerPoints pp) {
                    api = pp.getAPI();
                } else {
                    org.black_ixx.playerpoints.PlayerPoints pp = org.black_ixx.playerpoints.PlayerPoints.getInstance();
                    api = pp.getAPI();
                }
                if (api == null) {
                    unavailableReason = "PlayerPoints API 不可用";
                }
            } catch (Exception | LinkageError exception) {
                unavailableReason = "PlayerPoints API 初始化失败: " + exception.getMessage();
            }
        }

        private CurrencyTransactionResult invokePointsTransaction(Player player, BigDecimal amount, String defaultMessage, boolean isTake) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            int points;
            try {
                points = normalize(amount).intValueExact();
            } catch (ArithmeticException exception) {
                return CurrencyTransactionResult.failure(RANGE_FAILURE);
            }
            if (points <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            try {
                boolean success = isTake
                    ? api.take(player.getUniqueId(), points)
                    : api.give(player.getUniqueId(), points);
                return success ? CurrencyTransactionResult.ok() : CurrencyTransactionResult.failure(defaultMessage);
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] PlayerPoints " + (isTake ? "take" : "give") + " 失败: " + exception.getMessage());
                return CurrencyTransactionResult.unknown(defaultMessage);
            }
        }
    }

    private final class XConomyCurrencyBridge extends AbstractCurrencyBridge {

        private me.yic.xconomy.api.XConomyAPI api;
        private String unavailableReason = "";

        private XConomyCurrencyBridge(CurrencyDefinition definition) {
            super(definition);
            initializeXConomy();
        }

        @Override
        public boolean creditRequiresReconciliation() {
            return true;
        }

        @Override
        public boolean debitRequiresReconciliation() {
            // The native return code acknowledges the balance change, with no durable receipt.
            return true;
        }

        @Override
        public boolean available() {
            return api != null;
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            try {
                me.yic.xconomy.data.syncdata.PlayerData playerData = api.getPlayerData(player.getUniqueId());
                if (playerData == null) {
                    return BigDecimal.ZERO;
                }
                BigDecimal balance = playerData.getBalance();
                return balance != null ? balance : BigDecimal.ZERO;
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] XConomy balance 查询失败: " + exception.getMessage());
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return changeBalance(player, amount, Boolean.FALSE, "扣款失败。");
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return changeBalance(player, amount, Boolean.TRUE, "入账失败。");
        }

        private CurrencyTransactionResult changeBalance(
            Player player,
            BigDecimal amount,
            Boolean isAdd,
            String defaultMessage
        ) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            try {
                int result = api.changePlayerBalance(
                    player.getUniqueId(),
                    player.getName(),
                    normalize(amount),
                    isAdd,
                    "ArcartXSuite"
                );
                return switch (result) {
                    case 0 -> CurrencyTransactionResult.ok();
                    case 1 -> CurrencyTransactionResult.failure("当前不允许修改余额。");
                    case 2 -> CurrencyTransactionResult.failure("余额不足。");
                    case 3 -> CurrencyTransactionResult.failure(RANGE_FAILURE);
                    default -> CurrencyTransactionResult.failure(defaultMessage);
                };
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] XConomy changeBalance 失败: " + exception.getMessage());
                return CurrencyTransactionResult.unknown(defaultMessage);
            }
        }

        private void initializeXConomy() {
            Plugin xconomy = Bukkit.getPluginManager().getPlugin("XConomy");
            if (xconomy == null) {
                unavailableReason = "XConomy 未安装";
                return;
            }
            try {
                api = new me.yic.xconomy.api.XConomyAPI();
            } catch (Exception | LinkageError exception) {
                unavailableReason = "XConomy API 初始化失败: " + exception.getMessage();
            }
        }
    }

    private final class CommandCurrencyBridge extends AbstractCurrencyBridge {

        private final PlaceholderResolverAPI placeholderResolver;
        private String unavailableReason = "";

        private CommandCurrencyBridge(CurrencyDefinition definition, PlaceholderResolverAPI placeholderResolver) {
            super(definition);
            this.placeholderResolver = placeholderResolver;
            initializePlaceholderApi();
        }

        @Override
        public boolean available() {
            return placeholderResolver != null
                && !definition().balancePlaceholder().isBlank()
                && !definition().withdrawCommand().isBlank()
                && !definition().depositCommand().isBlank();
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            String resolved = placeholderResolver.applyPlaceholders(player, definition().balancePlaceholder());
            return parseNumericString(resolved);
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            Object lock = customOperationLocks.computeIfAbsent(player.getUniqueId(), ignored -> new Object());
            synchronized (lock) {
                if (balance(player).compareTo(normalize(amount)) < 0) {
                    return CurrencyTransactionResult.failure("余额不足。");
                }
                // 仅串行化本插件内的操作；外部插件并发修改余额仍由外部系统负责。
                return dispatchConsoleCommand(definition().withdrawCommand(), player, amount)
                    ? CurrencyTransactionResult.ok()
                    : CurrencyTransactionResult.failure("执行扣款命令失败。");
            }
        }
        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            String command = renderConsoleCommand(definition().depositCommand(), player, amount);
            if (command == null) return CurrencyTransactionResult.failure("发放命令未通过校验。");
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                ? CurrencyTransactionResult.ok()
                : CurrencyTransactionResult.unknown("发放命令未能确认入账结果。");
        }

        @Override
        public boolean creditRequiresReconciliation() {
            // Bukkit reports command handling, not the currency provider's durable result.
            return true;
        }

        private void initializePlaceholderApi() {
            if (placeholderResolver == null) {
                unavailableReason = "PlaceholderAPI 解析器未注入";
                return;
            }
            if (!placeholderResolver.available()) {
                unavailableReason = "PlaceholderAPI 未安装";
            }
        }

        private boolean dispatchConsoleCommand(String template, Player player, BigDecimal amount) {
            String command = renderConsoleCommand(template, player, amount);
            return command != null && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        private String renderConsoleCommand(String template, Player player, BigDecimal amount) {
            String playerName = player.getName();
            if (!MINECRAFT_NAME_PATTERN.matcher(playerName).matches()) {
                LOGGER.warning("[" + definition().id() + "] 拒绝执行命令：玩家名包含非法字符: " + playerName);
                return null;
            }
            String amountStr = formatAmount(amount);
            String rendered = template
                .replace("{player}", playerName)
                .replace("%player%", playerName)
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("{amount}", amountStr)
                .replace("%amount%", amountStr);
            // 防止换行注入多条命令
            if (rendered.indexOf('\n') >= 0 || rendered.indexOf('\r') >= 0) {
                LOGGER.warning("[" + definition().id() + "] 拒绝执行命令：渲染结果包含换行符");
                return null;
            }
            if (rendered.startsWith("/")) {
                rendered = rendered.substring(1);
            }
            LOGGER.fine("[" + definition().id() + "] 执行货币命令: " + rendered);
            return rendered.isBlank() ? null : rendered;
        }
    }

    private final class RondoCurrencyBridge extends AbstractCurrencyBridge {

        // Rondo 原生 API（多货币场景使用）
        private priv.seventeen.artist.rondo.api.RondoAPI rondoApi;
        private String rondoUnavailableReason = "";

        private final VaultCurrencyBridge fallback;

        private RondoCurrencyBridge(CurrencyDefinition definition) {
            super(definition);
            initializeRondo();
            fallback = rondoApi == null ? new VaultCurrencyBridge(definition) : null;
        }

        @Override
        public boolean available() {
            return fallback != null ? fallback.available() : rondoApi.getCurrency(definition().id()) != null;
        }

        @Override
        public String unavailableReason() {
            if (available()) {
                return "";
            }
            if (fallback != null) {
                String reason = fallback.unavailableReason();
                return reason.isBlank() ? rondoUnavailableReason : reason;
            }
            return "Rondo 未注册货币 " + definition().id();
        }

        @Override
        public BigDecimal balance(Player player) {
            if (player == null) return BigDecimal.ZERO;
            // Vault 只暴露一个主货币，原生 Rondo 保留配置指定的货币 ID。
            if (fallback != null) return fallback.balance(player);
            try {
                BigDecimal result = rondoApi.getBalance(player.getUniqueId(), definition().id());
                return result != null ? result : BigDecimal.ZERO;
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] Rondo balance 查询失败: " + exception.getMessage());
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokeTransaction(player, amount, "扣款失败。", true);
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokeTransaction(player, amount, "入账失败。", false);
        }

        @Override
        public Supplier<BigDecimal> prepareBackgroundBalance(Player player) {
            if (fallback != null || player == null || !available()) return null;
            UUID account = player.getUniqueId();
            return () -> rondoApi.getBalance(account, definition().id());
        }

        @Override
        public Supplier<CurrencyTransactionResult> prepareBackgroundDebit(Player player, BigDecimal amount) {
            if (fallback != null || player == null || !available()) return null;
            if (amount == null || amount.signum() <= 0) return () -> CurrencyTransactionResult.failure("金额必须大于 0。");
            UUID account = player.getUniqueId();
            return () -> invokeNativeTransaction(account, amount, "扣款失败。", true);
        }

        @Override
        public Supplier<CurrencyTransactionResult> prepareBackgroundCredit(Player player, BigDecimal amount) {
            if (fallback != null) return fallback.prepareBackgroundCredit(player, amount);
            if (player == null || amount == null || amount.signum() <= 0 || !available()) return null;
            return rondoCredit(player.getUniqueId(), definition().id(), normalize(amount), "ArcartXSuite");
        }

        @Override
        public boolean creditRequiresReconciliation() {
            return fallback != null && fallback.creditRequiresReconciliation();
        }

        @Override
        public boolean debitRequiresReconciliation() {
            return fallback != null && fallback.debitRequiresReconciliation();
        }

        @Override
        public boolean supportsExactAmount(BigDecimal amount) {
            if (fallback != null) return fallback.supportsExactAmount(amount);
            if (!super.supportsExactAmount(amount)) return false;
            try {
                var currency = rondoApi.getCurrency(definition().id());
                return currency != null && amount.stripTrailingZeros().scale() <= currency.getDecimalPlaces();
            } catch (RuntimeException | LinkageError failure) {
                return false;
            }
        }

        private CurrencyTransactionResult invokeTransaction(
            Player player, BigDecimal amount, String defaultMessage, boolean isWithdraw
        ) {
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            if (fallback != null) {
                return isWithdraw ? fallback.withdraw(player, amount) : fallback.deposit(player, amount);
            }
            if (!available()) return CurrencyTransactionResult.failure(unavailableReason());
            return invokeNativeTransaction(player.getUniqueId(), amount, defaultMessage, isWithdraw);
        }

        private CurrencyTransactionResult invokeNativeTransaction(UUID account, BigDecimal amount, String defaultMessage, boolean isWithdraw) {
            try {
                boolean success = isWithdraw
                    ? rondoApi.withdraw(account, definition().id(), normalize(amount), "ArcartXSuite")
                    : rondoApi.deposit(account, definition().id(), normalize(amount), "ArcartXSuite");
                return success ? CurrencyTransactionResult.ok() : CurrencyTransactionResult.failure("Rondo " + defaultMessage);
            } catch (Exception | LinkageError exception) {
                LOGGER.warning("[" + definition().id() + "] Rondo " + (isWithdraw ? "withdraw" : "deposit") + " 失败: " + exception.getMessage());
                return CurrencyTransactionResult.unknown(defaultMessage);
            }
        }

        private void initializeRondo() {
            Plugin rondo = Bukkit.getPluginManager().getPlugin("Rondo");
            if (rondo == null) {
                rondoUnavailableReason = "Rondo 未安装";
                return;
            }
            try {
                rondoApi = priv.seventeen.artist.rondo.api.RondoAPI.INSTANCE;
                if (rondoApi == null) {
                    rondoUnavailableReason = "Rondo API 实例不可用";
                }
            } catch (Exception | LinkageError exception) {
                rondoUnavailableReason = "Rondo API 初始化失败: " + exception.getMessage();
            }
        }
    }

    private static Supplier<CurrencyTransactionResult> rondoCredit(UUID account, String currency, BigDecimal amount, String source) {
        var api = priv.seventeen.artist.rondo.api.RondoAPI.INSTANCE;
        return () -> {
            try {
                return api.deposit(account, currency, amount, source) ? CurrencyTransactionResult.ok()
                    : CurrencyTransactionResult.failure("Rondo 入账失败。");
            } catch (RuntimeException | LinkageError exception) {
                LOGGER.warning("[" + currency + "] Rondo 后台入账失败: " + exception.getMessage());
                return CurrencyTransactionResult.unknown("Rondo 入账结果未知。");
            }
        };
    }

    private static boolean reconciliationRequired(net.milkbowl.vault.economy.Economy economy) {
        if (economy == null) return false;
        return switch (economy.getClass().getName()) {
            case "me.yic.xconomy.depend.economy.Vault",
                 "com.earth2me.essentials.economy.vault.VaultEconomyProvider",
                 "net.milkbowl.vault.economy.plugins.Economy_Essentials",
                 "net.tnemc.bukkit.hook.economy.TNEVault" -> true;
            default -> false;
        };
    }

    private static <T> Map<String, T> immutableCopy(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static RoundingMode roundingMode(String value) {
        if (value == null || value.isBlank()) {
            return RoundingMode.DOWN;
        }
        try {
            return RoundingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return RoundingMode.DOWN;
        }
    }

    private static BigDecimal parseNumericString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = rawValue
            .replace(",", "")
            .replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            LOGGER.warning("货币余额解析失败，原始值: " + rawValue + "，清洗后: " + normalized + "，返回 0");
            return BigDecimal.ZERO;
        }
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 事件分发装饰器，包装真实货币桥接，在 withdraw/deposit 成功后通过事件总线分发货币变动事件。
     * <p>
     * 分发的事件主题：
     * <ul>
     *   <li>{@code axs.currency.spent} — 扣款成功时</li>
     *   <li>{@code axs.currency.earned} — 入账成功时</li>
     * </ul>
     */
    private final class EventDispatchingBridge implements CurrencyBridge {

        private final CurrencyBridge delegate;
        private final String currencyId;

        private EventDispatchingBridge(CurrencyBridge delegate, String currencyId) {
            this.delegate = delegate;
            this.currencyId = currencyId;
        }

        @Override
        public CurrencyDefinition definition() {
            return delegate.definition();
        }

        @Override
        public boolean available() {
            return delegate.available();
        }

        @Override
        public String unavailableReason() {
            return delegate.unavailableReason();
        }

        @Override
        public BigDecimal balance(Player player) {
            return delegate.balance(player);
        }

        @Override
        public Supplier<BigDecimal> prepareBackgroundBalance(Player player) {
            return delegate.prepareBackgroundBalance(player);
        }

        @Override
        public Supplier<CurrencyTransactionResult> prepareBackgroundDebit(Player player, BigDecimal amount) {
            var debit = delegate.prepareBackgroundDebit(player, amount);
            if (debit == null) return null;
            var balance = delegate.prepareBackgroundBalance(player);
            UUID account = player.getUniqueId();
            return () -> {
                var result = debit.get();
                if (result.success()) publishBackgroundChange(account, "withdraw", amount, balance);
                return result;
            };
        }

        @Override
        public Supplier<CurrencyTransactionResult> prepareBackgroundCredit(Player player, BigDecimal amount) {
            var credit = delegate.prepareBackgroundCredit(player, amount);
            if (credit == null) return null;
            var balance = delegate.prepareBackgroundBalance(player);
            UUID account = player.getUniqueId();
            return () -> {
                var result = credit.get();
                if (result.success()) publishBackgroundChange(account, "deposit", amount, balance);
                return result;
            };
        }

        @Override
        public boolean creditRequiresReconciliation() {
            return delegate.creditRequiresReconciliation();
        }

        @Override
        public boolean debitRequiresReconciliation() {
            return delegate.debitRequiresReconciliation();
        }

        @Override
        public boolean supportsExactAmount(BigDecimal amount) {
            return delegate.supportsExactAmount(amount);
        }

        private void publishBackgroundChange(UUID account, String action, BigDecimal amount, Supplier<BigDecimal> balance) {
            var provider = eventBusProvider;
            if (provider == null || !plugin.isEnabled()) return;
            try {
                var bus = provider.get();
                if (bus == null || !bus.hasSubscribers(currencyTopic(action))) return;
                BigDecimal balanceAfter = balance == null ? BigDecimal.ZERO : balance.get();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(account);
                    if (online != null) publishCurrencyEvent(online, action, amount, () -> balanceAfter);
                });
            } catch (Exception | LinkageError exception) {
                LOGGER.fine("[" + currencyId + "] 后台货币事件分发失败: " + exception.getMessage());
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            CurrencyTransactionResult result = delegate.withdraw(player, amount);
            if (result.success()) {
                publishCurrencyEvent(player, "withdraw", amount);
            }
            return result;
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            if (delegate instanceof VaultCurrencyBridge vault) {
                var operation = vault.invokeEconomyTransaction(player, amount, "入账失败。", false);
                if (operation.result().success()) {
                    var balance = operation.balanceAfter();
                    publishCurrencyEvent(player, "deposit", amount, balance == null ? () -> vault.balance(player) : () -> balance);
                }
                return operation.result();
            }
            CurrencyTransactionResult result = delegate.deposit(player, amount);
            if (result.success()) {
                publishCurrencyEvent(player, "deposit", amount);
            }
            return result;
        }

        private void publishCurrencyEvent(Player player, String action, BigDecimal amount) {
            publishCurrencyEvent(player, action, amount, () -> delegate.balance(player));
        }

        private void publishCurrencyEvent(Player player, String action, BigDecimal amount, Supplier<BigDecimal> balance) {
            Supplier<EventBusCapability> provider = eventBusProvider;
            if (provider == null || player == null) return;
            try {
                EventBusCapability bus = provider.get();
                String topic = currencyTopic(action);
                if (bus == null || !bus.hasSubscribers(topic)) return;
                Map<String, String> payload = new LinkedHashMap<>(4);
                payload.put("currency_id", currencyId);
                payload.put("amount", amount == null ? "0" : amount.stripTrailingZeros().toPlainString());
                payload.put("action", action);
                // 尝试获取变动后余额（best-effort，失败不影响主流程）
                try {
                    BigDecimal balanceAfter = balance.get();
                    payload.put("balance_after", balanceAfter.stripTrailingZeros().toPlainString());
                } catch (Exception exception) {
                    LOGGER.fine("[" + currencyId + "] 货币事件 balance_after 获取失败: " + exception.getMessage());
                    payload.put("balance_after", "0");
                }
                bus.publish(topic, player, payload);
            } catch (Exception exception) {
                // 事件分发失败不应影响货币操作主流程
                LOGGER.fine("[" + currencyId + "] 货币事件分发失败: " + exception.getMessage());
            }
        }

        private String currencyTopic(String action) {
            return "withdraw".equals(action) ? "axs.currency.spent" : "axs.currency.earned";
        }
    }
}
