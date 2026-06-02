package xuanmo.arcartxsuite.announcer.config;

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
