package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonStats;
import ink.ptms.chemdah.core.quest.objective.Progress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 从 Chemdah Template / Task 读取展示元数据，并按 overlay 策略合并。
 */
public final class ChemdahMetadataReader {

    private final Logger logger;

    public ChemdahMetadataReader(Logger logger) {
        this.logger = logger;
    }

    public String questDisplayName(
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        QuestGpsModuleConfiguration.PresentationDefaults defaults
    ) {
        String chemdahName = readMetaName(template);
        if (chemdahName.isBlank()) {
            chemdahName = ChemdahConfigAccessor.readString(template, "name", "");
        }
        return ChemdahConfigAccessor.pickString(
            overlay.presentation().source(defaults.source()),
            chemdahName,
            overlay.displayNameOverride(),
            template.getId()
        );
    }

    public List<String> questDescription(
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        QuestGpsModuleConfiguration.PresentationDefaults defaults
    ) {
        List<String> chemdahLines = readMetaDescription(template);
        if (chemdahLines.isEmpty()) {
            chemdahLines = ChemdahConfigAccessor.readMultiline(template, "description");
        }
        List<String> fallback = List.of("该任务未配置描述。");
        return ChemdahConfigAccessor.pickLines(
            overlay.presentation().source(defaults.source()),
            chemdahLines,
            overlay.description(),
            fallback
        );
    }

    public String taskDisplayText(
        PlayerProfile profile,
        Template template,
        Task task,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        QuestGpsModuleConfiguration.PresentationDefaults defaults
    ) {
        QuestGpsModuleConfiguration.TaskDefinition taskOverlay = overlay.task(task.getId());
        String overlayText = taskOverlay == null ? "" : taskOverlay.displayText();
        String chemdahName = ChemdahConfigAccessor.readString(task, "name", task.getId());
        String base = ChemdahConfigAccessor.pickString(
            overlay.presentation().source(defaults.source()),
            chemdahName,
            overlayText,
            task.getId()
        );
        String progressSuffix = progressSuffix(profile, template, task);
        if (progressSuffix.isEmpty()) {
            return base;
        }
        return base + progressSuffix;
    }

    public List<String> taskDescription(
        Task task,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        QuestGpsModuleConfiguration.PresentationDefaults defaults
    ) {
        QuestGpsModuleConfiguration.TaskDefinition taskOverlay = overlay.task(task.getId());
        List<String> overlayLines = taskOverlay == null ? List.of() : taskOverlay.description();
        List<String> chemdahLines = ChemdahConfigAccessor.readMultiline(task, "description");
        return ChemdahConfigAccessor.pickLines(
            overlay.presentation().source(defaults.source()),
            chemdahLines,
            overlayLines,
            List.of()
        );
    }

    public String taskStatusText(PlayerProfile profile, Task task, boolean completed, boolean availablePage) {
        if (completed) {
            return "已完成";
        }
        if (availablePage) {
            return "未开始";
        }
        return "进行中";
    }

    private String readMetaName(Template template) {
        ConfigurationSection section = ChemdahConfigAccessor.section(template);
        if (section == null) {
            return "";
        }
        ConfigurationSection meta = section.getConfigurationSection("meta");
        if (meta == null) {
            return "";
        }
        return meta.getString("name", "").trim();
    }

    private List<String> readMetaDescription(Template template) {
        ConfigurationSection section = ChemdahConfigAccessor.section(template);
        if (section == null) {
            return List.of();
        }
        ConfigurationSection meta = section.getConfigurationSection("meta");
        if (meta == null) {
            return List.of();
        }
        List<String> lines = ChemdahConfigAccessor.normalizeMultiline(meta.get("description"));
        if (!lines.isEmpty()) {
            return lines;
        }
        return ChemdahConfigAccessor.normalizeMultiline(meta.get("lore"));
    }

    private String progressSuffix(PlayerProfile profile, Template template, Task task) {
        if (profile == null || template == null || task == null) {
            return "";
        }
        try {
            AddonStats stats = template.addon("stats");
            if (stats == null) {
                return "";
            }
            CompletableFuture<Progress> future = stats.getProgress(profile, task);
            Progress progress = future.getNow(null);
            if (progress == null) {
                return "";
            }
            Object value = progress.getValue();
            Object target = progress.getTarget();
            if (value == null || target == null) {
                return "";
            }
            return " (" + value + "/" + target + ")";
        } catch (Exception ex) {
            logger.fine("QuestGPS: 读取 Chemdah 进度失败: " + template.getId() + "/" + task.getId() + " -> " + ex.getMessage());
            return "";
        }
    }
}
