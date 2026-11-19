package xuanmo.arcartxsuite.mail.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimPhase;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimProgress;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ItemClaim;

class MailClaimProgressTest {
    @TempDir Path directory;

    private final UUID owner = UUID.randomUUID();
    private DataSource source;
    private JdbcMailRepository repository;
    private CurrencyBridgeAPI currencies;
    private MailService service;
    private Player player;
    private MockedStatic<AuthAccess> admission;

    @BeforeEach
    void initialize() throws Exception {
        source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(invocation -> {
            var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("mail.sqlite"));
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            }
            return connection;
        });
        repository = newRepository();
        var logger = Logger.getAnonymousLogger();
        currencies = mock(CurrencyBridgeAPI.class);
        service = spy(new MailService(mock(JavaPlugin.class), logger, directory.toFile(),
            MailModuleConfiguration.load(new YamlConfiguration(), logger), repository,
            null, null, null, null, null, currencies, null, null));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn("MailTester");
        when(player.isOnline()).thenReturn(true);
        doReturn(true).when(service).isCurrentClaimPlayer(player, owner);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).scheduleClaimWorker(any());
        var active = MailService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        admission = mockStatic(AuthAccess.class);
        var snapshot = new AuthSnapshot(owner, UUID.randomUUID(), 1, owner, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", java.util.Set.of(), "");
        admission.when(() -> AuthAccess.snapshot(owner)).thenReturn(snapshot);
    }

    @AfterEach
    void closeAdmission() {
        if (admission != null) admission.close();
    }

    private JdbcMailRepository newRepository() throws Exception {
        var result = new JdbcMailRepository(source, StorageDescriptor.sqlite("mail.sqlite"), directory.toFile(),
            Logger.getAnonymousLogger());
        result.initialize();
        return result;
    }

    private CurrencyBridge bridge(String id) {
        var bridge = mock(CurrencyBridge.class);
        when(currencies.bridge(id)).thenReturn(bridge);
        when(bridge.available()).thenReturn(true);
        return bridge;
    }

    private MailAttachment currency(String id, int order, double amount) {
        return new MailAttachment(0L, order, MailAttachmentType.CURRENCY, "", id, amount, "", null);
    }

    private boolean claim(long id) {
        var result = service.startClaim(player, id, false);
        for (int i = 0; i < 30 && !result.isDone(); i++) service.drainClaimEffects();
        assertTrue(result.isDone());
        return result.join().success();
    }

    @Test
    void singleItemClaimKeepsThePreviousUnconfirmedPlayerReceipt() throws Exception {
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        var contents = new java.util.concurrent.atomic.AtomicReference<>(new org.bukkit.inventory.ItemStack[36]);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenAnswer(call -> contents.get());
        doAnswer(call -> { contents.set(call.getArgument(0)); return null; })
            .when(inventory).setStorageContents(any(org.bukkit.inventory.ItemStack[].class));
        var data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        var stored = new java.util.HashMap<String, String>();
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(org.bukkit.NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.STRING)))
            .thenAnswer(call -> stored.get(call.getArgument(0).toString()));
        doAnswer(call -> { stored.put(call.getArgument(0).toString(), call.getArgument(2)); return null; })
            .when(data).set(any(org.bukkit.NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.STRING), anyString());
        doAnswer(call -> { stored.remove(call.getArgument(0).toString()); return null; }).when(data).remove(any());
        var path = MailService.class.getDeclaredField("playerData");
        path.setAccessible(true);
        path.set(service, directory);
        doAnswer(call -> {
            try (var out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(
                    java.nio.file.Files.newOutputStream(directory.resolve(owner + ".dat"))))) {
                out.writeByte(10); out.writeUTF("");
                out.writeByte(10); out.writeUTF("BukkitValues");
                for (var entry : stored.entrySet()) {
                    out.writeByte(8); out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
                }
                out.writeByte(0); out.writeByte(0);
            }
            return null;
        }).when(player).saveData();
        var attachment = new MailAttachment(0, 0, MailAttachmentType.ITEM, "test-item", "", 0, "", null);
        long first = repository.insertMail(service.systemMail(owner, "First", "", List.of(attachment), List.of(), null, "System")).id();
        long second = repository.insertMail(service.systemMail(owner, "Second", "", List.of(attachment), List.of(), null, "System")).id();
        try (var serializer = mockStatic(xuanmo.arcartxsuite.mail.util.MailItemSerializer.class)) {
            serializer.when(() -> xuanmo.arcartxsuite.mail.util.MailItemSerializer.deserialize("test-item"))
                .thenAnswer(call -> stack(1));
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_item_checkpoint BEFORE UPDATE OF claim_step ON mail_entries "
                    + "BEGIN SELECT RAISE(FAIL, 'checkpoint unavailable'); END");
            }
            assertFalse(claim(first));
            var receipt = repository.loadClaimProgress(owner, first);
            assertEquals(ClaimPhase.REVIEW, receipt.phase());
            assertFalse(claim(second));
            assertEquals(receipt.attempt().toString(), stored.get("arcartxsuite:mail_item_receipt"));
            assertEquals(ClaimPhase.NONE, repository.loadClaimProgress(owner, second).phase());
            assertEquals(1, java.util.Arrays.stream(contents.get()).filter(java.util.Objects::nonNull)
                .mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum());
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_item_checkpoint");
            }
            assertFalse(service.resolveClaimReview(owner, first, receipt.attempt(), false, "console").success());
            assertTrue(service.resolveClaimReview(owner, first, receipt.attempt(), true, "console").success());
            assertTrue(claim(second));
            assertEquals(2, java.util.Arrays.stream(contents.get()).filter(java.util.Objects::nonNull)
                .mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum());
        }
    }

    private static org.bukkit.inventory.ItemStack stack(int count) {
        var item = mock(org.bukkit.inventory.ItemStack.class);
        var amount = new java.util.concurrent.atomic.AtomicInteger(count);
        when(item.getType()).thenReturn(org.bukkit.Material.STONE);
        when(item.getAmount()).thenAnswer(call -> amount.get());
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.isSimilar(any())).thenReturn(true);
        doAnswer(call -> { amount.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(call -> stack(amount.get()));
        return item;
    }

    @Test
    void initializationAddsRequiredClaimColumnsWithoutChangingExistingMail() throws Exception {
        long mailId = repository.insertMail(service.systemMail(owner, "Stored reward", "Body",
            List.of(currency("cash", 0, 10)), List.of(), null, "System")).id();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            for (String column : List.of("claim_step", "claim_phase", "claim_attempt")) {
                statement.execute("ALTER TABLE mail_entries DROP COLUMN " + column);
            }
        }
        var initialized = newRepository();
        assertEquals(new ClaimProgress(0, ClaimPhase.NONE, null), initialized.loadClaimProgress(owner, mailId));
        assertEquals("Stored reward", initialized.loadMail(owner, mailId).orElseThrow().subject());
        assertEquals(10, initialized.loadMail(owner, mailId).orElseThrow().attachments().get(0).amount());
        assertTrue(initialized.reserveClaimStep(owner, mailId, 0, UUID.randomUUID(), Instant.now()));
    }

    @Test
    void aCommandWithAnUnknownOutcomeIsNotAutomaticallyExecutedAgain() throws Exception {
        long mailId = repository.insertMail(service.systemMail(owner, "Command", "", List.of(),
            List.of("say MailTester"), null, "System")).id();
        var console = mock(org.bukkit.command.ConsoleCommandSender.class);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(() -> org.bukkit.Bukkit.dispatchCommand(console, "say MailTester"))
                .thenThrow(new IllegalStateException("Command executed but its result was lost"));
            assertFalse(claim(mailId));
            assertFalse(claim(mailId));
            var progress = repository.loadClaimProgress(owner, mailId);
            assertEquals(ClaimPhase.REVIEW, progress.phase());
            bukkit.verify(() -> org.bukkit.Bukkit.dispatchCommand(console, "say MailTester"), times(1));
            assertTrue(service.resolveClaimReview(owner, mailId, progress.attempt(), true, "console").success());
            assertEquals(MailStatus.CLAIMED, repository.loadMail(owner, mailId).orElseThrow().status());
        }
    }

    @Test
    void anAcknowledgedButUnpersistedCurrencyIsNotAutomaticallyRetried() throws Exception {
        var cash = bridge("cash");
        when(cash.creditRequiresReconciliation()).thenReturn(true);
        when(cash.deposit(eq(player), any())).thenReturn(CurrencyTransactionResult.ok());
        long mailId = repository.insertMail(service.systemMail(owner, "Claim", "",
            List.of(currency("cash", 0, 10)), List.of(), null, "System")).id();
        assertFalse(claim(mailId));
        assertEquals(ClaimPhase.REVIEW, repository.loadClaimProgress(owner, mailId).phase());
        assertFalse(claim(mailId));
        verify(cash).deposit(eq(player), any());
    }

    @Test
    void failureInSecondCurrencyDoesNotReplayEarlierRewards() throws Exception {
        var cash = bridge("cash");
        var tokens = bridge("tokens");
        var cashAmount = BigDecimal.valueOf(100.0);
        var tokenAmount = BigDecimal.valueOf(2.0);
        when(cash.deposit(player, cashAmount)).thenReturn(CurrencyTransactionResult.ok());
        when(tokens.deposit(player, tokenAmount)).thenReturn(CurrencyTransactionResult.failure("provider timeout"),
            CurrencyTransactionResult.ok());
        var mailId = repository.insertMail(service.systemMail(owner, "Claim", "",
            List.of(currency("cash", 0, 100), currency("tokens", 1, 2)),
            List.of(), null, "System")).id();

        assertFalse(claim(mailId));
        assertEquals(MailStatus.CLAIM_REVIEW, repository.loadMail(owner, mailId).orElseThrow().status());
        var progress = repository.loadClaimProgress(owner, mailId);
        assertEquals(1, progress.step());
        assertEquals(ClaimPhase.REVIEW, progress.phase());
        assertFalse(claim(mailId));
        assertFalse(service.deleteMailByUuid(owner, mailId).success());
        verify(cash).deposit(player, cashAmount);
        verify(tokens).deposit(player, tokenAmount);
        assertEquals(0, repository.loadStats(owner).claimableCount());
        var adapter = new AxsMailServiceAdapter(mock(JavaPlugin.class), service, Logger.getAnonymousLogger(), false);
        try {
            assertEquals(xuanmo.arcartxsuite.api.mail.AxsMailState.CLAIM_REVIEW,
                adapter.getMail(owner, mailId).get(5, TimeUnit.SECONDS).state());
        } finally { adapter.close(); }

        assertTrue(service.resolveClaimReview(owner, mailId, progress.attempt(), false, "console").success());
        assertEquals(MailStatus.CLAIM_PENDING, repository.loadMail(owner, mailId).orElseThrow().status());
        assertEquals(1, repository.loadStats(owner).claimableCount());
        assertTrue(claim(mailId));
        assertEquals(MailStatus.CLAIMED, repository.loadMail(owner, mailId).orElseThrow().status());
        verify(cash).deposit(player, cashAmount);
        verify(tokens, times(2)).deposit(player, tokenAmount);
    }

    @Test
    void databaseCheckpointFailurePreservesAmbiguousPaymentForReview() throws Exception {
        var cash = bridge("cash");
        var amount = BigDecimal.valueOf(100.0);
        when(cash.deposit(player, amount)).thenReturn(CurrencyTransactionResult.ok());
        var mailId = repository.insertMail(service.systemMail(owner, "Claim", "",
            List.of(currency("cash", 0, 100)), List.of(), null, "System")).id();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER fail_claim_checkpoint BEFORE UPDATE OF claim_step ON mail_entries
                BEGIN SELECT RAISE(FAIL, 'checkpoint unavailable'); END
                """);
        }
        assertFalse(claim(mailId));
        var progress = newRepository().loadClaimProgress(owner, mailId);
        assertEquals(ClaimPhase.REVIEW, progress.phase());
        assertEquals(0, progress.step());
        assertFalse(claim(mailId));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_claim_checkpoint");
        }
        assertTrue(service.resolveClaimReview(owner, mailId, progress.attempt(), true, "console").success());
        assertFalse(claim(mailId));
        verify(cash).deposit(player, amount);
    }

    @Test
    void sharedMysqlAllowsOnlyOneClaimAndRejectsAnOldCheckpoint() throws Exception {
        String url = System.getenv("AXS_EVENTPACKET_MYSQL_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url != null
            && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify(?:_[0-9]+)?"));
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection(url, "root", ""));
        var descriptor = StorageDescriptor.mysql("127.0.0.1", 1, "isolated", "root", "", 2, "");
        var a = new JdbcMailRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        var b = new JdbcMailRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        a.initialize(); b.initialize();
        long mailId = a.insertMail(service.systemMail(owner, "Claim", "",
            List.of(currency("cash", 0, 10)), List.of(), null, "System")).id();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var one = UUID.randomUUID(); var two = UUID.randomUUID(); var now = Instant.now();
        try {
            var left = pool.submit(() -> { start.await(); return a.reserveClaimStep(owner, mailId, 0, one, now); });
            var right = pool.submit(() -> { start.await(); return b.reserveClaimStep(owner, mailId, 0, two, now); });
            start.countDown();
            boolean leftWon = left.get(5, TimeUnit.SECONDS);
            assertNotEquals(leftWon, right.get(5, TimeUnit.SECONDS));
            var winner = leftWon ? one : two; var loser = leftWon ? two : one;
            assertFalse(b.completeClaimStep(owner, mailId, new ClaimProgress(0, ClaimPhase.EXECUTING, loser), true, now));
            assertTrue(a.markClaimReview(owner, mailId, 0, winner, now));
            assertTrue(b.completeClaimStep(owner, mailId, new ClaimProgress(0, ClaimPhase.REVIEW, winner), true, now));
            assertFalse(a.reserveClaimStep(owner, mailId, 0, UUID.randomUUID(), now));
            assertEquals(MailStatus.CLAIMED, b.loadMail(owner, mailId).orElseThrow().status());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void sharedStorageReservesOnlyOneAttemptAndRejectsStaleUpdates() throws Exception {
        var mailId = repository.insertMail(service.systemMail(owner, "Claim", "",
            List.of(currency("cash", 0, 100)), List.of(), null, "System")).id();
        var other = newRepository();
        var start = new CountDownLatch(1);
        var one = UUID.randomUUID();
        var two = UUID.randomUUID();
        var now = Instant.now();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var left = pool.submit(() -> {
                start.await();
                return repository.reserveClaimStep(owner, mailId, 0, one, now);
            });
            var right = pool.submit(() -> {
                start.await();
                return other.reserveClaimStep(owner, mailId, 0, two, now);
            });
            start.countDown();
            boolean firstWon = left.get(5, TimeUnit.SECONDS);
            assertNotEquals(firstWon, right.get(5, TimeUnit.SECONDS));
            var winner = firstWon ? one : two;
            var loser = firstWon ? two : one;
            var executing = repository.loadClaimProgress(owner, mailId);
            assertEquals(winner, executing.attempt());
            assertFalse(repository.releaseReviewedClaimStep(owner, mailId, executing, now));
            assertFalse(repository.completeClaimStep(owner, mailId, new ClaimProgress(0, ClaimPhase.EXECUTING, loser), true, now));
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT status, claimed_at FROM mail_entries")) {
                assertTrue(rows.next());
                assertEquals("CLAIM_REVIEW", rows.getString("status"));
                assertNull(rows.getObject("claimed_at"));
            }
            assertTrue(repository.markClaimReview(owner, mailId, 0, winner, now));
            var review = repository.loadClaimProgress(owner, mailId);
            assertTrue(other.releaseReviewedClaimStep(owner, mailId, review, now));
            var next = UUID.randomUUID();
            assertTrue(other.reserveClaimStep(owner, mailId, 0, next, now));
            assertFalse(repository.completeClaimStep(owner, mailId, executing, true, now));
            assertTrue(other.completeClaimStep(owner, mailId, new ClaimProgress(0, ClaimPhase.EXECUTING, next), true, now));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimPagesSkipUnavailableMailAndKeepAttachmentAndCursorOrdering() throws Exception {
        var attachments = List.of(currency("cash", 0, 5), currency("tokens", 1, 2));
        long first = repository.insertMail(service.systemMail(owner, "First", "", attachments, List.of(), null, "System")).id();
        long second = repository.insertMail(service.systemMail(owner, "Second", "", attachments, List.of(), null, "System")).id();
        long interrupted = repository.insertMail(service.systemMail(owner, "Interrupted", "", attachments, List.of(), null, "System")).id();
        repository.insertMail(service.systemMail(owner, "Plain", "", List.of(), List.of(), null, "System"));
        repository.insertMail(service.systemMail(UUID.randomUUID(), "Other account", "", attachments, List.of(), null, "System"));
        var now = Instant.now();
        assertTrue(repository.reserveClaimStep(owner, second, 0, UUID.randomUUID(), now));
        assertTrue(repository.completeClaimStep(owner, second, repository.loadClaimProgress(owner, second), false, now));
        assertTrue(repository.reserveClaimStep(owner, interrupted, 0, UUID.randomUUID(), now));

        var page = repository.loadClaimablePage(owner, Long.MAX_VALUE, 1, now);
        assertEquals(1, page.size());
        assertEquals(second, page.get(0).mail().id());
        assertEquals(MailStatus.CLAIM_PENDING, page.get(0).mail().status());
        assertEquals(new ClaimProgress(1, ClaimPhase.READY, null), page.get(0).progress());
        assertEquals(List.of("cash", "tokens"), page.get(0).mail().attachments().stream()
            .map(MailAttachment::normalizedCurrencyId).toList());
        assertEquals(first, repository.loadClaimablePage(owner, second, 2, now).get(0).mail().id());
        assertEquals(ClaimPhase.EXECUTING, repository.loadClaimMail(owner, interrupted).orElseThrow().progress().phase());
        assertTrue(repository.loadClaimMail(UUID.randomUUID(), second).isEmpty());
    }

    @Test
    void itemBatchRollsBackConflictsAndKeepsUnconfirmedReceipts() throws Exception {
        var now = Instant.now();
        long first = repository.insertMail(service.systemMail(owner, "First", "", List.of(), List.of(), null, "System")).id();
        long second = repository.insertMail(service.systemMail(owner, "Second", "", List.of(), List.of(), null, "System")).id();
        var a = new ItemClaim(first, 0, UUID.randomUUID(), true);
        var b = new ItemClaim(second, 0, UUID.randomUUID(), false);
        var missing = new ItemClaim(second + 1, 0, UUID.randomUUID(), false);
        assertFalse(repository.reserveItemClaims(owner, List.of(a, missing), List.of(), now));
        assertEquals(ClaimPhase.NONE, repository.loadClaimProgress(owner, first).phase());
        assertTrue(repository.reserveItemClaims(owner, List.of(a, b), List.of(), now));
        assertFalse(repository.finishItemClaims(owner, List.of(a, missing), true, now));
        assertEquals(a.attempt(), repository.loadClaimProgress(owner, first).attempt());
        assertEquals(b.attempt(), repository.loadClaimProgress(owner, second).attempt());
        assertFalse(repository.reserveItemClaims(owner, List.of(missing), List.of(a.attempt()), now));
        assertTrue(repository.finishItemClaims(owner, List.of(a, b), true, now));
        assertEquals(ClaimPhase.NONE, repository.loadClaimProgress(owner, first).phase());
        assertEquals(new ClaimProgress(1, ClaimPhase.READY, null), repository.loadClaimProgress(owner, second));
        assertFalse(repository.finishItemClaims(owner, List.of(a, b), true, now));
    }
}
