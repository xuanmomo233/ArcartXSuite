/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.taboolib.common.util.Vector;
import java.util.List;
import kotlin.Metadata;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u000e\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H&J\u0016\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00050\u00032\u0006\u0010\u0006\u001a\u00020\u0007H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\b\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/module/scenes/BlockList;", "", "getList", "", "Link/ptms/chemdah/taboolib/common/util/Vector;", "Lorg/bukkit/block/Block;", "world", "Lorg/bukkit/World;", "Chemdah"})
public interface BlockList {
    @NotNull
    public List<Vector> getList();

    @NotNull
    public List<Block> getList(@NotNull World var1);
}

