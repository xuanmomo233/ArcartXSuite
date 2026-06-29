/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.Adyeshach
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.adyeshach.core.entity.EntityTypes
 *  ink.ptms.adyeshach.core.entity.manager.Manager
 *  ink.ptms.adyeshach.core.entity.manager.ManagerType
 *  ink.ptms.adyeshach.core.entity.type.AdyFallingBlock
 *  ink.ptms.chemdah.taboolib.common.platform.Schedule
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.Location
 *  ink.ptms.chemdah.taboolib.common.util.Vector
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.module.nms.Packet
 *  ink.ptms.chemdah.taboolib.module.nms.PacketReceiveEvent
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.adyeshach.core.Adyeshach;
import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.adyeshach.core.entity.EntityTypes;
import ink.ptms.adyeshach.core.entity.manager.Manager;
import ink.ptms.adyeshach.core.entity.manager.ManagerType;
import ink.ptms.adyeshach.core.entity.type.AdyFallingBlock;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.module.kether.ActionScenes;
import ink.ptms.chemdah.module.scenes.ScenesBlockData;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.module.nms.Packet;
import ink.ptms.chemdah.taboolib.module.nms.PacketReceiveEvent;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt;
import ink.ptms.chemdah.util.LocationKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0005\u0018\u0000 \u00032\u00020\u0001:\u0003\u0003\u0004\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/ActionScenes;", "", "()V", "Companion", "ScenesBlockSet0", "ScenesBlockSet1", "Chemdah"})
public final class ActionScenes {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private static final ConcurrentHashMap<String, Map<String, Map<Vector, ScenesBlockData>>> scenesBlocks = new ConcurrentHashMap();

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000j\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010%\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0005\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0003J\u0010\u0010\r\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u000eH\u0003J\u0010\u0010\u000f\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u0010H\u0003J\u0010\u0010\u0011\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u0012H\u0003J\u0012\u0010\u0013\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0014H\u0007J\b\u0010\u0015\u001a\u00020\nH\u0007J$\u0010\u0016\u001a\u00020\n*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001b2\b\b\u0002\u0010\u001c\u001a\u00020\u001dJ.\u0010\u001e\u001a\u00020\n*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001b2\b\b\u0002\u0010\u001c\u001a\u00020\u001d2\b\b\u0002\u0010\u001f\u001a\u00020 J\u0012\u0010!\u001a\u00020\n*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019J\u001c\u0010\"\u001a\u00020\n*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u001f\u001a\u00020 J\n\u0010#\u001a\u00020\n*\u00020\u0017R2\u0010\u0003\u001a&\u0012\u0004\u0012\u00020\u0005\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u00060\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2={"Link/ptms/chemdah/module/kether/ActionScenes$Companion;", "", "()V", "scenesBlocks", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "Link/ptms/chemdah/taboolib/common/util/Vector;", "Link/ptms/chemdah/module/scenes/ScenesBlockData;", "onChangeWorld", "", "e", "Lorg/bukkit/event/player/PlayerChangedWorldEvent;", "onPacketReceive", "Link/ptms/chemdah/taboolib/module/nms/PacketReceiveEvent;", "onReleased", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "onTeleport", "Lorg/bukkit/event/player/PlayerTeleportEvent;", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "updateScenesBlock40", "createScenesBlock", "Lorg/bukkit/entity/Player;", "location", "Lorg/bukkit/Location;", "material", "Lorg/bukkit/Material;", "data", "", "createScenesFallingBlock", "toSolid", "", "removeScenesBlock", "removeScenesFallingBlock", "updateScenesBlock", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionScenes.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionScenes.kt\nink/ptms/chemdah/module/kether/ActionScenes$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,323:1\n1855#2,2:324\n1855#2,2:328\n215#3,2:326\n*S KotlinDebug\n*F\n+ 1 ActionScenes.kt\nink/ptms/chemdah/module/kether/ActionScenes$Companion\n*L\n187#1:324,2\n298#1:328,2\n273#1:326,2\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"scenes"}, shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        @Schedule(async=true, period=40L)
        public final void updateScenesBlock40() {
            Collection collection = Bukkit.getOnlinePlayers();
            Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
            Iterable $this$forEach$iv = collection;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                Companion.updateScenesBlock(it);
            }
        }

        @SubscribeEvent
        private final void onPacketReceive(PacketReceiveEvent e) {
            Object data2;
            Vector vec;
            Object pos;
            if (Intrinsics.areEqual((Object)e.getPacket().getName(), (Object)"PacketPlayInUseItem")) {
                Object object;
                Vector vector;
                Object object2;
                if (MinecraftVersion.INSTANCE.isUniversal()) {
                    Object object3 = Packet.read$default((Packet)e.getPacket(), (String)"a/blockPos", (boolean)false, (int)2, null);
                    object2 = object3;
                    Intrinsics.checkNotNull((Object)object3);
                } else if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11400) {
                    Object object4 = Packet.read$default((Packet)e.getPacket(), (String)"a/c", (boolean)false, (int)2, null);
                    object2 = object4;
                    Intrinsics.checkNotNull((Object)object4);
                } else {
                    Object object5 = Packet.read$default((Packet)e.getPacket(), (String)"a", (boolean)false, (int)2, null);
                    object2 = object5;
                    Intrinsics.checkNotNull((Object)object5);
                }
                pos = object2;
                if (MinecraftVersion.INSTANCE.getMajor() >= 10) {
                    Object object6 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"x", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object6);
                    int n = ((Number)object6).intValue();
                    Object object7 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"y", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object7);
                    int n2 = ((Number)object7).intValue();
                    Object object8 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"z", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object8);
                    vector = new Vector(n, n2, ((Number)object8).intValue());
                } else {
                    Object object9 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getX", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object9);
                    int n = ((Number)object9).intValue();
                    Object object10 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getY", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object10);
                    int n3 = ((Number)object10).intValue();
                    Object object11 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getZ", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object11);
                    vector = vec = new Vector(n, n3, ((Number)object11).intValue());
                }
                if ((object = (Map)scenesBlocks.get(e.getPlayer().getName())) == null || (object = (Map)object.get(e.getPlayer().getWorld().getName())) == null || (object = (ScenesBlockData)object.get(vec)) == null) {
                    return;
                }
                data2 = object;
                new PlayerEvents.ScenesBlockInteract(e.getPlayer(), (ScenesBlockData)data2).call();
                e.setCancelled(true);
            }
            if (Intrinsics.areEqual((Object)e.getPacket().getName(), (Object)"PacketPlayInBlockDig")) {
                Object object;
                Vector vector;
                Object object12 = Packet.read$default((Packet)e.getPacket(), (String)"a", (boolean)false, (int)2, null);
                if (object12 == null) {
                    return;
                }
                pos = object12;
                if (MinecraftVersion.INSTANCE.getMajor() >= 10) {
                    Object object13 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"x", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object13);
                    int n = ((Number)object13).intValue();
                    Object object14 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"y", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object14);
                    int n4 = ((Number)object14).intValue();
                    Object object15 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"z", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object15);
                    vector = new Vector(n, n4, ((Number)object15).intValue());
                } else {
                    Object object16 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getX", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object16);
                    int n = ((Number)object16).intValue();
                    Object object17 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getY", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object17);
                    int n5 = ((Number)object17).intValue();
                    Object object18 = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)pos, (String)"getZ", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                    Intrinsics.checkNotNull((Object)object18);
                    vector = vec = new Vector(n, n5, ((Number)object18).intValue());
                }
                if ((object = (Map)scenesBlocks.get(e.getPlayer().getName())) == null || (object = (Map)object.get(e.getPlayer().getWorld().getName())) == null || (object = (ScenesBlockData)object.get(vec)) == null) {
                    return;
                }
                data2 = object;
                if (Intrinsics.areEqual((Object)String.valueOf(Packet.read$default((Packet)e.getPacket(), (String)"c", (boolean)false, (int)2, null)), (Object)"STOP_DESTROY_BLOCK")) {
                    if (!new PlayerEvents.ScenesBlockBreak(e.getPlayer(), (ScenesBlockData)data2).call()) {
                        ExecutorKt.submit$default((boolean)false, (boolean)false, (long)1L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e, vec, (ScenesBlockData)data2){
                            final /* synthetic */ PacketReceiveEvent $e;
                            final /* synthetic */ Vector $vec;
                            final /* synthetic */ ScenesBlockData $data;
                            {
                                this.$e = $e;
                                this.$vec = $vec;
                                this.$data = $data;
                                super(1);
                            }

                            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                                Player player2 = this.$e.getPlayer();
                                Location location = this.$vec.toLocation(this.$e.getPlayer().getWorld().getName());
                                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"vec.toLocation(e.player.world.name)");
                                ActionScenes.Companion.createScenesBlock(player2, BukkitLocationKt.toBukkitLocation((Location)location), this.$data.getMaterial(), this.$data.getData());
                            }
                        }), (int)11, null);
                        e.setCancelled(true);
                    } else {
                        Player player2 = e.getPlayer();
                        Location location = vec.toLocation(e.getPlayer().getWorld().getName());
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"vec.toLocation(e.player.world.name)");
                        this.removeScenesBlock(player2, BukkitLocationKt.toBukkitLocation((Location)location));
                    }
                }
            }
        }

        @SubscribeEvent
        private final void onTeleport(PlayerTeleportEvent e) {
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)20L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e){
                final /* synthetic */ PlayerTeleportEvent $e;
                {
                    this.$e = $e;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    Player player2 = this.$e.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
                    ActionScenes.Companion.updateScenesBlock(player2);
                }
            }), (int)11, null);
        }

        @SubscribeEvent
        private final void onChangeWorld(PlayerChangedWorldEvent e) {
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)20L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e){
                final /* synthetic */ PlayerChangedWorldEvent $e;
                {
                    this.$e = $e;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    Player player2 = this.$e.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
                    ActionScenes.Companion.updateScenesBlock(player2);
                }
            }), (int)11, null);
        }

        @SubscribeEvent
        private final void onReleased(PlayerEvents.Released e) {
            scenesBlocks.remove(e.getPlayer().getName());
        }

        public final void removeScenesBlock(@NotNull Player $this$removeScenesBlock, @NotNull org.bukkit.Location location) {
            Intrinsics.checkNotNullParameter((Object)$this$removeScenesBlock, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Map map = (Map)scenesBlocks.get($this$removeScenesBlock.getName());
            if (map != null && (map = (Map)map.get($this$removeScenesBlock.getWorld().getName())) != null) {
                ScenesBlockData cfr_ignored_0 = (ScenesBlockData)map.remove(new Vector(location.getX(), location.getY(), location.getZ()));
            }
            if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                $this$removeScenesBlock.sendBlockChange(location, location.getBlock().getBlockData());
            } else {
                $this$removeScenesBlock.sendBlockChange(location, location.getBlock().getType(), location.getBlock().getData());
            }
        }

        public final void createScenesBlock(@NotNull Player $this$createScenesBlock, @NotNull org.bukkit.Location location, @NotNull Material material, byte data2) {
            Intrinsics.checkNotNullParameter((Object)$this$createScenesBlock, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Map map = scenesBlocks.computeIfAbsent($this$createScenesBlock.getName(), arg_0 -> Companion.createScenesBlock$lambda$1(createScenesBlock.worlds.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)map, (String)"scenesBlocks.computeIfAb\u2026) { ConcurrentHashMap() }");
            Map worlds2 = map;
            Map map2 = worlds2.computeIfAbsent($this$createScenesBlock.getWorld().getName(), arg_0 -> Companion.createScenesBlock$lambda$2(createScenesBlock.blocks.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)map2, (String)"worlds.computeIfAbsent(w\u2026) { ConcurrentHashMap() }");
            Map blocks2 = map2;
            blocks2.put(new Vector(location.getX(), location.getY(), location.getZ()), new ScenesBlockData(material, data2, false, 4, null));
            if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                $this$createScenesBlock.sendBlockChange(location, material.createBlockData());
            } else {
                $this$createScenesBlock.sendBlockChange(location, material, data2);
            }
        }

        public static /* synthetic */ void createScenesBlock$default(Companion companion, Player player2, org.bukkit.Location location, Material material, byte by, int n, Object object) {
            if ((n & 4) != 0) {
                by = 0;
            }
            companion.createScenesBlock(player2, location, material, by);
        }

        public final void updateScenesBlock(@NotNull Player $this$updateScenesBlock) {
            block2: {
                Intrinsics.checkNotNullParameter((Object)$this$updateScenesBlock, (String)"<this>");
                Map map = (Map)scenesBlocks.get($this$updateScenesBlock.getName());
                if (map == null || (map = (Map)map.get($this$updateScenesBlock.getWorld().getName())) == null) break block2;
                Map $this$forEach$iv = map;
                boolean $i$f$forEach = false;
                Iterator iterator = $this$forEach$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry element$iv;
                    Map.Entry it = element$iv = iterator.next();
                    boolean bl = false;
                    Location location = ((Vector)it.getKey()).toLocation($this$updateScenesBlock.getWorld().getName());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.key.toLocation(world.name)");
                    org.bukkit.Location loc = BukkitLocationKt.toBukkitLocation((Location)location);
                    org.bukkit.Location location2 = $this$updateScenesBlock.getLocation();
                    Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location");
                    if (!(LocationKt.safeDistance(loc, location2) < 128.0)) continue;
                    if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                        $this$updateScenesBlock.sendBlockChange(loc, ((ScenesBlockData)it.getValue()).getMaterial().createBlockData());
                        continue;
                    }
                    $this$updateScenesBlock.sendBlockChange(loc, ((ScenesBlockData)it.getValue()).getMaterial(), ((ScenesBlockData)it.getValue()).getData());
                }
            }
        }

        public final void createScenesFallingBlock(@NotNull Player $this$createScenesFallingBlock, @NotNull org.bukkit.Location location, @NotNull Material material, byte data2, boolean toSolid) {
            Intrinsics.checkNotNullParameter((Object)$this$createScenesFallingBlock, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Manager manager = Adyeshach.INSTANCE.api().getPrivateEntityManager($this$createScenesFallingBlock, ManagerType.TEMPORARY);
            EntityInstance npc = manager.create(EntityTypes.FALLING_BLOCK, location, arg_0 -> Companion.createScenesFallingBlock$lambda$4(material, data2, arg_0));
            npc.setTag("chemdah:scenes", (Object)(toSolid ? "SOLID" : "NONE"));
            npc.setVelocity(new org.bukkit.util.Vector(0.0, 0.001, 0.0));
        }

        public static /* synthetic */ void createScenesFallingBlock$default(Companion companion, Player player2, org.bukkit.Location location, Material material, byte by, boolean bl, int n, Object object) {
            if ((n & 4) != 0) {
                by = 0;
            }
            if ((n & 8) != 0) {
                bl = false;
            }
            companion.createScenesFallingBlock(player2, location, material, by, bl);
        }

        /*
         * WARNING - void declaration
         */
        public final void removeScenesFallingBlock(@NotNull Player $this$removeScenesFallingBlock, @NotNull org.bukkit.Location location, boolean toSolid) {
            void $this$forEach$iv;
            Intrinsics.checkNotNullParameter((Object)$this$removeScenesFallingBlock, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Manager manager = Adyeshach.INSTANCE.api().getPrivateEntityManager($this$removeScenesFallingBlock, ManagerType.TEMPORARY);
            Iterable iterable = manager.getEntities(arg_0 -> Companion.removeScenesFallingBlock$lambda$5(location, toSolid, arg_0));
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                EntityInstance it = (EntityInstance)element$iv;
                boolean bl = false;
                it.remove();
            }
        }

        public static /* synthetic */ void removeScenesFallingBlock$default(Companion companion, Player player2, org.bukkit.Location location, boolean bl, int n, Object object) {
            if ((n & 2) != 0) {
                bl = false;
            }
            companion.removeScenesFallingBlock(player2, location, bl);
        }

        private static final Map createScenesBlock$lambda$1(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Map)$tmp0.invoke(p0);
        }

        private static final Map createScenesBlock$lambda$2(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Map)$tmp0.invoke(p0);
        }

        private static final void createScenesFallingBlock$lambda$4(Material $material, byte $data, EntityInstance it) {
            Intrinsics.checkNotNullParameter((Object)$material, (String)"$material");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            ((AdyFallingBlock)it).setMaterial($material, $data);
        }

        private static final boolean removeScenesFallingBlock$lambda$5(org.bukkit.Location $location, boolean $toSolid, EntityInstance it) {
            Intrinsics.checkNotNullParameter((Object)$location, (String)"$location");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            return it.getLocation().distance($location) <= 0.0 && Intrinsics.areEqual((Object)it.getTag("chemdah:scenes"), (Object)($toSolid ? "SOLID" : "NONE"));
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0005\n\u0000\n\u0002\u0010\u000b\n\u0002\b\f\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B3\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\b\b\u0002\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\n\u00a2\u0006\u0002\u0010\fJ\u001a\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00020\u00172\n\u0010\u0018\u001a\u00060\u0019j\u0002`\u001aH\u0016J\b\u0010\u001b\u001a\u00020\u001cH\u0016R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u000b\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0010\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/module/kether/ActionScenes$ScenesBlockSet0;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "location", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "material", "Lorg/bukkit/Material;", "data", "", "falling", "", "solid", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Lorg/bukkit/Material;BZZ)V", "getData", "()B", "getFalling", "()Z", "getLocation", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getMaterial", "()Lorg/bukkit/Material;", "getSolid", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "toString", "", "Chemdah"})
    public static final class ScenesBlockSet0
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> location;
        @NotNull
        private final Material material;
        private final byte data;
        private final boolean falling;
        private final boolean solid;

        public ScenesBlockSet0(@NotNull ParsedAction<?> location, @NotNull Material material, byte data2, boolean falling, boolean solid) {
            Intrinsics.checkNotNullParameter(location, (String)"location");
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            this.location = location;
            this.material = material;
            this.data = data2;
            this.falling = falling;
            this.solid = solid;
        }

        public /* synthetic */ ScenesBlockSet0(ParsedAction parsedAction, Material material, byte by, boolean bl, boolean bl2, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                by = 0;
            }
            this(parsedAction, material, by, bl, bl2);
        }

        @NotNull
        public final ParsedAction<?> getLocation() {
            return this.location;
        }

        @NotNull
        public final Material getMaterial() {
            return this.material;
        }

        public final byte getData() {
            return this.data;
        }

        public final boolean getFalling() {
            return this.falling;
        }

        public final boolean getSolid() {
            return this.solid;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.location).run().thenAccept(arg_0 -> ScenesBlockSet0.run$lambda$0((Function1)new Function1<org.bukkit.Location, Unit>(this, frame){
                final /* synthetic */ ScenesBlockSet0 this$0;
                final /* synthetic */ QuestContext.Frame $frame;
                {
                    this.this$0 = $receiver;
                    this.$frame = $frame;
                    super(1);
                }

                public final void invoke(org.bukkit.Location location) {
                    if (this.this$0.getFalling()) {
                        Player player2 = UtilsForKetherKt.getBukkitPlayer(this.$frame);
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"location");
                        ActionScenes.Companion.createScenesFallingBlock(player2, location, this.this$0.getMaterial(), this.this$0.getData(), this.this$0.getSolid());
                    } else {
                        Player player3 = UtilsForKetherKt.getBukkitPlayer(this.$frame);
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"location");
                        ActionScenes.Companion.createScenesBlock(player3, location, this.this$0.getMaterial(), this.this$0.getData());
                    }
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"override fun run(frame: \u2026}\n            }\n        }");
            return completionStage;
        }

        @NotNull
        public String toString() {
            return "ScenesBlockSet0(location=" + this.location + ", material=" + this.material + ", data=" + this.data + ", falling=" + this.falling + ", solid=" + this.solid + ')';
        }

        private static final void run$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B-\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\tJ\u001a\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00020\u00112\n\u0010\u0012\u001a\u00060\u0013j\u0002`\u0014H\u0016J\b\u0010\u0015\u001a\u00020\u0016H\u0016R\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000bR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\r\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/module/kether/ActionScenes$ScenesBlockSet1;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "location", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "copy", "falling", "", "solid", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;ZZ)V", "getCopy", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getFalling", "()Z", "getLocation", "getSolid", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "toString", "", "Chemdah"})
    public static final class ScenesBlockSet1
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> location;
        @NotNull
        private final ParsedAction<?> copy;
        private final boolean falling;
        private final boolean solid;

        public ScenesBlockSet1(@NotNull ParsedAction<?> location, @NotNull ParsedAction<?> copy, boolean falling, boolean solid) {
            Intrinsics.checkNotNullParameter(location, (String)"location");
            Intrinsics.checkNotNullParameter(copy, (String)"copy");
            this.location = location;
            this.copy = copy;
            this.falling = falling;
            this.solid = solid;
        }

        @NotNull
        public final ParsedAction<?> getLocation() {
            return this.location;
        }

        @NotNull
        public final ParsedAction<?> getCopy() {
            return this.copy;
        }

        public final boolean getFalling() {
            return this.falling;
        }

        public final boolean getSolid() {
            return this.solid;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.location).run().thenAccept(arg_0 -> ScenesBlockSet1.run$lambda$0((Function1)new Function1<org.bukkit.Location, Unit>(frame, this){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ ScenesBlockSet1 this$0;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(org.bukkit.Location location) {
                    this.$frame.newFrame(this.this$0.getCopy()).run().thenAccept(arg_0 -> run.1.invoke$lambda$0((Function1)new Function1<org.bukkit.Location, Unit>(this.this$0, this.$frame, location){
                        final /* synthetic */ ScenesBlockSet1 this$0;
                        final /* synthetic */ QuestContext.Frame $frame;
                        final /* synthetic */ org.bukkit.Location $location;
                        {
                            this.this$0 = $receiver;
                            this.$frame = $frame;
                            this.$location = $location;
                            super(1);
                        }

                        public final void invoke(org.bukkit.Location copy) {
                            Block block = copy.getBlock();
                            Intrinsics.checkNotNullExpressionValue((Object)block, (String)"copy.block");
                            Block block2 = block;
                            if (this.this$0.getFalling()) {
                                Player player2 = UtilsForKetherKt.getBukkitPlayer(this.$frame);
                                org.bukkit.Location location = this.$location;
                                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"location");
                                Material material = block2.getType();
                                Intrinsics.checkNotNullExpressionValue((Object)material, (String)"block.type");
                                ActionScenes.Companion.createScenesFallingBlock(player2, location, material, block2.getData(), this.this$0.getSolid());
                            } else {
                                Player player3 = UtilsForKetherKt.getBukkitPlayer(this.$frame);
                                org.bukkit.Location location = this.$location;
                                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"location");
                                Material material = block2.getType();
                                Intrinsics.checkNotNullExpressionValue((Object)material, (String)"block.type");
                                ActionScenes.Companion.createScenesBlock(player3, location, material, block2.getData());
                            }
                        }
                    }, arg_0));
                }

                private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    $tmp0.invoke(p0);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"override fun run(frame: \u2026}\n            }\n        }");
            return completionStage;
        }

        @NotNull
        public String toString() {
            return "ScenesBlockSet1(location=" + this.location + ", copy=" + this.copy + ", falling=" + this.falling + ", solid=" + this.solid + ')';
        }

        private static final void run$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }
    }
}

