/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.api.event.collect.TemplateEvents;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.core.quest.addon.data.ControlAgent;
import ink.ptms.chemdah.core.quest.addon.data.ControlCoexist;
import ink.ptms.chemdah.core.quest.addon.data.ControlCooldown;
import ink.ptms.chemdah.core.quest.addon.data.ControlOperator;
import ink.ptms.chemdah.core.quest.addon.data.ControlRepeat;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.TimeCycle;
import ink.ptms.chemdah.taboolib.common5.util.String2TimeCycleKt;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import ink.ptms.chemdah.util.NumberKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Id(id="control")
@Option(type=Option.Type.MAP_LIST)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u00112\u00020\u0001:\u0001\u0011B'\u0012\u0018\u0010\u0002\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00040\u0003\u0012\u0006\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tR \u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonControl;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "", "", "", "", "template", "Link/ptms/chemdah/core/quest/Template;", "(Ljava/util/List;Link/ptms/chemdah/core/quest/Template;)V", "control", "", "Link/ptms/chemdah/core/quest/addon/data/Control;", "getControl", "()Ljava/util/List;", "setControl", "(Ljava/util/List;)V", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonControl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonControl.kt\nink/ptms/chemdah/core/quest/addon/AddonControl\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,102:1\n1603#2,9:103\n1855#2:112\n1856#2:118\n1612#2:119\n125#3:113\n152#3,3:114\n1#4:117\n*S KotlinDebug\n*F\n+ 1 AddonControl.kt\nink/ptms/chemdah/core/quest/addon/AddonControl\n*L\n62#1:103,9\n62#1:112\n62#1:118\n62#1:119\n72#1:113\n72#1:114,3\n62#1:117\n*E\n"})
public final class AddonControl
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private List<Control> control;

    /*
     * WARNING - void declaration
     */
    public AddonControl(@NotNull List<? extends Map<String, ? extends Object>> root2, @NotNull Template template) {
        void $this$mapNotNullTo$iv$iv;
        void $this$mapNotNull$iv;
        Intrinsics.checkNotNullParameter(root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        super(root2, template);
        Iterable iterable = root2;
        AddonControl addonControl = this;
        boolean $i$f$mapNotNull = false;
        void var5_6 = $this$mapNotNull$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$mapNotNullTo = false;
        void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv$iv$iv.iterator();
        while (iterator.hasNext()) {
            Control control;
            Object element$iv$iv$iv;
            Object element$iv$iv = element$iv$iv$iv = iterator.next();
            boolean bl = false;
            Map map = (Map)element$iv$iv;
            boolean bl2 = false;
            if (map.get("$") != null) {
                Object v = map.get("$");
                Intrinsics.checkNotNull(v);
                control = new ControlAgent(CollectionKt.asList(v));
            } else {
                ControlTrigger trigger2;
                String type;
                Intrinsics.checkNotNullExpressionValue((Object)String.valueOf(map.get("type")).toLowerCase(Locale.ROOT), (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                if (Intrinsics.areEqual((Object)type, (Object)"coexist")) {
                    void $this$mapTo$iv$iv;
                    Map $this$map$iv = SectionsKt.asMap(map.get("amount"));
                    boolean $i$f$map = false;
                    Map map2 = $this$map$iv;
                    Collection destination$iv$iv2 = new ArrayList($this$map$iv.size());
                    boolean $i$f$mapTo = false;
                    Iterator iterator2 = $this$mapTo$iv$iv.entrySet().iterator();
                    while (iterator2.hasNext()) {
                        void it;
                        Map.Entry item$iv$iv;
                        Map.Entry entry = item$iv$iv = iterator2.next();
                        Collection collection = destination$iv$iv2;
                        boolean bl3 = false;
                        collection.add(TuplesKt.to(it.getKey(), (Object)NumberKt.asInt$default(it.getValue(), 0, 1, null)));
                    }
                    Map map3 = MapsKt.toMap((Iterable)((List)destination$iv$iv2));
                    control = new ControlCoexist(map3);
                } else if (StringsKt.startsWith$default((String)type, (String)"repeat", (boolean)false, (int)2, null)) {
                    Object v;
                    String string = type.substring(6);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
                    trigger2 = ControlTrigger.Companion.fromName(((Object)StringsKt.trim((CharSequence)string)).toString());
                    Object v2 = map.get("group");
                    control = new ControlRepeat(trigger2, NumberKt.asInt$default(map.get("amount"), 0, 1, null), (v = map.get("period")) != null && (v = v.toString()) != null ? String2TimeCycleKt.parseTimeCycle(v) : null, v2 != null ? v2.toString() : null);
                } else if (StringsKt.startsWith$default((String)type, (String)"cooldown", (boolean)false, (int)2, null)) {
                    String string = type.substring(8);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
                    trigger2 = ControlTrigger.Companion.fromName(((Object)StringsKt.trim((CharSequence)string)).toString());
                    Object v = map.get("time");
                    if (v == null || (v = v.toString()) == null || (v = String2TimeCycleKt.parseTimeCycle(v)) == null) {
                        Object var27_29 = null;
                        control = var27_29;
                    } else {
                        ControlTrigger controlTrigger;
                        Object v3 = map.get("group");
                        String string2 = v3 != null ? v3.toString() : null;
                        Object v4 = v;
                        control = new ControlCooldown(controlTrigger, (TimeCycle)v4, string2);
                    }
                } else {
                    QuestContainer questContainer = this.getQuestContainer();
                    Intrinsics.checkNotNull((Object)questContainer, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.Template");
                    TemplateEvents.ControlHook event = new TemplateEvents.ControlHook((Template)questContainer, type, map);
                    event.call();
                    if (event.getControl() != null) {
                        Object[] objectArray = new Object[]{"Unrecognized control format: " + type + ' ' + map};
                        IOKt.warning((Object[])objectArray);
                    }
                    control = event.getControl();
                }
            }
            if (control == null) continue;
            Control it$iv$iv = control;
            boolean bl4 = false;
            destination$iv$iv.add(it$iv$iv);
        }
        addonControl.control = CollectionsKt.toMutableList((Collection)((List)destination$iv$iv));
    }

    @NotNull
    public final List<Control> getControl() {
        return this.control;
    }

    public final void setControl(@NotNull List<Control> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.control = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonControl$Companion;", "", "()V", "control", "Link/ptms/chemdah/core/quest/addon/data/ControlOperator;", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final ControlOperator control(@NotNull Template $this$control) {
            Intrinsics.checkNotNullParameter((Object)$this$control, (String)"<this>");
            AddonControl addonControl = (AddonControl)$this$control.addon("control");
            return new ControlOperator($this$control, addonControl != null ? addonControl.getControl() : null);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

