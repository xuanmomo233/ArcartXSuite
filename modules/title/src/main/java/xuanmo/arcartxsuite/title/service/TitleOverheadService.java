package xuanmo.arcartxsuite.title.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleDefinition.OverheadMode;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * 称号头顶显示服务。
 * <p>
 * 根据已装备称号的头顶模式（纹理/文本/无），在玩家头顶渲染称号效果：
 * 纹理模式通过 WorldTextureBridge 挂载贴图实体，文本模式通过 Scoreboard Team 设置前后缀。
 * 模式切换时自动清理旧效果。属于 title 模块服务层。
 */
public class TitleOverheadService {

    private static final String EFFECT_PREFIX = "axs_title_";
    private static final String TEAM_PREFIX = "AXS_T_";

    private final WorldTextureBridgeAPI worldTextureService;
    private final Logger logger;
    private final Map<UUID, OverheadMode> activeMode = new ConcurrentHashMap<>();

    /**
     * @param worldTextureService 世界纹理桥接 API
     * @param logger              日志器
     */
    public TitleOverheadService(WorldTextureBridgeAPI worldTextureService, Logger logger) {
        this.worldTextureService = worldTextureService;
        this.logger = logger;
    }

    /**
     * 同步玩家的头顶显示效果。
     *
     * @param player       目标玩家
     * @param resolvedState 解析后的称号状态
     */
    public void sync(Player player, ResolvedTitleState resolvedState) {
        if (player == null || !player.isOnline()) return;

        TitleDefinition primaryEquipped = findPrimaryEquipped(resolvedState);
        OverheadMode targetMode = primaryEquipped == null ? OverheadMode.NONE : primaryEquipped.overheadMode();

        OverheadMode previousMode = activeMode.get(player.getUniqueId());
        if (previousMode != null && previousMode != targetMode) {
            clearMode(player, previousMode);
        }

        switch (targetMode) {
            case TEXTURE -> applyTexture(player, primaryEquipped);
            case TEXT -> applyText(player, primaryEquipped);
            case NONE -> {
                if (previousMode != null) clearMode(player, previousMode);
                activeMode.remove(player.getUniqueId());
                return;
            }
        }
        activeMode.put(player.getUniqueId(), targetMode);
    }

    /**
     * 清除玩家的头顶显示效果。
     *
     * @param player 目标玩家
     */
    public void clear(Player player) {
        if (player == null) return;
        OverheadMode mode = activeMode.remove(player.getUniqueId());
        if (mode != null) {
            clearMode(player, mode);
        }
    }

    /**
     * 关闭服务，清除所有在线玩家的头顶效果并注销 Scoreboard Team。
     */
    public void shutdown() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Map.Entry<UUID, OverheadMode> entry : activeMode.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                if (entry.getValue() == OverheadMode.TEXTURE && worldTextureService.isAvailable()) {
                    worldTextureService.removeFromEntity(player, effectId(player));
                }
            }
            String teamName = teamName(entry.getKey());
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
        activeMode.clear();
    }

    private void applyTexture(Player player, TitleDefinition definition) {
        if (!worldTextureService.isAvailable()) {
            applyText(player, definition);
            activeMode.put(player.getUniqueId(), OverheadMode.TEXT);
            return;
        }
        worldTextureService.removeFromEntity(player, effectId(player));
        worldTextureService.spawnOnEntity(
            player,
            effectId(player),
            definition.overheadTexture(),
            definition.overheadWidth(),
            definition.overheadHeight(),
            definition.overheadOffsetY(),
            true
        );
    }

    private void applyText(Player player, TitleDefinition definition) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        String prefix = definition.overheadPrefix().isBlank() ? "" : ChatColor.translateAlternateColorCodes('&', definition.overheadPrefix());
        String suffix = definition.overheadSuffix().isBlank() ? "" : ChatColor.translateAlternateColorCodes('&', definition.overheadSuffix());
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void clearMode(Player player, OverheadMode mode) {
        switch (mode) {
            case TEXTURE -> {
                if (worldTextureService.isAvailable() && player.isOnline()) {
                    worldTextureService.removeFromEntity(player, effectId(player));
                }
            }
            case TEXT -> {
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                String teamName = teamName(player.getUniqueId());
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    team.unregister();
                }
            }
            default -> {}
        }
    }

    private TitleDefinition findPrimaryEquipped(ResolvedTitleState resolvedState) {
        if (resolvedState == null || resolvedState.equippedTitlesByGroup().isEmpty()) {
            return null;
        }
        for (TitleDefinition def : resolvedState.equippedTitlesByGroup().values()) {
            if (def.overheadMode() != OverheadMode.NONE) {
                return def;
            }
        }
        return null;
    }

    private static String effectId(Player player) {
        return EFFECT_PREFIX + player.getUniqueId().toString().replace("-", "");
    }

    private static String teamName(UUID playerUuid) {
        String raw = TEAM_PREFIX + playerUuid.toString().replace("-", "");
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }
}
