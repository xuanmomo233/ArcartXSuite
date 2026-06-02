package xuanmo.arcartxsuite.conversation.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ConversationPayloadSupport {

    private ConversationPayloadSupport() {
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
            String key = String.valueOf(entries[index]);
            Object value = entries[index + 1];
            if (value instanceof Map<?, ?> || value instanceof Iterable<?> || isArray(value)) {
                throw new IllegalArgumentException("row value must be flat: " + key);
            }
            row.put(key, value);
        }
        return row;
    }

    static boolean isFlatRowDictionary(Map<String, ?> dictionary) {
        if (dictionary == null) {
            return false;
        }
        for (Object value : dictionary.values()) {
            if (!(value instanceof Map<?, ?> row)) {
                return false;
            }
            for (Object rowValue : row.values()) {
                if (rowValue instanceof Map<?, ?> || rowValue instanceof Iterable<?> || isArray(rowValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }
}
