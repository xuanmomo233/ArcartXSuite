/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.module.scenes.ScenesState;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.util.NumberKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\f\u001a\u00020\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/module/scenes/ScenesFile;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "state", "", "Link/ptms/chemdah/module/scenes/ScenesState;", "getState", "()Ljava/util/List;", "world", "", "getWorld", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nScenesFile.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScenesFile.kt\nink/ptms/chemdah/module/scenes/ScenesFile\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,23:1\n1603#2,9:24\n1855#2:33\n1856#2:35\n1612#2:36\n1#3:34\n*S KotlinDebug\n*F\n+ 1 ScenesFile.kt\nink/ptms/chemdah/module/scenes/ScenesFile\n*L\n16#1:24,9\n16#1:33\n16#1:35\n16#1:36\n16#1:34\n*E\n"})
public final class ScenesFile {
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private final String world;
    @NotNull
    private final List<ScenesState> state;

    /*
     * WARNING - void declaration
     */
    public ScenesFile(@NotNull ConfigurationSection root2) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        String string = this.root.getString("in");
        if (string == null) {
            string = "world";
        }
        this.world = string;
        ScenesFile scenesFile = this;
        Object object = this.root.getConfigurationSection("state");
        if (object != null && (object = object.getKeys(false)) != null) {
            void $this$mapNotNullTo$iv$iv;
            void $this$mapNotNull$iv;
            Iterable iterable = (Iterable)object;
            ScenesFile scenesFile2 = scenesFile;
            boolean $i$f$mapNotNull = false;
            void var4_5 = $this$mapNotNull$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$mapNotNullTo = false;
            void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv$iv$iv.iterator();
            while (iterator.hasNext()) {
                ScenesState scenesState;
                Object element$iv$iv$iv;
                Object element$iv$iv = element$iv$iv$iv = iterator.next();
                boolean bl = false;
                String it = (String)element$iv$iv;
                boolean bl2 = false;
                if (this.root.contains("state." + it + ".set")) {
                    int n = NumberKt.asInt$default(it, 0, 1, null);
                    ConfigurationSection configurationSection = this.root.getConfigurationSection("state." + it);
                    Intrinsics.checkNotNull((Object)configurationSection);
                    scenesState = new ScenesState.Block(n, configurationSection, this);
                } else if (this.root.contains("state." + it + ".copy")) {
                    int n = NumberKt.asInt$default(it, 0, 1, null);
                    ConfigurationSection configurationSection = this.root.getConfigurationSection("state." + it);
                    Intrinsics.checkNotNull((Object)configurationSection);
                    scenesState = new ScenesState.Copy(n, configurationSection, this);
                } else {
                    scenesState = null;
                }
                if (scenesState == null) continue;
                ScenesState it$iv$iv = scenesState;
                boolean bl3 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            list2 = (List)destination$iv$iv;
            scenesFile = scenesFile2;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        scenesFile.state = list2;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final String getWorld() {
        return this.world;
    }

    @NotNull
    public final List<ScenesState> getState() {
        return this.state;
    }
}

