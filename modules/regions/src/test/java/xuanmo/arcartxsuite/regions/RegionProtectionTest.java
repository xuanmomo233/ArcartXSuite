package xuanmo.arcartxsuite.regions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.RegionProtectionQueryable.Reason;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.util.ActiveFileReplacement;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.listener.RegionTransitions;
import xuanmo.arcartxsuite.regions.listener.RegionProtectionListener;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.RegionFlag.State;
import xuanmo.arcartxsuite.regions.packet.RegionsMenuPacketHandler;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.SelectionParticleTask;
import xuanmo.arcartxsuite.regions.storage.RegionSnapshots;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;

class RegionProtectionTest {

    @TempDir File directory;
    private final Logger logger = Logger.getLogger(getClass().getName());
    private java.nio.file.Path dataFile;
    private RegionsRepository repository;
    private RegionManager manager;
    private RegionsConfiguration config;
    private CommandSender admin;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        dataFile = directory.toPath().resolve("regions.yml");
        repository = new RegionsRepository(dataFile);
        repository.loadAllRegions();
        config = RegionsConfiguration.load(new YamlConfiguration(), new YamlConfiguration(), logger);
        manager = new RegionManager(new RegionsRepository(dataFile), config, logger, Runnable::run, Runnable::run);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        admin = mock(CommandSender.class);
        when(admin.getName()).thenReturn("Console");
        when(admin.hasPermission("axs.regions.admin")).thenReturn(true);
    }

    @Test
    void managedProtectionAndEqualPriorityDenialsSurviveClaimAllowAndReload() throws Exception {
        Region claim = region("claim", Region.Kind.CLAIM, 0, 10, RegionFlag.BLOCK_BREAK, State.ALLOW);
        claim.addOwner(UUID.randomUUID());
        claim.setPriority(Integer.MAX_VALUE);
        Region denied = region("z-deny", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.DENY);
        Region allowed = region("a-allow", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.ALLOW);
        seed(claim, denied, allowed);
        assertEquals(State.DENY, manager.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_BREAK));
        manager.update(admin, manager.getRegion("z-deny", "world"), "message",
            edit -> edit.setFlag(RegionFlag.GREETING, State.ALLOW));
        assertEquals(State.DENY, manager.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_BREAK));
        RegionManager reloaded = new RegionManager(new RegionsRepository(dataFile), config, logger, Runnable::run, Runnable::run);
        reloaded.loadAll();
        assertEquals(State.DENY, reloaded.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void worldFlightDeniedUsesOverrideAndHonorsExplicitLocalAllow() throws Exception {
        var global = region("__global__", Region.Kind.GLOBAL, 0, 0, RegionFlag.FLY, State.DENY);
        var arena = region("arena", Region.Kind.MANAGED, 30, 40, RegionFlag.FLY, State.ALLOW);
        seed(global, arena);
        var yaml = new YamlConfiguration();
        yaml.set("notifications.flight-denied.default", "默认禁飞");
        yaml.set("notifications.flight-denied.worlds.world", "&c{world} 世界禁止飞行");
        yaml.set("notifications.flight-denied.worlds.event.final", "活动禁飞");
        var config = RegionsConfiguration.load(yaml, new YamlConfiguration(), logger);
        assertEquals("默认禁飞", config.notifications().flightDenied("other"));
        assertEquals("活动禁飞", config.notifications().flightDenied("event.final"));
        var listener = new RegionProtectionListener(manager, config);
        Player player = player();
        when(player.getLocation()).thenReturn(new Location(world, 10, 1, 0));
        var denied = new PlayerToggleFlightEvent(player, true);
        listener.onToggleFlight(denied);
        assertTrue(denied.isCancelled());
        verify(player).sendMessage(config.messages().format("&cworld 世界禁止飞行"));
        verify(player).setFlying(false);

        when(player.getLocation()).thenReturn(new Location(world, 35, 1, 0));
        var allowed = new PlayerToggleFlightEvent(player, true);
        listener.onToggleFlight(allowed);
        assertFalse(allowed.isCancelled());
        verify(player, times(1)).setFlying(false);

        yaml.set("notifications.flight-denied.worlds.world", "");
        var muted = RegionsConfiguration.load(yaml, new YamlConfiguration(), logger);
        when(player.getLocation()).thenReturn(new Location(world, 10, 1, 0));
        when(player.isFlying()).thenReturn(true);
        new RegionProtectionListener(manager, muted).enforceFlight(player);
        verify(player, times(2)).setFlying(false);
        verify(player, times(1)).sendMessage(anyString());
    }

    @Test
    void claimsCannotOverlapOrIncreasePriorityAndWorldDefaultIsFallback() throws Exception {
        seed(region("spawn", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.DENY));
        Player player = player();
        select(player, 1, 2);
        assertThrows(CompletionException.class, () -> manager.createRegion(player, "overlap", edit -> {}).join());
        select(player, 20, 24);
        Region claim = manager.createRegion(player, "home", edit -> {}).join();
        assertEquals(Region.Kind.CLAIM, claim.kind());
        assertThrows(CompletionException.class,
            () -> manager.update(player, claim, "priority", edit -> edit.setPriority(100)).join());
        Region global = manager.createGlobal(admin, "world").join();
        manager.setFlag(admin, global, RegionFlag.PVP, State.DENY, null);
        assertEquals(State.DENY, manager.queryFlag("world", 500, -10, 2, RegionFlag.PVP));
        assertEquals(State.NONE, manager.queryFlag("other", 500, -10, 2, RegionFlag.PVP));
        assertTrue(manager.getRegionsAt("world", 500, -10, 2).isEmpty());
    }

    @Test
    void parentCyclesAreRejected() throws Exception {
        Region parent = region("parent", Region.Kind.MANAGED, 20, 30, RegionFlag.PVP, State.DENY);
        Region child = region("child", Region.Kind.MANAGED, 40, 50, RegionFlag.USE, State.DENY);
        child.setParentId("parent");
        seed(parent, child);
        assertEquals(State.NONE, manager.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_PLACE));
        assertEquals(Reason.INHERITED, manager.decide("world", 41, 1, 1, RegionFlag.PVP, null).reason());
        assertThrows(CompletionException.class, () -> manager.setParent(admin, manager.getRegion("parent", "world"), "child").join());
    }

    @Test
    void renameAndUndoKeepChildReferencesAndStableId() throws Exception {
        Region parent = region("parent", Region.Kind.MANAGED, 20, 30, RegionFlag.PVP, State.DENY);
        Region child = region("child", Region.Kind.MANAGED, 40, 50, RegionFlag.USE, State.DENY);
        child.setParentId("parent");
        seed(parent, child);
        Region renamed = manager.renameRegion(admin, manager.getRegion("parent", "world"), "renamed").join();
        assertEquals(parent.uid(), renamed.uid());
        assertEquals("renamed", manager.getRegion("child", "world").parentId());
        long rename = manager.history(admin, 1).get(0).id();
        manager.undo(admin, rename);
        assertNull(manager.getRegion("renamed", "world"));
        assertEquals("parent", manager.getRegion("child", "world").parentId());
        assertEquals(parent.uid(), manager.getRegion("parent", "world").uid());
        assertThrows(CompletionException.class, () -> manager.undo(admin, rename).join());
    }

    @Test
    void externallyEditedFileIsNotOverwrittenAndProtectionRemainsCommitted() throws Exception {
        seed(region("spawn", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.DENY));
        String externallyEdited = Files.readString(dataFile) + "\n# 启用其他规则前先重载\n";
        Files.writeString(dataFile, externallyEdited);
        var failure = assertThrows(CompletionException.class, () -> manager.setFlag(admin, manager.getRegion("spawn", "world"),
            RegionFlag.BLOCK_BREAK, State.ALLOW, null).join());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(State.DENY, manager.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_BREAK));
        assertEquals(externallyEdited, Files.readString(dataFile));
        assertEquals(1, manager.history(admin, 10).size());
    }

    @Test
    void backgroundWritesPublishInOrderAndRebaseQueuedEdits() throws Exception {
        seed(region("spawn", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.DENY));
        var saves = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        var queued = new RegionManager(new RegionsRepository(dataFile), config, logger, callbacks::add, saves::add);
        queued.loadAll();
        Region spawn = queued.getRegion("spawn", "world");
        var first = queued.setFlag(admin, spawn, RegionFlag.BLOCK_BREAK, State.ALLOW, null);
        var second = queued.setFlag(admin, spawn, RegionFlag.PVP, State.DENY, null);
        assertEquals(1, saves.size());
        saves.remove().run();
        assertEquals(State.DENY, queued.queryFlag("world", 1, 1, 1, RegionFlag.BLOCK_BREAK));
        assertFalse(first.isDone());
        assertTrue(saves.isEmpty());
        callbacks.remove().run();
        assertEquals(State.ALLOW, first.join().getFlag(RegionFlag.BLOCK_BREAK));
        assertEquals(State.NONE, queued.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
        saves.remove().run();
        callbacks.remove().run();
        assertEquals(State.ALLOW, second.join().getFlag(RegionFlag.BLOCK_BREAK));
        assertEquals(State.DENY, queued.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
        Region persisted = new RegionsRepository(dataFile).loadAllRegions().get(0);
        assertEquals(State.ALLOW, persisted.getFlag(RegionFlag.BLOCK_BREAK));
        assertEquals(State.DENY, persisted.getFlag(RegionFlag.PVP));
    }

    @Test
    void worldHasSamePrecedenceAndBypassAtActualInteractionLocation() throws Exception {
        Region spawn = region("spawn", Region.Kind.MANAGED, 0, 10, RegionFlag.USE, State.ALLOW);
        spawn.setFlag(RegionFlag.FLY, State.ALLOW);
        spawn.setFlag(RegionFlag.TELEPORT, State.ALLOW);
        seed(spawn);
        manager.editWorld(admin, "world", global -> {
            global.setFlag(RegionFlag.FLY, State.DENY);
            global.setFlag(RegionFlag.ENDERPEARL, State.DENY);
            global.setBlockedInteraction(org.bukkit.Material.ANVIL, true);
        }).join();
        Player player = player();
        assertEquals(State.ALLOW, manager.rulesAt(at(1)).decide(RegionFlag.FLY, player).state());
        assertEquals(State.DENY, manager.rulesAt(at(20)).decide(RegionFlag.FLY, player).state());
        assertEquals(State.ALLOW, manager.rulesAt(at(1)).travel(RegionFlag.ENDERPEARL, player).state());
        assertEquals(State.DENY, manager.rulesAt(at(20)).travel(RegionFlag.ENDERPEARL, player).state());
        assertEquals(State.ALLOW, manager.rulesAt(at(1)).interaction(org.bukkit.Material.ANVIL, player).state());
        assertEquals(State.DENY, manager.rulesAt(at(20)).interaction(org.bukkit.Material.ANVIL, player).state());
        when(player.hasPermission("axs.regions.bypass")).thenReturn(true);
        assertEquals(Reason.BYPASS, manager.rulesAt(at(20)).interaction(org.bukkit.Material.ANVIL, player).reason());
        assertEquals(Reason.BYPASS, manager.rulesAt(at(20)).decide(RegionFlag.FLY, player).reason());
    }

    @Test
    void redefineReplacesIndexedBoundsAndUndoRejectsLaterChanges() throws Exception {
        seed();
        Player player = player();
        select(player, 0, 10);
        Region claim = manager.createRegion(player, "home", edit -> {}).join();
        select(player, 40, 50);
        manager.redefine(player, claim);
        assertTrue(manager.getRegionsAt("world", 1, 1, 1).isEmpty());
        assertEquals(claim.uid(), manager.getRegionsAt("world", 41, 1, 1).get(0).uid());
        long redefine = manager.history(admin, 1).get(0).id();
        manager.setFlag(player, manager.getRegion("home", "world"), RegionFlag.BLOCK_BREAK, State.DENY, null);
        assertThrows(CompletionException.class, () -> manager.undo(admin, redefine).join());
    }

    @Test
    void snapshotsRoundTripAndImportDetectsNameAndCycleConflicts() throws Exception {
        seed(region("spawn", Region.Kind.MANAGED, 0, 10, RegionFlag.BLOCK_BREAK, State.DENY));
        Region exported = manager.getRegion("spawn", "world").copy("spawn");
        exported.setFlag(RegionFlag.GREETING, State.ALLOW);
        exported.setFlagData(RegionFlag.GREETING, "&a欢迎来到出生点");
        Region decoded = RegionSnapshots.read(RegionSnapshots.write(List.of(exported))).get(0);
        assertTrue(RegionSnapshots.same(exported, decoded));
        assertFalse(manager.reviewImport(List.of(decoded)).errors().isEmpty());
        Region cycle = region("cycle", Region.Kind.MANAGED, 40, 50, RegionFlag.USE, State.DENY);
        cycle.setParentId("cycle");
        assertFalse(manager.reviewImport(List.of(cycle)).errors().isEmpty());
        Region newRegion = region("imported", Region.Kind.MANAGED, 50, 60, RegionFlag.USE, State.DENY);
        assertEquals(1, manager.importRegions(admin, List.of(newRegion)).join());
        assertEquals(1, manager.reviewImport(List.of(newRegion)).unchanged());
    }

    @Test
    void walkingTeleportAndPortalShareEntryAndExitGuards() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.ENTRY, State.DENY);
        zone.setFlag(RegionFlag.EXIT, State.DENY);
        seed(zone);
        Player player = player();
        RegionTransitions listener = new RegionTransitions(null, manager, config, () -> null);
        PlayerMoveEvent walk = new PlayerMoveEvent(player, at(20), at(1));
        PlayerTeleportEvent teleport = new PlayerTeleportEvent(player, at(20), at(1), PlayerTeleportEvent.TeleportCause.PLUGIN);
        PlayerPortalEvent portal = new PlayerPortalEvent(player, at(1), at(20), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        listener.guardWalk(walk);
        listener.guardTeleport(teleport);
        listener.guardPortal(portal);
        assertTrue(walk.isCancelled());
        assertTrue(teleport.isCancelled());
        assertTrue(portal.isCancelled());
        PlayerMoveEvent within = new PlayerMoveEvent(player, at(1), at(2));
        listener.guardWalk(within);
        assertFalse(within.isCancelled());
    }

    @Test
    void travelAndBoundaryFlagsShareOneLookupPerEndpoint() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.ENTRY, State.DENY);
        zone.setFlag(RegionFlag.EXIT, State.DENY);
        zone.setFlag(RegionFlag.ENDERPEARL, State.ALLOW);
        seed(zone);
        var listener = new RegionTransitions(null, manager, config, () -> null);
        var event = new PlayerTeleportEvent(player(), at(1), at(2), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);

        listener.guardTeleport(event);

        assertFalse(event.isCancelled());
        verify(world, times(2)).getName();
    }

    @Test
    void sameBlockTeleportStillChecksPearlAndChorusFlags() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.ENDERPEARL, State.DENY);
        zone.setFlag(RegionFlag.CHORUS_FRUIT, State.DENY);
        seed(zone);
        var listener = new RegionTransitions(null, manager, config, () -> null);
        Player player = player();
        Location destination = at(1).add(0.25, 0, 0);
        var pearl = new PlayerTeleportEvent(player, at(1), destination, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        var chorus = new PlayerTeleportEvent(player, at(1), destination, PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);

        listener.guardTeleport(pearl);
        listener.guardTeleport(chorus);

        assertTrue(pearl.isCancelled());
        assertTrue(chorus.isCancelled());
        verify(world, times(2)).getName();
    }

    @Test
    void boundaryDenialUsesMatchedChildEvenWhenChildrenInheritTheSameRule() throws Exception {
        Region parent = region("parent", Region.Kind.MANAGED, 20, 30, RegionFlag.ENTRY, State.DENY);
        Region first = region("first", Region.Kind.MANAGED, 0, 3, RegionFlag.ENTRY, State.NONE);
        Region second = region("second", Region.Kind.MANAGED, 5, 8, RegionFlag.ENTRY, State.NONE);
        first.setParentId(parent.id());
        second.setParentId(parent.id());
        seed(parent, first, second);
        var listener = new RegionTransitions(null, manager, config, () -> null);
        Player player = player();
        var within = new PlayerMoveEvent(player, at(1), at(2));
        var betweenChildren = new PlayerMoveEvent(player, at(1), at(6));

        listener.guardWalk(within);
        listener.guardWalk(betweenChildren);

        assertFalse(within.isCancelled());
        assertTrue(betweenChildren.isCancelled());
    }

    @Test
    void blockEventsCheckTheAffectedSideOfTheBoundary() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PISTONS, State.DENY);
        zone.setFlag(RegionFlag.BLOCK_PLACE, State.DENY);
        zone.setFlag(RegionFlag.OTHER_EXPLOSION, State.DENY);
        seed(zone);
        RegionProtectionListener listener = new RegionProtectionListener(manager, config);
        Block protectedBlock = block(10);
        Block clicked = block(11);
        Block piston = block(12);
        when(clicked.getRelative(BlockFace.WEST)).thenReturn(protectedBlock);
        when(piston.getRelative(BlockFace.WEST)).thenReturn(clicked);
        var extend = new BlockPistonExtendEvent(piston, List.of(clicked), BlockFace.WEST);
        listener.onPistonExtend(extend);
        assertTrue(extend.isCancelled());

        // Slime can pull a block into a protected coordinate even when the piston is outside.
        Block retractPiston = block(20);
        Block retractHead = block(21);
        Block slimeAttached = block(11);
        when(retractPiston.getRelative(BlockFace.EAST)).thenReturn(retractHead);
        when(slimeAttached.getRelative(BlockFace.WEST)).thenReturn(protectedBlock);
        var retract = new BlockPistonRetractEvent(retractPiston, List.of(slimeAttached), BlockFace.EAST);
        listener.onPistonRetract(retract);
        assertTrue(retract.isCancelled());

        var bucket = new PlayerBucketEmptyEvent(player(), protectedBlock, clicked, BlockFace.WEST,
            Material.WATER_BUCKET, null);
        listener.onBucketEmpty(bucket);
        assertTrue(bucket.isCancelled());
        var explosion = new BlockExplodeEvent(clicked, new java.util.ArrayList<>(List.of(protectedBlock, clicked)), 1);
        listener.onBlockExplosion(explosion);
        assertEquals(List.of(clicked), explosion.blockList());
    }

    @Test
    void transitionEventsRequireThePlayerToActuallyCrossTheBoundary() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY);
        seed(zone);
        Player player = player();
        when(player.isOnline()).thenReturn(true);
        when(player.isFlying()).thenReturn(true);
        when(player.hasPermission("axs.regions.bypass")).thenReturn(true);
        when(player.getLocation()).thenReturn(at(20));
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        var tasks = new java.util.ArrayDeque<Runnable>();
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        var bus = mock(EventBusCapability.class);
        var listener = new RegionTransitions(plugin, manager, config, () -> bus);
        var retargeted = new PlayerTeleportEvent(player, at(20), at(21), PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.guardTeleport(retargeted);
        assertFalse(retargeted.isCancelled());
        retargeted.setTo(at(1));
        manager.setFlag(admin, manager.getRegion("zone", "world"), RegionFlag.FLY, State.DENY, null);
        when(player.hasPermission("axs.regions.bypass")).thenReturn(false);
        clearInvocations(world);
        listener.reportTeleport(retargeted);
        verify(player, never()).setFlying(false);
        verify(world, times(2)).getName();
        verifyNoInteractions(bus);

        // Changes to the event's mutable Location cannot move the saved origin.
        retargeted.getFrom().setX(1);
        tasks.remove().run();
        verifyNoInteractions(bus);

        listener.reportTeleport(new PlayerTeleportEvent(player, at(20), at(1), PlayerTeleportEvent.TeleportCause.PLUGIN));
        Region renamed = manager.renameRegion(admin, manager.getRegion("zone", "world"), "renamed").join();
        when(player.getLocation()).thenReturn(at(1));
        tasks.remove().run();
        verify(player).setFlying(false);
        verify(bus).publish("axs.regions.region_change", player, java.util.Map.of(
            "type", "enter", "region_id", "renamed", "world", "world", "region_key", "world:renamed",
            "region_uuid", renamed.uid().toString(), "cause", "plugin"));
    }

    @Test
    void manualAndNativePickupEventChecksBothSidesOfTheBoundary() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.ITEM_PICKUP, State.DENY);
        seed(zone);
        var listener = new RegionProtectionListener(manager, config);
        Player player = player();
        Item item = mock(Item.class);
        when(player.getLocation()).thenReturn(at(11));
        when(item.getLocation()).thenReturn(at(10));
        var manual = new EntityPickupItemEvent(player, item, 0);
        listener.onItemPickup(manual);
        assertTrue(manual.isCancelled());

        when(player.getLocation()).thenReturn(at(10));
        when(item.getLocation()).thenReturn(at(11));
        var nativePickup = new EntityPickupItemEvent(player, item, 0);
        listener.onItemPickup(nativePickup);
        assertTrue(nativePickup.isCancelled());

        when(player.hasPermission("axs.regions.bypass")).thenReturn(true);
        var bypass = new EntityPickupItemEvent(player, item, 0);
        listener.onItemPickup(bypass);
        assertFalse(bypass.isCancelled());
    }

    @Test
    void revokingAdminWhileAnExclusiveClaimRuleWaitsPreventsTheWrite() throws Exception {
        seed();
        Player owner = player();
        select(owner, 0, 10);
        Region claim = manager.createRegion(owner, "claim", ignored -> {}).join();
        var saves = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        var queued = new RegionManager(new RegionsRepository(dataFile), config, logger, callbacks::add, saves::add);
        queued.loadAll();
        when(owner.hasPermission("axs.regions.admin")).thenReturn(true);
        var first = queued.setFlag(owner, claim, RegionFlag.BLOCK_BREAK, State.DENY, null);
        var exclusive = queued.setFlag(owner, claim, RegionFlag.PVP, State.DENY, null);
        when(owner.hasPermission("axs.regions.admin")).thenReturn(false);
        saves.remove().run();
        callbacks.remove().run();
        assertEquals(State.DENY, first.join().getFlag(RegionFlag.BLOCK_BREAK));
        assertThrows(CompletionException.class, exclusive::join);
        assertEquals(State.NONE, queued.getRegion("claim", "world").getFlag(RegionFlag.PVP));
        assertTrue(saves.isEmpty());
    }

    @Test
    void flagPublicationUpdatesInheritedDecisionsWithoutRebuildingMembershipOrRecheckingFlight() throws Exception {
        Region parent = region("parent", Region.Kind.MANAGED, 20, 30, RegionFlag.PVP, State.DENY);
        Region child = region("child", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.NONE);
        child.setParentId("parent");
        seed(parent, child);
        var membership = manager.membershipIndex();
        var flightUpdates = new ArrayDeque<java.util.Set<String>>();
        manager.onPublication(flightUpdates::add);
        manager.setFlag(admin, manager.getRegion("parent", "world"), RegionFlag.PVP, State.ALLOW, null).join();
        assertSame(membership, manager.membershipIndex());
        assertEquals(State.ALLOW, manager.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
        assertTrue(flightUpdates.isEmpty());
        manager.setFlag(admin, manager.getRegion("parent", "world"), RegionFlag.FLY, State.DENY, null).join();
        assertEquals(java.util.Set.of("world"), flightUpdates.remove());
        assertEquals(State.DENY, manager.queryFlag("world", 1, 1, 1, RegionFlag.FLY));
    }

    @Test
    void explosionCellCacheStillChecksTheIndividualBlockBoundaries() throws Exception {
        seed(region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.OTHER_EXPLOSION, State.DENY));
        var rules = manager.blockRules(RegionFlag.OTHER_EXPLOSION);
        clearInvocations(world);
        assertTrue(rules.denied(block(4)));
        assertTrue(rules.denied(block(10)));
        assertFalse(rules.denied(block(11)));
        verify(world, times(1)).getName();
    }

    @Test
    void membershipViewUsesIndexedGroupsAndRevokesCachedRowsWhenPermissionChanges() throws Exception {
        Player player = player();
        Region direct = region("direct", Region.Kind.MANAGED, 0, 3, RegionFlag.FLY, State.NONE);
        direct.addMember(player.getUniqueId());
        Region grouped = region("grouped", Region.Kind.MANAGED, 5, 8, RegionFlag.FLY, State.NONE);
        grouped.addOwnerGroup("staff");
        seed(direct, grouped);
        when(player.hasPermission("axs.regions.group.staff")).thenReturn(true);
        var index = manager.membershipIndex();
        var view = index.view(player, null);
        assertEquals(List.of("direct", "grouped"), view.memberships().stream().map(entry -> entry.region().id()).toList());
        assertTrue(view.memberships().get(1).owner());
        assertSame(view, index.view(player, view));
        when(player.hasPermission("axs.regions.group.staff")).thenReturn(false);
        assertEquals(List.of("direct"), index.view(player, view).memberships().stream().map(entry -> entry.region().id()).toList());
    }

    @Test
    void nearbyPreviewUsesOneParticleBudgetAcrossRegionsAndViewers() throws Exception {
        var near = new ArrayList<Region>();
        for (int i = 0; i < 10; i++) near.add(new Region("near-" + i, "world", -30, -30, -30, 30, 30, 30));
        near.add(new Region("far", "world", 9000, 0, 10, 9100, 10, 60));
        seed(near.toArray(Region[]::new));
        assertEquals(10, manager.nearbyRegions(at(0), 48).size());

        Server server = mock(Server.class);
        var preview = new SelectionParticleTask(null, manager, config);
        var viewers = new ArrayList<Player>();
        for (int i = 0; i < 6; i++) {
            Player player = player();
            when(player.getLocation()).thenReturn(at(0));
            when(player.hasPermission("axs.regions.admin")).thenReturn(true);
            when(server.getPlayer(player.getUniqueId())).thenReturn(player);
            preview.toggleRegionBorder(player);
            viewers.add(player);
        }
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        try {
            var tick = SelectionParticleTask.class.getDeclaredMethod("tickRegionBorders");
            tick.setAccessible(true);
            tick.invoke(preview);
            long particles = 0;
            for (Player player : viewers) {
                long count = mockingDetails(player).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("spawnParticle")).count();
                assertTrue(count <= 400);
                particles += count;
            }
            assertEquals(1600, particles);
        } finally {
            serverField.set(null, previous);
        }
    }

    @Test
    void sameTickEntryAndExitPublishInOrderAndReuseTheGuardLookups() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY);
        seed(zone);
        Player player = player();
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(at(20));
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        var bus = mock(EventBusCapability.class);
        var listener = new RegionTransitions(plugin, manager, config, () -> bus);
        var enter = new PlayerMoveEvent(player, at(20), at(1));
        var leave = new PlayerMoveEvent(player, at(1), at(20));
        listener.guardWalk(enter);
        listener.reportWalk(enter);
        listener.guardWalk(leave);
        listener.reportWalk(leave);
        assertEquals(1, tasks.size());
        tasks.remove().run();

        var ordered = inOrder(bus);
        ordered.verify(bus).publish(eq("axs.regions.region_change"), eq(player),
            argThat(payload -> "enter".equals(payload.get("type"))));
        ordered.verify(bus).publish(eq("axs.regions.region_change"), eq(player),
            argThat(payload -> "leave".equals(payload.get("type"))));
        verifyNoMoreInteractions(bus);
        verify(world, times(4)).getName();
    }

    @Test
    void closingWhileTheWorkerIsQueuedDoesNotWaitOrPublishTheDraft() throws Exception {
        seed(region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY));
        var saves = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        var queued = new RegionManager(new RegionsRepository(dataFile), config, logger, callbacks::add, saves::add);
        queued.loadAll();
        var change = queued.setFlag(admin, queued.getRegion("zone", "world"), RegionFlag.PVP, State.ALLOW, null);
        queued.close();
        assertFalse(change.isDone());
        saves.remove().run();
        callbacks.remove().run();
        assertThrows(CompletionException.class, change::join);
        assertEquals(State.DENY, queued.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
    }

    @Test
    void replacementManagerLoadsCommittedRulesAndWaitsForPreviousFileReplacementOffThread() throws Exception {
        Region original = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY);
        seed(original);
        var worker = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        var next = new RegionManager(new RegionsRepository(dataFile), config, logger, callbacks::add, worker::add);
        java.util.concurrent.CompletableFuture<Region> edit;
        try (var pending = ActiveFileReplacement.begin(dataFile, Files.readString(dataFile))) {
            next.loadAll();
            assertEquals(State.DENY, next.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
            edit = next.setFlag(admin, next.getRegion("zone", "world"), RegionFlag.USE, State.DENY, null);
            assertTrue(worker.isEmpty());
            Region updated = original.copy(original.id());
            updated.setFlag(RegionFlag.PVP, State.ALLOW);
            Files.writeString(dataFile, RegionSnapshots.write(List.of(updated)));
        }
        worker.remove().run();
        assertEquals(State.DENY, next.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
        callbacks.remove().run();
        assertEquals(State.ALLOW, next.queryFlag("world", 1, 1, 1, RegionFlag.PVP));
        worker.remove().run();
        callbacks.remove().run();
        assertEquals(State.DENY, edit.join().getFlag(RegionFlag.USE));
    }

    @Test
    void currentMenuReportsEffectiveRulesAcrossOverlapsAndTheWorld() throws Exception {
        Region allowing = region("a-allow", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.ALLOW);
        Region denying = region("b-deny", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY);
        Region global = region("__global__", Region.Kind.GLOBAL, 0, 0, RegionFlag.FALL_DAMAGE, State.DENY);
        seed(allowing, denying, global);
        String ui = "AXS:regions_menu";
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        Player player = player();
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("axs.regions.select")).thenReturn(true);
        when(player.getLocation()).thenReturn(at(1));
        when(bridge.isUiOpen(player, ui)).thenReturn(true);
        var handler = new RegionsMenuPacketHandler(null, bridge, manager, config, ui);
        assertTrue(handler.handleClientPacket(player, RegionsMenuPacketHandler.PACKET_ID, List.of("current")));
        verify(bridge).sendPacket(eq(player), eq(ui), eq("region"), argThat(payload ->
            payload instanceof Map<?, ?> data && data.get("rows") instanceof List<?> rows
                && rows.stream().anyMatch(row -> row instanceof Map<?, ?> rule
                    && "b-deny".equals(rule.get("source")) && "禁止".equals(rule.get("state")))
                && rows.stream().anyMatch(row -> row instanceof Map<?, ?> rule
                    && "世界默认".equals(rule.get("source")) && "禁止".equals(rule.get("state")))));
    }

    @Test
    void menuDetailRequiresCurrentPermissionAndTheRegisteredOpenUi() throws Exception {
        Region zone = region("zone", Region.Kind.MANAGED, 0, 10, RegionFlag.PVP, State.DENY);
        seed(zone);
        String uiId = "AXS:Regions_Menu";
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        Player player = player();
        when(player.hasPermission("axs.regions.select")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        var handler = new RegionsMenuPacketHandler(null, bridge, manager, config, uiId);
        var detail = List.of("detail", zone.uid().toString(), "0");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(at(1));

        assertTrue(handler.handleClientPacket(player, RegionsMenuPacketHandler.PACKET_ID, detail));
        verify(bridge).isUiOpen(player, uiId);
        verify(player, never()).getLocation();
        verify(bridge, never()).sendPacket(any(), anyString(), anyString(), anyMap());

        when(bridge.isUiOpen(player, uiId)).thenReturn(true);
        when(player.hasPermission("axs.regions.select")).thenReturn(false);
        clearInvocations(bridge);
        assertTrue(handler.handleClientPacket(player, RegionsMenuPacketHandler.PACKET_ID, detail));
        verifyNoInteractions(bridge);
        verify(player, never()).getLocation();

        when(player.hasPermission("axs.regions.select")).thenReturn(true);
        assertTrue(handler.handleClientPacket(player, RegionsMenuPacketHandler.PACKET_ID, detail));
        verify(bridge).sendPacket(eq(player), eq(uiId), eq("region"),
            argThat(payload -> payload instanceof Map<?, ?> data
                && "detail".equals(data.get("view")) && zone.uid().toString().equals(data.get("id"))));
    }

    @Test
    void delayedMenuInitRechecksPermissionWithoutWaitingForUiAcknowledgement() throws Exception {
        String uiId = "AXS:Regions_Menu";
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        Player player = player();
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(at(20));
        var handler = new RegionsMenuPacketHandler(null, bridge, manager, config, uiId);
        when(bridge.openUi(player, uiId)).thenReturn(true);
        when(bridge.isUiOpen(player, uiId)).thenReturn(true);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(isNull(), any(Runnable.class), eq(2L))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object originalServer = serverField.get(null);
        serverField.set(null, server);
        try {
            handler.openMenu(player);
            assertTrue(tasks.isEmpty());
            verifyNoInteractions(bridge);

            when(player.hasPermission("arcartxsuite.admin")).thenReturn(true);
            handler.openMenu(player);
            when(player.hasPermission("arcartxsuite.admin")).thenReturn(false);
            tasks.remove().run();
            verify(bridge).openUi(player, uiId);
            verify(player, never()).getLocation();
            verify(bridge, never()).sendPacket(any(), anyString(), anyString(), anyMap());

            when(player.hasPermission("arcartxsuite.admin")).thenReturn(true);
            handler.openMenu(player);
            tasks.remove().run();
            verify(bridge).sendPacket(eq(player), eq(uiId), eq("region"),
                argThat(payload -> payload instanceof Map<?, ?> data && "current".equals(data.get("view"))));
        } finally {
            serverField.set(null, originalServer);
        }
    }

    private void seed(Region... regions) throws Exception {
        if (regions.length > 0) repository.applyChanges("Seed", "seed", List.of(), List.of(regions));
        manager.loadAll();
    }

    private static Region region(String id, Region.Kind kind, int min, int max, RegionFlag flag, State state) {
        Region region = new Region(id, "world", min, 0, 0, max, 10, 10);
        region.setKind(kind);
        region.setFlag(flag, state);
        return region;
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private Location at(int x) {
        return new Location(world, x, 1, 1);
    }

    private Block block(int x) {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(at(x));
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(1);
        when(block.getZ()).thenReturn(1);
        return block;
    }

    private void select(Player player, int from, int to) {
        var selection = manager.getSelection(player.getUniqueId());
        selection.setPos1(at(from));
        assertTrue(selection.setPos2(new Location(world, to, 3, 3)));
    }
}
