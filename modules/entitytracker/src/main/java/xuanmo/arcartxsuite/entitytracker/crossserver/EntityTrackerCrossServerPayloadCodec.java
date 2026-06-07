package xuanmo.arcartxsuite.entitytracker.crossserver;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;

/**
 * 跨服 Boss 最高伤害同步 payload（Tab 分隔，首字段为类型）。
 */
public final class EntityTrackerCrossServerPayloadCodec {

    public static final String TYPE_BEST_DAMAGE = "BEST_DAMAGE";

    private EntityTrackerCrossServerPayloadCodec() {
    }

    public static String encodeBestDamage(PlayerBossBestDamage record) {
        return String.join("\t",
            TYPE_BEST_DAMAGE,
            nullToEmpty(record.getPlayerUuid()),
            nullToEmpty(record.getBossId()),
            String.valueOf(record.getBestDamage() == null ? 0 : record.getBestDamage()),
            String.valueOf(toEpochMilli(record.getDamageTime())),
            sanitize(nullToEmpty(record.getPlayerName())),
            sanitize(nullToEmpty(record.getBossDisplayName())),
            sanitize(nullToEmpty(record.getServerName())),
            sanitize(nullToEmpty(record.getWorldName())),
            record.getLocationX() == null ? "" : String.valueOf(record.getLocationX()),
            record.getLocationY() == null ? "" : String.valueOf(record.getLocationY()),
            record.getLocationZ() == null ? "" : String.valueOf(record.getLocationZ())
        );
    }

    public static PlayerBossBestDamage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("empty payload");
        }
        String[] parts = payload.split("\t", -1);
        if (parts.length < 8 || !TYPE_BEST_DAMAGE.equals(parts[0])) {
            throw new IllegalArgumentException("unsupported payload");
        }
        PlayerBossBestDamage record = new PlayerBossBestDamage();
        record.setPlayerUuid(parts[1]);
        record.setBossId(parts[2]);
        record.setBestDamage(parseInt(parts[3], 0));
        record.setDamageTime(fromEpochMilli(parseLong(parts[4], 0L)));
        record.setPlayerName(parts[5]);
        record.setBossDisplayName(parts[6]);
        record.setServerName(parts[7]);
        if (parts.length > 8) {
            record.setWorldName(parts[8]);
        }
        if (parts.length > 9 && !parts[9].isBlank()) {
            record.setLocationX(Double.parseDouble(parts[9]));
        }
        if (parts.length > 10 && !parts[10].isBlank()) {
            record.setLocationY(Double.parseDouble(parts[10]));
        }
        if (parts.length > 11 && !parts[11].isBlank()) {
            record.setLocationZ(Double.parseDouble(parts[11]));
        }
        return record;
    }

    private static String sanitize(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime fromEpochMilli(long epochMilli) {
        if (epochMilli <= 0L) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private static int parseInt(String raw, int defaultValue) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long parseLong(String raw, long defaultValue) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
