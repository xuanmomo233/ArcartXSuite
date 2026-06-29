/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.util.ConfigurationKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\f\b\u0016\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R%\u0010\b\u001a\r\u0012\t\u0012\u00070\n\u00a2\u0006\u0002\b\u000b0\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001a\u0010\u0010\u001a\u00020\u0011X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015R\u001a\u0010\u0016\u001a\u00020\u0017X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0018\u0010\u0019\"\u0004\b\u001a\u0010\u001bR\u001a\u0010\u001c\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u0007\"\u0004\b\u001e\u0010\u001fR\u001a\u0010 \u001a\u00020\u0017X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010\u0019\"\u0004\b!\u0010\u001bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0007\u00a8\u0006#"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "root", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "content", "", "", "Lorg/jetbrains/annotations/NotNull;", "getContent", "()Ljava/util/List;", "setContent", "(Ljava/util/List;)V", "distance", "", "getDistance", "()D", "setDistance", "(D)V", "enable", "", "getEnable", "()Z", "setEnable", "(Z)V", "extraOption", "getExtraOption", "setExtraOption", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "isHideNear", "setHideNear", "getRoot", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTrackLandmark.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TrackLandmark.kt\nink/ptms/chemdah/core/quest/addon/data/TrackLandmark\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,78:1\n1360#2:79\n1446#2,5:80\n*S KotlinDebug\n*F\n+ 1 TrackLandmark.kt\nink/ptms/chemdah/core/quest/addon/data/TrackLandmark\n*L\n70#1:79\n70#1:80,5\n*E\n"})
public class TrackLandmark {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ConfigurationSection root;
    private boolean enable;
    @NotNull
    private ConfigurationSection extraOption;
    private boolean isHideNear;
    private double distance;
    @NotNull
    private List<String> content;

    /*
     * WARNING - void declaration
     */
    public TrackLandmark(@NotNull ConfigurationSection config, @NotNull ConfigurationSection root2) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.config = config;
        this.root = root2;
        this.enable = this.config.getBoolean("landmark", this.root.getBoolean("value"));
        ConfigurationSection configurationSection = this.config.getConfigurationSection("landmark-option");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        }
        this.extraOption = ConfigurationKt.mergeTo(this.root, configurationSection, false);
        this.isHideNear = this.extraOption.getBoolean("hide-near");
        this.distance = this.extraOption.getDouble("distance");
        TrackLandmark trackLandmark = this;
        if (this.extraOption.contains("content")) {
            Iterable iterable;
            void $this$flatMapTo$iv$iv;
            void $this$flatMap$iv;
            Object object = this.extraOption.get("content");
            Intrinsics.checkNotNull((Object)object);
            Iterable iterable2 = CollectionKt.asList((Object)object);
            TrackLandmark trackLandmark2 = trackLandmark;
            boolean $i$f$flatMap = false;
            void var5_6 = $this$flatMap$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$flatMapTo = false;
            for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
                String it = (String)element$iv$iv;
                boolean bl = false;
                Iterable list$iv$iv = StringsKt.lines((CharSequence)it);
                CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
            }
            trackLandmark = trackLandmark2;
            iterable2 = UtilKt.colored((List)((List)destination$iv$iv));
            if (iterable2.isEmpty()) {
                trackLandmark2 = trackLandmark;
                boolean bl = false;
                Object object2 = this.root.get("content");
                Intrinsics.checkNotNull((Object)object2);
                iterable = UtilKt.colored((List)CollectionKt.asList((Object)object2));
                trackLandmark = trackLandmark2;
            } else {
                iterable = iterable2;
            }
            list2 = (List)iterable;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        trackLandmark.content = list2;
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

    public final boolean isHideNear() {
        return this.isHideNear;
    }

    public final void setHideNear(boolean bl) {
        this.isHideNear = bl;
    }

    public final double getDistance() {
        return this.distance;
    }

    public final void setDistance(double d) {
        this.distance = d;
    }

    @NotNull
    public final List<String> getContent() {
        return this.content;
    }

    public final void setContent(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.content = list2;
    }
}

