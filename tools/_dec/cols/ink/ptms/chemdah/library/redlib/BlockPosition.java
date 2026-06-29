/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import java.util.Objects;
import org.bukkit.World;
import org.bukkit.block.Block;

class BlockPosition {
    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockPosition(Block block) {
        this(block.getX(), block.getY(), block.getZ());
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public Block getBlock(World world) {
        return world.getBlockAt(this.x, this.y, this.z);
    }

    public int hashCode() {
        return Objects.hash(this.x, this.y, this.z);
    }

    public boolean equals(Object o) {
        if (!(o instanceof BlockPosition)) {
            return false;
        }
        BlockPosition pos = (BlockPosition)o;
        return pos.x == this.x && pos.y == this.y && pos.z == this.z;
    }

    public String toString() {
        return this.x + " " + this.y + " " + this.z;
    }
}

