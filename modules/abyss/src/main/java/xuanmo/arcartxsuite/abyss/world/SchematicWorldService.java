package xuanmo.arcartxsuite.abyss.world;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;

/**
 * Schematic 世界源：虚空世界 + WorldEdit/FAWE 粘贴。
 * <p>
 * WorldEdit 是可选依赖（compileOnly + external-softdepends），未安装时
 * schematic 地牢直接报清晰错误而不影响其他地牢。
 * 通过反射持有 WE 类型句柄？——不，直接编译期引用 WE API（compileOnly jar 已在 libs），
 * 但所有 WE 调用集中在 {@link WorldEditAdapter}，未安装时该类不会被触碰。
 */
public final class SchematicWorldService {

    private final Plugin plugin;
    private final Logger logger;

    public SchematicWorldService(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public boolean isWorldEditAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
            || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
    }

    /** 创建虚空世界（schematic 容器）。 */
    public World createVoidWorld(String name, AbyssModuleConfiguration.AbyssSection cfg,
                                 @Nullable NmsWorldLoader nmsLoader) {
        WorldCreator creator = new WorldCreator(name);
        // 用 NORMAL + 自定义虚空生成器，避免 FLAT 类型在 Mohist/部分服务端上
        // 序列化空的平坦层配置导致 "No key layers in MapLike[{}]" 报错
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);
        creator.generator(new VoidGenerator());

        World world = nmsLoader != null
            ? nmsLoader.loadWorld(creator)
            : creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("虚空世界创建失败: " + name);
        }
        return world;
    }

    /**
     * 创建 schematic 会话：立即在主线程粘贴到世界原点。
     */
    public Session createSession(File schematicFile, World world) {
        return createSession(schematicFile, world, 0, 64, 0);
    }

    /**
     * 创建 schematic 会话：粘贴到指定锚点（floors pool 模式房间拼接用）。
     */
    public Session createSession(File schematicFile, World world, int x, int y, int z) {
        if (!isWorldEditAvailable()) {
            throw new IllegalStateException(
                "schematic 地牢需要 WorldEdit/FAWE，但未检测到（请安装插件）");
        }
        WorldEditAdapter.paste(schematicFile, world, x, y, z, logger);
        return new Session(world);
    }

    /**
     * 单次粘贴（不建会话）：floors pool 模式房间逐个拼接用。
     * 必须主线程调用（FAWE 内部异步），返回是否成功。
     */
    public boolean paste(File schematicFile, World world, int x, int y, int z) {
        if (!isWorldEditAvailable() || world == null) return false;
        try {
            WorldEditAdapter.paste(schematicFile, world, x, y, z, logger);
            return true;
        } catch (Throwable t) {
            logger.warning("[abyss] 房间粘贴失败 " + schematicFile.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /** 虚空生成器：全空气区块。 */
    public static final class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(org.bukkit.World world, java.util.Random random,
                                           int x, int z, BiomeGrid biome) {
            return createChunkData(world); // 全空气
        }
    }

    /** schematic 会话（WE EditSession 生命周期占位；回收由世界卸载统一完成）。 */
    public static final class Session implements AutoCloseable {
        private final World world;
        Session(World world) { this.world = world; }
        public World world() { return world; }
        @Override public void close() { /* WE EditSession 已在 paste 内关闭 */ }
    }

    /**
     * WorldEdit API 适配器——隔离 WE 类型引用。
     * 仅当 FAWE/WorldEdit 已启用时才应被调用。
     */
    static final class WorldEditAdapter {

        static void paste(File schematicFile, World bukkitWorld, int x, int y, int z,
                          Logger logger) {
            try {
                com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format =
                    com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(schematicFile);
                if (format == null) {
                    // 回退：按扩展名取已注册格式
                    String n = schematicFile.getName().toLowerCase();
                    format = n.endsWith(".schem")
                        ? com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat.SPONGE_SCHEMATIC
                        : com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat.MCEDIT_SCHEMATIC;
                }
                com.sk89q.worldedit.extent.clipboard.Clipboard clipboard;
                try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader =
                         format.getReader(new FileInputStream(schematicFile))) {
                    clipboard = reader.read();
                }

                com.sk89q.worldedit.world.World weWorld =
                    new com.sk89q.worldedit.bukkit.BukkitWorld(bukkitWorld);
                try (com.sk89q.worldedit.EditSession editSession =
                         com.sk89q.worldedit.WorldEdit.getInstance()
                             .newEditSessionBuilder().world(weWorld).build()) {
                    com.sk89q.worldedit.function.operation.Operation op =
                        new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(com.sk89q.worldedit.math.BlockVector3.at(x, y, z))
                            .ignoreAirBlocks(false)
                            .build();
                    com.sk89q.worldedit.function.operation.Operations.complete(op);
                }
                logger.info("[abyss] schematic 粘贴完成: " + schematicFile.getName()
                    + " -> " + bukkitWorld.getName() + " @(" + x + "," + y + "," + z + ")");
            } catch (Throwable t) {
                throw new RuntimeException("schematic 粘贴失败 " + schematicFile.getName()
                    + ": " + t.getMessage(), t);
            }
        }
    }
}
