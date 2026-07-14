package xuanmo.arcartxsuite.eventpacket.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.util.TemporaryOpExecutor;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketAction;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketRecipient;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketRule;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.config.PluginConfiguration;
import xuanmo.arcartxsuite.eventpacket.storage.EventPacketRepository;

/**
 * EventPacket 规则分发服务。
 * <p>
 * 跨模块调用全部通过 capability supplier 完成；缺失能力时对应动作直接跳过。
 */
public final class EventPacketDispatchService {

    private final Logger logger;
    private final PacketGuardAPI packetGuard;
    private final PacketBridgeAPI packetBridge;
    private final Supplier<PluginConfiguration> configurationProvider;
    private final Supplier<QuestGpsNavigable> questGpsProvider;
    private final Supplier<TitleGrantable> titleProvider;
    private final Supplier<SubtitlePlayable> subtitleProvider;
    private final Supplier<ChatCardSendable> chatCardProvider;
    private final Supplier<MailDispatchable> mailProvider;
    private final Supplier<QQBotBroadcastable> qqBotProvider;
    private final Supplier<String> combatEffectUiIdProvider;
    private final Supplier<EventPacketRepository> repositoryProvider;

    private final Set<String> firedRulesCache = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    public EventPacketDispatchService(
        Logger logger,
        PacketGuardAPI packetGuard,
        PacketBridgeAPI packetBridge,
        Supplier<PluginConfiguration> configurationProvider,
        Supplier<QuestGpsNavigable> questGpsProvider,
        Supplier<TitleGrantable> titleProvider,
        Supplier<SubtitlePlayable> subtitleProvider,
        Supplier<ChatCardSendable> chatCardProvider,
        Supplier<MailDispatchable> mailProvider,
        Supplier<QQBotBroadcastable> qqBotProvider,
        Supplier<String> combatEffectUiIdProvider,
        Supplier<EventPacketRepository> repositoryProvider
    ) {
        this.logger = logger;
        this.packetGuard = packetGuard;
        this.packetBridge = packetBridge;
        this.configurationProvider = configurationProvider;
        this.questGpsProvider = questGpsProvider;
        this.titleProvider = titleProvider;
        this.subtitleProvider = subtitleProvider;
        this.chatCardProvider = chatCardProvider;
        this.mailProvider = mailProvider;
        this.qqBotProvider = qqBotProvider;
        this.combatEffectUiIdProvider = combatEffectUiIdProvider;
        this.repositoryProvider = repositoryProvider;
    }

    public void dispatchAll(EventPacketTrigger trigger, Player subject, EventPacketContext context) {
        PluginConfiguration configuration = configurationProvider.get();
        if (configuration == null || context == null) {
            return;
        }
        for (EventPacketRule rule : configuration.rules()) {
            dispatchRule(rule, trigger, subject, context);
        }
    }

    /**
     * 处理客户端回包触发。
     *
     * @param packetId  客户端发送的 packetId（如 "ArcartXEventPacket"）
     * @param presetId  预设 ID（客户端 data 中的第一个元素）
     * @param subject   发包玩家
     * @param args      客户端 data 中除预设 ID 外的附加参数
     * @return true 表示匹配到了预设规则并已分发
     */
    public boolean dispatchClientPacket(String packetId, String presetId, Player subject, List<String> args) {
        return dispatchClientPacket(packetId, presetId, subject, args, false);
    }

