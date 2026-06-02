package xuanmo.arcartxsuite.questgps.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class QuestGpsPayloadSupport {

    private QuestGpsPayloadSupport() {
    }

    static String rowKey(char prefix, int index) {
        return String.format(Locale.ROOT, "%c%04d", prefix, index);
    }

    static LinkedHashMap<String, Object> flatRow(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("flatRow requires key/value pairs.");
        }
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value instanceof Map<?, ?> || value instanceof Iterable<?> || isArray(value)) {
                throw new IllegalArgumentException("row value must be flat: " + entries[index]);
            }
            row.put(String.valueOf(entries[index]), value);
        }
        return row;
    }

    private static boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }
}
