/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.lang.inline.InlineLanguageKt
 *  ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedStringList
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
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.lang.inline.InlineLanguageKt;
import ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedStringList;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="ui")
@Option(type=Option.Type.SECTION)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\t\b\u0007\u0018\u0000 \u001c2\u00020\u0001:\u0001\u001cB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0007\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001c\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u001a\u0010\u0013\u001a\u00020\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u001a\u0010\u0019\u001a\u00020\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u0016\"\u0004\b\u001b\u0010\u0018\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonUI;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/QuestContainer;)V", "description", "Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "getDescription", "()Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "setDescription", "(Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;)V", "icon", "", "getIcon", "()Ljava/lang/String;", "setIcon", "(Ljava/lang/String;)V", "visibleComplete", "", "getVisibleComplete", "()Z", "setVisibleComplete", "(Z)V", "visibleStart", "getVisibleStart", "setVisibleStart", "Companion", "Chemdah"})
public final class AddonUI
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private boolean visibleStart;
    private boolean visibleComplete;
    @Nullable
    private String icon;
    @NotNull
    private TranslatedStringList description;

    public AddonUI(@NotNull ConfigurationSection root2, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(root2, questContainer);
        this.visibleStart = root2.getBoolean("visible.start", false);
        this.visibleComplete = root2.getBoolean("visible.complete", true);
        this.icon = root2.getString("icon");
        this.description = InlineLanguageKt.getTranslatedStringList((ConfigurationSection)root2, (String)"description");
    }

    public final boolean getVisibleStart() {
        return this.visibleStart;
    }

    public final void setVisibleStart(boolean bl) {
        this.visibleStart = bl;
    }

    public final boolean getVisibleComplete() {
        return this.visibleComplete;
    }

    public final void setVisibleComplete(boolean bl) {
        this.visibleComplete = bl;
    }

    @Nullable
    public final String getIcon() {
        return this.icon;
    }

    public final void setIcon(@Nullable String string) {
        this.icon = string;
    }

    @NotNull
    public final TranslatedStringList getDescription() {
        return this.description;
    }

    public final void setDescription(@NotNull TranslatedStringList translatedStringList) {
        Intrinsics.checkNotNullParameter((Object)translatedStringList, (String)"<set-?>");
        this.description = translatedStringList;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\f\u0010\u0003\u001a\u0004\u0018\u00010\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonUI$Companion;", "", "()V", "ui", "Link/ptms/chemdah/core/quest/addon/AddonUI;", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @Nullable
        public final AddonUI ui(@NotNull Template $this$ui) {
            Intrinsics.checkNotNullParameter((Object)$this$ui, (String)"<this>");
            return (AddonUI)$this$ui.addon("ui");
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

