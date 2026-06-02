package xuanmo.arcartxsuite.onlinerewards.config;

public record OnlineRewardsMakeupConfiguration(
    boolean enabled,
    String cardName,
    String successMessage,
    String noCardMessage,
    String invalidDateMessage,
    String alreadySignedMessage
) {
}
