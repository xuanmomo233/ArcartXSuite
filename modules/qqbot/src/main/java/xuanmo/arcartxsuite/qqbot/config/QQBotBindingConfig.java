package xuanmo.arcartxsuite.qqbot.config;

public record QQBotBindingConfig(
    boolean enabled,
    String method,
    int codeExpireSeconds,
    int maxBindingsPerQq,
    String bindPrefix,
    String unbindPrefix,
    String queryPrefix
) {}
