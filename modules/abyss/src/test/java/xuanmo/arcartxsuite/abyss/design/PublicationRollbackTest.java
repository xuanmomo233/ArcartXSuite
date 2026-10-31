package xuanmo.arcartxsuite.abyss.design;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.abyss.world.WorldCopyUtil;
import static org.junit.jupiter.api.Assertions.*;

class PublicationRollbackTest {
    @TempDir Path dir;
    @Test void failedConfigurationCommitRestoresBothYamlAndOldBuilding()throws Exception {
        Path source=Files.createDirectories(dir.resolve("design")),world=Files.createDirectories(dir.resolve("world"));
        Files.writeString(source.resolve("level.dat"),"new building");Files.writeString(world.resolve("level.dat"),"old building");
        Path first=dir.resolve("config.yml"),second=dir.resolve("rooms.yml");Files.writeString(first,"old-config");Files.writeString(second,"old-rooms");
        Map<Path,byte[]> writes=new LinkedHashMap<>();writes.put(first,"new-config".getBytes());writes.put(second,"new-rooms".getBytes());
        Map<String,String> baseline=Map.of("config.yml",DesignWriter.hash(first),"rooms.yml",DesignWriter.hash(second));
        var moved=new AtomicInteger();
        assertThrows(java.io.IOException.class,()->WorldCopyUtil.publishWorldFolder(source.toFile(),world.toFile(),()->
            DesignWriter.commit(writes,baseline,dir,(from,to)->{
                if(moved.incrementAndGet()==2)throw new java.io.IOException("simulated second write failure");
                Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
            })));
        assertEquals("old building",Files.readString(world.resolve("level.dat")));
        assertEquals("old-config",Files.readString(first));assertEquals("old-rooms",Files.readString(second));
        assertEquals("new building",Files.readString(source.resolve("level.dat")));
    }
    @Test void concurrentWritersCannotBothPublishAgainstTheSameOldBaseline()throws Exception {
        Path target=dir.resolve("shared.yml");Files.writeString(target,"old");
        Map<String,String> baseline=Map.of("shared.yml",DesignWriter.hash(target));
        var secondAttempt=new java.util.concurrent.CountDownLatch(1);
        var writerEntered=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->{
                DesignWriter.commit(Map.of(target,"first".getBytes()),baseline,dir,(from,to)->{
                    writerEntered.countDown();
                    try {if(!secondAttempt.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("second writer did not start");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}
                    Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
                });return null;
            });
            assertTrue(writerEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var second=executor.submit(()->{secondAttempt.countDown();DesignWriter.commit(Map.of(target,"second".getBytes()),baseline,dir);return null;});
            first.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class,()->second.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("first",Files.readString(target));
        } finally {secondAttempt.countDown();executor.shutdownNow();}
    }
}
