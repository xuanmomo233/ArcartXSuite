package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;

/** Snapshot access is thread-safe. Player credential and binding actions are invoked on the server thread. */
@PublicCapability
public interface AuthRuntimeCapability {
    record Result(boolean success, String code, AuthSnapshot snapshot) {}
    record ExternalPrompt(UUID requestId,String verificationUrl,String userCode,long expiresAt,String confirmation) {}
    record Terms(boolean enabled,String version,java.util.List<String> lines) {
        public Terms { lines=java.util.List.copyOf(lines); }
    }
    Terms terms();
    boolean passwordRegistrationAllowed();
    boolean passwordAuthenticationAllowed();
    AuthSnapshot snapshot(UUID playerId);
    long eligibleNanos(UUID playerId);
    CompletionStage<Result> refresh(UUID playerId,UUID connectionId);
    default ExternalPrompt externalPrompt(UUID playerId) { return null; }
    default boolean externalLinkAvailable() { return false; }
    default CompletionStage<Result> beginExternalLink(UUID playerId,UUID connectionId) {
        return CompletableFuture.completedFuture(new Result(false,"EXTERNAL_DISABLED",snapshot(playerId)));
    }
    default CompletionStage<Result> confirmExternal(UUID playerId,UUID connectionId,UUID requestId) {
        return CompletableFuture.completedFuture(new Result(false,"EXTERNAL_DISABLED",snapshot(playerId)));
    }
    /** Host performs credential work off-thread; the supplied password array is consumed and cleared. */
    CompletionStage<Result> authenticate(UUID playerId, UUID connectionId, char[] password, boolean register, boolean agreed, String termsVersion);
    CompletionStage<Result> changePassword(UUID playerId, UUID connectionId, char[] oldPassword, char[] newPassword);
    CompletionStage<Result> confirmBinding(UUID playerId, UUID connectionId, String code);
    CompletionStage<Result> acceptTerms(UUID playerId,UUID connectionId,String version);
    CompletionStage<Void> revoke(UUID accountId, String reason);
}
