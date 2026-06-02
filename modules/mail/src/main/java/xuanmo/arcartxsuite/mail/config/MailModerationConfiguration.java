package xuanmo.arcartxsuite.mail.config;

import java.util.List;
import java.util.regex.Pattern;

public record MailModerationConfiguration(
    List<String> blockedWords,
    List<Pattern> blockedPatterns,
    List<String> blockedMaterials,
    List<Pattern> blockedLorePatterns
) {
}
