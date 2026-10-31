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
    private final xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler;
    private final Map<UUID,Integer> removalAttempts=new ConcurrentHashMap<>();
    private final java.util.Set<xuanmo.arcartxsuite.api.scheduler.ScheduledTask> cleanupTasks=ConcurrentHashMap.newKeySet();
    private volatile boolean closing;
    private final java.util.concurrent.atomic.AtomicLong preloadGeneration=new java.util.concurrent.atomic.AtomicLong();
    private final Map<UUID, CompletableFuture<Void>> worldWrites = new ConcurrentHashMap<>();
    private volatile NmsWorldLoader nmsLoader;
    private volatile SchematicWorldService schematicService;

    /** 实例id → 世界目录（回收时删目录用）。 */
    private final Map<UUID, File> instanceWorldDirs = new ConcurrentHashMap<>();
    /** 实例id → 世界名（预热世界名与实例 id 不相关，需显式记录）。 */
    private final Map<UUID, String> instanceWorldNames = new ConcurrentHashMap<>();
    /** 实例id → schematic 会话（回收用）。 */
    private final Map<UUID, SchematicWorldService.Session> schematicSessions = new ConcurrentHashMap<>();
    /** 预热池：地牢id → 已建好待接管的世界。 */
    private final Map<String, java.util.Deque<Prepared>> preloadPool = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger preloadSeq = new java.util.concurrent.atomic.AtomicInteger();

    /** 预热完成的世界（世界已加载+配置，等待实例接管）。 */
    public record Prepared(String worldName, File dir, World world,
                           @Nullable SchematicWorldService.Session schematicSession) { }

    public AbyssWorldPool(Plugin plugin, AbyssModuleConfiguration.AbyssSection cfg, Logger logger) {
        this(plugin,cfg,logger,null);
    }

    public AbyssWorldPool(Plugin plugin,AbyssModuleConfiguration.AbyssSection cfg,Logger logger,
            xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.logger = logger;
        this.scheduler=scheduler;
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

    /** 实例的世界目录（设计模式世界回写用；未绑定返回 null）。 */
    public @Nullable File worldDirOf(UUID instanceId) {
        return instanceWorldDirs.get(instanceId);
    }

    /** A persisted world must be closed before copying its region files. Main thread only. */
    public boolean suspendForSnapshot(UUID instanceId) {
        String name = instanceWorldNames.get(instanceId);
        World world = name != null ? Bukkit.getWorld(name) : null;
        if (world == null || !world.getPlayers().isEmpty()) return false;
        SchematicWorldService.Session session = schematicSessions.remove(instanceId);
        if (session != null) session.close();
        return Bukkit.unloadWorld(world, true);
    }

    public World resumeAfterSnapshot(UUID instanceId, AbyssTemplate template) {
        String name = instanceWorldNames.get(instanceId);
        if (name == null) throw new IllegalStateException("设计实例已被回收");
        return loadAndConfigure(name, template);
    }

    /** 在世界池 IO 线程跑任务（设计模式世界回拷用）。 */
    public CompletableFuture<Void> runIo(UUID instanceId, Runnable r) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(r, ioExecutor);
        worldWrites.put(instanceId, future);
        return future;
    }

    /**
     * 申请世界：优先取预热池，否则异步复制 → 主线程加载 + 配置。
     *
     * @return CompletableFuture&lt;World&gt;（主线程回调后获得），失败 exceptional
     */
    public CompletableFuture<World> acquireWorld(AbyssTemplate template, UUID instanceId) {
        // 预热接管：世界已建好，跳过整个创建路径
        Prepared pre = takePrepared(template.id());
        if (pre != null) {
            CompletableFuture<World> future = new CompletableFuture<>();
            runSync(() -> {
                bind(instanceId, pre);
                future.complete(pre.world());
            });
            refillPreload(template);
            return future;
        }
        return materialize(template).thenApply(mat -> {
            bind(instanceId, mat);
            return mat.world();
        });
    }
    public CompletableFuture<World> acquireDesignWorld(AbyssTemplate template, UUID instanceId) {
        // Authoring must not consume the worlds reserved for players in a strict preload pool.
        return materialize(template).thenApply(world -> { bind(instanceId, world); return world.world(); });
    }

    /** 把已建好的世界绑定到实例（回收时按此卸载/删目录/关会话）。 */
    private void bind(UUID instanceId, Prepared p) {
        instanceWorldDirs.put(instanceId, p.dir());
        instanceWorldNames.put(instanceId, p.worldName());
        if (p.schematicSession() != null) {
            schematicSessions.put(instanceId, p.schematicSession());
        }
    }

    /**
     * 现场创建世界（复制 → 加载 → 配置），返回 Prepared 供绑定或入池。
     */
    private CompletableFuture<Prepared> materialize(AbyssTemplate template) {
        return materialize(template, worldName(UUID.randomUUID()));
    }

    private CompletableFuture<Prepared> materialize(AbyssTemplate template, String name) {
        CompletableFuture<Prepared> future = new CompletableFuture<>();
        File dir = worldDir(name);

        AbyssTemplate.WorldSource src = template.worldSource();
        if (src.kind() == AbyssTemplate.WorldSource.Kind.VOID) {
            runSync(() -> {
                try {
                    World w = schematicService.createVoidWorld(name, cfg, nmsLoader);
                    configureWorld(w, template);
                    future.complete(new Prepared(name, dir, w, null));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
        if (src.kind() == AbyssTemplate.WorldSource.Kind.SCHEMATIC) {
            runSync(() -> {
                try {
                    World w = schematicService.createVoidWorld(name, cfg, nmsLoader);
                    configureWorld(w, template);
                    var session = schematicService.createSession(src.file(), w);
                    future.complete(new Prepared(name, dir, w, session));
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
                        World w = loadAndConfigure(name, template);
                        future.complete(new Prepared(name, dir, w, null));
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

    // ═══ 预热池 ═══

    /** 取出预热世界（无则 null）。 */
    public @Nullable Prepared takePrepared(String dungeonId) {
        var q = preloadPool.get(dungeonId);
        if (q == null) return null;
        synchronized (q) {
            return q.pollFirst();
        }
    }

    /** 池内剩余数量。 */
    public int preparedCount(String dungeonId) {
        var q = preloadPool.get(dungeonId);
        return q == null ? 0 : q.size();
    }

    /** 启动/重载预热：按配置数量补齐池子。 */
    public void initPreload(Map<String, AbyssTemplate> templates,
                            AbyssModuleConfiguration.PreloadSection preload) {
        if (!preload.enabled()) return;
        for (var e : preload.dungeons().entrySet()) {
            AbyssTemplate t = templates.get(e.getKey());
            if (t == null) {
                logger.warning("[abyss] 预热配置的地牢不存在: " + e.getKey());
                continue;
            }
            preloadPool.computeIfAbsent(e.getKey(), k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
            for (int i = e.getValue(); i > 0; i--) {
                warmOne(t);
            }
        }
    }

    /** 预热池消耗后补位。 */
    private void refillPreload(AbyssTemplate template) {
        var q = preloadPool.get(template.id());
        if (q == null) return; // 未配置预热
        // 目标数量 = 配置值；不足则补一个
        warmOne(template);
    }

    /** 预热一个实例世界（预热名 axs_abyss_pre_<地牢>_<seq>，worldPrefix 前缀命中残留清理）。 */
    private void warmOne(AbyssTemplate template) {
        long generation=preloadGeneration.get();
        String name = cfg.worldPrefix() + "pre_" + template.id() + "_" + UUID.randomUUID().toString().substring(0,8);
        materialize(template, name).whenComplete((pre, err) -> {
            if (err != null) {
                logger.warning("[abyss] 预热失败 " + template.id() + ": " + err.getMessage());
                return;
            }
            acceptPrepared(template,pre,generation);
            logger.info("[abyss] 预热就绪: " + name);
        });
    }

    void acceptPrepared(AbyssTemplate template,Prepared prepared,long generation) {
        if(closing || generation!=preloadGeneration.get()){disposePrepared(prepared);return;}
        var queue=preloadPool.computeIfAbsent(template.id(),k->new java.util.concurrent.ConcurrentLinkedDeque<>());
        synchronized(queue){queue.addLast(prepared);}
    }

    private void disposePrepared(Prepared prepared) {
        runSync(()->{
            try {
                if(prepared.schematicSession()!=null)prepared.schematicSession().close();
                World world=Bukkit.getWorld(prepared.worldName());
                if(world!=null && !unloadForDeletion(world)) {
                    logger.warning("[abyss] 过期预热世界仍在使用，保留目录: "+prepared.worldName());return;
                }
                deleteLater(prepared.dir());
            }catch(Exception failure){logger.warning("[abyss] 过期预热世界回收失败，保留目录: "+prepared.worldName()+": "+failure.getMessage());}
        });
    }

    /** 关闭时卸载全部预热世界。 */
    public void releasePreload() {
        preloadGeneration.incrementAndGet();
        for (var q : preloadPool.values()) {
            Prepared p;
            while ((p = q.pollFirst()) != null) {
                disposePrepared(p);
            }
        }
        preloadPool.clear();
    }

    /** 主线程内加载并配置世界。 */
    private World loadAndConfigure(String worldName, AbyssTemplate template) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);

        World world = nmsLoader != null
            ? nmsLoader.loadWorld(creator)
            : creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("世界加载返回 null: " + worldName);
        }
        configureWorld(world, template);
        return world;
    }

    /** 副本世界统一配置（基础规则 + 模板 gamerules 覆盖）。 */
    public void configureWorld(World world, @Nullable AbyssTemplate template) {
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
        // 模板 gamerules 覆盖（支持布尔/整数规则，未知规则名跳过）
        if (template != null) {
            applyTemplateGamerules(world, template.worldRules().gamerules());
        }
    }

    /** 应用模板 gamerules 覆盖：值先按布尔解析，失败按整数解析。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyTemplateGamerules(World world, Map<String, String> rules) {
        for (var e : rules.entrySet()) {
            GameRule<?> rule = GameRule.getByName(e.getKey());
            if (rule == null || e.getValue() == null) {
                logger.fine("[abyss] 未知 gamerule: " + e.getKey());
                continue;
            }
            try {
                String v = e.getValue().trim();
                if (rule.getType() == Boolean.class) {
                    setRule(world, (GameRule<Boolean>) rule, Boolean.parseBoolean(v));
                } else if (rule.getType() == Integer.class) {
                    world.setGameRule((GameRule) rule, Integer.parseInt(v));
                }
            } catch (Throwable t) {
                logger.fine("[abyss] gamerule 设置失败 " + e.getKey() + "=" + e.getValue());
            }
        }
    }

    private static void setRule(World w, GameRule<Boolean> rule, boolean v) {
        try { w.setGameRule(rule, v); } catch (Throwable ignored) { }
    }

    /**
     * 回收世界：卸载 + 删目录 + 清 schematic 会话。必须在主线程调用。
     */
    public void releaseWorld(UUID instanceId) {
        String name = instanceWorldNames.get(instanceId);
        if (name == null) name = worldName(instanceId);
        World world = Bukkit.getWorld(name);
        if(world!=null && !world.getPlayers().isEmpty()) {
            logger.warning("[abyss] 世界仍有玩家，保留登记与目录: "+name);retryRemoval(instanceId);return;
        }
        SchematicWorldService.Session editing=schematicSessions.get(instanceId);
        if(editing!=null)try{editing.close();schematicSessions.remove(instanceId,editing);}
        catch(Exception failure){logger.warning("[abyss] 世界编辑会话未关闭，保留目录: "+name);retryRemoval(instanceId);return;}
        if (world != null) {
            try {
                // 先清空实体再卸载（避免残留）
                world.getEntities().forEach(e -> {
                    if (!(e instanceof org.bukkit.entity.Player)) e.remove();
                });
                if (!unloadForDeletion(world)) {
                    logger.warning("[abyss] 世界卸载被拒绝，保留登记与目录: " + name);
                    retryRemoval(instanceId);
                    return;
                }
            } catch (Throwable t) {
                logger.warning("[abyss] 卸载世界失败 " + name + ": " + t.getMessage());
                retryRemoval(instanceId);
                return;
            }
        }
        instanceWorldNames.remove(instanceId);
        removalAttempts.remove(instanceId);
        SchematicWorldService.Session s = schematicSessions.remove(instanceId);
        if (s != null) {
            try { s.close(); } catch (Throwable ignored) { }
        }
        File dir = instanceWorldDirs.remove(instanceId);
        if (dir == null) dir = worldDir(name);
        final File toDelete = dir;
        // 删除目录异步化（大目录删除也耗时）
        CompletableFuture<Void> pending = worldWrites.remove(instanceId);
        if (pending == null) deleteLater(toDelete);
        else pending.whenComplete((v, error) -> deleteLater(toDelete));
    }

    /** 启动时扫描并回收残留世界（上次非正常关服的副本世界）。 */
    public int cleanupOrphanWorlds() {
        String prefix = cfg.worldPrefix();
        // 前缀为空/过短时 startsWith 命中范围过大，有整容器误删风险——拒绝执行并告警
        if (prefix == null || prefix.length() < 8) {
            logger.warning("[abyss] world-prefix 无效或过短（\"" + prefix
                + "\"，需 >=8 字符），跳过孤儿世界清理以防误删");
            return 0;
        }
        File container = Bukkit.getWorldContainer();
        File[] orphans = container.listFiles(f ->
            f.isDirectory() && f.getName().startsWith(prefix));
        if (orphans == null) return 0;
        int n = 0;
        for (File dir : orphans) {
            World w = Bukkit.getWorld(dir.getName());
            if (w != null) {
                try {
                    if (!unloadForDeletion(w)) {
                        logger.warning("[abyss] 残留世界仍在使用，保留目录: " + dir.getName());
                        continue;
                    }
                } catch (Throwable error) {
                    logger.warning("[abyss] 残留世界卸载失败，保留目录: " + dir.getName());
                    continue;
                }
            }
            deleteLater(dir);
            n++;
        }
        return n;
    }

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else if(scheduler!=null) {
            scheduler.scheduleOnce(plugin,r,0L);
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    private void deleteLater(File directory) {
        try{ioExecutor.submit(()->WorldCopyUtil.deleteRecursively(directory));}
        catch(java.util.concurrent.RejectedExecutionException stopped){logger.warning("[abyss] IO已停止，目录留待后续安全回收: "+directory.getName());}
    }

    static boolean unloadForDeletion(World world) {
        return world.getPlayers().isEmpty() && Bukkit.unloadWorld(world, false);
    }

    private void retryRemoval(UUID id) {
        if(closing || scheduler==null || removalAttempts.merge(id,1,Integer::sum)>3)return;
        xuanmo.arcartxsuite.api.scheduler.ScheduledTask[] task=new xuanmo.arcartxsuite.api.scheduler.ScheduledTask[1];
        task[0]=scheduler.scheduleOnce(plugin,()->{
            if(task[0]!=null)cleanupTasks.remove(task[0]);
            if(!closing)releaseWorld(id);
        },40L);
        if(task[0]!=null)cleanupTasks.add(task[0]);
    }

    public void shutdown() {
        closing=true;
        for(var task:cleanupTasks)try{task.cancel();}catch(RuntimeException ignored){}
        cleanupTasks.clear();
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
