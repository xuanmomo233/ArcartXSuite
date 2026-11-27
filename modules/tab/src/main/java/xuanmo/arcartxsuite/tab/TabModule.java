package xuanmo.arcartxsuite.tab;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

public final class TabModule extends AbstractAXSModule {
    private static final long HUD_OPEN_RETRY_TICKS = 20L;
    private static final int HUD_OPEN_ATTEMPTS = 3;

    private enum Layout {
        SIMPLE("tab-simple", "acf7830e649dc3a3197c87e8794fd678b025228a5ebb22e6510cc55537a68e99"),
        AVATAR("tab-avatar", "d66adad70923af0ef8ef1810cd9ec73f9cf5cbed54bf39dab9f43adb5e76e6da");

        private final String hudName;
        private final String previousDefaultHash;

        Layout(String hudName, String previousDefaultHash) {
            this.hudName = hudName;
            this.previousDefaultHash = previousDefaultHash;
        }

        String resourcePath() {
            return "arcartx/ui/" + hudName + ".yml";
        }

        String uiPath() {
            return "ui/tab/" + hudName + ".yml";
        }
    }

    private Layout layout;
    private boolean crossServerListEnabled;
    private String registeredHudId;
    private long hudGeneration;
    private TabCrossServerList crossServerList;
    private TabListSettings listSettings;
    private TabPlayerList playerList;
    private String nameFormat;
    private TabDisplayFormat displayFormat;
    private NativeTabNames nativeNames;
    private NativeTabOrder nativeOrder;
    private List<Player> nativeRanks = List.of();
    private ScheduledTask presentationTask;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("tab")
            .name("Tab")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .externalSoftDepends(List.of("PlaceholderAPI", "Vault", "LuckPerms"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder().configFileName("ArcartXTab.yml")
            .syncPolicy(SyncPolicy.builder().dynamicSection("player-list.groups").build()).build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return ModuleUiSpec.of(Map.of(
            Layout.SIMPLE.resourcePath(), Layout.SIMPLE.uiPath(),
            Layout.AVATAR.resourcePath(), Layout.AVATAR.uiPath()
        ));
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) {
        if (configFile == null) {
            throw new IllegalStateException("Tab 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        crossServerListEnabled = yaml.getBoolean("settings.cross-server-list", false);
        listSettings = yaml.getBoolean("player-list.enabled", false) ? TabListSettings.load(yaml) : null;
        if (yaml.contains("display.name-format") && !yaml.isString("display.name-format")) {
            throw new IllegalArgumentException("display.name-format 必须为字符串");
        }
        nameFormat = yaml.getString("display.name-format", "");
        if (nameFormat.length() > TabDisplayFormat.MAX_LENGTH || nameFormat.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("display.name-format 必须是最多 512 字符的单行文本");
        }
        String selected = yaml.getString("settings.layout", "simple").trim().toUpperCase(Locale.ROOT);
        try {
            layout = Layout.valueOf(selected);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("settings.layout 必须为 simple 或 avatar", failure);
        }
    }

    @Override
    protected void startService() throws IOException {
        for (Layout candidate : Layout.values()) {
            Path installed = pluginDataFolder.toPath().resolve(candidate.uiPath());
            Path backupFolder = pluginDataFolder.toPath().resolve("backup/tab");
            if (backupPreviousDefault(installed, backupFolder, candidate.previousDefaultHash)) {
                exportUiResource(candidate.resourcePath(), candidate.uiPath(), true, moduleClassLoader());
            }
        }
        String path = layout.uiPath();
        String id = "AXS:" + layout.hudName;
        var hudFile = new File(pluginDataFolder, path);
        var registration = packetBridge.registerOrReloadUi(id, hudFile);
        if (!registration.success()) {
            throw new IllegalStateException("Tab HUD 注册失败: " + registration.message());
        }
        registeredHudId = registration.runtimeUiId();
        recordUiBinding(path, new UiBinding(registeredHudId, registration.registeredUiId()));
        if (!nameFormat.isBlank()) {
            displayFormat = new TabDisplayFormat(nameFormat, placeholderResolver);
            nativeNames = new NativeTabNames();
        }
        playerList = listSettings == null ? null : new TabPlayerList(listSettings, placeholderResolver, displayFormat);
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerListEnabled && advancedCrossServerOrchestrationAllowed()) {
            crossServerList = TabCrossServerListFactory.create(plugin, this::advancedCrossServerOrchestrationAllowed, playerList);
            registerListener(crossServerList);
            registerCapability(TabRefreshable.class, crossServerList);
            crossServerList.requestGlobalRefresh("enable");
        }
        if (listSettings != null && listSettings.permitsNativeOrder() && crossServerList == null) {
            nativeOrder = NativeTabOrder.discover();
        }
        refreshNativePresentation();
        if (nativeOrder != null || nativeNames != null) {
            presentationTask = scheduler.scheduleAtFixedRate(plugin, this::refreshNativePresentation, 20L, 20L);
        }
        openDefaultHudForOnlinePlayers(hudFile, registeredHudId);
    }

    private void openDefaultHudForOnlinePlayers(File hudFile, String hudId) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        var hud = YamlConfiguration.loadConfiguration(hudFile);
        if (!"true".equalsIgnoreCase(hud.getString("ui.isHud", "false"))
            || !"true".equalsIgnoreCase(hud.getString("ui.defaultOpen", "true"))) return;

        var pending = new HashSet<Player>(Bukkit.getOnlinePlayers());
        long generation = hudGeneration;
        // Open after route registration. Definition delivery and screen opening are separate on the client.
        scheduler.scheduleOnce(plugin, () -> openHudForPendingPlayers(hudId, generation, pending, HUD_OPEN_ATTEMPTS), 1L);
    }

    private void openHudForPendingPlayers(String hudId, long generation, Set<Player> pending, int attemptsLeft) {
        if (!isReady() || generation != hudGeneration || !hudId.equals(registeredHudId)) return;
        pending.removeIf(player -> !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
            || !AuthAccess.admitted(player.getUniqueId()));
        for (Player player : List.copyOf(pending)) {
            packetBridge.openUiWithCallback(player, hudId, () -> pending.remove(player));
        }
        if (!pending.isEmpty() && attemptsLeft > 1) {
            scheduler.scheduleOnce(plugin,
                () -> openHudForPendingPlayers(hudId, generation, pending, attemptsLeft - 1), HUD_OPEN_RETRY_TICKS);
        }
    }

    private void refreshNativePresentation() {
        if (nativeOrder == null && nativeNames == null) return;
        var online = Bukkit.getOnlinePlayers();
        long now = System.nanoTime();
        if (nativeNames != null) {
            try {
                nativeNames.apply(displayFormat.namesFor(online, now));
            } catch (RuntimeException | LinkageError failure) {
                logger.warning("Tab 更新原版显示名失败：" + failure.getMessage());
                stopNativeNames();
            }
        }
        if (nativeOrder != null) {
            try {
                List<Player> ordered = online.stream().anyMatch(nativeOrder::supportsViewer)
                    ? playerList.orderedLocalPlayers(online, now) : List.of();
                if (!nativeOrder.apply(ordered)) {
                    logger.warning("Tab 原版排序被其他插件修改，已切换到自定义 HUD");
                    stopNativeOrder();
                } else {
                    nativeRanks = ordered;
                }
            } catch (RuntimeException | LinkageError unavailable) {
                logger.warning("Tab 原版排序执行失败，已切换到自定义 HUD：" + unavailable.getMessage());
                stopNativeOrder();
            }
        }
        if (nativeOrder == null && nativeNames == null) stopPresentationTask();
    }

    private void stopPresentationTask() {
        if (presentationTask == null) return;
        var task = presentationTask;
        presentationTask = null;
        try {
            task.cancel();
        } catch (RuntimeException failure) {
            logger.warning("Tab 取消原版名单更新任务失败：" + failure.getMessage());
        }
    }

    private void stopNativeOrder() {
        if (nativeOrder == null) return;
        nativeRanks = List.of();
        var retired = nativeOrder;
        nativeOrder = null;
        try {
            retired.close();
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("Tab 恢复原版名单顺序失败：" + failure.getMessage());
        }
    }

    private void stopNativeNames() {
        if (nativeNames == null) return;
        var retired = nativeNames;
        nativeNames = null;
        try {
            retired.close();
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("Tab 恢复原版显示名失败：" + failure.getMessage());
        }
    }

    /** Only refresh the previously shipped defaults and leave edited layouts in place. */
    static boolean backupPreviousDefault(Path installed, Path backupFolder, String previousDefaultHash) throws IOException {
        if (!Files.isRegularFile(installed, LinkOption.NOFOLLOW_LINKS) || Files.size(installed) > 64 * 1024) return false;
        String content = new String(Files.readAllBytes(installed), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        var normalized = content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
            if (!hash.equals(previousDefaultHash)) return false;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        Files.createDirectories(backupFolder);
        Path backup = Files.createTempFile(backupFolder, installed.getFileName().toString() + "-", ".bak");
        Files.copy(installed, backup, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((viewer, packetId, data) -> {
            if (registeredHudId != null && viewer.isOnline()
                && data.size() == 1 && ("open".equals(data.get(0)) || "refresh".equals(data.get(0)))) {
                if (playerList == null) {
                    packetBridge.sendPacket(viewer, registeredHudId, "tab-players", Map.of("custom", false, "rows", List.of()));
                } else {
                    var online = Bukkit.getOnlinePlayers();
                    boolean useNative = nativeOrder != null && !nativeRanks.isEmpty() && nativeOrder.supportsViewer(viewer)
                        && TabPlayerList.admissionAllowsNativeList(viewer, online);
                    if (useNative) {
                        // Keep vanilla's playback order, but never render a newly appeared unapproved entry.
                        var allowed = nativeRanks.stream()
                            .filter(player -> player.isOnline() && TabPlayerList.eligible(player) && viewer.canSee(player))
                            .limit(TabPlayerList.MAX_ROWS).map(Player::getUniqueId).map(UUID::toString).toList();
                        packetBridge.sendPacket(viewer, registeredHudId, "tab-players", Map.of("custom", false, "allowed", allowed));
                    } else {
                        var remote = crossServerList == null ? List.<TabPlayerList.RemotePlayer>of() : crossServerList.remotePlayers(viewer);
                        var rows = playerList.rowsFor(viewer, online, remote, System.nanoTime());
                        packetBridge.sendPacket(viewer, registeredHudId, "tab-players", Map.of("custom", true, "rows", rows));
                    }
                }
            }
            return true;
        }, 0, "AXS_TabPlayersRequest", "tab");
    }

    @Override
    protected void stopService() {
        hudGeneration++;
        stopPresentationTask();
        stopNativeOrder();
        stopNativeNames();
        displayFormat = null;
        playerList = null;
        if (crossServerList != null) {
            crossServerList.close();
            crossServerList = null;
        }
        if (registeredHudId != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                packetBridge.closeUi(player, registeredHudId);
            }
            registeredHudId = null;
        }
    }
}
