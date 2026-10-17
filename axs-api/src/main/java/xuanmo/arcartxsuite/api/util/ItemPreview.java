package xuanmo.arcartxsuite.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.inventory.ItemStack;

/** 客户端虚拟图标使用单件副本，真实数量由 UI 独立文本展示。 */
public final class ItemPreview {
    private static final Pattern COUNT_VALUE = Pattern.compile("[+-]?\\d+([bBsSlL]?)");

    private ItemPreview() {
    }

    public static ItemStack copyForIcon(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    /** 兼容原始 JSON/SNBT 图标；只改顶层 Count/count，不触碰嵌套容器与模组数据。 */
    public static String singleCount(String json) {
        if (json == null || json.isBlank()) return json;
        int depth = 0;
        int keyStart = 0;
        char quote = 0;
        boolean escaped = false;
        StringBuilder result = new StringBuilder();
        int copied = 0;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '{' || ch == '[') {
                depth++;
                if (depth == 1) keyStart = i + 1;
            } else if (ch == '}' || ch == ']') {
                depth--;
            } else if (ch == ',' && depth == 1) {
                keyStart = i + 1;
            } else if (ch == ':' && depth == 1) {
                String key = json.substring(keyStart, i).trim();
                if (key.length() >= 2 && (key.charAt(0) == '"' || key.charAt(0) == '\'')) {
                    key = key.substring(1, key.length() - 1);
                }
                if (!key.equals("Count") && !key.equals("count")) continue;
                int start = i + 1;
                while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
                Matcher matcher = COUNT_VALUE.matcher(json).region(start, json.length());
                if (matcher.lookingAt()) {
                    int end = matcher.end();
                    int next = end;
                    while (next < json.length() && Character.isWhitespace(json.charAt(next))) next++;
                    if (next < json.length() && (json.charAt(next) == ',' || json.charAt(next) == '}')) {
                        result.append(json, copied, start).append('1').append(matcher.group(1));
                        copied = end;
                    }
                }
            }
        }
        return copied == 0 ? json : result.append(json, copied, json.length()).toString();
    }
}
