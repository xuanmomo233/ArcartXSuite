/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Agent;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.StringKt;
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
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000v\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\n\b&\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u001f\u0010\"\u001a\u0004\u0018\u0001H#\"\b\b\u0000\u0010#*\u00020\t2\u0006\u0010$\u001a\u00020\u0003\u00a2\u0006\u0002\u0010%J4\u0010&\u001a\b\u0012\u0004\u0012\u00020(0'2\u0006\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\b\b\u0002\u0010-\u001a\u00020\u00032\n\b\u0002\u0010.\u001a\u0004\u0018\u00010\u0003H\u0016J\u0013\u0010/\u001a\u00020(2\b\u00100\u001a\u0004\u0018\u00010\u0001H\u0096\u0002J \u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00142\u0006\u0010+\u001a\u00020,2\b\b\u0002\u0010-\u001a\u00020\u0003H\u0016J\u001c\u00101\u001a\u0004\u0018\u0001022\u0006\u0010)\u001a\u00020*2\b\b\u0002\u00103\u001a\u00020(H\u0016J\b\u00104\u001a\u000205H\u0016J\u001a\u00106\u001a\u0002072\u0006\u0010$\u001a\u00020\u00032\b\b\u0002\u00108\u001a\u00020\u0003H\u0014J\u0018\u00109\u001a\u0002072\u0006\u0010:\u001a\u00020\u00032\u0006\u0010;\u001a\u00020\u0001H\u0014J\u001a\u0010<\u001a\u0002072\u0006\u0010=\u001a\u00020\u00032\b\b\u0002\u0010>\u001a\u00020\u0003H\u0014J#\u0010?\u001a\u0004\u0018\u0001H#\"\f\b\u0000\u0010#*\u0006\u0012\u0002\b\u00030\u001c2\u0006\u0010=\u001a\u00020\u0003\u00a2\u0006\u0002\u0010@R-\u0010\u0007\u001a\u001e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\t0\bj\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\t`\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR!\u0010\r\u001a\u0012\u0012\u0004\u0012\u00020\u000f0\u000ej\b\u0012\u0004\u0012\u00020\u000f`\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0017\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00030\u00148F\u00a2\u0006\u0006\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR5\u0010\u001b\u001a&\u0012\u0004\u0012\u00020\u0003\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u001c0\bj\u0012\u0012\u0004\u0012\u00020\u0003\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u001c`\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\fR\u0011\u0010\u001e\u001a\u00020\u00038F\u00a2\u0006\u0006\u001a\u0004\b\u001f\u0010\u001aR\u0011\u0010 \u001a\u00020\u00038F\u00a2\u0006\u0006\u001a\u0004\b!\u0010\u001a\u00a8\u0006A"}, d2={"Link/ptms/chemdah/core/quest/QuestContainer;", "", "id", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Ljava/lang/String;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "addonMap", "Ljava/util/HashMap;", "Link/ptms/chemdah/core/quest/addon/Addon;", "Lkotlin1822/collections/HashMap;", "getAddonMap", "()Ljava/util/HashMap;", "agentList", "Ljava/util/ArrayList;", "Link/ptms/chemdah/core/quest/Agent;", "Lkotlin1822/collections/ArrayList;", "getAgentList", "()Ljava/util/ArrayList;", "agents", "", "getAgents", "()Ljava/util/List;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getId", "()Ljava/lang/String;", "metaMap", "Link/ptms/chemdah/core/quest/meta/Meta;", "getMetaMap", "node", "getNode", "path", "getPath", "addon", "T", "addonId", "(Ljava/lang/String;)Link/ptms/chemdah/core/quest/addon/Addon;", "agent", "Ljava/util/concurrent/CompletableFuture;", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "agentType", "Link/ptms/chemdah/core/quest/AgentType;", "restrict", "reason", "equals", "other", "getQuest", "Link/ptms/chemdah/core/quest/Quest;", "openAPI", "hashCode", "", "loadAddon", "", "addonNode", "loadAgent", "source", "value", "loadMeta", "metaId", "metaNode", "meta", "(Ljava/lang/String;)Link/ptms/chemdah/core/quest/meta/Meta;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestContainer.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestContainer.kt\nink/ptms/chemdah/core/quest/QuestContainer\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,203:1\n1855#2,2:204\n1855#2,2:206\n1855#2,2:208\n1549#2:210\n1620#2,3:211\n288#2,2:214\n766#2:216\n857#2,2:217\n1#3:219\n*S KotlinDebug\n*F\n+ 1 QuestContainer.kt\nink/ptms/chemdah/core/quest/QuestContainer\n*L\n72#1:204,2\n74#1:206,2\n76#1:208,2\n47#1:210\n47#1:211,3\n94#1:214,2\n104#1:216\n104#1:217,2\n*E\n"})
public abstract class QuestContainer {
    @NotNull
    private final String id;
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final HashMap<String, Meta<?>> metaMap;
    @NotNull
    private final HashMap<String, Addon> addonMap;
    @NotNull
    private final ArrayList<Agent> agentList;

