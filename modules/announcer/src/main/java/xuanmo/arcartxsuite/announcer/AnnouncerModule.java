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
import xuanmo.arcartxsuite.bridge.ArcartXClientBridge;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration;
import xuanmo.arcartxsuite.announcer.config.AnnouncerProxyConfiguration;
import xuanmo.arcartxsuite.announcer.service.AnnouncerService;
import xuanmo.arcartxsuite.announcer.service.SubtitleService;
import xuanmo.arcartxsuite.announcer.transport.AnnouncerProxyTransport;

public final class AnnouncerModule extends AbstractAXSModule implements ModuleCommandHandler {

    private AnnouncerModuleConfiguration configuration;
    private AnnouncerService service;
    private SubtitleService subtitleService;
    private AnnouncerProxyTransport proxyTransport;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("announcer")
            .name("Announcer")
            .version("1.1.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXAnnouncer.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    private String msg(String key, Object... args) {
        return messages().get("prefix") + messages().get(key, args);
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // entries 由用户自由增删（每个公告条目自定义 id）
        return SyncPolicy.builder()
            .dynamicSection("entries")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // 公告轮播间隔（秒）
            ValidationRule.of("settings.interval-seconds", ValueType.INT)
                .withRange(1, 3600),
            // 字幕显示时长（秒）
            ValidationRule.of("subtitle.duration-seconds", ValueType.INT)
                .withRange(1, 60)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/announcer_hud.yml", "ui/announcer_hud.yml");
        mappings.put("arcartx/ui/subtitle_hud.yml", "ui/subtitle_hud.yml");
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        if (configuration == null) return false;
        return configuration.overwriteUiFile()
            || configuration.subtitle().overwriteUiFile();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXAnnouncer.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String entriesDirRelative = yaml.getString("entries-directory", "announcer");
        File entriesDirectory = new File(context.dataFolder(), entriesDirRelative);
        // 旧版 entries/ → 新版 announcer/：如果旧目录存在而新目录不存在，自动重命名
        if ("announcer".equals(entriesDirRelative)) {
            File legacyEntries = new File(context.dataFolder(), "entries");
            if (legacyEntries.isDirectory() && !entriesDirectory.exists()) {
                if (legacyEntries.renameTo(entriesDirectory)) {
                    context.logger().info("已将旧公告目录 entries/ 迁移至 announcer/");
                }
            }
        }
        if (!entriesDirectory.exists()) {
            entriesDirectory.mkdirs();
        }
        File defaultEntries = new File(entriesDirectory, "default.yml");
        if (!defaultEntries.exists()) {
            context.exportResource("announcer/default.yml", defaultEntries, false);
        }
        configuration = AnnouncerModuleConfiguration.load(yaml, context.logger(), entriesDirectory);
    }

