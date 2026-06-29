/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.Agent;
import ink.ptms.chemdah.core.conversation.AgentType;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.ConversationTransfer;
import ink.ptms.chemdah.core.conversation.Option;
import ink.ptms.chemdah.core.conversation.PlayerSide;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.Source;
import ink.ptms.chemdah.core.conversation.Trigger;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.ConfigurationKt;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.ProcessBool;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0094\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0011\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u001e\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020>0=2\u0006\u0010?\u001a\u00020@2\u0006\u0010A\u001a\u00020BH\u0016J\u0016\u0010C\u001a\b\u0012\u0004\u0012\u00020D0=2\u0006\u0010?\u001a\u00020@H\u0016J\u0013\u0010E\u001a\u00020D2\b\u0010F\u001a\u0004\u0018\u00010\u0001H\u0096\u0002J!\u0010G\u001a\u00020D2\u0012\u0010H\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00030I\"\u00020\u0003H\u0016\u00a2\u0006\u0002\u0010JJ\b\u0010K\u001a\u00020LH\u0016J\u0018\u0010M\u001a\u00020D2\u0006\u0010N\u001a\u00020\u00032\u0006\u0010\u0002\u001a\u00020\u0003H\u0016J$\u0010O\u001a\u00020@\"\u0004\b\u0000\u0010P2\u0006\u0010Q\u001a\u00020R2\f\u0010S\u001a\b\u0012\u0004\u0012\u0002HP0TH\u0016J!\u0010U\u001a\u00020D2\u0012\u0010H\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00030I\"\u00020\u0003H\u0016\u00a2\u0006\u0002\u0010JJF\u0010V\u001a\b\u0012\u0004\u0012\u00020@0=\"\u0004\b\u0000\u0010P2\u0006\u0010Q\u001a\u00020R2\f\u0010S\u001a\b\u0012\u0004\u0012\u0002HP0T2\n\b\u0002\u0010W\u001a\u0004\u0018\u00010@2\u000e\b\u0002\u0010X\u001a\b\u0012\u0004\u0012\u00020@0YH\u0016J\u001e\u0010Z\u001a\b\u0012\u0004\u0012\u00020@0=2\u0006\u0010Q\u001a\u00020R2\u0006\u0010[\u001a\u00020\u0003H\u0016R\u0017\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001c\u0010\u0010\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0017\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00030\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u000fR\u001c\u0010\u0019\u001a\u0004\u0018\u00010\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u0012\"\u0004\b\u001b\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0012R\u001a\u0010\u001d\u001a\u00020\u001eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"R \u0010#\u001a\b\u0012\u0004\u0012\u00020\u00030\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b$\u0010\u000f\"\u0004\b%\u0010&R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b'\u0010(\"\u0004\b)\u0010*R\u001a\u0010+\u001a\u00020,X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b-\u0010.\"\u0004\b/\u00100R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u00102R\u0015\u00103\u001a\u0006\u0012\u0002\b\u0003048F\u00a2\u0006\u0006\u001a\u0004\b5\u00106R\u001a\u00107\u001a\u000208X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b9\u0010:\"\u0004\b;\u0010<\u00a8\u0006\\"}, d2={"Link/ptms/chemdah/core/conversation/Conversation;", "", "id", "", "file", "Ljava/io/File;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "option", "Link/ptms/chemdah/core/conversation/Option;", "(Ljava/lang/String;Ljava/io/File;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/conversation/Option;)V", "agent", "", "Link/ptms/chemdah/core/conversation/Agent;", "getAgent", "()Ljava/util/List;", "condition", "getCondition", "()Ljava/lang/String;", "setCondition", "(Ljava/lang/String;)V", "getFile", "()Ljava/io/File;", "flags", "getFlags", "format", "getFormat", "setFormat", "getId", "npcId", "Link/ptms/chemdah/core/conversation/Trigger;", "getNpcId", "()Link/ptms/chemdah/core/conversation/Trigger;", "setNpcId", "(Link/ptms/chemdah/core/conversation/Trigger;)V", "npcSide", "getNpcSide", "setNpcSide", "(Ljava/util/List;)V", "getOption", "()Link/ptms/chemdah/core/conversation/Option;", "setOption", "(Link/ptms/chemdah/core/conversation/Option;)V", "playerSide", "Link/ptms/chemdah/core/conversation/PlayerSide;", "getPlayerSide", "()Link/ptms/chemdah/core/conversation/PlayerSide;", "setPlayerSide", "(Link/ptms/chemdah/core/conversation/PlayerSide;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "theme", "Link/ptms/chemdah/core/conversation/theme/Theme;", "getTheme", "()Link/ptms/chemdah/core/conversation/theme/Theme;", "transfer", "Link/ptms/chemdah/core/conversation/ConversationTransfer;", "getTransfer", "()Link/ptms/chemdah/core/conversation/ConversationTransfer;", "setTransfer", "(Link/ptms/chemdah/core/conversation/ConversationTransfer;)V", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "session", "Link/ptms/chemdah/core/conversation/Session;", "type", "Link/ptms/chemdah/core/conversation/AgentType;", "checkCondition", "", "equals", "other", "hasFlag", "value", "", "([Ljava/lang/String;)Z", "hashCode", "", "isNPC", "namespace", "newSession", "T", "player", "Lorg/bukkit/entity/Player;", "source", "Link/ptms/chemdah/core/conversation/Source;", "noFlag", "open", "parent", "prepare", "Ljava/util/function/Consumer;", "openSelf", "name", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversation.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Conversation.kt\nink/ptms/chemdah/core/conversation/Conversation\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 5 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,310:1\n1549#2:311\n1620#2,3:312\n766#2:315\n857#2,2:316\n1620#2,3:318\n1747#2,3:324\n766#2:327\n857#2,2:328\n1#3:321\n12744#4,2:322\n29#5:330\n29#5:331\n*S KotlinDebug\n*F\n+ 1 Conversation.kt\nink/ptms/chemdah/core/conversation/Conversation\n*L\n52#1:311\n52#1:312,3\n52#1:315\n52#1:316,2\n52#1:318,3\n109#1:324,3\n269#1:327\n269#1:328,2\n95#1:322,2\n246#1:330\n278#1:331\n*E\n"})
public class Conversation {
    @NotNull
    private final String id;
    @Nullable
    private final File file;
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private Option option;
    @NotNull
    private ConversationTransfer transfer;
    @NotNull
    private Trigger npcId;
    @NotNull
    private List<String> npcSide;
    @Nullable
    private String format;
    @Nullable
    private String condition;
    @NotNull
    private PlayerSide playerSide;
    @NotNull
    private final List<Agent> agent;
    @NotNull
    private final List<String> flags;

