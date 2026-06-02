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
import xuanmo.arcartxsuite.bridge.ArcartXWorldTextureService;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleDefinition.OverheadMode;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public class TitleOverheadService {

    private static final String EFFECT_PREFIX = "axs_title_";
    private static final String TEAM_PREFIX = "AXS_T_";

    private final ArcartXWorldTextureService worldTextureService;
    private final Logger logger;
    private final Map<UUID, OverheadMode> activeMode = new ConcurrentHashMap<>();

    public TitleOverheadService(ArcartXWorldTextureService worldTextureService, Logger logger) {
        this.worldTextureService = worldTextureService;
        this.logger = logger;
    }

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

    public void clear(Player player) {
        if (player == null) return;
        OverheadMode mode = activeMode.remove(player.getUniqueId());
        if (mode != null) {
            clearMode(player, mode);
        }
    }

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
