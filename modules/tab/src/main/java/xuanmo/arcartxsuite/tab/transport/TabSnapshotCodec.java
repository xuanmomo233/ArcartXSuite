package xuanmo.arcartxsuite.tab.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TabSnapshotCodec {

    private TabSnapshotCodec() {
    }

    /** 协议版本：v1 仅 sort-value/sort-string；v2 新增 sort-values/sort-string-values/group-key。 */
    private static final int PROTOCOL_VERSION = 2;

    public static String encode(TabServerSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("protocol", PROTOCOL_VERSION);
        yaml.set("node-id", snapshot.nodeId());
        yaml.set("definition-id", snapshot.definitionId());
        yaml.set("timestamp", snapshot.timestamp());
        List<Map<String, Object>> entriesData = new ArrayList<>();
        for (TabRemoteEntry entry : snapshot.entries()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", entry.playerUuid());
            map.put("name", entry.playerName());
            // v1 兼容字段（首键）
            map.put("sort-value", entry.sortValue());
            map.put("sort-string", entry.sortStringValue());
            // v2 新字段（多键 + groupKey）
            map.put("sort-values", new ArrayList<>(entry.sortValues()));
            map.put("sort-string-values", new ArrayList<>(entry.sortStringValues()));
            map.put("group-key", entry.groupKey());
            map.put("pack", entry.renderedPack());
            entriesData.add(map);
        }
        yaml.set("entries", entriesData);
        return yaml.saveToString();
    }

    public static TabServerSnapshot decode(String content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        String nodeId = string(yaml.getString("node-id"));
        String definitionId = string(yaml.getString("definition-id"));
        long timestamp = yaml.getLong("timestamp", 0L);
        List<TabRemoteEntry> entries = new ArrayList<>();
        List<?> entriesList = yaml.getList("entries");
        if (entriesList != null) {
            for (Object raw : entriesList) {
                if (raw instanceof Map<?, ?> map) {
                    double sortValue = numberValue(map.get("sort-value"));
                    String sortString = string(map.get("sort-string"));
                    List<Double> sortValues = readDoubleList(map.get("sort-values"), sortValue);
                    List<String> sortStrings = readStringList(map.get("sort-string-values"), sortString);
                    String groupKey = string(map.get("group-key"));
                    entries.add(new TabRemoteEntry(
                        string(map.get("uuid")),
                        string(map.get("name")),
                        sortValue,
                        sortString,
                        sortValues,
                        sortStrings,
                        groupKey,
                        normalizeNode(map.get("pack"))
                    ));
                }
            }
        }
        return new TabServerSnapshot(nodeId, definitionId, timestamp, List.copyOf(entries));
    }

    private static List<Double> readDoubleList(Object raw, double fallback) {
        if (raw instanceof List<?> listRaw && !listRaw.isEmpty()) {
            List<Double> result = new ArrayList<>(listRaw.size());
            for (Object value : listRaw) {
                result.add(value instanceof Number number ? number.doubleValue() : numberValue(value));
            }
            return result;
        }
        return List.of(fallback);
    }

    private static List<String> readStringList(Object raw, String fallback) {
        if (raw instanceof List<?> listRaw && !listRaw.isEmpty()) {
            List<String> result = new ArrayList<>(listRaw.size());
            for (Object value : listRaw) {
                result.add(value == null ? "" : String.valueOf(value));
            }
            return result;
        }
        return List.of(fallback == null ? "" : fallback);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    private static Object normalizeNode(Object node) {
        if (node == null) {
            return "";
        }
        if (node instanceof ConfigurationSection section) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, normalizeNode(section.get(key)));
            }
            return map;
        }
        if (node instanceof Map<?, ?> mapNode) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapNode.entrySet()) {
                map.put(String.valueOf(entry.getKey()), normalizeNode(entry.getValue()));
            }
            return map;
        }
        if (node instanceof List<?> listNode) {
            List<Object> list = new ArrayList<>(listNode.size());
            for (Object entry : listNode) {
                list.add(normalizeNode(entry));
            }
            return list;
        }
        return node;
    }
}