    /**
     * 处理客户端回包触发，支持是否绕过权限与冷却检查。
     *
     * @param packetId  客户端发送的 packetId（如 "ArcartXEventPacket"）
     * @param presetId  预设 ID（客户端 data 中的第一个元素）
     * @param subject   发包玩家
     * @param args      客户端 data 中除预设 ID 外的附加参数
     * @param bypass    是否绕过权限、冷却检查（管理命令调试用）
     * @return true 表示匹配到了预设规则并已分发
     */
    public boolean dispatchClientPacket(String packetId, String presetId, Player subject, List<String> args, boolean bypass) {
        PluginConfiguration configuration = configurationProvider.get();
        if (configuration == null || packetId == null || presetId == null || subject == null) {
            return false;
        }
        if (packetGuard != null && !packetGuard.allow(subject, "eventpacket", "client-packet", configuration.debug())) {
            return true;
        }
        EventPacketRule rule = configuration.findClientPacketRule(packetId, presetId);
        if (rule == null) {
            return false;
        }
        if (!bypass && rule.hasPermissionFilter() && !subject.hasPermission(rule.permission())) {
            return true;
        }
        List<String> effectiveArgs = args == null ? List.of() : List.copyOf(args);
        if (rule.allowArgs()) {
            if (!validateClientPacketArgs(rule, effectiveArgs)) {
                return true;
            }
        } else {
            effectiveArgs = List.of();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("preset_id", presetId);
        for (int index = 0; index < effectiveArgs.size(); index++) {
            variables.put("arg" + (index + 1), effectiveArgs.get(index));
        }
        if (bypass) {
            executeBypassClientPacket(rule, subject, variables);
        } else {
            dispatchRule(
                rule,
                EventPacketTrigger.CLIENT_PACKET,
                subject,
                EventPacketContext.fromVariables(
                    EventPacketTrigger.CLIENT_PACKET,
                    subject,
                    presetId,
                    variables
                )
            );
        }
        return true;
    }

    private void executeBypassClientPacket(EventPacketRule rule, Player subject, Map<String, String> variables) {
        EventPacketContext context = EventPacketContext.fromVariables(
            EventPacketTrigger.CLIENT_PACKET,
            subject,
            rule.signal(),
            variables
        );
        for (EventPacketAction action : rule.actions()) {
            executeAction(rule, action, subject, context);
        }
    }

    private boolean validateClientPacketArgs(EventPacketRule rule, List<String> args) {
        if (!rule.hasArgsPattern()) {
            return true;
        }
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(rule.argsPattern());
            for (String arg : args) {
                if (!pattern.matcher(arg).matches()) {
                    return false;
                }
            }
            return true;
        } catch (java.util.regex.PatternSyntaxException exception) {
            logger.warning("EventPacket 预设 " + rule.signal() + " 的参数正则表达式无效: " + rule.argsPattern());
            return false;
        }
    }

    public void dispatchSignal(String signal, Player subject, Map<String, String> variables) {
        if (signal == null || signal.isBlank()) {
            return;
        }
        dispatchAll(
            EventPacketTrigger.COMMAND_SIGNAL,
            subject,
            EventPacketContext.fromSignal(subject, signal, variables == null ? Map.of() : variables)
        );
    }

    public void dispatchRule(EventPacketRule rule, EventPacketTrigger trigger, Player subject, EventPacketContext context) {
        PluginConfiguration configuration = configurationProvider.get();
        if (configuration == null || rule == null || context == null || !rule.enabled() || rule.trigger() != trigger) {
            return;
        }
        if (trigger.signalFilterable() && !rule.signal().isBlank()) {
            Object renderedSignal = context.renderPayload("{signal}", EventPacketRecipient.SELF, subject);
            if (!rule.signal().equalsIgnoreCase(String.valueOf(renderedSignal))) {
                // 对于 Objective 事件（complete/continue/restart），signal 同时支持 quest_id 和 objective_name 过滤
                if (trigger.isObjectiveTrigger()) {
                    Object objectiveName = context.renderPayload("{objective_name}", EventPacketRecipient.SELF, subject);
                    if (!rule.signal().equalsIgnoreCase(String.valueOf(objectiveName))) {
                        return;
                    }
                } else {
                    return;
                }
            }
        }
        ScriptCondition failedCondition = ScriptConditionServices.evaluator().firstFailed(subject, rule.conditions());
        if (failedCondition != null) {
            if (configuration.debug()) {
                logger.info(
                    "EventPacket 条件未通过 -> rule=" + rule.id()
                        + " | player=" + (subject == null ? "console" : subject.getName())
                        + " | condition=" + failedCondition.raw()
                );
            }
            return;
        }
        QuestGpsNavigable questGps = questGpsProvider.get();
        if (questGps != null && subject != null && questGps.eventRuleLocked(subject, rule.id())) {
            return;
        }
        String playerId = subject == null ? "console" : subject.getUniqueId().toString();
        String stateKey = rule.id() + "|" + playerId;
        long now = System.currentTimeMillis();
        if (!rule.repeatable() && hasFired(stateKey, subject)) {
            return;
        }
        Long nextAllowedAt = cooldowns.get(stateKey);
        if (nextAllowedAt != null && nextAllowedAt > now) {
            return;
        }

        boolean anySuccess = false;
        int failedActionCount = 0;
        for (EventPacketAction action : rule.actions()) {
            boolean success = executeAction(rule, action, subject, context);
            anySuccess |= success;
            if (!success) {
                failedActionCount++;
            }
        }
        if (rule.actions().isEmpty() || anySuccess) {
            firedRulesCache.add(stateKey);
            persistFired(subject, rule.id());
            if (rule.cooldownMillis() > 0L) {
                cooldowns.put(stateKey, now + rule.cooldownMillis());
            }
        } else {
            logger.warning(
                "EventPacket 规则命中但所有动作执行失败，未记录为已触发: rule="
                    + rule.id() + " | failedActions=" + failedActionCount
            );
        }
    }

