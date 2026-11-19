package xuanmo.arcartxsuite.essentials.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.essentials.service.EssentialsWork;

final class EssentialsPanelTest {
    @Test
    void aCompletedReadCannotSendAfterThePanelCloses() throws Exception {
        verifyDeferredRead(false);
    }

    @Test
    void aCompletedReadRechecksThePlayersPermission() throws Exception {
        verifyDeferredRead(true);
    }

    private void verifyDeferredRead(boolean revokePermission) throws Exception {
        var commands = mock(EssentialsCommandDelegate.class);
        var work = mock(EssentialsWork.class);
        var bridge = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("axs.essentials.menu")).thenReturn(true);
        when(commands.actionEnabled("menu")).thenReturn(true);
        when(bridge.openUi(player, "menu")).thenReturn(true);
        when(bridge.isUiOpen(player, "menu")).thenReturn(true);
        var replies = new ArrayList<Runnable>();
        doAnswer(call -> { replies.add(call.getArgument(0)); return null; }).when(work).onMain(any());
        var loaded = new CompletableFuture<Map<String, Object>>();
        var panel = new EssentialsPanel(null, bridge, "menu", "menu", "axs.essentials.menu",
            "home", Set.of("home"), commands, work) {
            @Override void execute(Player player, String action, List<String> data) {}
            @Override boolean canView(Player player, Page page) { return true; }
            @Override CompletionStage<Map<String, Object>> snapshot(Player player, Page page, Map<String, Object> payload) {
                return loaded;
            }
        };
        panel.openMenu(player);
        assertTrue(replies.isEmpty());
        loaded.complete(Map.of("homes", List.of("home")));
        assertEquals(1, replies.size());
        if (revokePermission) when(player.hasPermission("axs.essentials.menu")).thenReturn(false);
        else panel.shutdown();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            replies.get(0).run();
        }
        verify(bridge, never()).sendPacket(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void allListPagesAreBoundedAndKeepTheirFilter() {
        List<Integer> records = IntStream.range(0, 105).boxed().toList();
        var payload = new HashMap<String, Object>();
        var first = new EssentialsPanel.Page("warps", 0, "主城", true);
        EssentialsPanel.pageItems(payload, "warps", EssentialsPanel.pageSlice(records, first));
        assertEquals(records.subList(0, 50), payload.get("warps"));
        assertEquals(true, payload.get("hasNext"));

        var middle = first.withIndex(1);
        assertEquals(first.query(), middle.query());
        assertEquals(first.favoritesOnly(), middle.favoritesOnly());
        EssentialsPanel.pageItems(payload, "warps", EssentialsPanel.pageSlice(records, middle));
        assertEquals(records.subList(50, 100), payload.get("warps"));
        assertEquals(true, payload.get("hasNext"));

        var last = middle.withIndex(2);
        EssentialsPanel.pageItems(payload, "warps", EssentialsPanel.pageSlice(records, last));
        assertEquals(records.subList(100, 105), payload.get("warps"));
        assertEquals(false, payload.get("hasNext"));
    }

    @Test
    void defaultUiResourcesUseThePagedProtocolDirectly() throws Exception {
        for (String file : List.of("essentials_menu.yml", "essentials_admin.yml")) {
            String path = "/arcartx/ui/" + file;
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var config = new YamlConfiguration();
                config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String page = file.equals("essentials_menu.yml") ? "home" : "players";
                assertEquals("Packet.send(var.packetId, 'refresh', '" + page + "')", config.getString("ui.action.open", ""));
                assertTrue(config.getString("ui.packetHandler.init", "").contains("packet['hasNext']"));
                assertTrue(config.getKeys(true).stream().anyMatch(key -> key.endsWith(".上一页.action.clickLeft")));
                assertTrue(config.getKeys(true).stream().anyMatch(key -> key.endsWith(".下一页.action.clickLeft")));
            }
        }
    }
}
