package xuanmo.arcartxsuite.abyss.recovery;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration.RecoverySection;

/**
 * 异常退出恢复：玩家在副本中掉线/崩服 → 记录标记文件；
 * 下次进服执行恢复动作后删除。动作格式与副本脚本 DSL 一致。
 * <p>
 * 记录存 {@code data/abyss/recovery/<uuid>.yml}，轻量文件存储（量小，不上 DB）。
 */
public final class RecoveryManager {

    private final File dir;
    private final RecoverySection cfg;
    private final Logger logger;

    public RecoveryManager(File moduleDataFolder, RecoverySection cfg, Logger logger) {
        this.dir = new File(moduleDataFolder, "recovery");
        this.cfg = cfg;
        this.logger = logger;
    }

    /** 标记玩家非正常退出副本。 */
    public void mark(UUID uuid, String dungeonId) {
        if (!cfg.enabled()) return;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = fileOf(uuid);
            YamlConfiguration y = new YamlConfiguration();
            y.set("dungeon", dungeonId);
            y.set("time", System.currentTimeMillis());
            y.save(f);
        } catch (Exception e) {
            logger.fine("[abyss] 恢复标记写入失败: " + e.getMessage());
        }
    }

    /** 进服时检查并执行恢复动作；无标记直接返回。 */
    public void runIfMarked(Player player) {
        if (!cfg.enabled()) return;
        File f = fileOf(player.getUniqueId());
        if (!f.isFile()) return;
        // 先删标记再执行（防动作失败导致重复执行）
        String dungeon;
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            dungeon = y.getString("dungeon", "?");
            f.delete();
        } catch (Exception e) {
            f.delete();
            return;
        }
        for (String action : cfg.actions()) {
            execute(player, action, dungeon);
        }
    }

    /**
     * 恢复动作执行，格式与副本脚本 DSL 一致（单目标、无选择器）：
     * <pre>
     *   message &lt;消息&gt;                     玩家消息
     *   title &lt;in&gt; &lt;stay&gt; &lt;out&gt; &lt;主&gt;|&lt;副&gt;    标题
     *   console &lt;命令&gt;                    控制台执行
     *   player_command &lt;命令&gt;             玩家身份执行
     *   teleport [世界] &lt;x,y,z[,yaw,pitch]&gt; 传送
     *   clear_inventory                       清空背包
     *   aria: &lt;表达式&gt; / js: &lt;脚本&gt;    宿主脚本引擎
     * </pre>
     */
    private void execute(Player p, String action, String dungeon) {
        String a = action == null ? "" : action.trim();
        if (a.isEmpty()) return;
        try {
            String lower = a.toLowerCase();
            if (lower.startsWith("aria:")) {
                evalScript(p, xuanmo.arcartxsuite.api.condition.ScriptActionKind.ARIA,
                    apply(a.substring(5), p, dungeon));
            } else if (lower.startsWith("js:")) {
                evalScript(p, xuanmo.arcartxsuite.api.condition.ScriptActionKind.JS,
                    apply(a.substring(3), p, dungeon));
            } else if (lower.startsWith("message ")) {
                p.sendMessage(apply(a.substring(8), p, dungeon));
            } else if (lower.startsWith("title ")) {
                String[] parts = apply(a.substring(6), p, dungeon).split(" ", 4);
                int fi = parseInt(parts[0], 10);
                int st = parts.length > 1 ? parseInt(parts[1], 40) : 40;
                int fo = parts.length > 2 ? parseInt(parts[2], 20) : 20;
                String[] tt = (parts.length > 3 ? parts[3] : "").split("\\|", 2);
                p.sendTitle(color(tt[0]), tt.length > 1 ? color(tt[1]) : "", fi, st, fo);
            } else if (lower.startsWith("console ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    apply(a.substring(8), p, dungeon));
            } else if (lower.startsWith("player_command ")) {
                p.performCommand(apply(a.substring(15), p, dungeon));
            } else if (lower.startsWith("teleport ")) {
                // teleport [世界名] <x,y,z[,yaw,pitch]>
                String[] parts = apply(a.substring(9), p, dungeon).split(" ", 2);
                String coordPart = parts.length > 1 ? parts[1] : parts[0];
                World w = parts.length > 1 && !parts[0].isBlank()
                    ? Bukkit.getWorld(parts[0].trim()) : p.getWorld();
                String[] c = coordPart.split(",");
                if (w != null && c.length >= 3) {
                    float yaw = c.length > 3 ? (float) parseDouble(c[3], 0) : 0f;
                    float pitch = c.length > 4 ? (float) parseDouble(c[4], 0) : 0f;
                    p.teleport(new Location(w, parseDouble(c[0], 0),
                        parseDouble(c[1], 64), parseDouble(c[2], 0), yaw, pitch));
                }
            } else if (lower.equals("clear_inventory")) {
                p.getInventory().clear();
            } else {
                p.sendMessage(apply(a, p, dungeon));
            }
        } catch (Exception e) {
            logger.fine("[abyss] 恢复动作失败 [" + a + "]: " + e.getMessage());
        }
    }

    private void evalScript(Player p, xuanmo.arcartxsuite.api.condition.ScriptActionKind kind,
                            String code) {
        if (code.isEmpty()) return;
        try {
            xuanmo.arcartxsuite.api.condition.ScriptConditionServices
                .actionExecutor().execute(p, kind, code);
        } catch (Throwable t) {
            logger.fine("[abyss] 恢复 " + kind + " 脚本异常: " + t.getMessage());
        }
    }

    private static String apply(String raw, Player p, String dungeon) {
        return color(raw.replace("{player}", p.getName()).replace("{dungeon}", dungeon).trim());
    }

    private static String color(String s) {
        return s.replace('&', '§');
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private File fileOf(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    /** 清理目录（关服用）。 */
    public void shutdown() { }
}
