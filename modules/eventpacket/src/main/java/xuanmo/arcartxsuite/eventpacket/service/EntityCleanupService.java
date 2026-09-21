package xuanmo.arcartxsuite.eventpacket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Animals;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 实体清理服务（ClearLag 功能）。
 * <p>
 * 支持定时清理掉落物、怪物等，可配置警告倒计时和白名单。
 */
public final class EntityCleanupService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CleanupConfiguration config;
    private BukkitTask cleanupTask;
    private BukkitTask warningTask;

    public EntityCleanupService(JavaPlugin plugin, Logger logger, CleanupConfiguration config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void start() {
        if (!config.enabled() || config.intervalSeconds() <= 0) return;

        long intervalTicks = config.intervalSeconds() * 20L;
        long warningTicks = intervalTicks - (config.warningSeconds() * 20L);

        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::executeCleanup, intervalTicks, intervalTicks);

        if (config.warningSeconds() > 0 && config.warningSeconds() < config.intervalSeconds()) {
            warningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                String msg = ChatColor.translateAlternateColorCodes('&', config.warningMessage()
                    .replace("{seconds}", String.valueOf(config.warningSeconds())));
                Bukkit.broadcastMessage(msg);
            }, warningTicks, intervalTicks);
        }

        logger.info("实体清理服务已启动 (间隔: " + config.intervalSeconds() + "s)");
    }

    public void shutdown() {
        if (cleanupTask != null) { cleanupTask.cancel(); cleanupTask = null; }
        if (warningTask != null) { warningTask.cancel(); warningTask = null; }
    }

    public int executeCleanup() {
        int total = 0;
        List<World> worlds = config.worlds().isEmpty()
            ? Bukkit.getWorlds()
            : config.worlds().stream().map(Bukkit::getWorld).filter(w -> w != null).toList();

        for (World world : worlds) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (shouldRemove(entity)) {
                    entity.remove();
                    total++;
                }
            }
        }

        if (total > 0 && !config.cleanupMessage().isBlank()) {
            String msg = ChatColor.translateAlternateColorCodes('&', config.cleanupMessage()
                .replace("{count}", String.valueOf(total)));
            Bukkit.broadcastMessage(msg);
        }

        return total;
    }

    private boolean shouldRemove(Entity entity) {
        // 不清理玩家和命名实体
        if (entity.getType() == EntityType.PLAYER) return false;
        if (entity.getCustomName() != null && config.skipNamed()) return false;

        // 白名单检查
        if (config.entityWhitelist().contains(entity.getType().name())) return false;

        // 按类型清理
        if (config.clearDroppedItems() && entity instanceof Item) return true;
        if (config.clearMonsters() && entity instanceof Monster) return true;
        if (config.clearAnimals() && entity instanceof Animals) return true;
        if (config.clearEntityTypes().contains(entity.getType().name())) return true;

        return false;
    }

    /**
     * 清理配置。
     */
    public record CleanupConfiguration(
        boolean enabled,
        int intervalSeconds,
        int warningSeconds,
        String warningMessage,
        String cleanupMessage,
        boolean clearDroppedItems,
        boolean clearMonsters,
        boolean clearAnimals,
        boolean skipNamed,
        List<String> clearEntityTypes,
        List<String> entityWhitelist,
        List<String> worlds
    ) {
        public static CleanupConfiguration fromSection(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return new CleanupConfiguration(false, 300, 30,
                    "&c[清理] 将在 {seconds} 秒后清理掉落物！",
                    "&a[清理] 已清除 {count} 个实体。",
                    true, false, false, true,
                    List.of(), List.of(), List.of());
            }
            return new CleanupConfiguration(
                section.getBoolean("enabled", false),
                section.getInt("interval-seconds", 300),
                section.getInt("warning-seconds", 30),
                section.getString("warning-message", "&c[清理] 将在 {seconds} 秒后清理掉落物！"),
                section.getString("cleanup-message", "&a[清理] 已清除 {count} 个实体。"),
                section.getBoolean("clear-dropped-items", true),
                section.getBoolean("clear-monsters", false),
                section.getBoolean("clear-animals", false),
                section.getBoolean("skip-named", true),
                section.getStringList("clear-entity-types"),
                section.getStringList("entity-whitelist"),
                section.getStringList("worlds")
            );
        }
    }
}
