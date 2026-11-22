package xuanmo.arcartxsuite.regions.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.util.ActiveFileReplacement;
import xuanmo.arcartxsuite.regions.model.Region;

/** 区域与变更记录共用一次原子文件替换。所有运行时写入由同一后台线程执行。 */
public final class RegionsRepository {
    private static final int HISTORY_LIMIT = 100;

    private final Path file;
    private String diskText;
    private boolean retired;
    private CompletionStage<Void> previousWrite;
    private volatile Snapshot snapshot = new Snapshot(List.of(), List.of(), 1);

    public RegionsRepository(Path file) {
        this.file = file.toAbsolutePath();
    }

    public List<Region> loadAllRegions() throws IOException, InvalidConfigurationException {
        var replacement = ActiveFileReplacement.current(file);
        if (replacement == null) return loadFromDisk();
        previousWrite = replacement.finished();
        return read(replacement.previousText());
    }

    public CompletionStage<Void> previousWrite() { return previousWrite; }

    public List<Region> loadFromDisk() throws IOException, InvalidConfigurationException {
        return read(Files.notExists(file) ? null : Files.readString(file));
    }

    private List<Region> read(String text) throws InvalidConfigurationException {
        if (text == null) {
            diskText = null;
            snapshot = new Snapshot(List.of(), List.of(), 1);
            return snapshot.regions();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        List<Region> regions = RegionSnapshots.read(yaml);
        List<AuditEntry> history = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("history")) {
            if (!(entry.get("id") instanceof Number id) || !(entry.get("time") instanceof Number time)
                    || !(entry.get("actor") instanceof String actor) || !(entry.get("action") instanceof String action)) {
                throw new IllegalArgumentException("regions.yml 中的变更记录格式无效。");
            }
            history.add(new AuditEntry(new AuditSummary(id.longValue(), time.longValue(), actor, action),
                RegionSnapshots.readEntries(entry.get("before")), RegionSnapshots.readEntries(entry.get("after"))));
        }
        long lastId = history.stream().mapToLong(entry -> entry.summary().id()).max().orElse(0);
        diskText = text;
        snapshot = new Snapshot(List.copyOf(regions), List.copyOf(history),
            Math.max(lastId + 1, yaml.getLong("next-history-id", 1)));
        return snapshot.regions();
    }

    public void applyChanges(String actor, String action, List<Region> before, List<Region> after) throws IOException {
        Snapshot current = snapshot;
        List<Region> regions = new ArrayList<>(current.regions());
        regions.removeAll(before);
        regions.addAll(after);
        regions.sort(Comparator.comparing(Region::key));
        List<AuditEntry> history = new ArrayList<>();
        if ("purge-player".equals(action) || "purge-all-players".equals(action)) {
            history.addAll(current.history().stream()
                .filter(entry -> !actor.equals("*") && !mentions(entry, actor)).toList());
        } else {
            history.add(new AuditEntry(
                new AuditSummary(current.nextId(), System.currentTimeMillis(), actor, action), before, after));
            history.addAll(current.history().stream().limit(HISTORY_LIMIT - 1L).toList());
        }
        try (var replacement = beginReplacement()) {
            save(new Snapshot(List.copyOf(regions), List.copyOf(history), current.nextId() + 1));
        }
    }

    private synchronized ActiveFileReplacement beginReplacement() {
        if (retired) throw new IllegalStateException("区域模块已停用，本次变更未保存。");
        return ActiveFileReplacement.begin(file, diskText);
    }

    public synchronized void retire() {
        retired = true;
    }

    private static boolean mentions(AuditEntry entry, String uuid) {
        var player = java.util.UUID.fromString(uuid);
        return entry.summary().actor().contains(uuid)
            || java.util.stream.Stream.concat(entry.before().stream(), entry.after().stream())
                .anyMatch(region -> region.isMember(player));
    }

    private void save(Snapshot next) throws IOException {
        YamlConfiguration yaml = RegionSnapshots.document(next.regions());
        yaml.options().setHeader(List.of(
            "区域和世界默认规则。修改后执行 /axs reload regions。",
            "type: global 表示整个世界。flags 中 true 为允许，false 为禁止，省略表示继承。",
            "greeting 和 farewell 可使用 {state: true, text: '自定义消息'}。",
            "世界默认规则的 blocked-interactions 可按 Bukkit 材质名禁用方块交互。"));
        yaml.set("next-history-id", next.nextId());
        yaml.set("history", next.history().stream().map(entry -> Map.of(
            "id", entry.summary().id(), "time", entry.summary().createdAt(),
            "actor", entry.summary().actor(), "action", entry.summary().action(),
            "before", entry.before().stream().map(RegionSnapshots::fields).toList(),
            "after", entry.after().stream().map(RegionSnapshots::fields).toList())).toList());
        String text = yaml.saveToString();
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "regions-", ".tmp");
        try {
            Files.writeString(temp, text);
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            String currentText = Files.notExists(file) ? null : Files.readString(file);
            if (!Objects.equals(diskText, currentText)) {
                throw new IllegalStateException("regions.yml 已被手动修改，请先执行 /axs reload regions。");
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        diskText = text;
        snapshot = next;
    }

    public List<AuditSummary> history(int limit) {
        return snapshot.history().stream().limit(limit).map(AuditEntry::summary).toList();
    }

    public AuditEntry audit(long id) {
        return snapshot.history().stream().filter(entry -> entry.summary().id() == id).findFirst().orElse(null);
    }

    private record Snapshot(List<Region> regions, List<AuditEntry> history, long nextId) {}
    public record AuditSummary(long id, long createdAt, String actor, String action) {}
    public record AuditEntry(AuditSummary summary, List<Region> before, List<Region> after) {}
}
