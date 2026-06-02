package xuanmo.arcartxsuite.chat.config;

public record ChatCardConfiguration(
    String mentionCardId,
    String privateCardId,
    String systemCardId,
    String itemPreviewCardId,
    int charWidthFull,
    int charWidthHalf,
    int lineHeight,
    int maxLineWidth,
    int textOffsetX,
    int padRight,
    int baseHeight,
    int minWidth
) {
}
