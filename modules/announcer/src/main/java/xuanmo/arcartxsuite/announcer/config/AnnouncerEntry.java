package xuanmo.arcartxsuite.announcer.config;

/**
 * 公告条目配置记录，隶属于 announcer 模块。
 * <p>
 * 表示一条可轮播的公告，包含唯一标识、启用状态、展示文本与可选的点击命令。
 * 紧凑构造器会将 {@code null} 字段归一化为空字符串。
 *
 * @param id           公告条目唯一标识
 * @param enabled      是否启用该条目参与轮播
 * @param text         公告展示文本
 * @param clickCommand 玩家点击公告时执行的命令，为空表示无点击行为
 */
public record AnnouncerEntry(
    String id,
    boolean enabled,
    String text,
    String clickCommand
) {

    public AnnouncerEntry {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        clickCommand = clickCommand == null ? "" : clickCommand;
    }
}
