package xuanmo.arcartxsuite.chat.config;

import java.util.List;

public record ChatFunctionConfiguration(
    boolean mentionEnabled,
    boolean mentionAllEnabled,
    boolean itemEnabled,
    String itemToken,
    String itemFormat,
    String itemFailedFormat,
    List<ChatCustomComponent> customComponents
) {
}
