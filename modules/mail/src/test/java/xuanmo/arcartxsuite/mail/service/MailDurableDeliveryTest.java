package xuanmo.arcartxsuite.mail.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.model.MailPlayerProfile;
import xuanmo.arcartxsuite.mail.model.MailPresetDefinition;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;
import xuanmo.arcartxsuite.mail.storage.MailRepository.PresetCdk;

class MailDurableDeliveryTest {
    @TempDir Path directory;

    @Test void presetByUuidUsesTheSameDurableReceiptAfterReplacingTheAdapter() throws Exception {
        var logger = Logger.getAnonymousLogger();
        var repository = new JdbcMailRepository(source("preset-durable"), StorageDescriptor.sqlite("preset-durable.sqlite"),
            directory.toFile(), logger);
        repository.initialize();
        var plugin = mock(JavaPlugin.class);
        var service = new MailService(plugin, logger, directory.toFile(), MailModuleConfiguration.load(new YamlConfiguration(), logger),
            repository, null, null, null, null, null, null, null, null);
        var preset = new MailPresetDefinition("boss", true, "Boss", "Reward", "Body", null,
            List.of(new MailAttachment(0L, 0, MailAttachmentType.CURRENCY, "", "coins", 12, "Money", null)),
            List.of("say claimed"), List.of(), List.of());
        var presets = MailService.class.getDeclaredField("presets");
        presets.setAccessible(true);
        presets.set(service, Map.of("boss", preset));
        var owner = UUID.randomUUID();
        var first = new AxsMailServiceAdapter(plugin, service, logger, false);
        var second = new AxsMailServiceAdapter(plugin, service, logger, false);
        try {
            assertTrue(first.sendPresetDurable(owner, "missing", "entitytracker", "missing").get(5, TimeUnit.SECONDS).isNoOp());
            var saved = first.sendPresetDurable(owner, "boss", "entitytracker", "kill:1:0").get(5, TimeUnit.SECONDS);
            first.close();
            first.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            var replay = second.sendPresetDurable(owner, "boss", "entitytracker", "kill:1:0").get(5, TimeUnit.SECONDS);
            assertFalse(saved.duplicate());
            assertTrue(replay.duplicate());
            assertEquals(saved.mailId(), replay.mailId());
            assertEquals(1, repository.loadInbox(owner).size());
            var message = repository.loadMail(owner, replay.mailId()).orElseThrow();
            assertEquals(MailSourceType.PRESET, message.sourceType());
            assertEquals("boss", message.presetId());
            assertEquals(1, message.attachments().size());
            assertEquals(List.of("say claimed"), message.claimCommands());
        } finally {
            first.close();
            second.close();
            second.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void syncingPresetAfterAnotherNodeRedeemsKeepsTheClaimCount() throws Exception {
        var repository = new JdbcMailRepository(source("preset-cdk"), StorageDescriptor.sqlite("preset-cdk.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var now = Instant.ofEpochSecond(1_800_000_000);
        var preset = new MailCdkDefinition("LIMITED", "welcome", 1, 0, null, true, "preset:welcome", now, now);
        assertTrue(repository.createCdk(preset));
        var owner = UUID.randomUUID();
        assertTrue(repository.redeemCdk(preset.code(), owner, now.plusSeconds(1),
            id -> cdkMail(owner, preset.code(), id, now, List.of())).success());

        var changedAt = now.plusSeconds(100);
        repository.syncPresetCdks(List.of(new PresetCdk(new MailCdkDefinition(preset.code(), "welcome", 1, 0,
            changedAt.plusSeconds(600), true, "reload", changedAt, changedAt), true)));

        var saved = repository.loadCdk(preset.code()).orElseThrow();
        assertEquals(1, saved.claimedCount());
        assertEquals(now, saved.createdAt());
        assertEquals(now.plusSeconds(600), saved.expiresAt());
        assertEquals("preset:welcome", saved.createdBy());
        assertFalse(repository.redeemCdk(preset.code(), UUID.randomUUID(), changedAt, id -> fail("quota already used")).success());
    }

    @Test void failingSecondPresetWriteRollsBackTheWholeBatch() throws Exception {
        var source = source("preset-batch");
        var repository = new JdbcMailRepository(source, StorageDescriptor.sqlite("preset-batch.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var now = Instant.now();
        assertTrue(repository.createCdk(new MailCdkDefinition("FIRST", "old", 2, 0, null, true, "admin", now, now)));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_second BEFORE INSERT ON mail_cdk_definitions "
                + "WHEN NEW.code = 'SECOND' BEGIN SELECT RAISE(ABORT, 'fixture write failure'); END");
        }
        var replacements = List.of(
            new PresetCdk(new MailCdkDefinition("FIRST", "new", 5, 0, null, true, "preset:new", now, now), false),
            new PresetCdk(new MailCdkDefinition("SECOND", "new", 5, 0, null, true, "preset:new", now, now), false));
        assertThrows(java.sql.SQLException.class, () -> repository.syncPresetCdks(replacements));
        assertEquals("old", repository.loadCdk("FIRST").orElseThrow().presetId());
        assertTrue(repository.loadCdk("SECOND").isEmpty());
    }

    @Test void failedAttachmentInsertDoesNotConsumeTheCdkAndRetryDeliversOnce() throws Exception {
        var source = source("redeem-cdk");
        var repository = new JdbcMailRepository(source, StorageDescriptor.sqlite("redeem-cdk.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var player = UUID.randomUUID();
        var now = Instant.now();
        assertTrue(repository.createCdk(new MailCdkDefinition("CODE", "welcome", 2, 0, null, true, "admin", now, now)));
        assertFalse(repository.createCdk(new MailCdkDefinition("CODE", "wrong", 9, 0, null, true, "other", now, now)));
        var query = new MailInboxQuery(MailInboxFilter.ALL, 1, 6);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_attachment BEFORE INSERT ON mail_attachments BEGIN SELECT RAISE(ABORT, 'fixture failure'); END");
            var attachment = new MailAttachment(0L, 0, MailAttachmentType.ITEM, "encoded", "", 0, "奖励", null);
            java.util.function.Function<String, MailMessage> reward = id -> cdkMail(player, "CODE", id, now, List.of(attachment));
            assertThrows(java.sql.SQLException.class, () -> repository.redeemCdk("CODE", player, now, reward));
            assertEquals(0, repository.loadCdk("CODE").orElseThrow().claimedCount());
            assertTrue(repository.loadInboxView(player, query, 0).page().entries().isEmpty());
            statement.execute("DROP TRIGGER reject_attachment");
            assertTrue(repository.redeemCdk("CODE", player, now, reward).success());
            assertFalse(repository.redeemCdk("CODE", player, now, reward).success());
            assertEquals(1, repository.loadCdk("CODE").orElseThrow().claimedCount());
            var mails = repository.loadInboxView(player, query, 0).page().entries();
            assertEquals(1, mails.size());
            assertEquals("welcome", mails.get(0).presetId());
            assertEquals(1, mails.get(0).attachments().size());
        }
    }

    private static MailMessage cdkMail(UUID player, String code, String preset, Instant now, List<MailAttachment> attachments) {
        return new MailMessage(0L, player, null, "CDK", MailSourceType.CDK, preset, code, "Reward", "",
            MailStatus.UNREAD, attachments, List.of(), List.of(), now, null, now, null, null);
    }

    @Test void lateStartupProfileKeepsANewerServerAndSendTime() throws Exception {
        var repository = new JdbcMailRepository(source("startup-profile"), StorageDescriptor.sqlite("startup-profile.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var player = UUID.randomUUID();
        var sent = Instant.ofEpochSecond(1_800_000_000);
        repository.upsertPlayerProfile(new MailPlayerProfile(player, "NewName", sent, sent, "B"));
        repository.upsertPlayerProfile(new MailPlayerProfile(player, "OldName", null, sent.minusSeconds(1), "A"));
        var latest = repository.loadPlayerProfile(player).orElseThrow();
        assertEquals("NewName", latest.lastKnownName());
        assertEquals("B", latest.lastServer());
        assertEquals(sent, latest.lastSendAt());

        repository.upsertPlayerProfile(new MailPlayerProfile(player, "NewName", null, sent.plusSeconds(1), "C"));
        var moved = repository.loadPlayerProfile(player).orElseThrow();
        assertEquals("C", moved.lastServer());
        assertEquals(sent, moved.lastSendAt());
    }

    @Test void durableDeliveryRefreshesOpenLocalViewsWithoutACrossServerChannel() throws Exception {
        var repository = new JdbcMailRepository(source("local-views"), StorageDescriptor.sqlite("local.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var bridge = mock(xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.class);
        var logger = Logger.getAnonymousLogger();
        var config = MailModuleConfiguration.load(new YamlConfiguration(), logger);
        var plugin = mock(JavaPlugin.class);
        var service = new MailService(plugin, logger, directory.toFile(), config, repository,
            bridge, null, null, null, null, null, null, null);
        var player = mock(org.bukkit.entity.Player.class);
        var owner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn("MailReader");
        when(player.isOnline()).thenReturn(true);
        for (var name : List.of("inboxViewers", "logViewers")) {
            var field = MailService.class.getDeclaredField(name);
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var viewers = (java.util.Set<UUID>) field.get(service);
            viewers.add(owner);
        }
        var message = service.systemMail(owner, "New reward", "Stored", List.of(), List.of(),
            java.time.Instant.now().plusSeconds(60), "System");
        repository.insertDurableMail("a".repeat(64), message);
        var server = mock(org.bukkit.Server.class);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(server.getName()).thenReturn("test");
        when(server.getPlayer(owner)).thenReturn(player);
        when(server.getScheduler()).thenReturn(scheduler);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(org.bukkit.scheduler.BukkitTask.class);
        });
        var serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        var active = MailService.class.getDeclaredField("active");
        active.setAccessible(true);
        try {
            serverField.set(null, server);
            active.setBoolean(service, true);
            service.notifyDurableMail(message);
            for (int view = 0; view < 2; view++) {
                var callback = callbacks.poll(5, TimeUnit.SECONDS);
                assertNotNull(callback);
                callback.run();
            }
        } finally {
            try {
                service.shutdown();
                service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally { serverField.set(null, previous); }
        }
        verify(bridge, times(2)).sendPacket(eq(player), nullable(String.class), eq("update"), anyMap());
        assertTrue(repository.loadPlayerProfile(owner).isPresent());
    }

    @Test void retryCompletesOnlyTheRecipientsMissingFromAPartialSend() throws Exception {
        var source = source("partial");
        var repository = spy(new JdbcMailRepository(source, StorageDescriptor.sqlite("partial.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger()));
        repository.initialize();
        var firstPlayer = UUID.randomUUID();
        var secondPlayer = UUID.randomUUID();
        var fail = new AtomicBoolean(true);
        doAnswer(call -> {
            var message = call.<xuanmo.arcartxsuite.mail.model.MailMessage>getArgument(1);
            if (message.ownerUuid().equals(secondPlayer) && fail.getAndSet(false)) {
                throw new java.sql.SQLException("Simulated recipient write failure");
            }
            return call.callRealMethod();
        }).when(repository).insertDurableMail(anyString(), any());
        var adapter = adapter(repository);
        try {
            var request = AxsMailRequest.builder("partial-reward", "Reward").sourcePlugin("eventpacket")
                .recipient(firstPlayer).recipient(secondPlayer).currency("money", 10).build();
            assertThrows(java.util.concurrent.ExecutionException.class,
                () -> adapter.sendDurable(request).get(5, TimeUnit.SECONDS));
            assertEquals(1, repository.loadInbox(firstPlayer).size());
            assertTrue(repository.loadInbox(secondPlayer).isEmpty());
            var retried = adapter.sendDurable(request).get(5, TimeUnit.SECONDS);
            assertFalse(retried.duplicate());
            assertEquals(1, retried.recipientCount());
            var duplicate = adapter.sendDurable(request).get(5, TimeUnit.SECONDS);
            assertTrue(duplicate.duplicate());
            assertEquals(retried.mailId(), duplicate.mailId());
            assertEquals(1, repository.loadInbox(firstPlayer).size());
            assertEquals(1, repository.loadInbox(secondPlayer).size());
        } finally { adapter.close(); }
    }

    @Test void attachmentFailureRollsBackTheMailAndItsIdempotencyReceipt() throws Exception {
        var source = source("rollback");
        var repository = new JdbcMailRepository(source, StorageDescriptor.sqlite("rollback.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER reject_attachment BEFORE INSERT ON mail_attachments
                BEGIN SELECT RAISE(ABORT, 'simulated attachment failure'); END
                """);
        }
        var adapter = adapter(repository);
        var player = UUID.randomUUID();
        var request = AxsMailRequest.builder("rollback-reward", "Reward").sourcePlugin("eventpacket")
            .recipient(player).currency("money", 10).build();
        try {
            assertThrows(java.util.concurrent.ExecutionException.class,
                () -> adapter.sendDurable(request).get(5, TimeUnit.SECONDS));
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                for (var table : List.of("mail_entries", "mail_attachments", "mail_delivery_receipts")) {
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                    }
                }
                statement.execute("DROP TRIGGER reject_attachment");
            }
            var receipt = adapter.sendDurable(request).get(5, TimeUnit.SECONDS);
            assertTrue(receipt.mailId() > 0);
            assertFalse(receipt.duplicate());
            assertEquals(1, repository.loadInbox(player).size());
        } finally { adapter.close(); }
    }

    private DataSource source(String name) throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> {
            var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(name + ".sqlite"));
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=5000"); }
            return connection;
        });
        return source;
    }

    private AxsMailServiceAdapter adapter(JdbcMailRepository repository) {
        var logger = Logger.getAnonymousLogger();
        var config = MailModuleConfiguration.load(new YamlConfiguration(), logger);
        var service = new MailService(mock(JavaPlugin.class), logger, directory.toFile(), config, repository,
            null, null, null, null, null, null, null, null);
        return new AxsMailServiceAdapter(mock(JavaPlugin.class), service, logger, false);
    }

    @Test void publicApiReturnsTheActualMailIdAndOnlyInsertsOneReward() throws Exception {
        exercise("jdbc:sqlite:" + directory.resolve("mail.sqlite"), false);
    }

    @Test void sharedMysqlReturnsOneMailIdAcrossTwoAdapters() throws Exception {
        String url = System.getenv("AXS_EVENTPACKET_MYSQL_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url != null
            && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify(?:_[0-9]+)?"));
        exercise(url, true);
    }

    private void exercise(String url, boolean mysql) throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(invocation -> {
            var connection = mysql ? DriverManager.getConnection(url, "root", "") : DriverManager.getConnection(url);
            if (!mysql) {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA busy_timeout=5000");
                }
            }
            return connection;
        });
        Logger logger = Logger.getAnonymousLogger();
        var descriptor = mock(StorageDescriptor.class);
        when(descriptor.isMysql()).thenReturn(mysql);
        var repository = new JdbcMailRepository(source, descriptor, directory.toFile(), logger);
        repository.initialize();
        var plugin = mock(JavaPlugin.class);
        var config = MailModuleConfiguration.load(new YamlConfiguration(), logger);
        var service = new MailService(plugin, logger, directory.toFile(), config, repository,
            null, null, null, null, null, null, null, null);
        var first = new AxsMailServiceAdapter(plugin, service, logger, mysql);
        var second = new AxsMailServiceAdapter(plugin, service, logger, mysql);
        UUID player = UUID.randomUUID();
        try {
            assertEquals(mysql, first.usesSharedHostStorage());
            assertEquals(mysql, second.usesSharedHostStorage());
            var request = AxsMailRequest.builder("stable-clear-reward", "通关奖励").recipient(player)
                .sourcePlugin("eventpacket").content("Congratulations").currency("money", 100).build();
            var one = first.sendDurable(request);
            var two = second.sendDurable(request);
            var a = one.get(5, TimeUnit.SECONDS);
            var b = two.get(5, TimeUnit.SECONDS);
            assertTrue(a.mailId() > 0);
            assertEquals(a.mailId(), b.mailId());
            assertNotEquals(a.duplicate(), b.duplicate());
            assertEquals(1, repository.loadInbox(player).size());
            var stored = repository.loadMail(player, a.mailId()).orElseThrow();
            assertEquals(100.0, stored.attachments().get(0).amount());
        } finally {
            first.close();
            second.close();
        }
    }
}
