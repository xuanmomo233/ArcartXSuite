package xuanmo.arcartxsuite.warehouse.service;

import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 仓库模块纯静态工具集：数值解析、字符串规整、物品描述/lore、包摘要、SHA-256、拼音搜索 token 等无状态辅助方法。
 * <p>自 {@link WarehouseService} 提取，仅含无私有字段依赖的方法。
 */
final class WarehouseTexts {

    private WarehouseTexts() {
    }

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }


    /**
     * 提取物品展示用 lore 文本：与 {@link #loreLines} 相同的 k!→§ 转换，
     * 行间以 {@code \n} 连接；无 lore 返回空串。供存入时冗余写入 lore_text 列。
     */
    static String loreText(ItemStack itemStack) {
        ItemMeta meta = itemStack == null ? null : itemStack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : meta.getLore()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line == null ? "" : line.replace("k!", "§"));
        }
        return builder.toString();
    }

    /** 已存储 lore_text 还原为展示行列表；空串返回默认占位提示。 */
    static List<String> loreLinesFromStored(String loreText) {
        if (loreText == null || loreText.isEmpty()) {
            return List.of("&f这个物品没有额外描述。");
        }
        return List.of(loreText.split("\n", -1));
    }


    static List<String> loreLines(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of("&f这个物品没有额外描述。");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
            return List.of("&f这个物品没有额外描述。");
        }
        List<String> result = new ArrayList<>();
        for (String line : meta.getLore()) {
            result.add(line == null ? "" : line.replace("k!", "§"));
        }
        return List.copyOf(result);
    }


    /** 将秒数格式化为人类可读的期限文本，如 "7天"、"30天"、"1小时"。 */
    static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "—";
        }
        if (seconds % 86400 == 0) {
            return (seconds / 86400) + "天";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "小时";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "分钟";
        }
        return seconds + "秒";
    }


    static String summarizeStoragePacket(Map<String, Object> packet) {
        Map<?, ?> slots = mapValue(packet.get("slots"));
        Map<?, ?> backpack = mapValue(packet.get("backpack"));
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " page=" + packet.get("page")
            + "/" + packet.get("pageTotal")
            + " pageText=" + packet.get("pageText")
            + " matchedSlots=" + packet.get("matchedSlots")
            + " selectedSlot=" + packet.get("selectedSlot")
            + " slots=" + slots.size()
            + " nonEmptySlots=" + countRows(slots, false)
            + " slotItemJson=" + countNonBlankField(slots, "itemJson")
            + " backpack=" + backpack.size()
            + " nonEmptyBackpack=" + countRows(backpack, false)
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }


    static String summarizeManagePacket(Map<String, Object> packet) {
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " categories=" + mapValue(packet.get("categories")).size()
            + " personalWarehouses=" + mapValue(packet.get("personalWarehouses")).size()
            + " sharedWarehouses=" + mapValue(packet.get("sharedWarehouses")).size()
            + " balances=" + mapValue(packet.get("balances")).size()
            + " products=" + mapValue(packet.get("products")).size()
            + " fixedDeposits=" + mapValue(packet.get("fixedDeposits")).size()
            + " sharedMembers=" + mapValue(packet.get("sharedMembers")).size()
            + " searchResults=" + mapValue(packet.get("searchResults")).size()
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }

    static Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    static long countRows(Map<?, ?> rows, boolean emptyValue) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && Boolean.valueOf(emptyValue).equals(map.get("empty")))
            .count();
    }


    static long countNonBlankField(Map<?, ?> rows, String field) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && !safe(String.valueOf(map.get(field))).isBlank() && !"null".equals(String.valueOf(map.get(field))))
            .count();
    }


    static String describeStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.getType().isAir()) {
            return stack.getType().name() + " x" + stack.getAmount();
        }
        ItemMeta meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stack.getType().name()
            + " x" + stack.getAmount()
            + " max=" + stack.getMaxStackSize()
            + " hasMeta=" + (meta != null)
            + (name.isBlank() ? "" : " name=" + name);
    }


    static String shortHash(String hash) {
        String safeHash = safe(hash);
        return safeHash.length() <= 12 ? safeHash : safeHash.substring(0, 12);
    }


    static BigDecimal parseDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }


    static int parseInt(String rawValue, int defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).intValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }


    static long parseLong(String rawValue, long defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).longValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }


    static WarehouseService.SearchTokens toSearchTokens(String rawValue) {
        String stripped = ChatColor.stripColor(safe(rawValue));
        String normalized = normalizeToken(stripped);
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) {
                continue;
            }
            String[] values;
            try {
                values = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                values = null;
            }
            if (values != null && values.length > 0) {
                // 多音字：遍历所有读音，同一字的多读音连续拼接
                // 字之间用空格分隔，支持跨词搜索（如 "zs jian" 匹配 "钻石剑"）
                StringBuilder charPinyin = new StringBuilder();
                StringBuilder charInitials = new StringBuilder();
                for (String value : values) {
                    String candidate = value.replaceAll("[^a-z0-9]", "");
                    if (!candidate.isBlank()) {
                        charPinyin.append(candidate);
                        charInitials.append(candidate.charAt(0));
                    }
                }
                if (charPinyin.length() > 0) {
                    if (pinyin.length() > 0) pinyin.append(' ');
                    if (initials.length() > 0) initials.append(' ');
                    pinyin.append(charPinyin);
                    initials.append(charInitials);
                    continue;
                }
            }
            if (Character.isLetterOrDigit(character)) {
                if (pinyin.length() > 0) pinyin.append(' ');
                if (initials.length() > 0) initials.append(' ');
                pinyin.append(Character.toLowerCase(character));
                initials.append(Character.toLowerCase(character));
            }
        }
        return new WarehouseService.SearchTokens(normalized, pinyin.toString(), initials.toString());
    }


    static String sha256Hex(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(Character.forDigit((current >> 4) & 0xF, 16));
                builder.append(Character.forDigit(current & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to build warehouse item fingerprint.", exception);
        }
    }


    static String value(List<String> data, int index, String defaultValue) {
        return data != null && data.size() > index ? safe(data.get(index)) : defaultValue;
    }


    static String safe(String value) {
        return value == null ? "" : value.trim();
    }


    static String normalizeId(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }


    static String normalizeToken(String value) {
        String stripped = ChatColor.stripColor(safe(value));
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT).replace(" ", "");
    }


    static String crop(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }


    /** 字节数格式化为可读大小（如 "3.5 MB"），用于物品超限提示。 */
    static String formatByteSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }
}
