package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.List;

/** Field-level overrides: absent view text inherits the parsed native component. */
public final class ChemdahViewText {
    private ChemdahViewText() {}

    public static String choose(String override, String nativeValue, String fallback) {
        if (override != null && !override.isBlank()) return override;
        return nativeValue == null || nativeValue.isBlank() ? fallback : nativeValue;
    }

    public static List<String> choose(List<String> override, List<String> nativeValue, List<String> fallback) {
        if (override != null && !override.isEmpty()) return List.copyOf(override);
        return nativeValue == null || nativeValue.isEmpty() ? List.copyOf(fallback) : List.copyOf(nativeValue);
    }
}
