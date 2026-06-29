/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.LazyKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.metadata.MetadataValue
 *  org.bukkit.metadata.Metadatable
 *  org.bukkit.plugin.Plugin
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
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
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
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
import kotlin1822.Lazy;
import kotlin1822.LazyKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u00b6\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010$\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\b\u0005\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J:\u0010*\u001a\b\u0012\u0004\u0012\u00020\u00060+2\b\u0010,\u001a\u0004\u0018\u00010-2\n\b\u0002\u0010.\u001a\u0004\u0018\u00010\u001f2\u0014\b\u0002\u0010/\u001a\u000e\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020-00H\u0016J\u000e\u00101\u001a\u0002022\u0006\u00103\u001a\u000204J-\u00101\u001a\u0002H5\"\u0004\b\u0000\u001052\u0006\u00103\u001a\u0002042\u0012\u00106\u001a\u000e\u0012\u0004\u0012\u000202\u0012\u0004\u0012\u0002H507\u00a2\u0006\u0002\u00108J\u0016\u00109\u001a\b\u0012\u0004\u0012\u00020\u000f0:2\u0006\u0010;\u001a\u00020\u000eH\u0016J\u001c\u0010<\u001a\u0004\u0018\u00010\u001f2\u0006\u0010=\u001a\u00020\u000e2\b\b\u0002\u0010>\u001a\u00020\u0006H\u0016J\u0010\u0010?\u001a\u00020@2\u0006\u0010A\u001a\u00020BH\u0016J\u0010\u0010?\u001a\u00020@2\u0006\u0010C\u001a\u00020\u000eH\u0016J\u0018\u0010D\u001a\u00020@2\u0006\u0010C\u001a\u00020\u000e2\u0006\u0010E\u001a\u00020\u000eH\u0016J\u0018\u0010F\u001a\b\u0012\u0004\u0012\u00020\u001f0G2\b\b\u0002\u0010>\u001a\u00020\u0006H\u0016J\u0010\u0010H\u001a\u00020\u00062\u0006\u0010;\u001a\u00020\u000eH\u0016J\u0010\u0010I\u001a\u00020\u00062\u0006\u0010A\u001a\u00020BH\u0016J\u0010\u0010I\u001a\u00020\u00062\u0006\u0010=\u001a\u00020\u000eH\u0016J\b\u0010J\u001a\u00020KH\u0016J\u0012\u0010J\u001a\u00020K2\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0016J\u001a\u0010L\u001a\u00020K2\u0006\u0010.\u001a\u00020\u001f2\b\b\u0002\u0010M\u001a\u00020\u0006H\u0016J\u0018\u0010N\u001a\u00020K2\u0006\u0010;\u001a\u00020\u000e2\u0006\u0010O\u001a\u00020PH\u0016J\u0018\u0010Q\u001a\u00020K2\u0006\u0010;\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000fH\u0016J\b\u0010R\u001a\u00020KH\u0016J*\u0010S\u001a\u00020K2\u0006\u0010T\u001a\u00020-2\u0018\u00106\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u001f\u0012\u0004\u0012\u0002040V0UH\u0016J6\u0010W\u001a\u00020K2\u0006\u0010X\u001a\u00020\u00002\n\u0010Y\u001a\u0006\u0012\u0002\b\u00030Z2\u0018\u00106\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u001f\u0012\u0004\u0012\u0002040V0UH\u0016J\u001a\u0010[\u001a\u00020K2\u0006\u0010.\u001a\u00020\u001f2\b\b\u0002\u0010\\\u001a\u00020\u0006H\u0016J\u0006\u0010]\u001a\u00020KR\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0007R\u0014\u0010\b\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\u0007R\u001a\u0010\t\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\u0007\"\u0004\b\n\u0010\u000bR\u001d\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u001a\u0010\u0012\u001a\u00020\u0013X\u0086.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017R\u001b\u0010\u0018\u001a\u00020\u00198VX\u0096\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001c\u0010\u001d\u001a\u0004\b\u001a\u0010\u001bR\u001d\u0010\u001e\u001a\u000e\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u001f0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0011R\u0017\u0010!\u001a\b\u0012\u0004\u0012\u00020\u001f0\"\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010$R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010&R\u001a\u0010'\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b(\u0010\u0007\"\u0004\b)\u0010\u000b\u00a8\u0006^"}, d2={"Link/ptms/chemdah/core/PlayerProfile;", "Lorg/bukkit/metadata/Metadatable;", "uniqueId", "Ljava/util/UUID;", "(Ljava/util/UUID;)V", "isDataChanged", "", "()Z", "isPlayerOnline", "isReleased", "setReleased", "(Z)V", "metadataMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Lorg/bukkit/metadata/MetadataValue;", "getMetadataMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "persistentDataContainer", "Link/ptms/chemdah/core/DataContainer;", "getPersistentDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "setPersistentDataContainer", "(Link/ptms/chemdah/core/DataContainer;)V", "player", "Lorg/bukkit/entity/Player;", "getPlayer", "()Lorg/bukkit/entity/Player;", "player$delegate", "Lkotlin1822/Lazy;", "questMap", "Link/ptms/chemdah/core/quest/Quest;", "getQuestMap", "releaseQuests", "Ljava/util/concurrent/CopyOnWriteArraySet;", "getReleaseQuests", "()Ljava/util/concurrent/CopyOnWriteArraySet;", "getUniqueId", "()Ljava/util/UUID;", "updaterLock", "getUpdaterLock", "setUpdaterLock", "checkAgent", "Ljava/util/concurrent/CompletableFuture;", "agent", "", "quest", "variables", "", "dataOperator", "Link/ptms/chemdah/core/quest/QuestDataOperator;", "task", "Link/ptms/chemdah/core/quest/Task;", "T", "func", "Ljava/util/function/Function;", "(Link/ptms/chemdah/core/quest/Task;Ljava/util/function/Function;)Ljava/lang/Object;", "getMetadata", "", "key", "getQuestById", "value", "openAPI", "getQuestCompletedDate", "", "template", "Link/ptms/chemdah/core/quest/Template;", "questId", "getQuestTaskCompleteDate", "taskId", "getQuests", "", "hasMetadata", "isQuestCompleted", "push", "", "registerQuest", "newQuest", "removeMetadata", "plugin", "Lorg/bukkit/plugin/Plugin;", "setMetadata", "setup", "tasksByEvent", "event", "Ljava/util/function/Consumer;", "Link/ptms/chemdah/util/Couple;", "tasksByObjective", "profile", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "unregisterQuest", "release", "validation", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPlayerProfile.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerProfile.kt\nink/ptms/chemdah/core/PlayerProfile\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,314:1\n1747#2,3:315\n288#2,2:318\n766#2:321\n857#2,2:322\n766#2:324\n857#2,2:325\n1855#2:327\n766#2:328\n857#2,2:329\n1855#2,2:331\n1856#2:333\n766#2:334\n857#2,2:335\n1855#2:337\n766#2:338\n857#2,2:339\n1855#2,2:341\n1856#2:343\n1#3:320\n215#4,2:344\n*S KotlinDebug\n*F\n+ 1 PlayerProfile.kt\nink/ptms/chemdah/core/PlayerProfile\n*L\n64#1:315,3\n138#1:318,2\n150#1:321\n150#1:322,2\n152#1:324\n152#1:325,2\n214#1:327\n215#1:328\n215#1:329,2\n217#1:331,2\n214#1:333\n228#1:334\n228#1:335,2\n229#1:337\n230#1:338\n230#1:339,2\n232#1:341,2\n229#1:343\n283#1:344,2\n*E\n"})
public class PlayerProfile
implements Metadatable {
    @NotNull
    private final UUID uniqueId;
    @NotNull
    private final Lazy player$delegate;
    private boolean updaterLock;
    private boolean isReleased;
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
        this.player$delegate = LazyKt.lazy((Function0)((Function0)new Function0<Player>(this){
            final /* synthetic */ PlayerProfile this$0;
            {
                this.this$0 = $receiver;
                super(0);
            }

            @NotNull
            public final Player invoke() {
                Player player2 = Bukkit.getPlayer((UUID)this.this$0.getUniqueId());
                if (player2 == null) {
                    throw new IllegalStateException(("\u73a9\u5bb6 " + this.this$0.getUniqueId() + " \u4e0d\u5728\u7ebf\uff0c\u65e0\u6cd5\u521b\u5efa PlayerProfile \u5b9e\u4f8b\u3002").toString());
                }
                return player2;
            }
        }));
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
        Lazy lazy = this.player$delegate;
        return (Player)lazy.getValue();
    }

    public boolean isPlayerOnline() {
        return this.getPlayer().isOnline();
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

    public final boolean isReleased() {
        return this.isReleased;
    }

    public final void setReleased(boolean bl) {
        this.isReleased = bl;
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

    public static /* synthetic */ void registerQuest$default(PlayerProfile playerProfile2, Quest quest2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: registerQuest");
        }
        if ((n & 2) != 0) {
            bl = true;
        }
        playerProfile2.registerQuest(quest2, bl);
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
                    Database database = Database.Companion.getINSTANCE();
                    Player player2 = this.this$0.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"player");
                    database.releaseQuest(player2, this.this$0, this.$quest);
                }
            }), (int)7, null);
        } else {
            ((Collection)this.releaseQuests).add(quest2);
        }
        new QuestEvents.Unregistered(quest2, this).call();
    }

    public static /* synthetic */ void unregisterQuest$default(PlayerProfile playerProfile2, Quest quest2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: unregisterQuest");
        }
        if ((n & 2) != 0) {
            bl = false;
        }
        playerProfile2.unregisterQuest(quest2, bl);
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

    public static /* synthetic */ Quest getQuestById$default(PlayerProfile playerProfile2, String string, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getQuestById");
        }
        if ((n & 2) != 0) {
            bl = true;
        }
        return playerProfile2.getQuestById(string, bl);
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
            PlayerProfile playerProfile2 = this;
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
            list2 = chemdahEventFactory2.callQuestCollect(playerProfile2, list3);
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

    public static /* synthetic */ List getQuests$default(PlayerProfile playerProfile2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getQuests");
        }
        if ((n & 1) != 0) {
            bl = false;
        }
        return playerProfile2.getQuests(bl);
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
            Player player2 = this.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"player");
            ProxyCommandSender proxyCommandSender = AdapterKt.adaptCommandSender((Object)player2);
            List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
            completionStage = KetherShell.eval$default((KetherShell)ketherShell, (List)list2, (boolean)false, list3, null, (ProxyCommandSender)proxyCommandSender, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(quest2, variables2){
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
            Intrinsics.checkNotNullExpressionValue(completionStage, (String)"quest: Quest? = null, va\u2026)\n            }\n        }");
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
            CompletableFuture<Boolean> completableFuture = CompletableFuture.completedFuture(false);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"{\n            e.printKet\u2026edFuture(false)\n        }");
            completionStage = completableFuture;
        }
        return completionStage;
    }

    public static /* synthetic */ CompletableFuture checkAgent$default(PlayerProfile playerProfile2, Object object, Quest quest2, Map map, int n, Object object2) {
        if (object2 != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: checkAgent");
        }
        if ((n & 2) != 0) {
            quest2 = null;
        }
        if ((n & 4) != 0) {
            map = MapsKt.emptyMap();
        }
        return playerProfile2.checkAgent(object, quest2, map);
    }

    /*
     * WARNING - void declaration
     */
    public void tasksByEvent(@NotNull Object event, @NotNull Consumer<Couple<Quest, Task>> func) {
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
     * Unable to fully structure code
     */
    public void tasksByObjective(@NotNull PlayerProfile profile, @NotNull Objective<?> objective, @NotNull Consumer<Couple<Quest, Task>> func) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter(objective, (String)"objective");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        var4_4 = this.getQuests(true);
        $i$f$filter = false;
        var6_6 = $this$filter$iv;
        destination$iv$iv = new ArrayList<E>();
        $i$f$filterTo = false;
        for (T element$iv$iv : $this$filterTo$iv$iv) {
            it = (Quest)element$iv$iv;
            $i$a$-filter-PlayerProfile$tasksByObjective$1 = false;
            if (!(it.isFreeze() == false)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        $this$filter$iv = (List)destination$iv$iv;
        $i$f$forEach = false;
        for (T element$iv : $this$forEach$iv) {
            quest = (Quest)element$iv;
            $i$a$-forEach-PlayerProfile$tasksByObjective$2 = false;
            $this$filter$iv = quest.getTasks();
            $i$f$filter = false;
            $i$a$-filter-PlayerProfile$tasksByObjective$1 = $this$filter$iv;
            destination$iv$iv = new ArrayList<E>();
            $i$f$filterTo = false;
            for (T element$iv$iv : $this$filterTo$iv$iv) {
                it = (Task)element$iv$iv;
                $i$a$-filter-PlayerProfile$tasksByObjective$2$1 = false;
                if (!Intrinsics.areEqual(it.getObjective(), objective) || it.isCompleted(profile)) ** GOTO lbl-1000
                v0 = it;
                v1 = profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)v1, (String)"profile.player");
                if (AddonDepend.Companion.isQuestDependCompleted(v0, v1)) {
                    v2 = true;
                } else lbl-1000:
                // 2 sources

                {
                    v2 = false;
                }
                if (!v2) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            $i$f$forEach = false;
            for (T element$iv : $this$forEach$iv) {
                it = (Task)element$iv;
                $i$a$-forEach-PlayerProfile$tasksByObjective$2$2 = false;
                func.accept(new Couple<Quest, Task>(quest, it));
            }
        }
    }

    public void push() {
        this.push(this.getPlayer());
    }

    public void push(@Nullable Player player2) {
        if (this.isDataChanged()) {
            Player player3 = player2;
            if (player3 == null && (player3 = Bukkit.getPlayer((UUID)this.uniqueId)) == null) {
                throw new IllegalStateException(("Player " + this.uniqueId + " is offline.").toString());
            }
            Player target = player3;
            Database.Companion.getINSTANCE().update(target, this);
            this.releaseQuests.removeIf(arg_0 -> PlayerProfile.push$lambda$14((Function1)new Function1<Quest, Boolean>(target, this){
                final /* synthetic */ Player $target;
                final /* synthetic */ PlayerProfile this$0;
                {
                    this.$target = $target;
                    this.this$0 = $receiver;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(Quest it) {
                    Database database = Database.Companion.getINSTANCE();
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    database.releaseQuest(this.$target, this.this$0, it);
                    return true;
                }
            }, arg_0));
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
        return func.apply(new QuestDataOperator(this, task));
    }

    public final void validation() {
        Map $this$forEach$iv = this.questMap;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            String k = (String)entry.getKey();
            Quest v = (Quest)entry.getValue();
            if (this.getQuestCompletedDate(k) <= v.getStartTime()) continue;
            this.questMap.remove(k);
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                        \u73a9\u5bb6 " + this.getPlayer().getName() + " \u4efb\u52a1 " + k + " \u72b6\u6001\u9519\u8bef\uff0c\u5df2\u81ea\u52a8\u6e05\u9664\u3002\n                        Player " + this.getPlayer().getName() + " has invalid quest " + k + ", it has been automatically removed.\n                    "))};
            IOKt.warning((Object[])objectArray);
        }
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

    private static final boolean push$lambda$14(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Boolean)$tmp0.invoke(p0);
    }
}

