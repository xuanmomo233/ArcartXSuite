package xuanmo.arcartxsuite.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.module.ModuleRegistry;

class PendingRewardPagingTest {
    @TempDir Path folder;

    @Test void sqliteCoalescesRequestsAndScansAStablePageAtATime() throws Exception {
        exercise("jdbc:sqlite:" + folder.resolve("pages.db"), false);
    }

    @Test void mysqlProtocolUsesTheSameBoundAndOrder() throws Exception {
        String url = System.getenv("AXS_PENDING_PAGING_MYSQL_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify"));
        exercise(url, true);
    }

    private void exercise(String url, boolean mysql) throws Exception {
        var storage = mock(StorageManager.class);
        var descriptor = mock(StorageDescriptor.class);
        when(descriptor.isMysql()).thenReturn(mysql);
        when(storage.getDescriptor()).thenReturn(descriptor);
        when(storage.getHostConnection()).thenAnswer(call -> mysql
            ? DriverManager.getConnection(url, "root", "") : DriverManager.getConnection(url));
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        var service = new DefaultPendingRewardService(plugin, Logger.getAnonymousLogger(), storage, mock(ModuleRegistry.class));
        service.initialize();
        UUID id = UUID.randomUUID();
        for (int i = 65; i >= 1; i--) {
            service.savePending(new PendingReward(0L, id, "battlepass", PendingReward.Type.TITLE,
                "{\"targetId\":\"hero\"}", Integer.toString(i), i, 0));
        }

        var delays = new ArrayDeque<Runnable>();
        var async = new ArrayDeque<Runnable>();
        var deliveries = new ArrayDeque<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            delays.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            async.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            deliveries.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var admission = new AuthSnapshot(id, UUID.randomUUID(), 1L, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(admission);
            for (int i = 0; i < 50; i++) service.flushPlayer(player);
            assertEquals(1, delays.size());
            for (int count : List.of(32, 64, 65)) {
                delays.removeFirst().run();
                async.removeFirst().run();
                assertEquals(1, deliveries.size());
                deliveries.removeFirst().run();
                while (!async.isEmpty() || !deliveries.isEmpty()) {
                    if (!async.isEmpty()) async.removeFirst().run();
                    if (!deliveries.isEmpty()) deliveries.removeFirst().run();
                }
                var rows = service.loadPending(id);
                assertEquals(65, rows.size());
                for (var row : rows) assertEquals(row.createdAt() <= count ? 1 : 0, row.attempts(), row.description());
            }
            assertTrue(delays.isEmpty());
            assertTrue(async.isEmpty());
            assertTrue(deliveries.isEmpty());
        }
    }
}
