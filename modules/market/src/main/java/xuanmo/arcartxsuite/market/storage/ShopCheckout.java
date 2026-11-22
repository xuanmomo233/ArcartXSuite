package xuanmo.arcartxsuite.market.storage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Stock and quota parameters frozen when a system shop order is accepted. */
public record ShopCheckout(String shop, String item, int quantity, String stockMode, int stockMaximum,
                           int limit, String resetPeriod) {
    public record Product(String item, String stockMode, int stockMaximum, int limit) { }

    public record Availability(int stock, int limit) { }

    public record Reservation(boolean started) { }

    public long nextLimitReset(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate boundary = switch (resetPeriod.toLowerCase(Locale.ROOT)) {
            case "daily" -> today.plusDays(1);
            case "weekly" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            case "monthly" -> today.plusMonths(1).withDayOfMonth(1);
            default -> null;
        };
        return boundary == null ? 0 : boundary.atStartOfDay(now.getZone()).toInstant().toEpochMilli();
    }
}
