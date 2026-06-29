/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.Schedule
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$VarTable
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionToColorKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.Ref$ObjectRef
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.addon.AddonStats;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.module.party.PartySystem;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionToColorKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.Couple;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.NumberKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.Ref;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="stats")
@Option(type=Option.Type.SECTION)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0007\u0018\u0000 =2\u00020\u0001:\u0002=>B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J \u00104\u001a\b\u0012\u0004\u0012\u000206052\u0006\u00107\u001a\u0002082\n\b\u0002\u00109\u001a\u0004\u0018\u00010:J\u0016\u0010;\u001a\u00020\t2\u0006\u00109\u001a\u00020\u00052\u0006\u0010<\u001a\u000206R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u000e\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u001a\u0010\u001a\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u001a\u0010\u001f\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010\u0011\"\u0004\b!\u0010\u0013R\u001a\u0010\"\u001a\u00020#X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b$\u0010%\"\u0004\b&\u0010'R\u001a\u0010(\u001a\u00020)X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b*\u0010+\"\u0004\b,\u0010-R\u001a\u0010.\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b/\u0010\u0011\"\u0004\b0\u0010\u0013R\u001a\u00101\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b2\u0010\u0011\"\u0004\b3\u0010\u0013\u00a8\u0006?"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonStats;", "Link/ptms/chemdah/core/quest/addon/Addon;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/QuestContainer;)V", "agent", "", "", "getAgent", "()Ljava/util/List;", "setAgent", "(Ljava/util/List;)V", "bossMusic", "", "getBossMusic", "()Z", "setBossMusic", "(Z)V", "color", "Lorg/bukkit/boss/BarColor;", "getColor", "()Lorg/bukkit/boss/BarColor;", "setColor", "(Lorg/bukkit/boss/BarColor;)V", "content", "getContent", "()Ljava/lang/String;", "setContent", "(Ljava/lang/String;)V", "darkenSky", "getDarkenSky", "setDarkenSky", "stay", "", "getStay", "()I", "setStay", "(I)V", "style", "Lorg/bukkit/boss/BarStyle;", "getStyle", "()Lorg/bukkit/boss/BarStyle;", "setStyle", "(Lorg/bukkit/boss/BarStyle;)V", "visible", "getVisible", "setVisible", "visibleAlways", "getVisibleAlways", "setVisibleAlways", "getProgress", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "getTitle", "progress", "Companion", "StatsMap", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonStats.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonStats.kt\nink/ptms/chemdah/core/quest/addon/AddonStats\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,420:1\n29#2:421\n288#3,2:422\n*S KotlinDebug\n*F\n+ 1 AddonStats.kt\nink/ptms/chemdah/core/quest/addon/AddonStats\n*L\n99#1:421\n162#1:422,2\n*E\n"})
public final class AddonStats
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private List<String> agent;
    private boolean visible;
    private boolean visibleAlways;
    private boolean bossMusic;
    private boolean darkenSky;
    private int stay;
    @NotNull
    private BarStyle style;
    @NotNull
    private BarColor color;
    @NotNull
    private String content;
    @NotNull
    private static final ConcurrentHashMap<String, StatsMap> statsMap = new ConcurrentHashMap();

    public AddonStats(@NotNull ConfigurationSection config, @NotNull QuestContainer questContainer) {
        AddonStats addonStats;
        BarStyle barStyle;
        AddonStats addonStats2;
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(config, questContainer);
        Object object = config.get("$", (Object)"pass");
        Intrinsics.checkNotNull((Object)object);
        this.agent = CollectionKt.asList((Object)object);
        Object $this$cbool$iv = config.get("visible");
        boolean $i$f$getCbool22 = false;
        this.visible = Coerce.toBoolean((Object)$this$cbool$iv);
        this.visibleAlways = Intrinsics.areEqual((Object)config.get("visible"), (Object)"always");
        this.bossMusic = config.getBoolean("boss-music");
        this.darkenSky = config.getBoolean("darken-sky");
        this.stay = config.getInt("stay", Chemdah.INSTANCE.getConf().getInt("default-stats.stay"));
        AddonStats addonStats3 = this;
        try {
            addonStats2 = addonStats3;
            String string = config.getString("style", Chemdah.INSTANCE.getConf().getString("default-stats.style"));
            Intrinsics.checkNotNull((Object)string);
            String string2 = string.toUpperCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
            barStyle = BarStyle.valueOf((String)string2);
        }
        catch (Throwable $i$f$getCbool22) {
            addonStats2 = addonStats3;
            barStyle = BarStyle.SOLID;
        }
        addonStats2.style = barStyle;
        addonStats3 = this;
        try {
            addonStats = addonStats3;
            String string = config.getString("color", Chemdah.INSTANCE.getConf().getString("default-stats.color"));
            Intrinsics.checkNotNull((Object)string);
            String string3 = string.toUpperCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
            barStyle = BarColor.valueOf((String)string3);
        }
        catch (Throwable ignored) {
            addonStats = addonStats3;
            barStyle = BarColor.WHITE;
        }
        addonStats.color = barStyle;
        this.content = String.valueOf(config.getString("content", SectionToColorKt.getStringColored((ConfigurationSection)((ConfigurationSection)Chemdah.INSTANCE.getConf()), (String)"default-stats.content")));
    }

    @NotNull
    public final List<String> getAgent() {
        return this.agent;
    }

    public final void setAgent(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.agent = list2;
    }

    public final boolean getVisible() {
        return this.visible;
    }

    public final void setVisible(boolean bl) {
        this.visible = bl;
    }

    public final boolean getVisibleAlways() {
        return this.visibleAlways;
    }

    public final void setVisibleAlways(boolean bl) {
        this.visibleAlways = bl;
    }

    public final boolean getBossMusic() {
        return this.bossMusic;
    }

    public final void setBossMusic(boolean bl) {
        this.bossMusic = bl;
    }

    public final boolean getDarkenSky() {
        return this.darkenSky;
    }

    public final void setDarkenSky(boolean bl) {
        this.darkenSky = bl;
    }

    public final int getStay() {
        return this.stay;
    }

    public final void setStay(int n) {
        this.stay = n;
    }

    @NotNull
    public final BarStyle getStyle() {
        return this.style;
    }

    public final void setStyle(@NotNull BarStyle barStyle) {
        Intrinsics.checkNotNullParameter((Object)barStyle, (String)"<set-?>");
        this.style = barStyle;
    }

    @NotNull
    public final BarColor getColor() {
        return this.color;
    }

    public final void setColor(@NotNull BarColor barColor) {
        Intrinsics.checkNotNullParameter((Object)barColor, (String)"<set-?>");
        this.color = barColor;
    }

    @NotNull
    public final String getContent() {
        return this.content;
    }

    public final void setContent(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.content = string;
    }

    @NotNull
    public final String getTitle(@NotNull QuestContainer task, @NotNull Progress progress) {
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)progress, (String)"progress");
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)MetaName.Companion.displayName$default(MetaName.Companion, task, false, 1, null)), TuplesKt.to((Object)"value", (Object)progress.getValue()), TuplesKt.to((Object)"target", (Object)progress.getTarget()), TuplesKt.to((Object)"percent", (Object)Coerce.format((double)(progress.getPercent() * (double)100)))};
        return StringKt.replace(this.content, pairArray);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final CompletableFuture<Progress> getProgress(@NotNull PlayerProfile profile, @Nullable Task task) {
        CompletableFuture<Progress> completableFuture;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        if (task != null) {
            Object v0;
            Object[] $this$firstOrNull$iv;
            CompletableFuture<Progress> future;
            block5: {
                future = new CompletableFuture<Progress>();
                $this$firstOrNull$iv = (Object[])profile.getQuests(true);
                boolean $i$f$firstOrNull = false;
                for (Object list2 : $this$firstOrNull$iv) {
                    Quest it = (Quest)list2;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)it.getId(), (Object)task.getTemplate().getId())) continue;
                    v0 = list2;
                    break block5;
                }
                v0 = null;
            }
            Quest quest2 = v0;
            if (quest2 == null) {
                $this$firstOrNull$iv = new Object[]{"Quest(" + this.getQuestContainer().getNode() + ") not accepted."};
                IOKt.warning((Object[])$this$firstOrNull$iv);
                future.complete(Progress.Companion.getZERO());
                return future;
            }
            AtomicReference<QuestContext.VarTable> refs = new AtomicReference<QuestContext.VarTable>();
            KetherShell ketherShell = KetherShell.INSTANCE;
            List<String> list2 = this.agent;
            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)profile.getPlayer());
            List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
            CompletionStage completionStage = KetherShell.eval$default((KetherShell)ketherShell, list2, (boolean)false, list3, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(refs, task){
                final /* synthetic */ AtomicReference<QuestContext.VarTable> $refs;
                final /* synthetic */ Task $task;
                {
                    this.$refs = $refs;
                    this.$task = $task;
                    super(1);
                }

                /*
                 * WARNING - void declaration
                 */
                public final void invoke(@NotNull ScriptContext $this$eval) {
                    void vars2;
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    QuestContext.VarTable varTable = $this$eval.rootFrame().variables();
                    Task task = this.$task;
                    QuestContext.VarTable varTable2 = varTable;
                    AtomicReference<QuestContext.VarTable> atomicReference = this.$refs;
                    boolean bl = false;
                    vars2.set("@QuestContainer", (Object)task);
                    atomicReference.set(varTable);
                }
            }), (int)42, null).thenApply(arg_0 -> AddonStats.getProgress$lambda$2(task, profile, future, refs, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"task: Task? = null): Com\u2026          }\n            }");
            FuturesKt.except(completionStage, (Function1)new Function1<Throwable, Boolean>(future){
                final /* synthetic */ CompletableFuture<Progress> $future;
                {
                    this.$future = $future;
                    super(1);
                }

                public final Boolean invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    return this.$future.complete(Progress.Companion.getZERO());
                }
            });
            return future;
        }
        if (this.getQuestContainer() instanceof Template) {
            void var3_4;
            CompletableFuture<Progress> future = new CompletableFuture<Progress>();
            Collection<Task> collection = ((Template)this.getQuestContainer()).getTaskMap().values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"questContainer.taskMap.values");
            List tasks = CollectionsKt.toList((Iterable)collection);
            Ref.ObjectRef p = new Ref.ObjectRef();
            p.element = Progress.Companion.getZERO();
            AddonStats.getProgress$process(tasks, this, profile, future, (Ref.ObjectRef<Progress>)p, 0);
            completableFuture = var3_4;
        } else {
            QuestContainer questContainer = this.getQuestContainer();
            Intrinsics.checkNotNull((Object)questContainer, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.Task");
            completableFuture = this.getProgress(profile, (Task)questContainer);
        }
        return completableFuture;
    }

    public static /* synthetic */ CompletableFuture getProgress$default(AddonStats addonStats, PlayerProfile playerProfile, Task task, int n, Object object) {
        if ((n & 2) != 0) {
            task = null;
        }
        return addonStats.getProgress(playerProfile, task);
    }

    private static final Boolean getProgress$lambda$2(Task $task, PlayerProfile $profile, CompletableFuture $future, AtomicReference $refs, Object it) {
        Intrinsics.checkNotNullParameter((Object)$profile, (String)"$profile");
        Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
        Intrinsics.checkNotNullParameter((Object)$refs, (String)"$refs");
        Progress $this$getProgress_u24lambda_u242_u24lambda_u241 = $task.getObjective().getProgress($profile, $task);
        boolean bl = false;
        Object object = ((QuestContext.VarTable)$refs.get()).get("value").orElse($this$getProgress_u24lambda_u242_u24lambda_u241.getValue());
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"refs.get().get<Any?>(\"value\").orElse(value)");
        Object object2 = ((QuestContext.VarTable)$refs.get()).get("target").orElse($this$getProgress_u24lambda_u242_u24lambda_u241.getTarget());
        Intrinsics.checkNotNullExpressionValue((Object)object2, (String)"refs.get().get<Any?>(\"target\").orElse(target)");
        return $future.complete(new Progress(object, object2, NumberKt.asDouble(((QuestContext.VarTable)$refs.get()).get("percent").orElse(null), $this$getProgress_u24lambda_u242_u24lambda_u241.getPercent())));
    }

    private static final void getProgress$process$lambda$3(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void getProgress$process(List<? extends Task> tasks, AddonStats this$0, PlayerProfile $profile, CompletableFuture<Progress> future, Ref.ObjectRef<Progress> p, int cur) {
        if (cur < tasks.size()) {
            CompletionStage completionStage = this$0.getProgress($profile, tasks.get(cur)).thenAccept(arg_0 -> AddonStats.getProgress$process$lambda$3((Function1)new Function1<Progress, Unit>(p, tasks, cur, this$0, $profile, future){
                final /* synthetic */ Ref.ObjectRef<Progress> $p;
                final /* synthetic */ List<Task> $tasks;
                final /* synthetic */ int $cur;
                final /* synthetic */ AddonStats this$0;
                final /* synthetic */ PlayerProfile $profile;
                final /* synthetic */ CompletableFuture<Progress> $future;
                {
                    this.$p = $p;
                    this.$tasks = $tasks;
                    this.$cur = $cur;
                    this.this$0 = $receiver;
                    this.$profile = $profile;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(Progress it) {
                    this.$p.element = new Progress(UtilsForKetherKt.increaseAny(((Progress)this.$p.element).getValue(), it.getValue()), UtilsForKetherKt.increaseAny(((Progress)this.$p.element).getTarget(), it.getTarget()), ((Progress)this.$p.element).getPercent() + it.getPercent() / (double)this.$tasks.size());
                    AddonStats.access$getProgress$process(this.$tasks, this.this$0, this.$profile, this.$future, this.$p, this.$cur + 1);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"fun getProgress(profile:\u2026 as Task)\n        }\n    }");
            FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(cur, tasks, this$0, $profile, future, p){
                final /* synthetic */ int $cur;
                final /* synthetic */ List<Task> $tasks;
                final /* synthetic */ AddonStats this$0;
                final /* synthetic */ PlayerProfile $profile;
                final /* synthetic */ CompletableFuture<Progress> $future;
                final /* synthetic */ Ref.ObjectRef<Progress> $p;
                {
                    this.$cur = $cur;
                    this.$tasks = $tasks;
                    this.this$0 = $receiver;
                    this.$profile = $profile;
                    this.$future = $future;
                    this.$p = $p;
                    super(1);
                }

                public final void invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    AddonStats.access$getProgress$process(this.$tasks, this.this$0, this.$profile, this.$future, this.$p, this.$cur + 1);
                }
            }));
        } else {
            future.complete((Progress)p.element);
        }
    }

    public static final /* synthetic */ void access$getProgress$process(List tasks, AddonStats this$0, PlayerProfile $profile, CompletableFuture future, Ref.ObjectRef p, int cur) {
        AddonStats.getProgress$process(tasks, this$0, $profile, future, (Ref.ObjectRef<Progress>)p, cur);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000t\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\nH\u0003J\u0010\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\rH\u0003J\u0010\u0010\u000e\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u000fH\u0003J\u0010\u0010\u0010\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0011H\u0003J\u0010\u0010\u0012\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0013H\u0003J\u0010\u0010\u0014\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0015H\u0003J\u0010\u0010\u0016\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0017H\u0003J\u0018\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u001a0\u0019*\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dJ\u0012\u0010\u001e\u001a\u00020\n*\u00020\u001f2\u0006\u0010\u001c\u001a\u00020\u001dJ\u0012\u0010\u001e\u001a\u00020\n*\u00020 2\u0006\u0010\u001c\u001a\u00020\u001dJ\u0012\u0010!\u001a\u00020\n*\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dJ\u0012\u0010\"\u001a\u00020\n*\u00020\u001f2\u0006\u0010\u001c\u001a\u00020\u001dJ\f\u0010#\u001a\u0004\u0018\u00010$*\u00020\u001bJ\u001a\u0010%\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010&0\u0019*\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dR\u001d\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006'"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonStats$Companion;", "", "()V", "statsMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Link/ptms/chemdah/core/quest/addon/AddonStats$StatsMap;", "getStatsMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "bossBarRemove20", "", "onCompletePost", "e", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Post;", "onContinuePost", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Post;", "onRegistered", "Link/ptms/chemdah/api/event/collect/QuestEvents$Registered;", "onReleased", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "onSelected", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Selected;", "onUnregistered", "Link/ptms/chemdah/api/event/collect/QuestEvents$Unregistered;", "getProgress", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/objective/Progress;", "Link/ptms/chemdah/core/quest/QuestContainer;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "hiddenStats", "Link/ptms/chemdah/core/quest/Quest;", "Link/ptms/chemdah/core/quest/Task;", "refreshStats", "refreshStatusAlwaysType", "stats", "Link/ptms/chemdah/core/quest/addon/AddonStats;", "statsDisplay", "Lorg/bukkit/boss/BossBar;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nAddonStats.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonStats.kt\nink/ptms/chemdah/core/quest/addon/AddonStats$Companion\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,420:1\n215#2,2:421\n215#2:430\n215#2,2:431\n216#2:433\n1855#3,2:423\n766#3:425\n857#3,2:426\n1855#3,2:428\n1855#3:434\n1855#3,2:435\n1856#3:437\n1855#3,2:438\n1855#3,2:440\n1855#3,2:442\n1855#3,2:444\n*S KotlinDebug\n*F\n+ 1 AddonStats.kt\nink/ptms/chemdah/core/quest/addon/AddonStats$Companion\n*L\n258#1:421,2\n366#1:430\n367#1:431,2\n366#1:433\n283#1:423,2\n312#1:425\n312#1:426,2\n312#1:428,2\n383#1:434\n384#1:435,2\n383#1:437\n392#1:438,2\n399#1:440,2\n406#1:442,2\n413#1:444,2\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final ConcurrentHashMap<String, StatsMap> getStatsMap() {
            return statsMap;
        }

        @Nullable
        public final AddonStats stats(@NotNull QuestContainer $this$stats) {
            Intrinsics.checkNotNullParameter((Object)$this$stats, (String)"<this>");
            return (AddonStats)$this$stats.addon("stats");
        }

        @NotNull
        public final CompletableFuture<BossBar> statsDisplay(@NotNull QuestContainer $this$statsDisplay, @NotNull PlayerProfile profile) {
            Intrinsics.checkNotNullParameter((Object)$this$statsDisplay, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            CompletableFuture<BossBar> future = new CompletableFuture<BossBar>();
            AddonStats stats = this.stats($this$statsDisplay);
            if (stats == null) {
                future.complete(null);
                return future;
            }
            CompletionStage completionStage = this.getProgress($this$statsDisplay, profile).thenApply(arg_0 -> Companion.statsDisplay$lambda$0((Function1)new Function1<Progress, Boolean>(stats, $this$statsDisplay, profile, future){
                final /* synthetic */ AddonStats $stats;
                final /* synthetic */ QuestContainer $this_statsDisplay;
                final /* synthetic */ PlayerProfile $profile;
                final /* synthetic */ CompletableFuture<BossBar> $future;
                {
                    this.$stats = $stats;
                    this.$this_statsDisplay = $receiver;
                    this.$profile = $profile;
                    this.$future = $future;
                    super(1);
                }

                public final Boolean invoke(Progress progress) {
                    BossBar bossBar = Bukkit.createBossBar((String)"", (BarColor)this.$stats.getColor(), (BarStyle)this.$stats.getStyle(), (BarFlag[])new BarFlag[0]);
                    Intrinsics.checkNotNullExpressionValue((Object)bossBar, (String)"createBossBar(\"\", stats.color, stats.style)");
                    BossBar bossBar2 = bossBar;
                    if (this.$stats.getDarkenSky()) {
                        bossBar2.addFlag(BarFlag.DARKEN_SKY);
                    }
                    if (this.$stats.getBossMusic()) {
                        bossBar2.addFlag(BarFlag.PLAY_BOSS_MUSIC);
                    }
                    bossBar2.setProgress(progress.getPercent());
                    Intrinsics.checkNotNullExpressionValue((Object)progress, (String)"progress");
                    bossBar2.setTitle(this.$stats.getTitle(this.$this_statsDisplay, progress));
                    bossBar2.addPlayer(this.$profile.getPlayer());
                    return this.$future.complete(bossBar2);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"QuestContainer.statsDisp\u2026te(bossBar)\n            }");
            FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(future){
                final /* synthetic */ CompletableFuture<BossBar> $future;
                {
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    this.$future.complete(null);
                }
            }));
            return future;
        }

        @NotNull
        public final CompletableFuture<Progress> getProgress(@NotNull QuestContainer $this$getProgress, @NotNull PlayerProfile profile) {
            CompletableFuture<Progress> completableFuture;
            CompletableFuture progress;
            Intrinsics.checkNotNullParameter((Object)$this$getProgress, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            AddonStats addonStats = this.stats($this$getProgress);
            CompletableFuture completableFuture2 = progress = addonStats != null ? AddonStats.getProgress$default(addonStats, profile, null, 2, null) : null;
            if (progress != null) {
                return progress;
            }
            QuestContainer questContainer = $this$getProgress;
            if (questContainer instanceof Template) {
                Progress total = null;
                total = Progress.Companion.getZERO();
                Map $this$forEach$iv = ((Template)$this$getProgress).getTaskMap();
                boolean $i$f$forEach = false;
                Iterator iterator = $this$forEach$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry element$iv;
                    Map.Entry entry = element$iv = iterator.next();
                    boolean bl = false;
                    Task task = (Task)entry.getValue();
                    Progress tp = task.getObjective().getProgress(profile, task);
                    total = new Progress(UtilsForKetherKt.increaseAny(total.getValue(), tp.getValue()), UtilsForKetherKt.increaseAny(total.getTarget(), tp.getTarget()), total.getPercent() + tp.getPercent() / (double)((Template)$this$getProgress).getTaskMap().size());
                }
                CompletableFuture<Progress> completableFuture3 = CompletableFuture.completedFuture(total);
                Intrinsics.checkNotNullExpressionValue(completableFuture3, (String)"{\n                    va\u2026(total)\n                }");
                completableFuture = completableFuture3;
            } else if (questContainer instanceof Task) {
                CompletableFuture<Progress> completableFuture4 = CompletableFuture.completedFuture(((Task)$this$getProgress).getObjective().getProgress(profile, (Task)$this$getProgress));
                completableFuture = completableFuture4;
                Intrinsics.checkNotNullExpressionValue(completableFuture4, (String)"completedFuture(objectiv\u2026tProgress(profile, this))");
            } else {
                throw new IllegalStateException("out of case".toString());
            }
            return completableFuture;
        }

        public final void hiddenStats(@NotNull Quest $this$hiddenStats, @NotNull PlayerProfile profile) {
            Intrinsics.checkNotNullParameter((Object)$this$hiddenStats, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            StatsMap statsMap2 = this.getStatsMap().computeIfAbsent(profile.getPlayer().getName(), arg_0 -> Companion.hiddenStats$lambda$2(hiddenStats.statsMap.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)statsMap2, (String)"statsMap.computeIfAbsent\u2026ayer.name) { StatsMap() }");
            StatsMap statsMap3 = statsMap2;
            BossBar bossBar = statsMap3.getBossBar().remove($this$hiddenStats.getTemplate().getPath());
            if (bossBar != null && (bossBar = bossBar.getKey()) != null) {
                bossBar.removeAll();
            }
            BossBar bossBar2 = statsMap3.getBossBarAlways().remove($this$hiddenStats.getTemplate().getPath());
            if (bossBar2 != null) {
                bossBar2.removeAll();
            }
            Iterable $this$forEach$iv = $this$hiddenStats.getTasks();
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Task it = (Task)element$iv;
                boolean bl = false;
                BossBar bossBar3 = statsMap3.getBossBar().remove(it.getPath());
                if (bossBar3 != null && (bossBar3 = bossBar3.getKey()) != null) {
                    bossBar3.removeAll();
                }
                BossBar bossBar4 = statsMap3.getBossBarAlways().remove(it.getPath());
                if (bossBar4 == null) continue;
                bossBar4.removeAll();
            }
        }

        public final void hiddenStats(@NotNull Task $this$hiddenStats, @NotNull PlayerProfile profile) {
            block1: {
                Intrinsics.checkNotNullParameter((Object)$this$hiddenStats, (String)"<this>");
                Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
                StatsMap statsMap2 = this.getStatsMap().computeIfAbsent(profile.getPlayer().getName(), arg_0 -> Companion.hiddenStats$lambda$4(hiddenStats.statsMap.2.INSTANCE, arg_0));
                Intrinsics.checkNotNullExpressionValue((Object)statsMap2, (String)"statsMap.computeIfAbsent\u2026ayer.name) { StatsMap() }");
                StatsMap statsMap3 = statsMap2;
                BossBar bossBar = statsMap3.getBossBar().remove($this$hiddenStats.getPath());
                if (bossBar != null && (bossBar = bossBar.getKey()) != null) {
                    bossBar.removeAll();
                }
                BossBar bossBar2 = statsMap3.getBossBarAlways().remove($this$hiddenStats.getPath());
                if (bossBar2 == null) break block1;
                bossBar2.removeAll();
            }
        }

        /*
         * WARNING - void declaration
         */
        public final void refreshStatusAlwaysType(@NotNull Quest $this$refreshStatusAlwaysType, @NotNull PlayerProfile profile) {
            void $this$forEach$iv;
            void $this$filterTo$iv$iv;
            Intrinsics.checkNotNullParameter((Object)$this$refreshStatusAlwaysType, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            StatsMap statsMap2 = this.getStatsMap().computeIfAbsent(profile.getPlayer().getName(), arg_0 -> Companion.refreshStatusAlwaysType$lambda$5(refreshStatusAlwaysType.statsMap.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)statsMap2, (String)"statsMap.computeIfAbsent\u2026ayer.name) { StatsMap() }");
            StatsMap statsMap3 = statsMap2;
            AddonStats addonStats = this.stats($this$refreshStatusAlwaysType.getTemplate());
            boolean bl = addonStats != null ? addonStats.getVisibleAlways() : false;
            if (bl) {
                FuturesKt.applyWithError(this.statsDisplay($this$refreshStatusAlwaysType.getTemplate(), profile), (Function1)new Function1<BossBar, Unit>(statsMap3, $this$refreshStatusAlwaysType){
                    final /* synthetic */ StatsMap $statsMap;
                    final /* synthetic */ Quest $this_refreshStatusAlwaysType;
                    {
                        this.$statsMap = $statsMap;
                        this.$this_refreshStatusAlwaysType = $receiver;
                        super(1);
                    }

                    public final void invoke(@Nullable BossBar bossBar) {
                        block1: {
                            if (bossBar == null) break block1;
                            BossBar bossBar2 = this.$statsMap.getBossBarAlways().put(this.$this_refreshStatusAlwaysType.getTemplate().getPath(), bossBar);
                            if (bossBar2 != null) {
                                bossBar2.removeAll();
                            }
                        }
                    }
                });
            }
            Iterable $this$filter$iv = $this$refreshStatusAlwaysType.getTasks();
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Task it = (Task)element$iv$iv;
                boolean bl2 = false;
                AddonStats addonStats2 = Companion.stats(it);
                boolean bl3 = addonStats2 != null ? addonStats2.getVisibleAlways() : false;
                if (!bl3) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Task it = (Task)element$iv;
                boolean bl4 = false;
                FuturesKt.applyWithError(Companion.statsDisplay(it, profile), (Function1)new Function1<BossBar, Unit>(statsMap3, it){
                    final /* synthetic */ StatsMap $statsMap;
                    final /* synthetic */ Task $it;
                    {
                        this.$statsMap = $statsMap;
                        this.$it = $it;
                        super(1);
                    }

                    public final void invoke(@Nullable BossBar bossBar) {
                        block1: {
                            if (bossBar == null) break block1;
                            BossBar bossBar2 = this.$statsMap.getBossBarAlways().put(this.$it.getPath(), bossBar);
                            if (bossBar2 != null) {
                                bossBar2.removeAll();
                            }
                        }
                    }
                });
            }
        }

        public final void refreshStats(@NotNull QuestContainer $this$refreshStats, @NotNull PlayerProfile profile) {
            Intrinsics.checkNotNullParameter((Object)$this$refreshStats, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            AddonStats addonStats = this.stats($this$refreshStats);
            if (addonStats == null) {
                return;
            }
            AddonStats stats = addonStats;
            StatsMap statsMap2 = this.getStatsMap().computeIfAbsent(profile.getPlayer().getName(), arg_0 -> Companion.refreshStats$lambda$8(refreshStats.statsMap.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)statsMap2, (String)"statsMap.computeIfAbsent\u2026ayer.name) { StatsMap() }");
            StatsMap statsMap3 = statsMap2;
            if (stats.getVisibleAlways()) {
                BossBar bossBar = statsMap3.getBossBarAlways().get($this$refreshStats.getPath());
                if (bossBar == null) {
                    FuturesKt.applyWithError(this.statsDisplay($this$refreshStats, profile), (Function1)new Function1<BossBar, Unit>(statsMap3, $this$refreshStats){
                        final /* synthetic */ StatsMap $statsMap;
                        final /* synthetic */ QuestContainer $this_refreshStats;
                        {
                            this.$statsMap = $statsMap;
                            this.$this_refreshStats = $receiver;
                            super(1);
                        }

                        public final void invoke(@Nullable BossBar bar) {
                            block1: {
                                if (bar == null) break block1;
                                BossBar bossBar = this.$statsMap.getBossBarAlways().put(this.$this_refreshStats.getPath(), bar);
                                if (bossBar != null) {
                                    bossBar.removeAll();
                                }
                            }
                        }
                    });
                } else {
                    FuturesKt.applyWithError(this.getProgress($this$refreshStats, profile), (Function1)new Function1<Progress, Unit>(bossBar, stats, $this$refreshStats){
                        final /* synthetic */ BossBar $bossBar;
                        final /* synthetic */ AddonStats $stats;
                        final /* synthetic */ QuestContainer $this_refreshStats;
                        {
                            this.$bossBar = $bossBar;
                            this.$stats = $stats;
                            this.$this_refreshStats = $receiver;
                            super(1);
                        }

                        public final void invoke(@NotNull Progress progress) {
                            Intrinsics.checkNotNullParameter((Object)progress, (String)"progress");
                            this.$bossBar.setProgress(progress.getPercent());
                            this.$bossBar.setTitle(this.$stats.getTitle(this.$this_refreshStats, progress));
                        }
                    });
                }
            } else if (stats.getVisible()) {
                Couple<BossBar, Long> bossBar = statsMap3.getBossBar().get($this$refreshStats.getPath());
                if (bossBar == null) {
                    FuturesKt.applyWithError(this.statsDisplay($this$refreshStats, profile), (Function1)new Function1<BossBar, Unit>(statsMap3, $this$refreshStats, stats){
                        final /* synthetic */ StatsMap $statsMap;
                        final /* synthetic */ QuestContainer $this_refreshStats;
                        final /* synthetic */ AddonStats $stats;
                        {
                            this.$statsMap = $statsMap;
                            this.$this_refreshStats = $receiver;
                            this.$stats = $stats;
                            super(1);
                        }

                        public final void invoke(@Nullable BossBar bar) {
                            block0: {
                                BossBar bossBar;
                                if (bar == null || (bossBar = this.$statsMap.getBossBar().put(this.$this_refreshStats.getPath(), new Couple<BossBar, Long>(bar, System.currentTimeMillis() + (long)this.$stats.getStay() * 50L))) == null || (bossBar = bossBar.getKey()) == null) break block0;
                                bossBar.removeAll();
                            }
                        }
                    });
                } else {
                    FuturesKt.applyWithError(this.getProgress($this$refreshStats, profile), (Function1)new Function1<Progress, Unit>(bossBar, stats, $this$refreshStats){
                        final /* synthetic */ Couple<BossBar, Long> $bossBar;
                        final /* synthetic */ AddonStats $stats;
                        final /* synthetic */ QuestContainer $this_refreshStats;
                        {
                            this.$bossBar = $bossBar;
                            this.$stats = $stats;
                            this.$this_refreshStats = $receiver;
                            super(1);
                        }

                        public final void invoke(@NotNull Progress progress) {
                            Intrinsics.checkNotNullParameter((Object)progress, (String)"progress");
                            this.$bossBar.getKey().setProgress(progress.getPercent());
                            this.$bossBar.getKey().setTitle(this.$stats.getTitle(this.$this_refreshStats, progress));
                            this.$bossBar.setValue(System.currentTimeMillis() + (long)this.$stats.getStay() * 50L);
                        }
                    });
                }
            }
        }

        @Schedule(period=20L)
        private final void bossBarRemove20() {
            Map $this$forEach$iv = this.getStatsMap();
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry element$iv;
                Map.Entry entry = element$iv = iterator.next();
                boolean bl = false;
                StatsMap statsMap2 = (StatsMap)entry.getValue();
                Map $this$forEach$iv2 = statsMap2.getBossBar();
                boolean $i$f$forEach2 = false;
                Iterator iterator2 = $this$forEach$iv2.entrySet().iterator();
                while (iterator2.hasNext()) {
                    Map.Entry element$iv2;
                    Map.Entry it = element$iv2 = iterator2.next();
                    boolean bl2 = false;
                    if (((Number)((Couple)it.getValue()).getValue()).longValue() >= System.currentTimeMillis()) continue;
                    ((BossBar)((Couple)it.getValue()).getKey()).removeAll();
                    statsMap2.getBossBar().remove(it.getKey());
                }
            }
        }

        @SubscribeEvent
        private final void onReleased(PlayerEvents.Released e) {
            this.getStatsMap().remove(e.getPlayer().getName());
        }

        @SubscribeEvent
        private final void onSelected(PlayerEvents.Selected e) {
            Iterable $this$forEach$iv = PlayerProfile.getQuests$default(e.getPlayerProfile(), false, 1, null);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Quest quest2 = (Quest)element$iv;
                boolean bl = false;
                Iterable $this$forEach$iv2 = PartySystem.INSTANCE.getMembers(quest2, true);
                boolean $i$f$forEach2 = false;
                for (Object element$iv2 : $this$forEach$iv2) {
                    Player it = (Player)element$iv2;
                    boolean bl2 = false;
                    Companion.refreshStatusAlwaysType(quest2, ChemdahAPI.INSTANCE.getChemdahProfile(it));
                }
            }
        }

        @SubscribeEvent
        private final void onRegistered(QuestEvents.Registered e) {
            Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Companion.refreshStatusAlwaysType(e.getQuest(), ChemdahAPI.INSTANCE.getChemdahProfile(it));
            }
        }

        @SubscribeEvent
        private final void onUnregistered(QuestEvents.Unregistered e) {
            Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Companion.hiddenStats(e.getQuest(), ChemdahAPI.INSTANCE.getChemdahProfile(it));
            }
        }

        @SubscribeEvent
        private final void onCompletePost(ObjectiveEvents.Complete.Post e) {
            Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Companion.hiddenStats(e.getTask(), ChemdahAPI.INSTANCE.getChemdahProfile(it));
            }
        }

        @SubscribeEvent
        private final void onContinuePost(ObjectiveEvents.Continue.Post e) {
            Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Companion.refreshStats(e.getTask(), ChemdahAPI.INSTANCE.getChemdahProfile(it));
                Companion.refreshStats(e.getQuest().getTemplate(), ChemdahAPI.INSTANCE.getChemdahProfile(it));
            }
        }

        private static final Boolean statsDisplay$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Boolean)$tmp0.invoke(p0);
        }

        private static final StatsMap hiddenStats$lambda$2(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (StatsMap)$tmp0.invoke(p0);
        }

        private static final StatsMap hiddenStats$lambda$4(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (StatsMap)$tmp0.invoke(p0);
        }

        private static final StatsMap refreshStatusAlwaysType$lambda$5(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (StatsMap)$tmp0.invoke(p0);
        }

        private static final StatsMap refreshStats$lambda$8(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (StatsMap)$tmp0.invoke(p0);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002R)\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u001d\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00070\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\n\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonStats$StatsMap;", "", "()V", "bossBar", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Link/ptms/chemdah/util/Couple;", "Lorg/bukkit/boss/BossBar;", "", "getBossBar", "()Ljava/util/concurrent/ConcurrentHashMap;", "bossBarAlways", "getBossBarAlways", "Chemdah"})
    public static final class StatsMap {
        @NotNull
        private final ConcurrentHashMap<String, Couple<BossBar, Long>> bossBar = new ConcurrentHashMap();
        @NotNull
        private final ConcurrentHashMap<String, BossBar> bossBarAlways = new ConcurrentHashMap();

        @NotNull
        public final ConcurrentHashMap<String, Couple<BossBar, Long>> getBossBar() {
            return this.bossBar;
        }

        @NotNull
        public final ConcurrentHashMap<String, BossBar> getBossBarAlways() {
            return this.bossBarAlways;
        }
    }
}