    /*
     * WARNING - void declaration
     */
    public Conversation(@NotNull String id2, @Nullable File file, @NotNull ConfigurationSection root2, @NotNull Option option) {
        void it;
        List list2;
        List list3;
        Conversation conversation2;
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        this.id = id2;
        this.file = file;
        this.root = root2;
        this.option = option;
        ConfigurationSection configurationSection = this.root.getConfigurationSection("transfer");
        if (configurationSection == null) {
            configurationSection = this.root.createSection("transfer");
        }
        this.transfer = new ConversationTransfer(configurationSection);
        Conversation conversation3 = this;
        Object object = this.root.get("npc id");
        if (object != null && (object = CollectionKt.asList((Object)object)) != null) {
            void $this$mapTo$iv$iv;
            void $this$map$iv;
            void $this$filterTo$iv$iv;
            void $this$filter$iv;
            Collection collection;
            void $this$mapTo$iv$iv2;
            void $this$map$iv2;
            Iterable iterable = (Iterable)object;
            conversation2 = conversation3;
            boolean $i$f$map22 = false;
            void var10_9 = $this$map$iv2;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv2, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv2) {
                void it2;
                String string = (String)item$iv$iv;
                collection = destination$iv$iv;
                boolean bl = false;
                char[] cArray = new char[]{' '};
                collection.add(StringsKt.split$default((CharSequence)((CharSequence)it2), (char[])cArray, (boolean)false, (int)0, (int)6, null));
            }
            Iterable $i$f$map22 = (List)destination$iv$iv;
            boolean $i$f$filter22 = false;
            destination$iv$iv = $this$filter$iv;
            Collection destination$iv$iv2 = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                List it3 = (List)element$iv$iv;
                boolean bl = false;
                if (!(it3.size() == 2)) continue;
                destination$iv$iv2.add(element$iv$iv);
            }
            Iterable $i$f$filter22 = (List)destination$iv$iv2;
            boolean $i$f$map = false;
            destination$iv$iv2 = $this$map$iv;
            Collection destination$iv$iv3 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo2 = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it4;
                List bl = (List)item$iv$iv;
                collection = destination$iv$iv3;
                boolean bl2 = false;
                collection.add(new Trigger.Id((String)it4.get(0), (String)it4.get(1)));
            }
            list3 = (List)destination$iv$iv3;
            conversation3 = conversation2;
        } else {
            list3 = CollectionsKt.emptyList();
        }
        List list4 = list3;
        conversation3.npcId = new Trigger(list4);
        List list5 = this.root.get("npc");
        if (list5 == null || (list5 = CollectionKt.asList((Object)list5)) == null || (list5 = StringKt.flatLines(list5)) == null || (list5 = CollectionsKt.toMutableList((Collection)list5)) == null) {
            list5 = new ArrayList();
        }
        this.npcSide = list5;
        this.format = this.root.getString("format", "type");
        this.condition = this.root.getString("condition", this.root.getString("if"));
        this.playerSide = new PlayerSide(SectionsKt.mapListAs((ConfigurationSection)this.root, (String)"player", (Function1)playerSide.1.INSTANCE));
        this.agent = ConfigurationKt.sectionAs(this.root, "agent", agent.1.INSTANCE, agent.2.INSTANCE);
        List list6 = list2 = CollectionsKt.toMutableList((Collection)this.root.getStringList("flags"));
        conversation2 = this;
        boolean bl = false;
        it.addAll((Collection)this.option.getGlobalFlags());
        conversation2.flags = list2;
    }

    @NotNull
    public final String getId() {
        return this.id;
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
    public final Option getOption() {
        return this.option;
    }

    public final void setOption(@NotNull Option option) {
        Intrinsics.checkNotNullParameter((Object)option, (String)"<set-?>");
        this.option = option;
    }

    @NotNull
    public final ConversationTransfer getTransfer() {
        return this.transfer;
    }

    public final void setTransfer(@NotNull ConversationTransfer conversationTransfer) {
        Intrinsics.checkNotNullParameter((Object)conversationTransfer, (String)"<set-?>");
        this.transfer = conversationTransfer;
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
    public final List<String> getNpcSide() {
        return this.npcSide;
    }

    public final void setNpcSide(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.npcSide = list2;
    }

    @Nullable
    public final String getFormat() {
        return this.format;
    }

    public final void setFormat(@Nullable String string) {
        this.format = string;
    }

    @Nullable
    public final String getCondition() {
        return this.condition;
    }

    public final void setCondition(@Nullable String string) {
        this.condition = string;
    }

    @NotNull
    public final PlayerSide getPlayerSide() {
        return this.playerSide;
    }

    public final void setPlayerSide(@NotNull PlayerSide playerSide2) {
        Intrinsics.checkNotNullParameter((Object)playerSide2, (String)"<set-?>");
        this.playerSide = playerSide2;
    }

    @NotNull
    public final List<Agent> getAgent() {
        return this.agent;
    }

    @NotNull
    public final List<String> getFlags() {
        return this.flags;
    }

    @NotNull
    public final Theme<?> getTheme() {
        return this.option.getThemeInstance();
    }

    public boolean hasFlag(String ... value2) {
        boolean bl;
        block1: {
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            String[] $this$any$iv = value2;
            boolean $i$f$any = false;
            int n = $this$any$iv.length;
            for (int i = 0; i < n; ++i) {
                String element$iv;
                String it = element$iv = $this$any$iv[i];
                boolean bl2 = false;
                if (!this.flags.contains(it)) continue;
                bl = true;
                break block1;
            }
            bl = false;
        }
        return bl;
    }

    public boolean noFlag(String ... value2) {
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        return !this.hasFlag(Arrays.copyOf(value2, value2.length));
    }

    public boolean isNPC(@NotNull String namespace, @NotNull String id2) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Iterable $this$any$iv = this.npcId.getId();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    Trigger.Id it = (Trigger.Id)element$iv;
                    boolean bl2 = false;
                    if (!it.isNPC(namespace, id2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    @NotNull
    public <T> Session newSession(@NotNull Player player, @NotNull Source<T> source) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(source, (String)"source");
        Location location = player.getLocation().clone();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location.clone()");
        return new Session(this, location, source.getOriginLocation(source.getEntity()), player, source);
    }

    @NotNull
    public CompletableFuture<Session> openSelf(@NotNull Player player, @NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return Conversation.open$default(this, player, Source.Companion.of(player), null, null, 12, null);
    }

    @NotNull
    public <T> CompletableFuture<Session> open(@NotNull Player player, @NotNull Source<T> source, @Nullable Session parent, @NotNull Consumer<Session> prepare) {
        Session session;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(source, (String)"source");
        Intrinsics.checkNotNullParameter(prepare, (String)"prepare");
        CompletableFuture<Session> future = new CompletableFuture<Session>();
        Session session2 = parent;
        if (session2 == null) {
            session2 = this.newSession(player, source);
        }
        if (UtilsKt.callIfFailed(new ConversationEvents.Pre(this, session = session2, parent != null))) {
            future.complete(session);
            new ConversationEvents.Cancelled(this, session, true).call();
            return future;
        }
        FuturesKt.thenBool(this.checkCondition(session), (Function1<? super ProcessBool, Unit>)((Function1)new Function1<ProcessBool, Unit>(this, source, player, session, prepare, parent, future){
            final /* synthetic */ Conversation this$0;
            final /* synthetic */ Source<T> $source;
            final /* synthetic */ Player $player;
            final /* synthetic */ Session $session;
            final /* synthetic */ Consumer<Session> $prepare;
            final /* synthetic */ Session $parent;
            final /* synthetic */ CompletableFuture<Session> $future;
            {
                this.this$0 = $receiver;
                this.$source = $source;
                this.$player = $player;
                this.$session = $session;
                this.$prepare = $prepare;
                this.$parent = $parent;
                this.$future = $future;
                super(1);
            }

            public final void invoke(@NotNull ProcessBool $this$thenBool) {
                Intrinsics.checkNotNullParameter((Object)$this$thenBool, (String)"$this$thenBool");
                $this$thenBool.ifTrue((Function0<Unit>)((Function0)new Function0<Unit>(this.this$0, this.$source, this.$player, this.$session, this.$prepare, this.$parent, this.$future){
                    final /* synthetic */ Conversation this$0;
                    final /* synthetic */ Source<T> $source;
                    final /* synthetic */ Player $player;
                    final /* synthetic */ Session $session;
                    final /* synthetic */ Consumer<Session> $prepare;
                    final /* synthetic */ Session $parent;
                    final /* synthetic */ CompletableFuture<Session> $future;
                    {
                        this.this$0 = $receiver;
                        this.$source = $source;
                        this.$player = $player;
                        this.$session = $session;
                        this.$prepare = $prepare;
                        this.$parent = $parent;
                        this.$future = $future;
                        super(0);
                    }

                    public final void invoke() {
                        Object[] objectArray;
                        if (this.this$0.getTransfer().getId() != null) {
                            String string = this.this$0.getTransfer().getId();
                            Intrinsics.checkNotNull((Object)string);
                            if (!this.$source.transfer(this.$player, string)) {
                                objectArray = new Object[]{"Unable to conversation transfer to " + this.this$0.getTransfer() + " (conversation: " + this.this$0.getId() + ", player: " + this.$player.getName() + ')'};
                                IOKt.warning((Object[])objectArray);
                            }
                        }
                        objectArray = (Object[])ConversationManager.INSTANCE.getSessions();
                        String string = this.$player.getName();
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
                        String string2 = string;
                        Session session = this.$session;
                        objectArray.put(string2, session);
                        this.$prepare.accept(this.$session);
                        this.$session.setSource(this.$source);
                        this.$session.reload();
                        this.this$0.agent(this.$session, AgentType.BEGIN_ASYNC);
                        FuturesKt.acceptWithError(this.this$0.agent(this.$session, AgentType.BEGIN), (Function0<Unit>)((Function0)new Function0<Unit>(this.$session, this.$parent, this.$player, this.$future, this.this$0){
                            final /* synthetic */ Session $session;
                            final /* synthetic */ Session $parent;
                            final /* synthetic */ Player $player;
                            final /* synthetic */ CompletableFuture<Session> $future;
                            final /* synthetic */ Conversation this$0;
                            {
                                this.$session = $session;
                                this.$parent = $parent;
                                this.$player = $player;
                                this.$future = $future;
                                this.this$0 = $receiver;
                                super(0);
                            }

                            public final void invoke() {
                                FuturesKt.acceptWithError(this.$session.resetTheme(), (Function0<Unit>)((Function0)new Function0<Unit>(this.$session, this.$parent, this.$player, this.$future, this.this$0){
                                    final /* synthetic */ Session $session;
                                    final /* synthetic */ Session $parent;
                                    final /* synthetic */ Player $player;
                                    final /* synthetic */ CompletableFuture<Session> $future;
                                    final /* synthetic */ Conversation this$0;
                                    {
                                        this.$session = $session;
                                        this.$parent = $parent;
                                        this.$player = $player;
                                        this.$future = $future;
                                        this.this$0 = $receiver;
                                        super(0);
                                    }

                                    /*
                                     * WARNING - void declaration
                                     */
                                    public final void invoke() {
                                        Object $this$cbool$iv = this.$session.getVariables().get("@Cancelled");
                                        boolean $i$f$getCbool = false;
                                        if (Coerce.toBoolean((Object)$this$cbool$iv)) {
                                            if (this.$parent != null) {
                                                FuturesKt.acceptWithError(Session.close$default(this.$parent, false, 1, null), (Function0<Unit>)((Function0)new Function0<Unit>(this.$future, this.$session, this.this$0){
                                                    final /* synthetic */ CompletableFuture<Session> $future;
                                                    final /* synthetic */ Session $session;
                                                    final /* synthetic */ Conversation this$0;
                                                    {
                                                        this.$future = $future;
                                                        this.$session = $session;
                                                        this.this$0 = $receiver;
                                                        super(0);
                                                    }

                                                    public final void invoke() {
                                                        this.$future.complete(this.$session);
                                                        new ConversationEvents.Cancelled(this.this$0, this.$session, true).call();
                                                    }
                                                }));
                                            } else {
                                                ConversationManager.INSTANCE.getSessions().remove(this.$player.getName());
                                                this.$future.complete(this.$session);
                                                new ConversationEvents.Cancelled(this.this$0, this.$session, false).call();
                                            }
                                        } else {
                                            void $this$mapTo$iv$iv;
                                            void $this$map$iv;
                                            $this$cbool$iv = this.this$0.getNpcSide();
                                            Session session = this.$session;
                                            ArrayList<String> arrayList = this.$session.getNpcSide();
                                            boolean $i$f$map = false;
                                            void var4_6 = $this$map$iv;
                                            Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                                            boolean $i$f$mapTo = false;
                                            for (T item$iv$iv : $this$mapTo$iv$iv) {
                                                String string;
                                                String string2 = (String)item$iv$iv;
                                                Collection collection = destination$iv$iv;
                                                boolean bl = false;
                                                try {
                                                    void it;
                                                    string = KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, (String)it, (boolean)false, UtilsForKetherKt.getNamespaceConversationNPC(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                                                        final /* synthetic */ Session $session;
                                                        {
                                                            this.$session = $session;
                                                            super(1);
                                                        }

                                                        public final void invoke(@NotNull ScriptContext $this$parse) {
                                                            Intrinsics.checkNotNullParameter((Object)$this$parse, (String)"$this$parse");
                                                            KetherHelperKt.extend((ScriptContext)$this$parse, this.$session.getVariables());
                                                        }
                                                    }), (int)58, null);
                                                }
                                                catch (Throwable e) {
                                                    KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
                                                    string = e.getLocalizedMessage();
                                                }
                                                collection.add(string);
                                            }
                                            arrayList.addAll((List)destination$iv$iv);
                                            new ConversationEvents.Begin(this.this$0, this.$session, this.$parent != null).call();
                                            FuturesKt.acceptWithError(this.this$0.getOption().getThemeInstance().onBegin(this.$session), (Function0<Unit>)((Function0)new Function0<Unit>(this.this$0, this.$session, this.$future, this.$parent){
                                                final /* synthetic */ Conversation this$0;
                                                final /* synthetic */ Session $session;
                                                final /* synthetic */ CompletableFuture<Session> $future;
                                                final /* synthetic */ Session $parent;
                                                {
                                                    this.this$0 = $receiver;
                                                    this.$session = $session;
                                                    this.$future = $future;
                                                    this.$parent = $parent;
                                                    super(0);
                                                }

                                                public final void invoke() {
                                                    this.this$0.agent(this.$session, AgentType.START_ASYNC);
                                                    FuturesKt.acceptWithError(this.this$0.agent(this.$session, AgentType.START), (Function0<Unit>)((Function0)new Function0<Unit>(this.$future, this.$session, this.this$0, this.$parent){
                                                        final /* synthetic */ CompletableFuture<Session> $future;
                                                        final /* synthetic */ Session $session;
                                                        final /* synthetic */ Conversation this$0;
                                                        final /* synthetic */ Session $parent;
                                                        {
                                                            this.$future = $future;
                                                            this.$session = $session;
                                                            this.this$0 = $receiver;
                                                            this.$parent = $parent;
                                                            super(0);
                                                        }

                                                        public final void invoke() {
                                                            this.$future.complete(this.$session);
                                                            new ConversationEvents.Post(this.this$0, this.$session, this.$parent != null).call();
                                                        }
                                                    }));
                                                }
                                            }));
                                        }
                                    }
                                }));
                            }
                        }));
                    }
                }));
                $this$thenBool.orElse((Function0<Unit>)((Function0)new Function0<Unit>(this.$session, this.$player, this.$future, this.this$0){
                    final /* synthetic */ Session $session;
                    final /* synthetic */ Player $player;
                    final /* synthetic */ CompletableFuture<Session> $future;
                    final /* synthetic */ Conversation this$0;
                    {
                        this.$session = $session;
                        this.$player = $player;
                        this.$future = $future;
                        this.this$0 = $receiver;
                        super(0);
                    }

                    public final void invoke() {
                        Session.close$default(this.$session, false, 1, null);
                        ConversationManager.INSTANCE.getSessions().remove(this.$player.getName());
                        this.$future.complete(this.$session);
                        new ConversationEvents.Cancelled(this.this$0, this.$session, false).call();
                    }
                }));
            }
        }));
        return future;
    }

    public static /* synthetic */ CompletableFuture open$default(Conversation conversation2, Player player, Source source, Session session, Consumer consumer, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: open");
        }
        if ((n & 4) != 0) {
            session = null;
        }
        if ((n & 8) != 0) {
            consumer = Conversation::open$lambda$6;
        }
        return conversation2.open(player, source, session, consumer);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    @NotNull
    public CompletableFuture<Boolean> checkCondition(@NotNull Session session) {
        CompletableFuture<Boolean> future;
        CompletableFuture<Boolean> completableFuture;
        block4: {
            block3: {
                Intrinsics.checkNotNullParameter((Object)session, (String)"session");
                future = completableFuture = new CompletableFuture<Boolean>();
                boolean bl = false;
                if (this.condition == null) break block3;
                String string = this.condition;
                Intrinsics.checkNotNull((Object)string);
                if (!(((CharSequence)string).length() == 0)) break block4;
            }
            future.complete(true);
            return completableFuture;
        }
        try {
            String string = this.condition;
            Intrinsics.checkNotNull((Object)string);
            KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, (String)string, (boolean)false, UtilsForKetherKt.getNamespaceConversationNPC(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                final /* synthetic */ Session $session;
                {
                    this.$session = $session;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    KetherHelperKt.extend((ScriptContext)$this$eval, this.$session.getVariables());
                }
            }), (int)58, null).thenApply(arg_0 -> Conversation.checkCondition$lambda$8$lambda$7(future, arg_0));
            return completableFuture;
        }
        catch (Throwable e) {
            future.complete(false);
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
        }
        return completableFuture;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public CompletableFuture<Void> agent(@NotNull Session session, @NotNull AgentType type) {
        void $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        if (UtilsKt.callIfFailed(new ConversationEvents.Agent(this, session, type))) {
            future.complete(null);
            return future;
        }
        Iterable $this$filter$iv = this.agent;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Agent it = (Agent)element$iv$iv;
            boolean bl = false;
            if (!(it.getType() == type)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        List agents2 = (List)destination$iv$iv;
        Conversation.agent$process(agents2, type, session, future, 0);
        return future;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Conversation)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.id, (Object)((Conversation)other).id);
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    private static final void open$lambda$6(Session it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
    }

    private static final Boolean checkCondition$lambda$8$lambda$7(CompletableFuture $future, Object it) {
        Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
        Object $this$cbool$iv = it;
        boolean $i$f$getCbool = false;
        return $future.complete(Coerce.toBoolean((Object)$this$cbool$iv));
    }

    private static final Object agent$process$lambda$11(Session $session, CompletableFuture $future, int $cur, List $agents, AgentType $type, Object it) {
        Boolean bl;
        Intrinsics.checkNotNullParameter((Object)$session, (String)"$session");
        Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
        Intrinsics.checkNotNullParameter((Object)$agents, (String)"$agents");
        Intrinsics.checkNotNullParameter((Object)((Object)$type), (String)"$type");
        Object $this$cbool$iv = $session.getVariables().get("@Cancelled");
        boolean $i$f$getCbool = false;
        if (Coerce.toBoolean((Object)$this$cbool$iv)) {
            bl = $future.complete(null);
        } else {
            Conversation.agent$process($agents, $type, $session, $future, $cur + 1);
            bl = Unit.INSTANCE;
        }
        return bl;
    }

    private static final void agent$process(List<Agent> agents2, AgentType $type, Session $session, CompletableFuture<Void> future, int cur) {
        if (cur < agents2.size()) {
            try {
                List list2;
                List it = list2 = CollectionsKt.toMutableList((Collection)agents2.get(cur).getAction());
                boolean bl = false;
                it.add("agent");
                List agent2 = list2;
                KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, (List)agent2, (boolean)false, $type.namespaceAll(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>($session, $type){
                    final /* synthetic */ Session $session;
                    final /* synthetic */ AgentType $type;
                    {
                        this.$session = $session;
                        this.$type = $type;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptContext $this$eval) {
                        Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                        KetherHelperKt.extend((ScriptContext)$this$eval, this.$session.getVariables());
                        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"type", (Object)this.$type.name()), TuplesKt.to((Object)"@Session", (Object)this.$session)};
                        KetherHelperKt.extend((ScriptContext)$this$eval, (Map)MapsKt.mapOf((Pair[])pairArray));
                    }
                }), (int)58, null).thenApply(arg_0 -> Conversation.agent$process$lambda$11($session, future, cur, agents2, $type, arg_0));
            }
            catch (Throwable e) {
                KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
                if ($type != AgentType.END && $type != AgentType.REFUSE) {
                    Session.close$default($session, false, 1, null);
                }
            }
        } else {
            future.complete(null);
        }
    }
}

