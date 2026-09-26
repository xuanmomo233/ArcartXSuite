package xuanmo.arcartxsuite.abyss.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/** 世界文件夹复制/删除工具（排除运行期文件）。 */
public final class WorldCopyUtil {

    private static final String[] EXCLUDE = {
        "session.lock", "uid.dat", "playerdata", "stats",
        "advancements", "data/map_", "DIM-1/data/map_", "DIM1/data/map_"
    };

    private WorldCopyUtil() { }

    /** 异步复制模板世界目录到目标目录（排除运行期文件）。 */
    public static void copyWorldFolder(File source, File target) throws IOException {
        Path src = source.toPath();
        Path dst = target.toPath();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String rel = src.relativize(dir).toString().replace('\\', '/');
                if (!rel.isEmpty() && isExcluded(rel, true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = src.relativize(file).toString().replace('\\', '/');
                if (isExcluded(rel, false)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dst.resolve(src.relativize(file)),
                    StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isExcluded(String rel, boolean isDir) {
        if (rel.isEmpty()) return false;
        for (String ex : EXCLUDE) {
            if (isDir && ex.contains("/data/map_")) continue; // 目录排除只针对整目录项
            if (!isDir && !ex.contains("/")) {
                if (rel.equals(ex) || rel.endsWith("/" + ex)) return true;
            } else if (rel.startsWith(ex) || rel.equals(ex)) {
                return true;
            }
            // map_*.dat 前缀匹配
            if (!isDir && ex.endsWith("map_") && (rel + "/").startsWith(ex)) {
                String name = rel.substring(rel.lastIndexOf('/') + 1);
                if (name.startsWith("map_") && name.endsWith(".dat")) return true;
            }
        }
        return false;
    }

    /** 递归删除目录（世界回收用），不存在则静默。 */
    public static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            s.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
        } catch (IOException ignored) { }
    }

    /** 判断是否为合法世界目录（含 level.dat）。 */
    public static boolean isWorldFolder(File dir) {
        return dir != null && dir.isDirectory() && new File(dir, "level.dat").isFile();
    }
}
