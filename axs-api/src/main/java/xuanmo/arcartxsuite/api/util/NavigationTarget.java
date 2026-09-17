package xuanmo.arcartxsuite.api.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 导航目标描述（{@link NavigationTargetService} 的输入单元，不可变）。
 * <p>
 * 一个目标对应客户端一个路标（waypoint）。同一玩家可登记多个目标并行追踪，
 * 但只有"最新激活且同世界"的主目标实际下发路标与路径标记（见 {@link NavigationTargetService}）；
 * 目标位置可以是静态坐标，也可以由 {@link #resolver()} 动态解析（NPC / 任务系统等）。
 * <p>
 * 到达语义：目标被注册后持续导航直到显式取消；{@link #finishRange()} &gt; 0 时
 * 进入半径会触发一次 {@link #finishCommands()} 与到达回调，但不会移除导航。
 * 滞回设计：触发后须离开 {@code finishRange + rearmBuffer} 才会重新武装，
 * 防止玩家在半径边界抖动导致命令反复执行。
 *
 * @since 1.6.0
 */
public final class NavigationTarget {

    private final String navId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final String title;
    private final String titleTemplate;
    private final String styleId;
    private final double finishRange;
    private final List<String> finishCommands;
    private final double rearmBuffer;
    private final Function<Player, Location> resolver;
    private final Map<String, String> placeholders;
    private final Object attachment;

    private NavigationTarget(Builder builder) {
        this.navId = builder.navId;
        this.world = builder.world;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.title = builder.title;
        this.titleTemplate = builder.titleTemplate;
        this.styleId = builder.styleId;
        this.finishRange = builder.finishRange;
        this.finishCommands = List.copyOf(builder.finishCommands);
        this.rearmBuffer = builder.rearmBuffer;
        this.resolver = builder.resolver;
        this.placeholders = Map.copyOf(builder.placeholders);
        this.attachment = builder.attachment;
    }

    /** @return 目标唯一标识（同一玩家内唯一，直接用作客户端路标 id） */
    public String navId() {
        return navId;
    }

    /** @return 静态目标世界名；动态目标可为空（由 resolver 决定） */
    public String world() {
        return world;
    }

    /** @return 静态目标 X 坐标 */
    public double x() {
        return x;
    }

    /** @return 静态目标 Y 坐标 */
    public double y() {
        return y;
    }

    /** @return 静态目标 Z 坐标 */
    public double z() {
        return z;
    }

    /** @return 显示标题（未配置 titleTemplate 时直接使用；同时作为 {@code %label%} 占位符值） */
    public String title() {
        return title;
    }

    /**
     * 标题模板，支持 {@code %key%} 占位符（内置 {@code %distance%}/{@code %world%}/{@code %x%}/{@code %y%}/{@code %z%}/{@code %label%}
     * 与 {@link #placeholders()} 中的自定义键）。为空时使用 {@link #title()} 原样显示。
     *
     * @return 标题模板
     */
    public String titleTemplate() {
        return titleTemplate;
    }

    /** @return ArcartX 路标样式 id */
    public String styleId() {
        return styleId;
    }

    /**
     * 到达触发半径（格）。&lt;= 0 表示不启用到达判定；&gt; 0 时进入半径触发一次
     * {@link #finishCommands()} 与到达回调（导航本身保持，直到显式取消）。
     *
     * @return 到达半径
     */
    public double finishRange() {
        return finishRange;
    }

    /**
     * 到达时由控制台执行的命令列表，支持 {@code %player%}/{@code %player_name%} 占位符。
     *
     * @return 到达命令
     */
    public List<String> finishCommands() {
        return finishCommands;
    }

    /**
     * 滞回缓冲（格）：到达触发后须离开 {@code finishRange + rearmBuffer} 才重新武装。
     *
     * @return 重武装缓冲距离
     */
    public double rearmBuffer() {
        return rearmBuffer;
    }

    /**
     * 动态位置解析器（可空）。非空时每轮刷新调用它获取目标实时位置；
     * 返回 {@code null} 表示目标当前无法定位（该轮不推送路标）。
     *
     * @return 位置解析器
     */
    public Function<Player, Location> resolver() {
        return resolver;
    }

    /** @return 标题模板可用的自定义占位符表（键不带 % 号） */
    public Map<String, String> placeholders() {
        return placeholders;
    }

    /**
     * 调用方附加载荷（模块自定义数据，服务不解释）。供位置变更/到达回调反查上下文。
     *
     * @return 附加载荷
     */
    public Object attachment() {
        return attachment;
    }

    public static Builder builder(String navId) {
        return new Builder(navId);
    }

    public static final class Builder {
        private final String navId;
        private String world = "";
        private double x;
        private double y;
        private double z;
        private String title = "";
        private String titleTemplate = "";
        private String styleId = "";
        private double finishRange = 0;
        private List<String> finishCommands = List.of();
        private double rearmBuffer = -1;
        private Function<Player, Location> resolver;
        private Map<String, String> placeholders = new LinkedHashMap<>();
        private Object attachment;

        private Builder(String navId) {
            this.navId = navId;
        }

        public Builder world(String world) {
            this.world = world == null ? "" : world;
            return this;
        }

        public Builder position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        public Builder titleTemplate(String titleTemplate) {
            this.titleTemplate = titleTemplate == null ? "" : titleTemplate;
            return this;
        }

        public Builder styleId(String styleId) {
            this.styleId = styleId == null ? "" : styleId;
            return this;
        }

        public Builder finishRange(double finishRange) {
            this.finishRange = finishRange;
            return this;
        }

        public Builder finishCommands(List<String> finishCommands) {
            this.finishCommands = finishCommands == null ? List.of() : finishCommands;
            return this;
        }

        public Builder rearmBuffer(double rearmBuffer) {
            this.rearmBuffer = rearmBuffer;
            return this;
        }

        public Builder resolver(Function<Player, Location> resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder placeholders(Map<String, String> placeholders) {
            this.placeholders = placeholders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(placeholders);
            return this;
        }

        public Builder placeholder(String key, String value) {
            this.placeholders.put(key, value == null ? "" : value);
            return this;
        }

        public Builder attachment(Object attachment) {
            this.attachment = attachment;
            return this;
        }

        public NavigationTarget build() {
            return new NavigationTarget(this);
        }
    }
}
