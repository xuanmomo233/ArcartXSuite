package xuanmo.arcartxsuite.tab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabPlayerListTest {
    @Test
    void configuredModuleSendsTheLocalListWithoutRequiringProxyEntitlement(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("config.yml"), """
            settings:
              layout: simple
              cross-server-list: true
            player-list:
              enabled: true
              include-groups: [member]
            """);
        var context = mock(ModuleContext.class);
        var bridge = mock(PacketBridgeAPI.class);
        var plugin = mock(JavaPlugin.class);
        var allowed = player("Member", "member");
        var excluded = player("Guest");
        var viewer = viewer();
        when(viewer.isOnline()).thenReturn(true);
        when(context.plugin()).thenReturn(plugin);
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.packetBridge()).thenReturn(bridge);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        when(context.advancedCrossServerOrchestrationAllowed()).thenReturn(false);
        String hud = "AXS:tab-simple";
        when(bridge.registerOrReloadUi(hud, folder.resolve("ui/tab/tab-simple.yml").toFile()))
            .thenReturn(new PacketBridgeAPI.UiRegistrationResult(true, hud, hud, "register", ""));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(allowed, excluded));
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                var handler = ArgumentCaptor.forClass(PacketHandlerSpec.class);
                verify(context).registerClientPacketHandler(handler.capture());
                var route = handler.getValue().handler();
                assertTrue(route.handleClientPacket(viewer, "AXS_TabPlayersRequest", List.of("invalid")));
                assertTrue(route.handleClientPacket(viewer, "AXS_TabPlayersRequest", List.of("open")));
                assertTrue(route.handleClientPacket(viewer, "AXS_TabPlayersRequest", List.of("refresh")));
                var payload = ArgumentCaptor.forClass(Object.class);
                verify(bridge, times(2)).sendPacket(eq(viewer), eq(hud), eq("tab-players"), payload.capture());
                assertEquals(payload.getAllValues().getFirst(), payload.getValue());
                var response = (Map<?, ?>) payload.getValue();
                assertEquals(java.util.Set.of("custom", "rows"), response.keySet());
                assertEquals(true, response.get("custom"));
                var rows = (List<?>) response.get("rows");
                assertEquals(1, rows.size());
                assertEquals("Member", ((Map<?, ?>) rows.get(0)).get("displayName"));
            } finally {
                module.onDisable();
            }
        }
    }

    @Test
    void disabledCustomRulesReturnNativeModeUsingTheSameTwoHudFiles(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("config.yml"), "settings:\n  layout: avatar\nplayer-list:\n  enabled: false\n");
        var context = mock(ModuleContext.class);
        var bridge = mock(PacketBridgeAPI.class);
        var viewer = viewer();
        when(viewer.isOnline()).thenReturn(true);
        when(context.plugin()).thenReturn(mock(JavaPlugin.class));
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.packetBridge()).thenReturn(bridge);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        String hud = "AXS:tab-avatar";
        when(bridge.registerOrReloadUi(hud, folder.resolve("ui/tab/tab-avatar.yml").toFile()))
            .thenReturn(new PacketBridgeAPI.UiRegistrationResult(true, hud, hud, "register", ""));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                assertEquals(Map.of("arcartx/ui/tab-simple.yml", "ui/tab/tab-simple.yml",
                    "arcartx/ui/tab-avatar.yml", "ui/tab/tab-avatar.yml"), module.uiSpec().resourceMappings());
                var handler = ArgumentCaptor.forClass(PacketHandlerSpec.class);
                verify(context).registerClientPacketHandler(handler.capture());
                assertTrue(handler.getValue().handler().handleClientPacket(viewer, "AXS_TabPlayersRequest", List.of("open")));
                verify(bridge).sendPacket(viewer, hud, "tab-players", Map.of("custom", false, "rows", List.of()));
            } finally {
                module.onDisable();
            }
        }
    }

    @Test
    void autoOrdersNativelyForSupportedViewersAndUsesHudForOldOrUnadmittedConnections(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("config.yml"), """
            player-list:
              enabled: true
              pinned:
                names: [Modern]
            """);
        var context = mock(ModuleContext.class);
        var bridge = mock(PacketBridgeAPI.class);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(SchedulerAPI.class);
        var task = mock(ScheduledTask.class);
        var modern = player("Modern");
        var legacy = player("Legacy");
        when(modern.canSee(any(Player.class))).thenReturn(true);
        when(legacy.canSee(any(Player.class))).thenReturn(true);
        String hud = "AXS:tab-simple";
        when(context.plugin()).thenReturn(plugin);
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.packetBridge()).thenReturn(bridge);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.scheduler()).thenReturn(scheduler);
        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        when(scheduler.scheduleAtFixedRate(eq(plugin), any(), eq(20L), eq(20L))).thenReturn(task);
        when(bridge.registerOrReloadUi(hud, folder.resolve("ui/tab/tab-simple.yml").toFile()))
            .thenReturn(new PacketBridgeAPI.UiRegistrationResult(true, hud, hud, "register", ""));

        var nativeOrder = mock(NativeTabOrder.class);
        when(nativeOrder.supportsViewer(modern)).thenReturn(true);
        when(nativeOrder.apply(anyList())).thenReturn(true);
        var restricted = new AtomicReference<UUID>();
        UUID legacyId = legacy.getUniqueId();
        try (var bukkit = mockStatic(Bukkit.class); var nativeApi = mockStatic(NativeTabOrder.class);
             var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(legacy, modern));
            nativeApi.when(NativeTabOrder::discover).thenReturn(nativeOrder);
            auth.when(() -> AuthAccess.snapshot(any(UUID.class))).thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return id.equals(restricted.get()) ? new AuthSnapshot(id, null, 1, null,
                    AuthSnapshot.Registration.UNKNOWN, AuthSnapshot.Stage.RESTRICTED, "", java.util.Set.of(), "")
                    : AuthSnapshot.absent(id);
            });
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                verify(nativeOrder).apply(List.of(modern, legacy));
                var handler = ArgumentCaptor.forClass(PacketHandlerSpec.class);
                verify(context).registerClientPacketHandler(handler.capture());
                var route = handler.getValue().handler();
                route.handleClientPacket(modern, "AXS_TabPlayersRequest", List.of("open"));
                verify(bridge).sendPacket(modern, hud, "tab-players", Map.of("custom", false,
                    "allowed", List.of(modern.getUniqueId().toString(), legacyId.toString())));
                route.handleClientPacket(legacy, "AXS_TabPlayersRequest", List.of("open"));
                var legacyPayload = ArgumentCaptor.forClass(Object.class);
                verify(bridge).sendPacket(eq(legacy), eq(hud), eq("tab-players"), legacyPayload.capture());
                assertEquals(true, ((Map<?, ?>) legacyPayload.getValue()).get("custom"));
                assertEquals(2, ((List<?>) ((Map<?, ?>) legacyPayload.getValue()).get("rows")).size());
                restricted.set(legacyId);
                clearInvocations(bridge);
                route.handleClientPacket(modern, "AXS_TabPlayersRequest", List.of("refresh"));
                var restrictedPayload = ArgumentCaptor.forClass(Object.class);
                verify(bridge).sendPacket(eq(modern), eq(hud), eq("tab-players"), restrictedPayload.capture());
                assertEquals(true, ((Map<?, ?>) restrictedPayload.getValue()).get("custom"));
                assertEquals(1, ((List<?>) ((Map<?, ?>) restrictedPayload.getValue()).get("rows")).size());
            } finally {
                module.onDisable();
            }
            verify(nativeOrder).close();
            verify(task).cancel();
        }
    }

    @Test
    void displayFormatWritesNativeNamesEvenWithCustomListDisabled(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("config.yml"), """
            display:
              name-format: '%axstitle_display_tab_prefix%&f%player_name%'
            player-list:
              enabled: false
            """);
        var context = mock(ModuleContext.class);
        var bridge = mock(PacketBridgeAPI.class);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(SchedulerAPI.class);
        var placeholders = mock(PlaceholderResolverAPI.class);
        var player = player("Ada");
        UUID id = player.getUniqueId();
        var current = new AtomicReference<>("Original");
        when(player.getPlayerListName()).thenAnswer(ignored -> current.get());
        doAnswer(call -> { current.set(call.getArgument(0)); return null; }).when(player).setPlayerListName(anyString());
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(player, "%axstitle_display_tab_prefix%&fAda")).thenReturn("&6首席 &fAda");
        when(context.plugin()).thenReturn(plugin);
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.packetBridge()).thenReturn(bridge);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.placeholderResolver()).thenReturn(placeholders);
        when(context.scheduler()).thenReturn(scheduler);
        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        when(scheduler.scheduleAtFixedRate(eq(plugin), any(), eq(20L), eq(20L))).thenReturn(mock(ScheduledTask.class));
        String hud = "AXS:tab-simple";
        when(bridge.registerOrReloadUi(hud, folder.resolve("ui/tab/tab-simple.yml").toFile()))
            .thenReturn(new PacketBridgeAPI.UiRegistrationResult(true, hud, hud, "register", ""));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                assertEquals("§6首席 §fAda", current.get());
            } finally {
                module.onDisable();
            }
            assertEquals("Original", current.get());
        }
    }

    @Test
    void formattedTitlesAreUsedInHudAndUnresolvedTokensAreNotDisplayed() throws Exception {
        var local = player("Ada");
        var placeholders = mock(PlaceholderResolverAPI.class);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(local, "%axstitle_display_tab_prefix%Ada"))
            .thenReturn("&6%000001<icon> %missing_suffix%&fAda");
        var format = new TabDisplayFormat("%axstitle_display_tab_prefix%%player_name%", placeholders);
        var ranks = new TabPlayerList(settings("player-list: {presentation: hud}\n"), placeholders, format);
        String value = "§6%000001<icon> §fAda";
        assertEquals(List.of(value), names(ranks.rowsFor(viewer(), List.of(local), List.of(), 0)));
        assertEquals(value, ranks.detailsFor(local, List.of(local), 0).displayName());
        verify(placeholders).applyPlaceholders(local, "%axstitle_display_tab_prefix%Ada");
        var noPapi = new TabDisplayFormat("%axstitle_display_tab_prefix%&f%player_name%", null);
        assertEquals("§fAda", noPapi.namesFor(List.of(local), 0).get(local));
        assertFalse(settings("player-list: {presentation: hud}\n").permitsNativeOrder());
        assertFalse(settings("player-list: {include-groups: [member]}\n").permitsNativeOrder());
        assertFalse(settings("player-list: {exclude-groups: [hidden]}\n").permitsNativeOrder());
    }

    @Test
    void backsUpOnlyAnUneditedPreviousDefaultBeforeReplacingIt(@TempDir Path folder) throws Exception {
        var installed = folder.resolve("tab-simple.yml");
        var backups = folder.resolve("backup");
        String stock = "packetHandler:\n  update: []\n";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(stock.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(installed, stock + "# Server customization\n");
        assertFalse(TabModule.backupPreviousDefault(installed, backups, hash));
        assertFalse(Files.exists(backups));
        String windowsStock = "\uFEFF" + stock.replace("\n", "\r\n");
        Files.writeString(installed, windowsStock);
        assertTrue(TabModule.backupPreviousDefault(installed, backups, hash));
        try (var files = Files.list(backups)) {
            var copies = files.toList();
            assertEquals(1, copies.size());
            assertEquals(windowsStock, Files.readString(copies.getFirst()));
        }
    }

    @Test
    void filtersGroupsAndShowsPinnedPlayersBeforeOrderedGroups() throws Exception {
        var rules = settings("""
            player-list:
              include-groups: [member, mod]
              exclude-groups: [hide]
              sort:
                placeholder: "%example_level%"
                order: desc
              groups:
                team:
                  label: 管理组
                  permission-groups: [mod]
              pinned:
                names: [carl]
                permission-groups: [supporter]
            """);
        var viewer = viewer();
        var alice = player("Alice", "member", "mod");
        var bob = player("Bob", "mod");
        var carl = player("Carl", "member");
        var dana = player("Dana", "member");
        var eli = player("Eli", "mod", "supporter");
        var guest = player("Guest");
        var excluded = player("Excluded", "member", "hide");
        var hidden = player("Hidden", "mod");
        when(viewer.canSee(hidden)).thenReturn(false);
        var placeholders = mock(PlaceholderResolverAPI.class);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(alice, "%example_level%")).thenReturn("§a20");
        when(placeholders.applyPlaceholders(bob, "%example_level%")).thenReturn("9");
        when(placeholders.applyPlaceholders(carl, "%example_level%")).thenReturn("3");
        when(placeholders.applyPlaceholders(dana, "%example_level%")).thenReturn("1,200");
        when(placeholders.applyPlaceholders(eli, "%example_level%")).thenReturn("5");
        when(placeholders.applyPlaceholders(hidden, "%example_level%")).thenReturn("200");

        var playerList = new TabPlayerList(rules, placeholders);
        var players = List.of(guest, excluded, dana, carl, bob, alice, hidden, eli);
        assertEquals(List.of("置顶", "Eli", "Carl", "管理组", "Alice", "Bob", "其他玩家", "Dana"),
            names(playerList.rowsFor(viewer, players, List.of(), 0)));
        playerList.rowsFor(viewer, players, List.of(), 10);
        verify(placeholders).applyPlaceholders(alice, "%example_level%");
    }

    @Test
    void unresolvedOrNonFiniteNumericValuesSortAfterNumbers() throws Exception {
        var rules = settings("""
            player-list:
              sort:
                placeholder: "%example_level%"
                numeric: true
                order: desc
            """);
        var alice = player("Alice");
        var bob = player("Bob");
        var dan = player("Dan");
        var eve = player("Eve");
        var placeholders = mock(PlaceholderResolverAPI.class);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(alice, "%example_level%")).thenReturn("%example_level%");
        when(placeholders.applyPlaceholders(bob, "%example_level%")).thenReturn("Infinity");
        when(placeholders.applyPlaceholders(dan, "%example_level%")).thenReturn("1");
        when(placeholders.applyPlaceholders(eve, "%example_level%")).thenReturn("10");
        assertEquals(List.of("Eve", "Dan", "Alice", "Bob"),
            names(new TabPlayerList(rules, placeholders).rowsFor(viewer(), List.of(alice, bob, dan, eve), List.of(), 0)));

        when(placeholders.available()).thenReturn(false);
        assertEquals(List.of("Alice", "Bob", "Dan", "Eve"),
            names(new TabPlayerList(rules, placeholders).rowsFor(viewer(), List.of(eve, bob, dan, alice), List.of(), 0)));
    }

    @Test
    void implicitOperatorPermissionsDoNotGrantGroupMembership() throws Exception {
        var operator = player("Operator");
        var member = player("Member", "member");
        when(operator.hasPermission("group.member")).thenReturn(true);
        var playerList = new TabPlayerList(settings("player-list:\n  include-groups: [member]\n"), null);
        assertEquals(List.of("Member"), names(playerList.rowsFor(viewer(), List.of(operator, member), List.of(), 0)));
    }

    @Test
    void considersPlayersOutsideTheVanillaWindowAndOmitsRestrictedConnections() throws Exception {
        var rules = settings("""
            player-list:
              pinned:
                names: [Player84]
            """);
        var players = IntStream.range(0, 85).mapToObj(i -> player(String.format(Locale.ROOT, "Player%02d", i))).toList();
        UUID restricted = players.get(0).getUniqueId();
        try (var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.snapshot(any(UUID.class))).thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return restricted.equals(id) ? new AuthSnapshot(id, null, 1, null, AuthSnapshot.Registration.UNKNOWN,
                    AuthSnapshot.Stage.RESTRICTED, "", java.util.Set.of(), "") : AuthSnapshot.absent(id);
            });
            var result = names(new TabPlayerList(rules, null).rowsFor(viewer(), players, List.of(), 0));
            assertEquals(80, result.size());
            assertEquals("Player84", result.get(0));
            assertFalse(result.contains("Player00"));
        }
    }

    @Test
    void mergesSourcePermissionAndSortValuesWithLocalRulesAndDoesNotReclassifyLocalConnections() throws Exception {
        var rules = settings("""
            player-list:
              include-groups: [staff, member]
              exclude-groups: [hidden]
              sort:
                placeholder: "%level%"
                order: desc
              groups:
                staff:
                  label: 管理组
                  permission-groups: [staff]
              pinned:
                names: [pinned]
            """);
        var local = player("Local", "staff");
        var vanishedLocal = player("VanishedLocal", "staff");
        var viewer = viewer();
        when(viewer.canSee(vanishedLocal)).thenReturn(false);
        var placeholders = mock(PlaceholderResolverAPI.class);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(local, "%level%")).thenReturn("20");
        var playerList = new TabPlayerList(rules, placeholders);
        var inputsKey = rules.inputsKey(rules.permissionInputs());
        var remote = List.of(
            remote("Higher", inputsKey, 4, "25"),
            remote("Lower", inputsKey, 4, "10"),
            remote("Pinned", inputsKey, 2, "1"),
            remote("Hidden", inputsKey, 5, "900"),
            remote("Guest", inputsKey, 0, "800"),
            remote("OtherInputs", "not-the-current-inputs", 4, "700"),
            remote("VanishedLocal", inputsKey, 4, "999"));

        assertEquals(List.of("置顶", "Pinned", "管理组", "Higher", "Local", "Lower"),
            names(playerList.rowsFor(viewer, List.of(local, vanishedLocal), remote, 0)));
    }

    private static TabPlayerList.RemotePlayer remote(String name, String inputsKey, long mask, String value) {
        var id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new TabPlayerList.RemotePlayer(id, name, 23, new TabPlayerList.Details(inputsKey, List.of(mask), value));
    }

    private static TabListSettings settings(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return TabListSettings.load(config);
    }

    private static Player player(String name, String... groups) {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        for (String group : groups) {
            String permission = "group." + group;
            when(player.isPermissionSet(permission)).thenReturn(true);
            when(player.hasPermission(permission)).thenReturn(true);
        }
        return player;
    }

    private static Player viewer() {
        var viewer = mock(Player.class);
        when(viewer.canSee(any(Player.class))).thenReturn(true);
        return viewer;
    }

    private static List<Object> names(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> row.get("displayName")).toList();
    }
}
