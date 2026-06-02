package xuanmo.arcartxsuite.title.service;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;

public final class TitleTextFormats {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private TitleTextFormats() {
    }

    public static String formatRemaining(PlayerOwnedTitle ownedTitle, Instant now) {
        if (ownedTitle == null) {
            return "未拥有";
        }
        if (ownedTitle.expiresAt() == null) {
            return "永久";
        }

        long totalSeconds = Math.max(0L, ownedTitle.remainingMillis(now) / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0L) {
            parts.add(days + "天");
        }
        if (hours > 0L) {
            parts.add(hours + "小时");
        }
        if (minutes > 0L) {
            parts.add(minutes + "分");
        }
        if (parts.isEmpty() || (days == 0L && hours == 0L && minutes == 0L)) {
            parts.add(seconds + "秒");
        }
        return String.join("", parts);
    }

    public static String formatAttributes(Map<String, Double> attributes) {
        return formatAttributes(attributes, toSourceLines(attributes, List.of()));
    }

    public static String formatAttributes(Map<String, Double> attributes, List<String> sourceLines) {
        List<String> lines = formatAttributesAsList(attributes, sourceLines);
        return lines.isEmpty() ? "-" : String.join("\n", lines);
    }

    /**
     * 与 {@link #formatAttributes(Map, List)} 相同的输入，但返回逐行列表，便于 UI 直接渲染多行。
     * 同名同类（可解析为 "属性名:数值" 形式）的行会被累加合并；不可解析的复杂行（如百分比、区间）按原样保留并去重。
     */
    public static List<String> formatAttributesAsList(Map<String, Double> attributes, List<String> sourceLines) {
        boolean hasLegacyAttributes = attributes != null && !attributes.isEmpty();
        boolean hasSourceLines = sourceLines != null && !sourceLines.isEmpty();
        if (!hasLegacyAttributes && !hasSourceLines) {
            return List.of();
        }

        // 收集所有行：从 Map 转出的部分 + 原生 sourceLines 中的非 Map 部分
        List<String> rawLines = new ArrayList<>();
        if (hasLegacyAttributes) {
            for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                rawLines.add(entry.getKey() + ": " + formatSignedNumber(entry.getValue()));
            }
        }
        if (hasSourceLines) {
            int nativeStart = hasLegacyAttributes ? Math.min(attributes.size(), sourceLines.size()) : 0;
            rawLines.addAll(sourceLines.subList(nativeStart, sourceLines.size()));
        }

        // 合并：可解析的同名累加；不可解析的保留（去重）
        // 注：以 strip 行首颜色码后的属性名为合并键；首次出现的颜色码保留作为最终前缀
        LinkedHashMap<String, Double> mergedNumeric = new LinkedHashMap<>();
        LinkedHashMap<String, String> mergedNumericSuffix = new LinkedHashMap<>();
        LinkedHashMap<String, String> mergedNumericColor = new LinkedHashMap<>();
        List<String> rawKept = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            ParsedAttributeLine parsed = parseAttributeLine(line);
            if (parsed == null) {
                if (!rawKept.contains(line)) {
                    rawKept.add(line);
                }
                continue;
            }
            mergedNumeric.merge(parsed.name, parsed.value, Double::sum);
            mergedNumericSuffix.putIfAbsent(parsed.name, parsed.suffix);
            mergedNumericColor.putIfAbsent(parsed.name, parsed.colorPrefix);
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : mergedNumeric.entrySet()) {
            String suffix = mergedNumericSuffix.getOrDefault(entry.getKey(), "");
            String colorPrefix = mergedNumericColor.getOrDefault(entry.getKey(), "");
            result.add(colorPrefix + entry.getKey() + ": " + formatSignedNumber(entry.getValue()) + suffix);
        }
        result.addAll(rawKept);
        return result;
    }

    private static final Pattern ATTRIBUTE_LINE_PATTERN = Pattern.compile(
        "^\\s*([^:：]+?)\\s*[:：]\\s*([+\\-]?\\d+(?:\\.\\d+)?)\\s*(.*)$"
    );
    // 行首连续的颜色码序列，如 "&4"、"&l&4"、"§a"
    private static final Pattern LEADING_COLOR_CODE_PATTERN = Pattern.compile(
        "^((?:[&§][0-9a-fk-orA-FK-OR])+)"
    );

    private static ParsedAttributeLine parseAttributeLine(String line) {
        if (line == null) {
            return null;
        }
        // 先剥离行首颜色码
        String colorPrefix = "";
        String body = line;
        Matcher colorMatcher = LEADING_COLOR_CODE_PATTERN.matcher(line);
        if (colorMatcher.find()) {
            colorPrefix = colorMatcher.group(1);
            body = line.substring(colorPrefix.length());
        }
        Matcher matcher = ATTRIBUTE_LINE_PATTERN.matcher(body);
        if (!matcher.matches()) {
            return null;
        }
        String name = matcher.group(1).trim();
        if (name.isEmpty()) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(matcher.group(2));
        } catch (NumberFormatException exception) {
            return null;
        }
        String suffix = matcher.group(3) == null ? "" : matcher.group(3).trim();
        // 复杂尾巴（如范围、单位）不参与合并
        if (!suffix.isEmpty() && !suffix.equals("(%)") && !suffix.equals("%")) {
            return null;
        }
        return new ParsedAttributeLine(colorPrefix, name, value, suffix.isEmpty() ? "" : suffix);
    }

    private record ParsedAttributeLine(String colorPrefix, String name, double value, String suffix) {
    }

    public static List<String> toSourceLines(Map<String, Double> attributes, List<String> attributePlusLines) {
        List<String> lines = new ArrayList<>();
        if (attributes == null || attributes.isEmpty()) {
            if (attributePlusLines == null || attributePlusLines.isEmpty()) {
                return List.of();
            }
        } else {
            for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                lines.add(entry.getKey() + ":" + formatNumber(entry.getValue()));
            }
        }
        if (attributePlusLines != null && !attributePlusLines.isEmpty()) {
            lines.addAll(attributePlusLines);
        }
        return lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    public static String formatNumber(double value) {
        return NUMBER_FORMAT.format(value);
    }

    private static String formatSignedNumber(double value) {
        if (value > 0.0D) {
            return "+" + formatNumber(value);
        }
        if (value < 0.0D) {
            return "-" + formatNumber(Math.abs(value));
        }
        return "0";
    }
}
