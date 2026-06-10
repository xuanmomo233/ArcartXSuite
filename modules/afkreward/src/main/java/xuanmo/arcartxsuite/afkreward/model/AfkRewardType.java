package xuanmo.arcartxsuite.afkreward.model;

import java.util.List;
import java.util.Map;

public record AfkRewardType(
    String name,
    String describe,
    Map<String, List<String>> tierCommands,
    List<String> mailPresets
) {}
