package xuanmo.arcartxsuite.title.config;

import java.util.List;

public record TitleDisplayConfiguration(
    List<String> groups,
    String separator,
    String emptyText
) {
    public TitleDisplayConfiguration {
        groups = groups == null ? List.of() : List.copyOf(groups);
        separator = separator == null ? " " : separator;
        emptyText = emptyText == null ? "" : emptyText;
    }
}
