package xuanmo.arcartxsuite.eventpacket;

import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.check;
import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.invalid;
import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.number;
import static xuanmo.arcartxsuite.eventpacket.RuleConfiguration.required;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

record LocalTriggerSettings(List<SampleSource> samples, List<KillRule> kills, Map<String, ClientRule> client,
                            Set<String> chemdahEvents, boolean quit, int sampleInterval, int samplesPerTick,
                            EntityCleanup.Settings cleanup) {
    static final String QUIT = "eventpacket.local.quit";
    static final String PACKET_ID = "AXS_EventPacketAction";
    static final Set<String> CHEMDAH_EVENTS = Set.of("quest-accept", "quest-complete", "quest-fail",
        "objective-complete", "objective-continue", "objective-restart", "level-change");

    enum Match { INCREASE, DECREASE, CHANGED, REACH, FALL_BELOW, TRUE }

    record SampleRule(String topic, Match match, double threshold) {
        boolean fires(String previous, String current) {
            if (previous == null || current == null) return false;
            if (match == Match.CHANGED) return !previous.equals(current);
            if (match == Match.TRUE) return current.equals("true") && previous.equals("false");
            try {
                double before = Double.parseDouble(previous);
                double after = Double.parseDouble(current);
                if (!Double.isFinite(before) || !Double.isFinite(after)) return false;
                return switch (match) {
                    case INCREASE -> after > before;
                    case DECREASE -> after < before;
                    case REACH -> before < threshold && after >= threshold;
                    case FALL_BELOW -> before >= threshold && after < threshold;
                    default -> false;
                };
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }

    record SampleSource(String placeholder, ScriptCondition script, List<SampleRule> rules) {}
    record KillRule(String id, String topic, EntityType type, String mythicMob, Set<String> worlds,
                    int count, boolean repeat) {}
    record ClientRule(String topic, String permission) {}

    static String chemdahTopic(String event) {
        return "eventpacket.local.chemdah." + event;
    }

    static final class Builder {
        private record Source(String placeholder, ScriptCondition script) {}

        private final ConfigurationSection config;
        private final Map<Source, List<SampleRule>> samples = new LinkedHashMap<>();
        private final List<KillRule> kills = new ArrayList<>();
        private final Map<String, ClientRule> client = new LinkedHashMap<>();
        private final java.util.Set<String> chemdah = new java.util.HashSet<>();
        private boolean quit;

        Builder(ConfigurationSection config) {
            this.config = config;
        }

        String add(String trigger, String id, ConfigurationSection rule) {
            String topic = "eventpacket.local." + id;
            return switch (trigger) {
                case "player-quit" -> {
                    quit = true;
                    yield QUIT;
                }
                case "mob-kills" -> {
                    String type = rule.getString("entity-type", "");
                    String mythic = rule.getString("mythic-mob", "");
                    check(!type.isEmpty() || !mythic.isEmpty(), id, "需要 entity-type 或 mythic-mob");
                    check(mythic.isEmpty() || mythic.matches("[A-Za-z0-9_.:-]{1,128}"), id, "无效的 MythicMobs ID");
                    EntityType entity = type.isEmpty() ? null : entityType(type, id);
                    check(entity == null || (entity.isAlive() && entity != EntityType.PLAYER), id, "需要非玩家生物类型");
                    kills.add(new KillRule(id, topic, entity, mythic, Set.copyOf(rule.getStringList("worlds")),
                        number(rule, "count", 1, 1, 1_000_000), rule.getBoolean("repeat", false)));
                    check(kills.size() <= 32, id, "最多允许 32 条击杀计数规则");
                    yield topic;
                }
                case "client-packet" -> {
                    String permission = required(rule, "permission", id);
                    check(permission.matches("[A-Za-z0-9_.-]{1,128}"), id, "无效的包触发权限");
                    client.put(id, new ClientRule(topic, permission));
                    yield topic;
                }
                case "placeholder-change" -> {
                    String placeholder = required(rule, "placeholder", id);
                    check(placeholder.matches("%[^%\\p{Cntrl}]{1,126}%"), id, "需要一个完整的 PAPI 占位符");
                    Match match = switch (required(rule, "match", id)) {
                        case "increase" -> Match.INCREASE;
                        case "decrease" -> Match.DECREASE;
                        case "changed" -> Match.CHANGED;
                        case "reach" -> Match.REACH;
                        case "fall-below" -> Match.FALL_BELOW;
                        default -> throw invalid(id, "无效的 match");
                    };
                    double threshold = rule.getDouble("threshold", Double.NaN);
                    check((match != Match.REACH && match != Match.FALL_BELOW) || Double.isFinite(threshold),
                        id, "阈值规则需要有限数值 threshold");
                    addSample(new Source(placeholder, null), new SampleRule(topic, match, threshold), id);
                    yield topic;
                }
                case "script-check" -> {
                    String body = required(rule, "script", id);
                    check(body.length() <= 2048, id, "script 不能超过 2048 字符");
                    var condition = switch (required(rule, "engine", id)) {
                        case "js" -> ScriptCondition.js(body, body);
                        case "aria" -> ScriptCondition.aria(body, body);
                        default -> throw invalid(id, "engine 仅接受 js 或 aria");
                    };
                    addSample(new Source("", condition), new SampleRule(topic, Match.TRUE, 0), id);
                    yield topic;
                }
                case "chemdah" -> {
                    String event = required(rule, "event", id);
                    check(CHEMDAH_EVENTS.contains(event), id, "未知的 Chemdah 事件 " + event);
                    chemdah.add(event);
                    yield chemdahTopic(event);
                }
                default -> throw invalid(id, "未知触发器 " + trigger);
            };
        }

        private void addSample(Source source, SampleRule rule, String id) {
            var group = samples.computeIfAbsent(source, ignored -> new ArrayList<>());
            check(samples.size() <= 32 && group.size() < 32, id, "最多 32 个采样来源，每个最多 32 条规则");
            group.add(rule);
        }

        private static EntityType entityType(String type, String id) {
            try {
                return EntityType.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException failure) {
                throw invalid(id, "未知实体类型 " + type);
            }
        }

        LocalTriggerSettings build() {
            List<SampleSource> sources = samples.entrySet().stream()
                .map(entry -> new SampleSource(entry.getKey().placeholder(), entry.getKey().script(),
                    List.copyOf(entry.getValue()))).toList();
            return new LocalTriggerSettings(sources, List.copyOf(kills), Map.copyOf(client), Set.copyOf(chemdah), quit,
                number(config, "sampling.interval-ticks", 100, 20, 12_000),
                number(config, "sampling.work-per-tick", 16, 1, 128), EntityCleanup.Settings.parse(config));
        }
    }
}
