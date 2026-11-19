package xuanmo.arcartxsuite.regions.service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.capability.RegionProtectionQueryable.Reason;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;
import xuanmo.arcartxsuite.regions.storage.RegionSnapshots;
import xuanmo.arcartxsuite.regions.service.RegionWriteQueue.Transaction;

/** 主线程上的区域状态和保护判定。只在 YAML 提交后替换查询使用的区域。 */
public final class RegionManager {

    private static final Comparator<Region> RULE_ORDER = Comparator
        .comparingInt((Region region) -> tier(region.kind()))
        .thenComparing(Comparator.comparingInt(Region::priority).reversed())
        .thenComparing(Region::key);
    private static final int MAX_INDEX_CELLS = 4096;
    private static final int SEND_CHAT_RULE = 1;
    private static final int RECEIVE_CHAT_RULE = 2;
    private static final Decision BYPASS = new Decision(RegionFlag.State.ALLOW, null, null, Reason.BYPASS);

    private final RegionsRepository repository;
    private final RegionsConfiguration config;
    private final Logger logger;
    private final Map<Key, Region> regions = new HashMap<>();
    private final Map<UUID, Region> regionsById = new HashMap<>();
    private final Map<UUID, IndexedRegion> indexedRegions = new HashMap<>();
    private final Map<String, Map<Cell, List<IndexedRegion>>> index = new HashMap<>();
    private final Map<String, List<IndexedRegion>> largeRegions = new HashMap<>();
    private final Map<String, Region> globalRegions = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private List<Region> orderedRegions;
    private List<Region> boundedRegions;
    private final Map<Region, Map<RegionFlag, Region>> ruleSources = new IdentityHashMap<>();
    private final Map<Key, List<Region>> childrenByParent = new HashMap<>();
    private final Set<UUID> sendChatDefinitions = new HashSet<>();
    private final Set<UUID> receiveChatDefinitions = new HashSet<>();
    private RegionMemberships membershipIndex = new RegionMemberships(List.of());
    private long revision;
    private volatile int chatRules;
    private final RegionWriteQueue writes;
    private final Executor primaryThread;
    private final Executor writer;
    private boolean closed;
    private Consumer<Set<String>> afterPublication = worlds -> {};
    private final RegionTimings timings = new RegionTimings();

