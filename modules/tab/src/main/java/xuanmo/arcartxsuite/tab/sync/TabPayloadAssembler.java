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
        if (packTemplate instanceof Map<?, ?>) {
            return new LinkedHashMap<String, Object>();
        }
        return new ArrayList<>(Math.max(0, expectedPlayerCount));
    }

    @SuppressWarnings("unchecked")
    static void append(Object payload, Object packTemplate, Object rendered, boolean omitBlankValues) {
        if (packTemplate instanceof Map<?, ?>) {
            mergeMapPayload((Map<String, Object>) payload, rendered, omitBlankValues);
            return;
        }

        List<Object> listPayload = (List<Object>) payload;
        if (packTemplate instanceof List<?>) {
            mergeListPayload(listPayload, rendered, omitBlankValues);
            return;
        }

        if (omitBlankValues && isBlankValue(rendered)) {
            return;
        }
        listPayload.add(rendered);
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

    private static void mergeListPayload(List<Object> payload, Object rendered, boolean omitBlankValues) {
        if (!(rendered instanceof List<?> renderedList)) {
            if (!omitBlankValues || !isBlankValue(rendered)) {
                payload.add(rendered);
            }
            return;
        }

        for (Object entry : renderedList) {
            if (omitBlankValues && isBlankValue(entry)) {
                continue;
            }
            payload.add(entry);
        }
    }

    private static void mergeMapPayload(Map<String, Object> payload, Object rendered, boolean omitBlankValues) {
        if (!(rendered instanceof Map<?, ?> renderedMap)) {
            if (!omitBlankValues || !isBlankValue(rendered)) {
                payload.put("value", rendered);
            }
            return;
        }

        for (Map.Entry<?, ?> entry : renderedMap.entrySet()) {
            Object value = entry.getValue();
            if (omitBlankValues && isBlankValue(value)) {
                continue;
            }
            payload.put(String.valueOf(entry.getKey()), value);
        }
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
