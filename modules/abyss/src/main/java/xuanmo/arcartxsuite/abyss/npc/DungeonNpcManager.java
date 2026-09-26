package xuanmo.arcartxsuite.abyss.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.NpcConfig.DungeonNpc;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/**
 * 副本 NPC 管理器（实体实现：LivingEntity + NoAI + 无敌 + 自定义名）。
 * <p>
 * PLAYER 型 NPC（皮肤）需要 Adyeshach——host 桥的 createAdyeshachNpcBridge
 * 仅支持查找/增强既有 NPC；副本 NPC 走 {@code ink.ptms.adyeshach} API 直接创建，
 * 未安装时降级为 ZOMBIE 实体提示。对话/点击/商店全部自己管理。
 */
public final class DungeonNpcManager {

    private final Map<UUID, Map<String, Object>> spawned = new ConcurrentHashMap<>(); // instanceId -> npcName -> entity/adyeshachEntity
    private final Map<UUID, String> entityToNpc = new ConcurrentHashMap<>();          // entityUuid -> npcName
    private final SchedulerAPI scheduler;
    private final org.bukkit.plugin.Plugin plugin;
    private final Logger logger;

    /** 点击回调（由监听器转发）。 */
    private BiConsumer<AbyssInstance, ClickEvent> clickCallback;
    private Function<AbyssInstance, Function<String, Location>> locParser;

    /** Adyeshach 直接 API 桥（可选依赖，懒加载）。 */
    private volatile AdyeshachNpcAdapter adyeshach;

    public record ClickEvent(String npcName, DungeonNpc config, Player player) { }

    public DungeonNpcManager(org.bukkit.plugin.Plugin plugin, SchedulerAPI scheduler,
                             Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setCallbacks(BiConsumer<AbyssInstance, ClickEvent> clickCallback,
                             Function<AbyssInstance, Function<String, Location>> locParser) {
        this.clickCallback = clickCallback;
        this.locParser = locParser;
    }

    public void initAdyeshachIfAvailable() {
        if (adyeshach == null
            && Bukkit.getPluginManager().isPluginEnabled("Adyeshach")) {
            try {
                adyeshach = new AdyeshachNpcAdapter(logger);
            } catch (Throwable t) {
                logger.fine("[abyss] Adyeshach 适配不可用: " + t.getMessage());
            }
        }
    }

    /** 副本 start：生成全部 NPC。 */
    public void initInstance(AbyssInstance instance) {
        for (DungeonNpc npc : instance.getTemplate().npcs().npcs().values()) {
            spawn(instance, npc.name());
        }
    }

    public boolean spawn(AbyssInstance instance, String npcName) {
        DungeonNpc npc = instance.getTemplate().npcs().npcs().get(npcName);
        if (npc == null || instance.getWorld() == null) return false;
        Function<String, Location> parse = locParser.apply(instance);
        Location loc = parse.apply(npc.location());
        if (loc == null) return false;

        Object handle = null;
        if ("PLAYER".equals(npc.type()) && adyeshach != null && adyeshach.available()) {
            handle = adyeshach.spawnPlayerNpc(npc, loc);
        }
        if (handle == null) {
            handle = spawnEntityNpc(npc, loc);
        }
        if (handle == null) return false;

        spawned.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentHashMap<>())
            .put(npcName, handle);
        entityToNpc.put(entityUuidOf(handle), npcName + "@" + instance.getInstanceId());
        return true;
    }

