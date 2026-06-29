/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="party")
@Option(type=Option.Type.SECTION)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\f\b\u0007\u0018\u0000 \u00192\u00020\u0001:\u0001\u0019B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0007\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001a\u0010\r\u001a\u00020\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u001a\u0010\u0013\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\n\"\u0004\b\u0015\u0010\fR\u001a\u0010\u0016\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\n\"\u0004\b\u0018\u0010\f\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonParty;", "Link/ptms/chemdah/core/quest/addon/Addon;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/QuestContainer;)V", "canContinue", "", "getCanContinue", "()Z", "setCanContinue", "(Z)V", "requireMembers", "", "getRequireMembers", "()I", "setRequireMembers", "(I)V", "share", "getShare", "setShare", "shareOnlyLeader", "getShareOnlyLeader", "setShareOnlyLeader", "Companion", "Chemdah"})
public final class AddonParty
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private boolean share;
    private boolean shareOnlyLeader;
    private boolean canContinue;
    private int requireMembers;

    public AddonParty(@NotNull ConfigurationSection config, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(config, questContainer);
        this.share = config.getBoolean("share");
        this.shareOnlyLeader = config.getBoolean("share-only-leader");
        this.canContinue = config.getBoolean("continue");
        this.requireMembers = config.getInt("require-members");
    }

    public final boolean getShare() {
        return this.share;
    }

    public final void setShare(boolean bl) {
        this.share = bl;
    }

    public final boolean getShareOnlyLeader() {
        return this.shareOnlyLeader;
    }

    public final void setShareOnlyLeader(boolean bl) {
        this.shareOnlyLeader = bl;
    }

    public final boolean getCanContinue() {
        return this.canContinue;
    }

    public final void setCanContinue(boolean bl) {
        this.canContinue = bl;
    }

    public final int getRequireMembers() {
        return this.requireMembers;
    }

    public final void setRequireMembers(int n) {
        this.requireMembers = n;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\f\u0010\u0003\u001a\u0004\u0018\u00010\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonParty$Companion;", "", "()V", "party", "Link/ptms/chemdah/core/quest/addon/AddonParty;", "Link/ptms/chemdah/core/quest/QuestContainer;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @Nullable
        public final AddonParty party(@NotNull QuestContainer $this$party) {
            Intrinsics.checkNotNullParameter((Object)$this$party, (String)"<this>");
            return (AddonParty)$this$party.addon("party");
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

