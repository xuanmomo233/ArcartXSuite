package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 游戏内事件播报到 QQ 群的配置（击杀播报等）。
 */
public record QQBotBroadcastConfig(
    boolean killEnabled,
    String killFormat,
    boolean bossOnly,
    List<String> bossKeywords,
    boolean playerKillOnly,
    boolean deathEnabled,
    String deathFormat
) {
    /** 判断给定实体名是否属于 Boss（基于关键词包含匹配） */
    public boolean isBoss(String entityName) {
        if (entityName == null) return false;
        String lower = entityName.toLowerCase();
        for (String kw : bossKeywords) {
            if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
