/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.module.scenes.BlockList;
import ink.ptms.chemdah.module.scenes.ScenesState;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\u000e\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00030\nH\u0016J\u0016\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\u0006\u0010\f\u001a\u00020\rH\u0016R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/module/scenes/BlockListArea;", "Link/ptms/chemdah/module/scenes/BlockList;", "min", "Link/ptms/chemdah/taboolib/common/util/Vector;", "max", "(Link/ptms/chemdah/taboolib/common/util/Vector;Link/ptms/chemdah/taboolib/common/util/Vector;)V", "getMax", "()Link/ptms/chemdah/taboolib/common/util/Vector;", "getMin", "getList", "", "Lorg/bukkit/block/Block;", "world", "Lorg/bukkit/World;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nBlockListArea.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BlockListArea.kt\nink/ptms/chemdah/module/scenes/BlockListArea\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,42:1\n1855#2:43\n1855#2:44\n1855#2,2:45\n1856#2:47\n1856#2:48\n1855#2:49\n1855#2:50\n1855#2,2:51\n1856#2:53\n1856#2:54\n*S KotlinDebug\n*F\n+ 1 BlockListArea.kt\nink/ptms/chemdah/module/scenes/BlockListArea\n*L\n21#1:43\n22#1:44\n23#1:45,2\n22#1:47\n21#1:48\n33#1:49\n34#1:50\n35#1:51,2\n34#1:53\n33#1:54\n*E\n"})
public final class BlockListArea
implements BlockList {
    @NotNull
    private final Vector min;
    @NotNull
    private final Vector max;

    public BlockListArea(@NotNull Vector min2, @NotNull Vector max2) {
        Intrinsics.checkNotNullParameter((Object)min2, (String)"min");
        Intrinsics.checkNotNullParameter((Object)max2, (String)"max");
        this.min = (Vector)ScenesState.Companion.getArea(min2, max2).getFirst();
        this.max = (Vector)ScenesState.Companion.getArea(min2, max2).getSecond();
    }

    @NotNull
    public final Vector getMin() {
        return this.min;
    }

    @NotNull
    public final Vector getMax() {
        return this.max;
    }

    @Override
    @NotNull
    public List<Vector> getList() {
        ArrayList<Vector> arrayList;
        ArrayList<Vector> blocks2 = arrayList = new ArrayList<Vector>();
        boolean bl = false;
        Iterable $this$forEach$iv = (Iterable)new IntRange(this.min.getBlockX(), this.max.getBlockX());
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.iterator();
        while (iterator.hasNext()) {
            int element$iv;
            int x = element$iv = ((IntIterator)iterator).nextInt();
            boolean bl2 = false;
            Iterable $this$forEach$iv2 = (Iterable)new IntRange(this.min.getBlockY(), this.max.getBlockY());
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.iterator();
            while (iterator2.hasNext()) {
                int element$iv2;
                int y = element$iv2 = ((IntIterator)iterator2).nextInt();
                boolean bl3 = false;
                Iterable $this$forEach$iv3 = (Iterable)new IntRange(this.min.getBlockZ(), this.max.getBlockZ());
                boolean $i$f$forEach3 = false;
                Iterator iterator3 = $this$forEach$iv3.iterator();
                while (iterator3.hasNext()) {
                    int element$iv3;
                    int z = element$iv3 = ((IntIterator)iterator3).nextInt();
                    boolean bl4 = false;
                    blocks2.add(new Vector(x, y, z));
                }
            }
        }
        return arrayList;
    }

    @Override
    @NotNull
    public List<Block> getList(@NotNull World world) {
        ArrayList<Block> arrayList;
        Intrinsics.checkNotNullParameter((Object)world, (String)"world");
        ArrayList<Block> blocks2 = arrayList = new ArrayList<Block>();
        boolean bl = false;
        Iterable $this$forEach$iv = (Iterable)new IntRange(this.min.getBlockX(), this.max.getBlockX());
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.iterator();
        while (iterator.hasNext()) {
            int element$iv;
            int x = element$iv = ((IntIterator)iterator).nextInt();
            boolean bl2 = false;
            Iterable $this$forEach$iv2 = (Iterable)new IntRange(this.min.getBlockY(), this.max.getBlockY());
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.iterator();
            while (iterator2.hasNext()) {
                int element$iv2;
                int y = element$iv2 = ((IntIterator)iterator2).nextInt();
                boolean bl3 = false;
                Iterable $this$forEach$iv3 = (Iterable)new IntRange(this.min.getBlockZ(), this.max.getBlockZ());
                boolean $i$f$forEach3 = false;
                Iterator iterator3 = $this$forEach$iv3.iterator();
                while (iterator3.hasNext()) {
                    int element$iv3;
                    int z = element$iv3 = ((IntIterator)iterator3).nextInt();
                    boolean bl4 = false;
                    blocks2.add(world.getBlockAt(x, y, z));
                }
            }
        }
        return arrayList;
    }
}

