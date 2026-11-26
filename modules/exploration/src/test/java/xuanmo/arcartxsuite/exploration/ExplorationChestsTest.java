package xuanmo.arcartxsuite.exploration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.message.MessageProvider;

class ExplorationChestsTest {
    private static final String UI = "AXS:exploration_chest";
    private final ChestModels models = mock(ChestModels.class);
    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private final ItemRewardDispatcher rewards = mock(ItemRewardDispatcher.class);
    private final ItemRewardDispatcher.DispatchOptions options =
        ItemRewardDispatcher.DispatchOptions.builder("exploration").sourceDetail("ruins").build();
    private final Player owner = mock(Player.class);
    private final Location origin = new Location(mock(World.class), 4.5, 71, -5.5);
    private final UUID ownerId = UUID.randomUUID();
    private final AtomicBoolean admitted = new AtomicBoolean(true);
    private ExplorationChests chests;
    private Consumer<Player> click;
    private Runnable clientOpened;
    private boolean uiOpen;
    private Map<?, ?> payload;

    @BeforeEach void setUp() {
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(owner.getLocation()).thenAnswer(call -> origin.clone());
        when(owner.isOnline()).thenReturn(true);
        when(models.inReach(owner)).thenReturn(true);
        when(models.spawn(eq(owner), eq("ruins_chest"), eq(1.25), any())).thenAnswer(call -> {
            click = call.getArgument(3);
            return true;
        });
        when(bridge.openUiWithCallback(eq(owner), eq(UI), any())).thenAnswer(call -> {
            clientOpened = call.getArgument(2);
            uiOpen = true;
            return true;
        });
        when(bridge.isUiOpen(owner, UI)).thenAnswer(call -> uiOpen);
        when(bridge.closeUi(owner, UI)).thenAnswer(call -> { uiOpen = false; return true; });
        when(bridge.sendPacket(eq(owner), eq(UI), eq("sync"), any())).thenAnswer(call -> {
            payload = call.getArgument(3);
            return true;
        });
        var names = mock(VanillaItemNameBridge.class);
        when(names.resolveDisplayName(any())).thenReturn("铁锭");
        var icons = mock(ItemBridgeAPI.class);
        when(icons.itemToPreviewJson(any())).thenReturn(Optional.of("icon"));
        when(rewards.dispatchItems(eq(owner), anyList(), any())).thenReturn(ItemRewardDispatcher.DispatchResult.empty());
        chests = new ExplorationChests(models, bridge, UI, icons, names, rewards, mock(MessageProvider.class),
            player -> admitted.get());
    }

    private void spawn() {
        var items = List.of(new ItemStack(Material.IRON_INGOT, 3));
        assertTrue(chests.spawn(owner, "遗迹宝箱", "ruins_chest", 1.25, items, options));
        assertTrue(chests.pending(ownerId));
    }

    private String open() {
        payload = null;
        click.accept(owner);
        assertNull(payload, "等待客户端打开界面后再同步");
        clientOpened.run();
        assertNotNull(payload, "客户端确认打开后发送战利品");
        assertEquals("遗迹宝箱", payload.get("title"));
        assertEquals(1, payload.get("count"));
        var rows = (Map<?, ?>) payload.get("rows");
        var item = (Map<?, ?>) rows.get("0");
        assertEquals("铁锭", item.get("name"));
        assertEquals(3, item.get("amount"));
        assertEquals("icon", item.get("icon"));
        String token = (String) payload.get("token");
        assertNotNull(token);
        assertFalse(token.isBlank());
        return token;
    }

    @Test void onlyClickingTheDiscoveredModelOpensItsOwnersUi() {
        spawn();
        verifyNoInteractions(bridge, rewards);
        var other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        click.accept(other);
        verifyNoInteractions(bridge, rewards);
        String token = open();
        chests.claim(other, List.of("claim", token));
        var reconnected = mock(Player.class);
        when(reconnected.getUniqueId()).thenReturn(ownerId);
        when(reconnected.isOnline()).thenReturn(true);
        when(models.inReach(reconnected)).thenReturn(true);
        when(bridge.isUiOpen(reconnected, UI)).thenReturn(true);
        chests.claim(reconnected, List.of("claim", token));
        chests.claim(owner, List.of("claim", UUID.randomUUID().toString()));
        verifyNoInteractions(rewards);
        assertTrue(chests.pending(ownerId));
    }

