/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.TimeCycle
 *  ink.ptms.chemdah.taboolib.common5.util.String2TimeCycleKt
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt
 *  kotlin.Metadata
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.api.event.collect.TemplateEvents;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.ParamInfo;
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
@MetaInfo(name="\u4efb\u52a1\u7ba1\u63a7\u7ec4\u4ef6", description={"\u7528\u4e8e\u7ba1\u7406\u4efb\u52a1\u7684\u5404\u79cd\u9650\u5236\u6761\u4ef6", "\u652f\u6301\u5171\u5b58\u3001\u91cd\u590d\u3001\u51b7\u5374\u9650\u5236\u548c\u811a\u672c\u4ee3\u7406", "\u914d\u7f6e\u4e3a Map \u5217\u8868\u7ed3\u6784\uff0c\u6bcf\u4e2a Map \u4ee3\u8868\u4e00\u4e2a\u7ba1\u63a7\u89c4\u5219"}, alias={"\u9650\u5236", "\u51b7\u5374", "\u6b21\u6570\u9650\u5236"}, params={@ParamInfo(name="control[]", type="map", required=true, description="\u7ba1\u63a7\u89c4\u5219\u5217\u8868\uff0c\u6bcf\u9879\u4e3a\u4e00\u4e2a\u7ba1\u63a7\u89c4\u5219\u7684\u914d\u7f6e Map"), @ParamInfo(name="control[].type", type="string", required=false, options={"coexist", "repeat", "cooldown"}, description="\u7ba1\u63a7\u7c7b\u578b\uff1acoexist(\u5171\u5b58)/repeat(\u91cd\u590d)/cooldown(\u51b7\u5374)"), @ParamInfo(name="control[].$", type="list", required=false, options={"kether"}, description="Kether \u811a\u672c\u4ee3\u7406\u5217\u8868\uff0c\u7528\u4e8e\u81ea\u5b9a\u4e49\u9650\u5236\u6761\u4ef6"), @ParamInfo(name="control[].amount", type="any", required=false, description="\u6570\u91cf\u914d\u7f6e\uff1aMap(\u5171\u5b58\u9650\u5236\u7684daily/weekly\u7b49) \u6216 Number(\u91cd\u590d\u9650\u5236\u7684\u6b21\u6570)"), @ParamInfo(name="control[].period", type="duration", required=false, description="\u91cd\u590d\u9650\u5236\u7684\u5468\u671f\uff0c\u683c\u5f0f\uff1aday 4 0"), @ParamInfo(name="control[].time", type="duration", required=false, description="\u51b7\u5374\u9650\u5236\u7684\u65f6\u95f4\uff0c\u683c\u5f0f\uff1a1h30m"), @ParamInfo(name="control[].group", type="string", required=false, description="\u7ba1\u63a7\u5206\u7ec4\u540d\u79f0\uff0c\u7528\u4e8e\u91cd\u590d\u548c\u51b7\u5374\u9650\u5236")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u00112\u00020\u0001:\u0001\u0011B'\u0012\u0018\u0010\u0002\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00040\u0003\u0012\u0006\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tR \u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonControl;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "", "", "", "", "template", "Link/ptms/chemdah/core/quest/Template;", "(Ljava/util/List;Link/ptms/chemdah/core/quest/Template;)V", "control", "", "Link/ptms/chemdah/core/quest/addon/data/Control;", "getControl", "()Ljava/util/List;", "setControl", "(Ljava/util/List;)V", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonControl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonControl.kt\nink/ptms/chemdah/core/quest/addon/AddonControl\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,119:1\n1603#2,9:120\n1855#2:129\n1856#2:135\n1612#2:136\n125#3:130\n152#3,3:131\n1#4:134\n*S KotlinDebug\n*F\n+ 1 AddonControl.kt\nink/ptms/chemdah/core/quest/addon/AddonControl\n*L\n78#1:120,9\n78#1:129\n78#1:135\n78#1:136\n88#1:130\n88#1:131,3\n78#1:134\n*E\n"})
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
                    if (event.getControl() == null) {
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

