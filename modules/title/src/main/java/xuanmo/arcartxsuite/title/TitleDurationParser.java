package xuanmo.arcartxsuite.title;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 称号时长解析器。
 * <p>
 * 将命令参数中的时长文本解析为 {@link TitleDurationSpec}，
 * 支持永久（permanent）、时长（如 7d、12h）和日期范围（如 2024-01-01~2024-12-31）三种格式。
 * 属于 title 模块工具层。
 */
public final class TitleDurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})~(\\d{4}-\\d{2}-\\d{2})$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TitleDurationParser() {
    }

    /**
     * 解析时长文本。
     *
     * @param input 时长文本，支持 permanent / 数字+单位 / 日期范围
     * @return 解析结果，无法识别时返回空 Optional
     */
    public static Optional<TitleDurationSpec> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return Optional.empty();
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if ("permanent".equals(normalized)) {
            return Optional.of(TitleDurationSpec.ofPermanent());
        }

        Matcher dateRangeMatcher = DATE_RANGE_PATTERN.matcher(trimmed);
        if (dateRangeMatcher.matches()) {
            return parseDateRange(dateRangeMatcher.group(1), dateRangeMatcher.group(2));
        }

        Matcher matcher = DURATION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (amount <= 0L) {
            return Optional.empty();
        }

        Duration duration = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> null;
        };
        return duration == null ? Optional.empty() : Optional.of(TitleDurationSpec.ofTemporary(duration));
    }

    private static Optional<TitleDurationSpec> parseDateRange(String startStr, String endStr) {
        try {
            LocalDate startDate = LocalDate.parse(startStr, DATE_FORMAT);
            LocalDate endDate = LocalDate.parse(endStr, DATE_FORMAT);
            if (!endDate.isAfter(startDate)) {
                return Optional.empty();
            }
            ZoneId zone = ZoneId.systemDefault();
            Instant activatesAt = startDate.atStartOfDay(zone).toInstant();
            Instant expiresAt = endDate.plusDays(1).atStartOfDay(zone).toInstant();
            return Optional.of(TitleDurationSpec.ofDateRange(activatesAt, expiresAt));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    /**
     * 称号时长规格。
     * <p>
     * 封装解析后的时长信息，区分永久、临时时长与日期范围三种模式。
     *
     * @param permanent   是否永久
     * @param duration    临时时长，永久或日期范围模式下为 {@link Duration#ZERO}
     * @param activatesAt 激活时间，仅日期范围模式非空
     * @param expiresAt   过期时间，仅日期范围模式非空
     */
    public record TitleDurationSpec(boolean permanent, Duration duration, Instant activatesAt, Instant expiresAt) {
        /**
         * @return 永久时长规格
         */
        public static TitleDurationSpec ofPermanent() {
            return new TitleDurationSpec(true, Duration.ZERO, null, null);
        }

        /**
         * @param duration 临时时长
         * @return 临时时长规格
         */
        public static TitleDurationSpec ofTemporary(Duration duration) {
            return new TitleDurationSpec(false, duration == null ? Duration.ZERO : duration, null, null);
        }

        /**
         * @param activatesAt 激活时间
         * @param expiresAt   过期时间
         * @return 日期范围规格
         */
        public static TitleDurationSpec ofDateRange(Instant activatesAt, Instant expiresAt) {
            return new TitleDurationSpec(false, Duration.ZERO, activatesAt, expiresAt);
        }

        /**
         * @return 是否为日期范围模式
         */
        public boolean isDateRange() {
            return activatesAt != null && expiresAt != null;
        }
    }
}
