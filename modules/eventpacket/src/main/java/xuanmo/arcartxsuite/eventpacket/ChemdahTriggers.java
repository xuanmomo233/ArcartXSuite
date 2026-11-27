package xuanmo.arcartxsuite.eventpacket;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Connects the configured Chemdah events to the module's bounded realtime queue. */
final class ChemdahTriggers implements Listener, AutoCloseable {
    private static final String PLUGIN_NAME = "Chemdah";
    private static final String EVENT_PACKAGE = "ink.ptms.chemdah.api.event.collect.";
    private static final Map<String, String> EVENT_CLASSES = Map.of(
        "quest-accept", "QuestEvents$Accept$Post",
        "quest-complete", "QuestEvents$Complete$Post",
        "quest-fail", "QuestEvents$Fail$Post",
        "objective-complete", "ObjectiveEvents$Complete$Post",
        "objective-continue", "ObjectiveEvents$Continue$Post",
        "objective-restart", "ObjectiveEvents$Restart$Post",
        "level-change", "PlayerEvents$LevelChange"
    );

    @FunctionalInterface
    interface Handler {
        void publish(String event, Player player, Map<String, String> fields);
    }

    @FunctionalInterface
    private interface PayloadReader {
        Payload read(Event event) throws ReflectiveOperationException;
    }

    private record Payload(Player player, Map<String, String> fields) {}

    private final JavaPlugin owner;
    private final Logger logger;
    private final Set<String> events;
    private final Handler handler;
    private final PluginManager plugins;
    private final Listener chemdahEvents = new Listener() {};
    private volatile Plugin attached;

    ChemdahTriggers(JavaPlugin plugin, Logger logger, Set<String> events, Handler handler) {
        this.owner = plugin;
        this.logger = logger;
        this.events = Set.copyOf(events);
        this.handler = handler;
        this.plugins = plugin.getServer().getPluginManager();
        plugins.registerEvents(this, owner);
        Plugin dependency = plugins.getPlugin(PLUGIN_NAME);
        if (dependency == null) {
            logger.warning("EventPacket 配置了 Chemdah 规则，但未找到 Chemdah 插件。");
        } else if (dependency.isEnabled()) {
            attach(dependency);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (PLUGIN_NAME.equals(event.getPlugin().getName())) attach(event.getPlugin());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == attached) detach();
    }

    private void attach(Plugin dependency) {
        if (attached == dependency) return;
        detach();
        attached = dependency;
        ClassLoader loader = dependency.getClass().getClassLoader();
        for (String event : events) {
            String className = EVENT_PACKAGE + EVENT_CLASSES.get(event);
            try {
                Class<? extends Event> eventClass = Class.forName(className, true, loader).asSubclass(Event.class);
                PayloadReader reader = event.equals("level-change") ? levelReader(eventClass)
                    : questReader(eventClass, event.startsWith("objective-"));
                AtomicBoolean reportedFailure = new AtomicBoolean();
                plugins.registerEvent(eventClass, chemdahEvents, EventPriority.MONITOR, (listener, rawEvent) -> {
                    // The event classes share the base BukkitProxyEvent handler list.
                    if (rawEvent.getClass() != eventClass || attached != dependency) return;
                    try {
                        Payload payload = reader.read(rawEvent);
                        if (payload != null) handler.publish(event, payload.player(), payload.fields());
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                        if (reportedFailure.compareAndSet(false, true)) {
                            logger.log(Level.WARNING, "EventPacket 读取 Chemdah 事件失败 " + event, failure);
                        }
                    }
                }, owner, true);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                logger.log(Level.WARNING, "EventPacket 无法监听 Chemdah 事件 " + event, failure);
            }
        }
    }

    private static PayloadReader questReader(Class<? extends Event> type, boolean objective)
            throws NoSuchMethodException {
        Method profile = type.getMethod("getPlayerProfile");
        Method player = profile.getReturnType().getMethod("getPlayer");
        Method quest = type.getMethod("getQuest");
        Method questId = quest.getReturnType().getMethod("getId");
        Method task = objective ? type.getMethod("getTask") : null;
        Method taskId = objective ? task.getReturnType().getMethod("getId") : null;
        Method objectiveGetter = objective ? type.getMethod("getObjective") : null;
        Method objectiveName = objective ? objectiveGetter.getReturnType().getMethod("getName") : null;
        return event -> {
            Player target = (Player) player.invoke(profile.invoke(event));
            if (target == null) return null;
            String id = (String) questId.invoke(quest.invoke(event));
            if (!objective) return new Payload(target, Map.of("quest_id", id));
            Object goal = objectiveGetter.invoke(event);
            return new Payload(target, Map.of(
                "quest_id", id,
                "task_id", (String) taskId.invoke(task.invoke(event)),
                "objective_type", goal.getClass().getSimpleName(),
                "objective_name", (String) objectiveName.invoke(goal)
            ));
        };
    }

    private static PayloadReader levelReader(Class<? extends Event> type) throws NoSuchMethodException {
        Method player = type.getMethod("getPlayer");
        Method option = type.getMethod("getOption");
        Method optionId = option.getReturnType().getMethod("getId");
        Method oldLevel = type.getMethod("getOldLevel");
        Method newLevel = type.getMethod("getNewLevel");
        Method oldExperience = type.getMethod("getOldExperience");
        Method newExperience = type.getMethod("getNewExperience");
        return event -> {
            Player target = (Player) player.invoke(event);
            if (target == null) return null;
            int before = (int) oldLevel.invoke(event);
            int after = (int) newLevel.invoke(event);
            return new Payload(target, Map.of(
                "level_option", (String) optionId.invoke(option.invoke(event)),
                "old_level", Integer.toString(before),
                "new_level", Integer.toString(after),
                "old_experience", Integer.toString((int) oldExperience.invoke(event)),
                "new_experience", Integer.toString((int) newExperience.invoke(event)),
                "level_delta", Long.toString((long) after - before)
            ));
        };
    }

    private void detach() {
        attached = null;
        HandlerList.unregisterAll(chemdahEvents);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        detach();
    }
}
