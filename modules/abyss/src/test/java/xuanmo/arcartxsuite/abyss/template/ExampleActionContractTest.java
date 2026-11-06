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
    @Test
    void bundledRewardGamesAreReachableFromCompletionAndSideChest() {
        Path abysses = Path.of("src/main/resources/abysses");
        Path example = abysses.resolve("example");
        Path procgen = abysses.resolve("procgen");
        assertRewardGame(example, "example_clear_flip", "flip", "scripts.yml", "complete", "@all");
        assertRewardGame(example, "example_side_wheel", "wheel", "interacts.yml", "abyss_chest.script", "@trigger");
        assertRewardGame(procgen, "procgen_clear_choice", "choose-three", "scripts.yml", "complete", "@all");
    }

    private void assertRewardGame(Path dungeon, String id, String strategy, String scriptFile, String hook, String target) {
        var scripts = YamlConfiguration.loadConfiguration(dungeon.resolve(scriptFile).toFile());
        assertTrue(scripts.getStringList(hook).contains("open_reward_game " + target + " " + id));

        var config = RewardConfig.load(YamlConfiguration.loadConfiguration(dungeon.resolve("rewards.yml").toFile()));
        var game = config.games().get(id);
        assertNotNull(game, id);
        assertEquals(strategy, game.strategy());
        assertEquals(0, game.autoAfterSeconds());
        var pool = config.rewards().get(game.pool());
        assertNotNull(pool, game.pool());
        assertTrue(pool.random());
        assertFalse(pool.pool().isEmpty());
        assertTrue(pool.pool().stream().allMatch(entry ->
            entry.weight() > 0 && config.rewards().containsKey(entry.rewardId())));
        if (!game.repeat()) {
            assertTrue(pool.pool().stream().map(RewardConfig.RewardEntry::rewardId).distinct().count() >= game.slots());
        }
    }

    private void check(List<String> lines,ScriptRunner runner,String source) {
        for(String line:lines) {
            String text=line.trim();if(text.isEmpty()||text.startsWith("#")||text.startsWith("aria:")||text.startsWith("js:")||text.startsWith("wait "))continue;
            String action=text.split("\\s+",2)[0];assertNotNull(runner.actions().get(action),source+": 未登记动作 "+action);
        }
    }
}
