package xuanmo.arcartxsuite.chat.model;

public record ChatItemPreview(
    String itemJson,
    String displayText,
    String materialKey,
    int amount
) {
    public boolean available() {
        return materialKey != null && !materialKey.isBlank() && amount > 0;
    }
}
