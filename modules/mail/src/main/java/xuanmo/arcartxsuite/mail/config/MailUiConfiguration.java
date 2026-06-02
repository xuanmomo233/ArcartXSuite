package xuanmo.arcartxsuite.mail.config;

public record MailUiConfiguration(
    String inboxUiId,
    String composeUiId,
    String logsUiId,
    String adminUiId,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles,
    String composeInventoryTitle,
    String notifyCardId,
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
