/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.core.conversation.AgentType;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0013\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\bJ\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0003J\u000b\u0010\u0017\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003J/\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u0006H\u00c6\u0001J\u0013\u0010\u0019\u001a\u00020\u001a2\b\u0010\u001b\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001c\u001a\u00020\u001dH\u00d6\u0001J\t\u0010\u001e\u001a\u00020\u0006H\u00d6\u0001R \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001c\u0010\u0007\u001a\u0004\u0018\u00010\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014\u00a8\u0006\u001f"}, d2={"Link/ptms/chemdah/core/conversation/Agent;", "", "type", "Link/ptms/chemdah/core/conversation/AgentType;", "action", "", "", "restrict", "(Link/ptms/chemdah/core/conversation/AgentType;Ljava/util/List;Ljava/lang/String;)V", "getAction", "()Ljava/util/List;", "setAction", "(Ljava/util/List;)V", "getRestrict", "()Ljava/lang/String;", "setRestrict", "(Ljava/lang/String;)V", "getType", "()Link/ptms/chemdah/core/conversation/AgentType;", "setType", "(Link/ptms/chemdah/core/conversation/AgentType;)V", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
public final class Agent {
    @NotNull
    private AgentType type;
    @NotNull
    private List<String> action;
    @Nullable
    private String restrict;

    public Agent(@NotNull AgentType type, @NotNull List<String> action, @Nullable String restrict) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        Intrinsics.checkNotNullParameter(action, (String)"action");
        this.type = type;
        this.action = action;
        this.restrict = restrict;
    }

    @NotNull
    public final AgentType getType() {
        return this.type;
    }

    public final void setType(@NotNull AgentType agentType) {
        Intrinsics.checkNotNullParameter((Object)((Object)agentType), (String)"<set-?>");
        this.type = agentType;
    }

    @NotNull
    public final List<String> getAction() {
        return this.action;
    }

    public final void setAction(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.action = list2;
    }

    @Nullable
    public final String getRestrict() {
        return this.restrict;
    }

    public final void setRestrict(@Nullable String string) {
        this.restrict = string;
    }

    @NotNull
    public final AgentType component1() {
        return this.type;
    }

    @NotNull
    public final List<String> component2() {
        return this.action;
    }

    @Nullable
    public final String component3() {
        return this.restrict;
    }

    @NotNull
    public final Agent copy(@NotNull AgentType type, @NotNull List<String> action, @Nullable String restrict) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        Intrinsics.checkNotNullParameter(action, (String)"action");
        return new Agent(type, action, restrict);
    }

    public static /* synthetic */ Agent copy$default(Agent agent2, AgentType agentType, List list2, String string, int n, Object object) {
        if ((n & 1) != 0) {
            agentType = agent2.type;
        }
        if ((n & 2) != 0) {
            list2 = agent2.action;
        }
        if ((n & 4) != 0) {
            string = agent2.restrict;
        }
        return agent2.copy(agentType, list2, string);
    }

    @NotNull
    public String toString() {
        return "Agent(type=" + (Object)((Object)this.type) + ", action=" + this.action + ", restrict=" + this.restrict + ')';
    }

    public int hashCode() {
        int result = this.type.hashCode();
        result = result * 31 + ((Object)this.action).hashCode();
        result = result * 31 + (this.restrict == null ? 0 : this.restrict.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Agent)) {
            return false;
        }
        Agent agent2 = (Agent)other;
        if (this.type != agent2.type) {
            return false;
        }
        if (!Intrinsics.areEqual(this.action, agent2.action)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.restrict, (Object)agent2.restrict);
    }
}

