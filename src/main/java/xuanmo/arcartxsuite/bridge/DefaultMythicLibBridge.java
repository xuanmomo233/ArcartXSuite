package xuanmo.arcartxsuite.bridge;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.api.stat.modifier.TemporaryStatModifier;
import io.lumine.mythic.lib.manager.StatManager;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;

/**
 * {@link MythicLibBridge} 的默认实现，桥接 MythicLib 属性系统，
 * 提供临时属性修饰符的注入与回收。
 * <p>
 * MythicLib 为可选依赖，运行时通过插件管理器检测可用性。
 */
public final class DefaultMythicLibBridge implements MythicLibBridge {

    private static final String PLUGIN_NAME = "MythicLib";

    private final JavaPlugin plugin;

    private boolean available;
    private StatManager statsManager;

    public DefaultMythicLibBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin mythicLib = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (mythicLib == null || !mythicLib.isEnabled()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[MythicLib] 未检测到 MythicLib 插件，MythicLib 属性桥接已停用。");
            return;
        }

        try {
            statsManager = MythicLib.inst().getStats();
            if (statsManager == null) {
                throw new IllegalStateException("MythicLib StatManager 不可用");
            }
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[MythicLib] 桥接初始化成功");
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public boolean isRegisteredStat(String statId) {
        if (!available || statId == null) return false;
        try {
            return statsManager.isRegistered(statId);
        } catch (RuntimeException | LinkageError e) { return false; }
    }

    @Override
    public Object getPlayerData(Player player) {
        if (!available || player == null) return null;
        try { return MMOPlayerData.get(player); } catch (RuntimeException | LinkageError e) { return null; }
    }

    @Override
    public Object getStatMap(Object playerData) {
        if (!(playerData instanceof MMOPlayerData data)) return null;
        try { return data.getStatMap(); } catch (RuntimeException | LinkageError e) { return null; }
    }

    // ─── 持久修饰符 ──────────────────────────────────────────────

    @Override
    public Object registerStatModifier(Object playerData, String modifierName, String statId, double value) {
        if (!(playerData instanceof MMOPlayerData data)) return null;
        try {
            StatModifier modifier = new StatModifier(
                modifierName, statId, value, ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER
            );
            modifier.register(data);
            return modifier;
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] registerStatModifier 失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void removeStatModifier(Object statMap, String statId, String modifierName) {
        if (!(statMap instanceof StatMap map)) return;
        try {
            StatInstance instance = map.getInstance(statId);
            if (instance != null) { instance.remove(modifierName); }
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] removeStatModifier 失败: " + e.getMessage());
        }
    }

    @Override
    public void updateStat(Object statMap, String statId) {
        if (!(statMap instanceof StatMap map)) return;
        try { map.update(statId); } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] updateStat 失败: " + e.getMessage());
        }
    }

    // ─── 临时修饰符 ──────────────────────────────────────────────

    @Override
    public Object registerTemporaryModifier(Object playerData, String modifierName, String statId, double value, long durationMillis) {
        if (!(playerData instanceof MMOPlayerData data)) return null;
        try {
            TemporaryStatModifier handle = new TemporaryStatModifier(
                modifierName, statId, value, ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER
            );
            long durationTicks = (Math.max(1L, durationMillis) - 1L) / 50L + 1L;
            handle.register(data, durationTicks);
            return new TemporaryModifierHandle(data, handle);
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] registerTemporaryModifier 失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void closeTemporaryModifier(Object handle) {
        if (!(handle instanceof TemporaryModifierHandle temporary)) return;
        var modifier = temporary.modifier();
        try {
            try {
                if (modifier.isActive()) modifier.close();
            } finally {
                // MythicLib's close cancels the expiry task without unregistering the stat.
                modifier.unregister(temporary.playerData());
            }
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[MythicLib] closeTemporaryModifier 失败: " + e.getMessage());
        }
    }

    private record TemporaryModifierHandle(MMOPlayerData playerData, TemporaryStatModifier modifier) {}

    private void reset() {
        available = false;
        statsManager = null;
    }
}
