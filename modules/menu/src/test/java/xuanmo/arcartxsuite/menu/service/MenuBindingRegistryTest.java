package xuanmo.arcartxsuite.menu.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.menu.config.MenuCommandBinding;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuItemBinding;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.listener.MenuBindingListener;

class MenuBindingRegistryTest {
    private final MenuBindingRegistry registry = new MenuBindingRegistry();
    private final Logger logger = Logger.getAnonymousLogger();
    private final Player player = mock(Player.class);
    private final ItemStack stack = mock(ItemStack.class);
    private final ItemMeta meta = mock(ItemMeta.class);

    @AfterEach
    void resetConditions() { ScriptConditionServices.reset(); }

    @Test
    void anEarlierRegexRetainsPriorityOverALiteral() {
        registry.rebuild(List.of(menu("regex", List.of(), List.of("warp.*")),
            menu("literal", List.of("warp"), List.of())), List.of(), logger);
        assertEquals("regex", registry.matchCommand("/WARP spawn").menuId());
    }

    @Test
    void earlierLiteralPrefixesRetainPriorityAcrossMoreSpecificCommands() {
        registry.rebuild(List.of(menu("broad", List.of("warp"), List.of()),
            menu("narrow", List.of("warp spawn"), List.of()),
            menu("regex", List.of(), List.of("warp.*"))), List.of(), logger);
        assertEquals("broad", registry.matchCommand("/warp spawn").menuId());
        assertEquals("regex", registry.matchCommand("/warped").menuId());
        assertNull(registry.matchCommand("/unrelated"));
        assertNull(registry.matchCommand("/"));
    }

