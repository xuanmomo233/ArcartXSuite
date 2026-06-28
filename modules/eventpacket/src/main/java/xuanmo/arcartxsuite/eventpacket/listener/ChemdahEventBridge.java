package xuanmo.arcartxsuite.eventpacket.listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import ink.ptms.chemdah.core.PlayerProfile;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;

/**
 * Chemdah 事件桥接器。
 * <p>
 * 通过反射注册 Chemdah 事件，仅在 Chemdah 可用时实例化。
 * 使用与 QuestGpsService 相同的反射注册模式，避免 Chemdah 缺失时的 ClassNotFoundException。
 */
public final class ChemdahEventBridge {

    private final Plugin plugin;
    private final Logger logger;
    private final EventPacketDispatchService dispatchService;
    private final List<Listener> registeredListeners = new ArrayList<>();

    public ChemdahEventBridge(Plugin plugin, Logger logger, EventPacketDispatchService dispatchService) {
        this.plugin = plugin;
        this.logger = logger;
        this.dispatchService = dispatchService;
    }

    /**
     * 注册所有 Chemdah 事件监听。
     * 必须在确认 Chemdah 已加载后调用。
     */
    public void register() {
        Plugin chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdah == null) {
            return;
        }
        ClassLoader cl = chemdah.getClass().getClassLoader();

        // ── Quest 生命周期事件 ──
        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Accept$Post", EventPacketTrigger.QUEST_ACCEPT);
        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post", EventPacketTrigger.QUEST_COMPLETE);
        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post", EventPacketTrigger.QUEST_FAIL);

        // ── Objective 事件 ──
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Complete$Post", EventPacketTrigger.OBJECTIVE_COMPLETE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Continue$Post", EventPacketTrigger.OBJECTIVE_CONTINUE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Restart$Post", EventPacketTrigger.OBJECTIVE_RESTART);

        // ── 等级变化事件 ──
        registerLevelChangeEvent(cl);

