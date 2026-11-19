package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;

/** Reuses configured presentation data. Permission checks and player-dependent rendering stay in the live view. */
final class MenuPresentation {
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private final BiFunction<Player, IconDefinition, String> resolveIcon;
    private final Map<IconDefinition, String> staticIcons = new HashMap<>();
    private final Map<Paragraph, List<Map<String, String>>> staticLines = new HashMap<>();

    MenuPresentation(BiFunction<Player, IconDefinition, String> resolveIcon) {
        this.resolveIcon = resolveIcon;
    }

    String icon(Player player, IconDefinition icon) {
        if (icon == null || !icon.hasIcon()) return "";
        String cached = staticIcons.get(icon);
        if (cached != null) return cached;
        String rendered = resolveIcon.apply(player, icon);
        if (rendered != null && !rendered.isBlank() && staticIcon(icon)) staticIcons.put(icon, rendered);
        return rendered;
    }

    private static boolean staticIcon(IconDefinition icon) {
        if (notBlank(icon.json())) return true;
        if (notBlank(icon.mmoType()) && notBlank(icon.mmoId())) return false;
        String source = icon.source() == null ? "" : icon.source().trim().toLowerCase(Locale.ROOT);
        if (!source.isEmpty() && !source.equals("minecraft") && !source.equals("plain") && !source.equals("vanilla")) return false;
        if (MenuConditionEvaluator.hasPlaceholders(icon.name())) return false;
        if (icon.lore() != null) {
            for (String line : icon.lore()) if (MenuConditionEvaluator.hasPlaceholders(line)) return false;
        }
        return true;
    }

    static boolean staticButton(MenuButtonDefinition button) {
        var details = button.details();
        return button.permission().isBlank() && button.viewConditions().isEmpty() && button.useConditions().isEmpty()
            && (button.icon() == null || !button.icon().hasIcon() || staticIcon(button.icon()))
            && !MenuConditionEvaluator.hasPlaceholders(button.text())
            && !MenuConditionEvaluator.hasPlaceholders(details.summary())
            && !MenuConditionEvaluator.hasPlaceholders(details.category())
            && !MenuConditionEvaluator.hasPlaceholders(details.actionHint())
            && details.description().stream().noneMatch(MenuConditionEvaluator::hasPlaceholders)
            && details.requirements().stream().noneMatch(MenuConditionEvaluator::hasPlaceholders);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    void appendLines(Player player, Map<String, Object> rows, List<String> paragraphs, String color) {
        for (String paragraph : paragraphs) {
            if (MenuConditionEvaluator.hasPlaceholders(paragraph)) {
                wrap(MenuConditionEvaluator.applyPlaceholders(player, paragraph), color,
                    line -> rows.put(MenuPacketFactory.rowKey(rows.size()), line));
            } else {
                var prepared = staticLines.computeIfAbsent(new Paragraph(paragraph == null ? "" : paragraph, color), key -> {
                    var lines = new ArrayList<Map<String, String>>();
                    wrap(key.text(), key.color(), lines::add);
                    return List.copyOf(lines);
                });
                for (var line : prepared) rows.put(MenuPacketFactory.rowKey(rows.size()), line);
            }
        }
    }

    private static void wrap(String input, String color, Consumer<Map<String, String>> lineOutput) {
        for (String line : LINE_BREAK.split(input, -1)) {
            String text = ChatColor.translateAlternateColorCodes('&', color + line);
            StringBuilder wrapped = new StringBuilder();
            int width = 0;
            for (int offset = 0; offset < text.length();) {
                int cp = text.codePointAt(offset);
                offset += Character.charCount(cp);
                if (cp == ChatColor.COLOR_CHAR && offset < text.length()) {
                    wrapped.appendCodePoint(cp).append(text.charAt(offset++));
                    continue;
                }
                int units = cp > 255 ? 2 : 1;
                if (width + units > 44) {
                    String completed = wrapped.toString();
                    lineOutput.accept(Map.of("text", completed));
                    wrapped = new StringBuilder(ChatColor.getLastColors(completed));
                    width = 0;
                }
                wrapped.appendCodePoint(cp);
                width += units;
            }
            lineOutput.accept(Map.of("text", wrapped.toString()));
        }
    }

    void clear() {
        staticIcons.clear();
        staticLines.clear();
    }

    private record Paragraph(String text, String color) {}
}