    @Override
    protected void startService() throws Exception {
        // Announcer UI（支持多 UI）
        java.util.List<String> announcerRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.uiIds()) {
            File uiFile = new File(context.pluginDataFolder(), "ui/announcer_hud.yml");
            UiBinding uiBinding = context.prepareUiBinding(
                "Announcer", candidateUiId, configuration.registerUiOnEnable(), uiFile
            );
            if (uiBinding != null) {
                recordUiBinding("ui/announcer_hud.yml#" + candidateUiId, uiBinding);
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
            File subtitleUiFile = new File(context.pluginDataFolder(), "ui/subtitle_hud.yml");
            UiBinding subtitleUiBinding = context.prepareUiBinding(
                "Subtitle", candidateUiId, subtitleCfg.registerUiOnEnable(), subtitleUiFile
            );
            if (subtitleUiBinding != null) {
                recordUiBinding("ui/subtitle_hud.yml#" + candidateUiId, subtitleUiBinding);
                subtitleRuntimeUiIds.add(subtitleUiBinding.runtimeUiId());
            }
        }

        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        ArcartXClientBridge clientBridge = (ArcartXClientBridge) context.clientBridge();
        PacketGuardAPI packetGuard = context.packetGuard();

        // 跨服传输
        AnnouncerProxyConfiguration proxyCfg = configuration.proxy();
        // service 先声明为 null，再创建 transport 传入 consumer
        final AnnouncerService[] serviceRef = new AnnouncerService[1];
        proxyTransport = new AnnouncerProxyTransport(
            context.plugin(), proxyCfg,
            envelope -> { if (serviceRef[0] != null) serviceRef[0].handleRemoteEnvelope(envelope); }
        );
        boolean transportActive = proxyTransport.start();

        service = new AnnouncerService(
            context.plugin(), configuration, packetBridge, clientBridge, packetGuard,
            java.util.List.copyOf(announcerRuntimeUiIds),
            proxyTransport, proxyCfg.nodeId()
        );
        serviceRef[0] = service;
        service.setQQBotProvider(() -> context.getCapability(
            xuanmo.arcartxsuite.api.capability.QQBotBroadcastable.class));
        service.start();
        service.syncAfterReload();

        // Subtitle Service
        if (!subtitleRuntimeUiIds.isEmpty()) {
            // 一次性迁移老路径 plugins/ArcartXSuite/subtitle/ -> data/announcer/subtitle/
            // （subtitleCfg.groupsDirectory() 默认 "subtitle/groups"，整体迁移 "subtitle" 一次到位）
            context.migrateLegacyDirectory("subtitle");

            // 导出内置默认字幕组（首次启动时）
            File groupsDir = new File(context.dataFolder(), subtitleCfg.groupsDirectory());
            if (!groupsDir.exists()) {
                groupsDir.mkdirs();
                try {
                    context.exportConfigResource(
                        "subtitle/groups/default.yml",
                        "data/" + context.dataFolder().getName() + "/" + subtitleCfg.groupsDirectory() + "/default.yml",
                        false, moduleClassLoader()
                    );
                } catch (Exception ex) {
                    context.logger().warning("导出默认字幕组失败: " + ex.getMessage());
                }
            }

            subtitleService = new SubtitleService(
                context.plugin(), context.logger(), packetBridge,
                subtitleCfg, java.util.List.copyOf(subtitleRuntimeUiIds), context.dataFolder()
            );
            subtitleService.loadGroups();
            context.registerCapability(SubtitlePlayable.class,
                (player, groupId) -> subtitleService != null && subtitleService.playGroup(player, groupId));
        }

        context.logger().fine(
            "Announcer 模块已载入，启用公告数: "
                + service.activeEntryCount()
                + "/" + configuration.entries().size()
                + " | UI: " + announcerRuntimeUiIds
                + " | Subtitle UI: " + subtitleRuntimeUiIds
                + " | 字幕组: " + (subtitleService != null ? subtitleService.groupCount() : 0)
                + " | 跨服: " + (transportActive ? "ON" : "OFF")
        );
    }

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
        if (proxyTransport != null) {
            proxyTransport.shutdown();
            proxyTransport = null;
        }
        configuration = null;
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

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

    public AnnouncerService getService() {
        return service;
    }

    public SubtitleService getSubtitleService() {
        return subtitleService;
    }

    // ─── ModuleCommandHandler ───────────────────────────────

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "broadcast", "broadcastnow", "gbroadcast", "gbroadcastnow", "subtitle");

    @Override public String commandId() { return "announcer"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> {
                String cmd = "/" + label + " announcer";
                sender.sendMessage(msg("help.title"));
                sender.sendMessage(msg("help.status", cmd));
                sender.sendMessage(msg("help.reload", cmd));
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
                sender.sendMessage(msg("status.proxy",
                    proxyTransport != null && proxyTransport.isActive()
                        ? messages().get("common.enabled") : messages().get("common.disabled")));
            }
            case "reload" -> sender.sendMessage(msg("common.reload-hint", label));
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
        if (forward && (proxyTransport == null || !proxyTransport.isActive())) {
            sender.sendMessage(msg("broadcast.proxy-disabled"));
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
            service.enqueueManualBroadcast(text, forward);
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
                    sender.sendMessage(msg("subtitle.play-not-found", args[4]));
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
