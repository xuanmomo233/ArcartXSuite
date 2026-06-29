/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.io.FileCreateKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.configuration.Type
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.ArraysKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.objective.Condition;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.taboolib.common.io.FileCreateKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.configuration.Type;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004J,\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\b\u0010\n\u001a\u0004\u0018\u00010\u000b2\b\b\u0002\u0010\f\u001a\u00020\rH\u0002J,\u0010\u000e\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\u000f\u001a\u00020\t2\n\u0010\u0010\u001a\u0006\u0012\u0002\b\u00030\u00112\u0006\u0010\u0012\u001a\u00020\tH\u0002J$\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u00072\n\u0010\u0014\u001a\u0006\u0012\u0002\b\u00030\u00152\u0006\u0010\u0016\u001a\u00020\tH\u0002J\u0014\u0010\u0017\u001a\u00020\t2\n\u0010\u0010\u001a\u0006\u0012\u0002\b\u00030\u0011H\u0002\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/core/quest/QuestDumper;", "", "()V", "dumpComponentsAPI", "", "dumpMetaInfoDetails", "dumpJson", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "path", "", "metaInfo", "Link/ptms/chemdah/core/quest/MetaInfo;", "includeParams", "", "dumpMetaOrAddonInfo", "id", "clazz", "Ljava/lang/Class;", "category", "dumpObjectiveInfo", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "dependPlugin", "getMetaInfoSource", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestDumper.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestDumper.kt\nink/ptms/chemdah/core/quest/QuestDumper\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 4 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n*L\n1#1,230:1\n215#2:231\n216#2:239\n215#2:240\n216#2:248\n215#2:249\n216#2:257\n215#2,2:258\n125#2:260\n152#2,3:261\n125#2:271\n152#2,3:272\n361#3,7:232\n361#3,7:241\n361#3,7:250\n515#3:264\n500#3,6:265\n12744#4:275\n12744#4,2:276\n12745#4:278\n12744#4:279\n12744#4,2:280\n12745#4:282\n12744#4:283\n12744#4,2:284\n12745#4:286\n11335#4:287\n11670#4,3:288\n*S KotlinDebug\n*F\n+ 1 QuestDumper.kt\nink/ptms/chemdah/core/quest/QuestDumper\n*L\n25#1:231\n25#1:239\n41#1:240\n41#1:248\n52#1:249\n52#1:257\n63#1:258,2\n96#1:260\n96#1:261,3\n100#1:271\n100#1:272,3\n33#1:232,7\n44#1:241,7\n55#1:250,7\n100#1:264\n100#1:265,6\n128#1:275\n129#1:276,2\n128#1:278\n131#1:279\n132#1:280,2\n131#1:282\n134#1:283\n135#1:284,2\n134#1:286\n206#1:287\n206#1:288,3\n*E\n"})
public final class QuestDumper {
    @NotNull
    public static final QuestDumper INSTANCE = new QuestDumper();

    private QuestDumper() {
    }

