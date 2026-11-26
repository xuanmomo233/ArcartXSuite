package xuanmo.arcartxsuite.announcer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration;
import xuanmo.arcartxsuite.announcer.service.AnnouncerService;
import xuanmo.arcartxsuite.announcer.service.SubtitleService;

/**
 * 公告轮播模块的主类，隶属于 ArcartXSuite 的 announcer 模块。
 * <p>
 * 负责加载公告配置、注册 ArcartX UI（公告 HUD 与字幕 HUD）、
 * 启动 {@link AnnouncerService} 与 {@link SubtitleService}，
 * 并提供 {@code /axs announcer} 命令的广播、字幕播放等子命令处理。
 * 支持单服轮播与跨服广播，可选转发到 QQ 机器人。
 */
public final class AnnouncerModule extends AbstractAXSModule implements ModuleCommandHandler {

    private AnnouncerModuleConfiguration configuration;
    private AnnouncerService service;
    private SubtitleService subtitleService;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("announcer")
            .name("Announcer")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXAnnouncer.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("entries")
                .build())
            .currentVersion(2)
            .validations(List.of(
                    ValidationRule.of("settings.check-interval-ticks", ValueType.INT)
                        .withRange(1, 1200),
                    ValidationRule.of("settings.cooldown-ms", ValueType.LONG)
                        .withRange(0, 3600000),
                    ValidationRule.of("settings.between-entry-interval-ms", ValueType.LONG)
                        .withRange(0, 3600000),
                    ValidationRule.of("settings.text-width-font-size", ValueType.INT)
                        .withRange(1, 200)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/announcer_hud.yml", "ui/announcer_hud.yml");
        mappings.put("arcartx/ui/subtitle_hud.yml", "ui/subtitle_hud.yml");
        return new ModuleUiSpec(mappings, configuration.overwriteUiFile());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data),
            0, AnnouncerService.CLICK_PACKET_ID, "announcer");
    }

    private String msg(String key, Object... args) {
        return messages().get("prefix") + messages().get(key, args);
    }

    /**
     * 加载模块配置：读取主 yml，准备公告条目目录（必要时导出默认条目），并解析为
     * {@link AnnouncerModuleConfiguration}。
     *
     * @param configFile 主配置文件，为 {@code null} 时抛出异常
     * @throws IllegalStateException 配置文件缺失时抛出
     * @throws Exception 解析配置过程中发生错误时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXAnnouncer.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String entriesDirRelative = yaml.getString("entries-directory", "announcer");
        File entriesDirectory = new File(dataFolder, entriesDirRelative);
        if (!entriesDirectory.exists()) {
            entriesDirectory.mkdirs();
        }
        File[] existing = entriesDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing == null || existing.length == 0) {
            File defaultEntries = new File(entriesDirectory, "default.yml");
            if (!defaultEntries.exists()) {
                exportResource("announcer/default.yml", defaultEntries, false);
            }
        }
        configuration = AnnouncerModuleConfiguration.load(yaml, logger, entriesDirectory);
    }

    /**
     * 启动公告与字幕服务：注册多套 UI，创建 {@link AnnouncerService} 与 {@link SubtitleService}，
     * 导出默认字幕组并注册 {@link SubtitlePlayable} 能力。
     *
     * @throws IllegalStateException UI 注册失败时抛出
     * @throws Exception 启动过程中发生错误时抛出
     */
    @Override
    protected void startService() throws Exception {
        // Announcer UI（支持多 UI）
        java.util.List<String> announcerRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.uiIds()) {
            UiBinding uiBinding = registerModuleUi(
                "arcartx/ui/announcer_hud.yml",
                "ui/announcer_hud.yml",
                candidateUiId,
                configuration.registerUiOnEnable()
            );
            if (uiBinding.registeredUiId() != null) {
                announcerRuntimeUiIds.add(uiBinding.runtimeUiId());
            }
        }
        if (announcerRuntimeUiIds.isEmpty()) {
            throw new IllegalStateException("Announcer UI 注册失败");
        }

        // Subtitle UI（支持多 UI）
        AnnouncerModuleConfiguration.SubtitleSettings subtitleCfg = configuration.subtitle();
        java.util.List<String> subtitleRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : subtitleCfg.uiIds()) {
            UiBinding subtitleUiBinding = registerModuleUi(
                "arcartx/ui/subtitle_hud.yml",
                "ui/subtitle_hud.yml",
                candidateUiId,
                configuration.registerUiOnEnable()
            );
            if (subtitleUiBinding.registeredUiId() != null) {
                subtitleRuntimeUiIds.add(subtitleUiBinding.runtimeUiId());
            }
        }

        // 跨服广播
        service = new AnnouncerService(
            plugin, logger, configuration, packetBridge,
            java.util.List.copyOf(announcerRuntimeUiIds),
            crossServer,
            placeholderResolver
        );
        service.setQQBotProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.QQBotBroadcastable.class));
        service.start();
        service.syncAfterReload();

        // Subtitle Service
        if (!subtitleRuntimeUiIds.isEmpty()) {
            // 导出内置默认字幕组（首次启动时）
            File groupsDir = new File(dataFolder, subtitleCfg.groupsDirectory());
            if (!groupsDir.exists()) {
                groupsDir.mkdirs();
                try {
                    exportConfigResource(
                        "subtitle/groups/default.yml",
                        "data/" + dataFolder.getName() + "/" + subtitleCfg.groupsDirectory() + "/default.yml",
                        false, moduleClassLoader()
                    );
                } catch (Exception ex) {
                    logger.warning("导出默认字幕组失败: " + ex.getMessage());
                }
            }

            subtitleService = new SubtitleService(
                plugin, logger, packetBridge,
                subtitleCfg, java.util.List.copyOf(subtitleRuntimeUiIds), dataFolder,
                placeholderResolver
            );
            subtitleService.loadGroups();
            registerCapability(SubtitlePlayable.class,
                (player, groupId) -> subtitleService != null && subtitleService.playGroup(player, groupId));
        }

        registerCapability(xuanmo.arcartxsuite.api.capability.AnnouncerBroadcastable.class,
            new xuanmo.arcartxsuite.api.capability.AnnouncerBroadcastable() {
                @Override public void broadcastNow(@NotNull String text) {
                    if (service != null) service.broadcastNow(text);
                }
                @Override public void broadcastNow(@NotNull String text, boolean forward) {
                    if (service != null) service.broadcastNow(text, forward);
                }
                @Override public void enqueue(@NotNull String text, boolean forward) {
                    if (service != null) service.enqueueManualBroadcast(text, forward);
                }
            });

        logger.fine(
            "Announcer 模块已载入，启用公告数: "
                + service.activeEntryCount()
                + "/" + configuration.entries().size()
                + " | UI: " + announcerRuntimeUiIds
                + " | Subtitle UI: " + subtitleRuntimeUiIds
                + " | 字幕组: " + (subtitleService != null ? subtitleService.groupCount() : 0)
                + " | 跨服: " + (service.crossServerActive() ? "ON" : "OFF")
        );
    }

    /**
     * 停止并释放字幕与公告服务，清空配置引用。
     */
    @Override
    protected void stopService() {
        if (subtitleService != null) {
            subtitleService.shutdown();
            subtitleService = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    /**
     * 创建客户端初始化回调，在玩家客户端就绪时通知公告与字幕服务。
     *
     * @return 客户端初始化回调
     */
    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) {
                service.markClientInitialized(player);
            }
            if (subtitleService != null) {
                subtitleService.handleClientReinitialized(player);
            }
        };
    }

    /**
     * 返回公告服务实例。
     *
     * @return 公告服务，模块未启动时为 {@code null}
     */
    public AnnouncerService getService() {
        return service;
    }

    /**
     * 返回字幕服务实例。
     *
     * @return 字幕服务，未启用字幕功能时为 {@code null}
     */
    public SubtitleService getSubtitleService() {
        return subtitleService;
    }

    // ─── ModuleCommandHandler ───────────────────────────────

    private static final List<String> ACTIONS = List.of("help", "status", "broadcast", "broadcastnow", "gbroadcast", "gbroadcastnow", "subtitle");

    @Override public String commandId() { return "announcer"; }
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 处理 {@code /axs announcer} 命令，分发到 help/status/reload/broadcast/subtitle 等子动作。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组，{@code args[1]} 为子动作
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> {
                String cmd = "/" + label + " announcer";
                sender.sendMessage(msg("help.title"));
                sender.sendMessage(msg("help.status", cmd));
                sender.sendMessage(msg("help.broadcast", cmd));
                sender.sendMessage(msg("help.broadcastnow", cmd));
                sender.sendMessage(msg("help.gbroadcast", cmd));
                sender.sendMessage(msg("help.gbroadcastnow", cmd));
                sender.sendMessage(msg("help.subtitle-list", cmd));
                sender.sendMessage(msg("help.subtitle-play", cmd));
                sender.sendMessage(msg("help.subtitle-stop", cmd));
            }
            case "status" -> {
                sender.sendMessage(msg("status.title"));
                sender.sendMessage(msg("status.active", service != null ? service.activeEntryCount() : 0));
                sender.sendMessage(msg("status.initialized", service != null ? service.initializedPlayerCount() : 0));
                sender.sendMessage(msg("status.groups", subtitleService != null ? subtitleService.groupCount() : 0));
                sender.sendMessage(msg("status.playing", subtitleService != null ? subtitleService.activePlayerCount() : 0));
                sender.sendMessage(msg("status.pending", service != null ? service.pendingManualBroadcasts() : 0));
                sender.sendMessage(msg("status.dropped", service != null ? service.droppedManualBroadcasts() : 0));
                sender.sendMessage(msg("status.cross-server",
                    service != null && service.crossServerActive()
                        ? messages().get("common.enabled") : messages().get("common.disabled")));
            }
            case "broadcast" -> handleBroadcast(sender, label, args, false, false);
            case "broadcastnow" -> handleBroadcast(sender, label, args, true, false);
            case "gbroadcast" -> handleBroadcast(sender, label, args, false, true);
            case "gbroadcastnow" -> handleBroadcast(sender, label, args, true, true);
            case "subtitle" -> handleSubtitle(sender, label, args);
            default -> sender.sendMessage(msg("common.unknown", label));
        }
        return true;
    }

    private void handleBroadcast(CommandSender sender, String label, String[] args, boolean immediate, boolean forward) {
        if (service == null) {
            sender.sendMessage(msg("broadcast.service-down"));
            return;
        }
        if (forward && (service == null || !service.crossServerActive())) {
            sender.sendMessage(msg("broadcast.cross-server-disabled"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("broadcast.usage", label, args[1]));
            return;
        }
        // args[2..] 拼接为广播文本
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String text = sb.toString();
        String scope = forward ? messages().get("broadcast.scope-global") : messages().get("broadcast.scope-local");
        if (immediate) {
            service.broadcastNow(text, forward);
            sender.sendMessage(msg("broadcast.now", scope, text));
        } else {
            if (!service.enqueueManualBroadcast(text, forward)) {
                sender.sendMessage(msg("broadcast.queue-full"));
                return;
            }
            sender.sendMessage(msg("broadcast.queued", scope, service.pendingManualBroadcasts(), text));
        }
    }

    private void handleSubtitle(CommandSender sender, String label, String[] args) {
        if (subtitleService == null) {
            sender.sendMessage(msg("subtitle.service-down"));
            return;
        }
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                var ids = subtitleService.groupIds();
                if (ids.isEmpty()) {
                    sender.sendMessage(msg("subtitle.list-empty"));
                } else {
                    sender.sendMessage(msg("subtitle.list-title", ids.size()));
                    for (String id : ids) {
                        sender.sendMessage(msg("subtitle.list-entry", id));
                    }
                }
            }
            case "play" -> {
                if (args.length < 5) {
                    sender.sendMessage(msg("subtitle.play-usage", label));
                    return;
                }
                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(msg("common.player-offline", args[3]));
                    return;
                }
                boolean ok = subtitleService.playGroup(target, args[4]);
                if (ok) {
                    sender.sendMessage(msg("subtitle.play-success", target.getName(), args[4]));
                } else {
                    sender.sendMessage(msg(subtitleService.groupIds().contains(args[4])
                        ? "subtitle.play-unavailable" : "subtitle.play-not-found", args[4]));
                }
            }
            case "stop" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("subtitle.stop-usage", label));
                    return;
                }
                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(msg("common.player-offline", args[3]));
                    return;
                }
                subtitleService.stopPlayer(target);
                sender.sendMessage(msg("subtitle.stop-success", target.getName()));
            }
            default -> sender.sendMessage(msg("subtitle.unknown", sub));
        }
    }

    /**
     * 提供 {@code /axs announcer} 命令的 Tab 补全：子动作、字幕子命令、在线玩家名与字幕组 id。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3 && "subtitle".equalsIgnoreCase(args[1])) {
            return filter(List.of("list", "play", "stop"), args[2]);
        }
        if (args.length == 4 && "subtitle".equalsIgnoreCase(args[1])
                && ("play".equalsIgnoreCase(args[2]) || "stop".equalsIgnoreCase(args[2]))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[3]);
        }
        if (args.length == 5 && "subtitle".equalsIgnoreCase(args[1]) && "play".equalsIgnoreCase(args[2])) {
            if (subtitleService != null) return filter(new ArrayList<>(subtitleService.groupIds()), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}

