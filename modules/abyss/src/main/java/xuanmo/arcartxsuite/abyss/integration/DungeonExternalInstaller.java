package xuanmo.arcartxsuite.abyss.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.links.AbyssLinks;

/**
 * 地牢 external/ 配套文件自动部署器。
 *
 * <p>地牢模板目录下的 {@code external/} 是本副本的外部系统依赖清单
 * （Chemdah 会话/任务、questgps 呈现层等）。这些文件必须落在各自插件的
 * 配置目录才生效——手动拷贝是示例落地的最大坑位，故模块在
 * {@code reloadTemplates()} 时自动部署：
 *
 * <pre>
 * external/chemdah/conversation/*.yml → plugins/Chemdah/core/conversation/
 * external/chemdah/quest/*.yml        → plugins/Chemdah/core/quest/
 * external/questgps/*.yml             → data/questgps/quests/
 * </pre>
 *
 * <p>规则：
 * <ul>
 *   <li>目标插件/模块缺席 → 整组跳过（副本其余玩法不受影响）</li>
 *   <li>目标文件已存在 → 跳过（不覆盖管理员改动）</li>
 *   <li>写入成功后触发目标端重载：Chemdah 控制台 {@code chemdah reload}；
 *       questgps 走 {@code ModuleAdminCapability.reloadModule}</li>
 * </ul>
 */
public final class DungeonExternalInstaller {

    private final Logger logger;
    /** data/abyss（dataFolder）；questgps 目标按兄弟目录解析。 */
    private final File dataFolder;
    private final Supplier<AbyssLinks> linksSupplier;

    public DungeonExternalInstaller(File dataFolder, Supplier<AbyssLinks> linksSupplier,
                                    Logger logger) {
        this.dataFolder = dataFolder;
        this.linksSupplier = linksSupplier;
        this.logger = logger;
    }

    /** 扫描全部地牢模板的 external/ 并部署（start/reloadTemplates 时调用）。 */
    public void installAll() {
        File abysses = new File(dataFolder, "abysses");
        File[] dirs = abysses.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            try {
                installDungeon(dir);
            } catch (Throwable t) {
                logger.warning("[abyss] external 部署失败 " + dir.getName() + ": " + t.getMessage());
            }
        }
    }

    private void installDungeon(File dir) {
        File external = new File(dir, "external");
        if (!external.isDirectory()) return;
        String id = dir.getName();

        int chemdahCopied = 0;
        int questgpsCopied = 0;

        // Chemdah：plugins/Chemdah/core/{conversation,quest}/
        org.bukkit.plugin.Plugin chemdahPlugin = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdahPlugin != null) {
            File chemdahRoot = chemdahPlugin.getDataFolder();
            chemdahCopied += copyMissing(
                new File(external, "chemdah/conversation"),
                new File(chemdahRoot, "core/conversation"), id);
            chemdahCopied += copyMissing(
                new File(external, "chemdah/quest"),
                new File(chemdahRoot, "core/quest"), id);
        }

        // questgps：data/questgps/quests/（abyss data 目录的兄弟模块目录）
        File questgpsData = new File(dataFolder.getParentFile(), "questgps");
        if (questgpsData.isDirectory() || questgpsPresent()) {
            File questsDir = new File(questgpsData, "quests");
            questgpsCopied += copyMissing(new File(external, "questgps"), questsDir, id);
        }

        // 部署后触发目标端重载，让文件立刻生效
        if (chemdahCopied > 0) {
            dispatchConsole("chemdah reload");
            logger.info("[abyss] 已部署 " + id + " 的 Chemdah 配套 x" + chemdahCopied + " 并触发 chemdah reload");
        }
        if (questgpsCopied > 0) {
            reloadModule("questgps");
            logger.info("[abyss] 已部署 " + id + " 的 questgps 配套 x" + questgpsCopied + " 并重载模块");
        }
    }

    /** 复制源目录内全部 .yml 到目标目录（已存在跳过）。返回新写入数。 */
    private int copyMissing(File srcDir, File dstDir, String dungeonId) {
        File[] src = srcDir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (src == null || src.length == 0) return 0;
        int copied = 0;
        for (File f : src) {
            File dst = new File(dstDir, f.getName());
            if (dst.exists()) continue;
            try {
                Files.createDirectories(dstDir.toPath());
                Files.copy(f.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                copied++;
            } catch (IOException e) {
                logger.warning("[abyss] external 拷贝失败 " + dungeonId + "/" + f.getName()
                    + " → " + dst.getPath() + ": " + e.getMessage());
            }
        }
        return copied;
    }

    private boolean questgpsPresent() {
        AbyssLinks links = linksSupplier != null ? linksSupplier.get() : null;
        if (links == null) return false;
        try {
            var cap = links.moduleAdmin();
            return cap != null && cap.findModule("questgps").isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private void reloadModule(String moduleId) {
        AbyssLinks links = linksSupplier != null ? linksSupplier.get() : null;
        if (links == null) return;
        try {
            var cap = links.moduleAdmin();
            if (cap != null) cap.reloadModule(moduleId).whenComplete((success, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(success)) logger.warning("[abyss] 重载模块 " + moduleId + " 失败");
            });
        } catch (Throwable t) {
            logger.warning("[abyss] 重载模块 " + moduleId + " 失败: " + t.getMessage());
        }
    }

    private void dispatchConsole(String command) {
        try {
            CommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, command);
        } catch (Throwable t) {
            logger.fine("[abyss] 控制台命令派发失败 " + command + ": " + t.getMessage());
        }
    }

    /** 调试用：列出某模板待部署文件清单（不落盘）。 */
    public List<String> pendingList(File templateDir) {
        List<String> out = new ArrayList<>();
        File external = new File(templateDir, "external");
        if (!external.isDirectory()) return out;
        collect(external, external.toPath(), out);
        return out;
    }

    private void collect(File dir, Path root, List<String> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, root, out);
            else if (f.isFile()) out.add(root.relativize(f.toPath()).toString());
        }
    }

    /** 供外部读取 abyss 数据根目录（测试/诊断用）。 */
    public File dataFolder() { return dataFolder; }
}
