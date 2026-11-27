package xuanmo.arcartxsuite.eventpacket;

import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.check;
import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.number;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/** Resumes in the next tick at the same chunk and entity. Never requests an unloaded chunk. */
final class EntityCleanup {
    record Settings(long period, long warning, int workPerTick, Set<String> worlds, boolean items, boolean monsters,
                    boolean animals, boolean skipNamed, Set<EntityType> types, Set<EntityType> excluded,
                    String warningMessage, String resultMessage) {
        static Settings parse(ConfigurationSection config) {
            var section = config.getConfigurationSection("cleanup");
            check(section != null || !config.contains("cleanup"), "cleanup", "需要配置映射");
            if (section == null || !section.getBoolean("enabled")) return null;
            int period = number(section, "interval-seconds", 300, 15, 86_400);
            int warning = number(section, "warning-seconds", Math.min(30, period - 1), 0, period - 1);
            return new Settings(period * 20L, warning * 20L, number(section, "work-per-tick", 48, 1, 512),
                Set.copyOf(section.getStringList("worlds")), section.getBoolean("items", true),
                section.getBoolean("monsters"), section.getBoolean("animals"), section.getBoolean("skip-named", true),
                types(section, "entity-types"), types(section, "excluded-types"),
                section.getString("warning-message", "&e将在 {seconds} 秒后清理实体。"),
                section.getString("result-message", "&a已清理 {count} 个实体。"));
        }

        private static Set<EntityType> types(ConfigurationSection section, String field) {
            var types = EnumSet.noneOf(EntityType.class);
            for (String name : section.getStringList(field)) {
                try {
                    types.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException error) {
                    throw RuleConfiguration.invalid("cleanup." + field, "未知实体类型 " + name);
                }
            }
            return Set.copyOf(types);
        }

        boolean removes(Entity entity) {
            return entity.isValid() && !(entity instanceof Player) && entity.getType() != EntityType.PLAYER
                && !(skipNamed && entity.getCustomName() != null) && !excluded.contains(entity.getType())
                && (types.contains(entity.getType()) || (items && entity instanceof Item)
                    || (monsters && entity instanceof Monster) || (animals && entity instanceof Animals));
        }
    }

    private final Settings settings;
    private Iterator<World> worlds;
    private Chunk[] chunks = new Chunk[0];
    private Entity[] entities = new Entity[0];
    private int nextChunk;
    private int nextEntity;
    private int removed;
    private long nextRun;
    private boolean warned;
    private IntConsumer manualResult;

    EntityCleanup(Settings settings) {
        this.settings = settings;
        nextRun = settings.period();
    }

    boolean request(long tick, IntConsumer result) {
        if (worlds != null || manualResult != null) return false;
        manualResult = result;
        nextRun = tick;
        warned = true;
        return true;
    }

    void tick(long tick) {
        if (worlds == null) {
            if (!warned && settings.warning() > 0 && tick >= nextRun - settings.warning()) {
                warned = true;
                announce(settings.warningMessage(), "{seconds}", settings.warning() / 20);
            }
            if (tick < nextRun) return;
            List<World> selected = Bukkit.getWorlds().stream()
                .filter(world -> settings.worlds().isEmpty() || settings.worlds().contains(world.getName())).toList();
            worlds = selected.iterator();
        }
        long start = System.nanoTime();
        for (int work = 0; work < settings.workPerTick() && System.nanoTime() - start < 750_000L; work++) {
            if (nextEntity < entities.length) {
                Entity entity = entities[nextEntity++];
                if (settings.removes(entity)) {
                    entity.remove();
                    removed++;
                }
            } else if (nextChunk < chunks.length) {
                Chunk chunk = chunks[nextChunk++];
                entities = chunk.isLoaded() && chunk.isEntitiesLoaded() ? chunk.getEntities() : new Entity[0];
                nextEntity = 0;
            } else if (worlds.hasNext()) {
                World world = worlds.next();
                chunks = Bukkit.getWorld(world.getUID()) == world ? world.getLoadedChunks() : new Chunk[0];
                nextChunk = 0;
            } else {
                int count = removed;
                IntConsumer manual = manualResult;
                clear(tick);
                if (count > 0) announce(settings.resultMessage(), "{count}", count);
                if (manual != null) manual.accept(count);
                return;
            }
        }
    }

    void clear(long tick) {
        worlds = null;
        chunks = new Chunk[0];
        entities = new Entity[0];
        nextChunk = nextEntity = removed = 0;
        nextRun = tick + settings.period();
        warned = false;
        manualResult = null;
    }

    private static void announce(String message, String placeholder, long value) {
        if (!message.isBlank()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                message.replace(placeholder, Long.toString(value))));
        }
    }
}