    public QuestContainer(@NotNull String id2, @NotNull ConfigurationSection config) {
        block5: {
            Object object;
            Object object2;
            String node;
            boolean $i$f$forEach;
            Iterable $this$forEach$iv;
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            this.id = id2;
            this.config = config;
            this.metaMap = new HashMap();
            this.addonMap = new HashMap();
            this.agentList = new ArrayList();
            Object object3 = this.config.getConfigurationSection("agent");
            if (object3 != null && (object3 = object3.getKeys(false)) != null) {
                $this$forEach$iv = (Iterable)object3;
                $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    node = (String)element$iv;
                    boolean bl = false;
                    Object object4 = this.config.get("agent." + node);
                    Intrinsics.checkNotNull((Object)object4);
                    this.loadAgent(node, object4);
                }
            }
            if ((object2 = this.config.getConfigurationSection("addon")) != null && (object2 = object2.getKeys(false)) != null) {
                $this$forEach$iv = (Iterable)object2;
                $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    node = (String)element$iv;
                    boolean bl = false;
                    QuestContainer.loadAddon$default(this, node, null, 2, null);
                }
            }
            if ((object = this.config.getConfigurationSection("meta")) == null || (object = object.getKeys(false)) == null) break block5;
            $this$forEach$iv = (Iterable)object;
            $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                node = (String)element$iv;
                boolean bl = false;
                QuestContainer.loadMeta$default(this, node, null, 2, null);
            }
        }
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final HashMap<String, Meta<?>> getMetaMap() {
        return this.metaMap;
    }

    @NotNull
    public final HashMap<String, Addon> getAddonMap() {
        return this.addonMap;
    }

    @NotNull
    public final ArrayList<Agent> getAgentList() {
        return this.agentList;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> getAgents() {
        void $this$mapTo$iv$iv;
        Iterable $this$map$iv = this.agentList;
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            Agent agent2 = (Agent)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(it.getType().name() + " @ " + it.getRestrict());
        }
        return (List)destination$iv$iv;
    }

    @NotNull
    public final String getNode() {
        QuestContainer questContainer = this;
        return questContainer instanceof Template ? this.id : (questContainer instanceof Task ? ((Task)this).getTemplate().getId() : "null");
    }

    @NotNull
    public final String getPath() {
        QuestContainer questContainer = this;
        return questContainer instanceof Template ? this.id : (questContainer instanceof Task ? ((Task)this).getTemplate().getId() + '.' + this.id : "null");
    }

    @Nullable
    public final <T extends Meta<?>> T meta(@NotNull String metaId) {
        Intrinsics.checkNotNullParameter((Object)metaId, (String)"metaId");
        Meta<?> meta = this.metaMap.get(metaId);
        return (T)(meta instanceof Meta ? meta : null);
    }

    @Nullable
    public final <T extends Addon> T addon(@NotNull String addonId) {
        Intrinsics.checkNotNullParameter((Object)addonId, (String)"addonId");
        Addon addon = this.addonMap.get(addonId);
        return (T)(addon instanceof Addon ? addon : null);
    }

    @Nullable
    public Quest getQuest(@NotNull PlayerProfile profile, boolean openAPI) {
        Quest quest2;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        QuestContainer questContainer = this;
        if (questContainer instanceof Template) {
            Object v0;
            block3: {
                Iterable $this$firstOrNull$iv = profile.getQuests(openAPI);
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    Quest it = (Quest)element$iv;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)it.getId(), (Object)this.id)) continue;
                    v0 = element$iv;
                    break block3;
                }
                v0 = null;
            }
            quest2 = v0;
        } else {
            quest2 = questContainer instanceof Task ? ((Task)this).getTemplate().getQuest(profile, openAPI) : null;
        }
        return quest2;
    }

    public static /* synthetic */ Quest getQuest$default(QuestContainer questContainer, PlayerProfile playerProfile, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getQuest");
        }
        if ((n & 2) != 0) {
            bl = false;
        }
        return questContainer.getQuest(playerProfile, bl);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<Agent> getAgentList(@NotNull AgentType agentType, @NotNull String restrict) {
        void $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)((Object)agentType), (String)"agentType");
        Intrinsics.checkNotNullParameter((Object)restrict, (String)"restrict");
        Iterable $this$filter$iv = this.agentList;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Agent it = (Agent)element$iv$iv;
            boolean bl = false;
            if (!(it.getType() == agentType && (Intrinsics.areEqual((Object)it.getRestrict(), (Object)"*") || Intrinsics.areEqual((Object)it.getRestrict(), (Object)"all") || Intrinsics.areEqual((Object)it.getRestrict(), (Object)restrict)))) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        return (List)destination$iv$iv;
    }

    public static /* synthetic */ List getAgentList$default(QuestContainer questContainer, AgentType agentType, String string, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getAgentList");
        }
        if ((n & 2) != 0) {
            string = "self";
        }
        return questContainer.getAgentList(agentType, string);
    }

    @NotNull
    public CompletableFuture<Boolean> agent(@NotNull PlayerProfile profile, @NotNull AgentType agentType, @NotNull String restrict, @Nullable String reason) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)((Object)agentType), (String)"agentType");
        Intrinsics.checkNotNullParameter((Object)restrict, (String)"restrict");
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        if (!new QuestEvents.Agent(this, profile, agentType, restrict).call()) {
            future.complete(false);
            return future;
        }
        List<Agent> agent2 = this.getAgentList(agentType, restrict);
        QuestContainer.agent$process(agent2, profile, agentType, this, future, reason, 0);
        return future;
    }

    public static /* synthetic */ CompletableFuture agent$default(QuestContainer questContainer, PlayerProfile playerProfile, AgentType agentType, String string, String string2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: agent");
        }
        if ((n & 4) != 0) {
            string = "self";
        }
        if ((n & 8) != 0) {
            string2 = null;
        }
        return questContainer.agent(playerProfile, agentType, string, string2);
    }

    protected void loadAgent(@NotNull String source, @NotNull Object value2) {
        String type;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        String[] stringArray = new String[]{"@"};
        List<String> args = StringKt.trim(StringsKt.split$default((CharSequence)source, (String[])stringArray, (boolean)false, (int)0, (int)6, null));
        Object object = this;
        String string = object instanceof Template ? "quest_" + args.get(0) : (type = object instanceof Task ? "task_" + args.get(0) : args.get(0));
        if (AgentType.Companion.toAgent(type) != AgentType.NONE) {
            Object object2;
            ArrayList<Agent> arrayList = this.agentList;
            AgentType agentType = AgentType.Companion.toAgent(type);
            List list2 = CollectionKt.asList((Object)value2);
            int n = 1;
            object = args;
            if (n <= CollectionsKt.getLastIndex((List)object)) {
                object2 = object.get(n);
            } else {
                int n2 = n;
                List list3 = list2;
                AgentType agentType2 = agentType;
                ArrayList<Agent> arrayList2 = arrayList;
                boolean bl = false;
                String string2 = "self";
                arrayList = arrayList2;
                agentType = agentType2;
                list2 = list3;
                object2 = string2;
            }
            String string3 = (String)object2;
            List list4 = list2;
            AgentType agentType3 = agentType;
            arrayList.add(new Agent(agentType3, list4, string3));
        } else {
            object = new Object[]{args.get(0) + " agent not supported."};
            IOKt.warning((Object[])object);
        }
    }

    protected void loadAddon(@NotNull String addonId, @NotNull String addonNode) {
        Intrinsics.checkNotNullParameter((Object)addonId, (String)"addonId");
        Intrinsics.checkNotNullParameter((Object)addonNode, (String)"addonNode");
        Class<? extends Addon> addon = ChemdahAPI.INSTANCE.getQuestAddon(addonId);
        if (addon != null) {
            Option.Type option = addon.isAnnotationPresent(Option.class) ? addon.getAnnotation(Option.class).type() : Option.Type.ANY;
            Object data2 = option.get(this.config, addonNode);
            if (data2 instanceof ConfigurationSection && ((ConfigurationSection)data2).getKeys(false).isEmpty()) {
                return;
            }
            if (data2 instanceof Map && ((Map)data2).isEmpty()) {
                return;
            }
            if (data2 instanceof List && ((List)data2).isEmpty()) {
                return;
            }
            Map map = this.addonMap;
            Object object = new Object[]{data2, this};
            object = Reflex.Companion.invokeConstructor(addon, object);
            map.put(addonId, object);
        }
    }

    public static /* synthetic */ void loadAddon$default(QuestContainer questContainer, String string, String string2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: loadAddon");
        }
        if ((n & 2) != 0) {
            string2 = "addon." + string;
        }
        questContainer.loadAddon(string, string2);
    }

    protected void loadMeta(@NotNull String metaId, @NotNull String metaNode) {
        Intrinsics.checkNotNullParameter((Object)metaId, (String)"metaId");
        Intrinsics.checkNotNullParameter((Object)metaNode, (String)"metaNode");
        Class<Meta<?>> meta = ChemdahAPI.INSTANCE.getQuestMeta(metaId);
        if (meta != null) {
            Option.Type option = meta.isAnnotationPresent(Option.class) ? meta.getAnnotation(Option.class).type() : Option.Type.ANY;
            Map map = this.metaMap;
            Object object = new Object[]{option.get(this.config, metaNode), this};
            object = Reflex.Companion.invokeConstructor(meta, object);
            map.put(metaId, object);
        }
    }

    public static /* synthetic */ void loadMeta$default(QuestContainer questContainer, String string, String string2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: loadMeta");
        }
        if ((n & 2) != 0) {
            string2 = "meta." + string;
        }
        questContainer.loadMeta(string, string2);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestContainer)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.getPath(), (Object)((QuestContainer)other).getPath());
    }

    public int hashCode() {
        return this.getPath().hashCode();
    }

    private static final Object agent$process$lambda$6(CompletableFuture $future, int $cur, List $agent, PlayerProfile $profile, AgentType $agentType, QuestContainer this$0, String $reason, Object it) {
        Boolean bl;
        Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
        Intrinsics.checkNotNullParameter((Object)$agent, (String)"$agent");
        Intrinsics.checkNotNullParameter((Object)$profile, (String)"$profile");
        Intrinsics.checkNotNullParameter((Object)((Object)$agentType), (String)"$agentType");
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        if (it instanceof Boolean && !((Boolean)it).booleanValue()) {
            bl = $future.complete(false);
        } else {
            QuestContainer.agent$process($agent, $profile, $agentType, this$0, $future, $reason, $cur + 1);
            bl = Unit.INSTANCE;
        }
        return bl;
    }

    private static final void agent$process(List<Agent> agent2, PlayerProfile $profile, AgentType $agentType, QuestContainer this$0, CompletableFuture<Boolean> future, String $reason, int cur) {
        if (cur < agent2.size()) {
            try {
                KetherShell ketherShell = KetherShell.INSTANCE;
                List<String> list2 = agent2.get(cur).getAction();
                ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)$profile.getPlayer());
                List<String> list3 = $agentType.namespaceAll();
                KetherShell.eval$default((KetherShell)ketherShell, list2, (boolean)false, list3, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>($reason, this$0){
                    final /* synthetic */ String $reason;
                    final /* synthetic */ QuestContainer this$0;
                    {
                        this.$reason = $reason;
                        this.this$0 = $receiver;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptContext $this$eval) {
                        Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                        $this$eval.set("reason", (Object)this.$reason);
                        $this$eval.set("@QuestSelected", (Object)this.this$0.getNode());
                        $this$eval.set("@QuestContainer", (Object)this.this$0);
                    }
                }), (int)42, null).thenApply(arg_0 -> QuestContainer.agent$process$lambda$6(future, cur, agent2, $profile, $agentType, this$0, $reason, arg_0));
            }
            catch (Throwable e) {
                Object[] objectArray = new Object[]{"path: " + this$0.getPath() + ", agentType: " + (Object)((Object)$agentType) + ", source: " + agent2.get(cur).getAction()};
                IOKt.warning((Object[])objectArray);
                KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
            }
        } else {
            future.complete(true);
        }
    }
}

