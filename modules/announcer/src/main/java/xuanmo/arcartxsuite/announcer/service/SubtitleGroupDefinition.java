package xuanmo.arcartxsuite.announcer.service;

import java.util.List;

/**
 * 字幕组定义，包含有序帧列表。
 */
public record SubtitleGroupDefinition(
    String id,
    List<SubtitleFrame> frames,
    List<String> uiIds
) {

    public record SubtitleFrame(
        String text,
        int length,
        int timeMs,
        double keepSeconds
    ) {}
}
