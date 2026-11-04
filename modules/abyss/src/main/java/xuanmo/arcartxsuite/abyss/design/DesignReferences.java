package xuanmo.arcartxsuite.abyss.design;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** References are migrated by property/action semantics, never by replacing arbitrary display text. */
final class DesignReferences {
    private DesignReferences() { }
    private static final Map<String, DesignObject.Kind> ACTIONS = Map.ofEntries(
        Map.entry("spawn_group", DesignObject.Kind.GROUP), Map.entry("kill_group", DesignObject.Kind.GROUP),
        Map.entry("remove_group", DesignObject.Kind.GROUP), Map.entry("group_alive_count", DesignObject.Kind.GROUP),
        Map.entry("create_interact", DesignObject.Kind.INTERACT), Map.entry("delete_interact", DesignObject.Kind.INTERACT),
        Map.entry("create_obstacle", DesignObject.Kind.OBSTACLE), Map.entry("delete_obstacle", DesignObject.Kind.OBSTACLE),
        Map.entry("spawn_npc", DesignObject.Kind.NPC), Map.entry("remove_npc", DesignObject.Kind.NPC),
        Map.entry("puzzle_reset", DesignObject.Kind.PUZZLE), Map.entry("puzzle_solve", DesignObject.Kind.PUZZLE),
        Map.entry("puzzle_prompt", DesignObject.Kind.PUZZLE), Map.entry("puzzle_state", DesignObject.Kind.PUZZLE),
        Map.entry("room_lock", DesignObject.Kind.ROOM), Map.entry("room_unlock", DesignObject.Kind.ROOM),
        Map.entry("room_status", DesignObject.Kind.ROOM), Map.entry("grant_reward", DesignObject.Kind.REWARD),
        Map.entry("set_stage", DesignObject.Kind.STAGE),
        Map.entry("flow_jump", DesignObject.Kind.STAGE),
        Map.entry("run_script", DesignObject.Kind.SCRIPT),
        Map.entry("open_chest_ui", DesignObject.Kind.CHEST), Map.entry("buff_offer", DesignObject.Kind.BUFF),
        Map.entry("buff_grant", DesignObject.Kind.BUFF), Map.entry("buff_clear", DesignObject.Kind.BUFF));
    static void migrate(Map<String,YamlConfiguration> files, DesignObject.Kind kind, String oldId,
                        String newId, String path, Map<String,String> roomRenames) throws Exception {
        if (kind == DesignObject.Kind.ROOM_GROUP) {
            String room = path.substring(6, path.length()-7);
            room = roomRenames.getOrDefault(room, room);
            var yaml=files.get("rooms.yml");
            var section=yaml.getConfigurationSection("rooms."+room);
            if(section!=null) yaml.set("rooms."+room, rewrite(DesignValues.copy(section.getValues(false)),
                "", DesignObject.Kind.GROUP,oldId,newId,true,false));
            return;
        }
        for (var entry:files.entrySet()) {
            Map<String,Object> values=DesignValues.copy(entry.getValue().getValues(false));
            boolean dependencies = objectReferencesIn(kind, entry.getKey());
            @SuppressWarnings("unchecked") Map<String,Object> replaced=(Map<String,Object>)rewrite(values,"",kind,oldId,newId,dependencies,true);
            YamlConfiguration output=entry.getValue();
            replaced.forEach(output::set);
            output.loadFromString(output.saveToString());
            entry.setValue(output);
        }
    }
    static boolean isReferenced(Map<String,YamlConfiguration> files, DesignObject.Kind kind, String id) {
        DesignObject.Kind target=kind==DesignObject.Kind.ROOM_GROUP?DesignObject.Kind.GROUP:kind;
        for(var entry:files.entrySet()) {
            Object values=DesignValues.copy(entry.getValue().getValues(false));
            boolean dependencies = objectReferencesIn(kind, entry.getKey());
            if(!values.equals(rewrite(values,"",target,id,"__deleted_reference__",dependencies,true))) return true;
        }
        return false;
    }
    static boolean roomGroupReferenced(Map<String,YamlConfiguration> files, String id, String path,
                                      Map<String,String> roomRenames) {
        String room = path.substring(6, path.length() - 7);
        var section = files.get("rooms.yml").getConfigurationSection("rooms." + roomRenames.getOrDefault(room,room));
        if (section == null) return false;
        Object values = DesignValues.copy(section.getValues(false));
        return !values.equals(rewrite(values,"",DesignObject.Kind.GROUP,id,"__deleted_reference__",true,false));
    }

