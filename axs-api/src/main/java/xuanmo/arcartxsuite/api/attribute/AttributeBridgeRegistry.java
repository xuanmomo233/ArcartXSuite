package xuanmo.arcartxsuite.api.attribute;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 统一属性桥接注册表。
 * <p>
 * 宿主维护单例实例，涵盖所有已对接的属性插件（AttributePlus、CraneAttribute、MythicLib、Symphony）。
 * 模块通过 {@code context.attributeBridge()} 获取，不再各自创建反射桥接。
 * <p>
 * 每个子桥接独立初始化，对应插件未安装时 {@code available()} 返回 false，调用为空操作。
 */
@ApiStability.Stable
public interface AttributeBridgeRegistry {

    /** AttributePlus 桥接 */
    AttributePlusBridge attributePlus();

    /** CraneAttribute 桥接 */
    CraneAttributeBridge craneAttribute();

    /** MythicLib 桥接 */
    MythicLibBridge mythicLib();

    /** Symphony 桥接 */
    SymphonyBridge symphony();
}
