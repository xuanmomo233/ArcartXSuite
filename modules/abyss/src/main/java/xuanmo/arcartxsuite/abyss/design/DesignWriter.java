package xuanmo.arcartxsuite.abyss.design;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/**
 * 设计保存：把会话快照写回地牢目录 YAML。
 * <p>
 * 策略：按文件分组 → 原文件 load → 对每个脏对象整节 set（快照即全量，等于整节覆盖）
 * → 删除名单 remove → save。同义 snake 键在写回前清除，统一落 kebab 风格。
 */
public final class DesignWriter {

    public record Result(boolean ok, int objects, int deletions, List<String> errors) { }

    private DesignWriter() { }

    /** 写回全部脏对象 + 删除名单。返回统计。 */
    public static Result save(AbyssTemplate template, DesignSession session, Logger logger) {
        // file → {path → 对象集}；删除 key → 原 id
        Map<String, Map<String, List<DesignObject>>> byFile = new LinkedHashMap<>();
        Map<String, List<String[]>> delsByFile = new LinkedHashMap<>(); // file → [path,id]
        List<String> errors = new ArrayList<>();
        int count = 0;

        for (DesignObject o : session.objects().values()) {
            if (!o.dirty()) continue;
            byFile.computeIfAbsent(o.file(), k -> new LinkedHashMap<>())
                .computeIfAbsent(o.path(), k -> new ArrayList<>())
                .add(o);
            count++;
        }
        // 删除名单按对象 kind 反查文件
        for (var e : session.deletions().entrySet()) {
            String key = e.getKey();
            String origId = e.getValue();
            DesignObject.Kind kind = kindOfKey(key);
            if (kind == null) continue;
            String path = kind.path();
            // 房内怪组 key 形如 group:room.gid → path rooms.<room>.groups
            if (kind == DesignObject.Kind.ROOM_GROUP && key.startsWith("group:")) {
                String compound = key.substring(6);
                int dot = compound.indexOf('.');
                if (dot > 0) path = "rooms." + compound.substring(0, dot) + ".groups";
            }
            delsByFile.computeIfAbsent(kind.file(), k -> new ArrayList<>())
                .add(new String[]{path, origId});
        }

        for (var fe : byFile.entrySet()) {
            File f = new File(template.dir(), fe.getKey());
            try {
                YamlConfiguration yml = f.isFile()
                    ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
                for (var pe : fe.getValue().entrySet()) {
                    for (DesignObject o : pe.getValue()) {
                        String fullPath = pe.getKey().isEmpty() ? o.id()
                            : pe.getKey() + "." + o.id();
                        // 重命名：旧键删除
                        String orig = session.originalId(o.key());
                        String origPath = pe.getKey().isEmpty() ? orig
                            : pe.getKey() + "." + orig;
                        if (!orig.equals(o.id())) {
                            yml.set(origPath, null);
                        }
                        // 写回：整节覆盖（快照即全量）+ 清 snake 别名
                        normalizeKeys(o.values());
                        yml.set(fullPath, o.values());
                    }
                }
                // 本文件删除名单
                List<String[]> dels = delsByFile.remove(fe.getKey());
                if (dels != null) {
                    for (String[] d : dels) {
                        yml.set(d[0].isEmpty() ? d[1] : d[0] + "." + d[1], null);
                    }
                }
                yml.save(f);
            } catch (IOException | RuntimeException ex) {
                errors.add(fe.getKey() + ": " + ex.getMessage());
                logger.warning("[abyss] 设计保存失败 " + fe.getKey() + ": " + ex.getMessage());
            }
        }
        // 删除名单落在未变动文件里的情况
        for (var fe : delsByFile.entrySet()) {
            File f = new File(template.dir(), fe.getKey());
            if (!f.isFile()) continue;
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                for (String[] d : fe.getValue()) {
                    yml.set(d[0].isEmpty() ? d[1] : d[0] + "." + d[1], null);
                }
                yml.save(f);
            } catch (IOException | RuntimeException ex) {
                errors.add(fe.getKey() + ": " + ex.getMessage());
            }
        }
        int dels = session.deletions().size();
        return new Result(errors.isEmpty(), count, dels, errors);
    }

    private static DesignObject.Kind kindOfKey(String key) {
        int i = key.indexOf(':');
        if (i <= 0) return null;
        String prefix = key.substring(0, i);
        if ("group".equals(prefix) && key.substring(i + 1).contains(".")) {
            return DesignObject.Kind.ROOM_GROUP;
        }
        try {
            return DesignObject.Kind.valueOf(prefix.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 递归把 snake_case 键归一为 kebab-case（同名 kebab 已存在时不覆盖）。 */
    @SuppressWarnings("unchecked")
    private static void normalizeKeys(Map<String, Object> map) {
        List<String> renames = new ArrayList<>();
        for (String k : new ArrayList<>(map.keySet())) {
            if (k.contains("_")) {
                String kebab = k.replace('_', '-');
                if (!map.containsKey(kebab)) renames.add(k);
            }
            Object v = map.get(k);
            if (v instanceof Map<?, ?> m) {
                normalizeKeys((Map<String, Object>) m);
            } else if (v instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m2) normalizeKeys((Map<String, Object>) m2);
                }
            }
        }
        for (String k : renames) {
            map.put(k.replace('_', '-'), map.remove(k));
        }
    }
}
