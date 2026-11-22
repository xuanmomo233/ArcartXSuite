package xuanmo.arcartxsuite.eventpacket;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

public final class EventPacketModule extends AbstractAXSModule implements ModuleCommandHandler {
    private final RealtimeRules realtime = new RealtimeRules();
    private final Map<String, String> subscriptions = new HashMap<>();
    private volatile RuleConfiguration configuration;
    private EventBusCapability bus;
    private DurableRules durable;
    private ModuleStorage storageScope;
    private ModuleCallback runtimeCalls;
    private File configurationFile;
    private ScheduledTask pollTask;
    private List<ScheduledTask> timers = List.of();
    private String authSubscription;
    private String auctionSubscription;
    private String resultSubscription;
    private long actionFailures;

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("eventpacket").name("EventPacket").version("2.0.0")
            .mainClass(getClass().getName()).build();
    }

    @Override protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder().configFileName("ArcartXEventPacket.yml").currentVersion(3)
            .syncPolicy(SyncPolicy.builder().dynamicSection("rules").build()).build();
    }

    @Override protected void loadConfiguration(@Nullable File file) throws Exception {
        if (file == null) throw new IllegalStateException("EventPacket 配置缺失");
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
            () -> configuration, logger);
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
        } catch (RuntimeException | LinkageError failure) {
            added.values().forEach(bus::unsubscribe);
            newTimers.forEach(ScheduledTask::cancel);
            throw failure;
        }
        configuration = replacement;
        realtime.install(replacement, task -> scheduler.scheduleAtFixedRate(plugin, task, 1L, 1L), this::execute);
        subscriptions.putAll(added);
        subscriptions.entrySet().removeIf(entry -> {
            if (replacement.topics().containsKey(entry.getKey())) return false;
            bus.unsubscribe(entry.getValue());
            return true;
        });
        timers.forEach(ScheduledTask::cancel);
        timers = List.copyOf(newTimers);
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

    private void execute(RealtimeRules.Execution execution) {
        try {
            var event = execution.event();
            Player player = event.player() == null ? null : Bukkit.getPlayer(event.player());
            if (event.player() != null && (player == null || !player.isOnline()
                    || !event.sameSession(AuthAccess.snapshot(event.player())))) return;
            var questGps = getCapability(QuestGpsNavigable.class);
            if (player != null && questGps != null && questGps.eventRuleLocked(player, execution.rule().id())) return;
            var action = execution.action();
            String value = action.value().render(event.fields());
            if (value.isBlank() || (player == null && !action.console())) return;
            switch (action.kind()) {
                case SUBTITLE -> {
                    var subtitle = getCapability(SubtitlePlayable.class);
                    if (subtitle == null || !subtitle.playGroup(player, value)) actionFailures++;
                }
                case QUEST_OFFER -> {
                    if (questGps != null) questGps.offerQuest(player, value, action.openMenu());
                    else actionFailures++;
                }
                case QUEST_TRACK -> {
                    if (questGps != null) questGps.trackQuest(player, value);
                    else actionFailures++;
                }
                case MESSAGE -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
                case COMMAND -> {
                    if (!Bukkit.dispatchCommand(action.console() ? Bukkit.getConsoleSender() : player, value)) actionFailures++;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            if (++actionFailures == 1 || actionFailures % 100 == 0) {
                logger.warning("EventPacket 规则动作失败 · " + execution.rule().id() + " · " + failure.getMessage());
            }
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

    @Override public List<String> actions() { return List.of("status", "topics", "fire"); }

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
            sender.sendMessage("/axs eventpacket status | topics | fire <信号> <玩家> [key=value...]");
        }
        return true;
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return sender.hasPermission("arcartxsuite.admin") && args.length == 2 ? actions() : List.of();
    }
}
