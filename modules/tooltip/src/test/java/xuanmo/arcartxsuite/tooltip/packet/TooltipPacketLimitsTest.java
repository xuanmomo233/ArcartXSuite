package xuanmo.arcartxsuite.tooltip.packet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.service.GunListService;
import xuanmo.arcartxsuite.tooltip.service.TooltipJsonParser;

class TooltipPacketLimitsTest {
    @Test
    void oversizedUtf8AndNestedJsonDoNotReplaceLocalTooltip() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        var cache = new TooltipDataCache();
        var handler = new TooltipPacketHandler(cache, new TooltipJsonParser(), null);
        String key = "test:gun";
        String packetId = TooltipPacketHandler.PACKET_ID;
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            handler.handleClientPacket(player, packetId, List.of(key, key, "[\"valid\"]", "{}"));
            handler.handleClientPacket(player, packetId,
                List.of(key, key, "[\"" + "枪".repeat(11_000) + "\"]", "{}"));
            handler.handleClientPacket(player, packetId, List.of(key, key, "[[\"nested\"]]", "{}"));
            handler.handleClientPacket(player, packetId, List.of(key, "test:other", "[\"wrong\"]", "{}"));
            handler.handleClientPacket(player, packetId, List.of(key, key, "[\"wrong\"]", "[]"));
            handler.handleClientPacket(player, packetId, List.of(key, key, "[\"wrong\"]",
                "{\"child\":" + "[".repeat(16) + "0" + "]".repeat(16) + "}"));
            assertEquals(List.of("valid"), cache.get(player, key).textLines());
        }
    }

    @Test
    void identicalReportsSkipBothParsersAndPartialUpdatesReuseUnchangedFields() {
        UUID id = UUID.randomUUID();
        Player current = mock(Player.class);
        Player previous = mock(Player.class);
        when(current.getUniqueId()).thenReturn(id);
        when(previous.getUniqueId()).thenReturn(id);
        var parser = spy(new TooltipJsonParser());
        var cache = new TooltipDataCache();
        var handler = new TooltipPacketHandler(cache, parser, null);
        String key = "test:gun";
        var report = List.of(key, key, "[\"valid\"]", "{\"ammo\":3}");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(current);
            handler.handleClientPacket(current, TooltipPacketHandler.PACKET_ID, report);
            handler.handleClientPacket(current, TooltipPacketHandler.PACKET_ID, report);
            handler.handleClientPacket(previous, TooltipPacketHandler.PACKET_ID, List.of(key, key, "[\"old\"]", "{}"));
            verify(parser).parseStringArray(anyString());
            verify(parser).isStructuredObject(anyString());
            assertEquals("{\"ammo\":3}", cache.get(current, key).structuredData());
            assertNull(cache.get(previous, key));

            var originalLines = cache.get(current, key).textLines();
            handler.handleClientPacket(current, TooltipPacketHandler.PACKET_ID, List.of(key, key, "[\"valid\"]", "{\"ammo\":2}"));
            verify(parser).parseStringArray(anyString());
            verify(parser, times(2)).isStructuredObject(anyString());
            assertEquals("{\"ammo\":2}", cache.get(current, key).structuredData());
            assertSame(originalLines, cache.get(current, key).textLines());

            handler.handleClientPacket(current, TooltipPacketHandler.PACKET_ID, List.of(key, key, "[\"new line\"]", "{\"ammo\":2}"));
            verify(parser, times(2)).parseStringArray(anyString());
            verify(parser, times(2)).isStructuredObject(anyString());
            assertEquals(List.of("new line"), cache.get(current, key).textLines());

            handler.handleClientPacket(current, TooltipPacketHandler.PACKET_ID, List.of(key, key, "[\"new line\"]", "[]"));
            assertEquals("{\"ammo\":2}", cache.get(current, key).structuredData());
            assertEquals(List.of("new line"), cache.get(current, key).textLines());
        }
    }

    @Test
    void rejectsMalformedRegistryIdsBeforeJsonParsing() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var parser = spy(new TooltipJsonParser());
        var cache = new TooltipDataCache();
        var handler = new TooltipPacketHandler(cache, parser, null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            for (String key : List.of("bare", "tacz:", ":gun", "TACZ:gun", "tacz:gun:extra", "枪:gun", "t/acz:gun")) {
                handler.handleClientPacket(player, TooltipPacketHandler.PACKET_ID, List.of(key, key, "[\"valid\"]", "{}"));
                assertNull(cache.get(player, key));
            }
            verifyNoInteractions(parser);

            String allowed = "mod-1.2:attachments/a_b";
            handler.handleClientPacket(player, TooltipPacketHandler.PACKET_ID, List.of(allowed, allowed, "[\"valid\"]", "{}"));
            assertEquals(List.of("valid"), cache.get(player, allowed).textLines());
        }
    }

    @Test
    void countsUtf8WithoutAllocatingAnEncodedCopy() {
        assertTrue(TooltipPacketHandler.withinBytes("abc", 3));
        assertFalse(TooltipPacketHandler.withinBytes("枪", 2));
        assertTrue(TooltipPacketHandler.withinBytes("枪", 3));
        String supplementary = new String(Character.toChars(0x1F600));
        assertTrue(TooltipPacketHandler.withinBytes(supplementary, 4));
        assertFalse(TooltipPacketHandler.withinBytes(supplementary + "a", 4));
        assertTrue(TooltipPacketHandler.withinBytes(String.valueOf((char) 0xD800), 1));
    }

    @Test
    void gunOpenRequiresAxModeAndDoesNotUseUnsafeFallback() {
        Player player = mock(Player.class);
        Player previous = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(previous.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(previous.isOnline()).thenReturn(true);
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        var gunList = new GunListService(mock(Plugin.class), bridge);
        gunList.bindUi("AXS:gun_list");
        var handler = new TooltipPacketHandler(new TooltipDataCache(), new TooltipJsonParser(), gunList);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            gunList.setMode(GunListService.UiMode.MOD);
            handler.handleGunList(player, TooltipPacketHandler.GUN_LIST_PACKET_ID, List.of("open"));
            verify(bridge, never()).openUi(player, "AXS:gun_list");
            gunList.setMode(GunListService.UiMode.AX);
            handler.handleGunList(previous, TooltipPacketHandler.GUN_LIST_PACKET_ID, List.of("open"));
            handler.handleGunList(player, TooltipPacketHandler.GUN_LIST_PACKET_ID, List.of("open"));
            verify(bridge, never()).openUi(previous, "AXS:gun_list");
            verify(bridge).openUi(player, "AXS:gun_list");
            verify(bridge, never()).openUiUnsafe(player, "AXS:gun_list");
        }
    }
}