    public RegionManager(RegionsRepository repository, RegionsConfiguration config, Logger logger,
                         Executor primaryThread, Executor writer) {
        this.primaryThread = primaryThread;
        this.writer = writer;
        writes = new RegionWriteQueue(primaryThread, writer);
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public void loadAll() throws IOException, InvalidConfigurationException {
        install(repository.loadAllRegions());
        var previousWrite = repository.previousWrite();
        if (previousWrite == null) return;
        writes.suspend();
        previousWrite.thenApplyAsync(ignored -> {
            try {
                return repository.loadFromDisk();
            } catch (IOException | InvalidConfigurationException error) {
                throw new CompletionException(error);
            }
        }, writer).whenComplete((loaded, error) -> primaryThread.execute(() -> finishReload(loaded, error)));
    }

    private void finishReload(List<Region> loaded, Throwable error) {
        if (closed) return;
        Set<String> affectedWorlds = new HashSet<>();
        regions.values().forEach(region -> affectedWorlds.add(region.world()));
        try {
            if (error != null) throw new CompletionException(error);
            install(loaded);
            loaded.forEach(region -> affectedWorlds.add(region.world()));
        } catch (RuntimeException failure) {
            writes.close();
            logger.log(java.util.logging.Level.SEVERE, "应用磁盘中的区域规则失败。请检查 regions.yml 后重载。", failure);
            return;
        }
        writes.resume();
        notifyFlightUpdate(affectedWorlds);
    }

    private void install(List<Region> loaded) {
        var definitions = new HashMap<Key, Region>();
        for (Region region : loaded) {
            if (definitions.putIfAbsent(key(region), region) != null) {
                throw new IllegalArgumentException("同世界存在大小写不同的同名区域：" + region.key());
            }
            if (!validId(region.id()) || region.world().contains(":")
                || (region.kind() == Region.Kind.GLOBAL) != region.id().equalsIgnoreCase(config.defaults().globalRegionId())
                || region.kind() == Region.Kind.CLAIM && region.owners().isEmpty() && region.ownerGroups().isEmpty()) {
                throw new IllegalArgumentException("区域定义无效：" + region.key());
            }
        }
        validateIdentities(definitions);
        for (Region region : loaded) validateParent(region, definitions);
        regions.clear();
        regionsById.clear();
        indexedRegions.clear();
        globalRegions.clear();
        largeRegions.clear();
        index.clear();
        orderedRegions = null;
        boundedRegions = null;
        for (Region region : loaded) put(region);
        rebuildDerivedState();
        logger.info("已加载 " + loaded.size() + " 个区域。");
    }

    private void rebuildDerivedState() {
        ruleSources.clear();
        childrenByParent.clear();
        sendChatDefinitions.clear();
        receiveChatDefinitions.clear();
        var ancestry = new ArrayDeque<Region>();
        for (Region region : regions.values()) {
            Key parent = parentKey(region);
            if (parent != null) childrenByParent.computeIfAbsent(parent, unused -> new ArrayList<>()).add(region);
            updateChatDefinitions(region);
            Region cursor = region;
            while (cursor != null && !ruleSources.containsKey(cursor)) {
                ancestry.push(cursor);
                cursor = regions.get(parentKey(cursor));
            }
            while (!ancestry.isEmpty()) resolveRules(ancestry.pop());
        }
        membershipIndex = new RegionMemberships(getBoundedRegions());
        revision++;
        refreshChatRules();
    }

    private void resolveRules(Region region) {
        Region parent = regions.get(parentKey(region));
        Map<RegionFlag, Region> inherited = parent == null ? Map.of() : ruleSources.get(parent);
        if (region.flags().isEmpty()) {
            ruleSources.put(region, inherited);
        } else {
            var sources = new EnumMap<RegionFlag, Region>(RegionFlag.class);
            sources.putAll(inherited);
            for (RegionFlag flag : region.flags().keySet()) sources.put(flag, region);
            ruleSources.put(region, sources);
        }
    }

    private void publishFlags(Region before, Region after) {
        regions.put(key(after), after);
        regionsById.put(after.uid(), after);
        if (after.kind() == Region.Kind.GLOBAL) globalRegions.put(after.world(), after);
        else indexedRegions.get(after.uid()).current = after;
        orderedRegions = null;
        boundedRegions = null;
        ruleSources.remove(before);

        Key parent = parentKey(after);
        if (parent != null) {
            List<Region> siblings = childrenByParent.get(parent);
            siblings.set(siblings.indexOf(before), after);
        }
        var affected = new ArrayDeque<Region>();
        affected.add(after);
        while (!affected.isEmpty()) {
            Region region = affected.remove();
            resolveRules(region);
            List<Region> children = childrenByParent.get(key(region));
            if (children != null) affected.addAll(children);
        }
        updateChatDefinitions(after);
        refreshChatRules();
        revision++;
        if (before.getFlag(RegionFlag.FLY) != after.getFlag(RegionFlag.FLY)) {
            notifyFlightUpdate(Set.of(after.world()));
        }
    }

    private static Key parentKey(Region region) {
        return region.parentId() == null ? null : new Key(region.world(), region.parentId().toLowerCase(Locale.ROOT));
    }

    public long revision() { return revision; }

    public RegionMemberships membershipIndex() { return membershipIndex; }

    public Selection getSelection(UUID player) {
        return selections.computeIfAbsent(player, k -> new Selection());
    }

    public void clearSelection(UUID player) { selections.remove(player); }

    public void cleanup(UUID player) { clearSelection(player); }

    public Region getRegion(String id, String world) {
        return regions.get(new Key(world, id.toLowerCase(Locale.ROOT)));
    }

    public Region getRegion(UUID uid) {
        return regionsById.get(uid);
    }

    /** 无世界前缀时优先当前世界，跨世界同名时要求显式指定。 */
    public Region findRegion(String ref, String preferredWorld) {
        int separator = ref.indexOf(':');
        if (separator >= 0) {
            return getRegion(ref.substring(separator + 1), ref.substring(0, separator));
        }
        if (preferredWorld != null) {
            Region local = getRegion(ref, preferredWorld);
            if (local != null) return local;
        }
        Region match = null;
        for (Region region : regions.values()) {
            if (!region.id().equalsIgnoreCase(ref)) continue;
            if (match != null) throw new IllegalArgumentException("存在同名区域，请使用 世界:区域ID。");
            match = region;
        }
        return match;
    }

    public List<Region> getRegionsInWorld(String world) {
        return getAllRegions().stream().filter(region -> region.world().equals(world)).toList();
    }

    public List<Region> getAllRegions() {
        if (orderedRegions == null) {
            orderedRegions = regions.values().stream().sorted(Comparator.comparing(Region::key)).toList();
        }
        return orderedRegions;
    }

    public List<Region> getBoundedRegions() {
        if (boundedRegions == null) boundedRegions = getAllRegions().stream()
            .filter(region -> region.kind() != Region.Kind.GLOBAL).toList();
        return boundedRegions;
    }

    public RegionFlag.State inheritedRule(Region region, RegionFlag flag) {
        Region parent = region.parentId() == null ? null : getRegion(region.parentId(), region.world());
        Region source = parent == null ? null : sourceOf(parent, flag);
        if (source == null && region.kind() != Region.Kind.GLOBAL) source = getGlobal(region.world());
        return source == null ? RegionFlag.State.NONE : source.getFlag(flag);
    }

    public Region getGlobal(String world) { return globalRegions.get(world); }

    public boolean hasChatRules() { return chatRules != 0; }
    public boolean hasSendChatRules() { return (chatRules & SEND_CHAT_RULE) != 0; }
    public boolean hasReceiveChatRules() { return (chatRules & RECEIVE_CHAT_RULE) != 0; }

    private void updateChatDefinitions(Region region) {
        updateChatDefinition(sendChatDefinitions, region, RegionFlag.SEND_CHAT);
        updateChatDefinition(receiveChatDefinitions, region, RegionFlag.RECEIVE_CHAT);
    }

    private static void updateChatDefinition(Set<UUID> definitions, Region region, RegionFlag flag) {
        if (region.getFlag(flag) == RegionFlag.State.NONE) definitions.remove(region.uid());
        else definitions.add(region.uid());
    }

    private void refreshChatRules() {
        chatRules = (sendChatDefinitions.isEmpty() ? 0 : SEND_CHAT_RULE)
            | (receiveChatDefinitions.isEmpty() ? 0 : RECEIVE_CHAT_RULE);
    }

    public Region selectionRegion(Player player, String id) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) throw new IllegalArgumentException("请先在同一世界设置两个选区点。");
        if (selection.volume() > config.selection().maxVolume()) {
            throw new IllegalArgumentException("选区体积超过限制 " + config.selection().maxVolume() + "。");
        }
        return new Region(id, selection.world(),
            selection.x1(), selection.y1(), selection.z1(), selection.x2(), selection.y2(), selection.z2());
    }

    public List<Region> overlaps(Region area, Region excluded) {
        return getRegionsInWorld(area.world()).stream()
            .filter(other -> other != excluded && other.kind() != Region.Kind.GLOBAL && area.intersects(other))
            .toList();
    }

    public CompletableFuture<Region> createRegion(Player actor, String name, Consumer<Region> configure) {
        String id = newId(name);
        Region region = selectionRegion(actor, id);
        Runnable clear = clearSelectionOnSave(actor);
        return writes.submit(() -> {
            region.setUid(UUID.randomUUID());
            boolean admin = isAdmin(actor);
            region.setKind(admin ? Region.Kind.MANAGED : Region.Kind.CLAIM);
            region.setPriority(config.defaults().priority());
            region.addOwner(actor.getUniqueId());
            configure.accept(region);
            if (!admin) {
                validateClaimFlags(null, region);
                validateClaimSpace(region, null);
                validateOwnerQuota(actor, actor.getUniqueId());
            }
            return commit(actor, "create", List.of(), List.of(region), region, clear);
        });
    }

    private Runnable clearSelectionOnSave(Player actor) {
        UUID id = actor.getUniqueId();
        Selection selection = selections.get(id);
        long version = selection.version();
        return () -> {
            if (selection.version() == version) selections.remove(id, selection);
        };
    }

    public CompletableFuture<Region> createGlobal(CommandSender actor, String world) {
        return writes.submit(() -> {
            requireAdmin(actor);
            Region existing = getGlobal(world);
            if (existing != null) return Transaction.unchanged(existing);
            Region global = newGlobal(world);
            return commit(actor, "world-default", List.of(), List.of(global), global, () -> {});
        });
    }

    public CompletableFuture<Region> editWorld(CommandSender actor, String world, Consumer<Region> edit) {
        return writes.submit(() -> {
            requireAdmin(actor);
            Region existing = getGlobal(world);
            Region updated = existing == null ? newGlobal(world) : existing.copy(existing.id());
            edit.accept(updated);
            return commit(actor, "world-rule", existing == null ? List.of() : List.of(existing),
                List.of(updated), updated, () -> {});
        });
    }

    private Region newGlobal(String world) {
        String id = config.defaults().globalRegionId();
        if (!validId(id) || world.isBlank() || world.contains(":")) {
            throw new IllegalArgumentException("世界名称或默认区域 ID 无效。");
        }
        Region global = new Region(id, world, 0, 0, 0, 0, 0, 0);
        global.setUid(UUID.randomUUID());
        global.setKind(Region.Kind.GLOBAL);
        return global;
    }

    public boolean canManage(CommandSender actor, Region region) {
        if (isAdmin(actor)) return true;
        return region.kind() == Region.Kind.CLAIM && actor instanceof Player player
            && (region.isOwner(player.getUniqueId())
                || region.ownerGroups().stream().anyMatch(group -> player.hasPermission("axs.regions.group." + group)));
    }

    public CompletableFuture<Region> update(CommandSender actor, Region region, String operation, Consumer<Region> edit) {
        return writes.submit(() -> updateChange(actor, current(region), operation, edit, () -> {}));
    }

    private Region current(Region previous) {
        Region region = getRegion(previous.id(), previous.world());
        if (region == null || !region.uid().equals(previous.uid())) {
            throw new IllegalArgumentException("区域已变更，请刷新后重试。");
        }
        return region;
    }

    private Transaction<Region> updateChange(CommandSender actor, Region region, String operation,
                                               Consumer<Region> edit, Runnable afterSave) {
        requireManage(actor, region);
        Region updated = region.copy(region.id());
        edit.accept(updated);
        if (!isAdmin(actor)) {
            validateClaimFlags(region, updated);
            if (updated.kind() != Region.Kind.CLAIM || updated.priority() != region.priority()
                || !Objects.equals(updated.parentId(), region.parentId())) {
                throw new IllegalArgumentException("只有管理员可以修改区域类型、优先级和父区域。");
            }
            if (updated.owners().isEmpty() && updated.ownerGroups().isEmpty()) {
                throw new IllegalArgumentException("玩家领地至少需要一位所有者。");
            }
            for (UUID owner : updated.owners()) {
                if (!region.isOwner(owner)) validateOwnerQuota(actor, owner);
            }
        }
        return commit(actor, operation, List.of(region), List.of(updated), updated, afterSave);
    }

    public CompletableFuture<Region> redefine(Player actor, Region region) {
        Region bounds = selectionRegion(actor, region.id());
        Runnable clear = clearSelectionOnSave(actor);
        return writes.submit(() -> {
            Region existing = current(region);
            if (existing.kind() == Region.Kind.GLOBAL) throw new IllegalArgumentException("全局规则没有选区。");
            if (!existing.world().equals(bounds.world())) throw new IllegalArgumentException("选区和区域必须在同一世界。");
            if (!isAdmin(actor)) validateClaimSpace(bounds, existing);
            return updateChange(actor, existing, "redefine", copy -> copy.redefine(
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()), clear);
        });
    }

    public CompletableFuture<Region> setParent(CommandSender actor, Region region, String parent) {
        requireAdmin(actor);
        if (region.kind() == Region.Kind.GLOBAL) throw new IllegalArgumentException("全局规则不支持父区域。");
        String id = parent.isBlank() || parent.equalsIgnoreCase("none") ? null : parent;
        if (id != null) {
            Region found = id.contains(":") ? findRegion(id, null) : getRegion(id, region.world());
            if (found == null || !found.world().equals(region.world())) {
                throw new IllegalArgumentException("父区域必须存在于同一世界。");
            }
            id = found.id();
        }
        String parentId = id;
        return update(actor, region, "parent", copy -> copy.setParentId(parentId));
    }

    private static void validateClaimFlags(Region before, Region after) {
        for (RegionFlag flag : RegionFlag.values()) {
            RegionFlag.State previous = before == null ? RegionFlag.State.NONE : before.getFlag(flag);
            if (!flag.claimEditable() && previous != after.getFlag(flag)) {
                throw new IllegalArgumentException("只有管理员可以设置此标志。");
            }
        }
    }

    public CompletableFuture<Region> setFlag(CommandSender actor, Region region, RegionFlag flag,
                                             RegionFlag.State state, String data) {
        return writes.submit(() -> {
            if (!isAdmin(actor) && !flag.claimEditable()) {
                throw new IllegalArgumentException("只有管理员可以设置此标志。");
            }
            return updateChange(actor, current(region), "flag:" + flag.configKey(),
                copy -> flag.apply(copy, state, data), () -> {});
        });
    }

    public CompletableFuture<Region> classify(CommandSender actor, Region region, Region.Kind kind) {
        return writes.submit(() -> {
            requireAdmin(actor);
            Region existing = current(region);
            if (existing.kind() == Region.Kind.GLOBAL || kind == Region.Kind.GLOBAL) {
                throw new IllegalArgumentException("只能将普通区域分类为 managed 或 claim。");
            }
            if (kind == Region.Kind.CLAIM) {
                validateClaimSpace(existing, existing);
                if (existing.owners().isEmpty() && existing.ownerGroups().isEmpty()) {
                    throw new IllegalArgumentException("请先设置领地所有者。");
                }
            }
            return updateChange(actor, existing, "classify", copy -> copy.setKind(kind), () -> {});
        });
    }

    public CompletableFuture<Void> deleteRegion(CommandSender actor, Region region) {
        return writes.submit(() -> {
            Region existing = current(region);
            requireManage(actor, existing);
            if (!children(existing).isEmpty()) throw new IllegalArgumentException("请先调整子区域的父区，再删除此区域。");
            return commit(actor, "delete", List.of(existing), List.of(), null, () -> {});
        });
    }

    public CompletableFuture<Region> renameRegion(CommandSender actor, Region region, String name) {
        return writes.submit(() -> {
            Region old = current(region);
            requireManage(actor, old);
            if (old.kind() == Region.Kind.GLOBAL) throw new IllegalArgumentException("全局区域不能重命名。");
            String id = newId(name);
            if (getRegion(id, old.world()) != null) throw new IllegalArgumentException("名称已存在：" + id);
            Region renamed = old.copy(id);
            List<Region> removed = new ArrayList<>(children(old));
            List<Region> saved = new ArrayList<>();
            for (Region child : removed) {
                Region updated = child.copy(child.id());
                updated.setParentId(id);
                saved.add(updated);
            }
            removed.add(old);
            saved.add(renamed);
            return commit(actor, "rename", removed, saved, renamed, () -> {});
        });
    }

    private List<Region> children(Region region) {
        return getRegionsInWorld(region.world()).stream()
            .filter(child -> region.id().equalsIgnoreCase(child.parentId()))
            .toList();
    }

    private <T> Transaction<T> commit(CommandSender actor, String action, List<Region> before, List<Region> after,
                                      T result, Runnable afterSave) {
        String actorId = actor instanceof Player player
            ? player.getName() + " (" + player.getUniqueId() + ")" : actor.getName();
        return commit(actorId, action, before, after, result, afterSave);
    }

    private <T> Transaction<T> commit(String actor, String action, List<Region> before, List<Region> after,
                                      T result, Runnable afterSave) {
        if (before.size() == 1 && after.size() == 1 && RegionSnapshots.same(before.get(0), after.get(0))) {
            afterSave.run();
            return Transaction.unchanged(result);
        }
        if (before.size() == 1 && after.size() == 1
                && RegionSnapshots.sameDefinition(before.get(0), after.get(0))) {
            Region original = before.get(0);
            if (regions.get(key(original)) != original) {
                throw new IllegalArgumentException("区域已变更，请刷新后重试。");
            }
            return new Transaction<>(result, () -> repository.applyChanges(actor, action, before, after), () -> {
                publishFlags(original, after.get(0));
                afterSave.run();
            });
        }
        Map<Key, Region> next = new HashMap<>(regions);
        for (Region region : before) {
            if (!next.remove(key(region), region)) throw new IllegalArgumentException("区域已变更，请刷新后重试。");
        }
        for (Region region : after) {
            if (next.putIfAbsent(key(region), region) != null) throw new IllegalArgumentException("区域已存在：" + region.key());
        }
        Set<Key> changed = new HashSet<>();
        before.forEach(region -> changed.add(key(region)));
        after.forEach(region -> changed.add(key(region)));
        for (Region region : next.values()) {
            if (changed.contains(key(region)) || region.parentId() != null
                && changed.contains(new Key(region.world(), region.parentId().toLowerCase(Locale.ROOT)))) {
                validateParent(region, next);
            }
        }
        validateIdentities(next);
        return new Transaction<>(result, () -> repository.applyChanges(actor, action, before, after), () -> {
            for (Region region : before) remove(region);
            for (Region region : after) put(region);
            rebuildDerivedState();
            afterSave.run();
            Set<String> affectedWorlds = new HashSet<>();
            before.forEach(region -> affectedWorlds.add(region.world()));
            after.forEach(region -> affectedWorlds.add(region.world()));
            notifyFlightUpdate(affectedWorlds);
        });
    }

    public CompletableFuture<Integer> purgePlayers(UUID playerId) {
        return writes.submit(() -> {
            List<Region> before = new ArrayList<>();
            List<Region> after = new ArrayList<>();
            for (Region region : regions.values()) {
                if (playerId == null ? region.owners().isEmpty() && region.members().isEmpty() : !region.isMember(playerId)) {
                    continue;
                }
                Region copy = region.copy(region.id());
                if (playerId == null) {
                    copy.owners().clear();
                    copy.members().clear();
                } else {
                    copy.removeOwner(playerId);
                    copy.removeMember(playerId);
                }
                before.add(region);
                if (copy.kind() != Region.Kind.CLAIM || !copy.owners().isEmpty() || !copy.ownerGroups().isEmpty()) {
                    after.add(copy);
                }
            }
            return commit(playerId == null ? "*" : playerId.toString(),
                playerId == null ? "purge-all-players" : "purge-player", before, after, before.size(), () -> {});
        });
    }

    public void onPublication(Consumer<Set<String>> callback) {
        afterPublication = callback;
    }

    private void notifyFlightUpdate(Set<String> worlds) {
        try {
            afterPublication.accept(worlds);
        } catch (RuntimeException error) {
            logger.log(java.util.logging.Level.WARNING, "区域已保存，刷新玩家保护状态失败。", error);
        }
    }

    public void close() {
        closed = true;
        repository.retire();
        writes.close();
    }

    private static void validateIdentities(Map<Key, Region> candidates) {
        Set<UUID> ids = new HashSet<>();
        Set<String> worlds = new HashSet<>();
        for (Region region : candidates.values()) {
            if (!ids.add(region.uid())) throw new IllegalArgumentException("重复的区域 UUID：" + region.key());
            if (region.kind() == Region.Kind.GLOBAL && !worlds.add(region.world())) {
                throw new IllegalArgumentException("世界存在多个默认区域：" + region.world());
            }
        }
    }

    public List<RegionsRepository.AuditSummary> history(CommandSender actor, int limit) {
        requireAdmin(actor);
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("历史条数须为 1 至 50。");
        return repository.history(limit);
    }

    public RegionsRepository.AuditEntry audit(CommandSender actor, long id) {
        requireAdmin(actor);
        var entry = repository.audit(id);
        if (entry == null) throw new IllegalArgumentException("找不到变更记录 " + id + "。");
        return entry;
    }

    public CompletableFuture<Void> undo(CommandSender actor, long id) {
        return writes.submit(() -> prepareUndo(actor, id));
    }

    private Transaction<Void> prepareUndo(CommandSender actor, long id) {
        var entry = audit(actor, id);
        Map<Key, Region> expected = new HashMap<>();
        List<Region> removed = new ArrayList<>();
        for (Region after : entry.after()) {
            Region current = regions.get(key(after));
            if (!RegionSnapshots.same(current, after)) throw changedSinceAudit(after);
            expected.put(key(after), after);
            removed.add(current);
        }
        List<Region> restored = new ArrayList<>();
        for (Region before : entry.before()) {
            if (!expected.containsKey(key(before)) && regions.containsKey(key(before))) throw changedSinceAudit(before);
            restored.add(before.copy(before.id()));
        }
        Map<Key, Region> proposed = new HashMap<>(regions);
        removed.forEach(region -> proposed.remove(key(region)));
        restored.forEach(region -> proposed.put(key(region), region));
        for (Region region : restored) {
            Region previous = expected.get(key(region));
            if (region.kind() == Region.Kind.CLAIM && (previous == null || !sameBounds(previous, region))) {
                boolean overlap = proposed.values().stream().anyMatch(other ->
                    other != region && other.kind() != Region.Kind.GLOBAL && region.intersects(other));
                if (overlap) throw new IllegalArgumentException("撤销会使玩家领地与其他区域重叠：" + region.key());
            }
        }
        return commit(actor, "undo:" + id, removed, restored, null, () -> {});
    }

    private static IllegalArgumentException changedSinceAudit(Region region) {
        return new IllegalArgumentException("区域已发生后续变更，不能直接撤销：" + region.key());
    }

    private static boolean sameBounds(Region first, Region second) {
        return first.minX() == second.minX() && first.minY() == second.minY() && first.minZ() == second.minZ()
            && first.maxX() == second.maxX() && first.maxY() == second.maxY() && first.maxZ() == second.maxZ();
    }

    public ImportReview reviewImport(List<Region> imported) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Region> additions = new ArrayList<>();
        Map<Key, Region> proposed = new HashMap<>(regions);
        Set<Key> seen = new HashSet<>();
        int unchanged = 0;
        for (Region region : imported) {
            if (!validId(region.id()) || region.world().contains(":")
                    || (region.kind() == Region.Kind.GLOBAL) != region.id().equalsIgnoreCase(config.defaults().globalRegionId())) {
                errors.add("区域 ID 或世界名无效：" + region.key());
                continue;
            }
            if (!seen.add(key(region))) {
                errors.add("文件存在同名区域：" + region.key());
                continue;
            }
            Region current = proposed.putIfAbsent(key(region), region);
            if (current != null) {
                if (RegionSnapshots.same(current, region)) unchanged++;
                else errors.add("已存在不同内容的同名区域：" + region.key());
            } else {
                additions.add(region);
            }
        }
        try {
            validateIdentities(proposed);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
        for (Region region : additions) {
            try {
                validateParent(region, proposed);
            } catch (IllegalArgumentException e) {
                errors.add(region.key() + " · " + e.getMessage());
            }
            if (region.kind() == Region.Kind.GLOBAL) {
                if (!region.id().equalsIgnoreCase(config.defaults().globalRegionId())) {
                    errors.add("世界默认区域 ID 与当前配置不匹配：" + region.key());
                }
                continue;
            }
            if (region.kind() == Region.Kind.CLAIM && region.owners().isEmpty() && region.ownerGroups().isEmpty()) {
                errors.add("玩家领地缺少所有者：" + region.key());
            }
            List<String> overlaps = proposed.values().stream()
                .filter(other -> other != region && other.kind() != Region.Kind.GLOBAL && region.intersects(other))
                .sorted(Comparator.comparing(Region::key)).limit(5).map(Region::key).toList();
            if (!overlaps.isEmpty()) {
                String message = region.key() + " 与 " + String.join("、", overlaps) + " 重叠";
                if (region.kind() == Region.Kind.CLAIM) errors.add(message);
                else warnings.add(message);
            }
        }
        return new ImportReview(List.copyOf(additions), unchanged, List.copyOf(errors), List.copyOf(warnings));
    }

    public CompletableFuture<Integer> importRegions(CommandSender actor, List<Region> imported) {
        return writes.submit(() -> {
            requireAdmin(actor);
            ImportReview review = reviewImport(imported);
            if (!review.errors().isEmpty()) throw new IllegalArgumentException(review.errors().get(0));
            if (review.additions().isEmpty()) return Transaction.unchanged(0);
            return commit(actor, "import", List.of(), review.additions(), review.additions().size(), () -> {});
        });
    }

    public record ImportReview(List<Region> additions, int unchanged, List<String> errors, List<String> warnings) {}

    private static void validateParent(Region region, Map<Key, Region> candidates) {
        Set<Key> visited = new HashSet<>();
        Region cursor = region;
        while (cursor.parentId() != null) {
            if (!visited.add(key(cursor))) throw new IllegalArgumentException("父区域存在继承环。");
            Region parent = candidates.get(new Key(cursor.world(), cursor.parentId().toLowerCase(Locale.ROOT)));
            if (parent == null) throw new IllegalArgumentException("父区域不存在：" + cursor.parentId());
            if (!canInherit(cursor, parent)) throw new IllegalArgumentException("当前区域类型不能继承此父区。");
            cursor = parent;
        }
    }

    private static boolean canInherit(Region child, Region parent) {
        if (child.kind() == Region.Kind.GLOBAL) return false;
        if (child.kind() == Region.Kind.CLAIM) return parent.kind() == Region.Kind.GLOBAL;
        return parent.kind() == Region.Kind.MANAGED
            || parent.kind() == Region.Kind.GLOBAL;
    }

    private void validateClaimSpace(Region candidate, Region excluded) {
        List<Region> conflicts = overlaps(candidate, excluded);
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("领地与已有区域重叠：" +
                String.join("、", conflicts.stream().limit(8).map(Region::key).toList()));
        }
    }

    private void validateOwnerQuota(CommandSender actor, UUID owner) {
        int limit = config.selection().maxRegionsPerPlayer();
        if (limit > 0 && !actor.hasPermission("axs.regions.bypass.limit") && countRegionsByOwner(owner) >= limit) {
            throw new IllegalArgumentException("所有者已达到区域数量上限 " + limit + "。");
        }
    }

    public int countRegionsByOwner(UUID owner) {
        return (int) regions.values().stream().filter(region -> region.isOwner(owner)).count();
    }

    private String newId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        if (!validId(id)) {
            throw new IllegalArgumentException("区域 ID 须为 1 至 64 位文字、数字、下划线或连字符。");
        }
        if (id.equalsIgnoreCase(config.defaults().globalRegionId())) {
            throw new IllegalArgumentException("该 ID 保留给世界默认规则。");
        }
        return id;
    }

    private static boolean validId(String id) {
        return id.length() <= 64 && id.matches("[\\p{L}\\p{N}_-]+");
    }

    private void requireManage(CommandSender actor, Region region) {
        if (!canManage(actor, region)) throw new IllegalArgumentException("你没有管理此区域的权限。");
    }

    public static boolean isAdmin(CommandSender actor) { return actor.hasPermission("axs.regions.admin"); }

    private static void requireAdmin(CommandSender actor) {
        if (!isAdmin(actor)) throw new IllegalArgumentException("此操作需要区域管理员权限。");
    }

    public RegionTimings timings() { return timings; }

    public List<Region> getRegionsAt(String world, int x, int y, int z) {
        long start = timings.begin();
        List<Region> result = new ArrayList<>();
        var cells = index.get(world);
        if (cells != null) addMatches(result, cells.get(new Cell(x >> 4, y >> 4, z >> 4)), x, y, z);
        addMatches(result, largeRegions.get(world), x, y, z);
        timings.finish(RegionTimings.Phase.CANDIDATES, start);
        long sort = timings.begin();
        result.sort(RULE_ORDER);
        timings.finish(RegionTimings.Phase.SORT, sort);
        return result;
    }

    private static void addMatches(List<Region> result, List<IndexedRegion> candidates, int x, int y, int z) {
        if (candidates == null) return;
        for (IndexedRegion candidate : candidates) {
            Region region = candidate.current;
            if (region.contains(x, y, z)) result.add(region);
        }
    }

    public List<Region> nearbyRegions(Location center, int radius) {
        String world = center.getWorld().getName();
        int x = center.getBlockX(), y = center.getBlockY(), z = center.getBlockZ();
        Set<IndexedRegion> candidates = new HashSet<>();
        var cells = index.get(world);
        if (cells != null) {
            for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
                for (int cy = (y - radius) >> 4; cy <= (y + radius) >> 4; cy++) {
                    for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
                        List<IndexedRegion> entries = cells.get(new Cell(cx, cy, cz));
                        if (entries != null) candidates.addAll(entries);
                    }
                }
            }
        }
        candidates.addAll(largeRegions.getOrDefault(world, List.of()));
        return candidates.stream().map(entry -> entry.current)
            .filter(region -> distanceSquared(region, x, y, z) <= (double) radius * radius)
            .sorted(Comparator.comparingDouble((Region region) -> distanceSquared(region, x, y, z))
                .thenComparing(RULE_ORDER))
            .toList();
    }

    private static double distanceSquared(Region region, int x, int y, int z) {
        double dx = (double) Math.max(region.minX(), Math.min(x, region.maxX())) - x;
        double dy = (double) Math.max(region.minY(), Math.min(y, region.maxY())) - y;
        double dz = (double) Math.max(region.minZ(), Math.min(z, region.maxZ())) - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public BlockRules blockRules(RegionFlag flag) { return new BlockRules(flag); }

    /** 爆炸回调内共享同格的有序候选及世界默认值。每个方块仍单独验证三维边界。 */
    public final class BlockRules {
        private final RegionFlag flag;
        private final Map<World, BlockWorld> worlds = new IdentityHashMap<>();

        private BlockRules(RegionFlag flag) { this.flag = flag; }

        public boolean denied(Block block) {
            BlockWorld world = worlds.computeIfAbsent(block.getWorld(), key -> new BlockWorld(key.getName()));
            int x = block.getX(), y = block.getY(), z = block.getZ();
            List<Decision> decisions = world.cells.computeIfAbsent(new Cell(x >> 4, y >> 4, z >> 4), world::load);
            Region matched = null;
            for (Decision decision : decisions) {
                Region region = decision.matched();
                if (matched != null && !samePriority(matched, region)) return false;
                if (!region.contains(x, y, z)) continue;
                if (decision.state() == RegionFlag.State.DENY) return true;
                matched = region;
            }
            return matched == null && world.deniedByDefault;
        }

        private final class BlockWorld {
            private final String name;
            private final boolean deniedByDefault;
            private final Map<Cell, List<Decision>> cells = new HashMap<>();

            private BlockWorld(String name) {
                this.name = name;
                deniedByDefault = worldDefault(name, flag, null).state() == RegionFlag.State.DENY;
            }

            private List<Decision> load(Cell cell) {
                var candidates = new ArrayList<Region>();
                var small = index.get(name);
                if (small != null) {
                    for (IndexedRegion entry : small.getOrDefault(cell, List.of())) candidates.add(entry.current);
                }
                for (IndexedRegion entry : largeRegions.getOrDefault(name, List.of())) {
                    if (intersectsCell(entry.current, cell)) candidates.add(entry.current);
                }
                candidates.sort(RULE_ORDER);
                var decisions = new ArrayList<Decision>();
                for (Region region : candidates) {
                    Decision decision = decide(region, flag, null);
                    if (decision != null && decision.source().kind() != Region.Kind.GLOBAL) decisions.add(decision);
                }
                return decisions;
            }
        }
    }

    private static boolean intersectsCell(Region region, Cell cell) {
        return cell.x() >= (region.minX() >> 4) && cell.x() <= (region.maxX() >> 4)
            && cell.y() >= (region.minY() >> 4) && cell.y() <= (region.maxY() >> 4)
            && cell.z() >= (region.minZ() >> 4) && cell.z() <= (region.maxZ() >> 4);
    }

    public Region getHighestPriorityRegion(String world, int x, int y, int z) {
        List<Region> at = getRegionsAt(world, x, y, z);
        return at.isEmpty() ? null : at.get(0);
    }

    public RegionFlag.State queryFlag(String world, int x, int y, int z, RegionFlag flag) {
        return decide(world, x, y, z, flag, null).state();
    }

    public RegionFlag.State queryFlagForPlayer(Location loc, RegionFlag flag, Player player) {
        return decide(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), flag, player).state();
    }

    /** Local region matches for reuse during a single synchronous event callback. */
    public LocationRules rulesAt(Location location) {
        return new LocationRules(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public final class LocationRules {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private List<Region> regions;

        private LocationRules(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public List<Region> regions() {
            if (regions == null) {
                List<Region> matches = getRegionsAt(world, x, y, z);
                regions = matches.isEmpty() ? List.of() : Collections.unmodifiableList(matches);
            }
            return regions;
        }

        public Decision decide(RegionFlag flag, Player player) {
            return hasBypass(flag, player) ? BYPASS : RegionManager.this.decide(world, regions(), flag, player);
        }

        public Decision travel(RegionFlag specific, Player player) {
            if (hasBypass(RegionFlag.TELEPORT, player)) return BYPASS;
            Decision local = specific == null ? null : decideLocal(regions(), specific, player);
            if (local == null) local = decideLocal(regions(), RegionFlag.TELEPORT, player);
            if (local != null) return local;
            if (specific != null) {
                Decision global = worldDefault(world, specific, player);
                if (global.state() != RegionFlag.State.NONE) return global;
            }
            return worldDefault(world, RegionFlag.TELEPORT, player);
        }

        public Decision interaction(Material material, Player player) {
            if (hasBypass(RegionFlag.USE, player)) return BYPASS;
            boolean anchor = material == Material.RESPAWN_ANCHOR;
            Decision local = anchor ? decideLocal(regions(), RegionFlag.RESPAWN_ANCHORS, player) : null;
            if (local == null) local = decideLocal(regions(), RegionFlag.USE, player);
            if (local != null) return local;
            if (anchor) {
                Decision specific = worldDefault(world, RegionFlag.RESPAWN_ANCHORS, player);
                if (specific.state() != RegionFlag.State.NONE) return specific;
            }
            Region global = globalRegions.get(world);
            if (global != null && global.blockedInteractions().contains(material)) {
                return new Decision(RegionFlag.State.DENY, global, global, Reason.DIRECT);
            }
            return worldDefault(world, RegionFlag.USE, player);
        }
    }

    public Decision decide(String world, int x, int y, int z, RegionFlag flag, Player player) {
        if (hasBypass(flag, player)) return BYPASS;
        return decide(world, getRegionsAt(world, x, y, z), flag, player);
    }

    private static boolean hasBypass(RegionFlag flag, Player player) {
        return player != null && flag.playerBypass() && player.hasPermission("axs.regions.bypass");
    }

    private Decision decide(String world, List<Region> candidates, RegionFlag flag, Player player) {
        Decision local = decideLocal(candidates, flag, player);
        return local != null ? local : worldDefault(world, flag, player);
    }

    private Decision decideLocal(List<Region> candidates, RegionFlag flag, Player player) {
        Decision best = null;
        for (Region region : candidates) {
            if (best != null && !samePriority(best.matched(), region)) break;
            Decision current = decide(region, flag, player);
            if (current == null || current.source().kind() == Region.Kind.GLOBAL) continue;
            if (current.state() == RegionFlag.State.DENY) return current;
            if (best == null) best = current;
        }
        return best;
    }

    private Decision worldDefault(String world, RegionFlag flag, Player player) {
        Region global = globalRegions.get(world);
        Decision decision = global == null ? null : decide(global, flag, player);
        return decision != null ? decision : new Decision(RegionFlag.State.NONE, null, null, Reason.UNSET);
    }

    private Decision decide(Region region, RegionFlag flag, Player player) {
        Region source = sourceOf(region, flag);
        if (source == null) return null;
        RegionFlag.State state = source.getFlag(flag);
        boolean exempt = state == RegionFlag.State.DENY && player != null && flag.memberExempt()
            && hasAccess(source, player);
        return new Decision(exempt ? RegionFlag.State.ALLOW : state, region, source,
            exempt ? Reason.MEMBER_EXEMPT : source == region ? Reason.DIRECT : Reason.INHERITED);
    }

    private Region sourceOf(Region region, RegionFlag flag) {
        Map<RegionFlag, Region> sources = ruleSources.get(region);
        return sources == null ? (region.getFlag(flag) == RegionFlag.State.NONE ? null : region) : sources.get(flag);
    }

    public Decision regionRule(Region region, RegionFlag flag) {
        Region source = sourceOf(region, flag);
        return source == null ? new Decision(RegionFlag.State.NONE, region, null, Reason.UNSET)
            : new Decision(source.getFlag(flag), region, source, source == region ? Reason.DIRECT : Reason.INHERITED);
    }

    public boolean hasAccess(Region region, Player player) {
        return region.isMember(player.getUniqueId())
            || region.ownerGroups().stream().anyMatch(group -> player.hasPermission("axs.regions.group." + group))
            || region.memberGroups().stream().anyMatch(group -> player.hasPermission("axs.regions.group." + group));
    }

    private static boolean samePriority(Region first, Region second) {
        return tier(first.kind()) == tier(second.kind()) && first.priority() == second.priority();
    }

    private static int tier(Region.Kind kind) {
        return switch (kind) {
            case MANAGED -> 0;
            case CLAIM -> 1;
            case GLOBAL -> 2;
        };
    }

    private void put(Region region) {
        regions.put(key(region), region);
        regionsById.put(region.uid(), region);
        orderedRegions = null;
        boundedRegions = null;
        if (region.kind() == Region.Kind.GLOBAL) {
            globalRegions.put(region.world(), region);
            return;
        }
        var indexed = new IndexedRegion(region);
        indexedRegions.put(region.uid(), indexed);
        if (isLarge(region)) {
            largeRegions.computeIfAbsent(region.world(), world -> new ArrayList<>()).add(indexed);
        } else {
            var cells = index.computeIfAbsent(region.world(), world -> new HashMap<>());
            visitCells(region, cell -> cells.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(indexed));
        }
    }

    private void remove(Region region) {
        regions.remove(key(region));
        regionsById.remove(region.uid());
        orderedRegions = null;
        boundedRegions = null;
        if (region.kind() == Region.Kind.GLOBAL) {
            globalRegions.remove(region.world());
            return;
        }
        IndexedRegion indexed = indexedRegions.remove(region.uid());
        if (isLarge(region)) {
            var list = largeRegions.get(region.world());
            list.remove(indexed);
            if (list.isEmpty()) largeRegions.remove(region.world());
        } else {
            var cells = index.get(region.world());
            visitCells(region, cell -> {
                var list = cells.get(cell);
                list.remove(indexed);
                if (list.isEmpty()) cells.remove(cell);
            });
            if (cells.isEmpty()) index.remove(region.world());
        }
    }

    private static boolean isLarge(Region region) {
        long x = (region.maxX() >> 4) - (region.minX() >> 4) + 1L;
        long y = (region.maxY() >> 4) - (region.minY() >> 4) + 1L;
        long z = (region.maxZ() >> 4) - (region.minZ() >> 4) + 1L;
        return x > MAX_INDEX_CELLS || y > MAX_INDEX_CELLS || z > MAX_INDEX_CELLS
            || x * y * z > MAX_INDEX_CELLS;
    }

    private static void visitCells(Region region, Consumer<Cell> visit) {
        for (int x = region.minX() >> 4; x <= region.maxX() >> 4; x++) {
            for (int y = region.minY() >> 4; y <= region.maxY() >> 4; y++) {
                for (int z = region.minZ() >> 4; z <= region.maxZ() >> 4; z++) visit.accept(new Cell(x, y, z));
            }
        }
    }

    private static Key key(Region region) { return new Key(region.world(), region.id().toLowerCase(Locale.ROOT)); }

    private static final class IndexedRegion {
        private Region current;

        private IndexedRegion(Region region) { current = region; }
    }

    private record Cell(int x, int y, int z) {}
    private record Key(String world, String id) {}

    public record Decision(RegionFlag.State state, Region matched, Region source, Reason reason) {}
}
