package xuanmo.arcartxsuite.abyss.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.NpcConfig.DungeonNpc;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/**
 * 副本 NPC 管理器。
 * <p>
 * 实体呈现委托 Adyeshach：经宿主桥 {@link AdyeshachNpcBridgeAPI#spawnPublicTemporaryNpc}
 * 在公共临时管理器创建实例内 NPC（不入持久库、可被 conversation 模块靠近扫描发现）。
 * 玩家右键经 {@link #initInteractHandler} 注册的 AdyeshachEntityInteractEvent 路由；
 * 绑定 Chemdah 会话的 NPC 由 conversation capability 开真对话 UI，否则聊天栏逐句兜底。
 * <p>
 * 本类只保留 abyss 编排面：实例生命周期内的生成/移除登记、once 判定、
 * click-script 回调、对话降级（Adyeshach 缺席时回退 Bukkit LivingEntity）。
 */
public final class DungeonNpcManager {

    private final Map<UUID, Map<String, Object>> spawned = new ConcurrentHashMap<>(); // instanceId -> npcName -> handle
    private final Map<String, String> handleToNpc = new ConcurrentHashMap<>();        // handleKey -> npcName@instanceId
    private final SchedulerAPI scheduler;
    private final org.bukkit.plugin.Plugin plugin;
    private final Logger logger;

    /** 点击回调（click-script 执行钩子）。 */
    private BiConsumer<AbyssInstance, ClickEvent> clickCallback;
    private Function<AbyssInstance, Function<String, Location>> locParser;
    /** 玩家 → 所在实例（Adyeshach 交互事件无实例上下文，需反查）。 */
    private Function<Player, AbyssInstance> instanceLookup;
    /** 打开 NPC 绑定会话（conversation capability；无会话/模块缺席返回 false）。 */
    private BiFunction<Player, String, Boolean> conversationOpener;

    /** 宿主 Adyeshach 桥（模块注入；不可用时降级 Bukkit 实体）。 */
    private volatile AdyeshachNpcBridgeAPI npcBridge;
    private volatile boolean interactRegistered;

    public record ClickEvent(String npcName, DungeonNpc config, Player player) { }

    public DungeonNpcManager(org.bukkit.plugin.Plugin plugin, SchedulerAPI scheduler,
                             Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setNpcBridge(@Nullable AdyeshachNpcBridgeAPI bridge) {
        this.npcBridge = bridge;
    }

    public void setCallbacks(BiConsumer<AbyssInstance, ClickEvent> clickCallback,
                             Function<AbyssInstance, Function<String, Location>> locParser,
                             Function<Player, AbyssInstance> instanceLookup,
                             BiFunction<Player, String, Boolean> conversationOpener) {
        this.clickCallback = clickCallback;
        this.locParser = locParser;
        this.instanceLookup = instanceLookup;
        this.conversationOpener = conversationOpener;
    }

    /** 注册 Adyeshach 右键交互路由（service.start 调用一次，可重入）。 */
    public void initInteractHandler() {
        AdyeshachNpcBridgeAPI bridge = npcBridge;
        if (interactRegistered || bridge == null || !bridge.isAvailable()) return;
        interactRegistered = bridge.registerInteractHandler(this::onAdyeshachInteract);
    }

    /** AdyeshachEntityInteractEvent → 实例/NPC 匹配 → once → 对话 + click-script。 */
    private void onAdyeshachInteract(Player player, Object entity) {
        if (player == null || entity == null) return;
        String tag = handleToNpc.get(handleKey(entity));
        if (tag == null) return;
        AbyssInstance instance = instanceLookup != null ? instanceLookup.apply(player) : null;
        if (instance == null) return;
        String npcName = tag.substring(0, tag.lastIndexOf('@'));
        String instId = tag.substring(tag.lastIndexOf('@') + 1);
        if (!instId.equals(instance.getInstanceId().toString())) return;
        fireClick(instance, player, npcName);
    }

    /** 副本 start：生成全部 NPC。 */
    public void initInstance(AbyssInstance instance) {
        initInteractHandler(); // 桥晚初始化时补注册（幂等）
        for (DungeonNpc npc : instance.getTemplate().npcs().npcs().values()) {
            spawn(instance, npc.name());
        }
    }

    public boolean spawn(AbyssInstance instance, String npcName) {
        DungeonNpc npc = instance.getTemplate().npcs().npcs().get(npcName);
        if (npc == null || instance.getWorld() == null) return false;
        // 重复 spawn 同名 NPC：先移除旧实体（spawn_npc 重复调用会残留孤儿实体）
        Map<String, Object> existing = spawned.get(instance.getInstanceId());
        if (existing != null) {
            Object old = existing.remove(npcName);
            if (old != null) removeHandle(old);
        }
        Function<String, Location> parse = locParser.apply(instance);
        Location loc = parse.apply(npc.location());
        if (loc == null) return false;

        Object handle = null;
        AdyeshachNpcBridgeAPI bridge = npcBridge;
        if (bridge != null && bridge.isAvailable()) {
            try {
                handle = bridge.spawnPublicTemporaryNpc(npc.type(), loc, npcName, npc.displayName());
            } catch (Throwable t) {
                logger.fine("[abyss] Adyeshach NPC 生成失败，降级实体: " + t.getMessage());
            }
        }
        if (handle == null) {
            handle = spawnEntityNpc(npc, loc);
        }
        if (handle == null) return false;

        spawned.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentHashMap<>())
            .put(npcName, handle);
        handleToNpc.put(handleKey(handle), npcName + "@" + instance.getInstanceId());
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
        handleToNpc.remove(handleKey(handle));
        if (handle instanceof Entity e) {
            e.remove();
            return;
        }
        AdyeshachNpcBridgeAPI bridge = npcBridge;
        if (bridge != null) {
            try {
                bridge.removeEntity(handle);
            } catch (Throwable t) {
                logger.fine("[abyss] Adyeshach NPC 移除失败: " + t.getMessage());
            }
        }
    }

