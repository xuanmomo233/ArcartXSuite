/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.QuestDataOperator;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.other.INever;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000H\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eJ\u000e\u0010\u001f\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eJ\u0016\u0010 \u001a\u00020!2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\"\u001a\u00020\u001cR\u001a\u0010\t\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\u000f\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\f\"\u0004\b\u0011\u0010\u000eR\"\u0010\u0012\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00140\u0013X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a\u00a8\u0006#"}, d2={"Link/ptms/chemdah/core/quest/Task;", "Link/ptms/chemdah/core/quest/QuestContainer;", "id", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "template", "Link/ptms/chemdah/core/quest/Template;", "(Ljava/lang/String;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/Template;)V", "condition", "Link/ptms/chemdah/core/DataContainer;", "getCondition", "()Link/ptms/chemdah/core/DataContainer;", "setCondition", "(Link/ptms/chemdah/core/DataContainer;)V", "goal", "getGoal", "setGoal", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "setObjective", "(Link/ptms/chemdah/core/quest/objective/Objective;)V", "getTemplate", "()Link/ptms/chemdah/core/quest/Template;", "isClosed", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "isCompleted", "setClosed", "", "value", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTask.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Task.kt\nink/ptms/chemdah/core/quest/Task\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,74:1\n215#2:75\n216#2:80\n215#2:81\n216#2:86\n1549#3:76\n1620#3,3:77\n1549#3:82\n1620#3,3:83\n*S KotlinDebug\n*F\n+ 1 Task.kt\nink/ptms/chemdah/core/quest/Task\n*L\n43#1:75\n43#1:80\n51#1:81\n51#1:86\n46#1:76\n46#1:77,3\n54#1:82\n54#1:83,3\n*E\n"})
public class Task
extends QuestContainer {
    @NotNull
    private final Template template;
    @NotNull
    private Objective<? extends Object> objective;
    @NotNull
    private DataContainer condition;
    @NotNull
    private DataContainer goal;

    public Task(@NotNull String id2, @NotNull ConfigurationSection config, @NotNull Template template) {
        block11: {
            Object object;
            String it;
            Collection<String> collection;
            Object item$iv$iv;
            Iterator iterator;
            char[] $this$mapTo$iv$iv;
            boolean $i$f$mapTo;
            Collection destination$iv$iv;
            Object $this$map$iv;
            boolean $i$f$map;
            DataContainer dataContainer;
            String string;
            Object v;
            String k;
            Map.Entry entry;
            Map.Entry element$iv;
            Iterator iterator2;
            boolean $i$f$forEach;
            Object $this$forEach$iv;
            Objective objective2;
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            Intrinsics.checkNotNullParameter((Object)template, (String)"template");
            super(id2, config);
            this.template = template;
            if (config.contains("objective")) {
                String string2 = config.getString("objective");
                Intrinsics.checkNotNull((Object)string2);
                Objective find = ChemdahAPI.INSTANCE.getQuestObjective(string2);
                if (find == null) {
                    Object[] objectArray = new Object[]{"[Task " + this.template.getId() + '.' + id2 + "] \u5b9a\u4e49\u4e86\u65e0\u6548\u76ee\u6807: " + config.getString("objective")};
                    IOKt.warning((Object[])objectArray);
                    objectArray = new Object[]{"\u53ef\u7528: " + ChemdahAPI.INSTANCE.getQuestObjective().keySet()};
                    IOKt.warning((Object[])objectArray);
                    objective2 = INever.INSTANCE;
                } else {
                    objective2 = find;
                }
            } else {
                Object[] objectArray = new Object[]{"[Task " + this.template.getId() + '.' + id2 + "] \u672a\u5b9a\u4e49\u76ee\u6807"};
                IOKt.warning((Object[])objectArray);
                objective2 = INever.INSTANCE;
            }
            this.objective = objective2;
            this.condition = new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY());
            this.goal = new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY());
            Object object2 = config.getConfigurationSection("condition");
            if (object2 != null && (object2 = object2.toMap()) != null) {
                $this$forEach$iv = object2;
                $i$f$forEach = false;
                iterator2 = $this$forEach$iv.entrySet().iterator();
                while (iterator2.hasNext()) {
                    entry = element$iv = iterator2.next();
                    boolean bl = false;
                    k = (String)entry.getKey();
                    v = entry.getValue();
                    if (v instanceof String && StringsKt.contains$default((CharSequence)((CharSequence)v), (char)';', (boolean)false, (int)2, null)) {
                        Object object3 = new char[]{';'};
                        object3 = StringsKt.split$default((CharSequence)((CharSequence)v), (char[])object3, (boolean)false, (int)0, (int)6, null);
                        string = k;
                        dataContainer = this.condition;
                        $i$f$map = false;
                        char[] cArray = $this$map$iv;
                        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                        $i$f$mapTo = false;
                        iterator = $this$mapTo$iv$iv.iterator();
                        while (iterator.hasNext()) {
                            item$iv$iv = iterator.next();
                            String string3 = (String)item$iv$iv;
                            collection = destination$iv$iv;
                            boolean bl2 = false;
                            collection.add(((Object)StringsKt.trim((CharSequence)it)).toString());
                        }
                        collection = (List)destination$iv$iv;
                        dataContainer.set(string, collection);
                        continue;
                    }
                    Object v2 = v;
                    Intrinsics.checkNotNull(v2);
                    this.condition.set(k, v2);
                }
            }
            if ((object = config.getConfigurationSection("goal")) == null || (object = object.toMap()) == null) break block11;
            $this$forEach$iv = object;
            $i$f$forEach = false;
            iterator2 = $this$forEach$iv.entrySet().iterator();
            while (iterator2.hasNext()) {
                entry = element$iv = iterator2.next();
                boolean bl = false;
                k = (String)entry.getKey();
                v = entry.getValue();
                if (v instanceof String && StringsKt.contains$default((CharSequence)((CharSequence)v), (char)';', (boolean)false, (int)2, null)) {
                    $this$map$iv = new char[]{';'};
                    $this$map$iv = StringsKt.split$default((CharSequence)((CharSequence)v), (char[])$this$map$iv, (boolean)false, (int)0, (int)6, null);
                    string = k;
                    dataContainer = this.goal;
                    $i$f$map = false;
                    $this$mapTo$iv$iv = $this$map$iv;
                    destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    $i$f$mapTo = false;
                    iterator = $this$mapTo$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        item$iv$iv = iterator.next();
                        it = (String)item$iv$iv;
                        collection = destination$iv$iv;
                        boolean bl3 = false;
                        collection.add(((Object)StringsKt.trim((CharSequence)it)).toString());
                    }
                    collection = (List)destination$iv$iv;
                    dataContainer.set(string, collection);
                    continue;
                }
                Object v3 = v;
                Intrinsics.checkNotNull(v3);
                this.goal.set(k, v3);
            }
        }
    }

    @NotNull
    public final Template getTemplate() {
        return this.template;
    }

    @NotNull
    public final Objective<? extends Object> getObjective() {
        return this.objective;
    }

    public final void setObjective(@NotNull Objective<? extends Object> objective2) {
        Intrinsics.checkNotNullParameter(objective2, (String)"<set-?>");
        this.objective = objective2;
    }

    @NotNull
    public final DataContainer getCondition() {
        return this.condition;
    }

    public final void setCondition(@NotNull DataContainer dataContainer) {
        Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"<set-?>");
        this.condition = dataContainer;
    }

    @NotNull
    public final DataContainer getGoal() {
        return this.goal;
    }

    public final void setGoal(@NotNull DataContainer dataContainer) {
        Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"<set-?>");
        this.goal = dataContainer;
    }

    public final boolean isCompleted(@NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        return this.objective.hasCompletedSignature(profile, this);
    }

    public final boolean isClosed(@NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        QuestDataOperator operator = profile.dataOperator(this);
        if (operator.getQuest() == null) {
            return false;
        }
        return operator.isTrue("closed");
    }

    public final void setClosed(@NotNull PlayerProfile profile, boolean value2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        profile.dataOperator(this).set("closed", value2);
    }
}