    private static boolean objectReferencesIn(DesignObject.Kind kind, String file) {
        if (kind == DesignObject.Kind.STAGE) return file.equals("stages.yml");
        return kind != DesignObject.Kind.GROUP || !file.equals("rooms.yml");
    }
    private static Object rewrite(Object value,String property,DesignObject.Kind kind,String oldId,String newId) {
        return rewrite(value,property,kind,oldId,newId,true,true);
    }
    private static Object rewrite(Object value,String property,DesignObject.Kind kind,String oldId,String newId,
                                  boolean dependencies, boolean scripts) {
        if(value instanceof Map<?,?> map) {
            Map<String,Object> out=new LinkedHashMap<>();
            map.forEach((k,v)->out.put(String.valueOf(k),rewrite(v,String.valueOf(k),kind,oldId,newId,dependencies,scripts)));
            return out;
        }
        if(value instanceof List<?> list) {
            List<Object> out=new ArrayList<>();
            for(Object item:list)out.add(rewrite(item,property,kind,oldId,newId,dependencies,scripts));
            return out;
        }
        if(!(value instanceof String text))return value;
        String prop=property.replace('_','-');
        String anchorSection = switch (kind) {
            case NPC -> "npcs";
            case CHEST -> "chests";
            case INTERACT -> "interacts";
            default -> "";
        };
        if (!anchorSection.isEmpty() && prop.equals(anchorSection)) {
            int colon = text.indexOf(':');
            if (colon > 0 && text.substring(colon + 1).trim().equals(oldId)) {
                return text.substring(0, colon + 1) + " " + newId;
            }
        }
        boolean ref=switch(kind) {
            case GROUP -> dependencies && prop.equals("trigger-after");
            case INTERACT -> prop.equals("steps") || prop.equals("design-interact");
            case ROOM -> Set.of("rooms","pool","boss-room").contains(prop);
            case REWARD -> prop.equals("reward") || prop.equals("challenge-reward");
            case STAGE -> dependencies && Set.of("next", "target", "stage", "entry").contains(prop);
            default -> false;
        };
        if(ref && text.equals(oldId))return newId;
        if (!scripts) return text;
        if (kind == DesignObject.Kind.SCRIPT && text.matches("(?i)^delay\\s+\\S+\\s+run_script\\s+\\S+$")) {
            int index = text.lastIndexOf(' ');
            if (index >= 0 && text.substring(index + 1).equalsIgnoreCase(oldId))
                return text.substring(0, index + 1) + newId;
        }
        if (text.startsWith("flow_if ")) {
            int end = text.indexOf(' ', 8);
            if (end > 8) {
                return "flow_if " + rewrite(text.substring(8, end), property, kind, oldId, newId, dependencies, true)
                    + " " + rewrite(text.substring(end + 1), property, kind, oldId, newId, dependencies, true);
            }
        }
        String prefix = kind == DesignObject.Kind.GROUP ? "group_cleared:"
            : kind == DesignObject.Kind.ROOM ? "room_cleared:" : "";
        if (!prefix.isEmpty() && text.equals(prefix + oldId)) return prefix + newId;
        String[] tokens=text.trim().split("\\s+");
        if(tokens.length<2 || ACTIONS.get(tokens[0])!=kind)return text;
        int index=(tokens[0].equals("grant_reward")||tokens[0].equals("open_chest_ui"))?2:1;
        if(tokens.length>index && tokens[index].equals(oldId)) {
            // Preserve whitespace and selectors around the reference token.
            var matcher=java.util.regex.Pattern.compile("\\S+").matcher(text);
            for(int i=0;matcher.find();i++)if(i==index)
                return text.substring(0,matcher.start())+newId+text.substring(matcher.end());
        }
        return text;
    }
}