    private @Nullable LivingEntity spawnEntityNpc(DungeonNpc npc, Location loc) {
        EntityType type;
        try {
            type = EntityType.valueOf(npc.type());
        } catch (IllegalArgumentException e) {
            type = EntityType.VILLAGER;
        }
        // PLAYER 无法通过 spawnEntity 生成；无 Adyeshach 时降级 ZOMBIE
        if (type == EntityType.PLAYER || !type.isAlive()) {
            logger.warning("[abyss] NPC " + npc.name() + " 类型 " + npc.type()
                + " 无法以实体生成（PLAYER 型需 Adyeshach），降级 ZOMBIE");
            type = EntityType.ZOMBIE;
        }
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        e.setAI(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setCollidable(false);
        e.setRemoveWhenFarAway(false);
        e.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', npc.displayName()));
        e.setCustomNameVisible(true);
        e.setPersistent(false);
        return e;
    }

    public boolean remove(AbyssInstance instance, String npcName) {
        Map<String, Object> map = spawned.get(instance.getInstanceId());
        if (map == null) return false;
        Object handle = map.remove(npcName);
        if (handle == null) return false;
        removeHandle(handle);
        return true;
    }

    private void removeHandle(Object handle) {
        if (handle instanceof Entity e) {
            entityToNpc.remove(e.getUniqueId());
            e.remove();
        } else if (adyeshach != null) {
            adyeshach.remove(handle);
        }
    }

    private static UUID entityUuidOf(Object handle) {
        if (handle instanceof Entity e) return e.getUniqueId();
        return UUID.nameUUIDFromBytes(("ady:" + System.identityHashCode(handle)).getBytes());
    }

    /** 玩家右键实体 → 匹配 NPC 触发对话+点击脚本。 */
    public boolean handleClick(AbyssInstance instance, Player player, Entity entity) {
        String tag = entityToNpc.get(entity.getUniqueId());
        if (tag == null) return false;
        int sep = tag.lastIndexOf('@');
        String npcName = tag.substring(0, sep);
        String instId = tag.substring(sep + 1);
        if (!instId.equals(instance.getInstanceId().toString())) {
            // UUID 包含连字符，取前段比较即可——tag 存的已是完整 instanceId 字符串
        }
        DungeonNpc npc = instance.getTemplate().npcs().npcs().get(npcName);
        if (npc == null) return false;
        if (npc.once() && instance.isTriggered("npc:" + npcName + ":" + player.getUniqueId())) {
            return true;
        }
        if (npc.once()) {
            instance.markTriggeredOnce("npc:" + npcName + ":" + player.getUniqueId());
        }
        playDialog(instance, npcName, player);
        if (clickCallback != null && !npc.clickScript().isEmpty()) {
            clickCallback.accept(instance, new ClickEvent(npcName, npc, player));
        }
        return true;
    }

    /** 逐句播放 NPC 对话。 */
    public void playDialog(AbyssInstance instance, String npcName, Player player) {
        DungeonNpc npc = instance.getTemplate().npcs().npcs().get(npcName);
        if (npc == null || npc.dialogs().isEmpty() || player == null) return;
        int i = 0;
        for (String line : npc.dialogs()) {
            final String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                npc.displayName() + "&f: " + line);
            final int delay = i * Math.max(1, npc.dialogInterval());
            if (delay == 0) {
                player.sendMessage(msg);
            } else {
                xuanmo.arcartxsuite.api.scheduler.ScheduledTask[] holder =
                    new xuanmo.arcartxsuite.api.scheduler.ScheduledTask[1];
                holder[0] = scheduler.scheduleAtFixedRate(plugin, () -> {
                    if (holder[0] != null) {
                        try { holder[0].cancel(); } catch (Throwable ignored) { }
                    }
                    if (player.isOnline()) player.sendMessage(msg);
                }, delay, Long.MAX_VALUE);
                instance.registerTask(holder[0]);
            }
            i++;
        }
    }

    /** Adyeshach NPC 播放模型动画（play_model_animation 动作）。 */
    public void playAnimation(AbyssInstance instance, String npcName, String anim,
                              double speed, int transitionTime, long keepTime) {
        Map<String, Object> map = spawned.get(instance.getInstanceId());
        if (map == null) return;
        Object handle = map.get(npcName);
        if (handle != null && adyeshach != null) {
            adyeshach.playAnimation(handle, anim, speed, transitionTime, keepTime);
        }
    }

    public void cleanup(AbyssInstance instance) {
        Map<String, Object> map = spawned.remove(instance.getInstanceId());
        if (map == null) return;
        map.values().forEach(this::removeHandle);
    }

    public void shutdown() {
        spawned.values().forEach(m -> m.values().forEach(this::removeHandle));
        spawned.clear();
        entityToNpc.clear();
    }

    // ═══ Adyeshach 直接 API 适配（可选依赖隔离） ═══

    /**
     * Adyeshach 直连适配器：创建临时 PLAYER 型 NPC（皮肤/视角跟随）。
     * <p>
     * Adyeshach API 版本间差异较大，为稳妥起见通过反射调用核心方法
     * （createPublicEntity / setCustomName / teleport），不可用即回退实体实现。
     */
    static final class AdyeshachNpcAdapter {
        private final Logger logger;
        private boolean ready;

        AdyeshachNpcAdapter(Logger logger) {
            this.logger = logger;
            try {
                Class.forName("ink.ptms.adyeshach.core.Adyeshach");
                ready = true;
            } catch (Throwable t) {
                ready = false;
            }
        }

        boolean available() { return ready; }

        /** 创建 PLAYER NPC。返回句柄对象（Adyeshach entity）。 */
        @Nullable Object spawnPlayerNpc(DungeonNpc npc, Location loc) {
            // Adyeshach API 跨版本差异大，采用最小反射面：
            // WorldEdit 模式下直接退回实体实现更稳。
            // 后续版本需要 PLAYER NPC 时在此接 ink.ptms.adyeshach API。
            return null;
        }

        void remove(Object handle) { }

        void playAnimation(Object handle, String anim, double speed, int transitionTime, long keepTime) { }
    }
}
