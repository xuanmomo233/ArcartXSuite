package xuanmo.arcartxsuite.map.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Filters only browse rows; map markers and authority checks keep the original snapshot. */
final class MapBrowseFilter {
    record Options(String query, boolean unlockedOnly, boolean distanceOrder) {
        static final Options DEFAULT = new Options("", false, false);
        Options {
            query = query == null ? "" : query.strip();
            if (query.length() > 64) query = query.substring(0, 64);
        }
    }

    private MapBrowseFilter() { }

    static LinkedHashMap<String, Object> apply(Map<String, Object> rows, Options options,
                                               double playerX, double playerZ, boolean sameWorld, boolean anchors) {
        String query = plain(options.query());
        var values = rows.values().stream().map(value -> (Map<?, ?>) value)
            .filter(row -> !anchors || !options.unlockedOnly() || Boolean.TRUE.equals(row.get("unlocked")))
            .filter(row -> query.isEmpty() || plain(name(row)).contains(query)
                || plain(String.valueOf(row.get("id"))).contains(query));
        Comparator<Map<?, ?>> order = Comparator.comparing(row -> plain(name(row)));
        if (options.distanceOrder() && sameWorld) {
            order = Comparator.<Map<?, ?>>comparingDouble(row -> Boolean.TRUE.equals(row.get("remote"))
                ? Double.POSITIVE_INFINITY : distance(row, playerX, playerZ)).thenComparing(order);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.sorted(order.thenComparing(row -> String.valueOf(row.get("id"))))
            .forEach(row -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                row.forEach((key, value) -> entry.put(String.valueOf(key), value));
                String label = name(row);
                entry.put("listLabel", label.codePointCount(0, label.length()) <= 18 ? label
                    : label.substring(0, label.offsetByCodePoints(0, 17)) + "…");
                result.put(Integer.toString(result.size()), entry);
            });
        return result;
    }

    private static double distance(Map<?, ?> row, double x, double z) {
        if (!(row.get("x") instanceof Number rx) || !(row.get("z") instanceof Number rz)) return Double.POSITIVE_INFINITY;
        return Math.hypot(rx.doubleValue() - x, rz.doubleValue() - z);
    }

    private static String name(Map<?, ?> row) {
        Object name = row.get("displayName");
        if (name == null) name = row.get("name");
        return name == null ? "" : name.toString();
    }

    private static String plain(String text) {
        return text.replaceAll("(?i)[§&][0-9a-fk-orx]", "").toLowerCase(Locale.ROOT);
    }
}
