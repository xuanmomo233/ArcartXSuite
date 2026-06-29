/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptOptions
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptOptions$ScriptOptionsBuilder
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.Trigger;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptOptions;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import ink.ptms.chemdah.util.debug.Debug;
import ink.ptms.chemdah.util.debug.DebugHandlerKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0011\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0086\b\u0018\u0000 (2\u00020\u0001:\u0002'(B\u001f\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u000b\u0010\u0018\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0007H\u00c6\u0003J)\u0010\u001b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u001c\u001a\u00020\u001d2\b\u0010\u001e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\u0014\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u000b0 2\u0006\u0010!\u001a\u00020\"J\t\u0010#\u001a\u00020$H\u00d6\u0001J\t\u0010%\u001a\u00020&H\u00d6\u0001R \u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017\u00a8\u0006)"}, d2={"Link/ptms/chemdah/core/conversation/ConversationSwitch;", "", "file", "Ljava/io/File;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "npcId", "Link/ptms/chemdah/core/conversation/Trigger;", "(Ljava/io/File;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/conversation/Trigger;)V", "cases", "", "Link/ptms/chemdah/core/conversation/ConversationSwitch$Case;", "getCases", "()Ljava/util/List;", "setCases", "(Ljava/util/List;)V", "getFile", "()Ljava/io/File;", "getNpcId", "()Link/ptms/chemdah/core/conversation/Trigger;", "setNpcId", "(Link/ptms/chemdah/core/conversation/Trigger;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "component1", "component2", "component3", "copy", "equals", "", "other", "get", "Ljava/util/concurrent/CompletableFuture;", "player", "Lorg/bukkit/entity/Player;", "hashCode", "", "toString", "", "Case", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationSwitch.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,134:1\n1549#2:135\n1620#2,3:136\n30#3:139\n*S KotlinDebug\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch\n*L\n31#1:135\n31#1:136,3\n43#1:139\n*E\n"})
public final class ConversationSwitch {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @Nullable
    private final File file;
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private Trigger npcId;
    @NotNull
    private List<Case> cases;
    @NotNull
    private static final HashMap<String, ConversationSwitch> switchMap = new HashMap();

    /*
     * WARNING - void declaration
     */
    public ConversationSwitch(@Nullable File file, @NotNull ConfigurationSection root2, @NotNull Trigger npcId) {
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)npcId, (String)"npcId");
        this.file = file;
        this.root = root2;
        this.npcId = npcId;
        Iterable iterable = this.root.getMapList("when");
        ConversationSwitch conversationSwitch = this;
        boolean $i$f$map = false;
        void var6_7 = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            Map map = (Map)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            Object v = it.get("if");
            if (v == null) {
                v = it.get("condition");
            }
            collection.add(new Case(String.valueOf(v), (Map<?, ?>)it));
        }
        conversationSwitch.cases = (List)destination$iv$iv;
    }

    @Nullable
    public final File getFile() {
        return this.file;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final Trigger getNpcId() {
        return this.npcId;
    }

    public final void setNpcId(@NotNull Trigger trigger2) {
        Intrinsics.checkNotNullParameter((Object)trigger2, (String)"<set-?>");
        this.npcId = trigger2;
    }

    @NotNull
    public final List<Case> getCases() {
        return this.cases;
    }

    public final void setCases(@NotNull List<Case> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.cases = list2;
    }

    @NotNull
    public final CompletableFuture<Case> get(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        DebugHandlerKt.debug((Entity)player2, Debug.CONVERSATION, "Switch \u5f00\u59cb\u6761\u4ef6\u5339\u914d\uff0c\u5206\u652f\u6570: " + this.cases.size());
        CompletableFuture<Case> future = new CompletableFuture<Case>();
        ConversationSwitch.get$process(this, player2, future, 0);
        return future;
    }

    @Nullable
    public final File component1() {
        return this.file;
    }

    @NotNull
    public final ConfigurationSection component2() {
        return this.root;
    }

    @NotNull
    public final Trigger component3() {
        return this.npcId;
    }

    @NotNull
    public final ConversationSwitch copy(@Nullable File file, @NotNull ConfigurationSection root2, @NotNull Trigger npcId) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)npcId, (String)"npcId");
        return new ConversationSwitch(file, root2, npcId);
    }

    public static /* synthetic */ ConversationSwitch copy$default(ConversationSwitch conversationSwitch, File file, ConfigurationSection configurationSection, Trigger trigger2, int n, Object object) {
        if ((n & 1) != 0) {
            file = conversationSwitch.file;
        }
        if ((n & 2) != 0) {
            configurationSection = conversationSwitch.root;
        }
        if ((n & 4) != 0) {
            trigger2 = conversationSwitch.npcId;
        }
        return conversationSwitch.copy(file, configurationSection, trigger2);
    }

    @NotNull
    public String toString() {
        return "ConversationSwitch(file=" + this.file + ", root=" + this.root + ", npcId=" + this.npcId + ')';
    }

    public int hashCode() {
        int result = this.file == null ? 0 : this.file.hashCode();
        result = result * 31 + this.root.hashCode();
        result = result * 31 + this.npcId.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConversationSwitch)) {
            return false;
        }
        ConversationSwitch conversationSwitch = (ConversationSwitch)other;
        if (!Intrinsics.areEqual((Object)this.file, (Object)conversationSwitch.file)) {
            return false;
        }
        if (!Intrinsics.areEqual((Object)this.root, (Object)conversationSwitch.root)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.npcId, (Object)conversationSwitch.npcId);
    }

    private static final void get$process$lambda$1(Player $player, int $cur, CompletableFuture $future, ConversationSwitch this$0, Object it) {
        Intrinsics.checkNotNullParameter((Object)$player, (String)"$player");
        Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        Object $this$cbool$iv = it;
        boolean $i$f$getCbool = false;
        if (Coerce.toBoolean((Object)$this$cbool$iv)) {
            DebugHandlerKt.debug((Entity)$player, Debug.CONVERSATION, "Switch \u5206\u652f " + $cur + " \u5339\u914d\u6210\u529f");
            $future.complete(this$0.cases.get($cur));
        } else {
            DebugHandlerKt.debug((Entity)$player, Debug.CONVERSATION, "Switch \u5206\u652f " + $cur + " \u4e0d\u5339\u914d");
            ConversationSwitch.get$process(this$0, $player, $future, $cur + 1);
        }
    }

    private static final void get$process(ConversationSwitch this$0, Player $player, CompletableFuture<Case> future, int cur) {
        if (cur < this$0.cases.size()) {
            DebugHandlerKt.debug((Entity)$player, Debug.CONVERSATION, "Switch \u8bc4\u4f30\u5206\u652f " + cur + ": " + StringsKt.take((String)this$0.cases.get(cur).getCondition(), (int)40));
            KetherShell.INSTANCE.eval(this$0.cases.get(cur).getCondition(), ScriptOptions.Companion.new((Function1)new Function1<ScriptOptions.ScriptOptionsBuilder, Unit>($player){
                final /* synthetic */ Player $player;
                {
                    this.$player = $player;
                    super(1);
                }

                public final void invoke(@NotNull ScriptOptions.ScriptOptionsBuilder $this$new) {
                    Intrinsics.checkNotNullParameter((Object)$this$new, (String)"$this$new");
                    $this$new.sender((Object)this.$player).namespace(UtilsForKetherKt.getNamespace()).sandbox(true);
                }
            })).thenAccept(arg_0 -> ConversationSwitch.get$process$lambda$1($player, cur, future, this$0, arg_0));
        } else {
            DebugHandlerKt.debug((Entity)$player, Debug.CONVERSATION, "Switch \u6240\u6709\u5206\u652f\u5747\u4e0d\u5339\u914d");
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010$\n\u0002\b\b\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u000e\u0010\u0004\u001a\n\u0012\u0002\b\u0003\u0012\u0002\b\u00030\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\u0011\u0010\u0012\u001a\n\u0012\u0002\b\u0003\u0012\u0002\b\u00030\u0005H\u00c6\u0003J%\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u0010\b\u0002\u0010\u0004\u001a\n\u0012\u0002\b\u0003\u0012\u0002\b\u00030\u0005H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\u0010\u0010\t\u001a\u0004\u0018\u00010\u00192\u0006\u0010\u001a\u001a\u00020\u001bJ\t\u0010\u001c\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u0004\u0018\u00010\u00038F\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\bR\u0019\u0010\u0004\u001a\n\u0012\u0002\b\u0003\u0012\u0002\b\u00030\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0019\u0010\r\u001a\n\u0012\u0004\u0012\u00020\u0003\u0018\u00010\u000e8F\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/core/conversation/ConversationSwitch$Case;", "", "condition", "", "root", "", "(Ljava/lang/String;Ljava/util/Map;)V", "getCondition", "()Ljava/lang/String;", "open", "getOpen", "getRoot", "()Ljava/util/Map;", "run", "", "getRun", "()Ljava/util/List;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "Link/ptms/chemdah/core/conversation/Conversation;", "player", "Lorg/bukkit/entity/Player;", "toString", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nConversationSwitch.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch$Case\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,134:1\n1360#2:135\n1446#2,5:136\n*S KotlinDebug\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch$Case\n*L\n65#1:135\n65#1:136,5\n*E\n"})
    public static final class Case {
        @NotNull
        private final String condition;
        @NotNull
        private final Map<?, ?> root;

        public Case(@NotNull String condition, @NotNull Map<?, ?> root2) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter(root2, (String)"root");
            this.condition = condition;
            this.root = root2;
        }

        @NotNull
        public final String getCondition() {
            return this.condition;
        }

        @NotNull
        public final Map<?, ?> getRoot() {
            return this.root;
        }

        /*
         * WARNING - void declaration
         */
        @Nullable
        public final List<String> getRun() {
            List list2;
            Object object = this.root.get("run");
            if (object != null && (object = CollectionKt.asList(object)) != null) {
                void $this$flatMapTo$iv$iv;
                Iterable $this$flatMap$iv = (Iterable)object;
                boolean $i$f$flatMap = false;
                Iterable iterable = $this$flatMap$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$flatMapTo = false;
                for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
                    String it = (String)element$iv$iv;
                    boolean bl = false;
                    Iterable list$iv$iv = StringsKt.lines((CharSequence)it);
                    CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                }
                list2 = (List)destination$iv$iv;
            } else {
                list2 = null;
            }
            return list2;
        }

        @Nullable
        public final String getOpen() {
            Object obj = this.root.get("open");
            return obj != null ? obj.toString() : null;
        }

        @Nullable
        public final Conversation open(@NotNull Player player2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            if (this.getRun() != null) {
                DebugHandlerKt.debug((Entity)player2, Debug.CONVERSATION, "Switch Case \u6267\u884c\u811a\u672c");
                List<String> list2 = this.getRun();
                Intrinsics.checkNotNull(list2);
                KetherShell.INSTANCE.eval(list2, ScriptOptions.Companion.new((Function1)new Function1<ScriptOptions.ScriptOptionsBuilder, Unit>(player2){
                    final /* synthetic */ Player $player;
                    {
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptOptions.ScriptOptionsBuilder $this$new) {
                        Intrinsics.checkNotNullParameter((Object)$this$new, (String)"$this$new");
                        $this$new.sender((Object)this.$player).namespace(UtilsForKetherKt.getNamespace()).sandbox(true);
                    }
                }));
            } else if (this.getOpen() != null) {
                String string = this.getOpen();
                Intrinsics.checkNotNull((Object)string);
                String id2 = KetherFunction.INSTANCE.parse(string, ScriptOptions.Companion.new((Function1)new Function1<ScriptOptions.ScriptOptionsBuilder, Unit>(player2){
                    final /* synthetic */ Player $player;
                    {
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptOptions.ScriptOptionsBuilder $this$new) {
                        Intrinsics.checkNotNullParameter((Object)$this$new, (String)"$this$new");
                        $this$new.sender((Object)this.$player).namespace(UtilsForKetherKt.getNamespace()).sandbox(true);
                    }
                }));
                Conversation conversation2 = ChemdahAPI.INSTANCE.getConversation(id2);
                if (conversation2 != null) {
                    DebugHandlerKt.debug((Entity)player2, Debug.CONVERSATION, "Switch Case \u8df3\u8f6c\u5bf9\u8bdd: " + id2);
                } else {
                    DebugHandlerKt.debug((Entity)player2, Debug.CONVERSATION, "Switch Case \u8df3\u8f6c\u5bf9\u8bdd\u5931\u8d25\uff0c\u672a\u627e\u5230: " + id2);
                }
                return conversation2;
            }
            return null;
        }

        @NotNull
        public final String component1() {
            return this.condition;
        }

        @NotNull
        public final Map<?, ?> component2() {
            return this.root;
        }

        @NotNull
        public final Case copy(@NotNull String condition, @NotNull Map<?, ?> root2) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter(root2, (String)"root");
            return new Case(condition, root2);
        }

        public static /* synthetic */ Case copy$default(Case case_, String string, Map map, int n, Object object) {
            if ((n & 1) != 0) {
                string = case_.condition;
            }
            if ((n & 2) != 0) {
                map = case_.root;
            }
            return case_.copy(string, map);
        }

        @NotNull
        public String toString() {
            return "Case(condition=" + this.condition + ", root=" + this.root + ')';
        }

        public int hashCode() {
            int result = this.condition.hashCode();
            result = result * 31 + ((Object)this.root).hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Case)) {
                return false;
            }
            Case case_ = (Case)other;
            if (!Intrinsics.areEqual((Object)this.condition, (Object)case_.condition)) {
                return false;
            }
            return Intrinsics.areEqual(this.root, case_.root);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0003J\u0010\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000fH\u0003R-\u0010\u0003\u001a\u001e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004j\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u0006`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/conversation/ConversationSwitch$Companion;", "", "()V", "switchMap", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/core/conversation/ConversationSwitch;", "Lkotlin1822/collections/HashMap;", "getSwitchMap", "()Ljava/util/HashMap;", "onLoad", "", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Load;", "onSelect", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Select;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nConversationSwitch.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,134:1\n1549#2:135\n1620#2,3:136\n766#2:139\n857#2,2:140\n1620#2,3:142\n288#2:145\n1747#2,3:146\n289#2:149\n*S KotlinDebug\n*F\n+ 1 ConversationSwitch.kt\nink/ptms/chemdah/core/conversation/ConversationSwitch$Companion\n*L\n106#1:135\n106#1:136,3\n106#1:139\n106#1:140,2\n106#1:142,3\n117#1:145\n117#1:146,3\n117#1:149\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final HashMap<String, ConversationSwitch> getSwitchMap() {
            return switchMap;
        }

        /*
         * WARNING - void declaration
         */
        @SubscribeEvent
        private final void onLoad(ConversationEvents.Load e) {
            if (e.getRoot().contains("when") && !((Collection)e.getRoot().getMapList("when")).isEmpty()) {
                Trigger trigger2;
                e.setCancelled(true);
                Object id2 = e.getRoot().get("npc id");
                if (id2 != null) {
                    Iterable $this$filterTo$iv$iv;
                    Iterable $this$filter$iv;
                    List it;
                    Collection collection;
                    void $this$mapTo$iv$iv;
                    Iterable $this$map$iv = CollectionKt.asList((Object)id2);
                    boolean $i$f$map = false;
                    Iterable iterable = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (Object item$iv$iv : $this$mapTo$iv$iv) {
                        String string = (String)item$iv$iv;
                        collection = destination$iv$iv;
                        boolean bl = false;
                        char[] cArray = new char[]{' '};
                        collection.add(StringsKt.split$default((CharSequence)((CharSequence)((Object)it)), (char[])cArray, (boolean)false, (int)0, (int)6, null));
                    }
                    $this$map$iv = (List)destination$iv$iv;
                    boolean $i$f$filter = false;
                    $this$mapTo$iv$iv = $this$filter$iv;
                    destination$iv$iv = new ArrayList();
                    boolean $i$f$filterTo = false;
                    for (Object element$iv$iv : $this$filterTo$iv$iv) {
                        it = (List)element$iv$iv;
                        boolean bl = false;
                        if (!(it.size() >= 2)) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    $this$filter$iv = (List)destination$iv$iv;
                    $i$f$map = false;
                    $this$filterTo$iv$iv = $this$map$iv;
                    destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    $i$f$mapTo = false;
                    for (Object item$iv$iv : $this$mapTo$iv$iv) {
                        it = (List)item$iv$iv;
                        collection = destination$iv$iv;
                        boolean bl = false;
                        collection.add(new Trigger.Id((String)it.get(0), (String)it.get(1), CollectionsKt.drop((Iterable)it, (int)2)));
                    }
                    List list2 = (List)destination$iv$iv;
                    trigger2 = new Trigger(list2);
                } else {
                    trigger2 = new Trigger();
                }
                Trigger trigger3 = trigger2;
                ((Map)this.getSwitchMap()).put(e.getRoot().getName(), new ConversationSwitch(e.getFile(), e.getRoot(), trigger3));
            }
        }

        @SubscribeEvent(priority=EventPriority.HIGH, ignoreCancelled=true)
        private final void onSelect(ConversationEvents.Select e) {
            if (e.getConversation() == null) {
                Object v3;
                block11: {
                    Collection<ConversationSwitch> collection = this.getSwitchMap().values();
                    Intrinsics.checkNotNullExpressionValue(collection, (String)"switchMap.values");
                    Iterable $this$firstOrNull$iv = collection;
                    boolean $i$f$firstOrNull = false;
                    for (Object element$iv : $this$firstOrNull$iv) {
                        boolean bl;
                        block10: {
                            ConversationSwitch it = (ConversationSwitch)element$iv;
                            boolean bl2 = false;
                            Iterable $this$any$iv = it.getNpcId().getId();
                            boolean $i$f$any = false;
                            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                                bl = false;
                            } else {
                                for (Object element$iv2 : $this$any$iv) {
                                    boolean bl3;
                                    block9: {
                                        Trigger.Id npc = (Trigger.Id)element$iv2;
                                        boolean bl4 = false;
                                        Iterable $this$any$iv2 = e.getId();
                                        boolean $i$f$any2 = false;
                                        if ($this$any$iv2 instanceof Collection && ((Collection)$this$any$iv2).isEmpty()) {
                                            bl3 = false;
                                        } else {
                                            for (Object element$iv3 : $this$any$iv2) {
                                                String id2 = (String)element$iv3;
                                                boolean bl5 = false;
                                                if (!npc.isNPC(e.getNamespace(), id2, e.getPlayer())) continue;
                                                bl3 = true;
                                                break block9;
                                            }
                                            bl3 = false;
                                        }
                                    }
                                    if (!bl3) continue;
                                    bl = true;
                                    break block10;
                                }
                                bl = false;
                            }
                        }
                        if (!bl) continue;
                        v3 = element$iv;
                        break block11;
                    }
                    v3 = null;
                }
                ConversationSwitch ele = v3;
                if (ele == null) {
                    return;
                }
                DebugHandlerKt.debug((Entity)e.getPlayer(), Debug.CONVERSATION, "Switch \u5339\u914d\u5230 NPC \u7ed1\u5b9a\uff0c\u5f00\u59cb\u8bc4\u4f30");
                FuturesKt.applyWithError(ele.get(e.getPlayer()), (Function1)new Function1<Case, Unit>(e){
                    final /* synthetic */ ConversationEvents.Select $e;
                    {
                        this.$e = $e;
                        super(1);
                    }

                    public final void invoke(@NotNull Case case_) {
                        Intrinsics.checkNotNullParameter((Object)case_, (String)"case");
                        Conversation find = case_.open(this.$e.getPlayer());
                        if (find != null) {
                            DebugHandlerKt.debug((Entity)this.$e.getPlayer(), Debug.CONVERSATION, "Switch \u6700\u7ec8\u9009\u62e9\u5bf9\u8bdd: " + find.getId());
                            this.$e.setConversation(find);
                        } else {
                            DebugHandlerKt.debug((Entity)this.$e.getPlayer(), Debug.CONVERSATION, "Switch Case \u672a\u8fd4\u56de\u5bf9\u8bdd");
                        }
                    }
                });
            }
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

