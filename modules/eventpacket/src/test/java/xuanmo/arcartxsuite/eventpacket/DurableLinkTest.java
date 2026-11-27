package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;

class DurableLinkTest {
    @TempDir Path folder;

    @Test
    void replayedAuctionConfirmationCreatesOnePersistedRewardMail() throws Exception {
        var source = source(false, "jdbc:sqlite:" + folder.resolve("auction-mail.sqlite"));
        var repository = repository(source, false);
        repository.initialize();
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              auction-bonus:
                trigger: auction-purchased
                actions:
                  - { type: mail, id: bonus, title: '成交 #{listing_id}', content: '实付 {formatted_price}', currency: money, amount: 2 }
            """);
        var config = RuleConfiguration.parse(yaml);
        var logger = Logger.getAnonymousLogger();
        var service = new xuanmo.arcartxsuite.mail.service.MailService(mock(org.bukkit.plugin.java.JavaPlugin.class),
            logger, folder.toFile(), xuanmo.arcartxsuite.mail.config.MailModuleConfiguration.load(new YamlConfiguration(), logger),
            repository, null, null, null, null, null, null, null, null);
        var mail = new xuanmo.arcartxsuite.mail.service.AxsMailServiceAdapter(
            mock(org.bukkit.plugin.java.JavaPlugin.class), service, logger, false);
        var buyer = UUID.randomUUID();
        var fields = java.util.Map.of("event_id", "auction-expiry:42", "listing_id", "42", "formatted_price", "20");
        var worker = new DurableRules(new RuleJournal(source, false), () -> null, () -> mail, () -> config, () -> false, logger);
        try (var retirement = retire(worker)) {
            worker.auctionPurchased("auction-expiry:42", buyer, fields).get(5, TimeUnit.SECONDS);
            worker.auctionPurchased("auction-expiry:42", buyer, fields).get(5, TimeUnit.SECONDS);
            worker.wake();
            await(() -> {
                try { return count(source, "SELECT COUNT(*) FROM ep_mail_job WHERE state=?", "DONE") == 1; }
                catch (SQLException failure) { return false; }
            });
            var inbox = repository.loadInbox(buyer);
            assertEquals(1, inbox.size());
            assertEquals("成交 #42", inbox.get(0).subject());
            assertEquals("实付 20", inbox.get(0).body());
            assertEquals(2.0, inbox.get(0).attachments().get(0).amount());
        } finally { mail.close(); }
    }

    @Test
    void missingPlanCanBeMatchedRenderedOrExcluded() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              no-plan:
                trigger: abyss-result
                when: { dungeon: dragon, plan: "" }
                actions:
                  - { type: mail, id: notice, title: Result, content: 'Plan [{plan}]' }
              with-plan:
                trigger: abyss-result
                when: { dungeon: dragon, plan: standard }
                actions:
                  - { type: mail, id: notice, title: Result, content: 'Selected {plan}' }
            """);
        var config = RuleConfiguration.parse(yaml);
        var result = result(UUID.randomUUID(), UUID.randomUUID());
        var jobs = new java.util.ArrayList<RuleJournal.MailJob>();
        for (var rule : config.abyss()) DurableRules.planResult(jobs, result, rule);
        assertEquals(1, jobs.size());
        assertEquals("Plan []", jobs.get(0).content());
    }

    @Test
    void sqliteTwoNodesAndLostMailAcknowledgement() throws Exception {
        exercise(false, "jdbc:sqlite:" + folder.resolve("shared.sqlite"));
    }

    @Test
    void mysqlProtocolTwoNodesAndLostMailAcknowledgement() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        String url = System.getenv("AXS_EVENTPACKET_MYSQL_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify(?:_[0-9]+)?"));
        exercise(true, url);
    }

    private void exercise(boolean mysql, String url) throws Exception {
        DataSource first = source(mysql, url);
        DataSource second = source(mysql, url);
        var firstJournal = new RuleJournal(first, mysql);
        var secondJournal = new RuleJournal(second, mysql);
        var mailFirst = repository(first, mysql);
        var mailSecond = repository(second, mysql);
        firstJournal.initialize();
        secondJournal.initialize();
        mailFirst.initialize();
        mailSecond.initialize();
        var config = configuration();
        var rule = config.abyss().get(0);
        UUID recipient = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        var result = result(recipient, left);
        var jobs = new java.util.ArrayList<RuleJournal.MailJob>();
        DurableRules.planResult(jobs, result, rule);
        assertEquals(List.of(recipient), jobs.stream().map(RuleJournal.MailJob::player).toList());
        String jobId = jobs.get(0).id();
        String owner = "test-" + UUID.randomUUID();
        firstJournal.startCursor(rule.id(), owner, 12, false);
        String retiredOwner = "retired-" + UUID.randomUUID();
        firstJournal.startCursor(rule.id(), retiredOwner, 99, false);
        firstJournal.startCursor("secondary", owner, 8, false);
        clearInvocations(second);
        var cursors = secondJournal.cursors(List.of(
            new RuleConfiguration.MailRule(rule.id(), owner, null, Map.of(), List.of()),
            new RuleConfiguration.MailRule("secondary", owner, null, Map.of(), List.of())));
        assertEquals(Map.of(new RuleJournal.CursorKey(rule.id(), owner), new RuleJournal.Cursor(12, 0),
            new RuleJournal.CursorKey("secondary", owner), new RuleJournal.Cursor(8, 0)), cursors);
        verify(second).getConnection();
        assertThrows(SQLException.class, () -> firstJournal.stagePage(rule.id(), owner, 12, 13,
            List.of(jobs.get(0), jobs.get(0))));
        assertEquals(12, secondJournal.cursor(rule.id(), owner).orElseThrow().sequence());
        assertEquals(0, count(first, "SELECT COUNT(*) FROM ep_mail_job WHERE job_id=?", jobId));

        var ready = new CountDownLatch(2);
        var begin = new CountDownLatch(1);
        var nodes = Executors.newFixedThreadPool(2);
        try {
            var a = nodes.submit(() -> stage(firstJournal, rule.id(), owner, jobs, ready, begin));
            var b = nodes.submit(() -> stage(secondJournal, rule.id(), owner, jobs, ready, begin));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            begin.countDown();
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        } finally {
            nodes.shutdownNow();
        }
        assertEquals(13, secondJournal.cursor(rule.id(), owner).orElseThrow().sequence());
        assertEquals(0L, secondJournal.nextDueDelayMillis().orElseThrow());

        var claim = firstJournal.claim(30_000);
        assertEquals(jobId, claim.job().id());
        assertNull(secondJournal.claim(30_000));
        assertTrue(secondJournal.nextDueDelayMillis().orElseThrow() > 0L);
        long mailId = mailFirst.insertDurableMail(jobId, message(claim.job())).id();
        assertTrue(mailId > 0);
        // Simulate node A dying after Mail commits but before EventPacket stores the acknowledgement.
        try (var connection = second.getConnection();
             var expire = connection.prepareStatement("UPDATE ep_mail_job SET due_at=0 WHERE job_id=?")) {
            expire.setString(1, jobId);
            expire.executeUpdate();
        }
        var replacement = secondJournal.claim(30_000);
        assertEquals(jobId, replacement.job().id());
        assertNotEquals(claim.token(), replacement.token());
        var repeated = mailSecond.insertDurableMail(jobId, message(replacement.job()));
        assertTrue(repeated.duplicate());
        assertEquals(mailId, repeated.id());
        assertFalse(firstJournal.complete(claim, mailId));
        assertTrue(secondJournal.complete(replacement, mailId));
        assertNull(firstJournal.claim(30_000));
        assertTrue(firstJournal.nextDueDelayMillis().isEmpty());
        assertEquals(1, count(first, "SELECT COUNT(*) FROM mail_entries WHERE owner_uuid=?", recipient.toString()));

        assertTrue(firstJournal.stageEvent("retry-event", List.of(
            new RuleJournal.MailJob("retry-job", recipient, "Retry", "Body", "", 0))));
        var retry = firstJournal.claim(30_000);
        assertNotNull(retry);
        assertTrue(firstJournal.retry(retry, 60_000L));
        assertTrue(secondJournal.nextDueDelayMillis().orElseThrow() > 30_000L);
        assertNull(secondJournal.claim(30_000));
    }

    @Test
    void liveWorkerReadsAbyssCursorAndDeliversThePersistedMailRequest() throws Exception {
        String url = "jdbc:sqlite:" + folder.resolve("worker.sqlite");
        var source = source(false, url);
        var journal = new RuleJournal(source, false);
        var repository = repository(source, false);
        repository.initialize();
        var config = configuration(true);
        var recipient = UUID.randomUUID();
        var result = result(recipient, UUID.randomUUID());
        var abyss = mock(AbyssRunResultAccess.class);
        when(abyss.usesLocalResultStorage()).thenReturn(true);
        when(abyss.latestCommittedSequence()).thenReturn(CompletableFuture.completedFuture(12L));
        when(abyss.committedResults(eq("abyss"), eq(12L), anyInt())).thenReturn(
            CompletableFuture.completedFuture(List.of(
                new AbyssRunResultAccess.Committed(13L, result(UUID.randomUUID(), UUID.randomUUID(), 3)),
                new AbyssRunResultAccess.Committed(14L, result))));
        when(abyss.committedResults(eq("abyss"), eq(14L), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(abyss.committedResults(eq("abyss-secondary"), eq(12L), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        var mail = mock(AxsMailService.class);
        var calls = new AtomicInteger();
        when(mail.sendDurable(any())).thenAnswer(invocation -> {
            var request = invocation.<xuanmo.arcartxsuite.api.mail.AxsMailRequest>getArgument(0);
            assertEquals(List.of(recipient), request.recipients());
            assertEquals(100.0, request.currencies().get(0).amount());
            assertEquals("dragon / hard", request.content());
            assertTrue(Thread.currentThread().getName().startsWith("AXS-EventPacket-SQL"));
            var inserted = repository.insertDurableMail(request.idempotencyKey(), message(new RuleJournal.MailJob(
                request.idempotencyKey(), recipient, request.title(), request.content(), "", 0)));
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(inserted.id(), 1));
        });
        var worker = new DurableRules(journal, () -> abyss, () -> mail, () -> config, () -> false, Logger.getAnonymousLogger());
        try (var retirement = retire(worker)) {
            worker.wake();
            await(() -> {
                try {
                    return count(source, "SELECT COUNT(*) FROM ep_mail_job WHERE state=?", "DONE") == 1;
                } catch (SQLException unavailable) {
                    return false;
                }
            });
        }
        assertEquals(1, calls.get());
        assertEquals(14, journal.cursor("clear", "abyss").orElseThrow().sequence());
        assertEquals(14, journal.cursor("other-dungeon", "abyss").orElseThrow().sequence());
        assertEquals(12, journal.cursor("interleaved", "abyss-secondary").orElseThrow().sequence());
        verify(abyss).committedResults(eq("abyss"), eq(12L), anyInt());
    }

    @Test
    void sharedAbyssOrchestrationRequiresCustomerAccessButQueuedMailContinues() throws Exception {
        assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        var source = source(false, "jdbc:sqlite:" + folder.resolve("tier.sqlite"));
        var journal = spy(new RuleJournal(source, false));
        doReturn(true).when(journal).mysql();
        journal.initialize();
        var player = UUID.randomUUID();
        assertTrue(journal.stageEvent("queued-before-cutover", List.of(
            new RuleJournal.MailJob("old-job", player, "旧奖励", "", "", 0.0))));

        var config = configuration();
        var abyss = mock(AbyssRunResultAccess.class);
        when(abyss.usesSharedHostStorage()).thenReturn(true);
        when(abyss.latestCommittedSequence()).thenReturn(CompletableFuture.completedFuture(12L));
        when(abyss.committedResults(eq("abyss"), eq(12L), anyInt())).thenReturn(
            CompletableFuture.completedFuture(List.of(new AbyssRunResultAccess.Committed(13L, result(player, UUID.randomUUID())))));
        when(abyss.committedResults(eq("abyss"), eq(13L), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        var mail = mock(AxsMailService.class);
        when(mail.usesSharedHostStorage()).thenReturn(true);
        when(mail.sendDurable(any())).thenReturn(CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(52L, 1)));
        var authorized = new java.util.concurrent.atomic.AtomicBoolean(false);
        var worker = new DurableRules(journal, () -> abyss, () -> mail, () -> config,
            authorized::get, Logger.getAnonymousLogger());
        try (var retirement = retire(worker)) {
            worker.wake();
            await(() -> {
                try { return count(source, "SELECT COUNT(*) FROM ep_mail_job WHERE state=?", "DONE") == 1; }
                catch (SQLException failure) { return false; }
            });
            verifyNoInteractions(abyss);
            assertTrue(journal.cursor("clear", "abyss").isEmpty());

            authorized.set(true);
            worker.wake();
            await(() -> {
                try { return count(source, "SELECT COUNT(*) FROM ep_mail_job WHERE state=?", "DONE") == 2; }
                catch (SQLException failure) { return false; }
            });
            assertEquals(13L, journal.cursor("clear", "abyss").orElseThrow().sequence());
        }
    }

    @Test
    void freeLocalJournalDoesNotReplayMySqlAbyssResultsAndStillDrainsStagedMail() throws Exception {
        assumeTrue(!BuildEdition.CROSS_SERVER_INCLUDED);
        var source = source(false, "jdbc:sqlite:" + folder.resolve("tier-local-journal.sqlite"));
        var journal = new RuleJournal(source, false);
        journal.initialize();
        var player = UUID.randomUUID();
        assertTrue(journal.stageEvent("queued-before-cutover", List.of(
            new RuleJournal.MailJob("old-job", player, "旧奖励", "", "", 0.0))));

        var config = configuration();
        var abyss = mock(AbyssRunResultAccess.class);
        var mail = mock(AxsMailService.class);
        when(mail.sendDurable(any())).thenReturn(CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(52L, 1)));
        var worker = new DurableRules(journal, () -> abyss, () -> mail, () -> config,
            () -> true, Logger.getAnonymousLogger());
        try (var retirement = retire(worker)) {
            worker.wake();
            await(() -> {
                try { return count(source, "SELECT COUNT(*) FROM ep_mail_job WHERE state=?", "DONE") == 1; }
                catch (SQLException failure) { return false; }
            });
            verify(abyss, never()).latestCommittedSequence();
            verify(abyss, never()).committedResults(anyString(), anyLong(), anyInt());
            assertTrue(journal.cursor("clear", "abyss").isEmpty());
        }
    }

    private static AutoCloseable retire(DurableRules worker) {
        return () -> {
            worker.close();
            var field = DurableRules.class.getDeclaredField("worker");
            field.setAccessible(true);
            var executor = (java.util.concurrent.ExecutorService) field.get(worker);
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Durable worker did not retire");
        };
    }

    private static void await(BooleanSupplier complete) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!complete.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(complete.getAsBoolean());
    }

    private static boolean stage(RuleJournal journal, String rule, String owner, List<RuleJournal.MailJob> jobs,
                                 CountDownLatch ready, CountDownLatch begin) throws Exception {
        ready.countDown();
        if (!begin.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
        return journal.stagePage(rule, owner, 12, 13, jobs);
    }

    private static DataSource source(boolean mysql, String url) throws SQLException {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(invocation -> {
            Connection connection = mysql ? DriverManager.getConnection(url, "root", "") : DriverManager.getConnection(url);
            if (!mysql) {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA busy_timeout=5000");
                }
            }
            return connection;
        });
        return source;
    }

    private JdbcMailRepository repository(DataSource source, boolean mysql) {
        var descriptor = mock(StorageDescriptor.class);
        when(descriptor.isMysql()).thenReturn(mysql);
        return new JdbcMailRepository(source, descriptor, folder.toFile(), Logger.getAnonymousLogger());
    }

    private static long count(DataSource source, String sql, String parameter) throws SQLException {
        try (var connection = source.getConnection(); var query = connection.prepareStatement(sql)) {
            query.setString(1, parameter);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private static MailMessage message(RuleJournal.MailJob job) {
        Instant now = Instant.now();
        return new MailMessage(0L, job.player(), null, "EventPacket", MailSourceType.SYSTEM, "", "", job.title(),
            job.content(), MailStatus.UNREAD, List.of(), List.of(), List.of(), now, null, now, null, null);
    }

    private static AbyssRunResultAccess.Result result(UUID recipient, UUID left) {
        return result(recipient, left, 4_102_444_800_000L);
    }

    private static AbyssRunResultAccess.Result result(UUID recipient, UUID left, long endedAt) {
        UUID run = UUID.randomUUID();
        return new AbyssRunResultAccess.Result(run, run, UUID.randomUUID(), "abyss", "dragon", null, "hard", "1",
            recipient, 1, 2, endedAt, AbyssRunResultAccess.Outcome.COMPLETED, "", List.of(
                new AbyssRunResultAccess.Member(recipient, AbyssRunResultAccess.MemberState.PRESENT, 0, "", 3),
                new AbyssRunResultAccess.Member(left, AbyssRunResultAccess.MemberState.LEFT, 0, "", 2)));
    }

    private static RuleConfiguration configuration() throws Exception {
        return configuration(false);
    }

    private static RuleConfiguration configuration(boolean sharedCursor) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              clear:
                trigger: abyss-result
                when: { dungeon: dragon, difficulty: hard }
                actions:
                  - id: mail
                    type: mail
                    title: 通关
                    content: '{dungeon} / {difficulty}'
                    currency: money
                    amount: 100
            """);
        if (sharedCursor) {
            yaml.loadFromString(yaml.saveToString() + """
                  interleaved:
                    trigger: abyss-result
                    owner: abyss-secondary
                    when: { dungeon: separate }
                    actions:
                      - { type: mail, id: bonus, title: Separate, content: Separate }
                  other-dungeon:
                    trigger: abyss-result
                    when: { dungeon: other }
                    actions:
                      - { type: mail, id: bonus, title: Other, content: Other }
                """);
        }
        return RuleConfiguration.parse(yaml);
    }
}
