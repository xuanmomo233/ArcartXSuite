/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableMap
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  kotlin.Metadata
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.collections.SetsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.Regex
 *  kotlin1822.text.StringsKt
 *  net.minecraft.network.chat.IChatBaseComponent
 *  net.minecraft.network.chat.IChatMutableComponent
 *  net.minecraft.network.protocol.game.ClientboundSystemChatPacket
 *  net.minecraft.server.v1_13_R2.IBlockState
 *  net.minecraft.server.v1_14_R1.IBlockState
 *  net.minecraft.server.v1_16_R3.IBlockState
 *  net.minecraft.world.level.block.state.IBlockData
 *  net.minecraft.world.level.block.state.IBlockDataHolder
 *  net.minecraft.world.level.block.state.properties.IBlockState
 *  org.bukkit.Bukkit
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.bukkit;

import com.google.common.collect.ImmutableMap;
import ink.ptms.chemdah.core.bukkit.NMS;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.collections.MapsKt;
import kotlin1822.collections.SetsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.Regex;
import kotlin1822.text.StringsKt;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.v1_16_R3.IBlockState;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.IBlockDataHolder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00042\u0006\u0010\u0007\u001a\u00020\bH\u0016J\b\u0010\t\u001a\u00020\nH\u0002J \u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\u0011H\u0016\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/bukkit/NMSImpl;", "Link/ptms/chemdah/core/bukkit/NMS;", "()V", "getBlocKData", "", "", "", "block", "Lorg/bukkit/block/Block;", "isPaper", "", "sendSystemMessage", "", "player", "Lorg/bukkit/entity/Player;", "message", "sendPlayer", "Ljava/util/UUID;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nNMSImpl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NMSImpl.kt\nink/ptms/chemdah/core/bukkit/NMSImpl\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n*L\n1#1,87:1\n1855#2,2:88\n1238#2,4:92\n1238#2,4:98\n1238#2,4:104\n1238#2,4:110\n457#3:90\n403#3:91\n457#3:96\n403#3:97\n457#3:102\n403#3:103\n457#3:108\n403#3:109\n*S KotlinDebug\n*F\n+ 1 NMSImpl.kt\nink/ptms/chemdah/core/bukkit/NMSImpl\n*L\n27#1:88,2\n53#1:92,4\n57#1:98,4\n61#1:104,4\n65#1:110,4\n53#1:90\n53#1:91\n57#1:96\n57#1:97\n61#1:102\n61#1:103\n65#1:108\n65#1:109\n*E\n"})
public final class NMSImpl
extends NMS {
    /*
     * WARNING - void declaration
     */
    @Override
    public void sendSystemMessage(@NotNull Player player2, @NotNull String message2, @NotNull UUID sendPlayer) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
        Intrinsics.checkNotNullParameter((Object)sendPlayer, (String)"sendPlayer");
        if (new Regex("^\\[.*]$").matches((CharSequence)message2)) {
            void $this$forEach$iv;
            Object object = StringsKt.removeSurrounding((String)message2, (CharSequence)"[", (CharSequence)"]");
            Regex regex = new Regex("( )?,( )?");
            int n = 0;
            object = regex.split((CharSequence)object, n);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                String string;
                String it = (String)element$iv;
                boolean bl = false;
                AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player2, it, SetsKt.emptySet());
                Bukkit.getPluginManager().callEvent((Event)event);
                if (event.isCancelled()) continue;
                Intrinsics.checkNotNullExpressionValue((Object)event.getFormat(), (String)"event.format");
                Object[] objectArray = new Object[]{Bukkit.getOfflinePlayer((UUID)sendPlayer), event.getMessage()};
                String string2 = String.format(string, Arrays.copyOf(objectArray, objectArray.length));
                Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"format(format, *args)");
                IChatMutableComponent component = IChatBaseComponent.translatable((String)string2);
                MinecraftServerUtilKt.sendPacket((Player)player2, (Object)new ClientboundSystemChatPacket((IChatBaseComponent)component, false));
            }
            return;
        }
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player2, message2, SetsKt.emptySet());
        Bukkit.getPluginManager().callEvent((Event)event);
        if (event.isCancelled()) {
            return;
        }
        String string = event.getFormat();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"event.format");
        String string3 = string;
        Object[] objectArray = new Object[]{Bukkit.getOfflinePlayer((UUID)sendPlayer).getName(), event.getMessage()};
        String string4 = String.format(string3, Arrays.copyOf(objectArray, objectArray.length));
        Intrinsics.checkNotNullExpressionValue((Object)string4, (String)"format(format, *args)");
        IChatMutableComponent component = IChatBaseComponent.translatable((String)UtilKt.colored((String)string4));
        MinecraftServerUtilKt.sendPacket((Player)player2, (Object)new ClientboundSystemChatPacket((IChatBaseComponent)component, false));
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public Map<String, Object> getBlocKData(@NotNull Block block) {
        Map map;
        Intrinsics.checkNotNullParameter((Object)block, (String)"block");
        if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11800) {
            void $this$mapKeysTo$iv$iv;
            BlockData blockData = block.getBlockData();
            Intrinsics.checkNotNull((Object)blockData, (String)"null cannot be cast to non-null type org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData{ ink.ptms.chemdah.core.bukkit.NMSImplKt.CraftBlockData19 }");
            IBlockData iBlockData = ((org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData)blockData).getState();
            Intrinsics.checkNotNull((Object)iBlockData, (String)"null cannot be cast to non-null type net.minecraft.world.level.block.state.IBlockDataHolder<net.minecraft.world.level.block.Block{ ink.ptms.chemdah.core.bukkit.NMSImplKt.NMSBlock }, net.minecraft.world.level.block.state.IBlockData>");
            Map map2 = ((IBlockDataHolder)iBlockData).getValues();
            Intrinsics.checkNotNullExpressionValue((Object)map2, (String)"(block.blockData as Craf\u2026lock, IBlockData>).values");
            Map $this$mapKeys$iv = map2;
            boolean $i$f$mapKeys = false;
            Map map3 = $this$mapKeys$iv;
            Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
            boolean $i$f$mapKeysTo = false;
            Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
            boolean $i$f$associateByTo = false;
            for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                String string;
                void it$iv$iv;
                Object it;
                Map.Entry entry = (Map.Entry)element$iv$iv$iv;
                Map map4 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.world.level.block.state.properties.IBlockState)it.getKey()).getName(), (String)"it.key.name");
                Map.Entry entry2 = (Map.Entry)element$iv$iv$iv;
                Map map5 = map4;
                boolean bl2 = false;
                it = it$iv$iv.getValue();
                map5.put(string, it);
            }
            map = destination$iv$iv;
        } else if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11600) {
            BlockData blockData = block.getBlockData();
            Intrinsics.checkNotNull((Object)blockData, (String)"null cannot be cast to non-null type org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData{ ink.ptms.chemdah.core.bukkit.NMSImplKt.CraftBlockData16 }");
            ImmutableMap immutableMap = ((org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData)blockData).getState().getStateMap();
            Intrinsics.checkNotNullExpressionValue((Object)immutableMap, (String)"block.blockData as Craft\u2026ockData16).state.stateMap");
            Map $this$mapKeys$iv = (Map)immutableMap;
            boolean $i$f$mapKeys = false;
            Map $this$mapKeysTo$iv$iv = $this$mapKeys$iv;
            Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
            boolean $i$f$mapKeysTo = false;
            Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
            boolean $i$f$associateByTo = false;
            for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                String string;
                Map.Entry it = (Map.Entry)element$iv$iv$iv;
                Map map6 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((IBlockState)it.getKey()).getName(), (String)"it.key.name");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map7 = map6;
                boolean bl3 = false;
                it = it$iv$iv.getValue();
                map7.put(string, it);
            }
            map = destination$iv$iv;
        } else if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11400) {
            BlockData blockData = block.getBlockData();
            Intrinsics.checkNotNull((Object)blockData, (String)"null cannot be cast to non-null type org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData{ ink.ptms.chemdah.core.bukkit.NMSImplKt.CraftBlockData14 }");
            ImmutableMap immutableMap = ((org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData)blockData).getState().getStateMap();
            Intrinsics.checkNotNullExpressionValue((Object)immutableMap, (String)"block.blockData as Craft\u2026ockData14).state.stateMap");
            Map $this$mapKeys$iv = (Map)immutableMap;
            boolean $i$f$mapKeys = false;
            Map $this$mapKeysTo$iv$iv = $this$mapKeys$iv;
            Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
            boolean $i$f$mapKeysTo = false;
            Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
            boolean $i$f$associateByTo = false;
            for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                String string;
                Map.Entry it = (Map.Entry)element$iv$iv$iv;
                Map map8 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.server.v1_14_R1.IBlockState)it.getKey()).a(), (String)"it.key.a()");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map9 = map8;
                boolean bl4 = false;
                it = it$iv$iv.getValue();
                map9.put(string, it);
            }
            map = destination$iv$iv;
        } else if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
            BlockData blockData = block.getBlockData();
            Intrinsics.checkNotNull((Object)blockData, (String)"null cannot be cast to non-null type org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData{ ink.ptms.chemdah.core.bukkit.NMSImplKt.CraftBlockData13 }");
            ImmutableMap immutableMap = ((CraftBlockData)blockData).getState().getStateMap();
            Intrinsics.checkNotNullExpressionValue((Object)immutableMap, (String)"block.blockData as Craft\u2026ockData13).state.stateMap");
            Map $this$mapKeys$iv = (Map)immutableMap;
            boolean $i$f$mapKeys = false;
            Map $this$mapKeysTo$iv$iv = $this$mapKeys$iv;
            Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
            boolean $i$f$mapKeysTo = false;
            Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
            boolean $i$f$associateByTo = false;
            for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                String string;
                Map.Entry it = (Map.Entry)element$iv$iv$iv;
                Map map10 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.server.v1_13_R2.IBlockState)it.getKey()).a(), (String)"it.key.a()");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map11 = map10;
                boolean bl5 = false;
                Object v = it$iv$iv.getValue();
                map11.put(string, v);
            }
            map = destination$iv$iv;
        } else {
            map = MapsKt.emptyMap();
        }
        return map;
    }

    private final boolean isPaper() {
        return Class.forName("net.minecraft.world.level.block.state.properties.Property") != null;
    }
}

