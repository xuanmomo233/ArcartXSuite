package xuanmo.arcartxsuite.abyss.world;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class WorldCopyUtilTest {
    @TempDir Path root;
    @Test void replacesSnapshotWithoutKeepingDeletedOldFilesAndKeepsBackup()throws Exception {
        Path source=Files.createDirectory(root.resolve("source")), target=Files.createDirectory(root.resolve("world"));
        Files.writeString(source.resolve("level.dat"),"new");Files.writeString(source.resolve("uid.dat"),"runtime");
        Files.writeString(target.resolve("level.dat"),"old");Files.writeString(target.resolve("deleted-region"),"stale");
        WorldCopyUtil.replaceWorldFolder(source.toFile(),target.toFile());
        assertEquals("new",Files.readString(target.resolve("level.dat")));
        assertFalse(Files.exists(target.resolve("deleted-region")));assertFalse(Files.exists(target.resolve("uid.dat")));
        try(var paths=Files.list(root)){
            Path backup=paths.filter(p->p.getFileName().toString().startsWith("world.backup-")).findFirst().orElseThrow();
            assertEquals("old",Files.readString(backup.resolve("level.dat")));
        }
    }
    @Test void rejectsOverlappingSourceAndTarget()throws Exception {
        Path source=Files.createDirectory(root.resolve("source"));
        assertThrows(java.io.IOException.class,()->WorldCopyUtil.replaceWorldFolder(source.toFile(),source.resolve("child").toFile()));
    }
    @Test void failedCopyLeavesExistingWorldUntouched()throws Exception {
        Path target=Files.createDirectory(root.resolve("world"));Files.writeString(target.resolve("level.dat"),"original");
        assertThrows(java.io.IOException.class,()->WorldCopyUtil.replaceWorldFolder(root.resolve("missing").toFile(),target.toFile()));
        assertEquals("original",Files.readString(target.resolve("level.dat")));
    }
}
