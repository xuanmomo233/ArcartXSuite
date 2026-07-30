package xuanmo.arcartxsuite.eventpacket.listener;

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
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;

/**
 * Chemdah 事件桥接器。
 * <p>
 * 事件类通过反射加载（因为 Chemdah 事件继承 BukkitProxyEvent，编译时不可见），
 * 但数据提取使用直接 API 调用（PlayerProfile、Quest、Task 等在 compileOnly jar 中可用）。
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

    public void register() {
        Plugin chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdah == null) {
            logger.fine("EventPacket: 未检测到 Chemdah 插件，Chemdah 事件桥接已停用。");
            return;
        }
        ClassLoader cl = chemdah.getClass().getClassLoader();

        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Accept$Post", EventPacketTrigger.QUEST_ACCEPT);
        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post", EventPacketTrigger.QUEST_COMPLETE);
        registerQuestEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post", EventPacketTrigger.QUEST_FAIL);

        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Complete$Post", EventPacketTrigger.OBJECTIVE_COMPLETE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Continue$Post", EventPacketTrigger.OBJECTIVE_CONTINUE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Restart$Post", EventPacketTrigger.OBJECTIVE_RESTART);

        registerLevelChangeEvent(cl);

        int count = registeredListeners.size();
        if (count > 0) {
            logger.info("EventPacket ChemdahBridge 已注册 " + count + " 个 Chemdah 事件监听器");
        }
    }

    public void unregister() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    @SuppressWarnings("unchecked")
    private void registerQuestEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawEventClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (ignored, event) -> handleQuestEvent(event, trigger),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册任务事件失败(" + className + "): " + e.getMessage());
        }
    }

    private void handleQuestEvent(Event event, EventPacketTrigger trigger) {
        try {
            var getProfile = event.getClass().getMethod("getPlayerProfile");
            var getQuest = event.getClass().getMethod("getQuest");
            PlayerProfile profile = (PlayerProfile) getProfile.invoke(event);
            Quest quest = (Quest) getQuest.invoke(event);
            if (profile == null || quest == null) return;
            Player player = profile.getPlayer();
            if (player == null || !player.isOnline()) return;
            String questId = quest.getId();
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("quest_id", questId);
            dispatchService.dispatchAll(trigger, player,
                EventPacketContext.fromVariables(trigger, player, questId, variables));
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 处理任务事件失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerObjectiveEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawEventClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (ignored, event) -> handleObjectiveEvent(event, trigger),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册目标事件失败(" + trigger.configValue() + "): " + e.getMessage());
        }
    }

    private void handleObjectiveEvent(Event event, EventPacketTrigger trigger) {
        try {
            var getProfile = event.getClass().getMethod("getPlayerProfile");
            var getQuest = event.getClass().getMethod("getQuest");
            var getTask = event.getClass().getMethod("getTask");
            var getObjective = event.getClass().getMethod("getObjective");
            PlayerProfile profile = (PlayerProfile) getProfile.invoke(event);
            Quest quest = (Quest) getQuest.invoke(event);
            Task task = (Task) getTask.invoke(event);
            Object objective = getObjective.invoke(event);
            if (profile == null || quest == null || task == null) return;
            Player player = profile.getPlayer();
            if (player == null || !player.isOnline()) return;
            String questId = quest.getId();
            String taskId = task.getId();
            String objectiveName = "";
            String objectiveType = "";
            if (objective != null) {
                objectiveType = objective.getClass().getSimpleName();
                try {
                    var getName = objective.getClass().getMethod("getName");
                    Object nameResult = getName.invoke(objective);
                    objectiveName = nameResult != null ? String.valueOf(nameResult) : "";
                } catch (ReflectiveOperationException ignored) {}
            }
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("quest_id", questId);
            variables.put("task_id", taskId);
            variables.put("objective_type", objectiveType);
            variables.put("objective_name", objectiveName);
            dispatchService.dispatchAll(trigger, player,
                EventPacketContext.fromVariables(trigger, player, questId, variables));
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 处理目标事件失败(" + trigger.configValue() + "): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerLevelChangeEvent(ClassLoader cl) {
        try {
            Class<?> rawEventClass = Class.forName("ink.ptms.chemdah.api.event.collect.PlayerEvents$LevelChange", true, cl);
            if (!Event.class.isAssignableFrom(rawEventClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (ignored, event) -> handleLevelChangeEvent(event),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册等级变化事件失败: " + e.getMessage());
        }
    }

    private void handleLevelChangeEvent(Event event) {
        try {
            var getPlayer = event.getClass().getMethod("getPlayer");
            var getOption = event.getClass().getMethod("getOption");
            var getOldLevel = event.getClass().getMethod("getOldLevel");
            var getNewLevel = event.getClass().getMethod("getNewLevel");
            var getOldExperience = event.getClass().getMethod("getOldExperience");
            var getNewExperience = event.getClass().getMethod("getNewExperience");
            Object rawPlayer = getPlayer.invoke(event);
            if (!(rawPlayer instanceof Player player) || !player.isOnline()) return;
            Object rawOption = getOption.invoke(event);
            String optionId = "";
            if (rawOption != null) {
                var getOptionId = rawOption.getClass().getMethod("getId");
                optionId = String.valueOf(getOptionId.invoke(rawOption));
            }
            int oldLevel = toInt(getOldLevel.invoke(event));
            int newLevel = toInt(getNewLevel.invoke(event));
            int oldExp = toInt(getOldExperience.invoke(event));
            int newExp = toInt(getNewExperience.invoke(event));
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("level_option", optionId);
            variables.put("old_level", String.valueOf(oldLevel));
            variables.put("new_level", String.valueOf(newLevel));
            variables.put("old_experience", String.valueOf(oldExp));
            variables.put("new_experience", String.valueOf(newExp));
            variables.put("level_delta", String.valueOf(newLevel - oldLevel));
            dispatchService.dispatchAll(EventPacketTrigger.CHEMDAH_LEVEL_CHANGE, player,
                EventPacketContext.fromVariables(EventPacketTrigger.CHEMDAH_LEVEL_CHANGE, player, optionId, variables));
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 处理等级变化事件失败: " + e.getMessage());
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        return 0;
    }
}
