package xuanmo.arcartxsuite.tab.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import xuanmo.arcartxsuite.tab.transport.TabRemoteEntry;

/**
 * Tab 调试快照落盘 / 加载工具：把 {@link TabRemoteEntry} 列表序列化为 JSON，便于复现 BUG 与回归测试。
 *
 * <p>文件格式（{@code version: 2}）：
 * <pre>
 * {
 *   "version": 2,
 *   "savedAt": &lt;epoch-ms&gt;,
 *   "serverId": "default",
 *   "localEntries": { "&lt;defId&gt;": [ TabRemoteEntry, ... ] },
 *   "remoteSnapshots": { "&lt;nodeId&gt;": { "&lt;defId&gt;": [ TabRemoteEntry, ... ] } }
 * }
 * </pre>
 *
 * <p>{@code TabRemoteEntry.renderedPack} 是任意结构（{@code String} / {@code List} / {@code Map}），
 * 通过 {@link Gson} 直接序列化；反序列化后由 {@code TabPayloadAssembler.append} 按
 * {@code instanceof Map / List} 处理，与原结构等价。
 *
 * <p>**仅 dev 用**：load 路径会把存档玩家伪装成虚拟跨服节点条目注入到当前 viewer 的 Tab，
 * 因此应仅在测试服或权限受控环境下使用。
 */
public final class TabSnapshotStore {

    private static final int CURRENT_VERSION = 2;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    private final Path baseDir;
    private final Gson gson;

    public TabSnapshotStore(Path baseDir) {
        this.baseDir = baseDir;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    /** 校验快照名称：仅允许字母 / 数字 / 下划线 / 短横线，长度 1~64。 */
    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public Path baseDir() {
        return baseDir;
    }

    public Path resolveFile(String name) {
        return baseDir.resolve(name + ".json");
    }

    /** 列出当前所有已保存的快照名（按字母序）。 */
    public List<String> list() throws IOException {
        if (!Files.isDirectory(baseDir)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .forEach(p -> {
                    String fn = p.getFileName().toString();
                    names.add(fn.substring(0, fn.length() - 5));
                });
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    /** 删除指定快照文件。 */
    public boolean delete(String name) throws IOException {
        if (!isValidName(name)) {
            return false;
        }
        return Files.deleteIfExists(resolveFile(name));
    }

    /**
     * 落盘：序列化本服快照 + 远程快照。
     *
     * @return 实际写入的绝对路径
     */
    public Path save(
        String name,
        String serverId,
        Map<String, List<TabRemoteEntry>> localEntries,
        Map<String, Map<String, List<TabRemoteEntry>>> remoteSnapshots
    ) throws IOException {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid snapshot name: " + name);
        }
        Files.createDirectories(baseDir);

        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        root.addProperty("savedAt", System.currentTimeMillis());
        root.addProperty("serverId", serverId == null ? "" : serverId);
        root.add("localEntries", encodeDefMap(localEntries));

        JsonObject remoteJson = new JsonObject();
        if (remoteSnapshots != null) {
            for (Map.Entry<String, Map<String, List<TabRemoteEntry>>> nodeEntry : remoteSnapshots.entrySet()) {
                if (nodeEntry.getKey() == null) {
                    continue;
                }
                // 不保存 snapshot:* 虚拟节点，避免循环嵌套
                if (nodeEntry.getKey().startsWith("snapshot:")) {
                    continue;
                }
                remoteJson.add(nodeEntry.getKey(), encodeDefMap(nodeEntry.getValue()));
            }
        }
        root.add("remoteSnapshots", remoteJson);

        Path target = resolveFile(name);
        Files.writeString(target, gson.toJson(root), StandardCharsets.UTF_8);
        return target;
    }

    /** 读取快照文件并解析为 {@link Loaded}。 */
    public Loaded load(String name) throws IOException {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid snapshot name: " + name);
        }
        Path file = resolveFile(name);
        if (!Files.isRegularFile(file)) {
            throw new IOException("snapshot file not found: " + file);
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null) {
            throw new IOException("snapshot file is empty or malformed: " + file);
        }
        int version = root.has("version") ? root.get("version").getAsInt() : 1;
        long savedAt = root.has("savedAt") ? root.get("savedAt").getAsLong() : 0L;
        String serverId = root.has("serverId") ? root.get("serverId").getAsString() : "";

        Map<String, List<TabRemoteEntry>> localEntries = decodeDefMap(root.getAsJsonObject("localEntries"));

        Map<String, Map<String, List<TabRemoteEntry>>> remote = new LinkedHashMap<>();
        if (root.has("remoteSnapshots") && root.get("remoteSnapshots").isJsonObject()) {
            JsonObject remoteJson = root.getAsJsonObject("remoteSnapshots");
            for (Map.Entry<String, JsonElement> e : remoteJson.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    remote.put(e.getKey(), decodeDefMap(e.getValue().getAsJsonObject()));
                }
            }
        }
        return new Loaded(name, version, savedAt, serverId, localEntries, remote);
    }

