package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.mail.*;

class DurableShutdownTest {
    @TempDir Path directory;

    @Test void acceptedFactsDrainThroughFullBoundedQueueAndCloseRejectsLateAdmissions() throws Exception {
        String url="jdbc:sqlite:"+directory.resolve("drain.sqlite");
        var source=source(url);var gate=mock(DataSource.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
        var primary=Thread.currentThread();var mainSql=new AtomicBoolean();
        when(gate.getConnection()).thenAnswer(call->{
            if(Thread.currentThread()==primary)mainSql.set(true);
            if(once.compareAndSet(true,false)){entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}
            return source.getConnection();
        });
        var active=new AtomicReference<>(configuration());
        var worker=new DurableRules(new RuleJournal(gate,false),()->null,()->null,active::get,java.util.logging.Logger.getAnonymousLogger());
        var admitted=new ArrayList<CompletableFuture<Void>>();
        try {
            admitted.add(worker.auctionPurchased("one",UUID.randomUUID(),Map.of()));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<128;i++)admitted.add(worker.auctionPurchased("queued-"+i,UUID.randomUUID(),Map.of()));
            assertThrows(ExecutionException.class,()->worker.auctionPurchased("overflow",UUID.randomUUID(),Map.of()).get(1,TimeUnit.SECONDS));
            worker.close();active.set(null);
            var canceledView=worker.stopped().toCompletableFuture();canceledView.cancel(false);
            assertFalse(worker.stopped().toCompletableFuture().isDone());assertFalse(mainSql.get());
            assertThrows(ExecutionException.class,()->worker.admit("late",UUID.randomUUID(),Map.of()).get());
            assertThrows(ExecutionException.class,()->worker.auctionPurchased("late",UUID.randomUUID(),Map.of()).get());
            release.countDown();
            for(var receipt:admitted)receipt.get(10,TimeUnit.SECONDS);
            worker.stopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
            assertEquals(129,count(source,"ep_admission_receipt"));assertEquals(129,count(source,"ep_mail_job"));assertFalse(mainSql.get());
        }finally{release.countDown();worker.close();worker.stopped().toCompletableFuture().get(10,TimeUnit.SECONDS);}
    }

