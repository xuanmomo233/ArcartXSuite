package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.api.ChemdahTrackAPI;
import ink.ptms.chemdah.api.ChemdahTrackAPI.TrackingSession;
import ink.ptms.chemdah.api.ChemdahTrackAPI.TrackingTarget;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackingSession;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 桥接 Chemdah 原生任务追踪 API，供 ArcartX 导航层消费坐标。
 */
public final class ChemdahTrackerBridge {

  private final Logger logger;
  private final boolean available;

  public ChemdahTrackerBridge(Logger logger) {
    this.logger = logger;
    this.available = probeAvailability();
    if (available) {
      logger.info("QuestGPS: Chemdah Tracker API 已就绪");
    } else {
      logger.warning("QuestGPS: Chemdah Tracker API 不可用，将回退 overlay 导航");
    }
  }

  public boolean available() {
    return available;
  }

  public boolean startQuestTracking(Player player) {
    if (!available || player == null) {
      return false;
    }
    try {
      QuestTrackHandler.INSTANCE.registerQuestSession(player);
      TrackingSession session = new QuestTrackingSession();
      ChemdahTrackAPI.INSTANCE.startTracking(player, session);
      return true;
    } catch (Exception ex) {
      logger.warning("QuestGPS 启动 Chemdah 追踪失败: " + ex.getMessage());
      return false;
    }
  }

  public void stopQuestTracking(Player player, String questId) {
    if (!available || player == null) {
      return;
    }
    try {
      ChemdahTrackAPI.INSTANCE.stopTracking(player, questId);
      QuestTrackHandler.INSTANCE.unregisterQuestSession(player);
    } catch (Exception ex) {
      logger.fine("QuestGPS 停止 Chemdah 追踪: " + ex.getMessage());
    }
  }

  public void stopAll(Player player) {
    if (!available || player == null) {
      return;
    }
    try {
      ChemdahTrackAPI.INSTANCE.stopTracking(player, QuestTrackingSession.SOURCE);
      QuestTrackHandler.INSTANCE.unregisterQuestSession(player);
    } catch (Exception ex) {
      logger.fine("QuestGPS 清除 Chemdah 追踪: " + ex.getMessage());
    }
  }

  public boolean isTracking(Player player, String questId) {
    if (!available || player == null) {
      return false;
    }
    try {
      return ChemdahTrackAPI.INSTANCE.isTracking(player, questId);
    } catch (Exception ex) {
      return false;
    }
  }

  public Optional<Location> resolveTrackingLocation(Player player, String questId) {
    if (!available || player == null) {
      return Optional.empty();
    }
    try {
      TrackingSession session = ChemdahTrackAPI.INSTANCE.getSession(player, questId);
      if (session == null) {
        session = ChemdahTrackAPI.INSTANCE.getSession(player, QuestTrackingSession.SOURCE);
      }
      if (session == null) {
        return Optional.empty();
      }
      List<TrackingTarget> targets = session.getTargets();
      if (targets == null || targets.isEmpty()) {
        return Optional.empty();
      }
      for (TrackingTarget target : targets) {
        TrackCenter center = target.getCenter();
        if (center == null) {
          continue;
        }
        Location location = center.getLocation(player);
        if (location != null && location.getWorld() != null) {
          return Optional.of(location);
        }
      }
    } catch (Exception ex) {
      logger.fine("QuestGPS 解析 Chemdah 追踪坐标失败: " + ex.getMessage());
    }
    return Optional.empty();
  }

  private boolean probeAvailability() {
    try {
      Class.forName("ink.ptms.chemdah.api.ChemdahTrackAPI");
      Class.forName("ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackingSession");
      return ChemdahTrackAPI.INSTANCE != null;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