    private JsonObject encodeDefMap(Map<String, List<TabRemoteEntry>> defMap) {
        JsonObject obj = new JsonObject();
        if (defMap == null) {
            return obj;
        }
        for (Map.Entry<String, List<TabRemoteEntry>> e : defMap.entrySet()) {
            JsonArray arr = new JsonArray();
            if (e.getValue() != null) {
                for (TabRemoteEntry entry : e.getValue()) {
                    arr.add(encodeEntry(entry));
                }
            }
            obj.add(e.getKey(), arr);
        }
        return obj;
    }

    private Map<String, List<TabRemoteEntry>> decodeDefMap(JsonObject obj) {
        Map<String, List<TabRemoteEntry>> out = new LinkedHashMap<>();
        if (obj == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonArray()) {
                continue;
            }
            List<TabRemoteEntry> list = new ArrayList<>();
            for (JsonElement elem : e.getValue().getAsJsonArray()) {
                if (elem.isJsonObject()) {
                    list.add(decodeEntry(elem.getAsJsonObject()));
                }
            }
            out.put(e.getKey(), list);
        }
        return out;
    }

    private JsonObject encodeEntry(TabRemoteEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("playerUuid", entry.playerUuid());
        obj.addProperty("playerName", entry.playerName());
        obj.addProperty("sortValue", entry.sortValue());
        obj.addProperty("sortStringValue", entry.sortStringValue());
        obj.add("sortValues", gson.toJsonTree(entry.sortValues()));
        obj.add("sortStringValues", gson.toJsonTree(entry.sortStringValues()));
        obj.addProperty("groupKey", entry.groupKey());
        obj.add("renderedPack", gson.toJsonTree(entry.renderedPack()));
        return obj;
    }

    @SuppressWarnings("unchecked")
    private TabRemoteEntry decodeEntry(JsonObject obj) {
        String uuid = obj.has("playerUuid") ? obj.get("playerUuid").getAsString() : "";
        String name = obj.has("playerName") ? obj.get("playerName").getAsString() : "";
        double sortValue = obj.has("sortValue") ? obj.get("sortValue").getAsDouble() : 0.0;
        String sortString = obj.has("sortStringValue") ? obj.get("sortStringValue").getAsString() : name;

        List<Double> sortValues = new ArrayList<>();
        if (obj.has("sortValues") && obj.get("sortValues").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("sortValues")) {
                sortValues.add(el.getAsDouble());
            }
        }
        if (sortValues.isEmpty()) {
            sortValues.add(sortValue);
        }
        List<String> sortStringValues = new ArrayList<>();
        if (obj.has("sortStringValues") && obj.get("sortStringValues").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("sortStringValues")) {
                sortStringValues.add(el.getAsString());
            }
        }
        if (sortStringValues.isEmpty()) {
            sortStringValues.add(sortString);
        }
        String groupKey = obj.has("groupKey") ? obj.get("groupKey").getAsString() : "";

        Object renderedPack = obj.has("renderedPack")
            ? convertJsonToJavaObject(obj.get("renderedPack"))
            : "";

        return new TabRemoteEntry(uuid, name, sortValue, sortString, sortValues, sortStringValues, groupKey, renderedPack);
    }

    /** Gson 默认会把 Object 反序列化为 LinkedTreeMap；这里把 JsonElement 转成原生 Map / List / 基本类型。 */
    private Object convertJsonToJavaObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            if (prim.isNumber()) {
                double d = prim.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return (long) d;
                }
                return d;
            }
            return prim.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement el : element.getAsJsonArray()) {
                list.add(convertJsonToJavaObject(el));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                map.put(e.getKey(), convertJsonToJavaObject(e.getValue()));
            }
            return map;
        }
        return "";
    }

    /** 反序列化结果。 */
    public record Loaded(
        String name,
        int version,
        long savedAt,
        String serverId,
        Map<String, List<TabRemoteEntry>> localEntries,
        Map<String, Map<String, List<TabRemoteEntry>>> remoteSnapshots
    ) {
    }
}
