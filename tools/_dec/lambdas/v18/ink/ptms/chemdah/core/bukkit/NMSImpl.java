/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableMap
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  kotlin.Metadata
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  net.minecraft.server.v1_13_R2.IBlockState
 *  net.minecraft.server.v1_14_R1.IBlockState
 *  net.minecraft.server.v1_16_R3.IBlockState
 *  net.minecraft.world.level.block.state.IBlockData
 *  net.minecraft.world.level.block.state.IBlockDataHolder
 *  net.minecraft.world.level.block.state.properties.IBlockState
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData
 *  org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.bukkit;

import com.google.common.collect.ImmutableMap;
import ink.ptms.chemdah.core.bukkit.NMS;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import net.minecraft.server.v1_16_R3.IBlockState;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.IBlockDataHolder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00042\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/bukkit/NMSImpl;", "Link/ptms/chemdah/core/bukkit/NMS;", "()V", "getBlocKData", "", "", "", "block", "Lorg/bukkit/block/Block;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nNMSImpl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NMSImpl.kt\nink/ptms/chemdah/core/bukkit/NMSImpl\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,48:1\n457#2:49\n403#2:50\n457#2:55\n403#2:56\n457#2:61\n403#2:62\n457#2:67\n403#2:68\n1238#3,4:51\n1238#3,4:57\n1238#3,4:63\n1238#3,4:69\n*S KotlinDebug\n*F\n+ 1 NMSImpl.kt\nink/ptms/chemdah/core/bukkit/NMSImpl\n*L\n20#1:49\n20#1:50\n24#1:55\n24#1:56\n28#1:61\n28#1:62\n32#1:67\n32#1:68\n20#1:51,4\n24#1:57,4\n28#1:63,4\n32#1:69,4\n*E\n"})
public final class NMSImpl
extends NMS {
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
            ImmutableMap immutableMap = ((IBlockDataHolder)iBlockData).getValues();
            Intrinsics.checkNotNullExpressionValue((Object)immutableMap, (String)"(block.blockData as Craf\u2026lock, IBlockData>).values");
            Map $this$mapKeys$iv = (Map)immutableMap;
            boolean $i$f$mapKeys = false;
            Map map2 = $this$mapKeys$iv;
            Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
            boolean $i$f$mapKeysTo = false;
            Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
            boolean $i$f$associateByTo = false;
            for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                String string;
                void it$iv$iv;
                Object it;
                Map.Entry entry = (Map.Entry)element$iv$iv$iv;
                Map map3 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.world.level.block.state.properties.IBlockState)it.getKey()).getName(), (String)"it.key.name");
                Map.Entry entry2 = (Map.Entry)element$iv$iv$iv;
                Map map4 = map3;
                boolean bl2 = false;
                it = it$iv$iv.getValue();
                map4.put(string, it);
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
                Map map5 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((IBlockState)it.getKey()).getName(), (String)"it.key.name");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map6 = map5;
                boolean bl3 = false;
                it = it$iv$iv.getValue();
                map6.put(string, it);
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
                Map map7 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.server.v1_14_R1.IBlockState)it.getKey()).a(), (String)"it.key.a()");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map8 = map7;
                boolean bl4 = false;
                it = it$iv$iv.getValue();
                map8.put(string, it);
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
                Map map9 = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)((net.minecraft.server.v1_13_R2.IBlockState)it.getKey()).a(), (String)"it.key.a()");
                Map.Entry it$iv$iv = (Map.Entry)element$iv$iv$iv;
                Map map10 = map9;
                boolean bl5 = false;
                Object v = it$iv$iv.getValue();
                map10.put(string, v);
            }
            map = destination$iv$iv;
        } else {
            map = MapsKt.emptyMap();
        }
        return map;
    }
}

