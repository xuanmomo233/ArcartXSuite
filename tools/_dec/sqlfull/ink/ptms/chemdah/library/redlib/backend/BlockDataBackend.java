/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.backend;

import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.ChunkPosition;
import ink.ptms.chemdah.library.redlib.backend.PDCBackend;
import ink.ptms.chemdah.library.redlib.backend.SQLiteBackend;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

public interface BlockDataBackend {
    public static BlockDataBackend pdc(Plugin plugin2) {
        return new PDCBackend(plugin2);
    }

    public static BlockDataBackend sqlite(Path path) {
        return new SQLiteBackend(path);
    }

    public CompletableFuture<byte[]> load(ChunkPosition var1);

    public CompletableFuture<Void> save(ChunkPosition var1, byte[] var2);

    public CompletableFuture<Void> remove(ChunkPosition var1);

    public CompletableFuture<Void> saveAll();

    public CompletableFuture<Void> close();

    public CompletableFuture<Map<ChunkPosition, byte[]>> loadAll();

    public boolean attemptMigration(BlockDataManager var1);
}

