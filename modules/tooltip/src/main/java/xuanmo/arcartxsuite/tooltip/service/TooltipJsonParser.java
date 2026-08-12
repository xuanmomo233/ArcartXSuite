package xuanmo.arcartxsuite.tooltip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

    /**
     * 解析 JSON 数组字符串为 List<String>。
     *
     * @param json JSON 数组字符串，如 {@code ["line1","line2"]}
     * @return 字符串列表，解析失败返回 null
     */
    @Nullable
    public List<String> parseStringArray(@NotNull String json) {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<String> result = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                result.add(element.getAsString());
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
