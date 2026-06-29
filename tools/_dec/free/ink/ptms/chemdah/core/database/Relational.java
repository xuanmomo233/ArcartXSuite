/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.module.database.ActionInsert
 *  ink.ptms.chemdah.taboolib.module.database.ActionInsert$DuplicateUpdateBehavior
 *  ink.ptms.chemdah.taboolib.module.database.ActionSelect
 *  ink.ptms.chemdah.taboolib.module.database.ActionUpdate
 *  ink.ptms.chemdah.taboolib.module.database.ColumnBuilder
 *  ink.ptms.chemdah.taboolib.module.database.ExecutableSource
 *  ink.ptms.chemdah.taboolib.module.database.Filter
 *  ink.ptms.chemdah.taboolib.module.database.Host
 *  ink.ptms.chemdah.taboolib.module.database.Table
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.Pair
 *  kotlin1822.Result
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.database.QuestTable;
import ink.ptms.chemdah.core.database.Relational;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestDataIsolation;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.module.database.ActionInsert;
import ink.ptms.chemdah.taboolib.module.database.ActionSelect;
import ink.ptms.chemdah.taboolib.module.database.ActionUpdate;
import ink.ptms.chemdah.taboolib.module.database.ColumnBuilder;
import ink.ptms.chemdah.taboolib.module.database.ExecutableSource;
import ink.ptms.chemdah.taboolib.module.database.Filter;
import ink.ptms.chemdah.taboolib.module.database.Host;
import ink.ptms.chemdah.taboolib.module.database.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import javax.sql.DataSource;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Pair;
import kotlin1822.Result;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0080\u0001\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010 \n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u0002*\u0004\b\u0001\u0010\u0003*\u0004\b\u0002\u0010\u00042\u00020\u0005B\u0005\u00a2\u0006\u0002\u0010\u0006J\u001f\u0010+\u001a\u0004\u0018\u00018\u00022\u0006\u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020/H&\u00a2\u0006\u0002\u00100J\u0017\u00101\u001a\u0004\u0018\u00018\u00012\u0006\u0010,\u001a\u00020-H&\u00a2\u0006\u0002\u00102J\u0018\u00103\u001a\b\u0012\u0004\u0012\u00028\u00000!2\b\u00104\u001a\u0004\u0018\u00010\u0015H&J \u00105\u001a\u0002062\u0006\u0010,\u001a\u00020-2\u0006\u00107\u001a\u0002082\u0006\u0010.\u001a\u00020/H\u0016J\u0010\u00109\u001a\u0002062\u0006\u0010:\u001a\u00020\u0015H\u0014J\u0012\u0010;\u001a\u0004\u0018\u00010\u00152\u0006\u0010:\u001a\u00020\u0015H\u0014J\u001a\u0010<\u001a\u0014\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00028\u0001\u0012\u0004\u0012\u00028\u00020\u0000H\u0016J\u001a\u0010=\u001a\b\u0012\u0004\u0012\u00028\u00000!2\n\b\u0002\u0010>\u001a\u0004\u0018\u00010\u0015H\u0016J\u0018\u0010?\u001a\u0002062\u0006\u0010:\u001a\u00020\u00152\u0006\u0010@\u001a\u00020\u0015H\u0014J\u000e\u0010A\u001a\b\u0012\u0004\u0012\u00020\u00150BH\u0016J!\u0010C\u001a\u000206*\u0002082\u0006\u0010D\u001a\u00028\u00012\u0006\u0010.\u001a\u00020/H&\u00a2\u0006\u0002\u0010EJ\u001a\u0010F\u001a\b\u0012\u0004\u0012\u00028\u00010G*\u0002082\u0006\u0010,\u001a\u00020-H&J\u0014\u0010H\u001a\u000206*\u0002082\u0006\u0010,\u001a\u00020-H\u0016J\u0014\u0010I\u001a\u000206*\u0002082\u0006\u0010,\u001a\u00020-H\u0016R\u001b\u0010\u0007\u001a\u00020\b8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000b\u0010\f\u001a\u0004\b\t\u0010\nR\u0018\u0010\r\u001a\b\u0012\u0004\u0012\u00028\u00000\u000eX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0012\u0010\u0011\u001a\u00020\u0012X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0011\u0010\u0013R\u0012\u0010\u0014\u001a\u00020\u0015X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0018\u001a\u00020\u0019\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u001bR\u0011\u0010\u001c\u001a\u00020\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u0017RC\u0010\u001e\u001a*\u0012\f\u0012\n  *\u0004\u0018\u00010\u00150\u0015\u0012\u0018\u0012\u0016\u0012\u0004\u0012\u00028\u0000  *\n\u0012\u0004\u0012\u00028\u0000\u0018\u00010!0!0\u001f8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b$\u0010\f\u001a\u0004\b\"\u0010#R$\u0010%\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u000e\u0012\u0004\u0012\u00028\u00000&X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b'\u0010(R$\u0010)\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u000e\u0012\u0004\u0012\u00028\u00000&X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b*\u0010(\u00a8\u0006J"}, d2={"Link/ptms/chemdah/core/database/Relational;", "Type", "Link/ptms/chemdah/taboolib/module/database/ColumnBuilder;", "UserId", "QuestId", "Link/ptms/chemdah/core/database/Database;", "()V", "dataSource", "Ljavax/sql/DataSource;", "getDataSource", "()Ljavax/sql/DataSource;", "dataSource$delegate", "Lkotlin1822/Lazy;", "host", "Link/ptms/chemdah/taboolib/module/database/Host;", "getHost", "()Link/ptms/chemdah/taboolib/module/database/Host;", "isDuplicateKeyUpdateSupported", "", "()Z", "questKey", "", "getQuestKey", "()Ljava/lang/String;", "questLock", "Ljava/util/concurrent/locks/StampedLock;", "getQuestLock", "()Ljava/util/concurrent/locks/StampedLock;", "tablePrefix", "getTablePrefix", "tableQuest", "Ljava/util/concurrent/ConcurrentHashMap;", "kotlin1822.jvm.PlatformType", "Link/ptms/chemdah/core/database/QuestTable;", "getTableQuest", "()Ljava/util/concurrent/ConcurrentHashMap;", "tableQuest$delegate", "tableUserData", "Link/ptms/chemdah/taboolib/module/database/Table;", "getTableUserData", "()Link/ptms/chemdah/taboolib/module/database/Table;", "tableVariables", "getTableVariables", "getQuestId", "player", "Lorg/bukkit/entity/Player;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/quest/Quest;)Ljava/lang/Object;", "getUserId", "(Lorg/bukkit/entity/Player;)Ljava/lang/Object;", "newQuestTable", "name", "releaseQuest", "", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "releaseVariable0", "key", "selectVariable0", "setup", "table", "id", "updateVariable0", "value", "variables", "", "createQuest", "userId", "(Link/ptms/chemdah/core/PlayerProfile;Ljava/lang/Object;Link/ptms/chemdah/core/quest/Quest;)V", "createUser", "Ljava/util/concurrent/CompletableFuture;", "update", "updateQuest", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nRelational.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Relational.kt\nink/ptms/chemdah/core/database/Relational\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 Execution.kt\ntaboolib/common/util/ExecutionKt\n+ 5 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 6 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,359:1\n1855#2,2:360\n766#2:370\n857#2,2:371\n1477#2:373\n1502#2,3:374\n1505#2,3:384\n1#3:362\n10#4,7:363\n10#4,7:388\n10#4,7:395\n361#5,7:377\n215#6:387\n216#6:402\n*S KotlinDebug\n*F\n+ 1 Relational.kt\nink/ptms/chemdah/core/database/Relational\n*L\n80#1:360,2\n190#1:370\n190#1:371,2\n202#1:373\n202#1:374,3\n202#1:384,3\n137#1:363,7\n208#1:388,7\n218#1:395,7\n202#1:377,7\n204#1:387\n204#1:402\n*E\n"})
public abstract class Relational<Type extends ColumnBuilder, UserId, QuestId>
extends Database {
    @NotNull
    private final Lazy tableQuest$delegate = LazyMakerKt.unsafeLazy((Function0)new Function0<ConcurrentHashMap<String, QuestTable<Type>>>(this){
        final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final ConcurrentHashMap<String, QuestTable<Type>> invoke() {
            void $this$associateWith$iv;
            Iterable iterable = QuestDataIsolation.INSTANCE.getKeys();
            Relational<Type, UserId, QuestId> relational = this.this$0;
            boolean $i$f$associateWith = false;
            LinkedHashMap<K, V> result$iv = new LinkedHashMap<K, V>(RangesKt.coerceAtLeast((int)MapsKt.mapCapacity((int)CollectionsKt.collectionSizeOrDefault((Iterable)$this$associateWith$iv, (int)10)), (int)16));
            void $this$associateWithTo$iv$iv = $this$associateWith$iv;
            boolean $i$f$associateWithTo = false;
            for (T element$iv$iv : $this$associateWithTo$iv$iv) {
                void it;
                String string = (String)element$iv$iv;
                T t = element$iv$iv;
                Map map = result$iv;
                boolean bl = false;
                QuestTable<Type> questTable = relational.newQuestTable((String)(!Intrinsics.areEqual((Object)it, (Object)"~") ? it : null));
                map.put(t, questTable);
            }
            Map map = result$iv;
            return new ConcurrentHashMap<String, QuestTable<Type>>(map);
        }
    });
    @NotNull
    private final String tablePrefix;
    @NotNull
    private final Lazy dataSource$delegate;
    @NotNull
    private final StampedLock questLock;

    public Relational() {
        String string = Chemdah.INSTANCE.getConf().getString("database.source.SQL.table", "chemdah");
        Intrinsics.checkNotNull((Object)string);
        this.tablePrefix = string;
        this.dataSource$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<DataSource>(this){
            final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
            {
                this.this$0 = $receiver;
                super(0);
            }

            @NotNull
            public final DataSource invoke() {
                return Host.createDataSource$default(this.this$0.getHost(), (boolean)false, (boolean)false, (int)3, null);
            }
        }));
        this.questLock = new StampedLock();
    }

    @NotNull
    public final ConcurrentHashMap<String, QuestTable<Type>> getTableQuest() {
        Lazy lazy = this.tableQuest$delegate;
        return (ConcurrentHashMap)lazy.getValue();
    }

    @NotNull
    public final String getTablePrefix() {
        return this.tablePrefix;
    }

    @NotNull
    public final DataSource getDataSource() {
        Lazy lazy = this.dataSource$delegate;
        return (DataSource)lazy.getValue();
    }

    @NotNull
    public final StampedLock getQuestLock() {
        return this.questLock;
    }

    @NotNull
    public abstract Host<Type> getHost();

    @NotNull
    public abstract Table<Host<Type>, Type> getTableUserData();

    @NotNull
    public abstract Table<Host<Type>, Type> getTableVariables();

    @NotNull
    public abstract String getQuestKey();

    public abstract boolean isDuplicateKeyUpdateSupported();

    @NotNull
    public Relational<Type, UserId, QuestId> setup() {
        if (!Database.Companion.isDisableAutoCreateTable()) {
            Table.createTable$default(this.getTableUserData(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
            Object[] objectArray = new String[]{"user", "key"};
            Table.createIndex$default(this.getTableUserData(), (DataSource)this.getDataSource(), (String)"idx_user_and_key", (List)CollectionsKt.listOf((Object[])objectArray), (boolean)true, (boolean)false, (int)16, null);
            Table.createTable$default(this.getTableVariables(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
            Collection<QuestTable<Type>> collection = this.getTableQuest().values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"tableQuest.values");
            Iterable $this$forEach$iv = collection;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                QuestTable it = (QuestTable)element$iv;
                boolean bl = false;
                Table.createTable$default(it.getQuest(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
                Table.createTable$default(it.getQuestData(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
                Object[] objectArray2 = new String[]{this.getQuestKey(), "key"};
                Table.createIndex$default(it.getQuestData(), (DataSource)this.getDataSource(), (String)"idx_quest_and_key", (List)CollectionsKt.listOf((Object[])objectArray2), (boolean)true, (boolean)false, (int)16, null);
            }
        }
        return this;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @NotNull
    public QuestTable<Type> table(@Nullable String id2) {
        long stamp = this.questLock.tryOptimisticRead();
        ConcurrentHashMap<String, QuestTable<Type>> concurrentHashMap = this.getTableQuest();
        String string = id2;
        if (string == null) {
            string = "~";
        }
        QuestTable<Type> table = concurrentHashMap.get(string);
        if (!this.questLock.validate(stamp)) {
            stamp = this.questLock.readLock();
            try {
                ConcurrentHashMap<String, QuestTable<Type>> concurrentHashMap2 = this.getTableQuest();
                String string2 = id2;
                if (string2 == null) {
                    string2 = "~";
                }
                table = concurrentHashMap2.get(string2);
            }
            finally {
                this.questLock.unlockRead(stamp);
            }
        }
        QuestTable<Type> questTable = table;
        if (questTable != null) {
            return questTable;
        }
        stamp = this.questLock.writeLock();
        try {
            ConcurrentHashMap<String, QuestTable<Type>> concurrentHashMap3 = this.getTableQuest();
            String string3 = id2;
            if (string3 == null) {
                string3 = "~";
            }
            if ((table = concurrentHashMap3.get(string3)) == null) {
                Object[] objectArray;
                Object[] it = objectArray = this.newQuestTable(id2);
                boolean bl = false;
                Map map = this.getTableQuest();
                String string4 = id2;
                if (string4 == null) {
                    string4 = "~";
                }
                map.put(string4, it);
                Object[] newTable = objectArray;
                if (!Database.Companion.isDisableAutoCreateTable()) {
                    Table.createTable$default(newTable.getQuest(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
                    Table.createTable$default(newTable.getQuestData(), (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
                    objectArray = new String[]{this.getQuestKey(), "key"};
                    Table.createIndex$default(newTable.getQuestData(), (DataSource)this.getDataSource(), (String)"idx_quest_and_key", (List)CollectionsKt.listOf((Object[])objectArray), (boolean)true, (boolean)false, (int)16, null);
                }
                table = newTable;
            }
        }
        finally {
            this.questLock.unlockWrite(stamp);
        }
        QuestTable<Type> questTable2 = table;
        Intrinsics.checkNotNull(questTable2);
        return questTable2;
    }

    public static /* synthetic */ QuestTable table$default(Relational relational, String string, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: table");
        }
        if ((n & 1) != 0) {
            string = null;
        }
        return relational.table(string);
    }

    public void update(@NotNull PlayerProfile $this$update, @NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)$this$update, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        if ($this$update.getPersistentDataContainer().isChanged()) {
            UserId UserId = this.getUserId(player2);
            if (UserId == null) {
                return;
            }
            UserId userId = UserId;
            boolean $i$f$execution = false;
            long startTime$iv = System.nanoTime();
            boolean bl = false;
            Result result$iv = Result.box-impl((Object)this.getTableUserData().transaction-gIAlu-s(this.getDataSource(), (Function1)new Function1<ExecutableSource, Unit>($this$update, this, userId){
                final /* synthetic */ PlayerProfile $this_update;
                final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                final /* synthetic */ UserId $userId;
                {
                    this.$this_update = $receiver;
                    this.this$0 = $receiver2;
                    this.$userId = $userId;
                    super(1);
                }

                /*
                 * WARNING - void declaration
                 */
                public final void invoke(@NotNull ExecutableSource $this$transaction) {
                    void $this$forEach$iv;
                    Intrinsics.checkNotNullParameter((Object)$this$transaction, (String)"$this$transaction");
                    ChangeTracker tracker = this.$this_update.getPersistentDataContainer().flush();
                    Map<String, Data> map = tracker.getModified();
                    Relational<Type, UserId, QuestId> relational = this.this$0;
                    UserId UserId = this.$userId;
                    boolean $i$f$forEach = false;
                    Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                    while (iterator.hasNext()) {
                        String[] stringArray;
                        Map.Entry<K, V> element$iv;
                        Map.Entry<K, V> entry = element$iv = iterator.next();
                        boolean bl = false;
                        String key = (String)entry.getKey();
                        Data data2 = (Data)entry.getValue();
                        if (relational.isDuplicateKeyUpdateSupported()) {
                            stringArray = new String[]{"user", "key", "value", "mode"};
                            $this$transaction.insert(stringArray, (Function1)new Function1<ActionInsert, Unit>(UserId, key, data2){
                                final /* synthetic */ UserId $userId;
                                final /* synthetic */ String $key;
                                final /* synthetic */ Data $data;
                                {
                                    this.$userId = $userId;
                                    this.$key = $key;
                                    this.$data = $data;
                                    super(1);
                                }

                                public final void invoke(@NotNull ActionInsert $this$insert) {
                                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                    Object[] objectArray = new Object[]{this.$userId, this.$key, this.$data.getData(), 1};
                                    $this$insert.value(objectArray);
                                    $this$insert.onDuplicateKeyUpdate((Function1)new Function1<ActionInsert.DuplicateUpdateBehavior, Unit>(this.$data){
                                        final /* synthetic */ Data $data;
                                        {
                                            this.$data = $data;
                                            super(1);
                                        }

                                        public final void invoke(@NotNull ActionInsert.DuplicateUpdateBehavior $this$onDuplicateKeyUpdate) {
                                            Intrinsics.checkNotNullParameter((Object)$this$onDuplicateKeyUpdate, (String)"$this$onDuplicateKeyUpdate");
                                            $this$onDuplicateKeyUpdate.update("value", this.$data.getData());
                                            $this$onDuplicateKeyUpdate.update("mode", (Object)1);
                                        }
                                    });
                                }
                            });
                            continue;
                        }
                        if ($this$transaction.select((Function1)new Function1<ActionSelect, Unit>(UserId, key){
                            final /* synthetic */ UserId $userId;
                            final /* synthetic */ String $key;
                            {
                                this.$userId = $userId;
                                this.$key = $key;
                                super(1);
                            }

                            public final void invoke(@NotNull ActionSelect $this$select) {
                                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                                $this$select.where($this$select.and($this$select.eq("user", this.$userId), $this$select.eq("key", (Object)this.$key)));
                            }
                        }).find()) {
                            $this$transaction.update((Function1)new Function1<ActionUpdate, Unit>(UserId, key, data2){
                                final /* synthetic */ UserId $userId;
                                final /* synthetic */ String $key;
                                final /* synthetic */ Data $data;
                                {
                                    this.$userId = $userId;
                                    this.$key = $key;
                                    this.$data = $data;
                                    super(1);
                                }

                                public final void invoke(@NotNull ActionUpdate $this$update) {
                                    Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                                    $this$update.where($this$update.and($this$update.eq("user", this.$userId), $this$update.eq("key", (Object)this.$key)));
                                    $this$update.set("value", this.$data.getData());
                                    $this$update.set("mode", (Object)1);
                                }
                            });
                            continue;
                        }
                        stringArray = new String[]{"user", "key", "value", "mode"};
                        $this$transaction.insert(stringArray, (Function1)new Function1<ActionInsert, Unit>(UserId, key, data2){
                            final /* synthetic */ UserId $userId;
                            final /* synthetic */ String $key;
                            final /* synthetic */ Data $data;
                            {
                                this.$userId = $userId;
                                this.$key = $key;
                                this.$data = $data;
                                super(1);
                            }

                            public final void invoke(@NotNull ActionInsert $this$insert) {
                                Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                Object[] objectArray = new Object[]{this.$userId, this.$key, this.$data.getData(), 1};
                                $this$insert.value(objectArray);
                            }
                        });
                    }
                    if (!((Collection)tracker.getDrops()).isEmpty()) {
                        $this$transaction.update((Function1)new Function1<ActionUpdate, Unit>(this.$userId, tracker){
                            final /* synthetic */ UserId $userId;
                            final /* synthetic */ ChangeTracker $tracker;
                            {
                                this.$userId = $userId;
                                this.$tracker = $tracker;
                                super(1);
                            }

                            public final void invoke(@NotNull ActionUpdate $this$update) {
                                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                                $this$update.where((Function1)new Function1<Filter, Unit>(this.$userId, this.$tracker){
                                    final /* synthetic */ UserId $userId;
                                    final /* synthetic */ ChangeTracker $tracker;
                                    {
                                        this.$userId = $userId;
                                        this.$tracker = $tracker;
                                        super(1);
                                    }

                                    public final void invoke(@NotNull Filter $this$where) {
                                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                                        Collection $this$toTypedArray$iv = this.$tracker.getDrops();
                                        boolean $i$f$toTypedArray = false;
                                        Collection thisCollection$iv = $this$toTypedArray$iv;
                                        $this$where.and($this$where.eq("user", this.$userId), $this$where.inside("key", thisCollection$iv.toArray(new Object[0])));
                                    }
                                });
                                $this$update.set("value", null);
                                $this$update.set("mode", (Object)0);
                            }
                        });
                    }
                }
            }));
            long endTime$iv = System.nanoTime();
            long duration$iv = (endTime$iv - startTime$iv) / (long)1000000;
            Pair pair = new Pair((Object)result$iv, (Object)duration$iv);
            Object result = ((Result)pair.component1()).unbox-impl();
            long l = ((Number)pair.component2()).longValue();
        }
    }

    /*
     * WARNING - void declaration
     */
    public void updateQuest(@NotNull PlayerProfile $this$updateQuest, @NotNull Player player2) {
        void $this$forEach$iv;
        void $this$groupByTo$iv$iv;
        Object $this$groupBy$iv;
        void $this$filterTo$iv$iv;
        Iterable $this$filter$iv;
        Intrinsics.checkNotNullParameter((Object)$this$updateQuest, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        UserId UserId = this.getUserId(player2);
        if (UserId == null) {
            return;
        }
        UserId userId = UserId;
        Iterable iterable = PlayerProfile.getQuests$default($this$updateQuest, false, 1, null);
        boolean $i$f$filter = false;
        Iterator iterator = $this$filter$iv;
        Object destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            boolean bl;
            Quest quest2 = (Quest)element$iv$iv;
            boolean bl2 = false;
            if (quest2.getNewQuest() || quest2.getPersistentDataContainer().isChanged()) {
                quest2.setNewQuest(false);
                QuestId questId2 = this.getQuestId(player2, quest2);
                if (questId2 == null) {
                    this.createQuest($this$updateQuest, userId, quest2);
                    bl = false;
                } else {
                    bl = true;
                }
            } else {
                bl = false;
            }
            if (!bl) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        $this$filter$iv = (List)destination$iv$iv;
        boolean $i$f$groupBy = false;
        $this$filterTo$iv$iv = $this$groupBy$iv;
        destination$iv$iv = new LinkedHashMap();
        boolean $i$f$groupByTo = false;
        for (Object element$iv$iv : $this$groupByTo$iv$iv) {
            Object object;
            Quest it = (Quest)element$iv$iv;
            boolean bl = false;
            String string = it.getTemplate().getDataIsolation();
            if (string == null) {
                string = "~";
            }
            String key$iv$iv = string;
            Object $this$getOrPut$iv$iv$iv = destination$iv$iv;
            boolean $i$f$getOrPut = false;
            Object value$iv$iv$iv = $this$getOrPut$iv$iv$iv.get(key$iv$iv);
            if (value$iv$iv$iv == null) {
                boolean bl3 = false;
                List answer$iv$iv$iv = new ArrayList();
                $this$getOrPut$iv$iv$iv.put(key$iv$iv, answer$iv$iv$iv);
                object = answer$iv$iv$iv;
            } else {
                object = value$iv$iv$iv;
            }
            List list$iv$iv = (List)object;
            list$iv$iv.add(element$iv$iv);
        }
        $this$groupBy$iv = destination$iv$iv;
        boolean $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            String id2 = (String)entry.getKey();
            List questList = (List)entry.getValue();
            QuestTable<Type> questTable = this.table(id2);
            Table<Host<Type>, Type> tq = questTable.component1();
            Table<Host<Type>, Type> td = questTable.component2();
            boolean $i$f$execution = false;
            long startTime$iv = System.nanoTime();
            boolean bl4 = false;
            Result result$iv = Result.box-impl((Object)tq.transaction-gIAlu-s(this.getDataSource(), (Function1)new Function1<ExecutableSource, Unit>((List<? extends Quest>)questList, userId, this, player2){
                final /* synthetic */ List<Quest> $questList;
                final /* synthetic */ UserId $userId;
                final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                final /* synthetic */ Player $player;
                {
                    this.$questList = $questList;
                    this.$userId = $userId;
                    this.this$0 = $receiver;
                    this.$player = $player;
                    super(1);
                }

                public final void invoke(@NotNull ExecutableSource $this$transaction) {
                    Intrinsics.checkNotNullParameter((Object)$this$transaction, (String)"$this$transaction");
                    $this$transaction.update((Function1)new Function1<ActionUpdate, Unit>(this.$questList, this.$userId, this.this$0, this.$player){
                        final /* synthetic */ List<Quest> $questList;
                        final /* synthetic */ UserId $userId;
                        final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                        final /* synthetic */ Player $player;
                        {
                            this.$questList = $questList;
                            this.$userId = $userId;
                            this.this$0 = $receiver;
                            this.$player = $player;
                            super(1);
                        }

                        /*
                         * WARNING - void declaration
                         */
                        public final void invoke(@NotNull ActionUpdate $this$update) {
                            void $this$mapTo$iv$iv;
                            void $this$map$iv;
                            Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                            Iterable iterable = this.$questList;
                            Relational<Type, UserId, QuestId> relational = this.this$0;
                            Player player2 = this.$player;
                            boolean $i$f$map = false;
                            void var7_7 = $this$map$iv;
                            Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                            boolean $i$f$mapTo = false;
                            for (T item$iv$iv : $this$mapTo$iv$iv) {
                                void quest2;
                                Quest quest3 = (Quest)item$iv$iv;
                                Collection collection = destination$iv$iv;
                                boolean bl = false;
                                QuestId QuestId = relational.getQuestId(player2, (Quest)quest2);
                                Intrinsics.checkNotNull(QuestId);
                                collection.add(QuestId);
                            }
                            Collection $this$toTypedArray$iv = (List)destination$iv$iv;
                            boolean $i$f$toTypedArray = false;
                            Collection thisCollection$iv = $this$toTypedArray$iv;
                            Object[] questIds = thisCollection$iv.toArray(new Object[0]);
                            $this$update.where($this$update.and($this$update.inside("id", questIds), $this$update.eq("user", this.$userId)));
                            $this$update.set("mode", (Object)1);
                        }
                    });
                }
            }));
            long endTime$iv = System.nanoTime();
            long duration$iv = (endTime$iv - startTime$iv) / (long)1000000;
            Pair pair = new Pair((Object)result$iv, (Object)duration$iv);
            Object r1 = ((Result)pair.component1()).unbox-impl();
            long c1 = ((Number)pair.component2()).longValue();
            boolean $i$f$execution2 = false;
            long startTime$iv2 = System.nanoTime();
            boolean bl5 = false;
            Result result$iv2 = Result.box-impl((Object)td.transaction-gIAlu-s(this.getDataSource(), (Function1)new Function1<ExecutableSource, Unit>((List<? extends Quest>)questList, this, player2){
                final /* synthetic */ List<Quest> $questList;
                final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                final /* synthetic */ Player $player;
                {
                    this.$questList = $questList;
                    this.this$0 = $receiver;
                    this.$player = $player;
                    super(1);
                }

                /*
                 * WARNING - void declaration
                 */
                public final void invoke(@NotNull ExecutableSource $this$transaction) {
                    void $this$forEach$iv;
                    Intrinsics.checkNotNullParameter((Object)$this$transaction, (String)"$this$transaction");
                    Iterable iterable = this.$questList;
                    Relational<Type, UserId, QuestId> relational = this.this$0;
                    Player player2 = this.$player;
                    boolean $i$f$forEach = false;
                    for (T element$iv : $this$forEach$iv) {
                        QuestId questId2;
                        Quest quest2 = (Quest)element$iv;
                        boolean bl = false;
                        Intrinsics.checkNotNull(relational.getQuestId(player2, quest2));
                        ChangeTracker tracker = quest2.getPersistentDataContainer().flush();
                        Map<String, Data> $this$forEach$iv2 = tracker.getModified();
                        boolean $i$f$forEach2 = false;
                        Iterator<Map.Entry<String, Data>> iterator = $this$forEach$iv2.entrySet().iterator();
                        while (iterator.hasNext()) {
                            String[] stringArray;
                            Map.Entry<String, Data> element$iv2;
                            Map.Entry<String, Data> entry = element$iv2 = iterator.next();
                            boolean bl2 = false;
                            String key = entry.getKey();
                            Data data2 = entry.getValue();
                            if (relational.isDuplicateKeyUpdateSupported()) {
                                stringArray = new String[]{relational.getQuestKey(), "key", "value", "mode"};
                                $this$transaction.insert(stringArray, (Function1)new Function1<ActionInsert, Unit>(questId2, key, data2){
                                    final /* synthetic */ QuestId $questId;
                                    final /* synthetic */ String $key;
                                    final /* synthetic */ Data $data;
                                    {
                                        this.$questId = $questId;
                                        this.$key = $key;
                                        this.$data = $data;
                                        super(1);
                                    }

                                    public final void invoke(@NotNull ActionInsert $this$insert) {
                                        Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                        Object[] objectArray = new Object[]{this.$questId, this.$key, this.$data.getData(), 1};
                                        $this$insert.value(objectArray);
                                        $this$insert.onDuplicateKeyUpdate((Function1)new Function1<ActionInsert.DuplicateUpdateBehavior, Unit>(this.$data){
                                            final /* synthetic */ Data $data;
                                            {
                                                this.$data = $data;
                                                super(1);
                                            }

                                            public final void invoke(@NotNull ActionInsert.DuplicateUpdateBehavior $this$onDuplicateKeyUpdate) {
                                                Intrinsics.checkNotNullParameter((Object)$this$onDuplicateKeyUpdate, (String)"$this$onDuplicateKeyUpdate");
                                                $this$onDuplicateKeyUpdate.update("value", this.$data.getData());
                                                $this$onDuplicateKeyUpdate.update("mode", (Object)1);
                                            }
                                        });
                                    }
                                });
                                continue;
                            }
                            if ($this$transaction.select((Function1)new Function1<ActionSelect, Unit>(relational, questId2, key){
                                final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                                final /* synthetic */ QuestId $questId;
                                final /* synthetic */ String $key;
                                {
                                    this.this$0 = $receiver;
                                    this.$questId = $questId;
                                    this.$key = $key;
                                    super(1);
                                }

                                public final void invoke(@NotNull ActionSelect $this$select) {
                                    Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                                    $this$select.where($this$select.and($this$select.eq(this.this$0.getQuestKey(), this.$questId), $this$select.eq("key", (Object)this.$key)));
                                }
                            }).find()) {
                                $this$transaction.update((Function1)new Function1<ActionUpdate, Unit>(relational, questId2, key, data2){
                                    final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                                    final /* synthetic */ QuestId $questId;
                                    final /* synthetic */ String $key;
                                    final /* synthetic */ Data $data;
                                    {
                                        this.this$0 = $receiver;
                                        this.$questId = $questId;
                                        this.$key = $key;
                                        this.$data = $data;
                                        super(1);
                                    }

                                    public final void invoke(@NotNull ActionUpdate $this$update) {
                                        Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                                        $this$update.where($this$update.and($this$update.eq(this.this$0.getQuestKey(), this.$questId), $this$update.eq("key", (Object)this.$key)));
                                        $this$update.set("value", this.$data.getData());
                                        $this$update.set("mode", (Object)1);
                                    }
                                });
                                continue;
                            }
                            stringArray = new String[]{relational.getQuestKey(), "key", "value", "mode"};
                            $this$transaction.insert(stringArray, (Function1)new Function1<ActionInsert, Unit>(questId2, key, data2){
                                final /* synthetic */ QuestId $questId;
                                final /* synthetic */ String $key;
                                final /* synthetic */ Data $data;
                                {
                                    this.$questId = $questId;
                                    this.$key = $key;
                                    this.$data = $data;
                                    super(1);
                                }

                                public final void invoke(@NotNull ActionInsert $this$insert) {
                                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                    Object[] objectArray = new Object[]{this.$questId, this.$key, this.$data.getData(), 1};
                                    $this$insert.value(objectArray);
                                }
                            });
                        }
                        if (!(!((Collection)tracker.getDrops()).isEmpty())) continue;
                        $this$transaction.update((Function1)new Function1<ActionUpdate, Unit>(relational, questId2, tracker){
                            final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                            final /* synthetic */ QuestId $questId;
                            final /* synthetic */ ChangeTracker $tracker;
                            {
                                this.this$0 = $receiver;
                                this.$questId = $questId;
                                this.$tracker = $tracker;
                                super(1);
                            }

                            public final void invoke(@NotNull ActionUpdate $this$update) {
                                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                                $this$update.where((Function1)new Function1<Filter, Unit>(this.this$0, this.$questId, this.$tracker){
                                    final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                                    final /* synthetic */ QuestId $questId;
                                    final /* synthetic */ ChangeTracker $tracker;
                                    {
                                        this.this$0 = $receiver;
                                        this.$questId = $questId;
                                        this.$tracker = $tracker;
                                        super(1);
                                    }

                                    public final void invoke(@NotNull Filter $this$where) {
                                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                                        Collection $this$toTypedArray$iv = this.$tracker.getDrops();
                                        boolean $i$f$toTypedArray = false;
                                        Collection thisCollection$iv = $this$toTypedArray$iv;
                                        $this$where.and($this$where.eq(this.this$0.getQuestKey(), this.$questId), $this$where.inside("key", thisCollection$iv.toArray(new Object[0])));
                                    }
                                });
                                $this$update.set("value", null);
                                $this$update.set("mode", (Object)0);
                            }
                        });
                    }
                }
            }));
            long endTime$iv2 = System.nanoTime();
            long duration$iv2 = (endTime$iv2 - startTime$iv2) / (long)1000000;
            Pair pair2 = new Pair((Object)result$iv2, (Object)duration$iv2);
            Object r2 = ((Result)pair2.component1()).unbox-impl();
            long c2 = ((Number)pair2.component2()).longValue();
        }
    }

    @NotNull
    public abstract QuestTable<Type> newQuestTable(@Nullable String var1);

    @Nullable
    public abstract UserId getUserId(@NotNull Player var1);

    @Nullable
    public abstract QuestId getQuestId(@NotNull Player var1, @NotNull Quest var2);

    @NotNull
    public abstract CompletableFuture<UserId> createUser(@NotNull PlayerProfile var1, @NotNull Player var2);

    public abstract void createQuest(@NotNull PlayerProfile var1, UserId var2, @NotNull Quest var3);

    @Override
    public void releaseQuest(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(quest2.getId());
        if (template == null) {
            return;
        }
        Template template2 = template;
        QuestId QuestId = this.getQuestId(player2, quest2);
        if (QuestId == null) {
            return;
        }
        QuestId questId2 = QuestId;
        QuestTable<Type> questTable = this.table(template2.getDataIsolation());
        Table<Host<Type>, Type> tq = questTable.component1();
        Table<Host<Type>, Type> td = questTable.component2();
        tq.update(this.getDataSource(), (Function1)new Function1<ActionUpdate, Unit>(this, player2, quest2){
            final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
            final /* synthetic */ Player $player;
            final /* synthetic */ Quest $quest;
            {
                this.this$0 = $receiver;
                this.$player = $player;
                this.$quest = $quest;
                super(1);
            }

            public final void invoke(@NotNull ActionUpdate $this$update) {
                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                $this$update.where((Function1)new Function1<Filter, Unit>(this.this$0, this.$player, this.$quest){
                    final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                    final /* synthetic */ Player $player;
                    final /* synthetic */ Quest $quest;
                    {
                        this.this$0 = $receiver;
                        this.$player = $player;
                        this.$quest = $quest;
                        super(1);
                    }

                    public final void invoke(@NotNull Filter $this$where) {
                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                        $this$where.and($this$where.eq("user", this.this$0.getUserId(this.$player)), $this$where.eq("quest", (Object)this.$quest.getId()));
                    }
                });
                $this$update.set("mode", (Object)0);
            }
        });
        td.update(this.getDataSource(), (Function1)new Function1<ActionUpdate, Unit>(this, questId2){
            final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
            final /* synthetic */ QuestId $questId;
            {
                this.this$0 = $receiver;
                this.$questId = $questId;
                super(1);
            }

            public final void invoke(@NotNull ActionUpdate $this$update) {
                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                $this$update.where((Function1)new Function1<Filter, Unit>(this.this$0, this.$questId){
                    final /* synthetic */ Relational<Type, UserId, QuestId> this$0;
                    final /* synthetic */ QuestId $questId;
                    {
                        this.this$0 = $receiver;
                        this.$questId = $questId;
                        super(1);
                    }

                    public final void invoke(@NotNull Filter $this$where) {
                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                        $this$where.eq(this.this$0.getQuestKey(), this.$questId);
                    }
                });
                $this$update.set("mode", (Object)0);
            }
        });
    }

    @Override
    @Nullable
    protected String selectVariable0(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return (String)this.getTableVariables().select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(key){
            final /* synthetic */ String $key;
            {
                this.$key = $key;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$select) {
                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                String[] stringArray = new String[]{"data"};
                $this$select.rows(stringArray);
                $this$select.where((Function1)new Function1<Filter, Unit>(this.$key){
                    final /* synthetic */ String $key;
                    {
                        this.$key = $key;
                        super(1);
                    }

                    public final void invoke(@NotNull Filter $this$where) {
                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                        $this$where.and($this$where.eq("name", (Object)this.$key), $this$where.eq("mode", (Object)1));
                    }
                });
                $this$select.limit(1);
            }
        }).firstOrNull((Function1)selectVariable0.2.INSTANCE);
    }

    @Override
    protected void updateVariable0(@NotNull String key, @NotNull String value2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        if (this.getTableVariables().find(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(key){
            final /* synthetic */ String $key;
            {
                this.$key = $key;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$find) {
                Intrinsics.checkNotNullParameter((Object)$this$find, (String)"$this$find");
                $this$find.where($this$find.eq("name", (Object)this.$key));
            }
        })) {
            this.getTableVariables().update(this.getDataSource(), (Function1)new Function1<ActionUpdate, Unit>(value2, key){
                final /* synthetic */ String $value;
                final /* synthetic */ String $key;
                {
                    this.$value = $value;
                    this.$key = $key;
                    super(1);
                }

                public final void invoke(@NotNull ActionUpdate $this$update) {
                    Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                    $this$update.where((Function1)new Function1<Filter, Unit>(this.$key){
                        final /* synthetic */ String $key;
                        {
                            this.$key = $key;
                            super(1);
                        }

                        public final void invoke(@NotNull Filter $this$where) {
                            Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                            $this$where.eq("name", (Object)this.$key);
                        }
                    });
                    $this$update.set("data", (Object)this.$value);
                    $this$update.set("mode", (Object)1);
                }
            });
        } else {
            String[] stringArray = new String[]{"name", "data", "mode"};
            this.getTableVariables().insert(this.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(key, value2){
                final /* synthetic */ String $key;
                final /* synthetic */ String $value;
                {
                    this.$key = $key;
                    this.$value = $value;
                    super(1);
                }

                public final void invoke(@NotNull ActionInsert $this$insert) {
                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                    Object[] objectArray = new Object[]{this.$key, this.$value, 1};
                    $this$insert.value(objectArray);
                }
            });
        }
    }

    @Override
    protected void releaseVariable0(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        this.getTableVariables().update(this.getDataSource(), (Function1)new Function1<ActionUpdate, Unit>(key){
            final /* synthetic */ String $key;
            {
                this.$key = $key;
                super(1);
            }

            public final void invoke(@NotNull ActionUpdate $this$update) {
                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                $this$update.where((Function1)new Function1<Filter, Unit>(this.$key){
                    final /* synthetic */ String $key;
                    {
                        this.$key = $key;
                        super(1);
                    }

                    public final void invoke(@NotNull Filter $this$where) {
                        Intrinsics.checkNotNullParameter((Object)$this$where, (String)"$this$where");
                        $this$where.eq("name", (Object)this.$key);
                    }
                });
                $this$update.set("data", null);
                $this$update.set("mode", (Object)0);
            }
        });
    }

    @Override
    @NotNull
    public List<String> variables() {
        return this.getTableVariables().select(this.getDataSource(), (Function1)variables.1.INSTANCE).map((Function1)variables.2.INSTANCE);
    }
}

