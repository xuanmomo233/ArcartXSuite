/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.util.ConfigurationKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0010\b\n\u0002\b\u0007\b\u0016\u0018\u00002\u00020\u0001:\u0001 B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R \u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\u000f\u001a\u00020\u0010X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0015\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0007\"\u0004\b\u0017\u0010\u0018R\u001a\u0010\u0019\u001a\u00020\u001aX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0007\u00a8\u0006!"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "root", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "content", "", "Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard$Line;", "getContent", "()Ljava/util/List;", "setContent", "(Ljava/util/List;)V", "enable", "", "getEnable", "()Z", "setEnable", "(Z)V", "extraOption", "getExtraOption", "setExtraOption", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "length", "", "getLength", "()I", "setLength", "(I)V", "getRoot", "Line", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTrackScoreboard.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TrackScoreboard.kt\nink/ptms/chemdah/core/quest/addon/data/TrackScoreboard\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,50:1\n1549#2:51\n1620#2,3:52\n*S KotlinDebug\n*F\n+ 1 TrackScoreboard.kt\nink/ptms/chemdah/core/quest/addon/data/TrackScoreboard\n*L\n44#1:51\n44#1:52,3\n*E\n"})
public class TrackScoreboard {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ConfigurationSection root;
    private boolean enable;
    @NotNull
    private ConfigurationSection extraOption;
    private int length;
    @NotNull
    private List<Line> content;

    /*
     * WARNING - void declaration
     */
    public TrackScoreboard(@NotNull ConfigurationSection config, @NotNull ConfigurationSection root2) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.config = config;
        this.root = root2;
        this.enable = this.config.getBoolean("scoreboard", this.root.getBoolean("value"));
        ConfigurationSection configurationSection = this.config.getConfigurationSection("scoreboard-option");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        }
        this.extraOption = ConfigurationKt.mergeTo(this.root, configurationSection, false);
        this.length = this.extraOption.getInt("length");
        TrackScoreboard trackScoreboard = this;
        List list3 = this.extraOption.getList("content");
        boolean bl = list3 != null ? !((Collection)list3).isEmpty() : false;
        if (bl) {
            void $this$mapTo$iv$iv;
            void $this$map$iv;
            List list4 = this.extraOption.getList("content");
            Intrinsics.checkNotNull((Object)list4);
            Iterable iterable = CollectionsKt.filterNotNull((Iterable)list4);
            TrackScoreboard trackScoreboard2 = trackScoreboard;
            boolean $i$f$map = false;
            void var5_6 = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            Iterator iterator = $this$mapTo$iv$iv.iterator();
            while (iterator.hasNext()) {
                void it;
                Object item$iv$iv;
                Object t = item$iv$iv = iterator.next();
                Collection collection = destination$iv$iv;
                boolean bl2 = false;
                collection.add(new Line(UtilKt.colored((List)CollectionKt.asList((Object)it))));
            }
            list2 = (List)destination$iv$iv;
            trackScoreboard = trackScoreboard2;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        trackScoreboard.content = list2;
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    public final boolean getEnable() {
        return this.enable;
    }

    public final void setEnable(boolean bl) {
        this.enable = bl;
    }

    @NotNull
    public final ConfigurationSection getExtraOption() {
        return this.extraOption;
    }

    public final void setExtraOption(@NotNull ConfigurationSection configurationSection) {
        Intrinsics.checkNotNullParameter((Object)configurationSection, (String)"<set-?>");
        this.extraOption = configurationSection;
    }

    public final int getLength() {
        return this.length;
    }

    public final void setLength(int n) {
        this.length = n;
    }

    @NotNull
    public final List<Line> getContent() {
        return this.content;
    }

    public final void setContent(@NotNull List<Line> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.content = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard$Line;", "", "content", "", "", "(Ljava/util/List;)V", "getContent", "()Ljava/util/List;", "isQuestLine", "", "()Z", "Chemdah"})
    public static final class Line {
        @NotNull
        private final List<String> content;
        private final boolean isQuestLine;

        public Line(@NotNull List<String> content) {
            Intrinsics.checkNotNullParameter(content, (String)"content");
            this.content = content;
            this.isQuestLine = this.content.size() > 1;
        }

        @NotNull
        public final List<String> getContent() {
            return this.content;
        }

        public final boolean isQuestLine() {
            return this.isQuestLine;
        }
    }
}

