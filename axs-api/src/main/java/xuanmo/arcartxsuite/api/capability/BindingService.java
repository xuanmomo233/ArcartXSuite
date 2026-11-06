package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Trusted server-side binding channel. Transport adapters must authenticate the external actor. */
@PublicCapability
public interface BindingService {
    record Binding(UUID accountId,UUID profileId,String profileName,long qqId,long boundAt) {}
    record BindingResult(boolean success,String message) {}
    List<Binding> snapshot();
    CompletionStage<BindingResult> requestFromQq(long qqId,String profileName);
    CompletionStage<BindingResult> revokeByQq(long verifiedQqId);
    CompletionStage<BindingResult> revokeByProfile(UUID profileId);
}
