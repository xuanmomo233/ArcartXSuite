/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$VarTable
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptOptions
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptOptions$ScriptOptionsBuilder
 *  kotlin.Metadata
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.jvm.internal.SpreadBuilder
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.ScriptOptions;
import ink.ptms.chemdah.util.StringNumber;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.jvm.internal.SpreadBuilder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000Z\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\u000e\u0010\r\u001a\u00020\u000e*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u0011\u001a\u00020\u0012*\u00060\u000fj\u0002`\u0010\u001a\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u0015\u001a\u00020\u0016*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u0017\u001a\u00020\u0002*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u0018\u001a\u00020\u0019*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u001a\u001a\u00020\u001b*\u00060\u000fj\u0002`\u0010\u001a\u000e\u0010\u001c\u001a\u00020\u001d*\u00060\u000fj\u0002`\u0010\u001a\u0014\u0010\u001e\u001a\u00020\u001f*\u0004\u0018\u00010\u001f2\u0006\u0010 \u001a\u00020\u001f\u001a\u0012\u0010!\u001a\u00020\u0002*\u00020\u00022\u0006\u0010\"\u001a\u00020\u0012\u001a\u000e\u0010#\u001a\u00020$*\u00060\u000fj\u0002`\u0010\u001a.\u0010%\u001a\"\u0012\u0004\u0012\u00020\u0002\u0012\u0006\u0012\u0004\u0018\u00010\u001f0&j\u0010\u0012\u0004\u0012\u00020\u0002\u0012\u0006\u0012\u0004\u0018\u00010\u001f`'*\u00060\u000fj\u0002`\u0010\"\u0017\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0003\u0010\u0004\"\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0004\"\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0004\"\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0004\"\u0017\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0004\u00a8\u0006("}, d2={"namespace", "", "", "getNamespace", "()Ljava/util/List;", "namespaceConversationNPC", "getNamespaceConversationNPC", "namespaceConversationPlayer", "getNamespaceConversationPlayer", "namespaceQuest", "getNamespaceQuest", "namespaceQuestUI", "getNamespaceQuestUI", "UI", "Link/ptms/chemdah/module/ui/UI;", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "getBukkitPlayer", "Lorg/bukkit/entity/Player;", "getProfile", "Link/ptms/chemdah/core/PlayerProfile;", "getQuestContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "getQuestSelected", "getSession", "Link/ptms/chemdah/core/conversation/Session;", "getTask", "Link/ptms/chemdah/core/quest/Task;", "getTemplate", "Link/ptms/chemdah/core/quest/Template;", "increaseAny", "", "any", "parseKether", "player", "rootVariables", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$VarTable;", "vars", "Ljava/util/HashMap;", "Lkotlin1822/collections/HashMap;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUtilsForKether.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UtilsForKether.kt\nink/ptms/chemdah/util/UtilsForKetherKt\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,100:1\n1549#2:101\n1620#2,3:102\n1549#2:105\n1620#2,3:106\n37#3,2:109\n37#3,2:111\n37#3,2:113\n37#3,2:115\n*S KotlinDebug\n*F\n+ 1 UtilsForKether.kt\nink/ptms/chemdah/util/UtilsForKetherKt\n*L\n71#1:101\n71#1:102,3\n74#1:105\n74#1:106,3\n23#1:109,2\n25#1:111,2\n27#1:113,2\n29#1:115,2\n*E\n"})
public final class UtilsForKetherKt {
    @NotNull
    private static final List<String> namespace;
    @NotNull
    private static final List<String> namespaceQuest;
    @NotNull
    private static final List<String> namespaceQuestUI;
    @NotNull
    private static final List<String> namespaceConversationNPC;
    @NotNull
    private static final List<String> namespaceConversationPlayer;

    @NotNull
    public static final List<String> getNamespace() {
        return namespace;
    }

    @NotNull
    public static final List<String> getNamespaceQuest() {
        return namespaceQuest;
    }

    @NotNull
    public static final List<String> getNamespaceQuestUI() {
        return namespaceQuestUI;
    }

    @NotNull
    public static final List<String> getNamespaceConversationNPC() {
        return namespaceConversationNPC;
    }

    @NotNull
    public static final List<String> getNamespaceConversationPlayer() {
        return namespaceConversationPlayer;
    }

    @NotNull
    public static final String getQuestSelected(@NotNull QuestContext.Frame $this$getQuestSelected) {
        Intrinsics.checkNotNullParameter((Object)$this$getQuestSelected, (String)"<this>");
        Object object = $this$getQuestSelected.variables().get("@QuestSelected").orElse(null);
        if (object == null || (object = object.toString()) == null) {
            throw new IllegalStateException("No quest selected.".toString());
        }
        return object;
    }

