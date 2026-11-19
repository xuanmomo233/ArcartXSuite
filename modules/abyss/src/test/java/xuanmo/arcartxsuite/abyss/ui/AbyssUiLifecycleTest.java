package xuanmo.arcartxsuite.abyss.ui;

import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

class AbyssUiLifecycleTest {
    Player player; PacketBridgeAPI bridge; List<Runnable> deferred; AbyssUiLifecycle lifecycle;
    @BeforeEach void setup() {
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        bridge=mock(PacketBridgeAPI.class);when(bridge.openUiUnsafe(any(),anyString())).thenReturn(true);
        when(bridge.sendPacket(any(),anyString(),anyString(),any())).thenReturn(true);
        deferred=new ArrayList<>();lifecycle=new AbyssUiLifecycle(bridge,(r,delay)->{assertTrue(delay==2L||delay==10L);deferred.add(r);});
    }
    @Test void firstPacketWaitsForMountAndUpdateIsDeliveredAfterInit() {
        lifecycle.open(player,"ui","init",Map.of("count",0),false);
        lifecycle.send(player,"ui","update",Map.of("count",3));
        verify(bridge,never()).sendPacket(any(),anyString(),anyString(),any());
        deferred.get(0).run();
        var order=inOrder(bridge);
        order.verify(bridge).openUiUnsafe(player,"ui");
        order.verify(bridge).sendPacket(eq(player),eq("ui"),eq("init"),argThat(p->p instanceof Map<?,?> m&&m.containsKey("view_nonce")));
        order.verify(bridge).sendPacket(player,"ui","update",Map.of("count",3));
    }
    @Test void closingBeforeMountCancelsPendingInit() {
        lifecycle.open(player,"ui","init",Map.of(),false);lifecycle.close(player,"ui");deferred.get(0).run();
        verify(bridge,never()).sendPacket(any(),anyString(),anyString(),any());
    }
    @Test void navigatingCancelsOldScreenButKeepsHud() {
        lifecycle.open(player,"hud","update",Map.of(),true);
        lifecycle.open(player,"browser","init",Map.of(),false);
        lifecycle.open(player,"party","init",Map.of(),false);
        deferred.forEach(Runnable::run);
        verify(bridge,never()).sendPacket(eq(player),eq("browser"),anyString(),any());
        verify(bridge).sendPacket(eq(player),eq("hud"),eq("update"),any());
        verify(bridge).sendPacket(eq(player),eq("party"),eq("init"),any());
    }
    @Test void staleCloseNotificationCannotCloseNewGeneration() {
        lifecycle.open(player,"ui","init",Map.of(),false);deferred.get(0).run();
        var captor=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(bridge).sendPacket(eq(player),eq("ui"),eq("init"),captor.capture());
        String nonce=(String)((Map<?,?>)captor.getValue()).get("view_nonce");
        lifecycle.open(player,"ui","init",Map.of("count",2),false);
        lifecycle.closed(player,"ui",nonce);
        assertTrue(lifecycle.isOpen(player,"ui"));
        deferred.get(1).run();
        verify(bridge,times(2)).sendPacket(eq(player),eq("ui"),eq("init"),any());
    }
    @Test void disconnectClearsPendingPacketsAndUpdatesToClosedViewsAreIgnored() {
        lifecycle.open(player,"ui","init",Map.of(),false);lifecycle.clear(player);deferred.get(0).run();
        assertFalse(lifecycle.send(player,"ui","update",Map.of()));
        verify(bridge,never()).sendPacket(any(),anyString(),anyString(),any());
    }
    @Test void queuedUpdatesAreCoalescedByHandler() {
        lifecycle.open(player,"ui","init",Map.of(),false);
        for(int i=0;i<100;i++)lifecycle.send(player,"ui","update",Map.of("count",i));
        deferred.get(0).run();
        verify(bridge,times(1)).sendPacket(player,"ui","update",Map.of("count",99));
        verify(bridge,times(2)).sendPacket(any(),anyString(),anyString(),any());
    }
    @Test void rejectedInitRetainsQueuedUpdatesUntilSuccessfulRetry() {
        when(bridge.sendPacket(eq(player),eq("ui"),eq("init"),any())).thenReturn(false,true);
        lifecycle.open(player,"ui","init",Map.of(),false);
        lifecycle.send(player,"ui","update",Map.of("count",1));deferred.get(0).run();
        verify(bridge,never()).sendPacket(eq(player),eq("ui"),eq("update"),any());
        lifecycle.send(player,"ui","update",Map.of("count",2));deferred.get(1).run();
        verify(bridge).sendPacket(player,"ui","update",Map.of("count",2));
    }
    @Test void repeatedInitFailureClosesTheViewAndStopsRetrying() {
        when(bridge.sendPacket(eq(player),eq("ui"),eq("init"),any())).thenReturn(false);
        lifecycle.open(player,"ui","init",Map.of(),false);
        for(int i=0;i<3;i++)deferred.get(i).run();
        assertEquals(3,deferred.size());assertFalse(lifecycle.isOpen(player,"ui"));
        verify(bridge).closeUiUnsafe(player,"ui");
        verify(player).sendMessage(contains("加载失败"));
    }
    @Test void nativeMountHandshakeInitialisesOnceAndCancelsTheLegacyFallback() {
        lifecycle.open(player,"ui","init",Map.of("count",1),false);
        assertTrue(lifecycle.clientReady(player,"ui"));assertFalse(lifecycle.clientReady(player,"ui"));
        lifecycle.send(player,"ui","update",Map.of("count",2));
        deferred.get(0).run();verify(bridge,never()).sendPacket(any(),anyString(),anyString(),any());
        deferred.get(1).run();
        var order=inOrder(bridge);order.verify(bridge).openUiUnsafe(player,"ui");
        order.verify(bridge).sendPacket(eq(player),eq("ui"),eq("init"),any());
        order.verify(bridge).sendPacket(player,"ui","update",Map.of("count",2));
    }
    @Test void readyCannotOpenAnUnauthorisedViewAndClosedViewsDoNotEmitClosePackets() {
        assertFalse(lifecycle.clientReady(player,"ui"));lifecycle.close(player,"ui");
        verifyNoInteractions(bridge);
    }
    @Test void failedUpdateKeepsLatestStateAndRetriesAfterMount() {
        when(bridge.sendPacket(eq(player),eq("ui"),eq("update"),any())).thenReturn(false,true);
        lifecycle.open(player,"ui","init",Map.of("count",0),false);
        lifecycle.send(player,"ui","update",Map.of("count",1));deferred.get(0).run();
        lifecycle.send(player,"ui","update",Map.of("count",2));deferred.get(1).run();
        verify(bridge).sendPacket(player,"ui","update",Map.of("count",1));
        verify(bridge).sendPacket(player,"ui","update",Map.of("count",2));
        assertTrue(lifecycle.isOpen(player,"ui"));
    }
    @Test void repeatedUpdateFailureClosesOnlyItsOwnGeneration() {
        when(bridge.sendPacket(eq(player),eq("ui"),eq("update"),any())).thenReturn(false);
        lifecycle.open(player,"ui","init",Map.of(),false);deferred.get(0).run();
        lifecycle.send(player,"ui","update",Map.of("count",1));deferred.get(1).run();deferred.get(2).run();
        assertFalse(lifecycle.isOpen(player,"ui"));verify(bridge).closeUiUnsafe(player,"ui");
    }

    @Test void unchangedHudStateDoesNotReplaceQueuedStateOrDuplicateTheInitialPacket() {
        lifecycle.open(player, "hud", "update", Map.of("count", 1), true);
        lifecycle.sendChanged(player, "hud", "update", Map.of("count", 1));
        assertTrue(lifecycle.clientReady(player, "hud"));
        deferred.get(1).run();
        verify(bridge, times(1)).sendPacket(eq(player), eq("hud"), eq("update"), any());

        when(bridge.sendPacket(player, "hud", "update", Map.of("count", 2))).thenReturn(false, true);
        assertFalse(lifecycle.sendChanged(player, "hud", "update", Map.of("count", 2)));
        assertTrue(lifecycle.sendChanged(player, "hud", "update", Map.of("count", 2)));
        deferred.get(2).run();
        lifecycle.sendChanged(player, "hud", "update", Map.of("count", 2));
        verify(bridge, times(2)).sendPacket(player, "hud", "update", Map.of("count", 2));

        lifecycle.open(player, "hud", "update", Map.of("count", 2), true);
        deferred.get(3).run();
        verify(bridge, times(4)).sendPacket(eq(player), eq("hud"), eq("update"), any());
    }
}
