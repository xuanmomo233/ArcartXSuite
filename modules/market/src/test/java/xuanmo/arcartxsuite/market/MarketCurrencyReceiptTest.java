package xuanmo.arcartxsuite.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.market.storage.JdbcMarketRepository;

class MarketCurrencyReceiptTest {
    @TempDir Path directory;

    @Test void acknowledgedCreditStillRequiresDurableReconciliation() throws Exception {
        exercise(true, true, CurrencyTransactionResult.ok(), "REVIEW");
    }
    @Test void unknownDebitCannotBeRecordedAsDefiniteFailure() throws Exception {
        exercise(false, false, CurrencyTransactionResult.unknown("timeout"), "REVIEW");
    }
    @Test void definiteDebitFailureRemainsFailed() throws Exception {
        exercise(false, false, CurrencyTransactionResult.failure("insufficient"), "FAILED");
    }
    @Test void confirmedCreditCanComplete() throws Exception {
        exercise(true, false, CurrencyTransactionResult.ok(), "DONE");
    }

    private void exercise(boolean credit, boolean reconcile, CurrencyTransactionResult result, String state) throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("currency.sqlite"));
        var repository = new JdbcMarketRepository(source, StorageDescriptor.sqlite("currency.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var ledger = new MarketLedger(repository, Logger.getAnonymousLogger());
        var currencies = mock(CurrencyBridgeAPI.class);
        var provider = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currencies.bridge("money")).thenReturn(provider);
        when(provider.creditRequiresReconciliation()).thenReturn(reconcile);
        var player = mock(Player.class);
        var owner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);
        var amount = BigDecimal.TEN;
        when(provider.deposit(player, amount)).thenReturn(result);
        when(provider.withdraw(player, amount)).thenReturn(result);
        var bridge = ledger.wrap(currencies).bridge("money");
        assertEquals(reconcile, bridge.creditRequiresReconciliation());
        assertEquals(result, credit ? bridge.deposit(player, amount) : bridge.withdraw(player, amount));
        var operations = repository.operations(null, owner, 0, 10);
        assertEquals(1, operations.size());
        assertEquals(state, operations.get(0).state());
        assertEquals(credit ? "CREDIT" : "DEBIT", operations.get(0).kind());
        if (credit) verify(provider).deposit(player, amount);
        else verify(provider).withdraw(player, amount);
    }
}
