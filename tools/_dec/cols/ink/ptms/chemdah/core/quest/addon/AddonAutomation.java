/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon;

import com.google.common.base.Enums;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.addon.data.Plan;
import ink.ptms.chemdah.core.quest.addon.data.PlanGroup;
import ink.ptms.chemdah.core.quest.addon.data.PlanType;
import ink.ptms.chemdah.core.quest.addon.data.PlanTypeDaily;
import ink.ptms.chemdah.core.quest.addon.data.PlanTypeHour;
import ink.ptms.chemdah.core.quest.addon.data.PlanTypeWeekly;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.RealTime;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.lang.LangKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.Ref;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.random.Random;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="automation")
@Option(type=Option.Type.SECTION)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0007\u0018\u0000 \u00182\u00020\u0001:\u0001\u0018B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0007\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\t\"\u0004\b\n\u0010\u000bR\u001c\u0010\f\u001a\u0004\u0018\u00010\rX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000f\"\u0004\b\u0010\u0010\u0011R\u001c\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonAutomation;", "Link/ptms/chemdah/core/quest/addon/Addon;", "source", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/QuestContainer;)V", "isAutoAccept", "", "()Z", "setAutoAccept", "(Z)V", "plan", "Link/ptms/chemdah/core/quest/addon/data/Plan;", "getPlan", "()Link/ptms/chemdah/core/quest/addon/data/Plan;", "setPlan", "(Link/ptms/chemdah/core/quest/addon/data/Plan;)V", "planGroup", "", "getPlanGroup", "()Ljava/lang/String;", "setPlanGroup", "(Ljava/lang/String;)V", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonAutomation.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonAutomation.kt\nink/ptms/chemdah/core/quest/addon/AddonAutomation\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,190:1\n8#2:191\n8#2:192\n8#2:193\n8#2:194\n8#2:195\n8#2:196\n8#2:197\n8#2:198\n*S KotlinDebug\n*F\n+ 1 AddonAutomation.kt\nink/ptms/chemdah/core/quest/addon/AddonAutomation\n*L\n72#1:191\n78#1:192\n79#1:193\n80#1:194\n86#1:195\n87#1:196\n88#1:197\n89#1:198\n*E\n"})
public final class AddonAutomation
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private boolean isAutoAccept;
    @Nullable
    private Plan plan;
    @Nullable
    private String planGroup;

    public AddonAutomation(@NotNull ConfigurationSection source, @NotNull QuestContainer questContainer) {
        Plan plan;
        block32: {
            block30: {
                PlanType type;
                Intrinsics.checkNotNullParameter((Object)source, (String)"source");
                Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
                super(source, questContainer);
                this.isAutoAccept = source.getBoolean("auto-accept");
                if (!source.contains("plan")) break block30;
                String string = String.valueOf(source.getString("plan.method")).toUpperCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
                RealTime method = (RealTime)Enums.getIfPresent(RealTime.class, (String)string).or((Object)RealTime.START_IN_MONDAY);
                String string2 = String.valueOf(source.getString("plan.type")).toLowerCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                String[] stringArray = new String[]{" "};
                List args = StringsKt.split$default((CharSequence)string2, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
                switch ((Object[])args.get(0)) {
                    case "hour": {
                        Intrinsics.checkNotNullExpressionValue((Object)method, (String)"method");
                        Object $this$cint$iv2 = args.get(1);
                        boolean $i$f$getCint2 = false;
                        PlanType planType = new PlanTypeHour(method, RealTime.Type.HOUR, Coerce.toInteger($this$cint$iv2));
                        break;
                    }
                    case "daily": 
                    case "day": {
                        int n;
                        int n2;
                        boolean $i$f$getCint;
                        String $this$cint$iv;
                        Intrinsics.checkNotNullExpressionValue((Object)method, (String)"method");
                        Object $this$cint$iv2 = args.get(1);
                        boolean $i$f$getCint2 = false;
                        int n3 = Coerce.toInteger($this$cint$iv2);
                        String string3 = (String)CollectionsKt.getOrNull((List)args, (int)2);
                        if (string3 != null) {
                            $this$cint$iv = string3;
                            $i$f$getCint = false;
                            n2 = Coerce.toInteger((Object)$this$cint$iv);
                        } else {
                            n2 = 6;
                        }
                        String string4 = (String)CollectionsKt.getOrNull((List)args, (int)3);
                        if (string4 != null) {
                            $this$cint$iv = string4;
                            $i$f$getCint = false;
                            n = Coerce.toInteger((Object)$this$cint$iv);
                        } else {
                            n = 0;
                        }
                        PlanType planType = new PlanTypeDaily(method, RealTime.Type.DAY, n3, n2, n);
                        break;
                    }
                    case "week": 
                    case "weekly": {
                        int n;
                        int n4;
                        int n5;
                        boolean $i$f$getCint;
                        String $this$cint$iv;
                        Intrinsics.checkNotNullExpressionValue((Object)method, (String)"method");
                        Object $this$cint$iv2 = args.get(1);
                        boolean $i$f$getCint2 = false;
                        int n6 = Coerce.toInteger($this$cint$iv2);
                        String string5 = (String)CollectionsKt.getOrNull((List)args, (int)2);
                        if (string5 != null) {
                            $this$cint$iv = string5;
                            $i$f$getCint = false;
                            n5 = Coerce.toInteger((Object)$this$cint$iv);
                        } else {
                            n5 = 6;
                        }
                        String string6 = (String)CollectionsKt.getOrNull((List)args, (int)3);
                        if (string6 != null) {
                            $this$cint$iv = string6;
                            $i$f$getCint = false;
                            n4 = Coerce.toInteger((Object)$this$cint$iv);
                        } else {
                            n4 = 0;
                        }
                        String string7 = (String)CollectionsKt.getOrNull((List)args, (int)4);
                        if (string7 != null) {
                            $this$cint$iv = string7;
                            $i$f$getCint = false;
                            n = Coerce.toInteger((Object)$this$cint$iv);
                        } else {
                            n = 0;
                        }
                        PlanType planType = new PlanTypeWeekly(method, RealTime.Type.WEEK, n6, n5, n4, n);
                        break;
                    }
                    default: {
                        PlanType planType = type = null;
                    }
                }
                if (type != null) {
                    if (type.getValue() == 0) {
                        type.setValue(1);
                        Object[] objectArray = new Object[]{questContainer.getId(), source};
                        LangKt.sendLang((ProxyCommandSender)AdapterKt.console(), (String)"console-automation-plan-error", (Object[])objectArray);
                    }
                    plan = new Plan(type, source.getInt("plan.count", 1), source.getString("plan.group"));
                } else {
                    plan = null;
                }
                break block32;
            }
            plan = null;
        }
        this.plan = plan;
        this.planGroup = source.getString("plan.group");
    }

    public final boolean isAutoAccept() {
        return this.isAutoAccept;
    }

    public final void setAutoAccept(boolean bl) {
        this.isAutoAccept = bl;
    }

    @Nullable
    public final Plan getPlan() {
        return this.plan;
    }

    public final void setPlan(@Nullable Plan plan) {
        this.plan = plan;
    }

    @Nullable
    public final String getPlanGroup() {
        return this.planGroup;
    }

    public final void setPlanGroup(@Nullable String string) {
        this.planGroup = string;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007J\n\u0010\u0005\u001a\u00020\u0006*\u00020\u0007J\f\u0010\b\u001a\u0004\u0018\u00010\t*\u00020\u0007J\f\u0010\n\u001a\u0004\u0018\u00010\u000b*\u00020\u0007\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonAutomation$Companion;", "", "()V", "automation40", "", "isAutoAccept", "", "Link/ptms/chemdah/core/quest/Template;", "plan", "Link/ptms/chemdah/core/quest/addon/data/Plan;", "planGroup", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nAddonAutomation.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonAutomation.kt\nink/ptms/chemdah/core/quest/addon/AddonAutomation$Companion\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,190:1\n215#2,2:191\n215#2,2:193\n215#2,2:201\n766#3:195\n857#3,2:196\n1855#3:198\n1855#3,2:199\n1856#3:203\n*S KotlinDebug\n*F\n+ 1 AddonAutomation.kt\nink/ptms/chemdah/core/quest/addon/AddonAutomation$Companion\n*L\n128#1:191,2\n141#1:193,2\n161#1:201,2\n152#1:195\n152#1:196,2\n152#1:198\n155#1:199,2\n152#1:203\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        public final boolean isAutoAccept(@NotNull Template $this$isAutoAccept) {
            Intrinsics.checkNotNullParameter((Object)$this$isAutoAccept, (String)"<this>");
            AddonAutomation addonAutomation = (AddonAutomation)$this$isAutoAccept.addon("automation");
            return addonAutomation != null ? addonAutomation.isAutoAccept() : false;
        }

        @Nullable
        public final Plan plan(@NotNull Template $this$plan) {
            Intrinsics.checkNotNullParameter((Object)$this$plan, (String)"<this>");
            AddonAutomation addonAutomation = (AddonAutomation)$this$plan.addon("automation");
            return addonAutomation != null ? addonAutomation.getPlan() : null;
        }

        @Nullable
        public final String planGroup(@NotNull Template $this$planGroup) {
            Intrinsics.checkNotNullParameter((Object)$this$planGroup, (String)"<this>");
            AddonAutomation addonAutomation = (AddonAutomation)$this$planGroup.addon("automation");
            return addonAutomation != null ? addonAutomation.getPlanGroup() : null;
        }

        /*
         * WARNING - void declaration
         */
        @Schedule(async=true, period=40L)
        public final void automation40() {
            void $this$filterTo$iv$iv;
            Template quest2;
            Map.Entry entry;
            Map.Entry element$iv;
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            HashMap<Object, PlanGroup> groups = new HashMap<Object, PlanGroup>();
            ArrayList<Template> autoAccept = new ArrayList<Template>();
            Object $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                PlanGroup group2;
                entry = element$iv = iterator.next();
                boolean bl = false;
                quest2 = (Template)entry.getValue();
                if (Companion.isAutoAccept(quest2)) {
                    autoAccept.add(quest2);
                    continue;
                }
                Plan plan = Companion.plan(quest2);
                if (plan == null) continue;
                String id2 = plan.getGroup() != null ? '@' + plan.getGroup() : quest2.getId();
                Intrinsics.checkNotNullExpressionValue((Object)groups.computeIfAbsent(id2, arg_0 -> Companion.automation40$lambda$1$lambda$0((Function1)new Function1<String, PlanGroup>(id2, plan){
                    final /* synthetic */ String $id;
                    final /* synthetic */ Plan $plan;
                    {
                        this.$id = $id;
                        this.$plan = $plan;
                        super(1);
                    }

                    @NotNull
                    public final PlanGroup invoke(@NotNull String it) {
                        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                        return new PlanGroup(this.$id, this.$plan);
                    }
                }, arg_0)), (String)"plan = quest.plan()\n    \u2026) { PlanGroup(id, plan) }");
                group2.getQuests().add(quest2);
            }
            $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
            $i$f$forEach = false;
            iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                String group3;
                entry = element$iv = iterator.next();
                boolean bl = false;
                quest2 = (Template)entry.getValue();
                if (Companion.plan(quest2) != null || (group3 = Companion.planGroup(quest2)) == null || !groups.containsKey('@' + group3)) continue;
                Object v = groups.get('@' + group3);
                Intrinsics.checkNotNull(v);
                ((PlanGroup)v).getQuests().add(quest2);
            }
            if (groups.isEmpty() && autoAccept.isEmpty()) {
                return;
            }
            Collection collection = Bukkit.getOnlinePlayers();
            Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
            Iterable $this$filter$iv = collection;
            boolean $i$f$filter = false;
            iterator = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Player it = (Player)element$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$forEach$iv = (List)destination$iv$iv;
            $i$f$forEach = false;
            iterator = $this$forEach$iv.iterator();
            while (iterator.hasNext()) {
                Object element$iv2;
                element$iv = iterator.next();
                Player player = (Player)element$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)player, (String)"player");
                PlayerProfile profile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
                Object $this$forEach$iv2 = autoAccept;
                boolean $i$f$forEach2 = false;
                Iterator<Object> iterator2 = $this$forEach$iv2.iterator();
                while (iterator2.hasNext()) {
                    element$iv2 = iterator2.next();
                    Template it = (Template)element$iv2;
                    boolean bl2 = false;
                    if (profile.getQuestById(it.getId(), false) != null) continue;
                    it.acceptTo(profile);
                }
                $this$forEach$iv2 = groups;
                $i$f$forEach2 = false;
                iterator2 = $this$forEach$iv2.entrySet().iterator();
                while (iterator2.hasNext()) {
                    Object object = element$iv2 = (Map.Entry)iterator2.next();
                    boolean bl3 = false;
                    String id3 = (String)object.getKey();
                    PlanGroup group4 = (PlanGroup)object.getValue();
                    long nextTime = profile.getPersistentDataContainer().get("quest.automation." + id3 + ".next", 0L).toLong();
                    if (nextTime >= System.currentTimeMillis()) continue;
                    long newTime = group4.getPlan().getNextTime();
                    if (newTime < System.currentTimeMillis()) {
                        Date now = new Date(System.currentTimeMillis());
                        Object[] objectArray = new Object[]{id3, new Date(newTime), now, group4.getPlan().getDebug()};
                        LangKt.sendLang((ProxyCommandSender)AdapterKt.console(), (String)"console-automation-plan-out-of-date", (Object[])objectArray);
                        continue;
                    }
                    profile.getPersistentDataContainer().set("quest.automation." + id3 + ".next", newTime);
                    List pool = CollectionsKt.toMutableList((Collection)group4.getQuests());
                    Ref.IntRef i = new Ref.IntRef();
                    i.element = group4.getPlan().getCount();
                    ink.ptms.chemdah.core.quest.addon.AddonAutomation$Companion.automation40$lambda$7$lambda$6$process(i, pool, profile);
                }
            }
        }

        private static final PlanGroup automation40$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (PlanGroup)$tmp0.invoke(p0);
        }

        private static final void automation40$lambda$7$lambda$6$process$lambda$5(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void automation40$lambda$7$lambda$6$process(Ref.IntRef i, List<Template> pool, PlayerProfile profile) {
            if (i.element > 0 && !((Collection)pool).isEmpty()) {
                pool.remove(Random.Default.nextInt(pool.size())).acceptTo(profile).thenAccept(arg_0 -> Companion.automation40$lambda$7$lambda$6$process$lambda$5((Function1)new Function1<AcceptResult, Unit>(i, pool, profile){
                    final /* synthetic */ Ref.IntRef $i;
                    final /* synthetic */ List<Template> $pool;
                    final /* synthetic */ PlayerProfile $profile;
                    {
                        this.$i = $i;
                        this.$pool = $pool;
                        this.$profile = $profile;
                        super(1);
                    }

                    public final void invoke(AcceptResult it) {
                        if (it.getType() == AcceptResult.Type.SUCCESSFUL) {
                            int n = this.$i.element;
                            this.$i.element = n + -1;
                        }
                        ink.ptms.chemdah.core.quest.addon.AddonAutomation$Companion.access$automation40$lambda$7$lambda$6$process(this.$i, this.$pool, this.$profile);
                    }
                }, arg_0));
            }
        }

        public static final /* synthetic */ void access$automation40$lambda$7$lambda$6$process(Ref.IntRef i, List pool, PlayerProfile profile) {
            ink.ptms.chemdah.core.quest.addon.AddonAutomation$Companion.automation40$lambda$7$lambda$6$process(i, pool, profile);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

