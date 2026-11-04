package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.QuestDefinition;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.TaskDefinition;

/** Owns addon registration, late attachment, and immutable snapshots across native reloads. */
public final class QuestGpsNativeViews implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final QuestGpsModuleConfiguration configuration;
    private final Runnable refreshed;
    private final Listener listener = new Listener() {};
    private final Set<QuestContainer> attached = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final OwnedAddonRegistration<Class<? extends Addon>> registration;
    private Map<String, Template> lastTemplates = Map.of();
    private BukkitTask poll;
    private final DeferredViewRefresh refresh;
    private volatile boolean active;

    public QuestGpsNativeViews(JavaPlugin plugin, Logger logger, QuestGpsModuleConfiguration configuration, Runnable refreshed) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.refreshed = refreshed;
        registration = new OwnedAddonRegistration<>(ChemdahAPI.INSTANCE.getQuestAddon(), AxsQuestViewAddon.ID, AxsQuestViewAddon.class);
        refresh = new DeferredViewRefresh(action -> {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, action, 1L);
            return task::cancel;
        }, () -> { if (active) rebuild(true); });
    }

    public void start() {
        if (active) return;
        registration.register();
        active = true;
        refresh.start();
        try {
            listen("ink.ptms.chemdah.api.event.collect.PluginReloadEvent$Quest");
            listen("ink.ptms.chemdah.api.event.collect.TemplateEvents$Load");
            rebuild(true);
            // Also detects file watcher deletions and template replacements without a full reload event.
            poll = Bukkit.getScheduler().runTaskTimer(plugin, () -> rebuild(false), 40L, 40L);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void listen(String name) {
        try {
            var chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
            if (chemdah == null) throw new IllegalStateException("Chemdah 未启用");
            Class<?> type = Class.forName(name, true, chemdah.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(type)) throw new IllegalStateException("Chemdah 重载事件不可用: " + name);
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) type, listener, EventPriority.MONITOR,
                (unused, event) -> requestRefresh(), plugin, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Chemdah 版本不支持原生展示组件重载", e);
        }
    }

    public void requestRefresh() {
        if (!active) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::requestRefresh);
            return;
        }
        refresh.request();
    }

    private void rebuild(boolean forced) {
        if (!active) return;
        try {
            Map<String, Template> templates = snapshotTemplates();
            if (!forced && sameTemplates(lastTemplates, templates)) return;
            Map<String, QuestGpsCategory> categories = new LinkedHashMap<>(configuration.categoryStyles());
            for (Template template : templates.values()) {
                Object meta = template.metaAll().get("type");
                if (meta instanceof MetaType types) for (String type : types.getType()) addCategory(categories, type);
                ConfigurationSection view = attach(template);
                if (view.contains("category")) addCategory(categories, view.getString("category", ""));
            }
            Map<String, QuestDefinition> views = new LinkedHashMap<>();
            for (Template template : templates.values()) {
                Map<String, TaskDefinition> tasks = new LinkedHashMap<>();
                for (var task : template.getTaskMap().values()) {
                    try {
                        tasks.put(key(task.getId()), QuestGpsModuleConfiguration.readNativeTask(task.getId(), attach(task)));
                    } catch (IllegalArgumentException e) {
                        logger.warning("QuestGPS: " + template.getId() + "/" + task.getId() + " 展示设置无效，使用原生内容: " + e.getMessage());
                        tasks.put(key(task.getId()), QuestGpsModuleConfiguration.readNativeTask(task.getId(), new MemoryConfiguration()));
                    }
                }
                try {
                    views.put(key(template.getId()), QuestGpsModuleConfiguration.readNativeView(template.getId(),
                        attach(template), tasks, categories, logger));
                } catch (IllegalArgumentException e) {
                    logger.warning("QuestGPS: " + template.getId() + " 展示设置无效，使用原生内容: " + e.getMessage());
                    views.put(key(template.getId()), QuestGpsModuleConfiguration.readNativeView(template.getId(),
                        new MemoryConfiguration(), tasks, categories, logger));
                }
            }
            if (!sameTemplates(templates, snapshotTemplates())) return;
            configuration.definitions().publish(views, categories);
            // Release components on replaced templates too: active quests may still reference those templates.
            Set<QuestContainer> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (Template template : templates.values()) {
                live.add(template);
                live.addAll(template.getTaskMap().values());
            }
            attached.removeIf(container -> {
                if (live.contains(container)) return false;
                removeOwned(container);
                return true;
            });
            lastTemplates = templates;
            try {
                refreshed.run();
            } catch (RuntimeException e) {
                logger.warning("QuestGPS: 任务列表已更新，部分界面刷新失败: " + e.getMessage());
            }
        } catch (ConcurrentModificationException ignored) {
            // Native loading may be parallel; retain the last complete snapshot and retry on the next tick/poll.
        } catch (RuntimeException e) {
            logger.warning("QuestGPS: 原生展示刷新失败，保留上一次完整结果: " + e.getMessage());
        }
    }

    private ConfigurationSection attach(QuestContainer container) {
        Addon addon = container.addon(AxsQuestViewAddon.ID);
        if (addon != null && addon.getClass() != AxsQuestViewAddon.class
            && addon.getClass().getName().equals(AxsQuestViewAddon.class.getName())
            && ChemdahAPI.INSTANCE.getQuestAddon(AxsQuestViewAddon.ID) == AxsQuestViewAddon.class) {
            // A late native loader may have completed an instance from the previous module classloader.
            container.getAddonMap().remove(AxsQuestViewAddon.ID);
            addon = null;
        }
        if (addon != null) {
            if (addon.getClass() != AxsQuestViewAddon.class) throw new IllegalStateException("任务展示组件所有者冲突: " + container.getId());
            attached.add(container);
            return ((AxsQuestViewAddon) addon).view();
        }
        Object root = ChemdahConfigAccessor.nativeSection(container, "addon." + AxsQuestViewAddon.ID);
        if (root == null) return new MemoryConfiguration();
        if (!(root instanceof ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection section)) {
            throw new IllegalArgumentException("任务展示组件必须是配置节: " + container.getId());
        }
        AxsQuestViewAddon view = new AxsQuestViewAddon(section, container);
        container.getAddonMap().put(AxsQuestViewAddon.ID, view);
        attached.add(container);
        return view.view();
    }

    private static void addCategory(Map<String, QuestGpsCategory> categories, String name) {
        if (name == null || name.isBlank()) return;
        categories.putIfAbsent(key(name), new QuestGpsCategory(name, name, 300));
    }

    private static Map<String, Template> snapshotTemplates() {
        return Map.copyOf(ChemdahAPI.INSTANCE.getQuestTemplate());
    }

    static boolean sameTemplates(Map<String, ?> left, Map<String, ?> right) {
        if (!left.keySet().equals(right.keySet())) return false;
        for (String key : left.keySet()) if (left.get(key) != right.get(key)) return false;
        return true;
    }

    private static String key(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static void removeOwned(QuestContainer container) {
        Addon addon = container.getAddonMap().get(AxsQuestViewAddon.ID);
        if (addon != null && addon.getClass() == AxsQuestViewAddon.class) container.getAddonMap().remove(AxsQuestViewAddon.ID);
    }

    @Override
    public void close() {
        active = false;
        refresh.close();
        if (poll != null) poll.cancel();
        poll = null;
        HandlerList.unregisterAll(listener);
        // Include newly loaded templates whose delayed refresh has not run yet.
        try {
            for (Template template : snapshotTemplates().values()) {
                removeOwned(template);
                for (var task : template.getTaskMap().values()) removeOwned(task);
            }
        } catch (ConcurrentModificationException ignored) {
            // The owned instances already observed by this module are released below in all cases.
        }
        for (QuestContainer container : attached) removeOwned(container);
        attached.clear();
        lastTemplates = Map.of();
        registration.close();
        configuration.definitions().clear();
    }
}
