package xuanmo.arcartxsuite.eventpacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

record Template(List<Part> parts, Set<String> fields) {
    private static final int MAX_RENDERED_LENGTH = 4096;
    private static final Pattern FIELD = Pattern.compile("\\{([a-z][a-z0-9_]{0,63})}");
    record Part(String text, boolean field) {}

    static Template literal(String text) {
        return new Template(List.of(new Part(text, false)), Set.of());
    }

    static Template compile(String text) {
        var match = FIELD.matcher(text);
        var parts = new ArrayList<Part>();
        var fields = new HashSet<String>();
        int end = 0;
        while (match.find()) {
            if (end < match.start()) parts.add(new Part(text.substring(end, match.start()), false));
            fields.add(match.group(1));
            parts.add(new Part(match.group(1), true));
            end = match.end();
        }
        if (end == 0) return literal(text);
        if (end < text.length()) parts.add(new Part(text.substring(end), false));
        return new Template(List.copyOf(parts), Set.copyOf(fields));
    }

    String render(Map<String, String> values) {
        if (fields.isEmpty()) return parts.get(0).text();
        var text = new StringBuilder();
        for (Part part : parts) {
            String value = part.field() ? values.getOrDefault(part.text(), "") : part.text();
            int remaining = MAX_RENDERED_LENGTH - text.length();
            if (remaining == 0) break;
            int end = Math.min(remaining, value.length());
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            text.append(value, 0, end);
            if (end < value.length()) break;
        }
        return text.toString();
    }
}
