package xuanmo.arcartxsuite.prop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge.ModifierOperation;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.bridge.PropSlotUpdates;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.modattr.ModAttributeModifier;
import xuanmo.arcartxsuite.api.modattr.ModAttributeService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.prop.config.PropAmount;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropDefinitionLoader;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;

class PropServiceTest {
    private static final String ATTRIBUTE = "gunsmithlib:bullet_damage";
    private static final String MOD_SOURCE = source("mod", "damage");
    private static final String MOD_EFFECTS = """
        effects:
          attributes:
            mod:
              damage:
                duration: 3
                modifiers:
                  - id: gunsmithlib:bullet_damage
                    amount: 5
        """;
    private static final String TOPIC = "axs.prop.prop_used";
    private static final String EXPRESSION = "5 + global.level * 0.2 + global.bonus * 0.5";
    private static final String FORMULA_EFFECTS = MOD_EFFECTS.replace("amount: 5", """
        amount:
          aria: "%s"
          papi:
            bonus: "%%sample_bonus%%"
        """.formatted(EXPRESSION).stripTrailing().replace("\n", "\n            "));
    private static final Logger LOGGER = Logger.getLogger("PropServiceTest");

    private final Player player = mock(Player.class);
    private final UUID playerId = UUID.randomUUID();
    private final UUID keyConnection = UUID.randomUUID();
    private long keyAdmissionGeneration;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final PropBridgeAPI bridge = mock(PropBridgeAPI.class, withSettings().extraInterfaces(PropSlotUpdates.class));
    private final PropPlayerHandle handle = mock(PropPlayerHandle.class);
    private final ModAttributeService modAttributes = mock(ModAttributeService.class);
    private final AriaBridge aria = mock(AriaBridge.class);
    private final PlaceholderResolverAPI placeholders = mock(PlaceholderResolverAPI.class);
    private final EventBusCapability events = mock(EventBusCapability.class);
    private final AttributePlusBridge attributePlus = mock(AttributePlusBridge.class);
    private final CraneAttributeBridge craneAttribute = mock(CraneAttributeBridge.class);
    private final SymphonyBridge symphony = mock(SymphonyBridge.class);
    private final AttributeBridgeRegistry attributes = mock(AttributeBridgeRegistry.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final ItemStack stack = new ItemStack(Material.PAPER, 2);
    private final List<ScheduledExpiry> expiries = new ArrayList<>();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<AuthAccess> auth;
    private PropService service;
    private Runnable autoTick;
    private Consumer<Player> keyPress;

    @BeforeEach
    void initializeBridges() {
        bukkit = mockStatic(Bukkit.class);
        auth = mockStatic(AuthAccess.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        var factory = mock(ItemFactory.class);
        when(factory.equals(isNull(), isNull())).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        auth.when(() -> AuthAccess.admitted(playerId)).thenReturn(true);
        auth.when(() -> AuthAccess.snapshot(playerId)).thenAnswer(call -> new AuthSnapshot(playerId, keyConnection,
            keyAdmissionGeneration, playerId, AuthSnapshot.Registration.REGISTERED,
            AuthAccess.admitted(playerId) ? AuthSnapshot.Stage.ADMITTED : AuthSnapshot.Stage.RESTRICTED,
            "test", java.util.Set.of(), ""));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Tester");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(stack);
        when(bridge.getPersistentPropId(stack)).thenReturn("boost");
        when(bridge.getCooldownTag(any())).thenReturn("test");
        when(bridge.resolvePlayerHandle(player)).thenReturn(Optional.of(handle));
        when(handle.getSlotItemStack("Slot1")).thenReturn(stack);
        when(handle.trySetSlotItemStack(eq("Slot1"), any())).thenReturn(true);
        when(attributes.attributePlus()).thenReturn(attributePlus);
        when(attributes.craneAttribute()).thenReturn(craneAttribute);
        when(attributes.mythicLib()).thenReturn(mock(MythicLibBridge.class));
        when(attributes.symphony()).thenReturn(symphony);
        when(modAttributes.available()).thenReturn(true);
        when(modAttributes.supportsAttribute(player, ATTRIBUTE)).thenReturn(true);
        when(bridge.registerClientKeyBind(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            keyPress = call.getArgument(3);
            return true;
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            BukkitTask task = mock(BukkitTask.class);
            long delay = call.getArgument(2);
            if (delay > 1) expiries.add(new ScheduledExpiry(call.getArgument(1), task, delay));
            return task;
        });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            autoTick = call.getArgument(1);
            return mock(BukkitTask.class);
        });
    }

