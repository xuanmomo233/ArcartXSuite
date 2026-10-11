package xuanmo.arcartxsuite.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper 1.21+ 命令名冲突整理。
 * <p>
 * Paper 将 Bukkit 命令并入单一 Brigadier 命令树后，客户端补全会把同一名字的
 * 所有字面量（{@code spawn}、{@code arcartx-suite:spawn}、{@code essentials:spawn}）
 * 合并显示为 "{@code spawn [xN]}"。当本插件声明的命令未被任何模块绑定
 * （executor 仍为插件默认值，即功能未启用）且同名命令已被其它插件或原版占用时，
 * 把该名字完整让给对方，消除补全列表中的 [xN] 噪音：
 * <ul>
 *   <li>裸 {@code <name>} 归对方：移除我方 {@code arcartx-suite:<name>} 影子字面量；</li>
 *   <li>裸 {@code <name>} 归我方（死命令）：移除我方全部 {@code <name>} 字面量，
 *       并把对方的 {@code *:<name>} 提升为裸 {@code <name>}，保证命令仍可执行。</li>
 * </ul>
 * 已绑定执行器的命令不受影响（冲突如实保留）。
 * <p>
 * Paper 上 {@code knownCommands} 是 Brigadier 树根的转发视图
 * （{@code BukkitBrigForwardingMap}），remove/put 会同步增删树节点；
 * Spigot 上为普通 Map，同样满足语义，因此本实现只使用 Bukkit API。
 */
public final class CommandLabelDeduper {

    private CommandLabelDeduper() {
    }

    /**
     * 延迟到下一 tick 执行整理。插件 enable 阶段所有命令尚未全部就位，
     * 统一延后一个 tick 让其它插件完成注册后再判断归属。
     *
     * @param host   宿主插件
     * @param logger 日志
     */
    public static void schedule(JavaPlugin host, Logger logger) {
        Bukkit.getScheduler().runTask(host, () -> reconcile(host, logger));
    }

    /**
     * 立即执行一次整理。遍历 plugin.yml 声明的全部命令（含别名），
     * 对未绑定执行器且存在同名冲突的命令执行让位。
     *
     * @param host   宿主插件
     * @param logger 日志
     */
    public static void reconcile(JavaPlugin host, Logger logger) {
        Map<String, Command> known = knownCommands();
        if (known == null) {
            return;
        }
        for (String declared : host.getDescription().getCommands().keySet()) {
            PluginCommand pc = host.getCommand(declared);
            if (pc == null || pc.getExecutor() != host) {
                continue; // 已绑定执行器：冲突如实保留
            }
            List<String> labels = new ArrayList<>();
            labels.add(pc.getName());
            labels.addAll(pc.getAliases());
            for (String label : labels) {
                reconcileLabel(known, pc, label, logger);
            }
        }
    }

    private static void reconcileLabel(Map<String, Command> known, PluginCommand ours,
                                       String label, Logger logger) {
        List<String> ourKeys = new ArrayList<>();
        List<String> foreignNamespacedKeys = new ArrayList<>();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            String key = e.getKey();
            if (!baseName(key).equalsIgnoreCase(label)) {
                continue;
            }
            if (e.getValue() == ours) {
                ourKeys.add(key);
            } else if (key.indexOf(':') >= 0 && !key.startsWith("minecraft:")) {
                foreignNamespacedKeys.add(key);
            }
        }
        if (ourKeys.isEmpty()) {
            return;
        }
        if (known.get(label) == ours) {
            // 裸名归我方但命令未启用：让位给其它插件的名字空间变体
            if (foreignNamespacedKeys.isEmpty()) {
                return;
            }
            String foreignKey = foreignNamespacedKeys.get(0);
            Command foreign = known.get(foreignKey);
            if (foreign == null) {
                return;
            }
            ourKeys.forEach(known::remove);
            known.remove(foreignKey);
            known.put(label, foreign);
            logger.info("[Command] /" + label + " 已让位给 " + foreignKey + "（我方同名命令未启用）");
            return;
        }
        // 裸名归对方（或缺失）：移除我方名字空间影子
        for (String key : ourKeys) {
            if (!key.equals(label)) {
                known.remove(key);
                logger.fine("[Command] 移除未启用命令的影子标签 " + key);
            }
        }
    }

    private static String baseName(String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    /**
     * 恢复被整理移除的名字空间注册。模块重新绑定执行器时调用：
     * 若裸名已被其它插件占用且我方名字空间变体缺失，重新注册以恢复
     * {@code arcartx-suite:<name>} 入口。
     *
     * @param host    宿主插件
     * @param command 已重新绑定执行器的命令
     */
    public static void restoreNamespaced(JavaPlugin host, PluginCommand command) {
        CommandMap map = commandMap();
        if (map == null || command == null) {
            return;
        }
        String prefix = host.getName().toLowerCase(Locale.ROOT);
        if (map.getCommand(command.getName()) != command
            && map.getCommand(prefix + ":" + command.getName()) == null) {
            map.register(prefix, command);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands() {
        try {
            CommandMap map = commandMap();
            if (!(map instanceof SimpleCommandMap)) {
                return null;
            }
            // Paper 上该字段持有 BukkitBrigForwardingMap（Brigadier 树根转发视图），
            // Spigot 上为普通 HashMap，两者均可直接 remove/put。
            java.lang.reflect.Field f = SimpleCommandMap.class.getDeclaredField("knownCommands");
            f.setAccessible(true);
            return (Map<String, Command>) f.get(map);
        } catch (Exception e) {
            return null;
        }
    }

    private static CommandMap commandMap() {
        try {
            org.bukkit.Server server = Bukkit.getServer();
            java.lang.reflect.Field f = server.getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(server);
        } catch (Exception e) {
            return null;
        }
    }
}
