package xuanmo.arcartxsuite.abyss.template;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.procgen.*;
import xuanmo.arcartxsuite.abyss.reward.ItemDefinition;

/** Shared publication/load-time business validation. No plugin generation or world mutation. */
public final class AbyssValidation {
    public static final List<String> FILES=List.of("config.yml","floors.yml","rooms.yml","monsters.yml","interacts.yml",
        "obstacles.yml","npcs.yml","puzzles.yml","tasks.yml","rewards.yml","chest_rewards.yml","buffs.yml",
        "shops.yml","scripts.yml","stages.yml","quests.yml","procgen.yml");
    private AbyssValidation() { }
    public static Map<String,YamlConfiguration> read(File dir) throws Exception {
        Map<String,YamlConfiguration> files=new LinkedHashMap<>();
        for(String name:FILES)files.put(name,load(new File(dir,name)));
        var pg=files.get("procgen.yml");
        if(!pg.getKeys(false).isEmpty()) {
            var cfg=ProcGenConfig.load(pg,dir.getName());
            require(RoomSetLibrary.validName(cfg.roomSet()),"procgen.yml: room-set 名称无效");
            File root=new File(dir.getParentFile().getParentFile(),"room-sets/"+cfg.roomSet());
            File[] metas=root.listFiles(f->f.isFile() && f.getName().endsWith(".yml") && !f.getName().endsWith(".region.yml"));
            require(metas!=null && metas.length>0,"procgen.yml: 房间集不存在或没有元数据");
            for(File meta:metas)files.put("../../room-sets/"+cfg.roomSet()+"/"+meta.getName(),load(meta));
        }
        return files;
    }
    private static YamlConfiguration load(File file) throws Exception {
        YamlConfiguration yaml=new YamlConfiguration();yaml.options().parseComments(true);
        if(file.isFile())yaml.load(file);return yaml;
    }
    public static void validate(Map<String,YamlConfiguration> files) throws IOException {
        files=new LinkedHashMap<>(files);
        for(String name:FILES)files.putIfAbsent(name,new YamlConfiguration());
        var floors=FloorConfig.load(files.get("floors.yml"),files.get("rooms.yml"));
        Set<String> floorIds=new HashSet<>();
        for(var floor:floors.floors()) {
            require(floorIds.add(floor.id()),"floors.yml: 楼层ID重复 "+floor.id());
            List<String> refs=new ArrayList<>(floor.source()==FloorConfig.Source.POOL?floor.pool():floor.roomRefs());
            if(floor.bossRoom()!=null)refs.add(floor.bossRoom());
            for(String ref:refs)require(floors.rooms().containsKey(ref),"floors.yml: 房间引用不存在 "+ref);
            require(floor.roomCount()>=0 && floor.roomCount()<=64,"floors.yml: room-count 必须为0～64");
        }
        var globalGroups=MonsterConfig.load(files.get("monsters.yml")).groups();
        validateGroups(globalGroups,globalGroups.keySet());
        for(var room:floors.rooms().values()) {
            require(room.schematic()!=null || (room.region().size()==2 && validPoint(room.region().get(0)) && validPoint(room.region().get(1))),
                "rooms.yml: 房间需要两角区域或schematic "+room.id());
            Set<String> allowed=new HashSet<>(globalGroups.keySet());allowed.addAll(room.groups().keySet());
            validateGroups(room.groups(),allowed);
        }
        for(String name:List.of("monsters.yml","rooms.yml","buffs.yml","rewards.yml","chest_rewards.yml")) {
            var yaml=files.get(name);if(yaml==null)continue;
            for(String key:yaml.getKeys(true)) {
                String leaf=key.substring(key.lastIndexOf('.')+1);
                if(Set.of("monsters","options","pool","doors").contains(leaf) && yaml.isList(key))
                    for(Object row:yaml.getList(key,List.of()))require(row instanceof Map<?,?> || name.equals("rooms.yml"),name+": "+key+" 必须使用对象条目");
            }
        }
        var rewards=RewardConfig.load(files.get("rewards.yml"));
        Map<String,List<String>> rewardEdges=new LinkedHashMap<>();
        for(var reward:rewards.rewards().values()) {
            require(Double.isFinite(reward.money()) && reward.money()>=0 && reward.exp()>=0,"rewards.yml: 金钱与经验不能为负 "+reward.name());
            if(reward.random()) {
                require(!reward.pool().isEmpty() && reward.picks()>0 && reward.picks()<=64,"rewards.yml: 随机奖励池为空或picks越界 "+reward.name());
                long total=0;List<String> refs=new ArrayList<>();
                for(var entry:reward.pool()) {
                    require(entry.weight()>0 && rewards.rewards().containsKey(entry.rewardId()),"rewards.yml: 奖励引用或权重无效 "+reward.name());
                    total+=entry.weight();refs.add(entry.rewardId());
                }
                require(total<=Integer.MAX_VALUE,"rewards.yml: 权重总和越界");rewardEdges.put(reward.name(),refs);
            }
            for(String item:reward.items())item(item);
            for(String item:reward.mythicItems())item("mythic:"+item);
            for(String item:reward.neigeItems())item("neige:"+item);
            for(String item:reward.overtureItems())item("overture:"+item);
            for(String item:reward.mmoItems())item("mmoitems:"+item);
        }
        acyclic(rewardEdges,"rewards.yml: 奖励循环引用");
        var chests=new LinkedHashMap<>(rewards.chestRewards());chests.putAll(RewardConfig.loadChestOnly(files.get("chest_rewards.yml")));
        for(var chest:chests.values()) {
            require(!chest.options().isEmpty() && chest.rows()>0 && chest.rows()<=6,"rewards.yml: 宝箱没有选项或rows越界 "+chest.name());
            long total=0;
            for(var option:chest.options()){require(option.weight()>0,"rewards.yml: 宝箱权重必须为正");total+=option.weight();item(option.item());}
            require(total<=Integer.MAX_VALUE,"rewards.yml: 宝箱权重总和越界");
        }
        var buffs=files.get("buffs.yml").getConfigurationSection("pools");
        if(buffs!=null)for(String id:buffs.getKeys(false))require(!buffs.getMapList(id+".options").isEmpty(),"buffs.yml: 增益池为空 "+id);
        for(var shop:ShopConfig.load(files.get("shops.yml")).shops().values()) {
            require(!shop.currencyVar().isBlank(),"shops.yml: currency-var 不能为空");
            for(var goods:shop.goods())require(goods.price()>=0 && !goods.script().isEmpty(),"shops.yml: 商品价格无效或脚本为空 "+goods.id());
        }
        var scripts=files.get("scripts.yml");
        var stages=StageConfig.load(files.get("stages.yml"),scripts.getConfigurationSection("stages")!=null?scripts.getConfigurationSection("stages"):scripts.getConfigurationSection("阶段"));
        if(stages.entry()!=null)require(stages.stages().containsKey(stages.entry()),"stages.yml: 入口阶段不存在");
        for(var stage:stages.stages().entrySet())for(var next:stage.getValue().next())
            require(stages.stages().containsKey(next.target()),"stages.yml: 后继阶段不存在 "+next.target());
        var entry=EntryRequirements.load(files.get("config.yml").getConfigurationSection("requirements"));
        String mode=files.get("config.yml").getString("mode","classic").toLowerCase(Locale.ROOT);
        require(Set.of("classic","floors").contains(mode),"config.yml: mode 无效");
        require(!mode.equals("floors") || !floors.floors().isEmpty(),"floors.yml: floors 模式需要楼层定义");
        require(entry.minPlayers()>=1 && entry.maxPlayers()>=entry.minPlayers(),"config.yml: 人数范围无效");
        require(Double.isFinite(entry.moneyCost()) && entry.moneyCost()>=0 && entry.staminaCost()>=0,"config.yml: 门票消耗无效");
        for(var required:entry.requiredItems())require(required.amount()>0,"config.yml: 门票数量必须为正");
        for(var puzzle:PuzzleConfig.load(files.get("puzzles.yml")).puzzles().values()) {
            if(puzzle.type()==PuzzleConfig.Type.INPUT) {
                require(puzzle.answer()!=null && !puzzle.answer().isEmpty() && puzzle.length()>=0 && puzzle.length()<=4096
                    && puzzle.answer().length()<=(puzzle.length()>0?puzzle.length():4096),"puzzles.yml: 答案为空或输入长度无效 "+puzzle.id());
                if(puzzle.inputPattern()!=null && !puzzle.inputPattern().isBlank())try {
                    var pattern=java.util.regex.Pattern.compile(puzzle.inputPattern());
                    require(pattern.matcher(puzzle.answer()).matches(),"puzzles.yml: 答案不符合输入正则 "+puzzle.id());
                } catch(java.util.regex.PatternSyntaxException invalid){throw new IOException("puzzles.yml: 输入正则无效 "+puzzle.id(),invalid);}
            }
        }
        var pg=files.get("procgen.yml");
        if(pg!=null && !pg.getKeys(false).isEmpty()) {
            var cfg=ProcGenConfig.load(pg,"validation");
            require(RoomSetLibrary.validName(cfg.roomSet()),"procgen.yml: room-set 名称无效");
            require(cfg.corridor().length()>=0 && cfg.corridor().length()<=64 && cfg.corridor().doorWidth()>0
                && cfg.corridor().doorWidth()<=31 && cfg.corridor().doorHeight()>=2 && cfg.corridor().doorHeight()<=32,"procgen.yml: 走廊尺寸越界");
            int budget=2;
            for(var count:cfg.counts().entrySet()) {
                try {
                    ProcGenRoomAsset.RoomKind.valueOf(count.getKey().toUpperCase(Locale.ROOT));
                    require(count.getValue().matches("\\d+(?:-\\d+)?"),"procgen.yml: counts 格式无效");
                    String[] range=count.getValue().split("-");int lo=Integer.parseInt(range[0]),hi=Integer.parseInt(range[range.length-1]);
                    require(lo<=hi && hi<=64,"procgen.yml: counts 范围无效");budget+=hi;
                } catch(IllegalArgumentException invalid){throw new IOException("procgen.yml: 房型或数量无效 "+count.getKey(),invalid);}
            }
            require(budget<=64,"procgen.yml: 配置房间总数超过64");
        }
    }
    public static void validateFiles(Map<String,YamlConfiguration> files,File dir) throws IOException {
        validate(files);
        Set<String> kinds=new HashSet<>();
        for(var entry:files.entrySet())if(entry.getKey().startsWith("../../room-sets/")) {
            Path meta=dir.toPath().resolve(entry.getKey()).normalize();
            File region=meta.resolveSibling(meta.getFileName().toString().replace(".yml",".region.yml")).toFile();
            RegionTemplate volume=region.isFile()?RegionTemplate.load(region):null;
            validateAsset(entry.getValue(),volume,entry.getKey());
            kinds.add(entry.getValue().getString("type","").toUpperCase(Locale.ROOT));
        }
        if(!files.get("procgen.yml").getKeys(false).isEmpty())
            require(kinds.containsAll(Set.of("START","COMBAT","BOSS")),"procgen.yml: 房间集缺少 START/COMBAT/BOSS 房型");
    }
    public static void validateAsset(YamlConfiguration yaml,RegionTemplate volume,String name) throws IOException {
        try{ProcGenRoomAsset.RoomKind.valueOf(yaml.getString("type","").toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid){throw new IOException(name+": 房型无效",invalid);}
        require(yaml.getInt("weight",10)>0,name+": weight 必须为正");
        for(String door:yaml.getStringList("doors")) {
            String[] parts=door.split(",");require(parts.length>=2,name+": doors 格式无效");
            String wall=parts[0].trim().toLowerCase(Locale.ROOT);
            require(Set.of("n","s","e","w","north","south","east","west").contains(wall),name+": 门朝向无效");
            try {
                int center=Integer.parseInt(parts[1].trim()),width=parts.length>2?Integer.parseInt(parts[2].trim()):5,height=parts.length>3?Integer.parseInt(parts[3].trim()):5;
                require(center>=0 && width>0 && height>=2 && height<=32,name+": 门宽高无效");
                if(volume!=null){int span=wall.startsWith("n")||wall.startsWith("s")?volume.width():volume.depth();
                    require(center-width/2>=1 && center+(width-1)/2<span-1,name+": 门洞超出墙边界");}
            } catch(NumberFormatException invalid){throw new IOException(name+": 门尺寸必须为整数",invalid);}
        }
        if(volume!=null) {
            require(volume.height()>=4 && volume.width()>=3 && volume.depth()>=3,name+": 构件没有足够的地板或净高");
            List<String> anchors=new ArrayList<>();
            if(yaml.contains("spawn"))anchors.add(yaml.getString("spawn"));
            for(String key:yaml.getKeys(false))if(key.equals("mobs")||key.startsWith("mobs-wave-")||Set.of("chests","npcs","interacts").contains(key))
                for(String row:yaml.getStringList(key))anchors.add(row.split(":",2)[0].trim());
            for(String anchor:anchors) {
                require(validPoint(anchor),name+": 锚点坐标无效");String[] xyz=anchor.split(",");
                double x=Double.parseDouble(xyz[0].trim()),y=Double.parseDouble(xyz[1].trim()),z=Double.parseDouble(xyz[2].trim());
                require(x>=0 && x<volume.width() && y>=0 && y<volume.height() && z>=0 && z<volume.depth(),name+": 锚点超出构件边界");
            }
        }
    }
    private static void validateGroups(Map<String,MonsterConfig.MonsterGroup> groups,Set<String> allowed) throws IOException {
        Map<String,List<String>> graph=new HashMap<>();
        for(var group:groups.values()) {
            graph.put(group.name(),group.triggerAfter());
            for(String dependency:group.triggerAfter())require(allowed.contains(dependency),"monsters/rooms: 怪组前置不存在 "+dependency);
            for(var mob:group.monsters())require(!mob.mobId().isBlank() && !mob.mobId().equals("?") && mob.amount()>0
                && mob.amount()<=1000 && Double.isFinite(mob.level()) && mob.level()>=0 && validPoint(mob.location()),"monsters/rooms: 怪物条目无效 "+group.name());
        }
        acyclic(graph,"monsters/rooms: 怪组依赖成环");
    }
    private static void acyclic(Map<String,List<String>> graph,String message) throws IOException {
        Set<String> done=new HashSet<>(),active=new HashSet<>();
        for(String key:graph.keySet())visit(key,graph,done,active,message);
    }
    private static void visit(String key,Map<String,List<String>> graph,Set<String> done,Set<String> active,String message) throws IOException {
        if(done.contains(key))return;require(active.add(key),message+": "+key);
        for(String next:graph.getOrDefault(key,List.of()))visit(next,graph,done,active,message);
        active.remove(key);done.add(key);
    }
    private static boolean validPoint(String point) {
        if(point==null)return false;String[] xyz=point.split(",");if(xyz.length<3)return false;
        try{for(int i=0;i<3;i++){double n=Double.parseDouble(xyz[i].trim());if(!Double.isFinite(n)||n<Integer.MIN_VALUE||n>=Integer.MAX_VALUE)return false;}return true;}
        catch(NumberFormatException invalid){return false;}
    }
    private static void item(String definition) throws IOException {try{ItemDefinition.parse(definition);}catch(RuntimeException invalid){throw new IOException("物品定义无效: "+definition,invalid);}}
    private static void require(boolean condition,String message) throws IOException {if(!condition)throw new IOException(message);}
}
