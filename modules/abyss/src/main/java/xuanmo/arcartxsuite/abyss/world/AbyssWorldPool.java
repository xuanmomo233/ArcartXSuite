package xuanmo.arcartxsuite.abyss.world;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/**
 * 世界池：每个实例复制一份模板世界 → 加载 → 配置 → 用完卸载删目录。
 * <p>
 * 文件复制走独立 IO 线程（不阻塞主线程）；世界加载/卸载回主线程。
 * FOLDER 来源直接复制；SCHEMATIC 来源走 {@link SchematicWorldService}
 * 虚空世界 + WE 粘贴（FAWE/WorldEdit 可选依赖，缺失时降级失败）。
 */
public final class AbyssWorldPool {

    private final Plugin plugin;
    private final AbyssModuleConfiguration.AbyssSection cfg;
    private final Logger logger;
    private final ExecutorService ioExecutor;
    private volatile NmsWorldLoader nmsLoader;
    private volatile SchematicWorldService schematicService;

    /** 实例id → 世界目录（回收时删目录用）。 */
    private final Map<UUID, File> instanceWorldDirs = new ConcurrentHashMap<>();
    /** 实例id → schematic 会话（回收用）。 */
    private final Map<UUID, SchematicWorldService.Session> schematicSessions = new ConcurrentHashMap<>();

    public AbyssWorldPool(Plugin plugin, AbyssModuleConfiguration.AbyssSection cfg, Logger logger) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.logger = logger;
        this.ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Abyss-WorldIO");
            t.setDaemon(true);
            return t;
        });
        if (cfg.nmsFastLoad()) {
            this.nmsLoader = NmsWorldLoader.resolve(logger);
        }
        this.schematicService = new SchematicWorldService(plugin, logger);
    }

    /** 世界名（可预测前缀便于残留清理）。 */
    public String worldName(UUID instanceId) {
        return cfg.worldPrefix() + instanceId.toString().replace("-", "").substring(0, 12);
    }

    /** 服务器根目录下的世界目录。 */
    private File worldDir(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    /**
     * 申请世界：异步复制 → 主线程加载 + 配置。
     *
     * @return CompletableFuture&lt;World&gt;（主线程回调后获得），失败 exceptional
     */
    public CompletableFuture<World> acquireWorld(AbyssTemplate template, UUID instanceId) {
        CompletableFuture<World> future = new CompletableFuture<>();
        String name = worldName(instanceId);
        File dir = worldDir(name);

        AbyssTemplate.WorldSource src = template.worldSource();
        if (src.kind() == AbyssTemplate.WorldSource.Kind.VOID) {
            // 虚空世界：无需模板/粘贴
            runSync(() -> {
                try {
                    World w = schematicService.createVoidWorld(name, cfg, nmsLoader);
                    instanceWorldDirs.put(instanceId, dir);
                    future.complete(w);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
        if (src.kind() == AbyssTemplate.WorldSource.Kind.SCHEMATIC) {
            // schematic：建虚空世界 → WE 粘贴
            runSync(() -> {
                try {
                    World w = schematicService.createVoidWorld(name, cfg, nmsLoader);
                    schematicSessions.put(instanceId,
                        schematicService.createSession(src.file(), w));
                    instanceWorldDirs.put(instanceId, dir);
                    future.complete(w);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }

        // FOLDER：IO 线程复制 → 主线程加载
        if (!WorldCopyUtil.isWorldFolder(src.file())) {
            future.completeExceptionally(new IOException(
                "世界模板目录无效: " + src.file().getAbsolutePath()));
            return future;
        }

        Runnable copyTask = () -> {
            try {
                WorldCopyUtil.copyWorldFolder(src.file(), dir);
                runSync(() -> {
                    try {
                        World w = loadAndConfigure(name);
                        instanceWorldDirs.put(instanceId, dir);
                        future.complete(w);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (cfg.asyncWorldCopy()) {
            ioExecutor.submit(copyTask);
        } else {
            copyTask.run();
        }
        return future;
    }

    /** 主线程内加载并配置世界。 */
    private World loadAndConfigure(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);

        World world = nmsLoader != null
            ? nmsLoader.loadWorld(creator)
            : creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("世界加载返回 null: " + worldName);
        }
        configureWorld(world);
        return world;
    }

    /** 副本世界统一配置（QueDP 同款游戏规则）。 */
    public void configureWorld(World world) {
        world.setAutoSave(false);
        setRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        setRule(world, GameRule.DO_WEATHER_CYCLE, false);
        setRule(world, GameRule.DO_MOB_SPAWNING, false);
        setRule(world, GameRule.KEEP_INVENTORY, true);
        setRule(world, GameRule.MOB_GRIEFING, false);
        setRule(world, GameRule.DO_FIRE_TICK, false);
        setRule(world, GameRule.SHOW_DEATH_MESSAGES, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.HARD);
        try {
            world.setSpawnFlags(false, false); // 不生成生物/动物
        } catch (Throwable ignored) { }
    }

    private static void setRule(World w, GameRule<Boolean> rule, boolean v) {
        try { w.setGameRule(rule, v); } catch (Throwable ignored) { }
    }

    /**
     * 回收世界：卸载 + 删目录 + 清 schematic 会话。必须在主线程调用。
     */
    public void releaseWorld(UUID instanceId) {
        String name = worldName(instanceId);
        World world = Bukkit.getWorld(name);
        if (world != null) {
            try {
                // 先清空实体再卸载（避免残留）
                world.getEntities().forEach(e -> {
                    if (!(e instanceof org.bukkit.entity.Player)) e.remove();
                });
                Bukkit.unloadWorld(world, false);
            } catch (Throwable t) {
                logger.warning("[abyss] 卸载世界失败 " + name + ": " + t.getMessage());
            }
        }
        SchematicWorldService.Session s = schematicSessions.remove(instanceId);
        if (s != null) {
            try { s.close(); } catch (Throwable ignored) { }
        }
        File dir = instanceWorldDirs.remove(instanceId);
        if (dir == null) dir = worldDir(name);
        final File toDelete = dir;
        // 删除目录异步化（大目录删除也耗时）
        ioExecutor.submit(() -> WorldCopyUtil.deleteRecursively(toDelete));
    }

    /** 启动时扫描并回收残留世界（上次非正常关服的副本世界）。 */
    public int cleanupOrphanWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] orphans = container.listFiles(f ->
            f.isDirectory() && f.getName().startsWith(cfg.worldPrefix()));
        if (orphans == null) return 0;
        int n = 0;
        for (File dir : orphans) {
            World w = Bukkit.getWorld(dir.getName());
            if (w != null) {
                try { Bukkit.unloadWorld(w, false); } catch (Throwable ignored) { }
            }
            ioExecutor.submit(() -> WorldCopyUtil.deleteRecursively(dir));
            n++;
        }
        return n;
    }

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
