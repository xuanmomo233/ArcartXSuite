/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.XSound
 *  ink.ptms.chemdah.taboolib.module.chat.Components
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.core.conversation.LineFormat;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.ThemeChatSettings;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XSound;
import ink.ptms.chemdah.taboolib.module.chat.Components;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000p\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0007\n\u0002\u0010\b\n\u0002\b\n\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 C2\u00020\u0001:\u0002CDB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010?\u001a\u00020@2\u0006\u0010A\u001a\u00020BR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u001d\u0010\t\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\f0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0017\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u001d\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u00140\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000eR\u0011\u0010\u0016\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\bR\u0011\u0010\u0018\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\bR\u0017\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0012R\u0011\u0010\u001b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001dR\u0013\u0010\u001e\u001a\u0004\u0018\u00010\u001f\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010!R\u0011\u0010\"\u001a\u00020#\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010%R\u0011\u0010&\u001a\u00020#\u00a2\u0006\b\n\u0000\u001a\u0004\b'\u0010%R\u0011\u0010(\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010\u001dR\u0011\u0010*\u001a\u00020+\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010-R\u0011\u0010.\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u00100R\u0011\u00101\u001a\u00020+\u00a2\u0006\b\n\u0000\u001a\u0004\b2\u0010-R\u0011\u00103\u001a\u00020+\u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010-R\u0011\u00105\u001a\u000206\u00a2\u0006\b\n\u0000\u001a\u0004\b7\u00108R\u0011\u00109\u001a\u00020:\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u0010<R\u0011\u0010=\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b>\u0010\b\u00a8\u0006E"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings;", "Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "animation", "", "getAnimation", "()Z", "customSelect", "", "", "Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings$ReplyFormat;", "getCustomSelect", "()Ljava/util/Map;", "format", "", "getFormat", "()Ljava/util/List;", "formatLine", "Link/ptms/chemdah/core/conversation/LineFormat;", "getFormatLine", "hoverText", "getHoverText", "isSingleLineEnabled", "replyInteract", "getReplyInteract", "select", "getSelect", "()Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings$ReplyFormat;", "selectSound", "Link/ptms/chemdah/taboolib/library/xseries/XSound;", "getSelectSound", "()Link/ptms/chemdah/taboolib/library/xseries/XSound;", "selectSoundPitch", "", "getSelectSoundPitch", "()F", "selectSoundVolume", "getSelectSoundVolume", "selected", "getSelected", "singleLineAutoSwap", "", "getSingleLineAutoSwap", "()I", "singleLineReplySeparator", "getSingleLineReplySeparator", "()Ljava/lang/String;", "spaceFilling", "getSpaceFilling", "spaceLine", "getSpaceLine", "speed", "", "getSpeed", "()J", "talking", "Link/ptms/chemdah/taboolib/module/chat/RawMessage;", "getTalking", "()Link/ptms/chemdah/taboolib/module/chat/RawMessage;", "useScroll", "getUseScroll", "playSelectSound", "", "session", "Link/ptms/chemdah/core/conversation/Session;", "Companion", "ReplyFormat", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nThemeChatSettings.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ThemeChatSettings.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChatSettings\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,103:1\n1#2:104\n*E\n"})
public final class ThemeChatSettings
extends ThemeSettings {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final List<String> format;
    @NotNull
    private final Map<String, LineFormat> formatLine;
    @NotNull
    private final ReplyFormat select;
    @NotNull
    private final ReplyFormat selected;
    @NotNull
    private final Map<String, ReplyFormat> customSelect;
    @NotNull
    private final RawMessage talking;
    private final boolean animation;
    private final long speed;
    private final boolean hoverText;
    private final int spaceLine;
    private final boolean useScroll;
    @Nullable
    private final XSound selectSound;
    private final float selectSoundPitch;
    private final float selectSoundVolume;
    private final boolean isSingleLineEnabled;
    private final int singleLineAutoSwap;
    @NotNull
    private final String singleLineReplySeparator;
    private final int spaceFilling;
    @NotNull
    private final List<String> replyInteract;
    @NotNull
    private static final Configuration defaultReplySection = Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);

    public ThemeChatSettings(@NotNull ConfigurationSection root2) {
        Collection collection;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        super(root2);
        this.format = root2.getStringList("format");
        this.formatLine = SectionsKt.mapSection((ConfigurationSection)root2, (String)"format-line", (Function1)formatLine.1.INSTANCE);
        ConfigurationSection configurationSection = root2.getConfigurationSection("select.reply");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)defaultReplySection;
        }
        this.select = new ReplyFormat(configurationSection);
        ConfigurationSection configurationSection2 = root2.getConfigurationSection("select.reply-selected");
        if (configurationSection2 == null) {
            configurationSection2 = (ConfigurationSection)defaultReplySection;
        }
        this.selected = new ReplyFormat(configurationSection2);
        this.customSelect = SectionsKt.mapSection((ConfigurationSection)root2, (String)"select.reply-custom", (Function1)customSelect.1.INSTANCE);
        String string = root2.getString("talking", "");
        Intrinsics.checkNotNull((Object)string);
        this.talking = Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)string, null, (int)2, null);
        this.animation = root2.getBoolean("animation", true);
        this.speed = root2.getLong("speed", 1L);
        this.hoverText = root2.getBoolean("hover-text", true);
        this.spaceLine = root2.getInt("space-line", 30);
        this.useScroll = root2.getBoolean("use-scroll");
        this.selectSound = XSound.matchXSound((String)String.valueOf(root2.getString("select.sound.name"))).orElse(null);
        this.selectSoundPitch = (float)root2.getDouble("select.sound.p");
        this.selectSoundVolume = (float)root2.getDouble("select.sound.v");
        this.isSingleLineEnabled = root2.getBoolean("single-line.enable");
        this.singleLineAutoSwap = root2.getInt("single-line.auto-swap", 12);
        String string2 = root2.getString("single-line.reply-separator", " ");
        Intrinsics.checkNotNull((Object)string2);
        this.singleLineReplySeparator = string2;
        this.spaceFilling = root2.getInt("space-filling", 5);
        ThemeChatSettings themeChatSettings = this;
        Collection collection2 = root2.getStringList("reply-interaction");
        if (collection2.isEmpty()) {
            ThemeChatSettings themeChatSettings2 = themeChatSettings;
            boolean bl = false;
            collection = CollectionsKt.listOf((Object)"SWAP");
            themeChatSettings = themeChatSettings2;
        } else {
            collection = collection2;
        }
        themeChatSettings.replyInteract = (List)collection;
    }

    @NotNull
    public final List<String> getFormat() {
        return this.format;
    }

    @NotNull
    public final Map<String, LineFormat> getFormatLine() {
        return this.formatLine;
    }

    @NotNull
    public final ReplyFormat getSelect() {
        return this.select;
    }

    @NotNull
    public final ReplyFormat getSelected() {
        return this.selected;
    }

    @NotNull
    public final Map<String, ReplyFormat> getCustomSelect() {
        return this.customSelect;
    }

    @NotNull
    public final RawMessage getTalking() {
        return this.talking;
    }

    public final boolean getAnimation() {
        return this.animation;
    }

    public final long getSpeed() {
        return this.speed;
    }

    public final boolean getHoverText() {
        return this.hoverText;
    }

    public final int getSpaceLine() {
        return this.spaceLine;
    }

    public final boolean getUseScroll() {
        return this.useScroll;
    }

    @Nullable
    public final XSound getSelectSound() {
        return this.selectSound;
    }

    public final float getSelectSoundPitch() {
        return this.selectSoundPitch;
    }

    public final float getSelectSoundVolume() {
        return this.selectSoundVolume;
    }

    public final boolean isSingleLineEnabled() {
        return this.isSingleLineEnabled;
    }

    public final int getSingleLineAutoSwap() {
        return this.singleLineAutoSwap;
    }

    @NotNull
    public final String getSingleLineReplySeparator() {
        return this.singleLineReplySeparator;
    }

    public final int getSpaceFilling() {
        return this.spaceFilling;
    }

    @NotNull
    public final List<String> getReplyInteract() {
        return this.replyInteract;
    }

    public final void playSelectSound(@NotNull Session session) {
        block0: {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            XSound xSound = this.selectSound;
            if (xSound == null) break block0;
            xSound.play((Entity)session.getPlayer(), this.selectSoundPitch, this.selectSoundVolume);
        }
    }

    static {
        defaultReplySection.set("1", (Object)"&7\u25b6 &f&n{player_side}&r &8(&6&lF&8)");
        defaultReplySection.set("0", (Object)"&8\u25b6 &7{player_side}&r   &r");
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings$Companion;", "", "()V", "defaultReplySection", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "getDefaultReplySection", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Configuration getDefaultReplySection() {
            return defaultReplySection;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u000e\u001a\u00020\u0006H\u0016R\u0016\u0010\u0005\u001a\u00070\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0016\u0010\f\u001a\u00070\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\t\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings$ReplyFormat;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "other", "", "Lorg/jetbrains/annotations/NotNull;", "getOther", "()Ljava/lang/String;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "select", "getSelect", "toString", "Chemdah"})
    public static final class ReplyFormat {
        @NotNull
        private final ConfigurationSection root;
        @NotNull
        private final String select;
        @NotNull
        private final String other;

        public ReplyFormat(@NotNull ConfigurationSection root2) {
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            this.root = root2;
            String string = this.root.getString("1", "");
            Intrinsics.checkNotNull((Object)string);
            this.select = UtilKt.colored((String)string);
            String string2 = this.root.getString("0", "");
            Intrinsics.checkNotNull((Object)string2);
            this.other = UtilKt.colored((String)string2);
        }

        @NotNull
        public final ConfigurationSection getRoot() {
            return this.root;
        }

        @NotNull
        public final String getSelect() {
            return this.select;
        }

        @NotNull
        public final String getOther() {
            return this.other;
        }

        @NotNull
        public String toString() {
            return "ReplyFormat(select='" + this.select + "', other='" + this.other + "')";
        }
    }
}

