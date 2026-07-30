package xuanmo.arcartxsuite.eventpacket.listener;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;

/**
 * Chemdah 事件桥接器。
 * <p>
 * Chemdah 事件类继承 taboolib BukkitProxyEvent（编译时不可用），需通过反射注册监听。
 */
public final class ChemdahEventBridge {

    private final Plugin plugin;
    private final Logger logger;
    private final EventPacketDispatchService dispatchService;
    private final java.util.List<Listener> registeredListeners = new java.util.ArrayList<>();

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
        registerQuestLifecycleEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Accept$Post", EventPacketTrigger.QUEST_ACCEPT);
        registerQuestLifecycleEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post", EventPacketTrigger.QUEST_COMPLETE);
        registerQuestLifecycleEvent(cl, "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post", EventPacketTrigger.QUEST_FAIL);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Complete$Post", EventPacketTrigger.OBJECTIVE_COMPLETE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Continue$Post", EventPacketTrigger.OBJECTIVE_CONTINUE);
        registerObjectiveEvent(cl, "ink.ptms.chemdah.api.event.collect.ObjectiveEvents$Restart$Post", EventPacketTrigger.OBJECTIVE_RESTART);
        registerLevelChangeEvent(cl);
        logger.info("EventPacket ChemdahBridge 已注册 Chemdah 事件监听器");
    }

    public void unregister() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    @SuppressWarnings("unchecked")
    private void registerQuestLifecycleEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getPlayerProfile = rawClass.getMethod("getPlayerProfile");
            Method getQuest = rawClass.getMethod("getQuest");
            Listener listener = new Listener() {};
            // Chemdah 事件均继承自 TabooLib BukkitProxyEvent，共享同一个 HandlerList，
            // 注册后其它 BukkitProxyEvent 子类事件也会触发本回调，需严格按运行时类型过滤。
            EventExecutor executor = (l, e) -> {
                if (e.getClass() != eventClass) return;
                handleQuestEvent(e, getPlayerProfile, getQuest, trigger);
            };
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册任务事件失败(" + className + "): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerObjectiveEvent(ClassLoader cl, String className, EventPacketTrigger trigger) {
        try {
            Class<?> rawClass = Class.forName(className, true, cl);
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getPlayerProfile = rawClass.getMethod("getPlayerProfile");
            Method getQuest = rawClass.getMethod("getQuest");
            Method getTask = rawClass.getMethod("getTask");
            Method getObjective = rawClass.getMethod("getObjective");
            Listener listener = new Listener() {};
            // 同上：BukkitProxyEvent 共享 HandlerList，严格按运行时类型过滤。
            EventExecutor executor = (l, e) -> {
                if (e.getClass() != eventClass) return;
                handleObjectiveEvent(e, getPlayerProfile, getQuest, getTask, getObjective, trigger);
            };
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册目标事件失败(" + className + "): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerLevelChangeEvent(ClassLoader cl) {
        try {
            Class<?> rawClass = Class.forName("ink.ptms.chemdah.api.event.collect.PlayerEvents$LevelChange", true, cl);
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getPlayer = rawClass.getMethod("getPlayer");
            Method getOption = rawClass.getMethod("getOption");
            Method getOldLevel = rawClass.getMethod("getOldLevel");
            Method getNewLevel = rawClass.getMethod("getNewLevel");
            Method getOldExperience = rawClass.getMethod("getOldExperience");
            Method getNewExperience = rawClass.getMethod("getNewExperience");
            Listener listener = new Listener() {};
            // 同上：BukkitProxyEvent 共享 HandlerList，严格按运行时类型过滤。
            EventExecutor executor = (l, e) -> {
                if (e.getClass() != eventClass) return;
                handleLevelChange(e, getPlayer, getOption, getOldLevel, getNewLevel, getOldExperience, getNewExperience);
            };
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
            registeredListeners.add(listener);
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 注册等级变化事件失败: " + e.getMessage());
        }
    }

    private void handleLevelChange(Event event, Method getPlayer, Method getOption,
            Method getOldLevel, Method getNewLevel, Method getOldExperience, Method getNewExperience) {
        try {
            Object rawPlayer = getPlayer.invoke(event);
            if (!(rawPlayer instanceof Player player) || !player.isOnline()) return;
            Object option = getOption.invoke(event);
            String optionId = "";
            if (option != null) {
                try {
                    Object id = option.getClass().getMethod("getId").invoke(option);
                    if (id instanceof String s) optionId = s;
                } catch (ReflectiveOperationException ignored) {}
            }
            int oldLevel = ((Number) getOldLevel.invoke(event)).intValue();
            int newLevel = ((Number) getNewLevel.invoke(event)).intValue();
            int oldExp = ((Number) getOldExperience.invoke(event)).intValue();
            int newExp = ((Number) getNewExperience.invoke(event)).intValue();
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

    private void handleQuestEvent(Event event, Method getPlayerProfile, Method getQuest, EventPacketTrigger trigger) {
        try {
            Object profile = getPlayerProfile.invoke(event);
            Object quest = getQuest.invoke(event);
            if (profile == null || quest == null) return;
            Player player = (Player) profile.getClass().getMethod("getPlayer").invoke(profile);
            if (player == null || !player.isOnline()) return;
            String questId = (String) quest.getClass().getMethod("getId").invoke(quest);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("quest_id", questId);
            dispatchService.dispatchAll(trigger, player,
                EventPacketContext.fromVariables(trigger, player, questId, variables));
        } catch (Exception | LinkageError e) {
            logger.warning("EventPacket ChemdahBridge 处理任务事件失败: " + e.getMessage());
        }
    }

    private void handleObjectiveEvent(Event event, Method getPlayerProfile, Method getQuest,
            Method getTask, Method getObjective, EventPacketTrigger trigger) {
        try {
            Object profile = getPlayerProfile.invoke(event);
            Object quest = getQuest.invoke(event);
            Object task = getTask.invoke(event);
            if (profile == null || quest == null || task == null) return;
            Player player = (Player) profile.getClass().getMethod("getPlayer").invoke(profile);
            if (player == null || !player.isOnline()) return;
            String questId = (String) quest.getClass().getMethod("getId").invoke(quest);
            String taskId = (String) task.getClass().getMethod("getId").invoke(task);
            String objectiveName = "";
            String objectiveType = "";
            Object objective = getObjective.invoke(event);
            if (objective != null) {
                objectiveType = objective.getClass().getSimpleName();
                try {
                    Object name = objective.getClass().getMethod("getName").invoke(objective);
                    if (name instanceof String s) objectiveName = s;
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
}
