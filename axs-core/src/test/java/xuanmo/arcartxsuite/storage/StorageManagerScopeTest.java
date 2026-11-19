package xuanmo.arcartxsuite.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class StorageManagerScopeTest {
    @TempDir Path folder;

    @Test
    void configuringSharedMysqlDoesNotWaitForAHandshake() throws Exception {
        try (var noHandshake = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            var config = new YamlConfiguration();
            config.set("storage.mode", "mysql");
            config.set("storage.mysql.port", noHandshake.getLocalPort());
            config.set("storage.mysql.jdbc-params", "connectTimeout=1000&socketTimeout=4000");
            var manager = assertTimeout(Duration.ofSeconds(2), () -> new StorageManagerImpl(config, folder.toFile()));
            try {
                assertEquals(-1L, assertInstanceOf(HikariDataSource.class, manager.getDataSource()).getInitializationFailTimeout());
            } finally {
                noHandshake.close();
                manager.shutdown().toCompletableFuture().get(8, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void hostPreparationRunsOffThreadAndKeepsPoolsOpenUntilItExits() throws Exception {
        var manager = new StorageManagerImpl(new YamlConfiguration(), folder.toFile());
        var shared = mock(HikariDataSource.class);
        setField(manager, "globalDataSource", shared);
        var caller = Thread.currentThread();
        var preparationStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var prepared = manager.prepareHost(() -> {
                assertNotSame(caller, Thread.currentThread());
                preparationStarted.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return "prepared";
            });
            assertTrue(preparationStarted.await(2, TimeUnit.SECONDS));
            var stopped = manager.shutdown().toCompletableFuture();
            assertFalse(stopped.isDone());
            verify(shared, never()).close();
            release.countDown();
            assertEquals("prepared", prepared.get(2, TimeUnit.SECONDS));
            stopped.get(2, TimeUnit.SECONDS);
            verify(shared).close();
        } finally {
            release.countDown();
            manager.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void queuedWritesDrainBeforeTheSuccessorCanBorrowThePool() throws Exception {
        var manager = new StorageManagerImpl(new YamlConfiguration(), folder.toFile());
        var worker = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(8), task -> new Thread(task, "scope-drain-test"));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = manager.openModuleStorage("chat", "chat.db", folder.toFile(), null);
            first.ready().toCompletableFuture().get(5, TimeUnit.SECONDS);
            try (var c = first.dataSource().getConnection(); var s = c.createStatement()) {
                s.execute("CREATE TABLE receipts (id INTEGER PRIMARY KEY, amount INTEGER)");
            }
            worker.execute(() -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            });
            var committed = worker.submit(() -> {
                try (var c = first.dataSource().getConnection(); var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO receipts VALUES (1, 7)");
                }
                return null;
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            worker.shutdown();
            first.retireAfter(worker.stopped());
            var next = manager.openModuleStorage("chat", "chat.db", folder.toFile(), null);
            assertFalse(next.ready().toCompletableFuture().isDone());
            assertThrows(java.sql.SQLException.class, () -> manager.resolveModuleDataSource("chat", "chat.db", folder.toFile(), null));
            assertThrows(java.sql.SQLException.class, () -> manager.getModuleDataSource("chat", "chat.db"));
            assertThrows(IllegalStateException.class, () -> manager.closeModuleDataSource("chat"));
            release.countDown();
            committed.get(5, TimeUnit.SECONDS);
            next.ready().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(java.sql.SQLException.class, () -> first.dataSource().getConnection());
            // 旧调用者只持有模块 ID，也不能关闭新实例的池。
            assertThrows(IllegalStateException.class, () -> manager.closeModuleDataSource("chat"));
            try (var c = next.dataSource().getConnection(); var s = c.createStatement();
                 var row = s.executeQuery("SELECT amount FROM receipts WHERE id=1")) {
                assertTrue(row.next());
                assertEquals(7, row.getInt(1));
            }
            next.retireAfter(CompletableFuture.completedFuture(null));
            next.retired().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            worker.shutdownNow();
            worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            manager.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void sqliteReopenSeesTheOldCommittedTransaction() throws Exception {
        var manager = new StorageManagerImpl(new YamlConfiguration(), folder.toFile());
        var worker = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var stopped = new CompletableFuture<Void>();
        try {
            var first = manager.openModuleStorage("title", "title.db", folder.toFile(), null);
            first.ready().toCompletableFuture().get(5, TimeUnit.SECONDS);
            try (var connection = first.dataSource().getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE sample (amount INTEGER)");
                sql.execute("INSERT INTO sample VALUES (0)");
            }
            var transaction = worker.submit(() -> {
                try (var connection = first.dataSource().getConnection(); var sql = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    sql.executeUpdate("UPDATE sample SET amount = 7");
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    connection.commit();
                } finally {
                    stopped.complete(null);
                }
                return null;
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            first.retireAfter(stopped);
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            var next = manager.openModuleStorage("title", "title.db", folder.toFile(), null);
            assertFalse(next.ready().toCompletableFuture().isDone());
            release.countDown();
            transaction.get(5, TimeUnit.SECONDS);
            next.ready().toCompletableFuture().get(5, TimeUnit.SECONDS);
            try (var connection = next.dataSource().getConnection(); var sql = connection.createStatement();
                 var values = sql.executeQuery("SELECT amount FROM sample")) {
                assertTrue(values.next());
                assertEquals(7, values.getInt(1));
            }
            next.retireAfter(CompletableFuture.completedFuture(null));
            next.retired().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            stopped.complete(null);
            worker.shutdownNow();
            manager.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void retiringAModuleDoesNotCloseTheSharedMysqlPool() throws Exception {
        var manager = new StorageManagerImpl(new YamlConfiguration(), folder.toFile());
        var shared = mock(HikariDataSource.class);
        setField(manager, "globalDataSource", shared);
        setField(manager, "descriptor", StorageDescriptor.mysql("127.0.0.1", 3306, "test", "test", "", 2, ""));
        try {
            var scope = manager.openModuleStorage("title", null, folder.toFile(), null);
            scope.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            scope.retireAfter(CompletableFuture.completedFuture(null));
            scope.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(shared, never()).close();
        } finally {
            manager.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        verify(shared).close();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
