package xuanmo.arcartxsuite.qqbot.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.capability.BindingService;
import xuanmo.arcartxsuite.qqbot.config.QQBotBindingConfig;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

/** QQ transport facade. No codes, binding facts, SQL writes, or whitelist commands are owned here. */
public final class QQBotBindService {
    private final QQBotBindingConfig config;
    private final QQBotRepository repository;
    public QQBotBindService(QQBotBindingConfig config, QQBotRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public record BindResult(boolean success, String message) {}

    private CompletionStage<BindResult> failed(String message) {
        return CompletableFuture.completedFuture(new BindResult(false, message));
    }

    private CompletionStage<BindResult> unavailable() { return failed("绑定服务未就绪，请稍后重试"); }

    public CompletionStage<BindResult> requestBind(long qq, String name) {
        if (qq <= 0 || name == null || !name.matches("[\\p{L}\\p{N}_]{1,16}")) return failed("请输入有效的游戏名");
        var service = AxsCapabilities.get(BindingService.class);
        if (!config.enabled() || service == null) return unavailable();
        return service.requestFromQq(qq, name)
            .thenApply(result -> new BindResult(result.success(), result.message()))
            .exceptionally(error -> new BindResult(false, "绑定请求未完成，请刷新绑定状态后重试"));
    }

    public CompletionStage<BindResult> confirmBind(Player player, String code) {
        if (code == null || code.isBlank() || code.length() > 128) return failed("请输入有效的绑定验证码");
        var service = AxsCapabilities.get(AuthRuntimeCapability.class);
        if (!config.enabled() || service == null) return unavailable();
        var state = service.snapshot(player.getUniqueId());
        if (!state.identityVerified()) return failed("请先验证账号身份");
        return service.confirmBinding(player.getUniqueId(), state.connectionId(), code.trim())
            .thenApply(result -> new BindResult(result.success(), result.success()
                ? "绑定成功" : "绑定未完成，请检查验证码是否有效并刷新状态"))
            .exceptionally(error -> new BindResult(false, "绑定结果暂未确认，请刷新绑定状态，勿重复提交"));
    }

    public CompletionStage<BindResult> unbindByQq(long qq) {
        var service = AxsCapabilities.get(BindingService.class);
        if (service == null) return unavailable();
        return service.revokeByQq(qq).thenApply(result -> new BindResult(result.success(), result.message()))
            .exceptionally(error -> new BindResult(false, "解绑结果暂未确认，请刷新绑定状态"));
    }

    public CompletionStage<BindResult> unbindByPlayer(UUID profile) {
        var service = AxsCapabilities.get(BindingService.class);
        if (service == null) return unavailable();
        return service.revokeByProfile(profile).thenApply(result -> new BindResult(result.success(), result.message()))
            .exceptionally(error -> new BindResult(false, "解绑结果暂未确认，请刷新绑定状态"));
    }

    public CompletionStage<BindResult> unbindByPlayer(String name) {
        var binding = repository.findByPlayerName(name);
        return binding == null ? failed("未绑定") : unbindByPlayer(binding.playerUuid());
    }

    public QQBotBinding findByQq(long qq) { return repository.findByQq(qq); }
    public QQBotBinding findByPlayer(UUID profile) { return repository.findByPlayer(profile); }
    public QQBotBinding findByPlayerName(String name) { return repository.findByPlayerName(name); }
}
