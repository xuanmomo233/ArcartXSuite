/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.Regex
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.Regex;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u000f\b\u0086\u0001\u0018\u0000 \u00162\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0016B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000fj\u0002\b\u0010j\u0002\b\u0011j\u0002\b\u0012j\u0002\b\u0013j\u0002\b\u0014j\u0002\b\u0015\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/AgentType;", "", "namespace", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getNamespace", "()Ljava/lang/String;", "namespaceAll", "", "QUEST_ACCEPT", "QUEST_ACCEPTED", "QUEST_ACCEPT_CANCELLED", "QUEST_FAIL", "QUEST_FAILED", "QUEST_COMPLETE", "QUEST_COMPLETED", "QUEST_RESTART", "QUEST_RESTARTED", "TASK_CONTINUED", "TASK_RESTARTED", "TASK_COMPLETED", "NONE", "Companion", "Chemdah"})
public final class AgentType
extends Enum<AgentType> {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private final String namespace;
    public static final /* enum */ AgentType QUEST_ACCEPT;
    public static final /* enum */ AgentType QUEST_ACCEPTED;
    public static final /* enum */ AgentType QUEST_ACCEPT_CANCELLED;
    public static final /* enum */ AgentType QUEST_FAIL;
    public static final /* enum */ AgentType QUEST_FAILED;
    public static final /* enum */ AgentType QUEST_COMPLETE;
    public static final /* enum */ AgentType QUEST_COMPLETED;
    public static final /* enum */ AgentType QUEST_RESTART;
    public static final /* enum */ AgentType QUEST_RESTARTED;
    public static final /* enum */ AgentType TASK_CONTINUED;
    public static final /* enum */ AgentType TASK_RESTARTED;
    public static final /* enum */ AgentType TASK_COMPLETED;
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
        Object[] objectArray = new String[]{"chemdah", "chemdah-quest", "chemdah-quest-" + this.namespace, "adyeshach"};
        return CollectionsKt.listOf((Object[])objectArray);
    }

    public static AgentType[] values() {
        return (AgentType[])$VALUES.clone();
    }

    public static AgentType valueOf(String value2) {
        return Enum.valueOf(AgentType.class, value2);
    }

    static {
        QUEST_ACCEPT = new AgentType("quest");
        QUEST_ACCEPTED = new AgentType("quest");
        QUEST_ACCEPT_CANCELLED = new AgentType("quest");
        QUEST_FAIL = new AgentType("quest");
        QUEST_FAILED = new AgentType("quest");
        QUEST_COMPLETE = new AgentType("quest");
        QUEST_COMPLETED = new AgentType("quest");
        QUEST_RESTART = new AgentType("quest");
        QUEST_RESTARTED = new AgentType("quest");
        TASK_CONTINUED = new AgentType("task");
        TASK_RESTARTED = new AgentType("task");
        TASK_COMPLETED = new AgentType("task");
        NONE = new AgentType("");
        $VALUES = agentTypeArray = new AgentType[]{AgentType.QUEST_ACCEPT, AgentType.QUEST_ACCEPTED, AgentType.QUEST_ACCEPT_CANCELLED, AgentType.QUEST_FAIL, AgentType.QUEST_FAILED, AgentType.QUEST_COMPLETE, AgentType.QUEST_COMPLETED, AgentType.QUEST_RESTART, AgentType.QUEST_RESTARTED, AgentType.TASK_CONTINUED, AgentType.TASK_RESTARTED, AgentType.TASK_COMPLETED, AgentType.NONE};
        Companion = new Companion(null);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/AgentType$Companion;", "", "()V", "toAgent", "Link/ptms/chemdah/core/quest/AgentType;", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final AgentType toAgent(@NotNull String $this$toAgent) {
            Object object;
            Intrinsics.checkNotNullParameter((Object)$this$toAgent, (String)"<this>");
            try {
                String string = $this$toAgent.toUpperCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
                object = string;
                Regex regex = new Regex("[ :]");
                String string2 = "_";
                object = AgentType.valueOf(regex.replace((CharSequence)object, string2));
            }
            catch (Exception ignored) {
                object = NONE;
            }
            return object;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

