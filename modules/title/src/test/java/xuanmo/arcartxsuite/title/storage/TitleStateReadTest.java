package xuanmo.arcartxsuite.title.storage;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;

class TitleStateReadTest {
    @TempDir Path folder;

    @Test
    void completeStateUsesOneConnectionAndPreservesIndexedQueries() throws Exception {
        var fixture = new Fixture();
        var id = UUID.randomUUID();
        var now = Instant.now().minusSeconds(100);
        fixture.repository.grantOwnedTitle(id, owned("later", false, now.plusSeconds(10)), false);
        fixture.repository.grantOwnedTitle(id, owned("勇者", true, now), false);
        fixture.repository.setEquippedTitle(id, "z", "later", now.plusSeconds(20));
        fixture.repository.setEquippedTitle(id, "a", "勇者", now.plusSeconds(21));
        fixture.repository.setDisplayTitle(id, "later", now.plusSeconds(30));
        fixture.resetCounts();
        var state = fixture.repository.loadState(id);
        assertEquals(1, fixture.connections.get());
        assertEquals(3, fixture.queries.get());
        assertEquals(List.of("勇者", "later"), new ArrayList<>(state.ownedTitles().keySet()));
        assertTrue(state.ownedTitles().get("勇者").hidden());
        assertEquals(List.of("a", "z"), new ArrayList<>(state.equippedTitleIdsByGroup().keySet()));
        assertEquals("later", state.displayTitleId());
        assertEquals(now.plusSeconds(30).toEpochMilli(), state.updatedAt().toEpochMilli());
    }

    @Test
    void emptyAndOtherPlayerStatesStayIsolated() throws Exception {
        var fixture = new Fixture();
        var id = UUID.randomUUID();
        fixture.repository.grantOwnedTitle(id, owned("other", false, Instant.now()), false);
        fixture.resetCounts();
        var empty = fixture.repository.loadState(UUID.randomUUID());
        assertEquals(Map.of(), empty.ownedTitles());
        assertEquals(Map.of(), empty.equippedTitleIdsByGroup());
        assertEquals("", empty.displayTitleId());
        assertEquals(Instant.EPOCH, empty.updatedAt());
        assertEquals(1, fixture.connections.get());
        assertEquals(3, fixture.queries.get());
    }

    @Test
    void missingStateTablePropagatesTheReadFailure() throws Exception {
        var fixture = new Fixture();
        try (var connection = fixture.source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("DROP TABLE title_player_display_title");
        }
        assertThrows(SQLException.class, () -> fixture.repository.loadState(UUID.randomUUID()));
    }

    @Test
    void successfulAndFailedReadTransactionsLeaveThePoolReusable() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve("pooled.db"));
        config.setMaximumPoolSize(1);
        try (HikariDataSource source = new HikariDataSource(config)) {
            var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("pooled.db"),
                folder.toFile(), Logger.getAnonymousLogger());
            repository.initialize();
            UUID id = UUID.randomUUID();
            repository.grantOwnedTitle(id, owned("first", false, Instant.now()), false);
            assertEquals(1, repository.loadState(id).ownedTitles().size());
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                assertTrue(connection.getAutoCommit());
                sql.execute("DROP TABLE title_player_display_title");
            }
            assertThrows(SQLException.class, () -> repository.loadState(id));
            try (var connection = source.getConnection()) {
                assertTrue(connection.getAutoCommit());
            }
            repository.initialize();
            repository.grantOwnedTitle(id, owned("second", false, Instant.now()), false);
            assertEquals(2, repository.loadState(id).ownedTitles().size());
        }
    }

    private PlayerOwnedTitle owned(String id, boolean hidden, Instant now) {
        return new PlayerOwnedTitle(id, hidden, now, null, null, now, "test");
    }

    private final class Fixture {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger queries = new AtomicInteger();
        final DataSource source = mock(DataSource.class);
        final JdbcTitleRepository repository;

        Fixture() throws Exception {
            when(source.getConnection()).thenAnswer(call -> {
                connections.incrementAndGet();
                Connection actual = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("title.db"));
                return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && args[0].toString().stripLeading().startsWith("SELECT")) queries.incrementAndGet();
                    try {
                        return method.invoke(actual, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
            });
            repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("title.db"),
                folder.toFile(), Logger.getAnonymousLogger());
            repository.initialize();
        }

        void resetCounts() {
            connections.set(0);
            queries.set(0);
        }
    }
}
