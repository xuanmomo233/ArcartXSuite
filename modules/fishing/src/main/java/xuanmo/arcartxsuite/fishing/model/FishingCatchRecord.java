package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/**
 * 捕获记录，持久化到数据库，用于 UI 最近捕获记录流展示。
 * <p>
 * 关联模块：{@code fishing}。跨赛季保留，跨服同步。
 */
public record FishingCatchRecord(
    long id,
    @NotNull UUID eventId,
    @NotNull UUID playerUuid,
    @NotNull String fishId,
    @NotNull String fishName,
    @NotNull String rarity,
    int size,
    boolean perfect,
    long timestamp
) {
    public FishingCatchRecord(@NotNull UUID eventId, @NotNull UUID playerUuid, @NotNull String fishId,
                              @NotNull String fishName, @NotNull String rarity,
                              int size, boolean perfect, long timestamp) {
        this(0, eventId, playerUuid, fishId, fishName, rarity, size, perfect, timestamp);
    }

    public String encodeCrossServer() {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return String.join(":", "CATCH2", eventId.toString(), playerUuid.toString(),
            encoder.encodeToString(fishId.getBytes(StandardCharsets.UTF_8)),
            encoder.encodeToString(fishName.getBytes(StandardCharsets.UTF_8)),
            rarity, Integer.toString(size), perfect ? "1" : "0", Long.toString(timestamp));
    }

    public static FishingCatchRecord decodeCrossServer(String message, long now) {
        String[] fields = message.split(":", -1);
        if (fields.length != 9 || !"CATCH2".equals(fields[0])) throw new IllegalArgumentException("未知捕获消息格式");
        int size = Integer.parseInt(fields[6]);
        long timestamp = Long.parseLong(fields[8]);
        if (size < 0 || timestamp < 0 || timestamp > now + 86_400_000L
            || !("1".equals(fields[7]) || "0".equals(fields[7]))) {
            throw new IllegalArgumentException("无效捕获记录");
        }
        var decoder = Base64.getUrlDecoder();
        return new FishingCatchRecord(UUID.fromString(fields[1]), UUID.fromString(fields[2]),
            new String(decoder.decode(fields[3]), StandardCharsets.UTF_8),
            new String(decoder.decode(fields[4]), StandardCharsets.UTF_8),
            fields[5], size, "1".equals(fields[7]), timestamp);
    }
}
