/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.ChemdahEventFactory;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.QuestDataOperator;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.Couple;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u00b2\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010$\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\b\u0005\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J:\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00060'2\b\u0010(\u001a\u0004\u0018\u00010)2\n\b\u0002\u0010*\u001a\u0004\u0018\u00010\u001a2\u0014\b\u0002\u0010+\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020)0,H\u0016J\u000e\u0010-\u001a\u00020.2\u0006\u0010/\u001a\u000200J-\u0010-\u001a\u0002H1\"\u0004\b\u0000\u001012\u0006\u0010/\u001a\u0002002\u0012\u00102\u001a\u000e\u0012\u0004\u0012\u00020.\u0012\u0004\u0012\u0002H103\u00a2\u0006\u0002\u00104J\u0016\u00105\u001a\b\u0012\u0004\u0012\u00020\f062\u0006\u00107\u001a\u00020\u000bH\u0016J\u001c\u00108\u001a\u0004\u0018\u00010\u001a2\u0006\u00109\u001a\u00020\u000b2\b\b\u0002\u0010:\u001a\u00020\u0006H\u0016J\u0010\u0010;\u001a\u00020<2\u0006\u0010=\u001a\u00020>H\u0016J\u0010\u0010;\u001a\u00020<2\u0006\u0010?\u001a\u00020\u000bH\u0016J\u0018\u0010@\u001a\u00020<2\u0006\u0010?\u001a\u00020\u000b2\u0006\u0010A\u001a\u00020\u000bH\u0016J\u0018\u0010B\u001a\b\u0012\u0004\u0012\u00020\u001a0C2\b\b\u0002\u0010:\u001a\u00020\u0006H\u0016J\u0010\u0010D\u001a\u00020\u00062\u0006\u00107\u001a\u00020\u000bH\u0016J\u0010\u0010E\u001a\u00020\u00062\u0006\u0010=\u001a\u00020>H\u0016J\u0010\u0010E\u001a\u00020\u00062\u0006\u00109\u001a\u00020\u000bH\u0016J\b\u0010F\u001a\u00020GH\u0016J\u0012\u0010F\u001a\u00020G2\b\u0010\u0015\u001a\u0004\u0018\u00010\u0016H\u0016J\u001a\u0010H\u001a\u00020G2\u0006\u0010*\u001a\u00020\u001a2\b\b\u0002\u0010I\u001a\u00020\u0006H\u0016J\u0018\u0010J\u001a\u00020G2\u0006\u00107\u001a\u00020\u000b2\u0006\u0010K\u001a\u00020LH\u0016J\u0018\u0010M\u001a\u00020G2\u0006\u00107\u001a\u00020\u000b2\u0006\u00109\u001a\u00020\fH\u0016J\b\u0010N\u001a\u00020GH\u0016J.\u0010O\u001a\u00020G2\n\u0010P\u001a\u0006\u0012\u0002\b\u00030Q2\u0018\u00102\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u0002000S0RH\u0016J*\u0010O\u001a\u00020G2\u0006\u0010T\u001a\u00020)2\u0018\u00102\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u0002000S0RH\u0016J\u001a\u0010U\u001a\u00020G2\u0006\u0010*\u001a\u00020\u001a2\b\b\u0002\u0010V\u001a\u00020\u0006H\u0016R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0007R\u0014\u0010\b\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\u0007R\u001d\u0010\t\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\f0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u001a\u0010\u000f\u001a\u00020\u0010X\u0086.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u0014\u0010\u0015\u001a\u00020\u00168VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0017\u0010\u0018R\u001d\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u001a0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u000eR\u0017\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001a0\u001d\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u001fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010!R\u001a\u0010\"\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010\u0007\"\u0004\b$\u0010%\u00a8\u0006W"}, d2={"Link/ptms/chemdah/core/PlayerProfile;", "Lorg/bukkit/metadata/Metadatable;", "uniqueId", "Ljava/util/UUID;", "(Ljava/util/UUID;)V", "isDataChanged", "", "()Z", "isPlayerOnline", "metadataMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Lorg/bukkit/metadata/MetadataValue;", "getMetadataMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "persistentDataContainer", "Link/ptms/chemdah/core/DataContainer;", "getPersistentDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "setPersistentDataContainer", "(Link/ptms/chemdah/core/DataContainer;)V", "player", "Lorg/bukkit/entity/Player;", "getPlayer", "()Lorg/bukkit/entity/Player;", "questMap", "Link/ptms/chemdah/core/quest/Quest;", "getQuestMap", "releaseQuests", "Ljava/util/concurrent/CopyOnWriteArraySet;", "getReleaseQuests", "()Ljava/util/concurrent/CopyOnWriteArraySet;", "getUniqueId", "()Ljava/util/UUID;", "updaterLock", "getUpdaterLock", "setUpdaterLock", "(Z)V", "checkAgent", "Ljava/util/concurrent/CompletableFuture;", "agent", "", "quest", "variables", "", "dataOperator", "Link/ptms/chemdah/core/quest/QuestDataOperator;", "task", "Link/ptms/chemdah/core/quest/Task;", "T", "func", "Ljava/util/function/Function;", "(Link/ptms/chemdah/core/quest/Task;Ljava/util/function/Function;)Ljava/lang/Object;", "getMetadata", "", "key", "getQuestById", "value", "openAPI", "getQuestCompletedDate", "", "template", "Link/ptms/chemdah/core/quest/Template;", "questId", "getQuestTaskCompleteDate", "taskId", "getQuests", "", "hasMetadata", "isQuestCompleted", "push", "", "registerQuest", "newQuest", "removeMetadata", "plugin", "Lorg/bukkit/plugin/Plugin;", "setMetadata", "setup", "tasks", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "Ljava/util/function/Consumer;", "Link/ptms/chemdah/util/Couple;", "event", "unregisterQuest", "release", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPlayerProfile.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerProfile.kt\nink/ptms/chemdah/core/PlayerProfile\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,274:1\n1747#2,3:275\n288#2,2:278\n766#2:281\n857#2,2:282\n766#2:284\n857#2,2:285\n1855#2:287\n766#2:288\n857#2,2:289\n1855#2,2:291\n1856#2:293\n1855#2:294\n766#2:295\n857#2,2:296\n1855#2,2:298\n1856#2:300\n1855#2,2:301\n1#3:280\n*S KotlinDebug\n*F\n+ 1 PlayerProfile.kt\nink/ptms/chemdah/core/PlayerProfile\n*L\n60#1:275,3\n129#1:278,2\n141#1:281\n141#1:282,2\n143#1:284\n143#1:285,2\n205#1:287\n206#1:288\n206#1:289,2\n206#1:291,2\n205#1:293\n214#1:294\n215#1:295\n215#1:296,2\n215#1:298,2\n214#1:300\n237#1:301,2\n*E\n"})
public class PlayerProfile
implements Metadatable {
    @NotNull
    private final UUID uniqueId;
    private boolean updaterLock;
    @NotNull
    private final ConcurrentHashMap<String, Quest> questMap;
    @NotNull
    private final CopyOnWriteArraySet<Quest> releaseQuests;
    public DataContainer persistentDataContainer;
    @NotNull
    private final ConcurrentHashMap<String, MetadataValue> metadataMap;

    public PlayerProfile(@NotNull UUID uniqueId) {
        Intrinsics.checkNotNullParameter((Object)uniqueId, (String)"uniqueId");
        this.uniqueId = uniqueId;
        this.questMap = new ConcurrentHashMap();
        this.releaseQuests = new CopyOnWriteArraySet();
        this.metadataMap = new ConcurrentHashMap();
    }

    @NotNull
    public final UUID getUniqueId() {
        return this.uniqueId;
    }

    @NotNull
    public Player getPlayer() {
        Player player = Bukkit.getPlayer((UUID)this.uniqueId);
        if (player == null) {
            throw new IllegalStateException(("Player " + this.uniqueId + " is offline.").toString());
        }
        return player;
    }

    public boolean isPlayerOnline() {
        return Bukkit.getPlayer((UUID)this.uniqueId) != null;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public boolean isDataChanged() {
        boolean bl;
        if (this.getPersistentDataContainer().isChanged()) return true;
        Iterable $this$any$iv = PlayerProfile.getQuests$default(this, false, 1, null);
        boolean $i$f$any = false;
        if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
            bl = false;
        } else {
            for (Object element$iv : $this$any$iv) {
                Quest it = (Quest)element$iv;
                boolean bl2 = false;
                if (it.getNewQuest()) return true;
                if (it.getPersistentDataContainer().isChanged()) {
                    return true;
                }
                boolean bl3 = false;
                if (!bl3) continue;
                return true;
            }
            bl = false;
        }
        if (bl) return true;
        if (((Collection)this.releaseQuests).isEmpty()) return false;
        return true;
    }

    public final boolean getUpdaterLock() {
        return this.updaterLock;
    }

    public final void setUpdaterLock(boolean bl) {
        this.updaterLock = bl;
    }

    @NotNull
    public final ConcurrentHashMap<String, Quest> getQuestMap() {
        return this.questMap;
    }

    @NotNull
    public final CopyOnWriteArraySet<Quest> getReleaseQuests() {
        return this.releaseQuests;
    }

    @NotNull
    public final DataContainer getPersistentDataContainer() {
        DataContainer dataContainer = this.persistentDataContainer;
        if (dataContainer != null) {
            return dataContainer;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"persistentDataContainer");
        return null;
    }

    public final void setPersistentDataContainer(@NotNull DataContainer dataContainer) {
        Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"<set-?>");
        this.persistentDataContainer = dataContainer;
    }

    public void setup() {
        this.setPersistentDataContainer(new SimpleDataContainer(DataContainerEventFactory.Companion.of(this)));
    }

    public void registerQuest(@NotNull Quest quest2, boolean newQuest) {
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        ((Map)this.questMap).put(quest2.getId(), quest2);
        if (quest2.isValid()) {
            this.releaseQuests.removeIf(arg_0 -> PlayerProfile.registerQuest$lambda$1((Function1)new Function1<Quest, Boolean>(quest2){
                final /* synthetic */ Quest $quest;
                {
                    this.$quest = $quest;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(Quest it) {
                    return Intrinsics.areEqual((Object)it.getId(), (Object)this.$quest.getId());
                }
            }, arg_0));
            quest2.setNewQuest(newQuest);
            if (newQuest) {
                new QuestEvents.Registered(quest2, this).call();
            }
        }
    }

    public static /* synthetic */ void registerQuest$default(PlayerProfile playerProfile, Quest quest2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: registerQuest");
        }
        if ((n & 2) != 0) {
            bl = true;
        }
        playerProfile.registerQuest(quest2, bl);
    }

    public void unregisterQuest(@NotNull Quest quest2, boolean release) {
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        this.questMap.remove(quest2.getId());
        if (release) {
            ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, quest2){
                final /* synthetic */ PlayerProfile this$0;
                final /* synthetic */ Quest $quest;
                {
                    this.this$0 = $receiver;
                    this.$quest = $quest;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submitAsync) {
                    Intrinsics.checkNotNullParameter((Object)$this$submitAsync, (String)"$this$submitAsync");
                    Database.Companion.getINSTANCE().releaseQuest(this.this$0.getPlayer(), this.this$0, this.$quest);
                }
            }), (int)7, null);
        } else {
            ((Collection)this.releaseQuests).add(quest2);
        }
        new QuestEvents.Unregistered(quest2, this).call();
    }

    public static /* synthetic */ void unregisterQuest$default(PlayerProfile playerProfile, Quest quest2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: unregisterQuest");
        }
        if ((n & 2) != 0) {
            bl = false;
        }
        playerProfile.unregisterQuest(quest2, bl);
    }

    @Nullable
    public Quest getQuestById(@NotNull String value2, boolean openAPI) {
        Quest quest2;
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        if (openAPI) {
            Object v0;
            block5: {
                Iterable $this$firstOrNull$iv = this.getQuests(true);
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    Quest it = (Quest)element$iv;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)it.getId(), (Object)value2)) continue;
                    v0 = element$iv;
                    break block5;
                }
                v0 = null;
            }
            quest2 = v0;
        } else {
            Quest quest3 = this.questMap.get(value2);
            if (quest3 != null) {
                Quest quest4;
                Quest it = quest4 = quest3;
                boolean bl = false;
                quest2 = it.isValid() ? quest4 : null;
            } else {
                quest2 = null;
            }
        }
        return quest2;
    }

    public static /* synthetic */ Quest getQuestById$default(PlayerProfile playerProfile, String string, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getQuestById");
        }
        if ((n & 2) != 0) {
            bl = true;
        }
        return playerProfile.getQuestById(string, bl);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<Quest> getQuests(boolean openAPI) {
        List<Quest> list2;
        if (openAPI) {
            void $this$filterTo$iv$iv;
            void $this$filter$iv;
            ChemdahEventFactory chemdahEventFactory = ChemdahAPI.INSTANCE.getEventFactory();
            Collection<Quest> collection = this.questMap.values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"questMap.values");
            Iterable iterable = collection;
            PlayerProfile playerProfile = this;
            ChemdahEventFactory chemdahEventFactory2 = chemdahEventFactory;
            boolean $i$f$filter = false;
            void var4_8 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Quest it = (Quest)element$iv$iv;
                boolean bl = false;
                if (!it.isValid()) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            List list3 = (List)destination$iv$iv;
            list2 = chemdahEventFactory2.callQuestCollect(playerProfile, list3);
        } else {
            Collection<Quest> collection = this.questMap.values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"questMap.values");
            Iterable $this$filter$iv = collection;
            boolean $i$f$filter = false;
            Iterable $this$filterTo$iv$iv = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Quest it = (Quest)element$iv$iv;
                boolean bl = false;
                if (!it.isValid()) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            list2 = (List<Quest>)destination$iv$iv;
        }
        return list2;
    }

    public static /* synthetic */ List getQuests$default(PlayerProfile playerProfile, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getQuests");
        }
        if ((n & 1) != 0) {
            bl = false;
        }
        return playerProfile.getQuests(bl);
    }

    public boolean isQuestCompleted(@NotNull String value2) {
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        return this.getQuestCompletedDate(value2) > 0L;
    }

    public boolean isQuestCompleted(@NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        return this.isQuestCompleted(template.getId());
    }

    public long getQuestCompletedDate(@NotNull String questId2) {
        Intrinsics.checkNotNullParameter((Object)questId2, (String)"questId");
        return this.getPersistentDataContainer().get("quest.complete." + questId2, 0L).toLong();
    }

    public long getQuestCompletedDate(@NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        return this.getQuestCompletedDate(template.getId());
    }

    public long getQuestTaskCompleteDate(@NotNull String questId2, @NotNull String taskId) {
        Intrinsics.checkNotNullParameter((Object)questId2, (String)"questId");
        Intrinsics.checkNotNullParameter((Object)taskId, (String)"taskId");
        return this.getPersistentDataContainer().get("quest.complete." + questId2 + '.' + taskId, 0L).toLong();
    }

    @NotNull
    public CompletableFuture<Boolean> checkAgent(@Nullable Object agent2, @Nullable Quest quest2, @NotNull Map<String, ? extends Object> variables2) {
        CompletionStage<Boolean> completionStage;
        Intrinsics.checkNotNullParameter(variables2, (String)"variables");
        if (agent2 == null) {
            CompletableFuture<Boolean> completableFuture = CompletableFuture.completedFuture(true);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(true)");
            return completableFuture;
        }
        try {
            KetherShell ketherShell = KetherShell.INSTANCE;
            List list2 = CollectionKt.asList((Object)agent2);
            ProxyCommandSender proxyCommandSender = AdapterKt.adaptCommandSender((Object)this.getPlayer());
            List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
            CompletionStage completionStage2 = KetherShell.eval$default((KetherShell)ketherShell, (List)list2, (boolean)false, list3, null, (ProxyCommandSender)proxyCommandSender, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(quest2, variables2){
                final /* synthetic */ Quest $quest;
                final /* synthetic */ Map<String, Object> $variables;
                {
                    this.$quest = $quest;
                    this.$variables = $variables;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    Object object = this.$quest;
                    $this$eval.set("@QuestSelected", object != null && (object = ((Quest)object).getTemplate()) != null ? ((QuestContainer)object).getNode() : null);
                    $this$eval.set("@QuestContainer", (Object)this.$quest);
                    Map<String, Object> $this$forEach$iv = this.$variables;
                    boolean $i$f$forEach = false;
                    Iterator<Map.Entry<String, Object>> iterator = $this$forEach$iv.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Object> element$iv;
                        Map.Entry<String, Object> entry = element$iv = iterator.next();
                        boolean bl = false;
                        String t = entry.getKey();
                        Object u = entry.getValue();
                        $this$eval.set(t, u);
                    }
                }
            }), (int)42, null).thenApply(PlayerProfile::checkAgent$lambda$6);
            Intrinsics.checkNotNullExpressionValue((Object)completionStage2, (String)"quest: Quest? = null, va\u2026)\n            }\n        }");
            completionStage = completionStage2;
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
            CompletableFuture<Boolean> completableFuture = CompletableFuture.completedFuture(false);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"{\n            e.printKet\u2026edFuture(false)\n        }");
            completionStage = completableFuture;
        }
        return completionStage;
    }

    public static /* synthetic */ CompletableFuture checkAgent$default(PlayerProfile playerProfile, Object object, Quest quest2, Map map, int n, Object object2) {
        if (object2 != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: checkAgent");
        }
        if ((n & 2) != 0) {
            quest2 = null;
        }
        if ((n & 4) != 0) {
            map = MapsKt.emptyMap();
        }
        return playerProfile.checkAgent(object, quest2, map);
    }

    /*
     * WARNING - void declaration
     */
    public void tasks(@NotNull Object event, @NotNull Consumer<Couple<Quest, Task>> func) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        Iterable $this$forEach$iv = this.getQuests(true);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            void $this$forEach$iv2;
            void $this$filterTo$iv$iv;
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            Iterable $this$filter$iv = quest2.getTasks();
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Task it = (Task)element$iv$iv;
                boolean bl2 = false;
                if (!(it.getObjective().isListener() && it.getObjective().getEvent().isInstance(event))) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$forEach2 = false;
            for (Object element$iv2 : $this$forEach$iv2) {
                Task it = (Task)element$iv2;
                boolean bl3 = false;
                func.accept(new Couple<Quest, Task>(quest2, it));
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    public void tasks(@NotNull Objective<?> objective2, @NotNull Consumer<Couple<Quest, Task>> func) {
        Intrinsics.checkNotNullParameter(objective2, (String)"objective");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        Iterable $this$forEach$iv = this.getQuests(true);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            void $this$forEach$iv2;
            void $this$filterTo$iv$iv;
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            Iterable $this$filter$iv = quest2.getTasks();
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Task it = (Task)element$iv$iv;
                boolean bl2 = false;
                if (!Intrinsics.areEqual(it.getObjective(), objective2)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$forEach2 = false;
            for (Object element$iv2 : $this$forEach$iv2) {
                Task it = (Task)element$iv2;
                boolean bl3 = false;
                func.accept(new Couple<Quest, Task>(quest2, it));
            }
        }
    }

    public void push() {
        this.push(null);
    }

    public void push(@Nullable Player player) {
        if (this.isDataChanged()) {
            Player player2 = player;
            if (player2 == null && (player2 = Bukkit.getPlayer((UUID)this.uniqueId)) == null) {
                throw new IllegalStateException(("Player " + this.uniqueId + " is offline.").toString());
            }
            Player target = player2;
            Database.Companion.getINSTANCE().update(target, this);
            Iterable $this$forEach$iv = this.releaseQuests;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Quest it = (Quest)element$iv;
                boolean bl = false;
                Database database = Database.Companion.getINSTANCE();
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                database.releaseQuest(target, this, it);
            }
            new PlayerEvents.Updated(target, this).call();
        }
    }

    @NotNull
    public final QuestDataOperator dataOperator(@NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        return new QuestDataOperator(this, task);
    }

    public final <T> T dataOperator(@NotNull Task task, @NotNull Function<QuestDataOperator, T> func) {
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        return (T)this.dataOperator(task, arg_0 -> PlayerProfile.dataOperator$lambda$14(func, arg_0));
    }

    @NotNull
    public final ConcurrentHashMap<String, MetadataValue> getMetadataMap() {
        return this.metadataMap;
    }

    public void setMetadata(@NotNull String key, @NotNull MetadataValue value2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        ((Map)this.metadataMap).put(key, value2);
    }

    @NotNull
    public List<MetadataValue> getMetadata(@NotNull String key) {
        Object object;
        block3: {
            block2: {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                object = this.metadataMap.get(key);
                if (object == null) break block2;
                MetadataValue it = object;
                boolean bl = false;
                Object[] objectArray = new MetadataValue[]{it};
                List list2 = CollectionsKt.mutableListOf((Object[])objectArray);
                object = list2;
                if (list2 != null) break block3;
            }
            object = new ArrayList();
        }
        return object;
    }

    public boolean hasMetadata(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.metadataMap.containsKey(key);
    }

    public void removeMetadata(@NotNull String key, @NotNull Plugin plugin2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)plugin2, (String)"plugin");
        this.metadataMap.remove(key);
    }

    private static final boolean registerQuest$lambda$1(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Boolean)$tmp0.invoke(p0);
    }

    private static final Boolean checkAgent$lambda$6(Object it) {
        return Coerce.toBoolean((Object)it);
    }

    private static final Object dataOperator$lambda$14(Function $func, QuestDataOperator it) {
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return $func.apply(it);
    }
}