    @NotNull
    public static final QuestContainer getQuestContainer(@NotNull QuestContext.Frame $this$getQuestContainer) {
        Intrinsics.checkNotNullParameter((Object)$this$getQuestContainer, (String)"<this>");
        Object var1_1 = $this$getQuestContainer.variables().get("@QuestContainer").orElse(null);
        QuestContainer questContainer = var1_1 instanceof QuestContainer ? (QuestContainer)var1_1 : null;
        if (questContainer == null) {
            throw new IllegalStateException("No quest container selected.".toString());
        }
        return questContainer;
    }

    @NotNull
    public static final UI UI(@NotNull QuestContext.Frame $this$UI) {
        Intrinsics.checkNotNullParameter((Object)$this$UI, (String)"<this>");
        Object var1_1 = $this$UI.variables().get("@QuestUI").orElse(null);
        UI uI = var1_1 instanceof UI ? (UI)var1_1 : null;
        if (uI == null) {
            throw new IllegalStateException("No quest ui selected.".toString());
        }
        return uI;
    }

    @NotNull
    public static final Template getTemplate(@NotNull QuestContext.Frame $this$getTemplate) {
        Intrinsics.checkNotNullParameter((Object)$this$getTemplate, (String)"<this>");
        QuestContainer questContainer = UtilsForKetherKt.getQuestContainer($this$getTemplate);
        Intrinsics.checkNotNull((Object)questContainer, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.Template");
        return (Template)questContainer;
    }

    @NotNull
    public static final Task getTask(@NotNull QuestContext.Frame $this$getTask) {
        Intrinsics.checkNotNullParameter((Object)$this$getTask, (String)"<this>");
        QuestContainer questContainer = UtilsForKetherKt.getQuestContainer($this$getTask);
        Intrinsics.checkNotNull((Object)questContainer, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.Task");
        return (Task)questContainer;
    }

    @NotNull
    public static final Session getSession(@NotNull QuestContext.Frame $this$getSession) {
        Intrinsics.checkNotNullParameter((Object)$this$getSession, (String)"<this>");
        Optional optional = $this$getSession.variables().get("@Session");
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"variables().get<Any?>(\"@Session\")");
        Object session = OptionalKt.orNull((Optional)optional);
        if (session instanceof Session) {
            return (Session)session;
        }
        Session session2 = ChemdahAPI.INSTANCE.getConversationSession(UtilsForKetherKt.getBukkitPlayer($this$getSession));
        if (session2 == null) {
            throw new IllegalStateException("No session selected.".toString());
        }
        return session2;
    }

    @Nullable
    public static final PlayerProfile getProfile(@NotNull QuestContext.Frame $this$getProfile) {
        Intrinsics.checkNotNullParameter((Object)$this$getProfile, (String)"<this>");
        Player bukkitPlayer = UtilsForKetherKt.getBukkitPlayer($this$getProfile);
        return ChemdahAPI.INSTANCE.isChemdahProfileLoaded(bukkitPlayer) ? ChemdahAPI.INSTANCE.getChemdahProfile(UtilsForKetherKt.getBukkitPlayer($this$getProfile)) : null;
    }

    @NotNull
    public static final Player getBukkitPlayer(@NotNull QuestContext.Frame $this$getBukkitPlayer) {
        Intrinsics.checkNotNullParameter((Object)$this$getBukkitPlayer, (String)"<this>");
        ProxyCommandSender proxyCommandSender = KetherHelperKt.script((QuestContext.Frame)$this$getBukkitPlayer).getSender();
        if (proxyCommandSender == null || (proxyCommandSender = (Player)proxyCommandSender.castSafely()) == null) {
            throw new IllegalStateException("No player selected.".toString());
        }
        return proxyCommandSender;
    }

    @NotNull
    public static final HashMap<String, Object> vars(@NotNull QuestContext.Frame $this$vars) {
        String it;
        Collection collection;
        Iterable $this$mapTo$iv$iv;
        boolean $i$f$mapTo;
        Collection destination$iv$iv;
        Iterable $this$map$iv;
        boolean $i$f$map;
        Map map;
        HashMap<String, Object> hashMap;
        Intrinsics.checkNotNullParameter((Object)$this$vars, (String)"<this>");
        HashMap<String, Object> map2 = hashMap = new HashMap<String, Object>();
        boolean bl = false;
        Optional parent = $this$vars.parent();
        while (parent.isPresent()) {
            Map map3 = map2;
            Set set2 = ((QuestContext.Frame)parent.get()).variables().keys();
            Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"parent.get().variables().keys()");
            Iterable iterable = set2;
            map = map3;
            $i$f$map = false;
            Iterable iterable2 = $this$map$iv;
            destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                String string = (String)item$iv$iv;
                collection = destination$iv$iv;
                boolean bl2 = false;
                collection.add(TuplesKt.to((Object)it, $this$vars.variables().get(it).orElse(null)));
            }
            MapsKt.putAll((Map)map, (Iterable)((List)destination$iv$iv));
            parent = ((QuestContext.Frame)parent.get()).parent();
        }
        Map map4 = map2;
        Set set3 = $this$vars.variables().keys();
        Intrinsics.checkNotNullExpressionValue((Object)set3, (String)"variables().keys()");
        $this$map$iv = set3;
        map = map4;
        $i$f$map = false;
        $this$mapTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            it = (String)item$iv$iv;
            collection = destination$iv$iv;
            boolean bl3 = false;
            collection.add(TuplesKt.to((Object)it, $this$vars.variables().get(it).orElse(null)));
        }
        MapsKt.putAll((Map)map, (Iterable)((List)destination$iv$iv));
        return hashMap;
    }

    @NotNull
    public static final QuestContext.VarTable rootVariables(@NotNull QuestContext.Frame $this$rootVariables) {
        Intrinsics.checkNotNullParameter((Object)$this$rootVariables, (String)"<this>");
        QuestContext.VarTable vars2 = $this$rootVariables.variables();
        Optional parent = $this$rootVariables.parent();
        while (parent.isPresent()) {
            vars2 = ((QuestContext.Frame)parent.get()).variables();
            parent = ((QuestContext.Frame)parent.get()).parent();
        }
        QuestContext.VarTable varTable = vars2;
        Intrinsics.checkNotNullExpressionValue((Object)varTable, (String)"vars");
        return varTable;
    }

    @NotNull
    public static final Object increaseAny(@Nullable Object $this$increaseAny, @NotNull Object any) {
        Intrinsics.checkNotNullParameter((Object)any, (String)"any");
        if ($this$increaseAny == null) {
            return any;
        }
        Object object = new StringNumber($this$increaseAny.toString()).add(any.toString()).get();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"StringNumber(toString()).add(any.toString()).get()");
        return object;
    }

    @NotNull
    public static final String parseKether(@NotNull String $this$parseKether, @NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)$this$parseKether, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        return KetherFunction.INSTANCE.parse($this$parseKether, ScriptOptions.Companion.new((Function1)new Function1<ScriptOptions.ScriptOptionsBuilder, Unit>(player2){
            final /* synthetic */ Player $player;
            {
                this.$player = $player;
                super(1);
            }

            public final void invoke(@NotNull ScriptOptions.ScriptOptionsBuilder $this$new) {
                Intrinsics.checkNotNullParameter((Object)$this$new, (String)"$this$new");
                $this$new.sender((Object)this.$player);
                $this$new.namespace(UtilsForKetherKt.getNamespaceQuestUI());
            }
        }));
    }

    static {
        String[] stringArray = new String[]{"adyeshach", "chemdah"};
        namespace = CollectionsKt.listOf((Object[])stringArray);
        stringArray = new SpreadBuilder(2);
        Collection $this$toTypedArray$iv = namespace;
        boolean $i$f$toTypedArray = false;
        Collection thisCollection$iv = $this$toTypedArray$iv;
        stringArray.addSpread((Object)thisCollection$iv.toArray(new String[0]));
        stringArray.add((Object)"chemdah-quest");
        namespaceQuest = CollectionsKt.listOf((Object[])stringArray.toArray((Object[])new String[stringArray.size()]));
        stringArray = new SpreadBuilder(2);
        $this$toTypedArray$iv = namespaceQuest;
        $i$f$toTypedArray = false;
        thisCollection$iv = $this$toTypedArray$iv;
        stringArray.addSpread((Object)thisCollection$iv.toArray(new String[0]));
        stringArray.add((Object)"chemdah-quest-ui");
        namespaceQuestUI = CollectionsKt.listOf((Object[])stringArray.toArray((Object[])new String[stringArray.size()]));
        stringArray = new SpreadBuilder(3);
        $this$toTypedArray$iv = namespace;
        $i$f$toTypedArray = false;
        thisCollection$iv = $this$toTypedArray$iv;
        stringArray.addSpread((Object)thisCollection$iv.toArray(new String[0]));
        stringArray.add((Object)"chemdah-conversation");
        stringArray.add((Object)"chemdah-conversation-npc");
        namespaceConversationNPC = CollectionsKt.listOf((Object[])stringArray.toArray((Object[])new String[stringArray.size()]));
        stringArray = new SpreadBuilder(3);
        $this$toTypedArray$iv = namespace;
        $i$f$toTypedArray = false;
        thisCollection$iv = $this$toTypedArray$iv;
        stringArray.addSpread((Object)thisCollection$iv.toArray(new String[0]));
        stringArray.add((Object)"chemdah-conversation");
        stringArray.add((Object)"chemdah-conversation-player");
        namespaceConversationPlayer = CollectionsKt.listOf((Object[])stringArray.toArray((Object[])new String[stringArray.size()]));
    }
}

