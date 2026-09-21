package xuanmo.arcartxsuite.chat.model;

import java.util.List;

/**
 * 预设红包定义，从 {@code chat/redpackets/} 目录的 yml 文件加载。
 * <p>
 * 预设红包由管理员命令发放，奖励池支持货币、物品与指令奖励的随机抽取。
 *
 * @param id            预设唯一 ID
 * @param enabled       是否启用
 * @param displayName   显示名称
 * @param wish          祝福语（显示在卡片上）
 * @param count         份数（可被命令行参数覆盖）
 * @param replacement   是否有放回抽取（true=奖项可重复中，受 max-claims 限制）
 * @param expireMinutes 过期时长（分钟），0/负数表示使用全局配置
 * @param rewards       奖励池条目列表
 */
public record RedPacketPreset(
    String id,
    boolean enabled,
    String displayName,
    String wish,
    int count,
    boolean replacement,
    long expireMinutes,
    List<RedPacketPoolEntry> rewards
) {
}
