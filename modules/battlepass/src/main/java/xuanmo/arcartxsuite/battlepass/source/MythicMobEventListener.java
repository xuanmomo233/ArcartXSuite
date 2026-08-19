package xuanmo.arcartxsuite.battlepass.source;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.event.TaczGunKillEvent;

/**
 * MythicMobs 生物死亡事件监听器（可选依赖）。
 * <p>
 * 监听 {@link MythicMobDeathEvent}，提取 MythicMobs 内部 mob 类型名作为 payload，
 * 供战令任务通过 condition 精确匹配特定 MythicMobs 怪物。
 * <p>
 * 此类独立于 {@link BukkitEventSourceListener}，因为 MythicMobs 为可选依赖，
 * 运行时可能不存在。若直接在 BukkitEventSourceListener 中引用 MythicMobs 事件类，
 * 会导致整个监听器类加载失败。通过独立类 + 条件注册避免此问题。
 * <p>
 * 注册方式：仅在 MythicMobs/MythicBukkit 插件存在时由 {@code BattlePassTaskEngine} 实例化并注册。
 * <p>
 * payload 字段：
 * <ul>
 *   <li>{@code entityType} — Bukkit 实体类型名（如 ZOMBIE）</li>
 *   <li>{@code entityName} — 实体显示名</li>
 *   <li>{@code mythicMobId} — MythicMobs 内部 mob 类型名（如 SkeletalKnight）</li>
 *   <li>{@code world} — 世界名</li>
 * </ul>
 * 击杀者通过 {@link LivingEntity#getKiller()} 解析，与 {@link BukkitEventSourceListener#onEntityDeath} 一致。
 * TACZ 枪械击杀 MythicMobs 生物时 {@code getKiller()} 可能返回 null，
 * 此时可配合 {@link TaczGunKillEvent} 交叉判断。
 */
public final class MythicMobEventListener implements Listener {

    // 使用字符串字面量而非 MythicMobDeathEvent.class.getName()，
    // 避免访问此常量时触发 MythicMobs 类加载（MythicMobs 为可选依赖）
    public static final String MYTHIC_MOB_DEATH = "io.lumine.mythic.bukkit.events.MythicMobDeathEvent";

    private final Plugin plugin;
    private final BiConsumer<Player, Map<String, String>> callback;

    private MythicMobEventListener(Plugin plugin, BiConsumer<Player, Map<String, String>> callback) {
        this.plugin = plugin;
        this.callback = callback;
    }

    /**
     * 当 MythicMobs 可用时创建并注册监听器。
     *
     * @param plugin   插件实例
     * @param callback 事件回调（player + payload）
     * @return 已注册的监听器实例，MythicMobs 不可用时返回 null
     */
    @Nullable
    public static MythicMobEventListener registerIfAvailable(
        Plugin plugin, BiConsumer<Player, Map<String, String>> callback
    ) {
        if (!isMythicAvailable()) return null;
        try {
            MythicMobEventListener listener = new MythicMobEventListener(plugin, callback);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            return listener;
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static boolean isMythicAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
            || Bukkit.getPluginManager().isPluginEnabled("MythicBukkit");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) return;

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("entityType", entity.getType().name());
        payload.put("entityName", entity.getName());
        payload.put("world", entity.getWorld().getName());
        // 提取 MythicMobs 内部 mob 类型名
        try {
            if (event.getMobType() != null && event.getMobType().getInternalName() != null) {
                payload.put("mythicMobId", event.getMobType().getInternalName());
            }
        } catch (Throwable ignored) {
            // getMobType() 在某些 MythicMobs 版本可能不可用
        }

        callback.accept(killer, payload);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
