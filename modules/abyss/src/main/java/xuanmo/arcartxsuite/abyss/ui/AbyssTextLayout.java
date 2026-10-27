package xuanmo.arcartxsuite.abyss.ui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

/** Wraps narrow UI text without discarding content or configured legacy formatting. */
final class AbyssTextLayout {
    private AbyssTextLayout() {}

    static List<String> wrap(String text, int halfWidthUnits) {
        if (halfWidthUnits < 2) throw new IllegalArgumentException("Text width must be at least 2");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            char ch = text.charAt(offset);
            if ((ch == '&' || ch == ChatColor.COLOR_CHAR) && offset + 1 < text.length()
                    && "0123456789abcdefklmnorx".indexOf(Character.toLowerCase(text.charAt(offset + 1))) >= 0) {
                line.append(ch).append(text.charAt(offset + 1));
                offset += 2;
                continue;
            }
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            if (point == '\r') continue;
            int advance = point <= 0x7f ? 1 : 2;
            if (point == '\n' || width + advance > halfWidthUnits) {
                String finished = line.toString();
                lines.add(finished);
                line.setLength(0);
                line.append(ChatColor.getLastColors(ChatColor.translateAlternateColorCodes('&', finished)));
                width = 0;
                if (point == '\n') continue;
            }
            line.appendCodePoint(point);
            width += advance;
        }
        lines.add(line.toString());
        return List.copyOf(lines);
    }
}
