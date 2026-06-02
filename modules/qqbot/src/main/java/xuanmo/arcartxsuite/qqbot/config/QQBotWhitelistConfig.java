package xuanmo.arcartxsuite.qqbot.config;

public record QQBotWhitelistConfig(
    boolean enabled,
    boolean autoAddOnBind,
    boolean autoRemoveOnUnbind,
    String addCommand,
    String removeCommand,
    String addPrefix,
    String removePrefix,
    String listPrefix
) {}
