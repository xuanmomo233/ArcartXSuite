package xuanmo.arcartxsuite.regions.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;

/** 读写区域数据与导出快照。 */
public final class RegionSnapshots {

    private static final int VERSION = 2;
    private static final Set<String> FIELDS = Set.of("id", "world", "uuid", "type", "min", "max",
        "priority", "parent", "owners", "members", "owner-groups", "member-groups", "flags", "blocked-interactions");

    private RegionSnapshots() {}

    public static String write(List<Region> regions) {
        return document(regions).saveToString();
    }

    static YamlConfiguration document(List<Region> regions) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        yaml.set("regions", regions.stream().map(RegionSnapshots::fields).toList());
        return yaml;
    }

    public static List<Region> read(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return read(yaml);
    }

    static List<Region> read(YamlConfiguration yaml) {
        if (!Integer.valueOf(VERSION).equals(yaml.get("version"))) {
            throw new IllegalArgumentException("不支持的区域快照版本。");
        }
        return readEntries(yaml.get("regions"));
    }

    static List<Region> readEntries(Object value) {
        if (!(value instanceof List<?> entries)) throw new IllegalArgumentException("区域记录必须为列表。");
        List<Region> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) throw new IllegalArgumentException("区域记录必须为映射。");
            if (!FIELDS.containsAll(map.keySet())) throw new IllegalArgumentException("区域记录包含未知字段。");
            result.add(readRegion(map));
        }
        return result;
    }

    public static Map<String, Object> fields(Region region) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", region.id());
        values.put("world", region.world());
        values.put("uuid", region.uid().toString());
        values.put("type", region.kind().name().toLowerCase(Locale.ROOT));
        if (region.kind() != Region.Kind.GLOBAL) {
            values.put("min", List.of(region.minX(), region.minY(), region.minZ()));
            values.put("max", List.of(region.maxX(), region.maxY(), region.maxZ()));
        }
        values.put("priority", region.priority());
        if (region.parentId() != null) values.put("parent", region.parentId());
        values.put("owners", region.owners().stream().map(UUID::toString).sorted().toList());
        values.put("members", region.members().stream().map(UUID::toString).sorted().toList());
        values.put("owner-groups", region.ownerGroups().stream().sorted().toList());
        values.put("member-groups", region.memberGroups().stream().sorted().toList());
        Map<String, Object> flags = new LinkedHashMap<>();
        for (var entry : region.flags().entrySet()) {
            boolean state = entry.getValue() == RegionFlag.State.ALLOW;
            String text = region.getFlagData(entry.getKey());
            flags.put(entry.getKey().configKey(), text == null ? state : Map.of("state", state, "text", text));
        }
        values.put("flags", flags);
        if (!region.blockedInteractions().isEmpty()) {
            values.put("blocked-interactions", region.blockedInteractions().stream().map(Material::name).sorted().toList());
        }
        return values;
    }

    private static Region readRegion(Map<?, ?> fields) {
        String id = string(fields.get("id"), "id", 64);
        String world = string(fields.get("world"), "world", 128);
        Region.Kind kind = Region.Kind.valueOf(string(fields.get("type"), "type", 12).toUpperCase(Locale.ROOT));
        List<Integer> min = kind == Region.Kind.GLOBAL ? List.of(0, 0, 0) : coordinates(fields.get("min"), "min");
        List<Integer> max = kind == Region.Kind.GLOBAL ? List.of(0, 0, 0) : coordinates(fields.get("max"), "max");
        for (int i = 0; i < 3; i++) {
            if (min.get(i) > max.get(i)) throw new IllegalArgumentException("区域最小坐标大于最大坐标：" + world + ":" + id);
        }
        Region region = new Region(id, world, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
        if (fields.containsKey("uuid")) region.setUid(UUID.fromString(string(fields.get("uuid"), "uuid", 36)));
        region.setKind(kind);
        if (fields.containsKey("priority")) {
            if (!(fields.get("priority") instanceof Integer number)) throw new IllegalArgumentException("priority 必须为整数。");
            region.setPriority(number);
        }
        if (fields.containsKey("parent")) region.setParentId(string(fields.get("parent"), "parent", 64));
        optionalList(fields, "owners", 36).forEach(value -> region.addOwner(UUID.fromString(value)));
        optionalList(fields, "members", 36).forEach(value -> region.addMember(UUID.fromString(value)));
        optionalList(fields, "owner-groups", 64).forEach(region::addOwnerGroup);
        optionalList(fields, "member-groups", 64).forEach(region::addMemberGroup);
        var rawFlags = fields.containsKey("flags") ? fields.get("flags") : Map.of();
        if (!(rawFlags instanceof Map<?, ?> flags)) throw new IllegalArgumentException("flags 必须为映射。");
        flags.forEach((raw, value) -> {
            String key = string(raw, "flag", 32);
            RegionFlag flag = RegionFlag.fromKey(key);
            if (flag == null) throw new IllegalArgumentException("未知区域标志 " + key);
            if (value instanceof Map<?, ?> setting) {
                flag.apply(region, state(setting.get("state"), key), string(setting.get("text"), key, 1024));
            } else {
                region.setFlag(flag, state(value, key));
            }
        });
        if (fields.containsKey("blocked-interactions")) {
            if (region.kind() != Region.Kind.GLOBAL) throw new IllegalArgumentException("禁交互材质只支持世界默认规则。");
            for (String name : strings(fields.get("blocked-interactions"), "blocked-interactions", 128)) {
                Material material = Material.matchMaterial(name);
                if (material == null || !material.isBlock()) throw new IllegalArgumentException("未知方块材质 " + name);
                region.setBlockedInteraction(material, true);
            }
        }
        return region;
    }

    private static RegionFlag.State state(Object value, String key) {
        if (!(value instanceof Boolean enabled)) throw new IllegalArgumentException(key + " 须为 true 或 false。");
        return enabled ? RegionFlag.State.ALLOW : RegionFlag.State.DENY;
    }

    private static List<Integer> coordinates(Object value, String label) {
        if (!(value instanceof List<?> list) || list.size() != 3
            || list.stream().anyMatch(element -> !(element instanceof Integer))) {
            throw new IllegalArgumentException(label + " 必须包含三个整数坐标。");
        }
        return list.stream().map(Integer.class::cast).toList();
    }

    private static List<String> optionalList(Map<?, ?> fields, String label, int maxLength) {
        return fields.containsKey(label) ? strings(fields.get(label), label, maxLength) : List.of();
    }

    private static List<String> strings(Object value, String label, int maxLength) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " 必须为列表。");
        return list.stream().map(element -> string(element, label, maxLength)).toList();
    }

    private static String string(Object value, String label, int maxLength) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength
            || text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new IllegalArgumentException(label + " 的文本无效。");
        }
        return text;
    }

    public static boolean same(Region first, Region second) {
        if (first == null || second == null) return first == second;
        return sameDefinition(first, second)
            && first.flags().equals(second.flags()) && first.flagData().equals(second.flagData());
    }

    public static boolean sameDefinition(Region first, Region second) {
        return first.id().equals(second.id()) && first.world().equals(second.world())
            && first.uid().equals(second.uid()) && first.kind() == second.kind()
            && first.minX() == second.minX() && first.minY() == second.minY() && first.minZ() == second.minZ()
            && first.maxX() == second.maxX() && first.maxY() == second.maxY() && first.maxZ() == second.maxZ()
            && first.priority() == second.priority() && Objects.equals(first.parentId(), second.parentId())
            && first.owners().equals(second.owners()) && first.members().equals(second.members())
            && first.ownerGroups().equals(second.ownerGroups()) && first.memberGroups().equals(second.memberGroups())
            && first.blockedInteractions().equals(second.blockedInteractions());
    }
}
