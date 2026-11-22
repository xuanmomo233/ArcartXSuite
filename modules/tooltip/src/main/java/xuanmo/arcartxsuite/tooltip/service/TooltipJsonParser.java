package xuanmo.arcartxsuite.tooltip.service;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tooltip JSON 解析工具。
 * <p>
 * 解析客户端 mod 发来的 JSON 格式 tooltip 数据。
 */
public final class TooltipJsonParser {
    private static final int MAX_TEXT_LINES = 64;
    private static final int MAX_OBJECT_DEPTH = 16;

    /**
     * 解析 JSON 数组字符串为 List<String>。
     *
     * @param json JSON 数组字符串，如 {@code ["line1","line2"]}
     * @return 字符串列表，解析失败返回 null
     */
    @Nullable
    public List<String> parseStringArray(@NotNull String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginArray();
            List<String> result = new ArrayList<>();
            while (reader.hasNext()) {
                if (result.size() == MAX_TEXT_LINES || reader.peek() != JsonToken.STRING) return null;
                result.add(reader.nextString());
            }
            reader.endArray();
            return reader.peek() == JsonToken.END_DOCUMENT ? result : null;
        } catch (IOException | IllegalStateException exception) {
            return null;
        }
    }

    /** Checks the client object without materializing a JSON tree. */
    public boolean isStructuredObject(@NotNull String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) return false;
            reader.beginObject();
            int depth = 1;
            while (depth > 0) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT -> {
                        if (++depth > MAX_OBJECT_DEPTH) return false;
                        reader.beginObject();
                    }
                    case BEGIN_ARRAY -> {
                        if (++depth > MAX_OBJECT_DEPTH) return false;
                        reader.beginArray();
                    }
                    case END_OBJECT -> {
                        reader.endObject();
                        depth--;
                    }
                    case END_ARRAY -> {
                        reader.endArray();
                        depth--;
                    }
                    case NAME -> reader.nextName();
                    case STRING, NUMBER, BOOLEAN, NULL -> reader.skipValue();
                    default -> { return false; }
                }
            }
            return reader.peek() == JsonToken.END_DOCUMENT;
        } catch (IOException | IllegalStateException failure) {
            return false;
        }
    }
}
