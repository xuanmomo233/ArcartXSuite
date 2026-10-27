package xuanmo.arcartxsuite.abyss.template;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionBuildSnapshotTest {
    @TempDir Path dir;
    @Test void generatedDoorAirIsRestoredWithoutOverwritingAuthoredBlocks()throws Exception {
        RegionTemplate original=RegionTemplate.of(2,1,1,List.of("minecraft:stone"),new byte[][]{new byte[]{0,0,0,0}});
        RegionTemplate edited=RegionTemplate.of(2,1,1,List.of("minecraft:air","minecraft:gold_block"),new byte[][]{new byte[]{0,0,1,0}});
        RegionTemplate restored=edited.restoreGeneratedAir(original,List.of(new int[]{0,0,0},new int[]{1,0,0}));
        Path file=dir.resolve("room.yml");restored.save(file.toFile());
        var yaml=YamlConfiguration.loadConfiguration(file.toFile());
        byte[] layer=Base64.getDecoder().decode(yaml.getStringList("layers").get(0));
        var palette=yaml.getStringList("palette");
        assertEquals("minecraft:stone",palette.get(layer[0]&255));
        assertEquals("minecraft:gold_block",palette.get(layer[2]&255));
    }
    @Test void rotatedBuildingRoundTripsToOriginalAssetOrientation()throws Exception {
        Server server=mock(Server.class);
        when(server.createBlockData(anyString())).thenAnswer(call->{
            BlockData data=mock(BlockData.class);when(data.getAsString()).thenReturn(call.getArgument(0));return data;
        });
        var field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);Object previous=field.get(null);
        try {
            field.set(null,server);
            byte[] layer=new byte[3*5*2];for(int i=0;i<15;i++)layer[i*2]=(byte)(i%2);
            RegionTemplate original=RegionTemplate.of(3,1,5,List.of("minecraft:stone","minecraft:gold_block"),new byte[][]{layer});
            RegionTemplate rotated=original.rotatedCopy(90);assertArrayEquals(new int[]{5,1,3},rotated.rotatedSize(0));
            RegionTemplate restored=rotated.rotatedCopy(270);
            Path a=dir.resolve("original.yml"),b=dir.resolve("restored.yml");
            original.save(a.toFile());restored.save(b.toFile());
            assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));
        } finally {field.set(null,previous);}
    }
}