    private static String handleKey(Object handle) {
        if (handle instanceof Entity e) return e.getUniqueId().toString();
        try {
            if (handle instanceof ink.ptms.adyeshach.common.entity.EntityBase eb) {
                String uid = eb.getUniqueId();
                if (uid != null && !uid.isBlank()) return "ady:" + uid;
            }
        } catch (Throwable ignored) { }
        return "h" + System.identityHashCode(handle);
    }

    /**
     * Bukkit 实体点击（降级 NPC 路径；Adyeshach 实体走 {@link #onAdyeshachInteract}）。
     */
    public boolean handleClick(AbyssInstance instance, Player player, Entity entity) {
        String tag = handleToNpc.get(entity.getUniqueId().toString());
        if (tag == null) return false;
        String npcName = tag.substring(0, tag.lastIndexOf('@'));
        fireClick(instance, player, npcName);
        return true;
    }

    /** 统一点击处理：once 判定 → 会话/降级对话 → click-script。 */
    private void fireClick(AbyssInstance instance, Player player, String npcName) {
        DungeonNpc npc = instance.getTemplate().npcs().npcs().get(npcName);
        if (npc == null) return;
        if (npc.once() && instance.isTriggered("npc:" + npcName + ":" + player.getUniqueId())) {
            return;
        }
        if (npc.once()) {
            instance.markTriggeredOnce("npc:" + npcName + ":" + player.getUniqueId());
        }
        // 对话：该 npcId 绑定 Chemdah 会话 → 开真对话 UI；否则聊天栏逐句兜底
        boolean opened = false;
        BiFunction<Player, String, Boolean> opener = conversationOpener;
        if (opener != null) {
            try {
                opened = Boolean.TRUE.equals(opener.apply(player, npcName));
            } catch (Throwable t) {
                logger.fine("[abyss] 会话开启失败 npc=" + npcName + ": " + t.getMessage());
            }
        }
        if (!opened) {
            playDialog(instance, npcName, player);
        }
        if (clickCallback != null && !npc.clickScript().isEmpty()) {
            clickCallback.accept(instance, new ClickEvent(npcName, npc, player));
        }
    }

    /** 逐句播放 NPC 对话（无 Chemdah 会话绑定时的聊天栏兜底）。 */
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
        AdyeshachNpcBridgeAPI bridge = npcBridge;
        if (handle == null || bridge == null) return;
        try {
            bridge.applyAnimation(handle, anim, speed, transitionTime, keepTime);
        } catch (Throwable t) {
            logger.fine("[abyss] NPC 动画播放失败: " + t.getMessage());
        }
    }

    /** NPC 的 Adyeshach 实体句柄（conversation/外观增强用；Bukkit 降级时为 Entity）。 */
    public @Nullable Object handleOf(AbyssInstance instance, String npcName) {
        Map<String, Object> map = spawned.get(instance.getInstanceId());
        return map == null ? null : map.get(npcName);
    }

    public void cleanup(AbyssInstance instance) {
        Map<String, Object> map = spawned.remove(instance.getInstanceId());
        if (map == null) return;
        map.values().forEach(this::removeHandle);
    }

    public void shutdown() {
        spawned.values().forEach(m -> m.values().forEach(this::removeHandle));
        spawned.clear();
        handleToNpc.clear();
        AdyeshachNpcBridgeAPI bridge = npcBridge;
        if (bridge != null && interactRegistered) {
            try {
                bridge.unregisterInteractHandler();
            } catch (Throwable ignored) { }
            interactRegistered = false;
        }
    }
}
