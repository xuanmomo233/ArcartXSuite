package xuanmo.arcartxsuite.abyss.design;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.AbyssValidation;

/** Strict YAML parsing, conflict detection, staging, durable backups and rollback. */
public final class DesignWriter {
    public record Result(boolean ok, int objects, int deletions, List<String> errors) { }
    public static final List<String> FILES = AbyssValidation.FILES;
    private DesignWriter() { }
    private static final Object PUBLICATION_LOCK=new Object();

    public static String hash(Path path) throws Exception {
        if (!Files.exists(path)) return "-";
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    public static void captureBaseline(AbyssTemplate template, DesignSession session) {
        session.fileHashes().clear();
        Set<String> baselineFiles=new LinkedHashSet<>(FILES);
        for(DesignObject object:session.objects().values()) if(object.kind()==DesignObject.Kind.DOCUMENT) baselineFiles.add(object.file());
        for (String file : baselineFiles) {
            try { session.fileHashes().put(file, hash(template.dir().toPath().resolve(file))); }
            catch (Exception ex) { throw new IllegalStateException("无法读取设计基线: " + file, ex); }
        }
    }
    public static YamlConfiguration load(Path path) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        if (Files.exists(path)) yaml.load(path.toFile());
        return yaml;
    }
    public static Result save(AbyssTemplate template, DesignSession session, Logger logger) {
        try {
            Map<Path, byte[]> writes = prepareWrites(template,session);
            commit(writes, session.fileHashes(), template.dir().toPath());
            return new Result(true, (int) session.objects().values().stream().filter(DesignObject::dirty).count(),
                session.deletions().size(), List.of());
        } catch (Exception ex) {
            saveDraft(template, session, logger);
            logger.warning("[abyss] 设计未保存，编辑内容已保留: " + ex.getMessage());
            return new Result(false, 0, 0, List.of(String.valueOf(ex.getMessage())));
        }
    }

