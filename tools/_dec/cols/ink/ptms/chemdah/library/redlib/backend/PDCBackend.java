/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.backend;

import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.ChunkPosition;
import ink.ptms.chemdah.library.redlib.backend.BlockDataBackend;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

class PDCBackend
implements BlockDataBackend {
    private final NamespacedKey key;

    public PDCBackend(Plugin plugin2) {
        this.key = new NamespacedKey(plugin2, "chemdah");
    }

    @Override
    public CompletableFuture<byte[]> load(ChunkPosition pos) {
        PersistentDataContainer pdc = pos.getWorld().getChunkAt(pos.getX(), pos.getZ()).getPersistentDataContainer();
        return CompletableFuture.completedFuture((byte[])pdc.get(this.key, PersistentDataType.BYTE_ARRAY));
    }

    @Override
    public CompletableFuture<Void> save(ChunkPosition pos, byte[] data2) {
        PersistentDataContainer pdc = pos.getWorld().getChunkAt(pos.getX(), pos.getZ()).getPersistentDataContainer();
        pdc.set(this.key, PersistentDataType.BYTE_ARRAY, (Object)data2);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> remove(ChunkPosition pos) {
        PersistentDataContainer pdc = pos.getWorld().getChunkAt(pos.getX(), pos.getZ()).getPersistentDataContainer();
        pdc.remove(this.key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<ChunkPosition, byte[]>> loadAll() {
        throw new UnsupportedOperationException("PDC backend cannot access all data blocks");
    }

    @Override
    public boolean attemptMigration(BlockDataManager manager) {
        return false;
    }
}

