/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.event.world.ChunkLoadEvent
 *  org.bukkit.event.world.ChunkUnloadEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.BlockDataContainerKt;
import ink.ptms.chemdah.library.redlib.BlockDataListener;
import ink.ptms.chemdah.library.redlib.BlockPosition;
import ink.ptms.chemdah.library.redlib.ChunkPosition;
import ink.ptms.chemdah.library.redlib.DataBlock;
import ink.ptms.chemdah.library.redlib.EventListener;
import ink.ptms.chemdah.library.redlib.backend.BlockDataBackend;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockDataManager {
    private final BlockDataBackend backend;
    private final Plugin plugin;
    private BlockDataListener listener;
    private final Map<ChunkPosition, Map<BlockPosition, DataBlock>> dataBlocks = new ConcurrentHashMap<ChunkPosition, Map<BlockPosition, DataBlock>>();
    private final Map<ChunkPosition, CompletableFuture<Void>> loading = new ConcurrentHashMap<ChunkPosition, CompletableFuture<Void>>();
    private final Set<ChunkPosition> modified = Collections.synchronizedSet(new HashSet());

    public static BlockDataManager createPDC(boolean autoLoad, boolean events) {
        JavaPlugin plugin2 = JavaPlugin.getProvidingPlugin(BlockDataManager.class);
        BlockDataBackend backend = BlockDataBackend.pdc((Plugin)plugin2);
        return new BlockDataManager(backend, autoLoad, events);
    }

    public static BlockDataManager createSQLite(Path path, boolean autoLoad, boolean events) {
        BlockDataBackend backend = BlockDataBackend.sqlite(path);
        return new BlockDataManager(backend, autoLoad, events);
    }

    public static BlockDataManager createAuto(Path path, boolean autoLoad, boolean events) {
        JavaPlugin plugin2 = JavaPlugin.getProvidingPlugin(BlockDataManager.class);
        BlockDataBackend backend = MinecraftVersion.INSTANCE.getMajorLegacy() >= 11400 ? BlockDataBackend.pdc((Plugin)plugin2) : BlockDataBackend.sqlite(path);
        return new BlockDataManager(backend, autoLoad, events);
    }

    public CompletableFuture<DataBlock> getDataBlockAsync(Block block, boolean create) {
        ChunkPosition pos = new ChunkPosition(block.getChunk());
        return this.load(pos).thenApply(n -> {
            BlockPosition bPos = new BlockPosition(block);
            DataBlock db = this.dataBlocks.get(pos).get(bPos);
            if (db != null) {
                return db;
            }
            if (!create) {
                return null;
            }
            db = new DataBlock(new HashMap<String, Object>(), bPos, block.getWorld().getName(), this);
            this.dataBlocks.get(pos).put(bPos, db);
            this.setModified(pos);
            return db;
        });
    }

    private BlockDataManager(BlockDataBackend backend, boolean autoLoad, boolean events) {
        this.plugin = JavaPlugin.getProvidingPlugin(BlockDataManager.class);
        this.backend = backend;
        new EventListener<ChunkUnloadEvent>(this.plugin, ChunkUnloadEvent.class, e -> this.unload(new ChunkPosition(e.getChunk())));
        if (autoLoad) {
            new EventListener<ChunkLoadEvent>(this.plugin, ChunkLoadEvent.class, e -> this.load(new ChunkPosition(e.getChunk())));
        }
        if (events) {
            this.listener = new BlockDataListener(this, this.plugin);
        }
    }

    public boolean migrate() {
        return this.backend.attemptMigration(this);
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    public void save() {
        ArrayList<ChunkPosition> modified = new ArrayList<ChunkPosition>(this.modified);
        modified.forEach(c -> this.save((ChunkPosition)c, true));
        this.modified.clear();
        this.unwrap(this.backend.saveAll());
    }

    public void saveAndClose() {
        this.save();
        this.unwrap(this.backend.close());
    }

    protected void setModified(ChunkPosition pos) {
        this.modified.add(pos);
    }

    public DataBlock getDataBlock(Block block) {
        return this.getDataBlock(block, true);
    }

    private CompletableFuture<Void> save(ChunkPosition pos, boolean force) {
        if (!force && !this.modified.contains(pos)) {
            return CompletableFuture.completedFuture(null);
        }
        this.modified.remove(pos);
        HashMap map = new HashMap();
        Map<BlockPosition, DataBlock> blocks2 = this.dataBlocks.get(pos);
        if (blocks2 == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (blocks2.size() == 0) {
            this.dataBlocks.remove(pos);
            return this.backend.remove(pos);
        }
        blocks2.forEach((k, v) -> map.put(k.toString(), v.data));
        return this.backend.save(pos, BlockDataContainerKt.serializeToByteArray(map, true));
    }

    private CompletableFuture<Void> unload(ChunkPosition pos) {
        CompletableFuture<Void> load2 = this.loading.remove(pos);
        if (load2 != null && !load2.isDone()) {
            load2.cancel(true);
            this.dataBlocks.remove(pos);
            return CompletableFuture.completedFuture(null);
        }
        return this.save(pos, false).thenRun(() -> this.dataBlocks.remove(pos));
    }

    public void remove(DataBlock db) {
        ChunkPosition cpos = db.getChunkPosition();
        this.setModified(cpos);
        Optional.ofNullable(this.dataBlocks.get(cpos)).ifPresent(m -> m.remove(db.getBlockPosition()));
    }

    public CompletableFuture<DataBlock> moveAsync(DataBlock db, Block location) {
        this.remove(db);
        ChunkPosition cpos = new ChunkPosition(location);
        this.modified.add(cpos);
        return this.getDataBlockAsync(location, true).thenApply(b -> {
            b.data = db.data;
            return b;
        });
    }

    public DataBlock move(DataBlock db, Block block) {
        return this.unwrap(this.moveAsync(db, block));
    }

    public CompletableFuture<Void> loadAsync(World world, int cx, int cz) {
        return this.load(new ChunkPosition(cx, cz, world.getName()));
    }

    public void load(World world, int cx, int cz) {
        this.unwrap(this.loadAsync(world, cx, cz));
    }

    public CompletableFuture<Void> unloadAsync(World world, int cx, int cz) {
        return this.unload(new ChunkPosition(cx, cz, world.getName()));
    }

    public void unload(World world, int cx, int cz) {
        this.unwrap(this.unloadAsync(world, cx, cz));
    }

    public Collection<DataBlock> getLoaded(World world, int cx, int cz) {
        ChunkPosition pos = new ChunkPosition(cx, cz, world.getName());
        return Optional.ofNullable(this.dataBlocks.get(pos)).map(Map::values).orElseGet(ArrayList::new);
    }

    public boolean isLoaded(World world, int cx, int cz) {
        ChunkPosition pos = new ChunkPosition(cx, cz, world.getName());
        return this.dataBlocks.containsKey(pos);
    }

    private CompletableFuture<Void> load(ChunkPosition pos) {
        if (this.dataBlocks.containsKey(pos)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> load2 = this.loading.get(pos);
        if (load2 != null && !load2.isDone()) {
            return load2;
        }
        this.dataBlocks.put(pos, new HashMap());
        load2 = this.backend.load(pos).thenApply(s -> {
            if (s == null) {
                this.loading.remove(pos);
                return null;
            }
            Map<String, Object> map = BlockDataContainerKt.deserializeToMap(s, true);
            map.keySet().forEach(k -> this.load((String)k, (Map)map.get(k), pos));
            this.loading.remove(pos);
            return null;
        });
        this.loading.put(pos, (CompletableFuture<Void>)load2);
        return load2;
    }

    private void load(String key, Map<String, Object> map, ChunkPosition pos) {
        String[] split = key.split(" ");
        int x = Integer.parseInt(split[0]);
        int y = Integer.parseInt(split[1]);
        int z = Integer.parseInt(split[2]);
        BlockPosition bPos = new BlockPosition(x, y, z);
        DataBlock db = new DataBlock(map, bPos, pos.getWorldName(), this);
        this.dataBlocks.get(pos).put(bPos, db);
    }

    public DataBlock getDataBlock(Block block, boolean create) {
        return this.unwrap(this.getDataBlockAsync(block, create));
    }

    public CompletableFuture<Void> loadAll() {
        this.save();
        this.loading.values().forEach(f -> f.cancel(true));
        this.loading.clear();
        this.dataBlocks.clear();
        return this.backend.loadAll().thenApply(chunkMap -> {
            chunkMap.forEach((cPos, data2) -> {
                Map<String, Object> chunkData = BlockDataContainerKt.deserializeToMap(data2, true);
                this.dataBlocks.computeIfAbsent((ChunkPosition)cPos, k -> new HashMap());
                chunkData.keySet().forEach(bPos -> this.load((String)bPos, (Map)chunkData.get(bPos), (ChunkPosition)cPos));
            });
            return null;
        });
    }

    public Set<DataBlock> getAllLoaded() {
        return this.dataBlocks.values().stream().flatMap(m -> m.values().stream()).collect(Collectors.toSet());
    }

    private <T> T unwrap(CompletableFuture<T> future) {
        try {
            return future.get();
        }
        catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BlockDataListener getListener() {
        return this.listener;
    }
}