    @AfterEach
    void closeService() {
        try {
            if (service != null) service.shutdown();
        } finally {
            auth.close();
            bukkit.close();
        }
    }

    @Test
    void manualUseConsumesAndPublishesAfterMountingTheModifier() {
        start(MOD_EFFECTS, true, false);
        click(EquipmentSlot.HAND);

        var order = inOrder(inventory, handle, modAttributes, events);
        order.verify(inventory).setItemInMainHand(argThat(PropServiceTest::singlePaper));
        order.verify(handle).setTagCooldown("test", 1000L);
        order.verify(modAttributes).setModifiers(player, MOD_SOURCE, Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 5)));
        order.verify(events).publish(TOPIC, player, payload("hand", true));
        assertEquals(60L, expiries.get(0).delay());
        expiries.get(0).run().run();
        verify(modAttributes).clearSource(player, MOD_SOURCE);
    }

    @Test
    void evaluatesTheFormulaWithNumericPlayerBindingsBeforeConsumingTheItem() {
        when(aria.available()).thenReturn(true);
        when(placeholders.available()).thenReturn(true);
        when(player.getLevel()).thenReturn(10);
        when(placeholders.applyPlaceholders(player, "%sample_bonus%")).thenReturn(" 12 ", "18");
        var firstBindings = Map.<String, Object>of("level", 10, "bonus", 12.0);
        var secondBindings = Map.<String, Object>of("level", 10, "bonus", 18.0);
        when(aria.eval(eq(EXPRESSION), withBindings(firstBindings))).thenReturn(13.0);
        when(aria.eval(eq(EXPRESSION), withBindings(secondBindings))).thenReturn(16.0);
        start(FORMULA_EFFECTS, true, false);

        click(EquipmentSlot.HAND);
        click(EquipmentSlot.HAND);

        var order = inOrder(aria, inventory, modAttributes);
        order.verify(aria).eval(eq(EXPRESSION), withBindings(firstBindings));
        order.verify(inventory).setItemInMainHand(argThat(PropServiceTest::singlePaper));
        order.verify(modAttributes).setModifiers(player, MOD_SOURCE, Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 13)));
        order.verify(aria).eval(eq(EXPRESSION), withBindings(secondBindings));
        order.verify(inventory).setItemInMainHand(argThat(PropServiceTest::singlePaper));
        order.verify(modAttributes).setModifiers(player, MOD_SOURCE, Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 16)));
    }

    @Test
    void rejectsNonNumericPlaceholdersWithoutEvaluatingTheirOutputAsCode() {
        when(aria.available()).thenReturn(true);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(player, "%sample_bonus%")).thenReturn("10 + 50");
        start(FORMULA_EFFECTS + "  food: 4\n", true, true);

        click(EquipmentSlot.HAND);
        keyPress.accept(player);
        autoTick.run();

        verify(aria, never()).eval(anyString(), any());
        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).trySetSlotItemStack(anyString(), any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(player, never()).setFoodLevel(anyInt());
        verify(events, never()).publish(anyString(), eq(player), any());
    }

    @Test
    void rejectsANonFiniteAriaResultBeforeApplyingAnyEffect() {
        String formula = "global.level / 0";
        when(aria.available()).thenReturn(true);
        when(aria.eval(eq(formula), withBindings(Map.of("level", 0)))).thenReturn(Double.POSITIVE_INFINITY);
        String effects = MOD_EFFECTS.replace("amount: 5", "amount: {aria: \"" + formula + "\"}");
        start(effects, true, false);
        click(EquipmentSlot.HAND);

        verify(aria).eval(eq(formula), withBindings(Map.of("level", 0)));
        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(modAttributes, never()).setModifiers(eq(player), anyString(), any());
    }

    @Test
    void readsOneSymphonySnapshotForAllFormulasAndCombinesItWithPapi() {
        String reload = "gunsmithlib:reload_speed";
        String physicalDamage = "symphony:physical_damage";
        when(aria.available()).thenReturn(true);
        when(symphony.available()).thenReturn(true);
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(player, "%sample_bonus%")).thenReturn("4");
        when(modAttributes.supportsAttribute(player, reload)).thenReturn(true);
        when(symphony.attributeValues(player)).thenReturn(Map.of(physicalDamage, 25.0));
        when(aria.eval(eq("global.damage * 0.2 + global.bonus"),
            withBindings(Map.of("level", 0, "damage", 25.0, "bonus", 4.0)))).thenReturn(9.0);
        when(aria.eval(eq("global.damage / 100"), withBindings(Map.of("level", 0, "damage", 25.0)))).thenReturn(0.25);
        start("""
            effects:
              attributes:
                mod:
                  damage:
                    duration: 3
                    modifiers:
                      - id: gunsmithlib:bullet_damage
                        amount:
                          aria: "global.damage * 0.2 + global.bonus"
                          symphony:
                            damage: symphony:physical_damage
                          papi:
                            bonus: "%sample_bonus%"
                  reload:
                    duration: 3
                    modifiers:
                      - id: gunsmithlib:reload_speed
                        operation: multiply-total
                        amount:
                          aria: "global.damage / 100"
                          symphony:
                            damage: symphony:physical_damage
            """, true, false);

        click(EquipmentSlot.HAND);

        verify(symphony).attributeValues(player);
        verify(modAttributes).setModifiers(player, MOD_SOURCE,
            Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 9)));
        verify(modAttributes).setModifiers(player, source("mod", "reload"),
            Map.of(reload, ModAttributeModifier.multiplyTotal(reload, 0.25)));
    }

    @Test
    void rejectsMissingSymphonyAttributesAndAcceptsAnExistingZero() {
        String physicalDamage = "symphony:physical_damage";
        when(aria.available()).thenReturn(true);
        when(symphony.available()).thenReturn(true);
        when(symphony.attributeValues(player)).thenReturn(Map.of()).thenReturn(Map.of(physicalDamage, 0.0));
        when(aria.eval(eq("5 + global.damage"), withBindings(Map.of("level", 0, "damage", 0.0)))).thenReturn(5.0);
        String effects = MOD_EFFECTS.replace("amount: 5",
            "amount: {aria: \"5 + global.damage\", symphony: {damage: \"symphony:physical_damage\"}}");
        start(effects, true, false);

        click(EquipmentSlot.HAND);
        verify(aria, never()).eval(anyString(), any());
        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());

        click(EquipmentSlot.HAND);
        verify(inventory).setItemInMainHand(argThat(PropServiceTest::singlePaper));
        verify(modAttributes).setModifiers(player, MOD_SOURCE,
            Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 5)));
    }

    @Test
    void evaluatesSingleLineAmountsWithDirectSymphonyAndPapiLookups() {
        String formula = "global.stats.symphony('physical_damage') * 0.2 + global.stats.papi('%sample_bonus%')";
        when(aria.available()).thenReturn(true);
        when(symphony.available()).thenReturn(true);
        when(symphony.attributeValues(player)).thenReturn(Map.of("symphony:physical_damage", 25.0));
        when(placeholders.available()).thenReturn(true);
        when(placeholders.applyPlaceholders(player, "%sample_bonus%")).thenReturn("4");
        when(aria.eval(eq(formula), any())).thenAnswer(invocation -> {
            Map<String, Object> globals = invocation.getArgument(1);
            var stats = (PropAmount.Inputs) globals.get("stats");
            assertEquals(25.0, stats.symphony("symphony:physical_damage"));
            assertEquals(4.0, stats.papi("%sample_bonus%"));
            return stats.symphony("physical_damage") * 0.2 + stats.papi("%sample_bonus%");
        });
        start(MOD_EFFECTS.replace("amount: 5", "amount: \"" + formula + "\""), true, false);
        click(EquipmentSlot.HAND);

        verify(symphony).attributeValues(player);
        verify(placeholders).applyPlaceholders(player, "%sample_bonus%");
        verify(modAttributes).setModifiers(player, MOD_SOURCE,
            Map.of(ATTRIBUTE, ModAttributeModifier.addition(ATTRIBUTE, 9)));
    }

    @Test
    void rejectsAnUnavailableInlineInputEvenWhenTheScriptReturnsANumber() {
        String formula = "global.stats.symphony('missing')";
        when(aria.available()).thenReturn(true);
        when(symphony.available()).thenReturn(true);
        when(symphony.attributeValues(player)).thenReturn(Map.of());
        when(aria.eval(eq(formula), any())).thenAnswer(invocation -> {
            Map<String, Object> globals = invocation.getArgument(1);
            var stats = (PropAmount.Inputs) globals.get("stats");
            assertEquals(Double.NaN, stats.symphony("missing"));
            return 5.0;
        });
        start(MOD_EFFECTS.replace("amount: 5", "amount: \"" + formula + "\""), true, false);
        click(EquipmentSlot.HAND);

        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(modAttributes, never()).setModifiers(eq(player), anyString(), any());
    }

    @Test
    void rightClickAirRespectsTheItemUseResultIndependentlyOfBlockUse() {
        start("effects: {food: 4}", true, false);
        var denied = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, stack, null, BlockFace.SELF, EquipmentSlot.HAND);
        denied.setUseItemInHand(Event.Result.DENY);
        service.onPlayerInteract(denied);
        verify(inventory, never()).setItemInMainHand(any());

        var allowed = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, stack, null, BlockFace.SELF, EquipmentSlot.HAND);
        assertEquals(Event.Result.DENY, allowed.useInteractedBlock());
        service.onPlayerInteract(allowed);
        verify(inventory).setItemInMainHand(argThat(PropServiceTest::singlePaper));
        assertEquals(Event.Result.DENY, allowed.useItemInHand());
    }

    @Test
    void skipsAllEffectsWhenThePlayerHasNoModAttribute() {
        when(modAttributes.supportsAttribute(player, ATTRIBUTE)).thenReturn(false);
        start(MOD_EFFECTS + "  food: 4\n", true, true);
        click(EquipmentSlot.HAND);
        keyPress.accept(player);
        autoTick.run();

        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).trySetSlotItemStack(anyString(), any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(player, never()).setFoodLevel(anyInt());
        verify(handle).syncSlotToClient("Slot1");
        verify(handle, never()).setSlotItemStack(anyString(), any());
        verify(events, never()).publish(anyString(), eq(player), any());
    }

    @Test
    void keyUseWithAnUnconfirmedSlotWriteDoesNotStartCooldownOrGrantTheEffect() {
        when(handle.trySetSlotItemStack(eq("Slot1"), any())).thenReturn(false);
        start(MOD_EFFECTS, true, false);
        keyPress.accept(player);

        verify(handle).trySetSlotItemStack(eq("Slot1"), argThat(PropServiceTest::singlePaper));
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(modAttributes, never()).setModifiers(eq(player), anyString(), any());
        verify(events, never()).publish(anyString(), eq(player), any());
    }

    @Test
    void autoUseAndQueuedKeyRespectAdmissionAndShutdown() {
        start(MOD_EFFECTS, true, true);
        auth.when(() -> AuthAccess.admitted(playerId)).thenReturn(false);
        autoTick.run();
        keyPress.accept(player);
        click(EquipmentSlot.HAND);
        verify(handle, never()).setTagCooldown(anyString(), anyLong());

        auth.when(() -> AuthAccess.admitted(playerId)).thenReturn(true);
        autoTick.run();
        verify(handle).trySetSlotItemStack(eq("Slot1"), argThat(PropServiceTest::singlePaper));
        verify(events).publish(TOPIC, player, payload("auto", true));
        service.shutdown();
        keyPress.accept(player);
        autoTick.run();
        verify(handle, times(1)).setTagCooldown("test", 1000L);
        verify(modAttributes).clearSource(player, MOD_SOURCE);
    }

    @Test
    void repeatedPressesUseOneTaskAndAnOlderAdmissionCannotConsumeTheSlot() {
        start(MOD_EFFECTS, true, false);
        var tasks = new java.util.ArrayDeque<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        for (int i = 0; i < 100; i++) keyPress.accept(player);
        assertEquals(1, tasks.size());
        keyAdmissionGeneration++;
        tasks.remove().run();
        verify(handle, never()).trySetSlotItemStack(anyString(), any());

        keyPress.accept(player);
        tasks.remove().run();
        assertTrue(tasks.isEmpty());
        verify(handle).trySetSlotItemStack(eq("Slot1"), argThat(PropServiceTest::singlePaper));
        assertEquals(2, stack.getAmount());
        verify(events).publish(TOPIC, player, payload("key", true));
    }

    @Test
    void refreshUsesOneTimerAndQuitClearsOnlyThePropsSources() {
        when(attributePlus.available()).thenReturn(true);
        when(craneAttribute.available()).thenReturn(true);
        when(craneAttribute.supportsStaticSource()).thenReturn(true);
        Object apData = new Object();
        Object craneData = new Object();
        List<String> lines = List.of("勇气:4");
        when(attributePlus.getAttrData(player)).thenReturn(apData);
        when(attributePlus.getAttributeSource(lines)).thenReturn(new Object());
        when(craneAttribute.getAttrData(player)).thenReturn(craneData);
        start(MOD_EFFECTS + """
            attributeplus:
              focus:
                duration: 3
                lines: ["勇气:4"]
            craneattribute:
              focus:
                duration: 3
                lines: ["勇气:4"]
        """, false, false);

        click(EquipmentSlot.OFF_HAND);
        click(EquipmentSlot.HAND);
        click(EquipmentSlot.HAND);
        assertEquals(2, expiries.size());
        verify(expiries.get(0).task()).cancel();
        expiries.get(0).run().run();
        verify(craneAttribute, times(2)).addStaticSource(craneData, source("craneattribute", "focus"), lines);
        verify(modAttributes).clearSource(player, MOD_SOURCE);

        service.onPlayerQuit(new PlayerQuitEvent(player, ""));
        verify(expiries.get(1).task()).cancel();
        verify(attributePlus, times(2)).removeSource(apData, source("attributeplus", "focus"));
        verify(craneAttribute, times(2)).removeSource(craneData, source("craneattribute", "focus"));
        verify(modAttributes, times(2)).clearSource(player, MOD_SOURCE);
        verify(modAttributes, never()).clearPlayer(player);
    }

    @Test
    void rejectsMalformedEffectsAndConfiguredConditions() {
        assertThrows(IllegalArgumentException.class, () -> load("effects: [\"food|4\"]"));
        assertThrows(IllegalArgumentException.class, () -> load(MOD_EFFECTS.replace("amount: 5", "amount: .nan")));
        assertThrows(IllegalArgumentException.class, () -> load(MOD_EFFECTS.replace("amount: 5", "amount: 5\n            operation: percent")));
        assertThrows(IllegalArgumentException.class, () -> load(MOD_EFFECTS + "conditions: [\"%player_level% >= 10\", \"bad syntax\"]"));
        assertFalse(load(MOD_EFFECTS + "auto: {enabled: true, conditions: [bad syntax]}").auto().enabled());
    }

    @Test
    void symphonyAcceptsCustomNamespacedKeysAndExplicitOperations() {
        when(symphony.available()).thenReturn(true);
        start("""
            effects:
              attributes:
                symphony:
                  mobility:
                    duration: 8
                    modifiers:
                      - id: server:custom_movement
                        amount: 0.1
                        operation: multiply-base
            """, false, false);
        click(EquipmentSlot.HAND);
        String key = source("symphony", "mobility") + "_0";
        verify(symphony).setAttribute(player, "server:custom_movement", ModifierOperation.MULTIPLY_BASE, 0.1, key);
        assertEquals(160L, expiries.get(0).delay());
        expiries.get(0).run().run();
        verify(symphony).removeAttribute(player, key);
    }

    @Test
    void bundledExampleLoadsAndUsesTheConfiguredPotionOptions() throws Exception {
        var type = mock(PotionEffectType.class);
        try (var registry = mockStatic(PotionEffectType.class);
             var resource = getClass().getResourceAsStream("/prop/props/道具示例.yml");
             var reader = new InputStreamReader(java.util.Objects.requireNonNull(resource), StandardCharsets.UTF_8)) {
            registry.when(() -> PotionEffectType.getByKey(NamespacedKey.minecraft("speed"))).thenReturn(type);
            var data = new YamlConfiguration();
            data.load(reader);
            var effects = PropDefinitionLoader.loadDefinition("example", data, LOGGER).effects();
            effects.instant().forEach(effect -> effect.accept(player));
            var captured = ArgumentCaptor.forClass(PotionEffect.class);
            verify(player).addPotionEffect(captured.capture());
            assertEquals(type, captured.getValue().getType());
            assertEquals(1, captured.getValue().getAmplifier());
            assertEquals(200, captured.getValue().getDuration());
            assertFalse(captured.getValue().hasParticles());
        }
    }

    @Test
    void sameAttributeCanHaveSeparateOperationsAndExpiryTimes() {
        start(MOD_EFFECTS + """
              multiplier:
                duration: 8
                modifiers:
                  - id: gunsmithlib:bullet_damage
                    amount: 0.2
                    operation: multiply-total
        """, false, false);
        click(EquipmentSlot.HAND);

        String multiplier = source("mod", "multiplier");
        verify(modAttributes).setModifiers(player, multiplier,
            Map.of(ATTRIBUTE, ModAttributeModifier.multiplyTotal(ATTRIBUTE, 0.2)));
        assertEquals(List.of(60L, 160L), expiries.stream().map(ScheduledExpiry::delay).toList());
        expiries.get(0).run().run();
        verify(modAttributes).clearSource(player, MOD_SOURCE);
        verify(modAttributes, never()).clearSource(player, multiplier);
        service.onPlayerQuit(new PlayerQuitEvent(player, ""));
        verify(modAttributes).clearSource(player, multiplier);
    }

    private void start(String effects, boolean consume, boolean auto) {
        start(effects, consume, auto, new PropKeyMappingConfiguration("props",
            Map.of("item", new PropKeyBindingDefinition("item", "X", "Slot1"))));
    }

    @Test
    void slotEventsReadOnlyChangedSlotsAndNeverUseAStaleCandidate() {
        var receiver = new AtomicReference<BiConsumer<Player, String>>();
        var unsubscribe = mock(Runnable.class);
        when(((PropSlotUpdates) bridge).observeSlots(any())).thenAnswer(call -> {
            receiver.set(call.getArgument(0));
            return unsubscribe;
        });
        when(handle.getSlotItemStack("Slot1")).thenReturn(null);
        var bindings = new PropKeyMappingConfiguration("props", Map.of(
            "one", new PropKeyBindingDefinition("one", "X", "Slot1"),
            "two", new PropKeyBindingDefinition("two", "C", "Slot2"),
            "three", new PropKeyBindingDefinition("three", "V", "Slot3")));
        start(MOD_EFFECTS, false, true, bindings);

        autoTick.run();
        autoTick.run();
        autoTick.run();
        auth.verify(() -> AuthAccess.admitted(playerId), times(1));
        verify(bridge, times(1)).resolvePlayerHandle(player);
        verify(handle, times(1)).getSlotItemStack("Slot1");
        verify(handle, times(1)).getSlotItemStack("Slot2");
        verify(handle, times(1)).getSlotItemStack("Slot3");

        when(handle.getSlotItemStack("Slot2")).thenReturn(stack);
        receiver.get().accept(player, "Slot2");
        autoTick.run();
        verify(handle, times(1)).getSlotItemStack("Slot1");
        verify(handle, times(1)).getSlotItemStack("Slot3");
        verify(events).publish(TOPIC, player, payload("auto", false));

        // A slot writer omitted the event. The cached definition cannot authorize using its replacement.
        when(handle.getSlotItemStack("Slot2")).thenReturn(new ItemStack(Material.STONE));
        autoTick.run();
        verify(events, times(1)).publish(eq(TOPIC), eq(player), any());
        when(handle.getSlotItemStack("Slot3")).thenReturn(stack);
        for (int i = 0; i < 11; i++) autoTick.run();
        verify(events, atLeast(2)).publish(TOPIC, player, payload("auto", false));

        // A new connection gets a fresh scan; a late slot signal from the old connection is ignored.
        var reconnect = mock(Player.class);
        var newHandle = mock(PropPlayerHandle.class);
        when(reconnect.getUniqueId()).thenReturn(playerId);
        when(reconnect.isOnline()).thenReturn(true);
        when(bridge.resolvePlayerHandle(reconnect)).thenReturn(Optional.of(newHandle));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(reconnect));
        autoTick.run();
        receiver.get().accept(player, "Slot2");
        autoTick.run();
        verify(newHandle).getSlotItemStack("Slot1");
        verify(newHandle).getSlotItemStack("Slot2");
        verify(newHandle).getSlotItemStack("Slot3");
        service.shutdown();
        verify(unsubscribe).run();
    }

    @Test
    void changedHandItemDuringPreparationCannotBeConsumedOrTriggerEffects() {
        when(inventory.getItemInMainHand()).thenReturn(stack, new ItemStack(Material.STONE));
        start(MOD_EFFECTS, true, false);
        click(EquipmentSlot.HAND);
        verify(inventory, never()).setItemInMainHand(any());
        verify(handle, never()).setTagCooldown(anyString(), anyLong());
        verify(events, never()).publish(anyString(), any(), any());
    }

    private void start(String effects, boolean consume, boolean auto, PropKeyMappingConfiguration bindings) {
        var yaml = yaml(effects);
        yaml.set("coolDownGroup", "test");
        yaml.set("coolDownTime", 1);
        yaml.set("remove", consume);
        yaml.set("auto.enabled", auto);
        yaml.set("auto.notify", false);
        yaml.set("auto-trigger.enabled", auto);
        var prop = PropDefinitionLoader.loadDefinition("boost", yaml, LOGGER);
        var names = mock(VanillaItemNameBridge.class);
        when(names.resolveDisplayName(any())).thenReturn("boost");
        var messages = mock(MessageProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenAnswer(call -> call.getArgument(0));
        service = new PropService(plugin, LOGGER, PropModuleConfiguration.load(yaml), bridge, bindings,
            PropLanguageConfiguration.load(yaml), Map.of(prop.id(), prop), attributes, modAttributes, aria, placeholders,
            messages, names);
        service.setEventBusProvider(() -> events);
        service.start();
    }

    private void click(EquipmentSlot slot) {
        var event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(slot);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        service.onPlayerInteract(event);
    }

    private Map<String, String> payload(String trigger, boolean consumed) {
        return Map.of("prop_id", "boost", "trigger", trigger, "consumed", Boolean.toString(consumed),
            "cooldown_group", "test", "max_duration_seconds", "3");
    }

    private static String source(String provider, String group) {
        String identity = "boost\0" + provider + "\0" + group;
        return "AXS_PROP_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static PropDefinition load(String text) {
        return PropDefinitionLoader.loadDefinition("boost", yaml(text), LOGGER);
    }

    private static YamlConfiguration yaml(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
            return yaml;
        } catch (InvalidConfigurationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean singlePaper(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.getAmount() == 1;
    }

    private static Map<String, Object> withBindings(Map<String, Object> expected) {
        return argThat(bindings -> bindings != null && bindings.entrySet().containsAll(expected.entrySet()));
    }

    private record ScheduledExpiry(Runnable run, BukkitTask task, long delay) {}
}
