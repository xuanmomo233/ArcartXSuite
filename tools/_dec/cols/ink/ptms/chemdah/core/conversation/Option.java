/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

/*
 * Illegal identifiers - consider using --renameillegalidents true
 */
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0016\u0018\u0000 \u001a2\u00020\u0001:\u0001\u001aB!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007R \u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013R\u0015\u0010\u0014\u001a\u0006\u0012\u0002\b\u00030\u00158F\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017R\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0018\u0010\u0011\"\u0004\b\u0019\u0010\u0013\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/conversation/Option;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "theme", "", "title", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Ljava/lang/String;Ljava/lang/String;)V", "globalFlags", "", "getGlobalFlags", "()Ljava/util/List;", "setGlobalFlags", "(Ljava/util/List;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getTheme", "()Ljava/lang/String;", "setTheme", "(Ljava/lang/String;)V", "themeInstance", "Link/ptms/chemdah/core/conversation/theme/Theme;", "getThemeInstance", "()Link/ptms/chemdah/core/conversation/theme/Theme;", "getTitle", "setTitle", "Companion", "Chemdah"})
public class Option {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private String theme;
    @NotNull
    private String title;
    @NotNull
    private List<String> globalFlags;
    @NotNull
    private static final Option default = new Option(Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null).createSection("__option__"), null, null, 6, null);

    public Option(@NotNull ConfigurationSection root2, @NotNull String theme, @NotNull String title) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)theme, (String)"theme");
        Intrinsics.checkNotNullParameter((Object)title, (String)"title");
        this.root = root2;
        this.theme = theme;
        this.title = title;
        this.globalFlags = this.root.getStringList("global-flags");
    }

    public /* synthetic */ Option(ConfigurationSection configurationSection, String string, String string2, int n, DefaultConstructorMarker defaultConstructorMarker) {
        if ((n & 2) != 0) {
            String string3 = configurationSection.getString("theme", "chat");
            Intrinsics.checkNotNull((Object)string3);
            string = string3;
        }
        if ((n & 4) != 0) {
            String string4 = configurationSection.getString("title", "NPC");
            Intrinsics.checkNotNull((Object)string4);
            string2 = UtilKt.colored((String)string4);
        }
        this(configurationSection, string, string2);
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final String getTheme() {
        return this.theme;
    }

    public final void setTheme(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.theme = string;
    }

    @NotNull
    public final String getTitle() {
        return this.title;
    }

    public final void setTitle(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.title = string;
    }

    @NotNull
    public final Theme<?> getThemeInstance() {
        Theme<?> theme = ChemdahAPI.INSTANCE.getConversationTheme(this.theme);
        if (theme == null) {
            throw new IllegalStateException(("Theme " + this.theme + " not supported.").toString());
        }
        return theme;
    }

    @NotNull
    public final List<String> getGlobalFlags() {
        return this.globalFlags;
    }

    public final void setGlobalFlags(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.globalFlags = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/conversation/Option$Companion;", "", "()V", "default", "Link/ptms/chemdah/core/conversation/Option;", "getDefault", "()Link/ptms/chemdah/core/conversation/Option;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Option getDefault() {
            return default;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

