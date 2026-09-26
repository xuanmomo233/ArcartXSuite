package xuanmo.arcartxsuite.abyss.instance;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/**
 * 实例注册表：模板表 + 实例表 + 玩家→实例映射。
 */
public final class AbyssInstanceManager {

    private final Map<String, AbyssTemplate> templates = new ConcurrentHashMap<>();
    private final Map<UUID, AbyssInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerInstance = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ── 模板 ──────────────────────────────────────────

    public void clearTemplates() { templates.clear(); }

    public void registerTemplate(AbyssTemplate template) {
        templates.put(template.id(), template);
    }

    public @Nullable AbyssTemplate getTemplate(String id) {
        return templates.get(id);
    }

    public Collection<AbyssTemplate> getAllTemplates() { return templates.values(); }

    // ── 实例 ──────────────────────────────────────────

    public AbyssInstance createInstance(AbyssTemplate template,
                                        @Nullable xuanmo.arcartxsuite.abyss.party.Party party) {
        UUID id = UUID.randomUUID();
        AbyssInstance inst = new AbyssInstance(id, template, party);
        instances.put(id, inst);
        return inst;
    }

    public int getInstanceCount(String templateId) {
        int n = 0;
        for (AbyssInstance i : instances.values()) {
            if (i.getTemplate().id().equals(templateId)) n++;
        }
        return n;
    }

    /** 某地牢的全部运行中实例。 */
    public java.util.List<AbyssInstance> getInstances(String templateId) {
        java.util.List<AbyssInstance> out = new java.util.ArrayList<>();
        for (AbyssInstance i : instances.values()) {
            if (i.getTemplate().id().equals(templateId)) out.add(i);
        }
        return out;
    }

    public boolean hasAvailableSlot(AbyssTemplate template) {
        return template.maxInstances() <= 0
            || getInstanceCount(template.id()) < template.maxInstances();
    }

    public @Nullable AbyssInstance getInstance(UUID instanceId) {
        return instances.get(instanceId);
    }

    /** 玩家当前所在实例（顺带清理脏映射）。 */
    public @Nullable AbyssInstance getInstance(Player player) {
        UUID id = playerInstance.get(player.getUniqueId());
        if (id == null) return null;
        AbyssInstance inst = instances.get(id);
        if (inst != null && inst.hasPlayer(player.getUniqueId())) {
            return inst;
        }
        playerInstance.remove(player.getUniqueId(), id);
        return null;
    }

    public Collection<AbyssInstance> getAllInstances() { return instances.values(); }

    public void playerEnterInstance(Player player, AbyssInstance instance) {
        playerInstance.put(player.getUniqueId(), instance.getInstanceId());
        instance.addPlayer(player);
    }

    public void playerLeaveInstance(Player player) {
        AbyssInstance inst = getInstance(player);
        if (inst != null) inst.removePlayer(player.getUniqueId());
        playerInstance.remove(player.getUniqueId());
    }

    public boolean isPlayerInDungeon(Player player) {
        return getInstance(player) != null;
    }

    public @Nullable AbyssInstance destroyInstance(AbyssInstance instance) {
        lock.writeLock().lock();
        try {
            for (UUID u : instance.getPlayerUUIDs()) {
                playerInstance.remove(u);
            }
            return instances.remove(instance.getInstanceId());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> m = new ConcurrentHashMap<>();
        m.put("templates", templates.size());
        m.put("instances", instances.size());
        m.put("activePlayers", playerInstance.size());
        return m;
    }
}
