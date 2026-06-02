package xuanmo.arcartxsuite.eventpacket.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class EventPacketContext {

    private static final DateTimeFormatter LOCAL_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 账号信息解析器钩子：由 EventPacket 模块在启动时注入，底层委托宿主统一账号识别服务。
     * 输入玩家 uuid 字符串与玩家名，返回 {@code [id, displayName, premium]} 三元组。
     */
    public interface AccountInfoResolver {
        String[] resolve(String uuid, String name);
    }

    private static volatile AccountInfoResolver accountInfoResolver;

    /** 注入账号信息解析器（传 null 清除）。 */
    public static void setAccountInfoResolver(AccountInfoResolver resolver) {
        accountInfoResolver = resolver;
    }

    private final EventPacketTrigger trigger;
    private final PlayerSnapshot subject;
    private final String placeholder;
    private final String oldValue;
    private final String newValue;
    private final BigDecimal oldNumber;
    private final BigDecimal newNumber;
    private final BigDecimal deltaNumber;
    private final String signal;
    private final Map<String, String> variables;
    private final long timestampUnix;
    private final String timestampLocal;

    private EventPacketContext(
        EventPacketTrigger trigger,
        Player subject,
        String placeholder,
        String oldValue,
        String newValue,
        BigDecimal oldNumber,
        BigDecimal newNumber,
        String signal,
        Map<String, String> variables,
        long timestampUnix,
        String timestampLocal
    ) {
        this.trigger = trigger;
        this.subject = PlayerSnapshot.capture(subject);
        this.placeholder = nullToEmpty(placeholder);
        this.oldValue = nullToEmpty(oldValue);
        this.newValue = nullToEmpty(newValue);
        this.oldNumber = oldNumber;
        this.newNumber = newNumber;
        this.deltaNumber = oldNumber == null || newNumber == null ? null : newNumber.subtract(oldNumber);
        this.signal = nullToEmpty(signal);
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
        this.timestampUnix = timestampUnix;
        this.timestampLocal = timestampLocal;
    }

    public static EventPacketContext fromSubjectTrigger(EventPacketTrigger trigger, Player subject) {
        long unix = Instant.now().getEpochSecond();
        return new EventPacketContext(
            trigger,
            subject,
            "",
            "",
            "",
            null,
            null,
            "",
            Map.of(),
            unix,
            LOCAL_TIME_FORMATTER.format(Instant.ofEpochSecond(unix))
        );
    }

    public static EventPacketContext fromPapiChange(
        EventPacketTrigger trigger,
        Player subject,
        String placeholder,
        String oldValue,
        String newValue,
        BigDecimal oldNumber,
        BigDecimal newNumber
    ) {
        long unix = Instant.now().getEpochSecond();
        return new EventPacketContext(
            trigger,
            subject,
            placeholder,
            oldValue,
            newValue,
            oldNumber,
            newNumber,
            "",
            Map.of(),
            unix,
            LOCAL_TIME_FORMATTER.format(Instant.ofEpochSecond(unix))
        );
    }

    public static EventPacketContext fromSignal(Player subject, String signal, Map<String, String> variables) {
        long unix = Instant.now().getEpochSecond();
        return new EventPacketContext(
            EventPacketTrigger.COMMAND_SIGNAL,
            subject,
            "",
            "",
            "",
            null,
            null,
            signal,
            variables,
            unix,
            LOCAL_TIME_FORMATTER.format(Instant.ofEpochSecond(unix))
        );
    }

    public static EventPacketContext fromVariables(
        EventPacketTrigger trigger,
        Player subject,
        Map<String, String> variables
    ) {
        long unix = Instant.now().getEpochSecond();
        return new EventPacketContext(
            trigger,
            subject,
            "",
            "",
            "",
            null,
            null,
            "",
            variables,
            unix,
            LOCAL_TIME_FORMATTER.format(Instant.ofEpochSecond(unix))
        );
    }

    public Object renderPayload(Object template, EventPacketRecipient recipientType, Player recipient) {
        Map<String, String> values = buildValues(recipientType, recipient);
        return renderNode(template, values);
    }

    private Map<String, String> buildValues(EventPacketRecipient recipientType, Player recipient) {
        Map<String, String> values = new LinkedHashMap<>();

        values.put("player_name", subject.name);
        values.put("player_display_name", subject.displayName);
        values.put("player_uuid", subject.uuid);
        values.put("player_world", subject.world);
        values.put("player_x", String.valueOf(subject.blockX));
        values.put("player_y", String.valueOf(subject.blockY));
        values.put("player_z", String.valueOf(subject.blockZ));
        values.put("player_health", formatDecimal(subject.health));
        values.put("player_max_health", formatDecimal(subject.maxHealth));
        values.put("player_level", String.valueOf(subject.level));
        values.put("player_ping", String.valueOf(subject.ping));

        values.put("subject_name", subject.name);
        values.put("subject_display_name", subject.displayName);
        values.put("subject_uuid", subject.uuid);
        values.put("subject_world", subject.world);
        values.put("subject_x", String.valueOf(subject.blockX));
        values.put("subject_y", String.valueOf(subject.blockY));
        values.put("subject_z", String.valueOf(subject.blockZ));
        values.put("subject_health", formatDecimal(subject.health));
        values.put("subject_max_health", formatDecimal(subject.maxHealth));
        values.put("subject_level", String.valueOf(subject.level));
        values.put("subject_ping", String.valueOf(subject.ping));

        values.put("receiver_name", recipient.getName());
        values.put("receiver_display_name", nullToEmpty(recipient.getDisplayName()));
        values.put("receiver_uuid", recipient.getUniqueId().toString());
        values.put("receiver_role", recipientType.configValue());

        values.put("trigger", trigger.configValue());
        values.put("trigger_type", trigger.configValue());
        values.put("world", subject.world);
        values.put("placeholder", placeholder);
        values.put("old_value", oldValue);
        values.put("new_value", newValue);
        values.put("old_number", formatDecimal(oldNumber));
        values.put("new_number", formatDecimal(newNumber));
        values.put("delta_number", formatDecimal(deltaNumber));
        values.put("signal", signal);
        values.put("command_signal", signal);
        String[] account = resolveAccountInfo(subject.uuid, subject.name);
        values.put("account_type", account[0]);
        values.put("account_type_display", account[1]);
        values.put("account_premium", account[2]);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            values.put(entry.getKey(), nullToEmpty(entry.getValue()));
        }
        values.put(
            "change_direction",
            trigger == EventPacketTrigger.PAPI_INCREASE
                ? "increase"
                : (trigger == EventPacketTrigger.PAPI_DECREASE ? "decrease" : "")
        );
        values.put("timestamp_unix", String.valueOf(timestampUnix));
        values.put("timestamp_local", timestampLocal);
        return values;
    }

    private static Object renderNode(Object node, Map<String, String> values) {
        if (node == null) {
            return "";
        }

        if (node instanceof String stringValue) {
            String rendered = stringValue;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return rendered.replace("\\n", "\n");
        }

        if (node instanceof List<?> listValue) {
            List<Object> rendered = new ArrayList<>(listValue.size());
            for (Object entry : listValue) {
                rendered.add(renderNode(entry, values));
            }
            return rendered;
        }

        if (node instanceof Map<?, ?> mapValue) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                Object rawKey = entry.getKey();
                String key = rawKey instanceof String stringKey
                    ? (String) renderNode(stringKey, values)
                    : String.valueOf(rawKey);
                rendered.put(key, renderNode(entry.getValue(), values));
            }
            return rendered;
        }

        return node;
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 通过钩子解析玩家账号信息，返回 {@code [id, displayName, premium]}；未注入或失败时返回空值。
     */
    private static String[] resolveAccountInfo(String uuid, String name) {
        AccountInfoResolver resolver = accountInfoResolver;
        if (resolver != null) {
            try {
                String[] info = resolver.resolve(uuid, name);
                if (info != null && info.length >= 3) {
                    return new String[]{nullToEmpty(info[0]), nullToEmpty(info[1]), nullToEmpty(info[2])};
                }
            } catch (RuntimeException ignored) {
                // 解析失败时回退为空值
            }
        }
        return new String[]{"", "", ""};
    }

    private static final class PlayerSnapshot {
        private final String name;
        private final String displayName;
        private final String uuid;
        private final String world;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final BigDecimal health;
        private final BigDecimal maxHealth;
        private final int level;
        private final int ping;

        private PlayerSnapshot(
            String name,
            String displayName,
            String uuid,
            String world,
            int blockX,
            int blockY,
            int blockZ,
            BigDecimal health,
            BigDecimal maxHealth,
            int level,
            int ping
        ) {
            this.name = name;
            this.displayName = displayName;
            this.uuid = uuid;
            this.world = world;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.health = health;
            this.maxHealth = maxHealth;
            this.level = level;
            this.ping = ping;
        }

        private static PlayerSnapshot capture(Player player) {
            Location location = player.getLocation();
            return new PlayerSnapshot(
                player.getName(),
                nullToEmpty(player.getDisplayName()),
                player.getUniqueId().toString(),
                player.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                decimal(player.getHealth()),
                decimal(resolveMaxHealth(player)),
                player.getLevel(),
                Math.max(0, player.getPing())
            );
        }

        private static double resolveMaxHealth(Player player) {
            if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                return Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
            }
            return 20.0D;
        }

        private static BigDecimal decimal(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.000001D) {
                return BigDecimal.valueOf((long) Math.rint(value));
            }
            return new BigDecimal(String.format(Locale.ROOT, "%.2f", value));
        }
    }
}
