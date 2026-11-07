package xuanmo.arcartxsuite.market.recycle;

import java.util.List;
import java.util.UUID;

public record RecyclePreferences(UUID player,boolean autoEnabled,long version,List<RecycleProtectionRule> rules) {}