    @Test void acceptedMailCompletionStillWritesItsReceiptAfterClose() throws Exception {
        var source=source("jdbc:sqlite:"+directory.resolve("ack.sqlite"));
        var mail=mock(AxsMailService.class);var sent=new CountDownLatch(1);var result=new CompletableFuture<AxsDeliveryReceipt>();
        when(mail.sendDurable(any())).thenAnswer(call->{sent.countDown();return result;});
        var config=configuration();
        var worker=new DurableRules(new RuleJournal(source,false),()->null,()->mail,()->config,java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.auctionPurchased("claim",UUID.randomUUID(),Map.of()).get(5,TimeUnit.SECONDS);
            assertTrue(sent.await(5,TimeUnit.SECONDS));worker.close();
            assertFalse(worker.stopped().toCompletableFuture().isDone());
            result.complete(AxsDeliveryReceipt.ok(42,1));worker.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
            try(var conn=source.getConnection();var query=conn.createStatement();var rows=query.executeQuery("SELECT state,mail_id FROM ep_mail_job")) {
                assertTrue(rows.next());assertEquals("DONE",rows.getString(1));assertEquals(42,rows.getLong(2));
            }
        }finally{result.complete(AxsDeliveryReceipt.ok(42,1));worker.close();worker.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);}
    }

    @Test void disabledRulesProbeOnceThenIdleWakeDoesNotBorrowConnections() throws Exception {
        var real=source("jdbc:sqlite:"+directory.resolve("idle.sqlite"));var source=mock(DataSource.class);var reads=new AtomicInteger();
        when(source.getConnection()).thenAnswer(call->{reads.incrementAndGet();return real.getConnection();});
        var empty=RuleConfiguration.parse(new YamlConfiguration());
        var worker=new DurableRules(new RuleJournal(source,false),()->null,()->null,()->empty,java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.pollIfDue();barrier(worker);int initial=reads.get();assertEquals(2,initial);
            for(int i=0;i<1000;i++)worker.pollIfDue();barrier(worker);
            assertEquals(initial,reads.get());
        }finally{worker.close();worker.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);}
    }

    @Test void emptyCacheExpiresSoWorkCommittedByAnotherNodeCanBeRecovered() throws Exception {
        var source=source("jdbc:sqlite:"+directory.resolve("remote.sqlite"));
        var mail=mock(AxsMailService.class);when(mail.sendDurable(any())).thenReturn(CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(77,1)));
        var empty=RuleConfiguration.parse(new YamlConfiguration());
        var worker=new DurableRules(new RuleJournal(source,false),()->null,()->mail,()->empty,java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.pollIfDue();barrier(worker);
            new RuleJournal(source,false).stageEvent("remote",List.of(new RuleJournal.MailJob("remote-job",UUID.randomUUID(),"Title","Body","",0)));
            expireProbe(worker);
            worker.pollIfDue();barrier(worker);
            try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery("SELECT state,mail_id FROM ep_mail_job")) {
                assertTrue(rows.next());assertEquals("DONE",rows.getString(1));assertEquals(77,rows.getLong(2));
            }
        }finally{worker.close();worker.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);}
    }

    @Test void anAbyssCommitDuringAnActiveProbeSchedulesAnotherPass() throws Exception {
        var source = source("jdbc:sqlite:" + directory.resolve("abyss-wake.sqlite"));
        var abyss = mock(AbyssRunResultAccess.class);
        var first = new CompletableFuture<List<AbyssRunResultAccess.Committed>>();
        var entered = new CountDownLatch(1);
        var repeated = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(abyss.committedResults("abyss", 0, 16)).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                return first;
            }
            repeated.countDown();
            return CompletableFuture.completedFuture(List.of());
        });
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              clear:
                trigger: abyss-result
                start-sequence: 0
                when: { dungeon: dragon }
                actions:
                  - { type: mail, id: bonus, title: Test, content: Test }
            """);
        var config = RuleConfiguration.parse(yaml);
        var worker = new DurableRules(new RuleJournal(source, false), () -> abyss, () -> null, () -> config,
            java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.pollIfDue();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            worker.abyssCommitted();
            worker.abyssCommitted();
            first.complete(List.of());
            assertTrue(repeated.await(5, TimeUnit.SECONDS));
            barrier(worker);
            for (int i = 0; i < 1000; i++) worker.pollIfDue();
            barrier(worker);
            assertEquals(2, calls.get());

            expireProbe(worker);
            worker.pollIfDue();
            barrier(worker);
            assertEquals(3, calls.get());
        } finally {
            first.complete(List.of());
            worker.close();
            worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void failingAbyssQueryDoesNotBlockAnAlreadyStagedMail() throws Exception {
        var source = source("jdbc:sqlite:" + directory.resolve("isolated-mail.sqlite"));
        var journal = new RuleJournal(source, false);
        journal.initialize();
        journal.stageEvent("auction-event", List.of(
            new RuleJournal.MailJob("auction-job", UUID.randomUUID(), "Title", "Body", "", 0)));

        var abyss = mock(AbyssRunResultAccess.class);
        when(abyss.committedResults("abyss", 0, 16)).thenReturn(
            CompletableFuture.failedFuture(new SQLException("Abyss unavailable")));
        var mail = mock(AxsMailService.class);
        when(mail.sendDurable(any())).thenReturn(CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(77, 1)));
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              clear:
                trigger: abyss-result
                start-sequence: 0
                when: { dungeon: dragon }
                actions:
                  - { type: mail, id: bonus, title: Test, content: Test }
            """);
        var config = RuleConfiguration.parse(yaml);
        var worker = new DurableRules(journal, () -> abyss, () -> mail, () -> config,
            java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.pollIfDue();
            barrier(worker);
            verify(abyss).committedResults("abyss", 0, 16);
            try (var conn = source.getConnection(); var statement = conn.createStatement();
                 var rows = statement.executeQuery("SELECT state,mail_id FROM ep_mail_job")) {
                assertTrue(rows.next());
                assertEquals("DONE", rows.getString(1));
                assertEquals(77, rows.getLong(2));
            }
        } finally {
            worker.close();
            worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void aJobWithAFutureRetryDoesNotTriggerRepeatedClaims() throws Exception {
        var database = source("jdbc:sqlite:" + directory.resolve("scheduled-retry.sqlite"));
        var journal = new RuleJournal(database, false);
        journal.initialize();
        assertTrue(journal.stageEvent("retry-event", List.of(
            new RuleJournal.MailJob("retry-job", UUID.randomUUID(), "Title", "Body", "", 0))));
        assertFalse(journal.stageEvent("retry-event", List.of()));
        var claim = journal.claim(30_000L);
        assertNotNull(claim);
        assertTrue(journal.retry(claim, 60_000L));
        assertNull(journal.claim(30_000L));
        assertTrue(journal.nextDueDelayMillis().orElseThrow() > 30_000L);

        var source = mock(DataSource.class);
        var borrowed = new AtomicInteger();
        when(source.getConnection()).thenAnswer(call -> {
            borrowed.incrementAndGet();
            return database.getConnection();
        });
        var empty = RuleConfiguration.parse(new YamlConfiguration());
        var mail = mock(AxsMailService.class);
        var worker = new DurableRules(new RuleJournal(source, false), () -> null, () -> mail, () -> empty,
            java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.pollIfDue();
            barrier(worker);
            assertEquals(2, borrowed.get());
            for (int i = 0; i < 1000; i++) worker.pollIfDue();
            barrier(worker);
            verifyNoInteractions(mail);
            assertEquals(2, borrowed.get());
        } finally {
            worker.close();
            worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void repeatedWakeupsCannotBypassAnSqlFailureAndAreRetriedAtTheDeadline() throws Exception {
        var database = source("jdbc:sqlite:" + directory.resolve("wake-backoff.sqlite"));
        var unavailable = new AtomicBoolean(true);
        var borrowed = new AtomicInteger();
        var failing = mock(DataSource.class);
        when(failing.getConnection()).thenAnswer(call -> {
            borrowed.incrementAndGet();
            if (unavailable.get()) throw new SQLException("test database unavailable");
            return database.getConnection();
        });
        var empty = RuleConfiguration.parse(new YamlConfiguration());
        var worker = new DurableRules(new RuleJournal(failing, false), () -> null, () -> null, () -> empty,
            java.util.logging.Logger.getAnonymousLogger());
        try {
            worker.wake();
            barrier(worker);
            assertTrue(worker.status().startsWith("retrying"));
            var backoff = DurableRules.class.getDeclaredField("retryNotBefore");
            backoff.setAccessible(true);
            assertNotEquals(0L, backoff.getLong(worker));
            backoff.setLong(worker, System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
            for (int index = 0; index < 1000; index++) {
                worker.wake();
                worker.pollIfDue();
            }
            barrier(worker);
            assertEquals(1, borrowed.get());

            unavailable.set(false);
            backoff.setLong(worker, System.nanoTime() - 1L);
            worker.pollIfDue();
            barrier(worker);
            assertEquals("ready", worker.status());
            assertEquals(3, borrowed.get());
            assertEquals(0L, backoff.getLong(worker));
        } finally {
            worker.close();
            worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void expireProbe(DurableRules worker) throws Exception {
        var deadline = DurableRules.class.getDeclaredField("nextPollAt");
        deadline.setAccessible(true);
        deadline.setLong(worker, System.nanoTime() - 1);
    }

    private static void barrier(DurableRules worker) throws Exception {
        var field=DurableRules.class.getDeclaredField("worker");field.setAccessible(true);
        ((ExecutorService)field.get(worker)).submit(()->{}).get(5,TimeUnit.SECONDS);
    }
    private static RuleConfiguration configuration() throws Exception {
        var yaml=new YamlConfiguration();yaml.loadFromString("""
            rules:
              reward:
                trigger: auction-purchased
                actions:
                  - {type: mail, id: bonus, title: Test, content: Test}
            """);return RuleConfiguration.parse(yaml);
    }
    private static DataSource source(String url)throws Exception {
        var source=mock(DataSource.class);when(source.getConnection()).thenAnswer(call->DriverManager.getConnection(url));return source;
    }
    private static int count(DataSource source,String table)throws Exception {
        try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rows.next());return rows.getInt(1);}
    }
}
