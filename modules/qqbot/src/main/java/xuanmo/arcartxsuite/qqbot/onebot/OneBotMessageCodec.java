package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes both standard OneBot message formats without treating escaped text as segments. */
public final class OneBotMessageCodec {
    private static final Pattern CQ = Pattern.compile("\\[CQ:([a-zA-Z_]+)(?:,([^]]*))?]");
    private static final Pattern AT = Pattern.compile("(?:^|,)qq=(\\d+)(?:,|$)");
    private OneBotMessageCodec() {}

    public static String text(JsonElement message) {
        if (message == null || message.isJsonNull()) return "";
        if (message.isJsonPrimitive()) {
            Matcher matcher = CQ.matcher(message.getAsString());
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String type = matcher.group(1);
                String replacement = switch (type) {
                    case "at" -> "@" + parameter(matcher.group(2), "qq");
                    case "image" -> "[图片]";
                    case "face" -> "[表情]";
                    case "reply" -> "";
                    default -> "[" + type + "]";
                };
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);
            return unescape(result.toString());
        }
        if (!message.isJsonArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonElement element : message.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject segment = element.getAsJsonObject();
            String type = scalar(segment, "type");
            JsonObject data = segment.has("data") && segment.get("data").isJsonObject()
                ? segment.getAsJsonObject("data") : new JsonObject();
            result.append(switch (type) {
                case "text" -> scalar(data, "text"); // Array text is already decoded.
                case "at" -> "@" + scalar(data, "qq");
                case "image" -> "[图片]";
                case "face" -> "[表情]";
                case "reply" -> "";
                default -> "[" + type + "]";
            });
        }
        return result.toString();
    }

    public static List<Long> mentions(JsonElement message) {
        List<Long> result = new ArrayList<>();
        if (message == null || message.isJsonNull()) return result;
        if (message.isJsonPrimitive()) {
            Matcher cq = CQ.matcher(message.getAsString());
            while (cq.find()) {
                if (!"at".equals(cq.group(1)) || cq.group(2) == null) continue;
                Matcher qq = AT.matcher(cq.group(2));
                if (qq.find()) addMention(result, qq.group(1));
            }
        } else if (message.isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject segment = element.getAsJsonObject();
                if ("at".equals(scalar(segment, "type")) && segment.has("data") && segment.get("data").isJsonObject())
                    addMention(result, scalar(segment.getAsJsonObject("data"), "qq"));
            }
        }
        return result;
    }
    private static void addMention(List<Long> result, String value) {
        try { long qq = Long.parseLong(value); if (qq > 0 && !result.contains(qq)) result.add(qq); }
        catch (NumberFormatException ignored) {}
    }
    private static String parameter(String parameters, String key) {
        if (parameters != null) for (String pair : parameters.split(","))
            if (pair.startsWith(key + "=")) return unescape(pair.substring(key.length() + 1));
        return "";
    }
    private static String scalar(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static String unescape(String text) {
        return text.replace("&#91;", "[").replace("&#93;", "]").replace("&#44;", ",").replace("&amp;", "&");
    }
}
