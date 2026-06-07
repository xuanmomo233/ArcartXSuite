package xuanmo.arcartxsuite.tab.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TabPayloadAssembler {

    private TabPayloadAssembler() {
    }

    static Object create(Object packTemplate, int expectedPlayerCount) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("entries", new LinkedHashMap<String, Object>());
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    static void append(Object payload, Object packTemplate, Object rendered, boolean omitBlankValues) {
        Map<String, Object> wrapper = (Map<String, Object>) payload;
        Map<String, Object> entries = (Map<String, Object>) wrapper.get("entries");

        if (packTemplate instanceof Map<?, ?>) {
            if (rendered instanceof Map<?, ?> renderedMap) {
                entries.put(Integer.toString(entries.size()), renderedMap);
            } else {
                if (!omitBlankValues || !isBlankValue(rendered)) {
                    entries.put(Integer.toString(entries.size()), rendered);
                }
            }
            return;
        }

        if (packTemplate instanceof List<?>) {
            if (rendered instanceof List<?> renderedList) {
                for (Object entry : renderedList) {
                    if (omitBlankValues && isBlankValue(entry)) {
                        continue;
                    }
                    entries.put(Integer.toString(entries.size()), entry);
                }
                return;
            }
        }

        if (omitBlankValues && isBlankValue(rendered)) {
            return;
        }
        entries.put(Integer.toString(entries.size()), rendered);
    }

    static Object snapshot(Object payload) {
        if (payload instanceof List<?> listPayload) {
            List<Object> copy = new ArrayList<>(listPayload.size());
            for (Object entry : listPayload) {
                copy.add(snapshot(entry));
            }
            return List.copyOf(copy);
        }

        if (payload instanceof Map<?, ?> mapPayload) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapPayload.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), snapshot(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }

        return payload;
    }

    static boolean structurallyEquals(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() != rightList.size()) {
                return false;
            }
            for (int index = 0; index < leftList.size(); index++) {
                if (!structurallyEquals(leftList.get(index), rightList.get(index))) {
                    return false;
                }
            }
            return true;
        }

        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (leftMap.size() != rightMap.size()) {
                return false;
            }

            Iterator<? extends Map.Entry<?, ?>> leftIterator = leftMap.entrySet().iterator();
            Iterator<? extends Map.Entry<?, ?>> rightIterator = rightMap.entrySet().iterator();
            while (leftIterator.hasNext() && rightIterator.hasNext()) {
                Map.Entry<?, ?> leftEntry = leftIterator.next();
                Map.Entry<?, ?> rightEntry = rightIterator.next();
                if (!Objects.equals(leftEntry.getKey(), rightEntry.getKey())) {
                    return false;
                }
                if (!structurallyEquals(leftEntry.getValue(), rightEntry.getValue())) {
                    return false;
                }
            }
            return !leftIterator.hasNext() && !rightIterator.hasNext();
        }

        return Objects.equals(left, right);
    }

    private static boolean isBlankValue(Object value) {
        if (value instanceof String stringValue) {
            return stringValue.isBlank();
        }

        if (value instanceof List<?> listValue) {
            if (listValue.isEmpty()) {
                return true;
            }
            for (Object entry : listValue) {
                if (!isBlankValue(entry)) {
                    return false;
                }
            }
            return true;
        }

        if (value instanceof Map<?, ?> mapValue) {
            if (mapValue.isEmpty()) {
                return true;
            }
            for (Object entry : mapValue.values()) {
                if (!isBlankValue(entry)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }
}
