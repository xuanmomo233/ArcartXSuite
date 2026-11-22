package xuanmo.arcartxsuite.mail.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;

class MailInboxQueriesTest {
    @TempDir Path directory;

    @Test
    void aViewReadsOnePageAndOneAttachmentBatchAndKeepsOnlyTheSelectedPayload() throws Exception {
        var statements = new ArrayList<String>();
        var source = dataSource(statements);
        var repo = repository(source);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        try {
            long oldest = repo.insertMail(mail(owner, MailSourceType.PLAYER, MailStatus.READ, now, "old payload")).id();
            long newest = repo.insertMail(mail(owner, MailSourceType.SYSTEM, MailStatus.UNREAD, now.plusMillis(1), "new payload")).id();
            long foreign = repo.insertMail(mail(other, MailSourceType.SYSTEM, MailStatus.UNREAD, now, "private payload")).id();
            statements.clear();
            clearInvocations(source);

            var query = new MailInboxQuery(MailInboxFilter.ALL, 1, 40);
            var inbox = repo.loadInboxView(owner, query, oldest);
            verify(source).getConnection();
            assertEquals(3, statements.size());
            assertEquals(1, statements.stream().filter(sql -> sql.contains("FROM mail_attachments WHERE mail_id IN")).count());
            assertEquals(new MailMailboxStats(2, 1, 2), inbox.stats());
            assertEquals(oldest, inbox.selectedMailId());
            assertEquals(List.of(newest, oldest), inbox.page().entries().stream().map(MailMessage::id).toList());
            assertEquals("", inbox.page().entries().get(0).attachments().get(0).itemData());
            assertEquals("材料", inbox.page().entries().get(0).attachments().get(0).description());
            assertTrue(inbox.page().entries().get(0).claimCommands().isEmpty());
            assertEquals("old payload", inbox.page().entries().get(1).attachments().get(0).itemData());

            var filtered = repo.loadInboxView(owner, new MailInboxQuery(MailInboxFilter.SYSTEM, 8, 1), foreign);
            assertEquals(1, filtered.page().page());
            assertEquals(1, filtered.page().totalItems());
            assertEquals(newest, filtered.selectedMailId());
            assertEquals("new payload", filtered.page().entries().get(0).attachments().get(0).itemData());
            assertEquals(inbox.stats(), filtered.stats());

            var full = repo.loadInboxPage(owner, query);
            assertEquals("new payload", full.entries().get(0).attachments().get(0).itemData());
            assertEquals(List.of("say test"), full.entries().get(0).claimCommands());
            var claim = repo.loadClaimablePage(owner, Long.MAX_VALUE, 10, now);
            assertEquals("new payload", claim.get(0).mail().attachments().get(0).itemData());
            assertTrue(repo.loadMail(owner, foreign).isEmpty());
            assertFalse(repo.markUnreadRead(other, newest, now));
            assertTrue(repo.markUnreadRead(owner, newest, now));
            assertFalse(repo.markUnreadRead(owner, newest, now));
            assertEquals(0, repo.loadInboxView(owner, new MailInboxQuery(MailInboxFilter.UNREAD, 1, 10), newest).stats().unreadCount());
        } finally {
            repo.close();
        }
    }

