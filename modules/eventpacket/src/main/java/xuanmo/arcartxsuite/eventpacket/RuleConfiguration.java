package xuanmo.arcartxsuite.eventpacket;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.command.CommandSanitizer;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

record RuleConfiguration(Map<String, List<LiveRule>> topics, List<TimerRule> timers,
                         List<MailRule> abyss, List<MailRule> firstAdmission, List<MailRule> auctionPurchased,
                         int queueCapacity, int workPerTick, long nanosPerTick) {
    static final String FIRST_ADMITTED = "axs.auth.profile_first_admitted";
    static final String AUCTION_PURCHASED = EventBusCapability.MARKET_AUCTION_PURCHASED;
    static final String SIGNAL_PREFIX = "eventpacket.signal.";
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern FIELD = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern TOPIC = Pattern.compile("axs(?:\\.[a-z][a-z0-9_-]*){2,8}");
    private static final Set<String> ABYSS_FIELDS = Set.of("result_id", "run_id", "owner", "dungeon", "plan",
        "difficulty", "outcome", "member_state", "deaths");
    private static final Set<String> AUTH_FIELDS = Set.of("event_id", "profile_id", "account_id", "provider",
        "realm_id", "connection_id");
    private static final Set<String> AUCTION_FIELDS = Set.of("event_id", "listing_id", "item", "price", "formatted_price",
        "currency", "tax", "seller_uuid", "buyer_uuid", "buyer");


    enum ActionKind { SUBTITLE, QUEST_OFFER, QUEST_TRACK, MESSAGE, COMMAND }

    record LiveAction(ActionKind kind, Template value, boolean openMenu, boolean console) {}
    record LiveRule(String id, Map<String, String> conditions, List<LiveAction> actions) {}
    record TimerRule(String id, ScheduleSpec spec) {}
    record MailAction(String id, Template title, Template content, String currency, double amount) {}
    record MailRule(String id, String owner, Long startSequence, Map<String, String> conditions,
                    List<MailAction> actions) {}

    static RuleConfiguration load(File file) throws Exception {
        var path = file.toPath();
        check(Files.size(path) <= 131_072, "config.yml", "配置文件不能超过 128 KiB");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(131_072);
        Object raw = new Yaml(new SafeConstructor(options)).load(text);
        check(raw == null || raw instanceof Map<?, ?>, "config.yml", "需要配置映射");
        var config = new YamlConfiguration();
        if (raw instanceof Map<?, ?> mapping) copySection(config, mapping);
        return parse(config);
    }

    private static void copySection(ConfigurationSection section, Map<?, ?> mapping) {
        for (var entry : mapping.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.contains(".")) {
                throw invalid(section.getCurrentPath(), "无效配置键 " + entry.getKey());
            }
            if (entry.getValue() instanceof Map<?, ?> children) {
                copySection(section.createSection(key), children);
            } else {
                section.set(key, entry.getValue());
            }
        }
    }

    static RuleConfiguration parse(ConfigurationSection config) {
        var topics = new HashMap<String, List<LiveRule>>();
        var timers = new ArrayList<TimerRule>();
        var abyss = new ArrayList<MailRule>();
        var auth = new ArrayList<MailRule>();
        var auction = new ArrayList<MailRule>();
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (config.contains("rules") && rules == null) throw invalid("rules", "需要映射");
        if (rules != null) {
            if (rules.getKeys(false).size() > 256) throw invalid("rules", "最多 256 条");
            for (String id : rules.getKeys(false)) {
                check(ID.matcher(id).matches(), id, "规则 ID 无效");
                var rule = rules.getConfigurationSection(id);
                check(rule != null, id, "需要规则映射");
                if (!rule.getBoolean("enabled", true)) continue;
                String trigger = required(rule, "trigger", id);
                var conditions = conditions(rule, id);
                var actions = rule.getMapList("actions");
                check(rule.getList("actions") != null && rule.getList("actions").size() == actions.size()
                    && !actions.isEmpty() && actions.size() <= 8, id, "需要 1 到 8 个动作映射");
                switch (trigger) {
                    case "abyss-result" -> {
                        check(ABYSS_FIELDS.containsAll(conditions.keySet()), id, "副本条件包含未知字段");
                        check(conditions.containsKey("dungeon"), id, "副本邮件规则必须限定 when.dungeon");
                        String owner = rule.getString("owner", "abyss");
                        check(owner.matches("[a-z0-9_.:-]{1,96}"), id, "owner 无效");
                        Long start = rule.contains("start-sequence") ? rule.getLong("start-sequence", -1) : null;
                        check(start == null || start >= 0, id, "start-sequence 必须是非负整数");
                        abyss.add(new MailRule(id, owner, start, conditions, mailActions(actions, ABYSS_FIELDS, id)));
                    }
                    case "first-admitted" -> {
                        check(AUTH_FIELDS.containsAll(conditions.keySet()), id, "首次准入条件包含未知字段");
                        auth.add(new MailRule(id, "", null, conditions, mailActions(actions, AUTH_FIELDS, id)));
                    }
                    case "auction-purchased" -> {
                        check(AUCTION_FIELDS.containsAll(conditions.keySet()), id, "拍卖成交条件包含未知字段");
                        auction.add(new MailRule(id, "", null, conditions, mailActions(actions, AUCTION_FIELDS, id)));
                    }
                    case "event-bus", "signal", "schedule" -> {
                        String topic;
                        if (trigger.equals("event-bus")) {
                            topic = required(rule, "topic", id);
                            check(TOPIC.matcher(topic).matches() && !topic.equals(FIRST_ADMITTED)
                                && !topic.equals(AbyssRunResultAccess.RESULT_COMMITTED_TOPIC), id, "无效的实时主题");
                        } else if (trigger.equals("signal")) {
                            String signal = required(rule, "signal", id);
                            check(ID.matcher(signal).matches(), id, "信号名无效");
                            topic = SIGNAL_PREFIX + signal;
                        } else {
                            var schedule = rule.getConfigurationSection("schedule");
                            check(schedule != null, id, "缺少 schedule");
                            check(conditions.isEmpty(), id, "定时规则没有事件字段");
                            check(!rule.getBoolean("leader-only"), id, "定时规则仅在本节点运行");
                            timers.add(new TimerRule(id, ScheduleSpec.load(schedule)));
                            topic = timerTopic(id);
                        }
                        var group = topics.computeIfAbsent(topic, ignored -> new ArrayList<>());
                        check(group.size() < 32, id, "每个主题最多 32 条规则");
                        group.add(new LiveRule(id, conditions, liveActions(actions, trigger.equals("schedule"), id)));
                    }
                    default -> throw invalid(id, "未知触发器 " + trigger);
                }
            }
        }
        topics.replaceAll((topic, group) -> List.copyOf(group));
        return new RuleConfiguration(Map.copyOf(topics), List.copyOf(timers), List.copyOf(abyss), List.copyOf(auth), List.copyOf(auction),
            number(config, "runtime.queue-capacity", 1024, 16, 8192),
            number(config, "runtime.work-per-tick", 64, 1, 512),
            number(config, "runtime.budget-micros", 750, 100, 5000) * 1000L);
    }

    static String timerTopic(String id) {
        return "eventpacket.schedule." + id;
    }

    private static Map<String, String> conditions(ConfigurationSection rule, String id) {
        var when = rule.getConfigurationSection("when");
        check(when != null || !rule.contains("when"), id, "when 需要映射");
        if (when == null) return Map.of();
        var result = new HashMap<String, String>();
        for (var entry : when.getValues(false).entrySet()) {
            Object value = entry.getValue();
            check(FIELD.matcher(entry.getKey()).matches() && (value instanceof String
                || value instanceof Number || value instanceof Boolean), id, "无效的匹配字段 " + entry.getKey());
            result.put(entry.getKey(), String.valueOf(value));
        }
        return Map.copyOf(result);
    }

    private static List<LiveAction> liveActions(List<Map<?, ?>> actions, boolean timer, String id) {
        var result = new ArrayList<LiveAction>();
        for (var action : actions) {
            var type = value(action, "type", id);
            LiveAction parsed = switch (type) {
                case "subtitle" -> new LiveAction(ActionKind.SUBTITLE, template(action, "group", id), false, false);
                case "quest.offer" -> new LiveAction(ActionKind.QUEST_OFFER, template(action, "quest", id),
                    !Boolean.FALSE.equals(action.get("open-menu")), false);
                case "quest.track" -> new LiveAction(ActionKind.QUEST_TRACK, template(action, "quest", id), false, false);
                case "message" -> new LiveAction(ActionKind.MESSAGE, template(action, "text", id), false, false);
                case "command" -> command(action, id);
                default -> throw invalid(id, "未知实时动作 " + type);
            };
            check(!timer || parsed.console(), id, "定时规则需要控制台命令动作");
            result.add(parsed);
        }
        return List.copyOf(result);
    }

    private static LiveAction command(Map<?, ?> action, String id) {
        String command = CommandSanitizer.validateExpandedCommand(value(action, "command", id));
        check(!command.contains("{") && !command.contains("}"), id, "命令必须使用固定参数");
        String executor = value(action, "executor", id);
        check(executor.equals("console") || executor.equals("player"), id, "executor 仅接受 console / player");
        return new LiveAction(ActionKind.COMMAND, Template.literal(command.startsWith("/") ? command.substring(1) : command),
            false, executor.equals("console"));
    }

    private static List<MailAction> mailActions(List<Map<?, ?>> actions, Set<String> fields, String rule) {
        var result = new ArrayList<MailAction>();
        var ids = new java.util.HashSet<String>();
        for (var action : actions) {
            check(value(action, "type", rule).equals("mail"), rule, "持久规则只接受 mail 动作");
            String id = value(action, "id", rule);
            check(ID.matcher(id).matches() && ids.add(id), rule, "动作 ID 无效或重复 " + id);
            Template title = template(action, "title", rule);
            Template content = action.containsKey("content") ? template(action, "content", rule) : Template.literal("");
            check(fields.containsAll(title.fields()) && fields.containsAll(content.fields()), rule, "邮件模板包含未知字段");
            String currency = action.containsKey("currency") ? value(action, "currency", rule) : "";
            Object amount = action.get("amount");
            double number = amount instanceof Number n ? n.doubleValue() : 0;
            check(currency.isEmpty() ? amount == null : currency.matches("[a-zA-Z0-9_.:-]{1,64}")
                && Double.isFinite(number) && number > 0, rule, "货币及金额无效");
            result.add(new MailAction(id, title, content, currency, number));
        }
        return List.copyOf(result);
    }

    private static Template template(Map<?, ?> action, String field, String id) {
        return Template.compile(value(action, field, id));
    }

    private static String value(Map<?, ?> action, String field, String id) {
        Object value = action.get(field);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 4096) {
            throw invalid(id, "无效动作字段 " + field);
        }
        return text;
    }

    private static String required(ConfigurationSection section, String field, String id) {
        if (!section.isString(field) || section.getString(field, "").isBlank()) throw invalid(id, "缺少 " + field);
        return section.getString(field);
    }

    private static int number(ConfigurationSection section, String field, int fallback, int min, int max) {
        int value = section.getInt(field, fallback);
        check((!section.contains(field) || section.isInt(field)) && value >= min && value <= max, field,
            "需要 " + min + " 到 " + max + " 的整数");
        return value;
    }

    private static void check(boolean pass, String id, String message) {
        if (!pass) throw invalid(id, message);
    }

    private static IllegalArgumentException invalid(String id, String message) {
        return new IllegalArgumentException(id + " · " + message);
    }
}