        int count = registeredListeners.size();
        if (count > 0) {
            logger.info("EventPacket ChemdahBridge 已注册 " + count + " 个 Chemdah 事件监听器");
        }
    }

    /**
     * 注销所有已注册的 Chemdah 事件监听。
     */
    public void unregister() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    // ── Quest Accept / Complete / Fail ──────────────────────────

    @SuppressWarnings("unchecked")
    private void registerQuestEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawEventClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerProfile = rawEventClass.getMethod("getPlayerProfile");
            Method getQuest = rawEventClass.getMethod("getQuest");
            Class<?> questClass = Class.forName("ink.ptms.chemdah.core.quest.Quest", true, cl);
            Method getQuestId = questClass.getMethod("getId");

            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> handleQuestEvent(event, rawEventClass, getPlayerProfile, getQuest, getQuestId, trigger),
                plugin,
                true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException exception) {
            logger.warning("EventPacket ChemdahBridge 注册任务事件失败(" + className + "): " + exception.getMessage());
        }
    }

    private void handleQuestEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getQuestId,
        EventPacketTrigger trigger
    ) {
        if (!expectedClass.isInstance(event)) {
            return;
        }
        try {
            Object rawProfile = invokeCompat(getPlayerProfile, event);
            Object rawQuest = invokeCompat(getQuest, event);
            Object rawQuestId = invokeCompat(getQuestId, rawQuest);
            if (rawProfile == null || rawQuest == null || rawQuestId == null) {
                return;
            }
            if (!(rawProfile instanceof PlayerProfile profile)) {
                return;
            }
            Player player = profile.getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }
            String questId = String.valueOf(rawQuestId);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("quest_id", questId);
            dispatchService.dispatchAll(
                trigger,
                player,
                EventPacketContext.fromVariables(trigger, player, questId, variables)
            );
        } catch (Exception exception) {
            logger.warning("EventPacket ChemdahBridge 处理任务事件失败: " + exception.getMessage());
        }
    }

    // ── Objective Events ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerObjectiveEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawEventClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerProfile = rawEventClass.getMethod("getPlayerProfile");
            Method getQuest = rawEventClass.getMethod("getQuest");
            Method getTask = rawEventClass.getMethod("getTask");
            Method getObjective = rawEventClass.getMethod("getObjective");
            Class<?> questClass = Class.forName("ink.ptms.chemdah.core.quest.Quest", true, cl);
            Method getQuestId = questClass.getMethod("getId");
            Class<?> taskClass = Class.forName("ink.ptms.chemdah.core.quest.Task", true, cl);
            Method getTaskId = taskClass.getMethod("getId");

            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> handleObjectiveEvent(
                    event, rawEventClass, getPlayerProfile, getQuest, getTask, getObjective, getQuestId, getTaskId, trigger
                ),
                plugin,
                true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException exception) {
            logger.warning("EventPacket ChemdahBridge 注册目标事件失败(" + trigger.configValue() + "): " + exception.getMessage());
        }
    }

    private void handleObjectiveCompleteEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getTask,
        Method getObjective,
        Method getQuestId,
        Method getTaskId
    ) {
        handleObjectiveEvent(event, expectedClass, getPlayerProfile, getQuest, getTask, getObjective, getQuestId, getTaskId, EventPacketTrigger.OBJECTIVE_COMPLETE);
    }

    private void handleObjectiveContinueEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getTask,
        Method getObjective,
        Method getQuestId,
        Method getTaskId
    ) {
        handleObjectiveEvent(event, expectedClass, getPlayerProfile, getQuest, getTask, getObjective, getQuestId, getTaskId, EventPacketTrigger.OBJECTIVE_CONTINUE);
    }

    private void handleObjectiveRestartEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getTask,
        Method getObjective,
        Method getQuestId,
        Method getTaskId
    ) {
        handleObjectiveEvent(event, expectedClass, getPlayerProfile, getQuest, getTask, getObjective, getQuestId, getTaskId, EventPacketTrigger.OBJECTIVE_RESTART);
    }

    private void handleObjectiveEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getTask,
        Method getObjective,
        Method getQuestId,
        Method getTaskId,
        EventPacketTrigger trigger
    ) {
        if (!expectedClass.isInstance(event)) {
            return;
        }
        try {
            Object rawProfile = invokeCompat(getPlayerProfile, event);
            Object rawQuest = invokeCompat(getQuest, event);
            Object rawTask = invokeCompat(getTask, event);
            Object rawObjective = invokeCompat(getObjective, event);
            if (rawProfile == null || rawQuest == null || rawTask == null) {
                return;
            }
            if (!(rawProfile instanceof PlayerProfile profile)) {
                return;
            }
            Player player = profile.getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }
            String questId = String.valueOf(invokeCompat(getQuestId, rawQuest));
            String taskId = String.valueOf(invokeCompat(getTaskId, rawTask));
            String objectiveName = "";
            String objectiveType = "";
            if (rawObjective != null) {
                objectiveType = rawObjective.getClass().getSimpleName();
                try {
                    Method getName = rawObjective.getClass().getMethod("getName");
                    Object nameResult = getName.invoke(rawObjective);
                    objectiveName = nameResult != null ? String.valueOf(nameResult) : "";
                } catch (ReflectiveOperationException ignored) {
                }
            }

            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("quest_id", questId);
            variables.put("task_id", taskId);
            variables.put("objective_type", objectiveType);
            variables.put("objective_name", objectiveName);
            dispatchService.dispatchAll(
                trigger,
                player,
                EventPacketContext.fromVariables(trigger, player, questId, variables)
            );
        } catch (Exception exception) {
            logger.warning("EventPacket ChemdahBridge 处理目标事件失败(" + trigger.configValue() + "): " + exception.getMessage());
        }
    }

    // ── Level Change ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerLevelChangeEvent(ClassLoader cl) {
        String className = "ink.ptms.chemdah.api.event.collect.PlayerEvents$LevelChange";
        try {
            Class<?> rawEventClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayer = rawEventClass.getMethod("getPlayer");
            Method getOption = rawEventClass.getMethod("getOption");
            Method getOldLevel = rawEventClass.getMethod("getOldLevel");
            Method getNewLevel = rawEventClass.getMethod("getNewLevel");
            Method getOldExperience = rawEventClass.getMethod("getOldExperience");
            Method getNewExperience = rawEventClass.getMethod("getNewExperience");

            // LevelOption.getId()
            Class<?> optionClass = Class.forName("ink.ptms.chemdah.module.level.LevelOption", true, cl);
            Method getOptionId = optionClass.getMethod("getId");

            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> handleLevelChangeEvent(
                    event, rawEventClass, getPlayer, getOption, getOptionId,
                    getOldLevel, getNewLevel, getOldExperience, getNewExperience
                ),
                plugin,
                true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException exception) {
            logger.warning("EventPacket ChemdahBridge 注册等级变化事件失败: " + exception.getMessage());
        }
    }

    private void handleLevelChangeEvent(
        Event event,
        Class<?> expectedClass,
        Method getPlayer,
        Method getOption,
        Method getOptionId,
        Method getOldLevel,
        Method getNewLevel,
        Method getOldExperience,
        Method getNewExperience
    ) {
        if (!expectedClass.isInstance(event)) {
            return;
        }
        try {
            Object rawPlayer = invokeCompat(getPlayer, event);
            if (!(rawPlayer instanceof Player player) || !player.isOnline()) {
                return;
            }
            Object rawOption = invokeCompat(getOption, event);
            String optionId = rawOption != null ? String.valueOf(invokeCompat(getOptionId, rawOption)) : "";
            int oldLevel = toInt(invokeCompat(getOldLevel, event));
            int newLevel = toInt(invokeCompat(getNewLevel, event));
            int oldExp = toInt(invokeCompat(getOldExperience, event));
            int newExp = toInt(invokeCompat(getNewExperience, event));

            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("level_option", optionId);
            variables.put("old_level", String.valueOf(oldLevel));
            variables.put("new_level", String.valueOf(newLevel));
            variables.put("old_experience", String.valueOf(oldExp));
            variables.put("new_experience", String.valueOf(newExp));
            variables.put("level_delta", String.valueOf(newLevel - oldLevel));

            dispatchService.dispatchAll(
                EventPacketTrigger.CHEMDAH_LEVEL_CHANGE,
                player,
                EventPacketContext.fromVariables(EventPacketTrigger.CHEMDAH_LEVEL_CHANGE, player, optionId, variables)
            );
        } catch (Exception exception) {
            logger.warning("EventPacket ChemdahBridge 处理等级变化事件失败: " + exception.getMessage());
        }
    }

    // ── 工具方法 ────────────────────────────────────────────────

    private static Object invokeCompat(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
