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
    public QQBotBindService(QQBotBindingConfig config,QQBotRepository repository) { this.config=config; this.repository=repository; }
    public record BindResult(boolean success,String message) {}
    private CompletionStage<BindResult> unavailable() { return CompletableFuture.completedFuture(new BindResult(false,"绑定服务未就绪")); }
    public CompletionStage<BindResult> requestBind(long qq,String name) {
        var service=AxsCapabilities.get(BindingService.class);
        if(!config.enabled() || service==null) return unavailable();
        return service.requestFromQq(qq,name).thenApply(r->new BindResult(r.success(),r.message()));
    }
    public CompletionStage<BindResult> confirmBind(Player player,String code) {
        var service=AxsCapabilities.get(AuthRuntimeCapability.class);
        if(!config.enabled() || service==null) return unavailable();
        var state=service.snapshot(player.getUniqueId());
        if(!state.identityVerified()) return CompletableFuture.completedFuture(new BindResult(false,"请先验证账号身份"));
        return service.confirmBinding(player.getUniqueId(),state.connectionId(),code)
            .thenApply(r->new BindResult(r.success(),r.success()?"绑定成功":r.code()));
    }
    public CompletionStage<BindResult> unbindByQq(long qq) {
        var service=AxsCapabilities.get(BindingService.class);
        return service==null?unavailable():service.revokeByQq(qq).thenApply(r->new BindResult(r.success(),r.message()));
    }
    public CompletionStage<BindResult> unbindByPlayer(UUID profile) {
        var service=AxsCapabilities.get(BindingService.class);
        return service==null?unavailable():service.revokeByProfile(profile).thenApply(r->new BindResult(r.success(),r.message()));
    }
    public CompletionStage<BindResult> unbindByPlayer(String name) {
        var binding=repository.findByPlayerName(name);
        return binding==null?CompletableFuture.completedFuture(new BindResult(false,"未绑定")):unbindByPlayer(binding.playerUuid());
    }
    public QQBotBinding findByQq(long qq) { return repository.findByQq(qq); }
    public QQBotBinding findByPlayer(UUID profile) { return repository.findByPlayer(profile); }
    public QQBotBinding findByPlayerName(String name) { return repository.findByPlayerName(name); }
}
