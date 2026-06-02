package xuanmo.arcartxsuite.tab.config;

/**
 * 跨服聚合模式：每个服务器在 Tab 中只占一行，不展开玩家列表。
 * 仅当 {@code cross-server: true} 时生效。
 *
 * <p>linePack 中可使用以下花括号占位符：
 * <ul>
 *   <li>{@code {server-id}} — 服务端 ID</li>
 *   <li>{@code {server-online}} — 当前服在线人数</li>
 *   <li>{@code {server-display}} — 同 server-id（自定义渲染时可被替换）</li>
 * </ul>
 *
 * <p>另外 PAPI 仍按本服第一个在线玩家上下文渲染，便于显示服务端属性。
 */
public record TabAggregateConfiguration(
    boolean enabled,
    Object linePack
) {
    public static TabAggregateConfiguration disabled() {
        return new TabAggregateConfiguration(false, null);
    }
}
