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
 * 异常退出恢复（BTDungeons status-file 同款思路）：
 * 玩家在副本中掉线/崩服 → 记录标记文件；下次进服执行恢复动作后删除。
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

    private void execute(Player p, String action, String dungeon) {
        String a = action == null ? "" : action.trim();
        if (a.isEmpty()) return;
        try {
            if (a.startsWith("[tell]")) {
                p.sendMessage(apply(a.substring(6), p, dungeon));
            } else if (a.startsWith("[title]")) {
                String[] parts = apply(a.substring(7), p, dungeon).split(";");
                String main = parts.length > 0 ? parts[0] : "";
                String sub = parts.length > 1 ? parts[1] : "";
                int fi = parts.length > 2 ? parseInt(parts[2], 10) : 10;
                int st = parts.length > 3 ? parseInt(parts[3], 40) : 40;
                int fo = parts.length > 4 ? parseInt(parts[4], 20) : 20;
                p.sendTitle(color(main), color(sub), fi, st, fo);
            } else if (a.startsWith("[console]")) {
                String cmd = apply(a.substring(9), p, dungeon);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else if (a.startsWith("[command]")) {
                String cmd = apply(a.substring(9), p, dungeon);
                p.performCommand(cmd);
            } else if (a.startsWith("[teleport]")) {
                String[] parts = apply(a.substring(10), p, dungeon).split(";");
                if (parts.length >= 4) {
                    World w = parts.length > 4 && !parts[4].isBlank()
                        ? Bukkit.getWorld(parts[4].trim()) : p.getWorld();
                    if (w != null) {
                        float yaw = parts.length > 5 ? (float) parseDouble(parts[5], 0) : 0f;
                        float pitch = parts.length > 6 ? (float) parseDouble(parts[6], 0) : 0f;
                        p.teleport(new Location(w, parseDouble(parts[0], 0),
                            parseDouble(parts[1], 64), parseDouble(parts[2], 0), yaw, pitch));
                    }
                }
            } else if (a.startsWith("[clear]")) {
                p.getInventory().clear();
            } else {
                p.sendMessage(apply(a, p, dungeon));
            }
        } catch (Exception e) {
            logger.fine("[abyss] 恢复动作失败 [" + a + "]: " + e.getMessage());
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
