package xuanmo.arcartxsuite.auth.protocol;

import com.google.gson.*;
import java.util.*;

/** Node-only protocol. No public browser endpoints and no credential hashes in responses. */
public final class AuthWire {
    public static final String PATH="/v1/node/rpc";
    public static final Gson JSON=new Gson();
    private AuthWire() {}
    public enum Operation { OPEN, PASSWORD, TERMS, BIND, EXCHANGE, CHECK, CLOSE,
        CHANGE_PASSWORD, BINDINGS, REQUEST_BINDING, UNBIND_QQ, UNBIND_PROFILE, REVOKE,
        PRESENCE, ACK_PRESENCE, ACK_FIRST, OUTBOX, EXTERNAL_LINK, EXTERNAL_CONFIRM }
    public record Context(String realm,String audience,UUID profile,String name,UUID connection) {}
    public record Request(int version,UUID requestId,Operation operation,Context context,JsonObject data) {}
    public record Policy(int minLength,int maxLength,boolean requireTerms,String termsVersion,List<String> termsLines,
            boolean requireBinding,String timezone,int loginTimeoutSeconds,boolean gamePasswordEnabled,boolean externalEnabled) {
        public Policy { termsLines=List.copyOf(termsLines); }
    }
    public record ExternalPrompt(UUID requestId,String verificationUrl,String userCode,long expiresAt,String confirmation) {}
    public record State(boolean registered,UUID account,long securityVersion,String provider,Set<String> requirements,
            UUID flow,String ticket,UUID session,long leaseMillis,boolean firstAdmission) {
        public State { requirements=Set.copyOf(requirements); }
    }
    public record Binding(UUID accountId,UUID profileId,String profileName,long qqId,long boundAt) {}
    public enum EventType { ACCOUNT_CREATED, TERMS_ACCEPTED, BINDING_ADDED, BINDING_REMOVED, PASSWORD_CHANGED, ACCOUNT_REVOKED, ADMISSION_GRANTED, FIRST_ADMISSION, PRESENCE_DAY, IDENTITY_LINKED }
    public record OutboxEvent(long sequence,String id,EventType type,UUID account,UUID profile,long securityVersion,UUID connection,String detail) {}
    public record OutboxPage(long cursor,boolean gap,List<OutboxEvent> events) { public OutboxPage { events=List.copyOf(events); } }
    public record Reply(boolean success,String code,State state,Policy policy,List<Binding> bindings,String value) {
        public Reply { bindings=bindings==null?List.of():List.copyOf(bindings); }
        public static Reply fail(String code) { return new Reply(false,code,null,null,List.of(),""); }
        public static Reply ok(String code) { return new Reply(true,code,null,null,List.of(),""); }
    }
}