    @Test void closingAndReopeningKeepsLootAndOnlyTheLatestWindowCanClaimItOnce() {
        spawn();
        String oldToken = open();
        Runnable oldOpen = clientOpened;
        uiOpen = false; // Client CLOSE makes the host bridge forget the open UI.
        chests.claim(owner, List.of("claim", oldToken));
        verifyNoInteractions(rewards);
        assertTrue(chests.pending(ownerId));
        String token = open();
        assertNotEquals(oldToken, token);
        oldOpen.run(); // A late OPEN confirmation must not replace the latest token and rows.
        assertEquals(token, payload.get("token"));
        chests.claim(owner, List.of("claim", oldToken));
        verifyNoInteractions(rewards);
        chests.claim(owner, List.of("claim", token));
        chests.claim(owner, List.of("claim", token));
        verify(rewards, times(1)).dispatchItems(eq(owner), argThat(items -> items.size() == 1
            && items.get(0).getType() == Material.IRON_INGOT && items.get(0).getAmount() == 3), same(options));
        verify(models).remove(owner);
        assertFalse(chests.pending(ownerId));
    }

    @Test void movingOutOfReachAfterOpeningPreventsClaim() {
        spawn();
        String token = open();
        when(models.inReach(owner)).thenReturn(false);
        chests.claim(owner, List.of("claim", token));
        verifyNoInteractions(rewards);
    }

    @Test void failedUiSyncAllowsReopenButCannotAuthorizeClaim() {
        spawn();
        AtomicBoolean syncAvailable = new AtomicBoolean(false);
        when(bridge.sendPacket(eq(owner), eq(UI), eq("sync"), any())).thenAnswer(call -> {
            payload = call.getArgument(3);
            return syncAvailable.get();
        });
        String undelivered = open();
        verify(bridge).closeUi(owner, UI);
        chests.claim(owner, List.of("claim", undelivered));
        verifyNoInteractions(rewards);
        assertTrue(chests.pending(ownerId));
        syncAvailable.set(true);
        String retried = open();
        assertNotEquals(undelivered, retried);
        chests.claim(owner, List.of("claim", retried));
        verify(rewards).dispatchItems(eq(owner), anyList(), same(options));
    }

    @Test void losingAdmissionInvalidatesAnOtherwiseValidClaim() {
        spawn();
        String token = open();
        admitted.set(false);
        chests.claim(owner, List.of("claim", token));
        verifyNoInteractions(rewards);
    }

    @Test void quittingDestroysModelAndStaleClickCannotOpenOrClaim() {
        spawn();
        String token = open();
        Consumer<Player> staleClick = click;
        chests.onQuit(new PlayerQuitEvent(owner, ""));
        assertFalse(chests.pending(ownerId));
        staleClick.accept(owner);
        verify(bridge, times(1)).openUiWithCallback(eq(owner), eq(UI), any());
        chests.claim(owner, List.of("claim", token));
        verifyNoInteractions(rewards);
    }

    @Test void walkingAwayOrChangingWorldDiscardsThePrivateModel() {
        spawn();
        chests.onMove(new PlayerMoveEvent(owner, origin, origin.clone().add(32, 0, 0)));
        assertTrue(chests.pending(ownerId));
        chests.onMove(new PlayerMoveEvent(owner, origin, origin.clone().add(33, 0, 0)));
        assertFalse(chests.pending(ownerId));

        spawn();
        Location otherWorld = new Location(mock(World.class), origin.getX(), origin.getY(), origin.getZ());
        chests.onTeleport(new PlayerTeleportEvent(owner, origin, otherWorld));
        assertFalse(chests.pending(ownerId));
        verify(models, times(2)).remove(owner);
        verifyNoInteractions(rewards);
    }
}