    public static Map<Path,byte[]> prepareWrites(AbyssTemplate template,DesignSession session) throws Exception {
            Map<String,YamlConfiguration> files=prepare(template,session);
            Map<Path,byte[]> writes=new LinkedHashMap<>();
            for (var entry : files.entrySet()) {
                Path path = template.dir().toPath().resolve(entry.getKey()).toAbsolutePath().normalize();
                DesignObject document = session.get("document:" + entry.getKey());
                if (document != null && document.dirty() && document.strList("content").isEmpty()
                    && !entry.getKey().equals("config.yml")) {
                    if (Files.exists(path)) writes.put(path, null);
                    continue;
                }
                byte[] bytes = entry.getValue().saveToString().getBytes(StandardCharsets.UTF_8);
                if (!Files.exists(path) && entry.getValue().getKeys(false).isEmpty()) continue;
                if (!Files.exists(path) || !load(path).saveToString().equals(entry.getValue().saveToString())) writes.put(path, bytes);
            }
            return writes;
    }
    /** A readable recovery copy, separate from published definitions and their conflict baselines. */
    public static void saveDraft(AbyssTemplate template, DesignSession session, Logger logger) {
        if (!session.hasDirty()) return;
        try {
            YamlConfiguration draft = new YamlConfiguration();
            draft.set("dungeon", template.id()); draft.set("player", session.playerId().toString());
            draft.set("world-save-pending", session.saving() || session.worldSnapshot());
            List<Map<String, Object>> objects = new ArrayList<>();
            for (DesignObject object : session.objects().values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", object.key()); row.put("kind", object.kind().name());
                row.put("id", object.id()); row.put("original-id", session.originalId(object.key()));
                row.put("file", object.file()); row.put("path", object.path());
                row.put("dirty", object.dirty()); row.put("values", DesignValues.copy(object.values()));
                objects.add(row);
            }
            draft.set("objects", objects); draft.set("deletions", new LinkedHashMap<>(session.deletions()));
            Path folder = template.dir().toPath().resolve(".design-drafts"); Files.createDirectories(folder);
            Path target = folder.resolve(System.currentTimeMillis() + "-" + session.designToken() + ".yml");
            Files.writeString(target, draft.saveToString(), StandardCharsets.UTF_8);
            logger.info("[abyss] 未发布设计草稿已保留: " + target);
        } catch (Exception failure) { logger.warning("[abyss] 草稿恢复副本写入失败: " + failure.getMessage()); }
    }
    public static Map<String, YamlConfiguration> prepare(AbyssTemplate template, DesignSession session) throws Exception {
        Map<String, YamlConfiguration> files = AbyssValidation.read(template.dir());
        Set<String> documents = new HashSet<>();
        for (DesignObject o : session.objects().values()) {
            if (!o.dirty() || o.kind() != DesignObject.Kind.DOCUMENT) continue;
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.loadFromString(String.join("\n", o.strList("content")));
            files.put(o.file(), yaml); documents.add(o.file());
        }
        Map<String, String> roomRenames = new LinkedHashMap<>();
        Set<String> addresses = new HashSet<>();
        for (DesignObject o : session.objects().values()) {
            if (o.kind() == DesignObject.Kind.DOCUMENT) continue;
            if (!addresses.add(o.file() + ":" + o.path() + ":" + o.id())) throw new IOException("对象ID冲突: " + o.id());
            if (o.dirty() && documents.contains(o.file())) throw new IOException(o.file() + " 同时存在文件编辑和对象编辑，请先分别保存");
            if (o.kind() == DesignObject.Kind.ROOM && !session.originalId(o.key()).equals(o.id()))
                roomRenames.put(session.originalId(o.key()), o.id());
        }
        // Parent snapshots are applied before child mutations, including renames and deletions.
        for (DesignObject o : session.objects().values()) {
            if (!o.dirty() || o.kind() == DesignObject.Kind.DOCUMENT
                || o.kind() == DesignObject.Kind.ROOM_GROUP || o.kind() == DesignObject.Kind.FLOOR) continue;
            YamlConfiguration yaml = files.get(o.file());
            if (o.kind() == DesignObject.Kind.SETTLEMENT) {
                String prefix = join(o.path(), o.id());
                o.values().forEach((key, value) -> {
                    if (key.equals("design-revive-limit")) yaml.set("requirements.revive-limit", value);
                    else if (key.startsWith("design-native-"))
                        yaml.set("requirements.native-rescue." + key.substring("design-native-".length()), value);
                    else yaml.set(prefix + "." + key, value);
                });
                continue;
            }
            String old = session.originalId(o.key());
            if (!old.equals(o.id())) yaml.set(join(o.path(), old), null);
            if (o.kind() == DesignObject.Kind.SCRIPT) {
                if (DesignManager.LIFECYCLE_SEGMENTS.contains(o.id()) && o.id().contains("-"))
                    yaml.set(o.id().replace('-', '_'), null);
                yaml.set(o.id(), o.strList("script"));
                continue;
            }
            Map<String, Object> values = DesignValues.copy(o.values());
            if (o.kind() == DesignObject.Kind.STAGE) {
                if (values.containsKey("design-condition")) {
                    values.remove("complete_when");
                    values.remove("when");
                    String condition = StageForm.condition(o.str("design-condition"), o.str("design-condition-ref"));
                    if (condition.isBlank()) values.remove("complete-when");
                    else values.put("complete-when", condition);
                }
                if (Boolean.TRUE.equals(values.get("design-entry"))) yaml.set("entry", o.id());
                else if (old.equals(yaml.getString("entry"))) yaml.set("entry", null);
                values.keySet().removeIf(key -> key.startsWith("design-"));
            }
            values.keySet().removeIf(key -> key.startsWith("design-action-"));
            yaml.set(join(o.path(), o.id()), values);
        }
        // Bukkit set(path, Map) does not create child sections; materialize parents before child writes.
        materialize(files);
        for (var deletion : session.deletions().entrySet()) {
            DesignObject.Kind kind = kindOfKey(deletion.getKey());
            if (kind == null) continue;
            DesignObject original = session.originalObject(deletion.getKey());
            String path = original != null ? original.path() : kind.path();
            String file = original != null ? original.file() : kind.file();
            if (documents.contains(file)) {
                throw new IOException(file + " 同时存在文件编辑和对象删除，请先分别保存");
            }
            if (kind == DesignObject.Kind.FLOOR) continue;
            if (kind == DesignObject.Kind.STAGE && deletion.getValue().equals(files.get(file).getString("entry")))
                files.get(file).set("entry", null);
            if (kind == DesignObject.Kind.ROOM_GROUP) {
                String compound = deletion.getKey().substring(6);
                String room = compound.substring(0, compound.indexOf('.'));
                path = "rooms." + roomRenames.getOrDefault(room, room) + ".groups";
            }
            files.get(file).set(join(path, deletion.getValue()), null);
        }
        boolean floorsChanged = session.objects().values().stream()
            .anyMatch(o -> o.kind() == DesignObject.Kind.FLOOR && o.dirty())
            || session.deletions().keySet().stream().anyMatch(key -> key.startsWith("floor:"));
        if (floorsChanged) {
            var rows = session.floorsInOrder().stream()
                .map(o -> {
                    Map<String, Object> row = DesignValues.copy(o.values());
                    row.keySet().removeIf(key -> key.startsWith("design-"));
                    row.put("id", o.id());
                    return row;
                }).toList();
            files.get("floors.yml").set("floors", rows);
        }
        for (DesignObject o : session.objects().values()) {
            if (o.kind() != DesignObject.Kind.ROOM_GROUP || !o.dirty()) continue;
            String oldRoom = o.path().substring(6, o.path().length() - 7);
            if (session.deletions().containsKey("room:" + oldRoom)) throw new IOException("房间已删除，不能继续修改其怪组: " + oldRoom);
            String path = "rooms." + roomRenames.getOrDefault(oldRoom, oldRoom) + ".groups";
            YamlConfiguration yaml = files.get(o.file());
            String old = session.originalId(o.key());
            if (!old.equals(o.id())) yaml.set(join(path, old), null);
            Map<String, Object> values = DesignValues.copy(o.values());
            values.keySet().removeIf(key -> key.startsWith("design-action-"));
            yaml.set(join(path, o.id()), values);
        }
        for (var entry : files.entrySet()) {
            YamlConfiguration parsed = new YamlConfiguration();
            parsed.options().parseComments(true);
            parsed.loadFromString(entry.getValue().saveToString()); entry.setValue(parsed);
        }
        for (DesignObject o : session.objects().values()) {
            String old = session.originalId(o.key());
            if (o.kind() == DesignObject.Kind.DOCUMENT || old.equals(o.id())) continue;
            DesignReferences.migrate(files, o.kind(), old, o.id(), o.path(), roomRenames);
        }
        materialize(files);
        for (var deletion : session.deletions().entrySet()) {
            DesignObject.Kind kind = kindOfKey(deletion.getKey());
            DesignObject original = session.originalObject(deletion.getKey());
            boolean referenced = kind == DesignObject.Kind.ROOM_GROUP && original != null
                ? DesignReferences.roomGroupReferenced(files, deletion.getValue(), original.path(), roomRenames)
                : kind != null && DesignReferences.isReferenced(files, kind, deletion.getValue());
            if (referenced)
                throw new IOException("对象仍被流程引用，请先移除引用再删除: " + deletion.getValue());
        }
        if (session.worldSnapshot()) {
            YamlConfiguration config = files.get("config.yml");
            config.set("world.template", "world"); config.set("world-source", null);
            config.set("world_source", null); config.set("world.design-snapshot", true);
        }
        AbyssValidation.validateFiles(files,template.dir());
        return files;
    }
    private static void materialize(Map<String, YamlConfiguration> files) throws Exception {
        for (var entry : files.entrySet()) {
            YamlConfiguration parsed = new YamlConfiguration();
            parsed.options().parseComments(true);
            parsed.loadFromString(entry.getValue().saveToString());
            entry.setValue(parsed);
        }
    }
    /** Back up all original bytes before the first replacement; restore all targets on failure. */
    public static void commit(Map<Path, byte[]> writes, Map<String, String> baseline, Path root) throws Exception {
        commit(writes, baseline, root, DesignWriter::replace);
    }
    @FunctionalInterface interface Mover { void move(Path from, Path to) throws IOException; }
    static void commit(Map<Path, byte[]> writes, Map<String, String> baseline, Path root, Mover mover) throws Exception {
        synchronized(PUBLICATION_LOCK) { commitLocked(writes,baseline,root,mover); }
    }
    private static void commitLocked(Map<Path,byte[]> writes,Map<String,String> baseline,Path root,Mover mover) throws Exception {
        for (var entry : baseline.entrySet()) {
            if (!entry.getValue().equals(hash(root.resolve(entry.getKey()))))
                throw new IOException(entry.getKey() + " 已被外部修改，请保留编辑内容并重新进入设计");
        }
        if (writes.isEmpty()) return;
        Path backup = root.resolve(".design-backups").resolve(java.time.Instant.now().toEpochMilli()
            + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(backup);
        Map<Path, Path> staged = new LinkedHashMap<>();
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        List<Path> committed = new ArrayList<>();
        try {
            for (var entry : writes.entrySet()) {
                Path target = entry.getKey();
                byte[] original = Files.exists(target) ? Files.readAllBytes(target) : null;
                originals.put(target, original);
                if (original != null) {
                    String name=target.toAbsolutePath().normalize().getParent().equals(root.toAbsolutePath().normalize())
                        ? target.getFileName().toString()
                        : UUID.nameUUIDFromBytes(target.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8))+"-"+target.getFileName();
                    Files.write(backup.resolve(name),original);
                }
                if (entry.getValue() == null) staged.put(target, null);
                else {
                    Path temp = Files.createTempFile(root, ".design-", ".tmp");
                    staged.put(target, temp); Files.write(temp, entry.getValue());
                }
            }
            for (var entry : baseline.entrySet()) {
                if (!entry.getValue().equals(hash(root.resolve(entry.getKey()))))
                    throw new IOException(entry.getKey() + " 在保存期间被修改，已取消写入");
            }
            for (var entry : staged.entrySet()) {
                committed.add(entry.getKey());
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else mover.move(entry.getValue(), entry.getKey());
            }
        } catch (Exception failure) {
            java.util.Collections.reverse(committed);
            for (Path target : committed) {
                try {
                    if (originals.get(target) == null) Files.deleteIfExists(target);
                    else {
                        Path recovery = Files.createTempFile(root, ".restore-", ".tmp");
                        Files.write(recovery, originals.get(target)); replace(recovery, target);
                    }
                } catch (Exception rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally { for (Path temp : staged.values()) if (temp != null) Files.deleteIfExists(temp); }
    }
    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String join(String path, String id) { return path.isEmpty() ? id : path + "." + id; }
    private static DesignObject.Kind kindOfKey(String key) {
        int i = key.indexOf(':');
        if (i < 1) return null;
        if (key.startsWith("group:") && key.substring(i + 1).contains(".")) return DesignObject.Kind.ROOM_GROUP;
        try { return DesignObject.Kind.valueOf(key.substring(0, i).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
