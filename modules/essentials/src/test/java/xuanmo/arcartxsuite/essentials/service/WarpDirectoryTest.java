package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

final class WarpDirectoryTest {
    @TempDir Path directory;
    private final Logger logger = Logger.getAnonymousLogger();
    private SQLiteDataSource source;
    private EssentialsRepository repository;
    private Path configFile;

    @BeforeEach
    void initialize() throws Exception {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("essentials.db"));
        repository = new EssentialsRepository(source, StorageDescriptor.sqlite("essentials.db"), directory.toFile(), logger);
        repository.initialize();
        configFile = directory.resolve("config.yml");
    }

    @Test
    void persistsCreateUpdateAndDeleteAndKeepsUnrelatedSettings() throws Exception {
        String original = """
            # 配置注释
            debug: false
            warps:
              Hub:
                world: world
                x: 12.5
                y: 70
                z: 30
                yaw: 90
                group: 主城
                permission: warp.vip
                enabled: false
            """;
        Files.writeString(configFile, original);
        WarpDirectory warps = loadDirectory();
        Files.writeString(configFile, original.replace("debug: false", "debug: true"));

        warps.setWarp("hub", "world_nether", 14, 75, 32.5, 0, -5);
        warps.setWarp("活动广场", "world", 100, 64, -7, 90, 0);
        var saved = YamlConfiguration.loadConfiguration(configFile.toFile());
        assertTrue(saved.getBoolean("debug"));
        assertTrue(Files.readString(configFile).contains("# 配置注释"));
        assertEquals("主城", saved.getString("warps.Hub.group"));
        assertEquals("warp.vip", saved.getString("warps.Hub.permission"));
        assertFalse(saved.getBoolean("warps.Hub.enabled"));
        assertEquals("world_nether", saved.getString("warps.Hub.world"));
        assertEquals(32.5, saved.getDouble("warps.Hub.z"));
        assertEquals(0, saved.getDouble("warps.Hub.yaw"));
        assertEquals(-5, saved.getDouble("warps.Hub.pitch"));
        assertEquals(List.of("Hub", "活动广场"), warps.adminNames(""));
        assertEquals(warps.adminNames(""), loadDirectory().adminNames(""));
        assertTrue(warps.deleteWarp("hUb"));
        assertFalse(warps.deleteWarp("Hub"));
        assertEquals(List.of("活动广场"), warps.adminNames(""));
        assertEquals(warps.adminNames(""), loadDirectory().adminNames(""));
        assertTrue(warps.deleteWarp("活动广场"));
        assertTrue(loadDirectory().adminNames("").isEmpty());
    }

    @Test
    void refusesToOverwriteInvalidConfigOrAnAmbiguousName() throws Exception {
        Files.writeString(configFile, "warps: {}\n");
        WarpDirectory warps = loadDirectory();
        warps.setWarp("hub", "world", 1, 64, 2, 0, 0);
        String malformed = "warps: [broken\n";
        Files.writeString(configFile, malformed);
        assertThrows(IOException.class, () -> warps.deleteWarp("hub"));
        assertEquals(malformed, Files.readString(configFile));
        assertEquals(List.of("hub"), warps.adminNames(""));

        String duplicate = "warps:\n  hub: {}\n  Hub: {}\n";
        Files.writeString(configFile, duplicate);
        assertThrows(IllegalArgumentException.class, () -> warps.setWarp("hub", "world", 1, 64, 2, 0, 0));
        assertEquals(duplicate, Files.readString(configFile));
    }

    @Test
    void listsYamlWarpsWithPermissionsAndKeepsPersonalFavoritesInStorage() throws Exception {
        Files.writeString(configFile, """
            warps:
              Hub:
                world: world
                x: 12.5
                y: 70
                z: 30
                group: 主城
                permission: warp.vip
              closed:
                enabled: false
            """);
        var warps = loadDirectory();
        UUID id = UUID.randomUUID();
        assertTrue(warps.visible(player(id, false)).isEmpty());
        Player member = player(id, true);
        assertEquals(List.of("Hub"), warps.names(member, false));
        assertTrue(warps.toggleFavorite(id, warps.favoriteKey(member, "hub")));
        var favorites = warps.list(id, warps.visible(member), "主城", true);
        assertEquals(List.of(new WarpDirectory.Entry("Hub", "主城", true)), favorites);
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, repository.tablePrefix() + "warps");
            try (var rows = statement.executeQuery()) {
                assertFalse(rows.next());
            }
        }
    }

    private WarpDirectory loadDirectory() {
        return new WarpDirectory(repository, configFile, YamlConfiguration.loadConfiguration(configFile.toFile()), logger);
    }

    private static Player player(UUID id, boolean allowed) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "hasPermission" -> allowed && "warp.vip".equals(args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
