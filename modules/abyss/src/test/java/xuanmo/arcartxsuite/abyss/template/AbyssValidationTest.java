package xuanmo.arcartxsuite.abyss.template;

import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbyssValidationTest {
    Map<String,YamlConfiguration> files() {
        Map<String,YamlConfiguration> files=new LinkedHashMap<>();for(String name:AbyssValidation.FILES)files.put(name,new YamlConfiguration());return files;
    }
    @Test void bundledDefinitionsPassTheSameValidatorAsEditorPublication()throws Exception {
        for(String name:List.of("example","procgen")) {
            var dir=Path.of("src/main/resources/abysses",name).toFile();
            AbyssValidation.validateFiles(AbyssValidation.read(dir),dir);
        }
    }
    @Test void missingStagesAndCyclicRewardsAreRejectedBeforePublication()throws Exception {
        var files=files();files.get("stages.yml").loadFromString("entry: a\na:\n  next: missing\n");
        assertThrows(java.io.IOException.class,()->AbyssValidation.validate(files));
        files.put("stages.yml",new YamlConfiguration());
        files.get("rewards.yml").loadFromString("rewards:\n  a:\n    type: random\n    pool: [{reward: b, weight: 1}]\n  b:\n    type: random\n    pool: [{reward: a, weight: 1}]\n");
        assertThrows(java.io.IOException.class,()->AbyssValidation.validate(files));
    }
    @Test void invalidCorridorCountsAndWeightsAreRejected()throws Exception {
        var files=files();files.get("procgen.yml").loadFromString("room-set: example\ncounts: {combat: '999'}\n");
        assertThrows(java.io.IOException.class,()->AbyssValidation.validate(files));
        files.put("procgen.yml",new YamlConfiguration());
        files.get("rewards.yml").loadFromString("chest-rewards:\n  bad:\n    options: [{item: 'STONE:1', weight: -1}]\n");
        assertThrows(java.io.IOException.class,()->AbyssValidation.validate(files));
    }
    @Test void doorBoundsAndUnanswerableInputAreRejected()throws Exception {
        var meta=new YamlConfiguration();meta.loadFromString("type: combat\ndoors: ['east,8,3,5']\n");
        var volume=RegionTemplate.of(9,7,9,List.of("minecraft:air"),new byte[7][9*9*2]);
        assertThrows(java.io.IOException.class,()->AbyssValidation.validateAsset(meta,volume,"room"));
        var files=files();files.get("puzzles.yml").loadFromString("test:\n  type: input\n  answer: abcd\n  input-pattern: '[0-9]*'\n");
        assertThrows(java.io.IOException.class,()->AbyssValidation.validate(files));
    }
}
