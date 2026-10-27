package xuanmo.arcartxsuite.abyss.design;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.template.*;

/** Validation before publishing an edited definition. External mob/item IDs are runtime integrations. */
final class DesignValidation {
    private DesignValidation() { }
    static void validate(Map<String,YamlConfiguration> files) throws IOException {
        var floors=FloorConfig.load(files.get("floors.yml"),files.get("rooms.yml"));
        Set<String> floorIds=new HashSet<>();
        for(var floor:floors.floors()) {
            if(!floorIds.add(floor.id()))throw new IOException("楼层ID重复: "+floor.id());
            List<String> refs=new ArrayList<>(floor.source()==FloorConfig.Source.POOL?floor.pool():floor.roomRefs());
            if(floor.bossRoom()!=null)refs.add(floor.bossRoom());
            for(String ref:refs)if(!floors.rooms().containsKey(ref))throw new IOException("楼层引用的房间不存在: "+ref);
        }
        validateGroups(MonsterConfig.load(files.get("monsters.yml")).groups());
        for(var room:floors.rooms().values()) {
            if(room.region().size()!=2 || DesignCoords.box(room.region())==null)
                throw new IOException("房间需要有效的两角区域: "+room.id());
            validateGroups(room.groups());
        }
        var puzzles=PuzzleConfig.load(files.get("puzzles.yml"));
        // Structured entries must retain their types; malformed rows may otherwise load as empty lists.
        for(String file:List.of("monsters.yml","rooms.yml","buffs.yml","rewards.yml")) {
            var yaml=files.get(file);
            for(String key:yaml.getKeys(true)) {
                String leaf=key.substring(key.lastIndexOf('.')+1);
                if(Set.of("monsters","options","pool","doors").contains(leaf) && yaml.isList(key)) {
                    for(Object row:yaml.getList(key,List.of())) {
                        if(!(row instanceof Map<?,?>)) {
                            // Pool may be a list of room references; only room pools use that format.
                            if(!file.equals("rooms.yml"))throw new IOException(file+" 的 "+key+" 必须使用对象条目");
                        }
                    }
                }
            }
        }
        var rewards=RewardConfig.load(files.get("rewards.yml"));
        for(var reward:rewards.rewards().values()) if(reward.random() && reward.pool().isEmpty())
            throw new IOException("随机奖励池不能为空: "+reward.name());
        var buffs=files.get("buffs.yml").getConfigurationSection("pools");
        if(buffs!=null)for(String id:buffs.getKeys(false)) {
            if(buffs.getMapList(id+".options").isEmpty())throw new IOException("增益池选项不能为空: "+id);
        }
        for(var group:MonsterConfig.load(files.get("monsters.yml")).groups().values())
            for(String dep:group.triggerAfter())
                if(!MonsterConfig.load(files.get("monsters.yml")).groups().containsKey(dep))
                    throw new IOException("怪组前置不存在: "+dep);
    }
    private static void validateGroups(Map<String,MonsterConfig.MonsterGroup> groups) throws IOException {
        for(var group:groups.values())for(var spawn:group.monsters()) {
            if(spawn.mobId().isBlank()||spawn.mobId().equals("?")||spawn.amount()<1
                ||spawn.amount()>1000||!Double.isFinite(spawn.level())||spawn.level()<0
                ||DesignCoords.point(spawn.location())==null)
                throw new IOException("无效的怪物条目: "+group.name());
        }
    }
}