    private boolean hasFired(String stateKey, Player subject) {
        if (firedRulesCache.contains(stateKey)) {
            return true;
        }
        EventPacketRepository repository = repositoryProvider.get();
        if (repository == null || subject == null) {
            return false;
        }
        try {
            boolean fired = repository.hasFired(subject.getUniqueId(), stateKey.substring(0, stateKey.indexOf('|')));
            if (fired) {
                firedRulesCache.add(stateKey);
            }
            return fired;
        } catch (SQLException exception) {
            logger.warning("EventPacket 查询规则触发记录失败: " + exception.getMessage());
            return false;
        }
    }

    private void persistFired(Player subject, String ruleId) {
        EventPacketRepository repository = repositoryProvider.get();
        if (repository == null || subject == null) {
            return;
        }
        try {
            repository.markFired(subject.getUniqueId(), ruleId);
        } catch (SQLException exception) {
            logger.warning("EventPacket 保存规则触发记录失败: " + exception.getMessage());
        }
    }

    private boolean executeAction(EventPacketRule rule, EventPacketAction action, Player subject, EventPacketContext context) {
        if (action == null || action.type() == null || action.type().isBlank()) {
            return false;
        }
        PluginConfiguration configuration = configurationProvider.get();
        try {
            String actionType = action.type().trim().toLowerCase(Locale.ROOT);
            boolean success = switch (actionType) {
                case "questgps.offer" -> executeQuestGpsOffer(action, subject, context);
                case "questgps.accept" -> executeQuestGpsAccept(action, subject, context);
                case "questgps.open" -> executeQuestGpsOpen(subject);
                case "questgps.track" -> executeQuestGpsTrack(action, subject, context);
                case "subtitle.play" -> executeSubtitlePlay(action, subject, context);
                case "chat.card" -> executeChatCard(action, subject, context);
                case "title.give" -> executeTitleGive(action, subject, context);
                case "command.dispatch" -> executeCommandDispatch(action, subject, context);
                case "ui-packet" -> executeUiPacket(rule, action, subject, context);
                case "mail.send" -> executeMailSend(action, subject, context);
                case "qq-broadcast" -> executeQQBroadcast(action, subject, context);
                case "announcer.play" -> executeAnnouncerPlay(action, subject, context);
                case "combateffect.play" -> executeCombatEffectPlay(action, subject, context);
                default -> {
                    logger.warning("EventPacket 规则动作类型未知，已跳过: " + rule.id() + " -> " + action.type());
                    yield false;
                }
            };
            if (configuration != null && configuration.debug()) {
                logger.info(
                    "EventPacket 动作执行 -> rule=" + rule.id()
                        + " | type=" + action.type()
                        + " | player=" + (subject == null ? "console" : subject.getName())
                        + " | success=" + success
                );
            }
            return success;
        } catch (RuntimeException exception) {
            logger.warning(
                "EventPacket 规则动作执行失败: " + rule.id()
                    + " -> " + action.type()
                    + " | " + exception.getMessage()
            );
            return false;
        }
    }

