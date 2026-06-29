/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.block.Block
 */
package ink.ptms.chemdah.library.redlib.backend;

import ink.ptms.chemdah.library.redlib.BlockDataContainerKt;
import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.ChunkPosition;
import ink.ptms.chemdah.library.redlib.DataBlock;
import ink.ptms.chemdah.library.redlib.Locations;
import ink.ptms.chemdah.library.redlib.backend.BlockDataBackend;
import ink.ptms.chemdah.library.redlib.sql.SQLHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.bukkit.block.Block;

class SQLiteBackend
implements BlockDataBackend {
    private final SQLHelper helper;
    private final Executor exec = Executors.newSingleThreadExecutor();
    private final Path path;

    public SQLiteBackend(Path path) {
        this.path = path;
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        this.helper = new SQLHelper(SQLHelper.openSQLite(path));
        this.helper.execute("PRAGMA synchronous = OFF;", new Object[0]);
        this.helper.executeUpdate("CREATE TABLE IF NOT EXISTS data (x INT, z INT, world STRING, data BLOB, PRIMARY KEY (x, z, world));", new Object[0]);
        this.helper.setCommitInterval(6000);
    }

    @Override
    public boolean attemptMigration(BlockDataManager manager) {
        try {
            DatabaseMetaData metadata = this.helper.getConnection().getMetaData();
            ResultSet results = metadata.getTables(null, null, "blocks", null);
            if (!results.next()) {
                return false;
            }
            results.close();
            Files.copy(this.path, this.path.getParent().resolve(this.path.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
            this.helper.queryResults("SELECT x, y, z, world, data FROM blocks;", new Object[0]).forEach(r -> {
                int x = (Integer)r.get(1);
                int y = (Integer)r.get(2);
                int z = (Integer)r.get(3);
                String worldName = r.getString(4);
                byte[] data2 = r.getBytes(5);
                Locations.waitForWorld(worldName, world -> {
                    Block block = world.getBlockAt(x, y, z);
                    DataBlock db = manager.getDataBlock(block);
                    Map<String, Object> map = BlockDataContainerKt.deserializeToMap(data2, true);
                    map.keySet().forEach(k -> db.set((String)k, map.get(k)));
                });
            });
            this.helper.executeUpdate("DROP TABLE blocks;", new Object[0]);
            manager.save();
            return true;
        }
        catch (IOException | SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public CompletableFuture<byte[]> load(ChunkPosition pos) {
        return CompletableFuture.supplyAsync(() -> this.helper.querySingleResultBytes("SELECT data FROM data WHERE x=? AND z=? AND world=?", pos.getX(), pos.getZ(), pos.getWorld().getName()), this.exec);
    }

    @Override
    public CompletableFuture<Void> save(ChunkPosition pos, byte[] data2) {
        return CompletableFuture.runAsync(() -> this.helper.executeUpdate("REPLACE INTO data VALUES (?, ?, ?, ?);", pos.getX(), pos.getZ(), pos.getWorld().getName(), data2), this.exec);
    }

    @Override
    public CompletableFuture<Void> remove(ChunkPosition pos) {
        return CompletableFuture.runAsync(() -> this.helper.executeUpdate("DELETE FROM data WHERE x=? AND z=? AND world=?;", pos.getX(), pos.getZ(), pos.getWorld().getName()), this.exec);
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        return CompletableFuture.runAsync(this.helper::commit, this.exec);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            this.saveAll();
            this.helper.close();
        }, this.exec);
    }

    @Override
    public CompletableFuture<Map<ChunkPosition, byte[]>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            SQLHelper.Results results = this.helper.queryResults("SELECT * FROM data;", new Object[0]);
            HashMap map = new HashMap();
            results.forEach(r -> {
                int x = (Integer)r.get(1);
                int z = (Integer)r.get(2);
                String world = r.getString(3);
                ChunkPosition pos = new ChunkPosition(x, z, world);
                byte[] data2 = r.getBytes(4);
                map.put(pos, data2);
            });
            return map;
        }, this.exec);
    }
}

