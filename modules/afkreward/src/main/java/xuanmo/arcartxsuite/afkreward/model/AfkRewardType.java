package xuanmo.arcartxsuite.afkreward.model;

import java.util.List;
import java.util.Map;

/**
 * 挂机奖励类型定义，描述某一类挂机奖励的发放规则。
 * <p>
 * 关联模块：afkreward。每个 {@code AfkArea} 通过 {@code reward.type()} 引用一个奖励类型，
 * 服务层在结算时根据档位（tier）执行对应命令或发送预设邮件。
 *
 * @param name             奖励类型名称（与区域配置中 {@code reward.type} 对应）
 * @param describe         奖励类型描述文本，用于展示
 * @param tierCommands     档位到命令列表的映射，key 为档位标识、value 为该档位触发时执行的命令
 * @param mailPresets      预设邮件标识列表，用于背包满时通过邮件补发奖励
 * @param fullInventoryMailPresets 背包满时使用的兜底邮件预设列表
 */
public record AfkRewardType(
    String name,
    String describe,
    Map<String, List<String>> tierCommands,
    List<String> mailPresets,
    List<String> fullInventoryMailPresets
) {}