    private boolean executeQuestGpsOffer(EventPacketAction action, Player subject, EventPacketContext context) {
        QuestGpsNavigable questGps = questGpsProvider.get();
        if (questGps == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String questId = renderString(action.object("quest-id"), context, subject);
        if (questId.isBlank()) {
            questId = renderString(action.object("questId"), context, subject);
        }
        if (questId.isBlank()) {
            return false;
        }
        questGps.offerQuest(subject, questId, action.bool("open-menu", true));
        return true;
    }

    private boolean executeQuestGpsAccept(EventPacketAction action, Player subject, EventPacketContext context) {
        QuestGpsNavigable questGps = questGpsProvider.get();
        if (questGps == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String questId = renderString(action.object("quest-id"), context, subject);
        if (questId.isBlank()) {
            questId = renderString(action.object("questId"), context, subject);
        }
        if (questId.isBlank()) {
            return false;
        }
        questGps.acceptQuest(subject, questId);
        return true;
    }

    private boolean executeQuestGpsOpen(Player subject) {
        QuestGpsNavigable questGps = questGpsProvider.get();
        if (questGps == null || subject == null || !subject.isOnline()) {
            return false;
        }
        questGps.openMenu(subject);
        return true;
    }

    private boolean executeQuestGpsTrack(EventPacketAction action, Player subject, EventPacketContext context) {
        QuestGpsNavigable questGps = questGpsProvider.get();
        if (questGps == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String questId = renderString(action.object("quest-id"), context, subject);
        if (questId.isBlank()) {
            questId = renderString(action.object("questId"), context, subject);
        }
        if (questId.isBlank()) {
            return false;
        }
        String taskId = renderString(action.object("task-id"), context, subject);
        if (taskId.isBlank()) {
            taskId = renderString(action.object("taskId"), context, subject);
        }
        if (taskId.isBlank()) {
            questGps.trackQuest(subject, questId);
        } else {
            questGps.trackTask(subject, questId, taskId);
        }
        return true;
    }

    private boolean executeSubtitlePlay(EventPacketAction action, Player subject, EventPacketContext context) {
        SubtitlePlayable subtitle = subtitleProvider.get();
        if (subtitle == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String groupId = renderString(action.object("group-id"), context, subject);
        if (groupId.isBlank()) {
            groupId = renderString(action.object("subtitle-id"), context, subject);
        }
        if (groupId.isBlank()) {
            groupId = renderString(action.object("id"), context, subject);
        }
        return !groupId.isBlank() && subtitle.playGroup(subject, groupId);
    }

    private boolean executeChatCard(EventPacketAction action, Player subject, EventPacketContext context) {
        ChatCardSendable chatCard = chatCardProvider.get();
        if (chatCard == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String cardId = renderString(action.object("card-id"), context, subject);
        if (cardId.isBlank()) {
            cardId = renderString(action.object("id"), context, subject);
        }
        if (cardId.isBlank()) {
            return false;
        }
        return chatCard.sendChatCard(subject, cardId, renderStringMap(action.object("data"), context, subject));
    }

    private boolean executeTitleGive(EventPacketAction action, Player subject, EventPacketContext context) {
        TitleGrantable title = titleProvider.get();
        if (title == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String titleId = renderString(action.object("title-id"), context, subject);
        if (titleId.isBlank()) {
            titleId = renderString(action.object("id"), context, subject);
        }
        if (titleId.isBlank()) {
            return false;
        }
        String duration = renderString(action.object("duration"), context, subject);
        if (duration.isBlank()) {
            duration = "permanent";
        }
        return title.giveTitle(subject.getUniqueId(), titleId, duration, "EventPacket");
    }

    private boolean executeCommandDispatch(EventPacketAction action, Player subject, EventPacketContext context) {
        String command = renderString(action.object("command"), context, subject);
        if (command.isBlank()) {
            return false;
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String executor = renderString(action.object("executor"), context, subject);
        if ("op".equalsIgnoreCase(executor) && subject != null) {
            String commandToDispatch = command;
            return TemporaryOpExecutor.execute(
                subject,
                () -> Bukkit.dispatchCommand(subject, commandToDispatch)
            );
        }
        CommandSender sender = "player".equalsIgnoreCase(executor) && subject != null
            ? subject
            : Bukkit.getConsoleSender();
        return Bukkit.dispatchCommand(sender, command);
    }

    private boolean executeUiPacket(EventPacketRule rule, EventPacketAction action, Player subject, EventPacketContext context) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return false;
        }
        String uiId = renderString(action.object("ui-id"), context, subject);
        String handler = renderString(action.object("packet-handler"), context, subject);
        if (handler.isBlank()) {
            handler = renderString(action.object("handler"), context, subject);
        }
        if (uiId.isBlank() || handler.isBlank()) {
            logger.warning("EventPacket ui-packet 动作缺少 ui-id 或 packet-handler: " + rule.id());
            return false;
        }
        boolean anySuccess = false;
        for (EventPacketRecipient recipientType : resolveRecipients(action.object("recipients"))) {
            for (Player recipient : recipientType.resolve(subject, Bukkit.getOnlinePlayers())) {
                if (!canDispatchTo(recipient, rule.trigger(), subject)) {
                    continue;
                }
                Object payload = context.renderPayload(action.object("pack"), recipientType, recipient);
                anySuccess |= packetBridge.sendPacket(recipient, uiId, handler, payload);
            }
        }
        return anySuccess;
    }

    private boolean executeMailSend(EventPacketAction action, Player subject, EventPacketContext context) {
        MailDispatchable mail = mailProvider.get();
        if (mail == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String presetId = renderString(action.object("preset-id"), context, subject);
        if (presetId.isBlank()) {
            presetId = renderString(action.object("presetId"), context, subject);
        }
        if (presetId.isBlank()) {
            return false;
        }
        return mail.dispatchPreset(presetId, subject.getName(), "EventPacket");
    }

    private boolean executeQQBroadcast(EventPacketAction action, Player subject, EventPacketContext context) {
        QQBotBroadcastable qqBot = qqBotProvider.get();
        if (qqBot == null) {
            return false;
        }
        String message = renderString(action.object("message"), context, subject);
        if (message.isBlank()) {
            return false;
        }
        long groupId = 0;
        Object rawGroupId = action.object("group-id");
        if (rawGroupId instanceof Number num) {
            groupId = num.longValue();
        } else if (rawGroupId instanceof String str) {
            try { groupId = Long.parseLong(str.trim()); } catch (NumberFormatException ignored) {}
        }
        if (groupId > 0) {
            qqBot.sendToGroup(groupId, message);
        } else {
            qqBot.sendToAllGroups(message);
        }
        return true;
    }

    private boolean executeAnnouncerPlay(EventPacketAction action, Player subject, EventPacketContext context) {
        SubtitlePlayable subtitle = subtitleProvider.get();
        if (subtitle == null || subject == null || !subject.isOnline()) {
            return false;
        }
        String groupId = renderString(action.object("group-id"), context, subject);
        if (groupId.isBlank()) {
            groupId = renderString(action.object("id"), context, subject);
        }
        if (groupId.isBlank()) {
            return false;
        }
        return subtitle.playGroup(subject, groupId);
    }

    private boolean executeCombatEffectPlay(EventPacketAction action, Player subject, EventPacketContext context) {
        if (packetBridge == null || !packetBridge.isAvailable() || subject == null || !subject.isOnline()) {
            return false;
        }
        String uiId = renderString(action.object("ui-id"), context, subject);
        if (uiId.isBlank() && combatEffectUiIdProvider != null) {
            uiId = combatEffectUiIdProvider.get();
        }
        if (uiId == null || uiId.isBlank()) {
            return false;
        }
        Object pack = context.renderPayload(action.object("pack"), EventPacketRecipient.SELF, subject);
        String handler = renderString(action.object("packet-handler"), context, subject);
        if (handler.isBlank()) {
            handler = "play";
        }
        return packetBridge.sendPacket(subject, uiId, handler, pack);
    }

    private List<EventPacketRecipient> resolveRecipients(Object rawRecipients) {
        List<EventPacketRecipient> recipients = new ArrayList<>();
        if (rawRecipients instanceof Iterable<?> iterable) {
            for (Object rawRecipient : iterable) {
                EventPacketRecipient recipient = EventPacketRecipient.parse(String.valueOf(rawRecipient));
                if (recipient != null && !recipients.contains(recipient)) {
                    recipients.add(recipient);
                }
            }
        } else if (rawRecipients != null) {
            String[] parts = String.valueOf(rawRecipients).split(",");
            for (String part : parts) {
                EventPacketRecipient recipient = EventPacketRecipient.parse(part);
                if (recipient != null && !recipients.contains(recipient)) {
                    recipients.add(recipient);
                }
            }
        }
        if (recipients.isEmpty()) {
            recipients.add(EventPacketRecipient.SELF);
        }
        return List.copyOf(recipients);
    }

    private boolean canDispatchTo(Player recipient, EventPacketTrigger trigger, Player subject) {
        if (recipient == null) {
            return false;
        }
        if (recipient.isOnline()) {
            return true;
        }
        return trigger == EventPacketTrigger.QUIT
            && subject != null
            && recipient.getUniqueId().equals(subject.getUniqueId());
    }

    private String renderString(Object rawValue, EventPacketContext context, Player subject) {
        if (rawValue == null || context == null || subject == null) {
            return "";
        }
        Object rendered = context.renderPayload(rawValue, EventPacketRecipient.SELF, subject);
        return rendered == null ? "" : String.valueOf(rendered).trim();
    }

    private Map<String, String> renderStringMap(Object rawValue, EventPacketContext context, Player subject) {
        if (rawValue == null || context == null || subject == null) {
            return Map.of();
        }
        Object rendered = context.renderPayload(rawValue, EventPacketRecipient.SELF, subject);
        if (!(rendered instanceof Map<?, ?> renderedMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : renderedMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    public void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        String playerId = player.getUniqueId().toString();
        firedRulesCache.removeIf(key -> key.endsWith("|" + playerId));
        cooldowns.keySet().removeIf(key -> key.endsWith("|" + playerId));
    }

    public void shutdown() {
        firedRulesCache.clear();
        cooldowns.clear();
    }
}
