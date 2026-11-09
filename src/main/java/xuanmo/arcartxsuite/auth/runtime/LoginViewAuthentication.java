package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.BindingService;

/** Stable capability handle. Only a running LoginView owns Suite authentication and player restrictions. */
public final class LoginViewAuthentication implements HostAuthentication {
    private final Supplier<HostAuthentication> serverFactory;
    private final Supplier<HostAuthentication> authenticationFactory;
    private volatile HostAuthentication delegate;
    private boolean loginViewEnabled;
    private java.util.concurrent.CompletableFuture<Void> retired=java.util.concurrent.CompletableFuture.completedFuture(null);

    LoginViewAuthentication(Supplier<HostAuthentication> serverFactory,Supplier<HostAuthentication> authenticationFactory) {
        this.serverFactory=serverFactory;
        this.authenticationFactory=authenticationFactory;
        delegate=serverFactory.get();
    }

    @Override public void start() { delegate.start(); }

    /** Called on the server thread by the module lifecycle, including failed loads and unloads. */
    public void setLoginViewEnabled(boolean enabled) {
        if(loginViewEnabled==enabled) return;
        HostAuthentication previous=delegate;
        HostAuthentication next=enabled?authenticationFactory.get():serverFactory.get();
        var admitted=Bukkit.getOnlinePlayers().stream().map(player->previous.snapshot(player.getUniqueId()))
            .filter(AuthSnapshot::admitted).toList();
        previous.close();
        retired=java.util.concurrent.CompletableFuture.allOf(retired,previous.drained().toCompletableFuture());
        next.awaitPrevious(retired);
        delegate=next;
        loginViewEnabled=enabled;
        try {
            next.start();
        } catch(RuntimeException | LinkageError e) {
            try { next.close(); }
            finally {
                retired=java.util.concurrent.CompletableFuture.allOf(retired,next.drained().toCompletableFuture());
                delegate=serverFactory.get();
                loginViewEnabled=false;
                delegate.start();
            }
            throw e;
        }
        if(enabled) {
            for(AuthSnapshot snapshot:admitted) Bukkit.getPluginManager().callEvent(new AuthRestrictionEvent(snapshot,"LOGINVIEW_ENABLED"));
        }
    }

    @Override public boolean requiresAdmission() { return delegate.requiresAdmission(); }
    @Override public Terms terms() { return delegate.terms(); }
    @Override public boolean passwordRegistrationAllowed() { return delegate.passwordRegistrationAllowed(); }
    @Override public boolean passwordAuthenticationAllowed() { return delegate.passwordAuthenticationAllowed(); }
    @Override public AuthSnapshot snapshot(UUID profile) { return delegate.snapshot(profile); }
    @Override public long eligibleNanos(UUID profile) { return delegate.eligibleNanos(profile); }
    @Override public CompletionStage<Result> refresh(UUID profile,UUID connection) { return delegate.refresh(profile,connection); }
    @Override public ExternalPrompt externalPrompt(UUID profile) { return delegate.externalPrompt(profile); }
    @Override public boolean externalLinkAvailable() { return delegate.externalLinkAvailable(); }
    @Override public CompletionStage<Result> beginExternalLink(UUID profile,UUID connection) { return delegate.beginExternalLink(profile,connection); }
    @Override public CompletionStage<Result> confirmExternal(UUID profile,UUID connection,UUID request) { return delegate.confirmExternal(profile,connection,request); }
    @Override public CompletionStage<Result> authenticate(UUID profile,UUID connection,char[] password,boolean register,boolean agreed,String version) {
        return delegate.authenticate(profile,connection,password,register,agreed,version);
    }
    @Override public CompletionStage<Result> changePassword(UUID profile,UUID connection,char[] oldPassword,char[] newPassword) {
        return delegate.changePassword(profile,connection,oldPassword,newPassword);
    }
    @Override public CompletionStage<Result> confirmBinding(UUID profile,UUID connection,String code) { return delegate.confirmBinding(profile,connection,code); }
    @Override public CompletionStage<Result> acceptTerms(UUID profile,UUID connection,String version) { return delegate.acceptTerms(profile,connection,version); }
    @Override public CompletionStage<Void> revoke(UUID account,String reason) { return delegate.revoke(account,reason); }
    @Override public List<BindingService.Binding> snapshot() { return delegate.snapshot(); }
    @Override public CompletionStage<BindingService.BindingResult> requestFromQq(long qq,String name) { return delegate.requestFromQq(qq,name); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByQq(long qq) { return delegate.revokeByQq(qq); }
    @Override public CompletionStage<BindingService.BindingResult> revokeByProfile(UUID profile) { return delegate.revokeByProfile(profile); }
    @Override public void close() { delegate.close(); }
}
