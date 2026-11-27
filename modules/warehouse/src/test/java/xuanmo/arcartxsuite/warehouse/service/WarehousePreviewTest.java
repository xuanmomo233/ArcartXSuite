package xuanmo.arcartxsuite.warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerPayloadCodec;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;

class WarehousePreviewTest {
    private static final String STORAGE = "AXS:warehouse_storage";
    private static final String MANAGE = "AXS:warehouse_manage";
    private final UUID target = UUID.randomUUID();
    private final WarehouseRepository repository = mock(WarehouseRepository.class);
    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private WarehouseService service;

    @BeforeEach
    void setup() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ui.page-size", 2);
        var config = WarehouseModuleConfiguration.load(yaml, Logger.getAnonymousLogger());
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartXSuite");
        service = new WarehouseService(plugin, Logger.getAnonymousLogger(), bridge,
            null, null, null, config, repository, mock(ItemSourceRegistry.class), null, null, () -> null, null);
        service.storageRuntimeUiId = STORAGE;
        service.manageRuntimeUiId = MANAGE;
        var active = WarehouseService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(service, true);
        when(bridge.isUiOpen(any(Player.class), eq(STORAGE))).thenReturn(true);
        when(bridge.openUi(any(Player.class), eq(STORAGE))).thenReturn(true);
        when(repository.loadPersonalWarehouses(target)).thenReturn(List.of(personal(true),
            new WarehouseRecord(target, "private", 1, "", false, 1, 0)));
        when(repository.loadSharedWarehousesByOwner(target)).thenReturn(List.of(
            new SharedWarehouseRecord("shared-public", target, "Public shared", 1, 72, 1, 1, "owner", true),
            new SharedWarehouseRecord("shared-private", target, "Private shared", 2, 216, 1, 1, "owner", false)));
    }

    @Test
    void adminCanPageAndSwitchToPrivateWarehousesThenReturnToOwnStorage() throws Exception {
        ItemFactory itemFactory = mock(ItemFactory.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            verifyAdminPreview();
        }
    }

    private void verifyAdminPreview() throws Exception {
        Player viewer = player(UUID.randomUUID(), true);
        var items = List.of(slot(0), slot(1), slot(2));
        when(repository.loadSlotSummaries("personal", target.toString(), "private")).thenReturn(items);
        when(repository.loadSlots("personal", target.toString(), "private", List.of(0, 1))).thenReturn(items.subList(0, 2));
        when(repository.loadSlots("personal", target.toString(), "private", List.of(2))).thenReturn(items.subList(2, 3));
        assertTrue(service.openPreview(viewer, target, "private").success());
        verify(bridge).openUi(viewer, STORAGE);
        var opened = latestPacket(viewer);
        assertEquals(2, opened.get("pageTotal"), "Preview must include all matching rows");
        assertEquals(true, opened.get("readOnly"));
        assertEquals("", opened.get("action_token"));
        assertEquals(2, ((Map<?, ?>) opened.get("personalWarehouses")).size());
        assertEquals(2, ((Map<?, ?>) opened.get("sharedWarehouses")).size());

        request(viewer, "page", "next");
        var second = latestPacket(viewer);
        assertEquals(2, second.get("page"), "Next must use the current server page");
        var secondSlots = (Map<?, ?>) second.get("slots");
        assertEquals(2, ((Map<?, ?>) secondSlots.get("0")).get("slot"));
        request(viewer, "page", "1.0");
        assertEquals(1, latestPacket(viewer).get("page"));

        request(viewer, "shared", "shared-private");
        var shared = latestPacket(viewer);
        assertEquals("shared-private", shared.get("warehouseId"));
        assertEquals(216L, shared.get("capacity"));
        assertEquals(false, shared.get("sharedCanEdit"));
        request(viewer, "warehouse", "private");
        assertEquals("private", latestPacket(viewer).get("warehouseId"));
        request(viewer, "open");
        verify(bridge, times(2)).openUi(viewer, STORAGE);
        assertEquals(viewer.getUniqueId().toString(), latestPacket(viewer).get("ownerId"));
        assertEquals(false, latestPacket(viewer).get("previewMode"));
        assertEquals(false, latestPacket(viewer).get("readOnly"));
    }

    @Test
    void showcaseEntryUsesPublicScopeAndChecksAccessBeforePaging() throws Exception {
        Player viewer = player(UUID.randomUUID(), true);
        assertTrue(service.openShowcasePreview(viewer, target, "personal").success());
        var packet = latestPacket(viewer);
        assertEquals(1, ((Map<?, ?>) packet.get("personalWarehouses")).size());
        assertEquals(1, ((Map<?, ?>) packet.get("sharedWarehouses")).size());
        request(viewer, "warehouse", "private");
        request(viewer, "shared", "shared-private");
        assertEquals("personal", service.state(viewer).warehouseId());

        when(repository.loadPersonalWarehouses(target)).thenReturn(List.of(personal(false)));
        clearInvocations(bridge);
        request(viewer, "page", "next");
        verify(bridge).closeUi(viewer, STORAGE);
        verify(bridge, never()).sendPacket(eq(viewer), eq(STORAGE), eq("update"), anyMap());
        assertFalse(service.viewStates.containsKey(viewer.getUniqueId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disablingShowcaseClosesOpenPublicViewsImmediately(boolean shared) throws Exception {
        Player owner = player(target, false);
        Player viewer = player(UUID.randomUUID(), false);
        Player admin = player(UUID.randomUUID(), true);
        String id = shared ? "shared-public" : "personal";
        String action = shared ? "shared_showcase_toggle" : "personal_showcase_toggle";
        if (shared) when(repository.loadSharedWarehouses(target)).thenReturn(List.of(
            new SharedWarehouseRecord(id, target, "Public shared", 1, 72, 1, 1, "owner", true)));
        assertTrue(service.openShowcasePreview(viewer, target, id).success());
        assertTrue(service.openPreview(admin, target, id).success());
        when(bridge.isUiOpen(owner, MANAGE)).thenReturn(true);
        service.issueActionToken(owner, MANAGE);
        String token = service.manageActionTokens.get(target);
        UUID viewerId = viewer.getUniqueId();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);
            request(owner, action, id, token);
        }
        if (shared) verify(repository).updateSharedWarehouseShowcase(eq(id), eq(target), eq(false), anyLong());
        else verify(repository).updatePersonalWarehouseShowcase(eq(target), eq(id), eq(false), anyLong());
        verify(bridge).closeUi(viewer, STORAGE);
        verify(bridge, never()).closeUi(admin, STORAGE);
        assertFalse(service.viewStates.containsKey(viewer.getUniqueId()));
        assertTrue(service.viewStates.get(admin.getUniqueId()).preview().administrator());
    }

    @Test
    void crossServerRevocationKeepsWarehouseIdentity() {
        org.junit.jupiter.api.Assumptions.assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        var revoke = new WarehouseCrossServerPayloadCodec.ShowcaseRevocation(target, "shared", "shared-public");
        assertEquals(revoke, WarehouseCrossServerPayloadCodec.decode(
            WarehouseCrossServerPayloadCodec.encodeShowcaseRevoked(revoke)).showcaseRevocation());
    }

    private Player player(UUID id, boolean admin) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("arcartxsuite.warehouse.use")).thenReturn(true);
        when(player.hasPermission("arcartxsuite.warehouse.admin")).thenReturn(admin);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private WarehouseRecord personal(boolean publicView) {
        return new WarehouseRecord(target, "personal", 1, "", publicView, 1, 0);
    }

    private SlotItemRecord slot(int index) {
        return new SlotItemRecord("personal", target.toString(), "private", index, "hash-" + index,
            "all", "Item " + index, "STONE", "", "", "", "", "", 1, index + 1, index + 1, "");
    }

    private void request(Player player, String... data) {
        assertTrue(service.handleClientPacket(player, "AXS_WAREHOUSE", List.of(data)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestPacket(Player viewer) {
        var packets = ArgumentCaptor.forClass(Map.class);
        verify(bridge, atLeastOnce()).sendPacket(eq(viewer), eq(STORAGE), eq("update"), packets.capture());
        return packets.getValue();
    }
}