    @Test
    void indexedLookupMatchesTheOrderedScanForMixedCommands() {
        List<MenuDefinition> menus = new ArrayList<>();
        List<MenuCommandBinding> ordered = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            MenuDefinition menu = menu("menu" + i, List.of("cmd" + i, "shared " + i),
                i % 7 == 0 ? List.of("cmd" + i + ".*", "shared " + i + ".*") : List.of());
            menus.add(menu);
            for (String command : menu.commands()) {
                ordered.add(new MenuCommandBinding(menu.id(), command, false, null, ""));
            }
            for (String regex : menu.commandRegex()) {
                ordered.add(new MenuCommandBinding(menu.id(), regex, true,
                    Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), ""));
            }
        }
        registry.rebuild(menus, List.of(), logger);
        for (int i = 0; i < 150; i++) {
            for (String input : List.of("cmd" + i, "CMD" + i + " arg", "cmd" + i + "suffix", "shared " + i + " arg")) {
                MenuCommandBinding expected = ordered.stream().filter(binding -> binding.matches(input)).findFirst().orElse(null);
                MenuCommandBinding actual = registry.matchCommand(" /" + input + " ");
                assertEquals(expected == null ? null : expected.menuId(), actual == null ? null : actual.menuId(), input);
                assertEquals(expected == null ? null : expected.pattern(), actual == null ? null : actual.pattern(), input);
            }
        }
    }

    @Test
    void commandCaseMatchingIsIndependentOfTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            registry.rebuild(List.of(menu("kit", List.of("kit"), List.of())), List.of(), logger);
            assertEquals("kit", registry.matchCommand("/KIT starter").menuId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void snapshotsAreImmutableAndRemainValidAcrossReload() {
        registry.rebuild(List.of(), List.of(binding("old", "STONE", "", "", "", -1)), logger);
        List<MenuItemBinding> snapshot = registry.itemBindings();
        assertSame(snapshot, registry.itemBindings());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        registry.rebuild(List.of(), List.of(), logger);
        assertEquals("old", snapshot.get(0).menuId());
        assertTrue(registry.itemBindings().isEmpty());
    }

    @Test
    void materialOnlyRulesDoNotReadItemMetadata() {
        when(stack.getType()).thenReturn(Material.STONE);
        registry.rebuild(List.of(), List.of(binding("stone", "STONE", "", "", "", -1)), logger);
        assertEquals("stone", match().menuId());
        verify(stack, never()).getItemMeta();
    }

    @Test
    void repeatedRulesReadAndNormalizeTheSameMetadataOnce() {
        when(stack.getType()).thenReturn(Material.STONE);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§aMenu Kit");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("§6Special Token"));
        when(meta.hasCustomModelData()).thenReturn(true);
        when(meta.getCustomModelData()).thenReturn(42);
        registry.rebuild(List.of(), List.of(
            binding("name-miss", "STONE", "missing", "", "", -1),
            binding("lore-miss", "STONE", "menu", "", "missing", -1),
            binding("match", "STONE", "&aMENU", "kit$", "TOKEN", 42)), logger);
        assertEquals("match", match().menuId());
        verify(stack).getItemMeta();
        verify(meta).getDisplayName();
        verify(meta).getLore();
    }

    @Test
    void invalidRegexDoesNotThrowDuringInteractionsOrMatchBroadly() {
        when(stack.getType()).thenReturn(Material.STONE);
        registry.rebuild(List.of(), List.of(binding("broken", "STONE", "", "[", "", -1)), logger);
        assertNull(assertDoesNotThrow(this::match));
        verify(stack, never()).getItemMeta();
    }

    @Test
    void invalidMaterialDoesNotBecomeAnUnrestrictedRule() {
        when(stack.getType()).thenReturn(Material.STONE);
        registry.rebuild(List.of(), List.of(binding("broken", "invalid_material", "", "", "", -1)), logger);
        assertNull(match());
    }

    @Test
    void reloadReplacesTheCompiledPatternsAndPreservesRuleOrder() {
        when(stack.getType()).thenReturn(Material.STONE);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Ticket");
        registry.rebuild(List.of(), List.of(binding("first", "STONE", "", "ticket", "", -1),
            binding("second", "STONE", "", "ticket", "", -1)), logger);
        assertEquals("first", match().menuId());
        registry.rebuild(List.of(), List.of(binding("replacement", "STONE", "", "other", "", -1)), logger);
        assertNull(match());
    }

    @Test
    void handSneakAndCurrentPermissionsAreRechecked() {
        when(stack.getType()).thenReturn(Material.STONE);
        MenuItemBinding binding = new MenuItemBinding("restricted", "STONE", "", "", "", -1,
            Action.RIGHT_CLICK_AIR, true, true, false, "menu.private");
        registry.rebuild(List.of(), List.of(binding), logger);
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission("menu.private")).thenReturn(true);
        assertEquals("restricted", match().menuId());
        assertNull(registry.matchItem(player, stack, Action.RIGHT_CLICK_AIR, false));
        assertNull(registry.matchItem(player, stack, Action.LEFT_CLICK_AIR, true));
        when(player.hasPermission("menu.private")).thenReturn(false);
        assertNull(match());
        when(player.hasPermission("menu.private")).thenReturn(true);
        when(player.isSneaking()).thenReturn(false);
        assertNull(match());
    }

    @Test
    void anEmptyNormalizedLoreNeedleStillRequiresALoreLine() {
        when(stack.getType()).thenReturn(Material.STONE);
        when(stack.getItemMeta()).thenReturn(meta);
        registry.rebuild(List.of(), List.of(binding("lore", "STONE", "", "", "&r", -1)), logger);
        assertNull(match());
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("anything"));
        assertEquals("lore", match().menuId());
    }

    @Test
    void anAirInteractionCanTriggerWithoutEnablingVanillaItemUse() {
        MenuService service = listenerService();
        var listener = new MenuBindingListener(() -> service);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, stack, null, BlockFace.UP, EquipmentSlot.HAND);
        assertTrue(event.isCancelled());
        assertEquals(Event.Result.DEFAULT, event.useItemInHand());
        try (var auth = admitListenerPlayer()) {
            listener.onItemInteract(event);
        }
        verify(service).openMenu(player, "stone");
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void thirdPartyItemDenialStopsTheBinding() {
        MenuService service = listenerService();
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, stack, null, BlockFace.UP, EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.DENY);
        try (var auth = admitListenerPlayer()) {
            new MenuBindingListener(() -> service).onItemInteract(event);
        }
        verify(service, never()).openMenu(any(), anyString());
    }

    @Test
    void thirdPartyBlockDenialStopsTheBinding() {
        MenuService service = listenerService();
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stack, mock(Block.class),
            BlockFace.UP, EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.DENY);
        try (var auth = admitListenerPlayer()) {
            new MenuBindingListener(() -> service).onItemInteract(event);
        }
        verify(service, never()).openMenu(any(), anyString());
    }

    private MockedStatic<AuthAccess> admitListenerPlayer() {
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var auth = mockStatic(AuthAccess.class);
        auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
        return auth;
    }

    private MenuService listenerService() {
        when(stack.getType()).thenReturn(Material.STONE);
        registry.rebuild(List.of(), List.of(binding("stone", "STONE", "", "", "", -1)), logger);
        MenuService service = mock(MenuService.class);
        when(service.bindingRegistry()).thenReturn(registry);
        return service;
    }

    private MenuItemBinding match() {
        return registry.matchItem(player, stack, Action.RIGHT_CLICK_AIR, true);
    }

    private MenuItemBinding binding(String menuId, String material, String name, String regex, String lore, int model) {
        return new MenuItemBinding(menuId, material, name, regex, lore, model,
            Action.RIGHT_CLICK_AIR, null, true, true, "");
    }

    private MenuDefinition menu(String id, List<String> commands, List<String> regex) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("commands", commands);
        yaml.set("command-regex", regex);
        return MenuDefinition.load(yaml, id, MenuLayoutType.PANEL, 2, 12, logger);
    }
}
