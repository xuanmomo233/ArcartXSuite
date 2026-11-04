package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.QuestDefinition;

/** Reads parsed native components; the view component overrides individual fields only. */
public final class ChemdahMetadataReader {
    private final Logger logger;

    public ChemdahMetadataReader(Logger logger) {
        this.logger = logger;
    }

    public String questDisplayName(Template template, QuestDefinition view) {
        Object meta = template.metaAll().get("name");
        String name = meta instanceof MetaName nativeName ? nativeName.getDisplayName() : "";
        return MessageProvider.translateColors(ChemdahViewText.choose(view.displayNameOverride(), name, template.getId()));
    }

    public List<String> questDescription(PlayerProfile profile, Template template, QuestDefinition view) {
        return translated(ChemdahViewText.choose(view.description(), nativeDescription(template, player(profile)), List.of()));
    }

    public String taskDisplayText(PlayerProfile profile, Template template, Task task, QuestDefinition view) {
        var override = view.task(task.getId());
        MetaName meta = task.meta("name");
        String nativeName = meta == null ? nativeText(task.addon("track"), "getName", player(profile)).stream().findFirst().orElse("")
            : meta.getDisplayName();
        String label = ChemdahViewText.choose(override == null ? "" : override.displayText(), nativeName, task.getId());
        return MessageProvider.translateColors(label + progressSuffix(profile, template, task));
    }

    public List<String> taskDescription(PlayerProfile profile, Task task, QuestDefinition view) {
        var override = view.task(task.getId());
        return translated(ChemdahViewText.choose(override == null ? List.of() : override.description(),
            nativeDescription(task, player(profile)), List.of()));
    }

    private List<String> nativeDescription(QuestContainer container, Player player) {
        List<String> ui = nativeText(container.addon("ui"), "getDescription", player);
        return ui.isEmpty() ? nativeText(container.addon("track"), "getDescription", player) : ui;
    }

    // These translated types are supplied by Chemdah's runtime language dependency, not Bukkit YAML.
    private List<String> nativeText(Addon addon, String getter, Player player) {
        if (addon == null) return List.of();
        try {
            Object text = addon.getClass().getMethod(getter).invoke(addon);
            if (text == null) return List.of();
            Object value = player == null
                ? text.getClass().getMethod("getDefault").invoke(text)
                : text.getClass().getMethod("get", String.class).invoke(text, player.getLocale());
            return ChemdahConfigAccessor.normalizeMultiline(value);
        } catch (ReflectiveOperationException e) {
            logger.fine("QuestGPS: 原生任务文字读取失败: " + e.getMessage());
            return List.of();
        }
    }

    private static Player player(PlayerProfile profile) {
        return profile == null ? null : profile.getPlayer();
    }

    private static List<String> translated(List<String> lines) {
        return lines.stream().map(MessageProvider::translateColors).toList();
    }

    public String taskStatusText(PlayerProfile profile, Task task, boolean completed, QuestGpsPage page) {
        if (completed) return "已完成";
        if (page == QuestGpsPage.COMPLETED) return "任务已结束（无历史进度）";
        return page == QuestGpsPage.AVAILABLE ? "未开始" : "进行中";
    }

    private String progressSuffix(PlayerProfile profile, Template template, Task task) {
        if (profile == null || task.getObjective() == null || profile.getQuestById(template.getId(), false) == null) return "";
        try {
            var progress = task.getObjective().getProgress(profile, task);
            if (progress == null || progress.getValue() == null || progress.getTarget() == null) return "";
            return " (" + progress.getValue() + "/" + progress.getTarget() + ")";
        } catch (RuntimeException e) {
            logger.fine("QuestGPS: 原生目标进度读取失败: " + template.getId() + "/" + task.getId() + " | " + e.getMessage());
            return "";
        }
    }
}
