/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.module.scenes.BlockList;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\bH\u0016J\u0016\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000bH\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/module/scenes/BlockListSingle;", "Link/ptms/chemdah/module/scenes/BlockList;", "block", "Link/ptms/chemdah/taboolib/common/util/Vector;", "(Link/ptms/chemdah/taboolib/common/util/Vector;)V", "getBlock", "()Link/ptms/chemdah/taboolib/common/util/Vector;", "getList", "", "Lorg/bukkit/block/Block;", "world", "Lorg/bukkit/World;", "Chemdah"})
public final class BlockListSingle
implements BlockList {
    @NotNull
    private final Vector block;

    public BlockListSingle(@NotNull Vector block) {
        Intrinsics.checkNotNullParameter((Object)block, (String)"block");
        this.block = block;
    }

    @NotNull
    public final Vector getBlock() {
        return this.block;
    }

    @Override
    @NotNull
    public List<Vector> getList() {
        return CollectionsKt.listOf((Object)this.block);
    }

    @Override
    @NotNull
    public List<Block> getList(@NotNull World world) {
        Intrinsics.checkNotNullParameter((Object)world, (String)"world");
        Block block = world.getBlockAt(this.block.getBlockX(), this.block.getBlockY(), this.block.getBlockZ());
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"world.getBlockAt(block.b\u2026ock.blockY, block.blockZ)");
        return CollectionsKt.listOf((Object)block);
    }
}

