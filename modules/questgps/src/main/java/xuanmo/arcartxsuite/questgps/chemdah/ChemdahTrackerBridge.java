package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Reads Chemdah's native destinations while Suite owns its navigation rendering. */
public final class ChemdahTrackerBridge {
    private final Logger logger;
    private final boolean available;

    public ChemdahTrackerBridge(Logger logger) {
        this.logger = logger;
        this.available = ChemdahAPI.INSTANCE != null;
    }

    public boolean available() { return available; }

    public boolean hasQuestCenter(String questId, List<String> prioritizedTaskIds) {
        Template template = template(questId);
        if (template == null) return false;
        if (center(template) != null) return true;
        for (String id : taskIds(template, prioritizedTaskIds)) {
            if (center(template.getTaskMap().get(id)) != null) return true;
        }
        return false;
    }

    public boolean hasTaskCenter(String questId, String taskId) {
        Template template = template(questId);
        return template != null && center(template.getTaskMap().get(taskId)) != null;
    }

    public Optional<Location> resolveQuestLocation(Player player, String questId, List<String> prioritizedTaskIds) {
        try {
            var api = ChemdahAPI.INSTANCE;
            if (!api.isChemdahProfileLoaded(player)) return Optional.empty();
            Template template = template(questId);
            if (template == null) return Optional.empty();
            if (!AddonDepend.Companion.isQuestDependCompleted(template, player)) return Optional.empty();
            Location location = resolveCenter(player, center(template));
            if (location != null) return Optional.of(location);
            PlayerProfile profile = api.getChemdahProfile(player);
            return firstResolved(player, taskIds(template, prioritizedTaskIds), id -> {
                Task task = template.getTaskMap().get(id);
                return task != null && !task.isCompleted(profile) ? eligibleCenter(player, task) : null;
            });
        } catch (RuntimeException failure) {
            logger.fine("QuestGPS: 解析 Chemdah 任务坐标失败: " + failure.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Location> resolveTaskLocation(Player player, String questId, String taskId) {
        try {
            if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) return Optional.empty();
            Template template = template(questId);
            if (template == null) return Optional.empty();
            if (!AddonDepend.Companion.isQuestDependCompleted(template, player)) return Optional.empty();
            return Optional.ofNullable(resolveCenter(player, eligibleCenter(player, template.getTaskMap().get(taskId))));
        } catch (RuntimeException failure) {
            logger.fine("QuestGPS: 解析 Chemdah 目标坐标失败: " + failure.getMessage());
            return Optional.empty();
        }
    }

    private Template template(String questId) {
        return available ? ChemdahAPI.INSTANCE.getQuestTemplate(questId) : null;
    }

    private static Iterable<String> taskIds(Template template, List<String> prioritizedTaskIds) {
        return prioritizedTaskIds == null || prioritizedTaskIds.isEmpty()
            ? template.getTaskMap().keySet() : prioritizedTaskIds;
    }

    private static TrackCenter center(QuestContainer container) {
        return container != null && container.addon("track") instanceof AddonTrack track ? track.getCenter() : null;
    }

    private static TrackCenter eligibleCenter(Player player, QuestContainer container) {
        TrackCenter configured = center(container);
        return configured != null && AddonDepend.Companion.isQuestDependCompleted(container, player) ? configured : null;
    }

    static Optional<Location> firstResolved(Player player, Iterable<String> ids, Function<String, TrackCenter> centers) {
        for (String id : ids) {
            Location location = resolveCenter(player, centers.apply(id));
            if (location != null) return Optional.of(location);
        }
        return Optional.empty();
    }

    static Location resolveCenter(Player player, TrackCenter center) {
        if (center == null) return null;
        Location location = center.getLocation(player);
        return location != null && location.getWorld() != null
            && Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
            ? location : null;
    }
}
