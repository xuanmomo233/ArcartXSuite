package xuanmo.arcartxsuite.bridge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import priv.seventeen.artist.rondo.api.RondoAPI;
import priv.seventeen.artist.rondo.api.PlayerEconomySnapshot;
import priv.seventeen.artist.rondo.api.CurrencyEconomySnapshot;
import priv.seventeen.artist.rondo.api.TransferResult;
import priv.seventeen.artist.rondo.ranking.RankingEntry;
import priv.seventeen.artist.rondo.log.TransactionLog;
import xuanmo.arcartxsuite.api.currency.RondoBridge;
import xuanmo.arcartxsuite.api.currency.RondoCurrencySnapshot;
import xuanmo.arcartxsuite.api.currency.RondoRankingEntry;
import xuanmo.arcartxsuite.api.currency.RondoTransactionLog;
import xuanmo.arcartxsuite.api.currency.RondoTransferResult;

/**
 * {@link RondoBridge} 的默认实现，桥接 Rondo 经济系统插件。
 * <p>
 * Rondo 为可选依赖，运行时通过插件管理器检测可用性。
 */
public final class DefaultRondoBridge implements RondoBridge {

    private boolean available;
    private RondoAPI rondoApi;

    public void initialize() {
        reset();

        Plugin rondo = Bukkit.getPluginManager().getPlugin("Rondo");
        if (rondo == null || !rondo.isEnabled()) {
            return;
        }

        try {
            rondoApi = RondoAPI.INSTANCE;
            if (rondoApi == null) {
                return;
            }
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Rondo] 桥接初始化成功");
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Rondo] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    // ─── 排行榜 ──────────────────────────────────────────────

    @Override
    public List<RondoRankingEntry> ranking(String currencyId, int page, int pageSize) {
        if (!available || currencyId == null) return Collections.emptyList();
        try {
            List<RankingEntry> raw = rondoApi.getRanking(currencyId, page, pageSize);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<RondoRankingEntry> result = new ArrayList<>(raw.size());
            for (RankingEntry entry : raw) {
                result.add(new RondoRankingEntry(
                    entry.getRank(),
                    entry.getPlayerUuid(),
                    entry.getPlayerName(),
                    entry.getBalance()
                ));
            }
            return result;
        } catch (Exception | LinkageError e) {
            return Collections.emptyList();
        }
    }

    @Override
    public @Nullable Integer playerRank(UUID playerUuid, String currencyId) {
        if (!available || playerUuid == null || currencyId == null) return null;
        try {
            return rondoApi.getPlayerRank(playerUuid, currencyId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ─── 转账 ────────────────────────────────────────────────

    @Override
    public RondoTransferResult transfer(UUID from, UUID to, String currencyId, BigDecimal amount) {
        if (!available || from == null || to == null || currencyId == null || amount == null) {
            return new RondoTransferResult(false, "参数无效", BigDecimal.ZERO);
        }
        try {
            TransferResult raw = rondoApi.transfer(from, to, currencyId, amount);
            return new RondoTransferResult(
                raw.getSuccess(),
                raw.getMessage(),
                raw.getTaxAmount() != null ? raw.getTaxAmount() : BigDecimal.ZERO
            );
        } catch (Exception | LinkageError e) {
            return new RondoTransferResult(false, e.getMessage(), BigDecimal.ZERO);
        }
    }

    // ─── 经济快照 ────────────────────────────────────────────

    @Override
    public @Nullable BigDecimal peekBalance(UUID playerUuid, String currencyId) {
        if (!available || playerUuid == null || currencyId == null) return null;
        try {
            PlayerEconomySnapshot snapshot = rondoApi.peekEconomySnapshot(playerUuid);
            if (snapshot == null) return null;
            return snapshot.getBalance(currencyId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<RondoCurrencySnapshot.Full> economySnapshot(UUID playerUuid) {
        if (!available || playerUuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return rondoApi.getEconomySnapshot(playerUuid).thenApply(this::convertSnapshot);
        } catch (Exception | LinkageError e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private RondoCurrencySnapshot.Full convertSnapshot(PlayerEconomySnapshot raw) {
        if (raw == null) return null;
        try {
            Map<String, CurrencyEconomySnapshot> rawCurrencies = raw.getCurrencies();
            Map<String, RondoCurrencySnapshot> currencies = new java.util.HashMap<>(rawCurrencies.size());
            for (Map.Entry<String, CurrencyEconomySnapshot> entry : rawCurrencies.entrySet()) {
                CurrencyEconomySnapshot ces = entry.getValue();
                currencies.put(entry.getKey(), new RondoCurrencySnapshot(
                    ces.getBalance(),
                    ces.getTotalEarned(),
                    ces.getTotalSpent()
                ));
            }
            return new RondoCurrencySnapshot.Full(
                raw.getPlayerUuid(),
                Collections.unmodifiableMap(currencies),
                raw.getUpdatedAtEpochMillis()
            );
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ─── 交易日志 ────────────────────────────────────────────

    @Override
    public List<RondoTransactionLog> log(UUID playerUuid, @Nullable String currencyId, int page, int pageSize) {
        if (!available || playerUuid == null) return Collections.emptyList();
        try {
            List<TransactionLog> raw = rondoApi.getLog(playerUuid, currencyId, page, pageSize);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<RondoTransactionLog> result = new ArrayList<>(raw.size());
            for (TransactionLog entry : raw) {
                result.add(new RondoTransactionLog(
                    entry.getCurrencyId(),
                    entry.getAction() != null ? entry.getAction().name() : "UNKNOWN",
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    entry.getSource(),
                    entry.getTimestamp()
                ));
            }
            return result;
        } catch (Exception | LinkageError e) {
            return Collections.emptyList();
        }
    }

    private void reset() {
        available = false;
        rondoApi = null;
    }
}
