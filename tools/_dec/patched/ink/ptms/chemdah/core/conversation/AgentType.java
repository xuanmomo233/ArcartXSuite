/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation;

import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\f\b\u0086\u0001\u0018\u0000 \u00132\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0013B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000fj\u0002\b\u0010j\u0002\b\u0011j\u0002\b\u0012\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/conversation/AgentType;", "", "namespace", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getNamespace", "()Ljava/lang/String;", "namespaceAll", "", "BEGIN", "BEGIN_ASYNC", "START", "START_ASYNC", "REFUSE", "REFUSE_ASYNC", "END", "END_ASYNC", "GOTO", "NONE", "Companion", "Chemdah"})
public final class AgentType
extends Enum<AgentType> {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private final String namespace;
    public static final /* enum */ AgentType BEGIN;
    public static final /* enum */ AgentType BEGIN_ASYNC;
    public static final /* enum */ AgentType START;
    public static final /* enum */ AgentType START_ASYNC;
    public static final /* enum */ AgentType REFUSE;
    public static final /* enum */ AgentType REFUSE_ASYNC;
    public static final /* enum */ AgentType END;
    public static final /* enum */ AgentType END_ASYNC;
    public static final /* enum */ AgentType GOTO;
    public static final /* enum */ AgentType NONE;
    private static final /* synthetic */ AgentType[] $VALUES;

    private AgentType(String namespace) {
        this.namespace = namespace;
    }

    @NotNull
    public final String getNamespace() {
        return this.namespace;
    }

    @NotNull
    public final List<String> namespaceAll() {
        Object[] objectArray = new String[]{"chemdah", "chemdah-conversation", "chemdah-conversation-" + this.namespace, "adyeshach"};
        return CollectionsKt.listOf((Object[])objectArray);
    }

    public static AgentType[] values() {
        return (AgentType[])$VALUES.clone();
    }

    public static AgentType valueOf(String value2) {
        return Enum.valueOf(AgentType.class, value2);
    }

    static {
        BEGIN = new AgentType("npc");
        BEGIN_ASYNC = new AgentType("npc");
        START = new AgentType("npc");
        START_ASYNC = new AgentType("npc");
        REFUSE = new AgentType("player");
        REFUSE_ASYNC = new AgentType("player");
        END = new AgentType("player");
        END_ASYNC = new AgentType("player");
        GOTO = new AgentType("npc");
        NONE = new AgentType("");
        $VALUES = agentTypeArray = new AgentType[]{AgentType.BEGIN, AgentType.BEGIN_ASYNC, AgentType.START, AgentType.START_ASYNC, AgentType.REFUSE, AgentType.REFUSE_ASYNC, AgentType.END, AgentType.END_ASYNC, AgentType.GOTO, AgentType.NONE};
        Companion = new Companion(null);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/conversation/AgentType$Companion;", "", "()V", "toAgent", "Link/ptms/chemdah/core/conversation/AgentType;", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final AgentType toAgent(@NotNull String $this$toAgent) {
            AgentType agentType;
            Intrinsics.checkNotNullParameter((Object)$this$toAgent, (String)"<this>");
            try {
                String string = $this$toAgent.toUpperCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
                agentType = AgentType.valueOf(string);
            }
            catch (Exception ignored) {
                agentType = NONE;
            }
            return agentType;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

