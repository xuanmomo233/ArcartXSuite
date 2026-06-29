/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.PlatformFactory
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformIO
 *  ink.ptms.chemdah.taboolib.module.metrics.CustomChart
 *  ink.ptms.chemdah.taboolib.module.metrics.Metrics
 *  ink.ptms.chemdah.taboolib.module.metrics.charts.AdvancedPie
 *  ink.ptms.chemdah.taboolib.module.metrics.charts.SimplePie
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.database.Type;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.party.PartySystem;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.Platform;
import ink.ptms.chemdah.taboolib.common.platform.PlatformFactory;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformIO;
import ink.ptms.chemdah.taboolib.module.metrics.CustomChart;
import ink.ptms.chemdah.taboolib.module.metrics.charts.AdvancedPie;
import ink.ptms.chemdah.taboolib.module.metrics.charts.SimplePie;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/Metrics;", "", "()V", "init", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nMetrics.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Metrics.kt\nink/ptms/chemdah/module/Metrics\n+ 2 Plugin.kt\ntaboolib/common/platform/function/PluginKt\n+ 3 PlatformFactory.kt\ntaboolib/common/platform/PlatformFactory\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 5 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,57:1\n16#2:58\n137#3:59\n215#4:60\n215#4,2:61\n216#4:63\n215#4:64\n215#4:65\n216#4:68\n216#4:69\n215#4:70\n215#4:71\n216#4:74\n216#4:75\n1855#5,2:66\n1855#5,2:72\n*S KotlinDebug\n*F\n+ 1 Metrics.kt\nink/ptms/chemdah/module/Metrics\n*L\n18#1:58\n18#1:59\n21#1:60\n22#1:61,2\n21#1:63\n30#1:64\n31#1:65\n31#1:68\n30#1:69\n41#1:70\n42#1:71\n42#1:74\n41#1:75\n32#1:66,2\n43#1:72,2\n*E\n"})
public final class Metrics {
    @NotNull
    public static final Metrics INSTANCE = new Metrics();

    private Metrics() {
    }

    @Awake(value=LifeCycle.ENABLE)
    public final void init() {
        boolean $i$f$getPluginVersion = false;
        PlatformFactory this_$iv$iv = PlatformFactory.INSTANCE;
        boolean $i$f$getService = false;
        String string = PlatformIO.class.getName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"T::class.java.name");
        ink.ptms.chemdah.taboolib.module.metrics.Metrics metrics = new ink.ptms.chemdah.taboolib.module.metrics.Metrics(11183, ((PlatformIO)this_$iv$iv.getService(string)).getPluginVersion(), Platform.BUKKIT);
        metrics.addCustomChart((CustomChart)new AdvancedPie("objectives", Metrics::init$lambda$3));
        metrics.addCustomChart((CustomChart)new AdvancedPie("addon", Metrics::init$lambda$8));
        metrics.addCustomChart((CustomChart)new AdvancedPie("agent", Metrics::init$lambda$13));
        metrics.addCustomChart((CustomChart)new SimplePie("database", Metrics::init$lambda$14));
        metrics.addCustomChart((CustomChart)new SimplePie("party_hook", Metrics::init$lambda$15));
    }

    private static final Map init$lambda$3() {
        HashMap hashMap;
        HashMap map = hashMap = new HashMap();
        boolean bl = false;
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry template = element$iv = iterator.next();
            boolean bl2 = false;
            Map $this$forEach$iv2 = ((Template)template.getValue()).getTaskMap();
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry element$iv2;
                Map.Entry it = element$iv2 = iterator2.next();
                boolean bl3 = false;
                Map map2 = map;
                String string = ((Task)it.getValue()).getObjective().getName();
                Integer n = (Integer)map.get(((Task)it.getValue()).getObjective().getName());
                if (n == null) {
                    n = 0;
                }
                Integer n2 = n + 1;
                map2.put(string, n2);
            }
        }
        return hashMap;
    }

    private static final Map init$lambda$8() {
        HashMap hashMap;
        HashMap map = hashMap = new HashMap();
        boolean bl = false;
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry template = element$iv = iterator.next();
            boolean bl2 = false;
            Map $this$forEach$iv2 = ((Template)template.getValue()).getTaskMap();
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry element$iv2;
                Map.Entry task = element$iv2 = iterator2.next();
                boolean bl3 = false;
                Set<String> set2 = ((Task)task.getValue()).getAddonMap().keySet();
                Intrinsics.checkNotNullExpressionValue(set2, (String)"task.value.addonMap.keys");
                Iterable $this$forEach$iv3 = set2;
                boolean $i$f$forEach3 = false;
                for (Object element$iv3 : $this$forEach$iv3) {
                    String it = (String)element$iv3;
                    boolean bl4 = false;
                    Map map2 = map;
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    String string = it;
                    Integer n = (Integer)map.get(it);
                    if (n == null) {
                        n = 0;
                    }
                    Integer n2 = n + 1;
                    map2.put(string, n2);
                }
            }
        }
        return hashMap;
    }

    private static final Map init$lambda$13() {
        HashMap hashMap;
        HashMap map = hashMap = new HashMap();
        boolean bl = false;
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry template = element$iv = iterator.next();
            boolean bl2 = false;
            Map $this$forEach$iv2 = ((Template)template.getValue()).getTaskMap();
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry element$iv2;
                Map.Entry task = element$iv2 = iterator2.next();
                boolean bl3 = false;
                Iterable $this$forEach$iv3 = ((Task)task.getValue()).getAgents();
                boolean $i$f$forEach3 = false;
                for (Object element$iv3 : $this$forEach$iv3) {
                    String it = (String)element$iv3;
                    boolean bl4 = false;
                    Map map2 = map;
                    Integer n = (Integer)map.get(it);
                    if (n == null) {
                        n = 0;
                    }
                    Integer n2 = n + 1;
                    map2.put(it, n2);
                }
            }
        }
        return hashMap;
    }

    private static final String init$lambda$14() {
        return Type.Companion.getINSTANCE().name();
    }

    private static final String init$lambda$15() {
        String string = PartySystem.INSTANCE.getConf().getString("default.plugin", "");
        Intrinsics.checkNotNull((Object)string);
        return string;
    }
}

