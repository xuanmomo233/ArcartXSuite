package xuanmo.arcartxsuite.eventpacket;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.CombatEffectTriggerable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

public final class EventPacketModule extends AbstractAXSModule implements ModuleCommandHandler {
    private static final List<String> RULE_FILES = List.of("rules/events.yml", "rules/sampling.yml",
        "rules/client.yml", "rules/mail.yml", "rules/schedule.yml");
    private final RealtimeRules realtime = new RealtimeRules();
    private final Map<String, String> subscriptions = new HashMap<>();
    private volatile RuleConfiguration configuration;
    private EventBusCapability bus;
    private DurableRules durable;
    private ModuleStorage storageScope;
    private ModuleCallback runtimeCalls;
    private File configurationFile;
    private ScheduledTask pollTask;
    private LocalTriggerRuntime localTriggers;
    private UiPacketBroadcasts uiBroadcasts;
    private List<ScheduledTask> timers = List.of();
    private String authSubscription;
    private String auctionSubscription;
    private String resultSubscription;
    private final AtomicLong actionFailures = new AtomicLong();
    private final AtomicInteger liveMailInFlight = new AtomicInteger();

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("eventpacket").name("EventPacket").version("3.3.7")
            .mainClass(getClass().getName()).externalSoftDepends(List.of("PlaceholderAPI", "MythicMobs", "Chemdah")).build();
    }

    @Override protected @NotNull ModuleConfig configSpec() {
        List<ModuleConfigSpec> files = new ArrayList<>();
        files.add(ModuleConfigSpec.basic("eventpacket",
            new ConfigSyncSpec("eventpacket/cleanup.yml", "data/eventpacket/cleanup.yml", SyncPolicy.strict())));
        for (String path : RULE_FILES) {
            files.add(ModuleConfigSpec.basic("eventpacket", new ConfigSyncSpec("eventpacket/" + path,
                "data/eventpacket/" + path, SyncPolicy.builder().opaqueSection("rules").build())));
        }
        return ModuleConfig.builder().configFileName("ArcartXEventPacket.yml").currentVersion(4)
            .additionalSpecs(files).build();
    }

    @Override protected void loadConfiguration(@Nullable File file) throws Exception {
        if (file == null) throw new IllegalStateException("EventPacket 配置缺失");
        for (var extra : configSpec().additionalSpecs()) {
            var sync = extra.sync();
            exportConfigResource(sync.resourcePath(), sync.targetRelativePath(), false, moduleClassLoader());
        }
        configurationFile = file;
        configuration = RuleConfiguration.load(file);
    }

    @Override protected void startService() throws Exception {
        bus = getCapability(EventBusCapability.class);
        if (bus == null) throw new IllegalStateException("Suite EventBus 不可用");
        runtimeCalls = new ModuleCallback(() -> {});
        storageScope = storageManager.openModuleStorage("eventpacket", "eventpacket.db", dataFolder, null);
        durable = new DurableRules(new RuleJournal(storageScope.dataSource(), storageManager.getDescriptor().isMysql()),
            () -> getCapability(AbyssRunResultAccess.class), () -> getCapability(AxsMailService.class),
            () -> configuration, this::advancedCrossServerOrchestrationAllowed, logger);
        var worker = durable;
        var calls = runtimeCalls;
        authSubscription = bus.subscribeConfirmed("eventpacket/admission-rules", RuleConfiguration.FIRST_ADMITTED,
            event -> confirmed(calls, () -> acceptAdmission(worker, event)));
        auctionSubscription = bus.subscribeConfirmed(EventBusCapability.EVENTPACKET_AUCTION_CONSUMER,
            RuleConfiguration.AUCTION_PURCHASED,
            event -> confirmed(calls, () -> acceptAuctionPurchase(worker, event)));
        resultSubscription = bus.subscribe(AbyssRunResultAccess.RESULT_COMMITTED_TOPIC,
            event -> calls.call(() -> { worker.abyssCommitted(); return null; }, null));
        registerCapability(SignalDispatchable.class,
            (name, player, fields) -> calls.call(() -> signal(name, player, fields), false));
        install(configuration);
        pollTask = scheduler.scheduleAtFixedRate(plugin, worker::pollIfDue, 1L, 20L);
    }

    @Override public boolean reloadsInPlace() {
        return true;
    }

    @Override public java.util.concurrent.CompletionStage<Void> onReload() {
        try {
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("EventPacket 重载必须在服务器线程执行");
            install(RuleConfiguration.load(configurationFile));
            durable.wake();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        } catch (Exception | LinkageError failure) {
            return java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
    }

    private void install(RuleConfiguration replacement) {
        var added = new HashMap<String, String>();
        var newTimers = new ArrayList<ScheduledTask>();
        var calls = runtimeCalls;
        var newLocal = new LocalTriggerRuntime(replacement.local(), realtime, placeholderResolver, scriptConditionEvaluator, logger);
        try {
            for (String topic : replacement.topics().keySet()) {
                if (topic.startsWith("axs.") && !subscriptions.containsKey(topic)) {
                    added.put(topic, bus.subscribe(topic,
                        event -> calls.call(() -> { receive(event); return null; }, null)));
                }
            }
            for (var timer : replacement.timers()) {
                Runnable fire = () -> {
                    if (configuration == replacement) {
                        realtime.offer(RuleConfiguration.timerTopic(timer.id()), null, Map.of());
                    }
                };
                newTimers.add(scheduler.scheduleCalendar(plugin, fire, timer.spec()));
            }
            newLocal.start(plugin, scheduler);
        } catch (RuntimeException | LinkageError failure) {
            added.values().forEach(bus::unsubscribe);
            newTimers.forEach(ScheduledTask::cancel);
            cleanup(newLocal::close);
            throw failure;
        }
        configuration = replacement;
        realtime.install(replacement, task -> scheduler.scheduleAtFixedRate(plugin, task, 1L, 1L), this::passes, this::execute);
        if (uiBroadcasts != null) cleanup(uiBroadcasts::close);
        uiBroadcasts = new UiPacketBroadcasts(task -> scheduler.scheduleAtFixedRate(plugin, task, 1L, 1L));
        var previousLocal = localTriggers;
        localTriggers = newLocal;
        if (previousLocal != null) cleanup(previousLocal::close);
        subscriptions.putAll(added);
        subscriptions.entrySet().removeIf(entry -> {
            if (replacement.topics().containsKey(entry.getKey())) return false;
            bus.unsubscribe(entry.getValue());
            return true;
        });
        timers.forEach(ScheduledTask::cancel);
        timers = List.copyOf(newTimers);
    }

    @Override protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, args) -> {
            var current = configuration;
            if (current == null || args.size() != 2 || !"client-packet".equals(args.get(0)) || args.get(1).length() > 64) {
                return true;
            }
            var rule = current.local().client().get(args.get(1));
            if (rule != null && player.hasPermission(rule.permission()) && player.isPermissionSet(rule.permission())) {
                realtime.offer(rule.topic(), AuthAccess.snapshot(player.getUniqueId()), Map.of("player_name", player.getName()));
            }
            return true;
        }, 0, LocalTriggerSettings.PACKET_ID, "eventpacket");
    }

    private static CompletableFuture<Void> confirmed(ModuleCallback calls,
            java.util.function.Supplier<CompletableFuture<Void>> operation) {
        var result = calls.call(operation, null);
        return result == null ? CompletableFuture.failedFuture(new IllegalStateException("EventPacket runtime retired")) : result;
    }

    private void receive(EventBusCapability.BusEvent event) {
        var session = event.player() == null ? null : AuthAccess.snapshot(event.player().getUniqueId());
        realtime.offer(event.topic(), session, event.typedPayload());
    }

    private boolean signal(String name, Player player, Map<String, String> fields) {
        if (name == null || name.length() > 64) return false;
        var session = player == null ? null : AuthAccess.snapshot(player.getUniqueId());
        return realtime.offer(RuleConfiguration.SIGNAL_PREFIX + name, session, fields);
    }

    private CompletableFuture<Void> acceptAdmission(DurableRules worker, EventBusCapability.BusEvent event) {
        var fields = event.payload();
        try {
            String id = fields.get("event_id");
            UUID player = UUID.fromString(fields.getOrDefault("profile_id", ""));
            if (id == null || id.isBlank() || id.length() > 256) throw new IllegalArgumentException("Invalid admission event");
            if (event.player() != null && !event.player().getUniqueId().equals(player)) {
                throw new IllegalArgumentException("Admission player mismatch");
            }
            return worker.admit(id, player, Map.copyOf(fields));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<Void> acceptAuctionPurchase(DurableRules worker, EventBusCapability.BusEvent event) {
        try {
            var fields = event.payload();
            String listing = fields.getOrDefault("listing_id", "");
            String id = fields.get("event_id");
            UUID buyer = UUID.fromString(fields.getOrDefault("buyer_uuid", ""));
            if (!listing.matches("[1-9][0-9]{0,18}") || !("auction-expiry:" + listing).equals(id)
                || (event.player() != null && !event.player().getUniqueId().equals(buyer))) {
                throw new IllegalArgumentException("Invalid auction purchase event");
            }
            return worker.auctionPurchased(id, buyer, Map.copyOf(fields));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private boolean passes(RuleConfiguration.LiveRule rule, RealtimeRules.Snapshot event) {
        try {
            Player player = event.player() == null ? null : Bukkit.getPlayer(event.player());
            return player != null && player.isOnline() && event.sameSession(AuthAccess.snapshot(event.player()))
                && scriptConditionEvaluator.firstFailed(player, rule.checks(), event.fields()) == null;
        } catch (RuntimeException | LinkageError failure) {
            actionFailure(rule.id(), failure);
            return false;
        }
    }

    private void execute(RealtimeRules.Execution execution) {
        try {
            var event = execution.event();
            boolean quit = execution.rule().quit();
            Player player = quit || event.player() == null ? null : Bukkit.getPlayer(event.player());
            if (!quit && event.player() != null && (player == null || !player.isOnline()
                    || !event.sameSession(AuthAccess.snapshot(event.player())))) return;
            var questGps = getCapability(QuestGpsNavigable.class);
            if (player != null && questGps != null && questGps.eventRuleLocked(player, execution.rule().id())) return;
            var action = execution.action();
            String value = action.value().render(event.fields());
            if (value.isBlank() || (player == null && !quit && !action.console())) return;
            switch (action.kind()) {
                case SUBTITLE -> {
                    var subtitle = getCapability(SubtitlePlayable.class);
                    if (subtitle == null || !subtitle.playGroup(player, value)) actionFailures.incrementAndGet();
                }
                case QUEST_OFFER -> {
                    if (questGps != null) questGps.offerQuest(player, value, action.openMenu());
                    else actionFailures.incrementAndGet();
                }
                case QUEST_TRACK -> {
                    if (questGps != null) questGps.trackQuest(player, value);
                    else actionFailures.incrementAndGet();
                }
                case QUEST_ACCEPT -> {
                    if (questGps != null) questGps.acceptQuest(player, value);
                    else actionFailures.incrementAndGet();
                }
                case QUEST_OPEN -> {
                    if (questGps != null) questGps.openMenu(player);
                    else actionFailures.incrementAndGet();
                }
                case MESSAGE -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
                case COMMAND -> {
                    if (!Bukkit.dispatchCommand(action.console() ? Bukkit.getConsoleSender() : player, value)) actionFailures.incrementAndGet();
                }
                case UI_PACKET -> sendUiPacket(player, execution, value);
                case CHAT_CARD -> {
                    var chat = getCapability(ChatCardSendable.class);
                    if (chat == null || !chat.sendChatCard(player, value, renderData(action, event))) actionFailures.incrementAndGet();
                }
                case TITLE -> {
                    var titles = getCapability(TitleGrantable.class);
                    if (titles == null || !titles.giveTitle(event.player(), value, action.extra(), "EventPacket")) {
                        actionFailures.incrementAndGet();
                    }
                }
                case COMBAT_EFFECT -> {
                    var effects = getCapability(CombatEffectTriggerable.class);
                    if (effects == null || !effects.triggerPacket(value, player, renderData(action, event))) {
                        actionFailures.incrementAndGet();
                    }
                }
                case QQ_MESSAGE -> {
                    var qq = getCapability(QQBotBroadcastable.class);
                    if (qq == null) actionFailures.incrementAndGet();
                    else if (action.extra().equals("all")) qq.sendToAllGroups(value);
                    else qq.sendToGroup(Long.parseLong(action.extra()), value);
                }
                case MAIL, MAIL_PRESET -> sendLiveMail(execution, value);
            }
        } catch (RuntimeException | LinkageError failure) {
            actionFailure(execution.rule().id(), failure);
        }
    }

    private void sendUiPacket(Player player, RealtimeRules.Execution execution, String ui) {
        var action = execution.action();
        var data = renderData(action, execution.event());
        if (action.audience() == RuleConfiguration.UiAudience.SELF) {
            if (!packetBridge.sendPacket(player, ui, action.extra(), data)) actionFailures.incrementAndGet();
            return;
        }
        String rule = execution.rule().id();
        var broadcasts = uiBroadcasts;
        if (broadcasts == null || !broadcasts.enqueue(execution.event(), action.audience(), target -> {
            try {
                if (!packetBridge.sendPacket(target, ui, action.extra(), data)) actionFailures.incrementAndGet();
            } catch (RuntimeException | LinkageError failure) {
                actionFailure(rule, failure);
            }
        })) actionFailures.incrementAndGet();
    }

    private static Map<String, String> renderData(RuleConfiguration.LiveAction action, RealtimeRules.Snapshot event) {
        if (action.data().isEmpty()) return Map.of();
        var data = new LinkedHashMap<String, String>();
        action.data().forEach((key, value) -> data.put(key, value.render(event.fields())));
        return data;
    }

    private void sendLiveMail(RealtimeRules.Execution execution, String value) {
        var mail = getCapability(AxsMailService.class);
        UUID recipient = execution.event().player();
        if (mail == null || recipient == null || liveMailInFlight.get() >= 64) {
            actionFailures.incrementAndGet();
            return;
        }
        var action = execution.action();
        String key = UUID.randomUUID().toString();
        liveMailInFlight.incrementAndGet();
        try {
            var sent = action.kind() == RuleConfiguration.ActionKind.MAIL_PRESET
                ? mail.sendPresetDurable(recipient, value, "eventpacket", key)
                : mail.send(AxsMailRequest.builder(key, value).sourcePlugin("eventpacket").recipient(recipient)
                    .content(action.data().getOrDefault("content", Template.literal("")).render(execution.event().fields()))
                    .build());
            sent.whenComplete((receipt, failure) -> {
                liveMailInFlight.decrementAndGet();
                if (failure != null) actionFailure(execution.rule().id(), failure);
                else if (receipt == null || receipt.mailId() <= 0) actionFailures.incrementAndGet();
            });
        } catch (RuntimeException | LinkageError failure) {
            liveMailInFlight.decrementAndGet();
            throw failure;
        }
    }

    private void actionFailure(String rule, Throwable failure) {
        long count = actionFailures.incrementAndGet();
        if (count == 1 || count % 100 == 0) {
            logger.warning("EventPacket 规则动作失败 · " + rule + " · " + failure.getMessage());
        }
    }

    @Override protected void stopService() {
        var scope = storageScope;
        var worker = durable;
        var calls = runtimeCalls;
        storageScope = null;
        durable = null;
        runtimeCalls = null;
        if (calls != null) calls.close();
        if (worker != null) worker.close();
        configuration = null;
        cleanup(realtime::stop);
        if (uiBroadcasts != null) cleanup(uiBroadcasts::close);
        uiBroadcasts = null;
        if (localTriggers != null) cleanup(localTriggers::close);
        localTriggers = null;
        timers.forEach(task -> cleanup(task::cancel));
        timers = List.of();
        if (pollTask != null) cleanup(pollTask::cancel);
        pollTask = null;
        if (bus != null) {
            subscriptions.values().forEach(id -> cleanup(() -> bus.unsubscribe(id)));
            if (authSubscription != null) cleanup(() -> bus.unsubscribe(authSubscription));
            if (auctionSubscription != null) cleanup(() -> bus.unsubscribe(auctionSubscription));
            if (resultSubscription != null) cleanup(() -> bus.unsubscribe(resultSubscription));
        }
        subscriptions.clear();
        authSubscription = null;
        auctionSubscription = null;
        resultSubscription = null;
        var stopped = CompletableFuture.allOf(
            worker == null ? CompletableFuture.completedFuture(null) : worker.stopped().toCompletableFuture(),
            calls == null ? CompletableFuture.completedFuture(null) : calls.stopped().toCompletableFuture());
        trackShutdown(stopped);
        if (scope != null) { scope.retireAfter(stopped); trackShutdown(scope.retired()); }
    }

    private void cleanup(Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException | LinkageError failure) { logger.warning("EventPacket 清理失败: " + failure.getMessage()); }
    }

    @Override public String commandId() { return "eventpacket"; }

    @Override public List<String> actions() { return List.of("status", "topics", "fire", "cleanup"); }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arcartxsuite.admin")) {
            sender.sendMessage("没有 EventPacket 管理权限。");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
            sender.sendMessage("实时规则 " + realtime.statistics() + " · 动作失败 " + actionFailures
                + " · 持久规则 " + durable.status());
        } else if (args.length > 1 && args[1].equalsIgnoreCase("topics")) {
            sender.sendMessage("实时主题 " + configuration.topics().keySet().stream().sorted().toList()
                + " · 副本规则 " + configuration.abyss().stream().map(RuleConfiguration.MailRule::id).toList());
        } else if (args.length == 2 && args[1].equalsIgnoreCase("cleanup")) {
            requestCleanup(sender);
        } else if (args.length >= 4 && args[1].equalsIgnoreCase("fire")) {
            Player player = Bukkit.getPlayerExact(args[3]);
            if (player == null) {
                sender.sendMessage("目标玩家不在线。");
                return true;
            }
            var fields = new LinkedHashMap<String, String>();
            for (int i = 4; i < args.length; i++) {
                String[] pair = args[i].split("=", 2);
                if (pair.length == 2) fields.put(pair[0], pair[1]);
            }
            sender.sendMessage(signal(args[2], player, fields) ? "信号已入队。" : "信号未入队，检查规则、玩家准入或队列容量。");
        } else {
            sender.sendMessage("/axs eventpacket status | topics | fire <信号> <玩家> [key=value...] | cleanup");
        }
        return true;
    }

    private void requestCleanup(CommandSender sender) {
        var local = localTriggers;
        var request = local == null ? LocalTriggerRuntime.CleanupRequest.DISABLED
            : local.requestCleanup(count -> sender.sendMessage("本轮实体清理完成，共清理 " + count + " 个实体。"));
        sender.sendMessage(switch (request) {
            case QUEUED -> "实体清理已排队，将从下一 tick 开始分批执行。";
            case BUSY -> "已有实体清理排队或进行中。";
            case DISABLED -> "请在 EventPacket 的 cleanup.yml 中启用实体清理并重载。";
        });
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return sender.hasPermission("arcartxsuite.admin") && args.length == 2 ? actions() : List.of();
    }
}
