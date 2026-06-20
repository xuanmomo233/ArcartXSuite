package xuanmo.arcartxsuite.announcer.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration.SubtitleSettings;
import xuanmo.arcartxsuite.api.config.UiIdParser;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;

/**
 * 字幕打字机播放服务。
 * <p>
 * 每个玩家同一时间只有一个字幕序列在播放。新的播放请求会终止旧序列。
 */
public final class SubtitleService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ArcartXPacketBridge packetBridge;
    private final SubtitleSettings settings;
    private final java.util.List<String> subtitleUiIds;
    private final File groupsDirectory;
    private final PlaceholderResolverAPI placeholderResolver;

    /** groupId -> definition */
    private final Map<String, SubtitleGroupDefinition> groups = new LinkedHashMap<>();

    /** 正在播放的玩家 -> 活跃任务 */
    private final Map<UUID, ActiveSubtitlePlayback> activePlayers = new ConcurrentHashMap<>();

    /** 玩家 -> 已为其打开过的 UI ID 集合，避免重复 openUi 触发 load 重置动画 */
    private final Map<UUID, Set<String>> playerOpenedUiIds = new ConcurrentHashMap<>();

    public SubtitleService(
        JavaPlugin plugin,
        Logger logger,
        ArcartXPacketBridge packetBridge,
        SubtitleSettings settings,
        java.util.List<String> subtitleUiIds,
        File dataFolder,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.subtitleUiIds = subtitleUiIds;
        this.groupsDirectory = new File(dataFolder, settings.groupsDirectory());
        this.placeholderResolver = placeholderResolver;
    }

    public void loadGroups() {
        groups.clear();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (!groupsDirectory.isDirectory()) {
            logger.warning("字幕组目录不存在: " + groupsDirectory.getAbsolutePath());
            return;
        }
        File[] files = groupsDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.info("字幕组目录为空: " + groupsDirectory.getAbsolutePath());
            return;
        }
        for (File file : files) {
            String groupId = file.getName().replace(".yml", "");
            SubtitleGroupDefinition group = loadGroup(groupId, file);
            if (group != null && !group.frames().isEmpty()) {
                groups.put(groupId, group);
            }
        }
        logger.fine("已加载 " + groups.size() + " 个字幕组。");
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (ActiveSubtitlePlayback playback : activePlayers.values()) {
            playback.cancel();
        }
        activePlayers.clear();
        playerOpenedUiIds.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    /**
     * 播放字幕组给指定玩家。
     *
     * @return true=成功开始播放，false=字幕组不存在
     */
    public boolean playGroup(Player player, String groupId) {
        SubtitleGroupDefinition group = groups.get(groupId);
        if (group == null) return false;

        // 取消旧播放任务（不发 close 包，新的 play 会直接覆盖客户端状态）
        ActiveSubtitlePlayback old = activePlayers.remove(player.getUniqueId());
        if (old != null) {
            old.cancel();
        }

        // 解析本组使用的 UI ID（组级覆盖全局）
        List<String> resolvedUiIds = resolveUiIds(group);

        // 仅对尚未打开的 UI 调用 openUi（避免重复 openUi 触发 load action 重置动画状态）
        if (packetBridge.isAvailable()) {
            Set<String> opened = playerOpenedUiIds.computeIfAbsent(
                player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            List<String> toOpen = new ArrayList<>();
            for (String uiId : resolvedUiIds) {
                if (opened.add(uiId)) {
                    toOpen.add(uiId);
                }
            }
            if (!toOpen.isEmpty()) {
                packetBridge.openUiAll(player, toOpen);
            }
        }

        // 开始新播放
        ActiveSubtitlePlayback playback = new ActiveSubtitlePlayback(player, group, resolvedUiIds);
        activePlayers.put(player.getUniqueId(), playback);
        playback.start();
        return true;
    }

    /**
     * 停止玩家当前字幕播放。
     */
    public void stopPlayer(Player player) {
        ActiveSubtitlePlayback playback = activePlayers.remove(player.getUniqueId());
        List<String> uiIds = (playback != null) ? playback.resolvedUiIds : subtitleUiIds;
        if (playback != null) {
            playback.cancel();
        }
        sendClose(player, uiIds);
    }

    /**
     * 检查玩家是否正在播放字幕。
     */
    public boolean isPlaying(Player player) {
        return activePlayers.containsKey(player.getUniqueId());
    }

    public Set<String> groupIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public int groupCount() {
        return groups.size();
    }

    public int activePlayerCount() {
        return activePlayers.size();
    }

    /**
     * 玩家退出时清理。
     */
    /**
     * 客户端重新初始化时清除 HUD 打开记录，下次播放会重新 openUi。
     */
    public void handleClientReinitialized(Player player) {
        playerOpenedUiIds.remove(player.getUniqueId());
    }

    public void handlePlayerQuit(UUID uuid) {
        ActiveSubtitlePlayback playback = activePlayers.remove(uuid);
        if (playback != null) {
            playback.cancel();
        }
        playerOpenedUiIds.remove(uuid);
    }

    // ─── 内部 ──────────────────────────────────────────────────────

    private void sendPlay(Player player, SubtitleGroupDefinition.SubtitleFrame frame, List<String> uiIds) {
        if (!packetBridge.isAvailable() || !player.isOnline()) return;
        String text = renderText(player, frame.text());
        int length = frame.length() > 0 ? frame.length() : visibleLength(text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        payload.put("length", length);
        payload.put("time", frame.timeMs());
        payload.put("showBackground", settings.showBackground());
        packetBridge.sendPacketToAll(player, uiIds, "play", payload);
        if (settings.debug()) {
            logger.info("Subtitle play -> " + player.getName()
                + " | ui=" + uiIds + " | text=" + text + " | length=" + length + " | time=" + frame.timeMs());
        }
    }

    private void sendClose(Player player, List<String> uiIds) {
        if (!packetBridge.isAvailable() || !player.isOnline()) return;
        packetBridge.sendPacketToAll(player, uiIds, "close", Map.of());
        if (settings.debug()) {
            logger.info("Subtitle close -> " + player.getName() + " | ui=" + uiIds);
        }
    }

    private List<String> resolveUiIds(SubtitleGroupDefinition group) {
        return (group.uiIds() != null && !group.uiIds().isEmpty()) ? group.uiIds() : subtitleUiIds;
    }

    private String renderText(Player player, String text) {
        if (text == null) return "";
        return placeholderResolver.applyPlaceholders(player, text);
    }

    private static int visibleLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 去除颜色代码后计算可见字符数
        String stripped = text.replaceAll("§[0-9a-fk-or]", "")
            .replaceAll("&[0-9a-fk-or]", "");
        return stripped.length();
    }

    private SubtitleGroupDefinition loadGroup(String groupId, File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            List<SubtitleGroupDefinition.SubtitleFrame> frames = new ArrayList<>();
            // 顶层数字节点按数字升序
            List<String> keys = new ArrayList<>(yaml.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section == null) continue;
                String text = section.getString("text", "");
                int length = section.getInt("length", 0);
                int timeMs = section.getInt("time", 1000);
                double keep = section.getDouble("keep", 1.0);
                frames.add(new SubtitleGroupDefinition.SubtitleFrame(text, length, timeMs, keep));
            }
            List<String> groupUiIds = UiIdParser.readUiIds(yaml, "ui-id");
            return new SubtitleGroupDefinition(groupId, List.copyOf(frames), groupUiIds.isEmpty() ? null : groupUiIds);
        } catch (Exception e) {
            logger.warning("加载字幕组失败 [" + groupId + "]: " + e.getMessage());
            return null;
        }
    }

    // ─── 活跃播放会话 ─────────────────────────────────────────────

    private final class ActiveSubtitlePlayback {
        private final Player player;
        private final SubtitleGroupDefinition group;
        private final List<String> resolvedUiIds;
        private int frameIndex = 0;
        private BukkitTask currentTask;

        ActiveSubtitlePlayback(Player player, SubtitleGroupDefinition group, List<String> resolvedUiIds) {
            this.player = player;
            this.group = group;
            this.resolvedUiIds = resolvedUiIds;
        }

        void start() {
            playNextFrame();
        }

        void cancel() {
            if (currentTask != null) {
                currentTask.cancel();
                currentTask = null;
            }
        }

        private void playNextFrame() {
            if (frameIndex >= group.frames().size() || !player.isOnline()) {
                // 播放完毕
                sendClose(player, resolvedUiIds);
                activePlayers.remove(player.getUniqueId());
                return;
            }
            SubtitleGroupDefinition.SubtitleFrame frame = group.frames().get(frameIndex);
            sendPlay(player, frame, resolvedUiIds);
            frameIndex++;

            // 计算下一帧延迟 = 动画时间 + 停留时间
            long delayTicks = (frame.timeMs() / 50L) + (long) (frame.keepSeconds() * 20);
            currentTask = Bukkit.getScheduler().runTaskLater(plugin, this::playNextFrame, delayTicks);
        }
    }
}
