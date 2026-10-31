package xuanmo.arcartxsuite.abyss.template;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExampleActionContractTest {
    @Test void examplesAndSharedRoomScriptsUseRegisteredActions()throws Exception {
        var runner=new ScriptRunner(null,mock(SchedulerAPI.class),()->mock(AbyssService.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE)),Logger.getAnonymousLogger());
        Path resources=Path.of("src/main/resources");
        for(String dungeon:List.of("example","procgen")) {
            var scripts=YamlConfiguration.loadConfiguration(resources.resolve("abysses/"+dungeon+"/scripts.yml").toFile());
            for(String key:scripts.getKeys(false))if(scripts.isList(key))check(scripts.getStringList(key),runner,key);
            var shops=ShopConfig.load(YamlConfiguration.loadConfiguration(resources.resolve("abysses/"+dungeon+"/shops.yml").toFile()));
            for(var shop:shops.shops().values())for(var goods:shop.goods())check(goods.script(),runner,goods.id());
        }
        try(var files=Files.list(resources.resolve("room-sets/example"))) {
            for(Path path:files.filter(p->p.toString().endsWith(".yml")&&!p.toString().endsWith(".region.yml")).toList()) {
                var meta=YamlConfiguration.loadConfiguration(path.toFile()).getConfigurationSection("scripts");
                if(meta!=null)for(String key:meta.getKeys(false))if(meta.isList(key))check(meta.getStringList(key),runner,path.toString());
            }
        }
    }
    private void check(List<String> lines,ScriptRunner runner,String source) {
        for(String line:lines) {
            String text=line.trim();if(text.isEmpty()||text.startsWith("#")||text.startsWith("aria:")||text.startsWith("js:")||text.startsWith("wait "))continue;
            String action=text.split("\\s+",2)[0];assertNotNull(runner.actions().get(action),source+": 未登记动作 "+action);
        }
    }
}