    @Test
    void bulkDeletionIsOwnerScopedAndPreservesPendingAndProtectedMail() throws Exception {
        var source = dataSource(java.util.Collections.synchronizedList(new ArrayList<>()));
        var first = repository(source);
        var second = repository(source);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        try {
            for (int i = 0; i < 130; i++) {
                first.insertMail(bulkMail(owner, MailStatus.UNREAD, "readable " + i, now, List.of(), List.of()));
            }
            var item = new MailAttachment(0, 0, MailAttachmentType.ITEM, "encoded", "", 0, "materials", null);
            long protectedItem = first.insertMail(bulkMail(owner, MailStatus.READ, "protected item", now, List.of(item), List.of())).id();
            long protectedCommand = first.insertMail(bulkMail(owner, MailStatus.UNREAD, "protected command", now, List.of(), List.of("say test"))).id();
            long claimed = first.insertMail(bulkMail(owner, MailStatus.CLAIMED, "claimed", now, List.of(item), List.of())).id();
            long pending = first.insertMail(bulkMail(owner, MailStatus.CLAIM_PENDING, "pending", now, List.of(item), List.of())).id();
            long executing = first.insertMail(bulkMail(owner, MailStatus.UNREAD, "executing", now, List.of(item), List.of())).id();
            assertTrue(first.reserveClaimStep(owner, executing, 0, UUID.randomUUID(), now));
            long expired = first.insertMail(bulkMail(owner, MailStatus.EXPIRED, "expired", now, List.of(), List.of())).id();
            long later = first.insertMail(bulkMail(owner, MailStatus.UNREAD, "later", now.plusMillis(1), List.of(), List.of())).id();
            long foreign = first.insertMail(bulkMail(other, MailStatus.UNREAD, "foreign", now, List.of(), List.of())).id();

            var start = new CountDownLatch(1);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var a = workers.submit(() -> { start.await(); return first.deleteInbox(owner, false, now); });
                var b = workers.submit(() -> { start.await(); return second.deleteInbox(owner, false, now); });
                start.countDown();
                int deletedA = a.get(7, TimeUnit.SECONDS);
                int deletedB = b.get(7, TimeUnit.SECONDS);
                assertEquals(131, deletedA + deletedB);
                assertTrue(deletedA == 0 || deletedB == 0);
            } finally {
                workers.shutdownNow();
            }
            assertEquals(MailStatus.DELETED, first.loadMail(owner, claimed).orElseThrow().status());
            assertEquals(MailStatus.READ, first.loadMail(owner, protectedItem).orElseThrow().status());
            assertEquals(MailStatus.UNREAD, first.loadMail(owner, protectedCommand).orElseThrow().status());
            var logs = first.loadLogPage(owner, 1, 100);
            assertEquals(100, logs.totalItems());
            assertEquals(100, logs.entries().stream().map(entry -> entry.content()).distinct().count());
            assertEquals(2, first.deleteInbox(owner, true, now));
            assertEquals(MailStatus.CLAIM_PENDING, first.loadMail(owner, pending).orElseThrow().status());
            assertEquals(MailStatus.CLAIM_REVIEW, first.loadMail(owner, executing).orElseThrow().status());
            assertEquals(MailStatus.EXPIRED, first.loadMail(owner, expired).orElseThrow().status());
            assertEquals(MailStatus.UNREAD, first.loadMail(owner, later).orElseThrow().status());
            assertEquals(MailStatus.UNREAD, first.loadMail(other, foreign).orElseThrow().status());
            assertEquals(0, first.deleteInbox(owner, true, now));
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void aLateAuditFailureRollsBackEveryDeletionBatch() throws Exception {
        var source = dataSource(new ArrayList<>());
        var repo = repository(source);
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        try {
            for (int i = 0; i < 129; i++) {
                repo.insertMail(bulkMail(owner, MailStatus.READ, "batch " + i, now, List.of(), List.of()));
            }
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.execute("""
                    CREATE TRIGGER reject_oldest_audit BEFORE INSERT ON mail_logs
                    WHEN NEW.content = '删除邮件: batch 0'
                    BEGIN SELECT RAISE(ABORT, 'isolated audit failure'); END
                    """);
            }
            assertThrows(SQLException.class, () -> repo.deleteInbox(owner, false, now));
            assertEquals(129, repo.loadStats(owner).totalCount());
            assertEquals(0, repo.loadLogPage(owner, 1, 100).totalItems());
        } finally {
            repo.close();
        }
    }

    private DataSource dataSource(List<String> statements) throws SQLException {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> {
            Connection delegate = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("mail.sqlite"));
            try (var options = delegate.createStatement()) { options.execute("PRAGMA busy_timeout=5000"); }
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && args[0] instanceof String sql && sql.startsWith("SELECT ")) {
                        statements.add(sql);
                    }
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        });
        return source;
    }

    private JdbcMailRepository repository(DataSource source) throws SQLException {
        var repo = new JdbcMailRepository(source, StorageDescriptor.sqlite("mail.sqlite"), directory.toFile(), Logger.getAnonymousLogger());
        repo.initialize();
        return repo;
    }

    private static MailMessage mail(UUID owner, MailSourceType source, MailStatus status, Instant now, String item) {
        var attachment = new MailAttachment(0, 0, MailAttachmentType.ITEM, item, "", 0, "材料", null);
        return new MailMessage(0, owner, null, "Tester", source, "", "", "测试邮件", "正文", status,
            List.of(attachment), List.of("say test"), List.of(), now, null, now, null, null);
    }

    private static MailMessage bulkMail(UUID owner, MailStatus status, String subject, Instant time,
                                       List<MailAttachment> attachments, List<String> commands) {
        return new MailMessage(0, owner, null, "Tester", MailSourceType.SYSTEM, "", "", subject, "", status,
            attachments, commands, List.of(), time, null, time, null, null);
    }
}
