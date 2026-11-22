package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration.ModerationConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

final class ModerationServiceTest {
    @Test void warningAndAutomaticBanCommitTogether() throws Exception {
        var result = repository.warn(player, "KnownPlayer", "违规", "Mod", 1, 0);
        assertEquals(1, result.count());
        assertEquals(result.ban(), repository.getBan(player));
        assertEquals(java.util.List.of("AUTO_BAN", "WARN"), repository.history(player, 10, 0).stream().map(EssentialsRepository.ModerationEntry::action).toList());
    }

    @Test void failedAutomaticBanRollsBackTheWarningAndItsHistory() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_auto_ban BEFORE INSERT ON " + repository.tablePrefix() + "bans BEGIN SELECT RAISE(ABORT, 'fixture'); END");
        }
        assertThrows(java.sql.SQLException.class, () -> repository.warn(player, "KnownPlayer", "违规", "Mod", 1, 0));
        assertTrue(repository.history(player, 10, 0).isEmpty());
        assertEquals(null, repository.getBan(player));
        try (var connection = source.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + repository.tablePrefix() + "warnings")) {
            assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
        }
    }

    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final Logger logger = Logger.getAnonymousLogger();
    private SQLiteDataSource source;
    private EssentialsRepository repository;
    private ModerationConfiguration config;

    @BeforeEach
    void setUp() throws Exception {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("essentials.db"));
        repository = new EssentialsRepository(source, StorageDescriptor.sqlite("essentials.db"), directory.toFile(), logger);
        repository.initialize();
        config = new ModerationConfiguration("封禁：{reason}\\n到期：{expiry}", "", "", 5, 30, false);
    }

    @Test
    void loginRemainsClosedDuringBanInitializationAndChecksCommittedBansAfterward() throws Exception {
        repository.ban(player, "KnownPlayer", "违规", "Console", -1, null);
        try (var work = new EssentialsWork(null)) {
            var service = new ModerationService(repository, config, logger, work);
            service.beginLoadingBans();
            assertEquals(Result.KICK_OTHER, login(service).getLoginResult());
            service.initialize();
            assertEquals(Result.KICK_BANNED, login(service).getLoginResult());
            service.unban(player, "Console");
            assertEquals(Result.ALLOWED, login(service).getLoginResult());
        }
    }

    @Test
    void persistedBanBlocksReconnectAndOfflineUnbanAllowsIt() throws Exception {
        repository.ban(player, "KnownPlayer", "违规", "Console", -1, null);
        var service = new ModerationService(repository, config, logger, new EssentialsWork(null));
        service.initialize();

        var denied = login(service);
        assertEquals(Result.KICK_BANNED, denied.getLoginResult());
        assertTrue(denied.getKickMessage().contains("违规\n到期：永久"));
        assertEquals(player, service.resolveStored("knownplayer", null).id());
        assertTrue(service.unban(player, "Console"));
        assertEquals(Result.ALLOWED, login(service).getLoginResult());
        assertFalse(service.unban(player, "Console"));
    }

    @Test
    void temporaryBansRespectExpirationAndOtherLoginDenials() throws Exception {
        var service = new ModerationService(repository, config, logger, new EssentialsWork(null));
        repository.ban(player, "KnownPlayer", "违规", "Console", System.currentTimeMillis() + 60_000, null);
        assertEquals(Result.KICK_BANNED, login(service).getLoginResult());
        assertEquals(1, repository.listActiveBans(1, 0).size());

        repository.ban(player, "KnownPlayer", "违规", "Console", System.currentTimeMillis() - 1, null);
        assertEquals(Result.ALLOWED, login(service).getLoginResult());
        assertTrue(repository.listActiveBans(1, 0).isEmpty());
        var denied = new AsyncPlayerPreLoginEvent("KnownPlayer", InetAddress.getLoopbackAddress(), player);
        denied.disallow(Result.KICK_WHITELIST, "白名单");
        service.onPreLogin(denied);
        assertEquals(Result.KICK_WHITELIST, denied.getLoginResult());
        assertEquals("白名单", denied.getKickMessage());
    }

    @Test
    void banDuringAConnectionBlocksTheFinalLoginStage() throws Exception {
        var service = new ModerationService(repository, config, logger, new EssentialsWork(null));
        assertEquals(Result.ALLOWED, login(service).getLoginResult());
        AtomicReference<String> kick = new AtomicReference<>();
        Player target = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> player;
                case "getName" -> "KnownPlayer";
                case "kickPlayer" -> {
                    kick.set((String) args[0]);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var committed = service.ban(new ModerationService.Identity(player, "KnownPlayer"), "Console", "违规", -1, null);
        service.enforce(committed, target);
        assertTrue(kick.get().contains("违规"));

        var finalLogin = new PlayerLoginEvent(target, "localhost", InetAddress.getLoopbackAddress());
        service.onLogin(finalLogin);
        assertEquals(PlayerLoginEvent.Result.KICK_BANNED, finalLogin.getResult());
        assertEquals(Result.KICK_BANNED, login(service).getLoginResult());
    }

    @Test
    void storageFailureDoesNotSilentlyAllowLogin() throws Exception {
        var service = new ModerationService(repository, config, logger, new EssentialsWork(null));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE " + repository.getDescriptor().tablePrefix() + "bans");
        }
        assertEquals(Result.KICK_OTHER, login(service).getLoginResult());
    }

    @Test
    void validatesAndBoundsTemporaryPunishments() {
        assertEquals(121_000, ModerationService.expiryAt("2m", 1_000));
        assertEquals(3_000, ModerationService.expiryAt("2", 1_000));
        for (String invalid : new String[]{"0", "-1h", "2hh", "NaN", "9999999999999999999999h"}) {
            assertThrows(IllegalArgumentException.class, () -> ModerationService.expiryAt(invalid, 0));
        }
        assertThrows(ArithmeticException.class, () -> ModerationService.expiryAt("9223372036854775807d", 0));
    }

    @Test
    void expirationAndOfflineActionsKeepHistoryWithoutDuplicates() throws Exception {
        repository.rememberPlayer(player, "OldName");
        repository.rememberPlayer(player, "NewName");
        assertEquals(java.util.List.of(player), repository.findKnownPlayers("oldname"));
        repository.warn(player, "NewName", "警告", "Mod", 0, 0);
        repository.ban(player, "NewName", "临时", "Mod", System.currentTimeMillis() - 1, null);
        repository.expireBans(50);
        repository.expireBans(50);
        repository.ban(player, "NewName", "永久", "Mod", -1, null);
        assertTrue(repository.unban(player, "SecondMod"));
        var history = repository.history(player, 10, 0);
        assertEquals(java.util.List.of("UNBAN", "BAN", "EXPIRE", "TEMPBAN", "WARN"),
            history.stream().map(EssentialsRepository.ModerationEntry::action).toList());
        assertEquals("SecondMod", history.get(0).operator());
    }

    @Test
    void doesNotChangeActiveBanWhenHistoryWriteFails() throws Exception {
        repository.ban(player, "KnownPlayer", "原始封禁", "Console", -1, null);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE " + repository.tablePrefix() + "moderation_history");
        }
        assertThrows(java.sql.SQLException.class, () -> repository.unban(player, "Console"));
        assertThrows(java.sql.SQLException.class, () -> repository.ban(player, "KnownPlayer", "新封禁", "Console", -1, null));
        assertEquals("原始封禁", repository.getBan(player).reason());
    }

    private AsyncPlayerPreLoginEvent login(ModerationService service) {
        var event = new AsyncPlayerPreLoginEvent("KnownPlayer", InetAddress.getLoopbackAddress(), player);
        service.onPreLogin(event);
        return event;
    }
}
