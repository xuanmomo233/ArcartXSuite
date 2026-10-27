package xuanmo.arcartxsuite.abyss.ui;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbyssTextLayoutTest {
    private static String plain(String value) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }

    @Test void wrapsChineseWithoutLosingTextOrColor() {
        String text = "&a" + "远征消息".repeat(20);
        var lines = AbyssTextLayout.wrap(text, 40);
        assertEquals(4, lines.size());
        assertTrue(lines.stream().allMatch(line -> plain(line).length() == 20));
        assertEquals(plain(text), plain(String.join("", lines)));
        assertTrue(lines.get(1).startsWith("§a"));
    }

    @Test void keepsPlayerNamesAndMixedTextWithinWidth() {
        String text = "&6Player1234567890 &7: &f" + "消息".repeat(24);
        var lines = AbyssTextLayout.wrap(text, 40);
        for (var line : lines) assertTrue(plain(line).codePoints().map(c -> c <= 127 ? 1 : 2).sum() <= 40);
        assertEquals(plain(text), plain(String.join("", lines)));
    }

    @Test void keepsExplicitLineBreaksAndWholeUnicodeCharacters() {
        var lines = AbyssTextLayout.wrap("&b你好😀\n继续", 4);
        assertEquals("你好😀继续", plain(String.join("", lines)));
        assertEquals(3, lines.size());
        assertTrue(lines.stream().noneMatch(line -> line.endsWith("\uD83D")));
    }

    @Test void handlesEmptyTextAndRejectsInvalidWidth() {
        assertEquals(java.util.List.of(""), AbyssTextLayout.wrap("", 40));
        assertThrows(IllegalArgumentException.class, () -> AbyssTextLayout.wrap("text", 1));
    }

    @Test void keepsHexColorAcrossWrappedLines() {
        var lines = AbyssTextLayout.wrap("&x&1&2&3&4&5&6" + "远征".repeat(10), 8);
        assertTrue(lines.get(1).startsWith("§x§1§2§3§4§5§6"));
    }
}
