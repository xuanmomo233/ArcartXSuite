/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000.\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010!\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\u001a\u001c\u0010\u0000\u001a\u00020\u0001*\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00012\b\b\u0002\u0010\u0003\u001a\u00020\u0004\u001aR\u0010\u0005\u001a\b\u0012\u0004\u0012\u0002H\u00070\u0006\"\u0004\b\u0000\u0010\b\"\u0004\b\u0001\u0010\u0007*\u00020\u00012\u0006\u0010\t\u001a\u00020\n2\u0012\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u0002H\b0\f2\u0018\u0010\r\u001a\u0014\u0012\u0004\u0012\u0002H\b\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u0002H\u00070\u000e\u00a8\u0006\u0010"}, d2={"mergeTo", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "section", "overwrite", "", "sectionAs", "", "T", "K", "path", "", "kf", "Lkotlin1822/Function1;", "transform", "Lkotlin1822/Function2;", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConfiguration.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Configuration.kt\nink/ptms/chemdah/util/ConfigurationKt\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,35:1\n457#2:36\n403#2:37\n1238#3,4:38\n1855#3,2:46\n125#4:42\n152#4,3:43\n*S KotlinDebug\n*F\n+ 1 Configuration.kt\nink/ptms/chemdah/util/ConfigurationKt\n*L\n14#1:36\n14#1:37\n14#1:38,4\n21#1:46,2\n14#1:42\n14#1:43,3\n*E\n"})
public final class ConfigurationKt {
    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final <K, T> List<T> sectionAs(@NotNull ConfigurationSection $this$sectionAs, @NotNull String path, @NotNull Function1<? super String, ? extends K> kf, @NotNull Function2<? super K, Object, ? extends T> transform) {
        Object object;
        block5: {
            block4: {
                void $this$mapTo$iv$iv;
                Object object2;
                void $this$mapKeysTo$iv$iv;
                Intrinsics.checkNotNullParameter((Object)$this$sectionAs, (String)"<this>");
                Intrinsics.checkNotNullParameter((Object)path, (String)"path");
                Intrinsics.checkNotNullParameter(kf, (String)"kf");
                Intrinsics.checkNotNullParameter(transform, (String)"transform");
                object = $this$sectionAs.getConfigurationSection(path);
                if (object == null || (object = object.getValues(false)) == null) break block4;
                ConfigurationSection $this$mapKeys$iv = object;
                boolean $i$f$mapKeys = false;
                ConfigurationSection configurationSection = $this$mapKeys$iv;
                Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapKeys$iv.size()));
                boolean $i$f$mapKeysTo = false;
                Iterable $this$associateByTo$iv$iv$iv = $this$mapKeysTo$iv$iv.entrySet();
                boolean $i$f$associateByTo = false;
                for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
                    void it$iv$iv;
                    void it;
                    Map.Entry entry = (Map.Entry)element$iv$iv$iv;
                    object2 = destination$iv$iv;
                    boolean bl = false;
                    Map.Entry entry2 = (Map.Entry)element$iv$iv$iv;
                    Object object3 = kf.invoke(it.getKey());
                    Map map = object2;
                    boolean bl2 = false;
                    Object v = it$iv$iv.getValue();
                    map.put(object3, v);
                }
                Map $this$map$iv = destination$iv$iv;
                boolean $i$f$map = false;
                destination$iv$iv = $this$map$iv;
                Collection destination$iv$iv2 = new ArrayList($this$map$iv.size());
                boolean $i$f$mapTo = false;
                for (Map.Entry item$iv$iv : $this$mapTo$iv$iv.entrySet()) {
                    void it;
                    Object element$iv$iv$iv;
                    element$iv$iv$iv = item$iv$iv;
                    object2 = destination$iv$iv2;
                    boolean bl = false;
                    Object k = it.getKey();
                    Object v = it.getValue();
                    Intrinsics.checkNotNull(v);
                    object2.add(transform.invoke(k, v));
                }
                object = CollectionsKt.toMutableList((Collection)((List)destination$iv$iv2));
                if (object != null) break block5;
            }
            object = new ArrayList();
        }
        return object;
    }

    @NotNull
    public static final ConfigurationSection mergeTo(@NotNull ConfigurationSection $this$mergeTo, @NotNull ConfigurationSection section, boolean overwrite) {
        Intrinsics.checkNotNullParameter((Object)$this$mergeTo, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)section, (String)"section");
        Iterable $this$forEach$iv = $this$mergeTo.getKeys(false);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String key = (String)element$iv;
            boolean bl = false;
            if ($this$mergeTo.isConfigurationSection(key)) {
                ConfigurationSection value2;
                Intrinsics.checkNotNull((Object)$this$mergeTo.getConfigurationSection(key));
                ConfigurationSection configurationSection = section.getConfigurationSection(key);
                if (configurationSection == null) {
                    configurationSection = section.createSection(key);
                }
                ConfigurationSection targetSection = configurationSection;
                ConfigurationKt.mergeTo(value2, targetSection, overwrite);
                continue;
            }
            if (!overwrite && section.contains(key)) continue;
            section.set(key, $this$mergeTo.get(key));
        }
        return section;
    }

    public static /* synthetic */ ConfigurationSection mergeTo$default(ConfigurationSection configurationSection, ConfigurationSection configurationSection2, boolean bl, int n, Object object) {
        if ((n & 2) != 0) {
            bl = false;
        }
        return ConfigurationKt.mergeTo(configurationSection, configurationSection2, bl);
    }
}

