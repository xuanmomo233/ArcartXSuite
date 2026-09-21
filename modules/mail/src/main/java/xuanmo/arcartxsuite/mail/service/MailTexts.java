package xuanmo.arcartxsuite.mail.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import xuanmo.arcartxsuite.api.command.CommandSanitizer;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.mail.model.MailAttachment;

/**
 * 邮件模块纯静态工具集：文本度量/折行、货币与数值解析、附件复制、PDC 读取等无状态辅助方法。
 * <p>自 {@link MailService} 提取，仅含无私有字段依赖的 static 方法。
 */
final class MailTexts {

    private MailTexts() {
    }


    static String crop(String rawValue, int maxLength) {
        String value = rawValue == null ? "" : rawValue.trim();
        int limit = Math.max(1, maxLength);
        return value.length() <= limit ? value : value.substring(0, limit);
    }


    static int measureTextWidth(String text, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                i++;
                continue;
            }
            width += (c >= '\u2E80') ? charWidthFull : charWidthHalf;
        }
        return width;
    }


    static List<String> wrapText(String text, int maxLineWidth, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;
        String lastColor = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                lastColor = String.valueOf(c) + text.charAt(i + 1);
                currentLine.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            int cw = (c >= '\u2E80') ? charWidthFull : charWidthHalf;
            if (currentWidth + cw > maxLineWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(lastColor);
                currentWidth = 0;
            }
            currentLine.append(c);
            currentWidth += cw;
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }


    static String readPdcValueByKey(org.bukkit.persistence.PersistentDataContainer pdc, NamespacedKey key) {
        String stringValue = pdc.get(key, PersistentDataType.STRING);
        if (stringValue != null) return stringValue;
        Integer integerValue = pdc.get(key, PersistentDataType.INTEGER);
        if (integerValue != null) return String.valueOf(integerValue);
        Long longValue = pdc.get(key, PersistentDataType.LONG);
        if (longValue != null) return String.valueOf(longValue);
        Byte byteValue = pdc.get(key, PersistentDataType.BYTE);
        if (byteValue != null) return String.valueOf(byteValue);
        Double doubleValue = pdc.get(key, PersistentDataType.DOUBLE);
        return doubleValue == null ? null : String.valueOf(doubleValue);
    }


    static boolean valuesEqual(String expected, String actual) {
        // 尝试数值比较
        try {
            java.math.BigDecimal expectedNum = new java.math.BigDecimal(expected.trim());
            java.math.BigDecimal actualNum = new java.math.BigDecimal(actual.trim());
            return expectedNum.compareTo(actualNum) == 0;
        } catch (NumberFormatException ignored) {
            // 非数值，走字符串比较
        }
        return expected.trim().equalsIgnoreCase(actual.trim());
    }


    static BigDecimal normalizeAmount(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO)).setScale(4, RoundingMode.DOWN);
    }


    static BigDecimal parseCurrencyAmount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.DOWN);
        }
        try {
            return normalizeAmount(new BigDecimal(rawValue.trim().replace(",", "")));
        } catch (NumberFormatException exception) {
            return null;
        }
    }


    static String normalizeCurrencyId(String currencyId) {
        return currencyId == null || currencyId.isBlank() ? "money" : currencyId.trim().toLowerCase(Locale.ROOT);
    }


    static String safe(String value) {
        return value == null ? "" : value;
    }


    static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }


    /**
     * 将 ArcartX Backpack slot id 转换为 Bukkit {@link PlayerInventory} slot index。
     * <p>ArcartX UI 的 Backpack 槽位 id 映射与原版 Bukkit 不同：
     * <ul>
     *   <li>ArcartX 9~35 = 背包主区 → Bukkit 9~35（一致）</li>
     *   <li>ArcartX 36~44 = 快捷栏   → Bukkit 0~8（需转换）</li>
     * </ul>
     *
     * @param arcartxSlot ArcartX UI Backpack 槽位 id（9~44）
     * @return Bukkit PlayerInventory slot index，无效范围返回 -1
     */
    static int arcartxSlotToBukkit(int arcartxSlot) {
        if (arcartxSlot >= 9 && arcartxSlot <= 35) return arcartxSlot;
        if (arcartxSlot >= 36 && arcartxSlot <= 44) return arcartxSlot - 36;
        return -1;
    }


    static boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir() && itemStack.getAmount() > 0;
    }


    static List<MailAttachment> copyAttachments(List<MailAttachment> attachments) {
        return attachments == null ? List.of() : List.copyOf(attachments);
    }


    static List<String> copyStrings(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }


    static List<ScriptCondition> copyConditions(List<ScriptCondition> conditions) {
        return conditions == null ? List.of() : List.copyOf(conditions);
    }


    static String trimDecimal(BigDecimal amount) {
        return normalizeAmount(amount).stripTrailingZeros().toPlainString();
    }


    static String replacePlayerTokens(String command, Player player) {
        String safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
        return safe(command)
            .replace("<player>", safeName)
            .replace("{player}", safeName)
            .replace("%player%", safeName);
    }
}
