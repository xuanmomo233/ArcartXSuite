package xuanmo.arcartxsuite.prop.config;

/**
 * 单个按键绑定的定义模型。
 * <p>
 * 描述一个客户端按键与道具槽位的映射关系，由 {@link PropKeyMappingConfiguration} 从 {@code key.yml} 解析。
 *
 * @param bindingId 绑定唯一标识
 * @param defaultKey 默认按键（如 {@code "X"}）
 * @param slotId    关联的道具槽位标识
 */
public record PropKeyBindingDefinition(
    String bindingId,
    String defaultKey,
    String slotId
) {
}
