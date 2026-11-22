package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.BindingService;

/** Stable capability handle for ordinary admission and active LoginView. */
public final class LoginViewAuthentication implements HostAuthentication {
    private final Supplier<HostAuthentication> serverFactory;
    private final Supplier<HostAuthentication> authenticationFactory;
    private volatile HostAuthentication delegate;
    private volatile boolean admissionSuspended;
    private boolean loginViewEnabled;
    private CompletableFuture<Void> retired = CompletableFuture.completedFuture(null);

    LoginViewAuthentication(Supplier<HostAuthentication> serverFactory,Supplier<HostAuthentication> authenticationFactory) {
        this.serverFactory=serverFactory;
        this.authenticationFactory=authenticationFactory;
        delegate=serverFactory.get();
    }

    @Override public void start() { delegate.start(); }

    private void suspendLoginViewAdmission() {
        if (admissionSuspended) return;
        var admitted = Bukkit.getOnlinePlayers().stream()
            .map(player -> delegate.snapshot(player.getUniqueId()))
            .filter(AuthSnapshot::admitted).toList();
        suspendDelegate();
        for (AuthSnapshot snapshot : admitted) {
            Bukkit.getPluginManager().callEvent(new AuthRestrictionEvent(snapshot, "LOGINVIEW_REQUIRED"));
        }
    }

    private void suspendDelegate() {
        if (admissionSuspended) return;
        admissionSuspended = true;
        loginViewEnabled = false;
        retire(delegate);
    }

    private void retire(HostAuthentication backend) {
        try {
            backend.close();
        } finally {
            retired = CompletableFuture.allOf(retired, backend.drained().toCompletableFuture());
        }
    }

    public boolean isAdmissionSuspended() { return admissionSuspended; }

    /** Called on the server thread. A disabled or failed LoginView leaves ordinary admission in charge. */
    public void setLoginViewEnabled(boolean enabled) {
        if (!admissionSuspended && loginViewEnabled == enabled) return;
        HostAuthentication next = enabled ? authenticationFactory.get() : serverFactory.get();
        boolean installed = false;
        try {
            if (enabled) suspendLoginViewAdmission();
            else suspendDelegate();
            next.awaitPrevious(retired);
            delegate = next;
            loginViewEnabled = enabled;
            admissionSuspended = false;
            installed = true;
            next.start();
        } catch (RuntimeException | LinkageError failure) {
            if (installed) {
                admissionSuspended = true;
                loginViewEnabled = false;
            }
            try {
                retire(next);
            } catch (RuntimeException | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            if (enabled) {
                try {
                    setLoginViewEnabled(false);
                } catch (RuntimeException | LinkageError restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    @Override public boolean requiresAdmission() { return admissionSuspended || delegate.requiresAdmission(); }
    @Override public Terms terms() { return delegate.terms(); }
    @Override public boolean passwordRegistrationAllowed() { return delegate.passwordRegistrationAllowed(); }
    @Override public boolean passwordAuthenticationAllowed() { return delegate.passwordAuthenticationAllowed(); }
    @Override public AuthSnapshot snapshot(UUID profile) {
        return admissionSuspended ? AuthSnapshot.absent(profile) : delegate.snapshot(profile);
    }
    @Override public long eligibleNanos(UUID profile) { return admissionSuspended ? 0 : delegate.eligibleNanos(profile); }
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
    @Override public void close() { suspendDelegate(); }
}