    public final void dumpComponentsAPI() {
        String id2;
        Configuration dumpJson;
        Configuration answer$iv;
        Object value$iv;
        boolean $i$f$getOrPut;
        Map $this$getOrPut$iv;
        String source;
        Map.Entry entry;
        Map.Entry element$iv;
        Map dumpJsonMap = new LinkedHashMap();
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestObjective();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = element$iv = iterator.next();
            boolean bl = false;
            Objective objective2 = (Objective)entry.getValue();
            try {
                Object object;
                String dependPlugin = objective2.getClass().isAnnotationPresent(Dependency.class) ? objective2.getClass().getAnnotation(Dependency.class).plugin() : "minecraft";
                source = INSTANCE.getMetaInfoSource(objective2.getClass());
                $this$getOrPut$iv = dumpJsonMap;
                $i$f$getOrPut = false;
                value$iv = $this$getOrPut$iv.get(source);
                if (value$iv == null) {
                    boolean bl2 = false;
                    answer$iv = Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, (Type)Type.JSON, (boolean)false, (int)2, null);
                    $this$getOrPut$iv.put(source, answer$iv);
                    object = answer$iv;
                } else {
                    object = value$iv;
                }
                dumpJson = (Configuration)object;
                INSTANCE.dumpObjectiveInfo(dumpJson, objective2, dependPlugin);
            }
            catch (Throwable dependPlugin) {
            }
        }
        $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestMeta();
        $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = element$iv = iterator.next();
            boolean bl = false;
            id2 = (String)entry.getKey();
            Class metaClass = (Class)entry.getValue();
            try {
                Object object;
                source = INSTANCE.getMetaInfoSource(metaClass);
                $this$getOrPut$iv = dumpJsonMap;
                $i$f$getOrPut = false;
                value$iv = $this$getOrPut$iv.get(source);
                if (value$iv == null) {
                    boolean bl3 = false;
                    answer$iv = Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, (Type)Type.JSON, (boolean)false, (int)2, null);
                    $this$getOrPut$iv.put(source, answer$iv);
                    object = answer$iv;
                } else {
                    object = value$iv;
                }
                dumpJson = (Configuration)object;
                INSTANCE.dumpMetaOrAddonInfo(dumpJson, id2, metaClass, "quest_meta");
            }
            catch (Throwable source2) {
            }
        }
        $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestAddon();
        $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = element$iv = iterator.next();
            boolean bl = false;
            id2 = (String)entry.getKey();
            Class addonClass = (Class)entry.getValue();
            try {
                Object object;
                source = INSTANCE.getMetaInfoSource(addonClass);
                $this$getOrPut$iv = dumpJsonMap;
                $i$f$getOrPut = false;
                value$iv = $this$getOrPut$iv.get(source);
                if (value$iv == null) {
                    boolean bl4 = false;
                    answer$iv = Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, (Type)Type.JSON, (boolean)false, (int)2, null);
                    $this$getOrPut$iv.put(source, answer$iv);
                    object = answer$iv;
                } else {
                    object = value$iv;
                }
                dumpJson = (Configuration)object;
                INSTANCE.dumpMetaOrAddonInfo(dumpJson, id2, addonClass, "quest_addon");
            }
            catch (Throwable source3) {
            }
        }
        $this$forEach$iv = dumpJsonMap;
        $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = element$iv = iterator.next();
            boolean bl = false;
            String source4 = (String)entry.getKey();
            Configuration json = (Configuration)entry.getValue();
            String fileName = Intrinsics.areEqual((Object)source4, (Object)"chemdah") ? "api-default.json" : "api-" + source4 + ".json";
            json.saveToFile(FileCreateKt.newFile$default((File)IOKt.getDataFolder(), (String)("/api/" + fileName), (boolean)true, (boolean)false, (int)8, null));
        }
    }

    private final String getMetaInfoSource(Class<?> clazz) {
        return clazz.isAnnotationPresent(MetaInfo.class) ? clazz.getAnnotation(MetaInfo.class).source() : "other";
    }

    /*
     * WARNING - void declaration
     */
    private final void dumpObjectiveInfo(Configuration dumpJson, Objective<?> objective2, String dependPlugin) {
        Map.Entry g;
        Map $this$filterTo$iv$iv;
        Object $this$filter$iv;
        Pair[] pairArray;
        Object object;
        void $this$mapTo$iv$iv;
        Map $this$map$iv;
        Map map = objective2.getConditions$Chemdah();
        String string = dependPlugin + ".objective." + objective2.getName() + ".condition";
        Configuration configuration = dumpJson;
        boolean $i$f$map = false;
        void var6_8 = $this$map$iv;
        Object destination$iv$iv = new ArrayList($this$map$iv.size());
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void c;
            Map.Entry item$iv$iv;
            Map.Entry entry = item$iv$iv = iterator.next();
            object = destination$iv$iv;
            boolean bl = false;
            pairArray = new Pair[]{TuplesKt.to((Object)"name", c.getKey()), TuplesKt.to((Object)"pattern", (Object)((Condition)c.getValue()).getPatternName())};
            object.add(MapsKt.mapOf((Pair[])pairArray));
        }
        object = (List)destination$iv$iv;
        configuration.set(string, object);
        dumpJson.set(dependPlugin + ".objective." + objective2.getName() + ".condition-vars", objective2.getConditionVars$Chemdah().keySet());
        $this$map$iv = objective2.getGoals$Chemdah();
        string = dependPlugin + ".objective." + objective2.getName() + ".goal";
        configuration = dumpJson;
        boolean $i$f$filter = false;
        $this$mapTo$iv$iv = $this$filter$iv;
        destination$iv$iv = new LinkedHashMap();
        boolean $i$f$filterTo = false;
        iterator = $this$filterTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv$iv;
            g = element$iv$iv = iterator.next();
            boolean bl = false;
            if (!(!Intrinsics.areEqual(g.getKey(), (Object)"null"))) continue;
            destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
        }
        $this$filter$iv = object = destination$iv$iv;
        $i$f$map = false;
        $this$filterTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList($this$map$iv.size());
        $i$f$mapTo = false;
        for (Map.Entry item$iv$iv : $this$mapTo$iv$iv.entrySet()) {
            g = item$iv$iv;
            object = destination$iv$iv;
            boolean bl = false;
            pairArray = new Pair[]{TuplesKt.to((Object)"name", g.getKey()), TuplesKt.to((Object)"pattern", (Object)((Condition)g.getValue()).getPatternName())};
            object.add(MapsKt.mapOf((Pair[])pairArray));
        }
        object = (List)destination$iv$iv;
        configuration.set(string, object);
        dumpJson.set(dependPlugin + ".objective." + objective2.getName() + ".goal-vars", objective2.getGoalVars$Chemdah().keySet());
        MetaInfo metaInfo = objective2.getClass().isAnnotationPresent(MetaInfo.class) ? objective2.getClass().getAnnotation(MetaInfo.class) : null;
        this.dumpMetaInfoDetails(dumpJson, dependPlugin + ".objective." + objective2.getName(), metaInfo, true);
    }

    private final void dumpMetaOrAddonInfo(Configuration dumpJson, String id2, Class<?> clazz, String category) {
        boolean bl;
        boolean hasTemplateConstructor;
        boolean hasTaskConstructor;
        block17: {
            boolean bl2;
            int element$iv2;
            Constructor<?>[] constructors;
            block15: {
                boolean bl3;
                int n;
                block13: {
                    constructors = clazz.getConstructors();
                    Intrinsics.checkNotNullExpressionValue(constructors, (String)"constructors");
                    Constructor<?>[] $this$any$iv = constructors;
                    boolean $i$f$any = false;
                    n = $this$any$iv.length;
                    for (int i = 0; i < n; ++i) {
                        boolean bl4;
                        block12: {
                            Constructor<?> element$iv2;
                            Constructor<?> constructor = element$iv2 = $this$any$iv[i];
                            boolean bl5 = false;
                            Class<?>[] classArray = constructor.getParameterTypes();
                            Intrinsics.checkNotNullExpressionValue(classArray, (String)"constructor.parameterTypes");
                            Object[] $this$any$iv2 = classArray;
                            boolean $i$f$any2 = false;
                            int n2 = $this$any$iv2.length;
                            for (int j = 0; j < n2; ++j) {
                                Object element$iv3 = $this$any$iv2[j];
                                Class it = (Class)element$iv3;
                                boolean bl6 = false;
                                if (!Intrinsics.areEqual((Object)it, Task.class)) continue;
                                bl4 = true;
                                break block12;
                            }
                            bl4 = false;
                        }
                        if (!bl4) continue;
                        bl3 = true;
                        break block13;
                    }
                    bl3 = false;
                }
                hasTaskConstructor = bl3;
                Constructor<?>[] $this$any$iv = constructors;
                boolean $i$f$any = false;
                element$iv2 = $this$any$iv.length;
                for (n = 0; n < element$iv2; ++n) {
                    boolean bl7;
                    block14: {
                        Constructor<?> element$iv4;
                        Constructor<?> constructor = element$iv4 = $this$any$iv[n];
                        boolean bl8 = false;
                        Class<?>[] classArray = constructor.getParameterTypes();
                        Intrinsics.checkNotNullExpressionValue(classArray, (String)"constructor.parameterTypes");
                        Object[] $this$any$iv3 = classArray;
                        boolean $i$f$any3 = false;
                        for (Object element$iv5 : $this$any$iv3) {
                            Class it = (Class)element$iv5;
                            boolean bl9 = false;
                            if (!Intrinsics.areEqual((Object)it, Template.class)) continue;
                            bl7 = true;
                            break block14;
                        }
                        bl7 = false;
                    }
                    if (!bl7) continue;
                    bl2 = true;
                    break block15;
                }
                bl2 = false;
            }
            hasTemplateConstructor = bl2;
            Constructor<?>[] $this$any$iv = constructors;
            boolean $i$f$any = false;
            int n = $this$any$iv.length;
            for (element$iv2 = 0; element$iv2 < n; ++element$iv2) {
                boolean bl10;
                block16: {
                    Constructor<?> element$iv6;
                    Constructor<?> constructor = element$iv6 = $this$any$iv[element$iv2];
                    boolean bl11 = false;
                    Class<?>[] classArray = constructor.getParameterTypes();
                    Intrinsics.checkNotNullExpressionValue(classArray, (String)"constructor.parameterTypes");
                    Object[] $this$any$iv4 = classArray;
                    boolean $i$f$any4 = false;
                    for (Object element$iv7 : $this$any$iv4) {
                        Class it = (Class)element$iv7;
                        boolean bl12 = false;
                        if (!Intrinsics.areEqual((Object)it, QuestContainer.class)) continue;
                        bl10 = true;
                        break block16;
                    }
                    bl10 = false;
                }
                if (!bl10) continue;
                bl = true;
                break block17;
            }
            bl = false;
        }
        boolean hasQuestContainerConstructor = bl;
        String option = clazz.isAnnotationPresent(Option.class) ? clazz.getAnnotation(Option.class).type().name() : "ANY";
        MetaInfo metaInfo = clazz.isAnnotationPresent(MetaInfo.class) ? clazz.getAnnotation(MetaInfo.class) : null;
        if (hasTaskConstructor && !hasTemplateConstructor && !hasQuestContainerConstructor) {
            String actualCategory = StringsKt.startsWith$default((String)category, (String)"quest_meta", (boolean)false, (int)2, null) ? "task_meta" : "task_addon";
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".option_type", (Object)option);
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".class", (Object)clazz.getName());
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".scope", (Object)"task_only");
            QuestDumper.dumpMetaInfoDetails$default(this, dumpJson, "minecraft." + actualCategory + '.' + id2, metaInfo, false, 8, null);
        } else if (hasTemplateConstructor && !hasTaskConstructor && !hasQuestContainerConstructor) {
            String actualCategory = StringsKt.startsWith$default((String)category, (String)"quest_meta", (boolean)false, (int)2, null) ? "quest_meta" : "quest_addon";
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".option_type", (Object)option);
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".class", (Object)clazz.getName());
            dumpJson.set("minecraft." + actualCategory + '.' + id2 + ".scope", (Object)"quest_only");
            QuestDumper.dumpMetaInfoDetails$default(this, dumpJson, "minecraft." + actualCategory + '.' + id2, metaInfo, false, 8, null);
        } else if (hasQuestContainerConstructor) {
            String baseCategory = StringsKt.startsWith$default((String)category, (String)"quest_meta", (boolean)false, (int)2, null) ? "meta" : "addon";
            dumpJson.set("minecraft." + baseCategory + '.' + id2 + ".option_type", (Object)option);
            dumpJson.set("minecraft." + baseCategory + '.' + id2 + ".class", (Object)clazz.getName());
            dumpJson.set("minecraft." + baseCategory + '.' + id2 + ".scope", (Object)"both");
            QuestDumper.dumpMetaInfoDetails$default(this, dumpJson, "minecraft." + baseCategory + '.' + id2, metaInfo, false, 8, null);
        } else {
            dumpJson.set("minecraft." + category + '.' + id2 + ".option_type", (Object)option);
            dumpJson.set("minecraft." + category + '.' + id2 + ".class", (Object)clazz.getName());
            dumpJson.set("minecraft." + category + '.' + id2 + ".scope", (Object)"unknown");
            QuestDumper.dumpMetaInfoDetails$default(this, dumpJson, "minecraft." + category + '.' + id2, metaInfo, false, 8, null);
        }
    }

    /*
     * WARNING - void declaration
     */
    private final void dumpMetaInfoDetails(Configuration dumpJson, String path, MetaInfo metaInfo, boolean includeParams) {
        if (metaInfo != null) {
            dumpJson.set(path + ".name", (Object)metaInfo.name());
            dumpJson.set(path + ".description", (Object)ArraysKt.toList((Object[])metaInfo.description()));
            dumpJson.set(path + ".alias", (Object)ArraysKt.toList((Object[])metaInfo.alias()));
            if (includeParams && !(metaInfo.params().length == 0)) {
                Collection<Map> collection;
                void $this$mapTo$iv$iv;
                void $this$map$iv;
                ParamInfo[] paramInfoArray = metaInfo.params();
                String string = path + ".params";
                Configuration configuration = dumpJson;
                boolean $i$f$map = false;
                void var7_10 = $this$map$iv;
                Collection destination$iv$iv = new ArrayList(((void)$this$map$iv).length);
                boolean $i$f$mapTo = false;
                int n = ((void)$this$mapTo$iv$iv).length;
                for (int i = 0; i < n; ++i) {
                    void param;
                    void item$iv$iv;
                    void var13_16 = item$iv$iv = $this$mapTo$iv$iv[i];
                    collection = destination$iv$iv;
                    boolean bl = false;
                    Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)param.name()), TuplesKt.to((Object)"type", (Object)param.type()), TuplesKt.to((Object)"required", (Object)param.required()), TuplesKt.to((Object)"options", (Object)ArraysKt.toList((Object[])param.options())), TuplesKt.to((Object)"description", (Object)param.description())};
                    collection.add(MapsKt.mapOf((Pair[])pairArray));
                }
                collection = (List)destination$iv$iv;
                configuration.set(string, (Object)collection);
            }
        } else if (includeParams) {
            Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)StringsKt.substringAfterLast$default((String)path, (char)'.', null, (int)2, null)), TuplesKt.to((Object)"type", (Object)"any"), TuplesKt.to((Object)"required", (Object)false), TuplesKt.to((Object)"options", (Object)CollectionsKt.emptyList()), TuplesKt.to((Object)"description", (Object)"\u672a\u63d0\u4f9b\u8be6\u7ec6\u6587\u6863\u4fe1\u606f")};
            dumpJson.set(path + ".params", (Object)CollectionsKt.listOf((Object)MapsKt.mapOf((Pair[])pairArray)));
        }
    }

    static /* synthetic */ void dumpMetaInfoDetails$default(QuestDumper questDumper, Configuration configuration, String string, MetaInfo metaInfo, boolean bl, int n, Object object) {
        if ((n & 8) != 0) {
            bl = true;
        }
        questDumper.dumpMetaInfoDetails(configuration, string, metaInfo, bl);
    }
}

