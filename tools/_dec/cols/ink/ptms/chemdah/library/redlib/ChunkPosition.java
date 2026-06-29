/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.BlockPosition;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

public class ChunkPosition {
    private final int x;
    private final int z;
    private final String world;

    public ChunkPosition(Chunk chunk) {
        this(chunk.getX(), chunk.getZ(), chunk.getWorld().getName());
    }

    public ChunkPosition(Block block) {
        this(new BlockPosition(block), block.getWorld().getName());
    }

    public ChunkPosition(int x, int z, String world) {
        this.x = x;
        this.z = z;
        this.world = world;
    }

    public ChunkPosition(BlockPosition bPos, String world) {
        this(bPos.getX() >> 4, bPos.getZ() >> 4, world);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public World getWorld() {
        return Bukkit.getWorld((String)this.world);
    }

    public String getWorldName() {
        return this.world;
    }

    public int hashCode() {
        return Objects.hash(this.x, this.z, this.world);
    }

    public String toString() {
        return this.world + " " + this.x + " " + this.z;
    }

    public boolean equals(Object o) {
        if (!(o instanceof ChunkPosition)) {
            return false;
        }
        ChunkPosition pos = (ChunkPosition)o;
        return pos.x == this.x && pos.z == this.z && this.world.equals(pos.world);
    }
}

