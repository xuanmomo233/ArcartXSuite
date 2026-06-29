/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.module.database.ActionInsert
 *  ink.ptms.chemdah.taboolib.module.database.ActionSelect
 *  ink.ptms.chemdah.taboolib.module.database.FileToHostKt
 *  ink.ptms.chemdah.taboolib.module.database.Host
 *  ink.ptms.chemdah.taboolib.module.database.HostSQLite
 *  ink.ptms.chemdah.taboolib.module.database.SQLite
 *  ink.ptms.chemdah.taboolib.module.database.Table
 *  kotlin.Metadata
 *  kotlin1822.NoWhenBranchMatchedException
 *  kotlin1822.Pair
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.core.database.DatabaseSQLite;
import ink.ptms.chemdah.core.database.QuestTable;
import ink.ptms.chemdah.core.database.Relational;
import ink.ptms.chemdah.core.database.UserIndex;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestDataIsolation;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.module.database.ActionInsert;
import ink.ptms.chemdah.taboolib.module.database.ActionSelect;
import ink.ptms.chemdah.taboolib.module.database.FileToHostKt;
import ink.ptms.chemdah.taboolib.module.database.Host;
import ink.ptms.chemdah.taboolib.module.database.HostSQLite;
import ink.ptms.chemdah.taboolib.module.database.SQLite;
import ink.ptms.chemdah.taboolib.module.database.Table;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kotlin.Metadata;
import kotlin1822.NoWhenBranchMatchedException;
import kotlin1822.Pair;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 )2\u0014\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00030\u0001:\u0001)B\u0005\u00a2\u0006\u0002\u0010\u0004J\u001a\u0010\u0016\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u001aH\u0016J\u0010\u0010\u001b\u001a\u00020\u00032\u0006\u0010\u0017\u001a\u00020\u0018H\u0016J\u0018\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00020\u001d2\b\u0010\u001e\u001a\u0004\u0018\u00010\u0003H\u0016J\u0010\u0010\u001f\u001a\u00020 2\u0006\u0010\u0017\u001a\u00020\u0018H\u0016J\u0018\u0010!\u001a\u00020\"2\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010#\u001a\u00020 H\u0016J\u001c\u0010$\u001a\u00020\"*\u00020 2\u0006\u0010%\u001a\u00020\u00032\u0006\u0010\u0019\u001a\u00020\u001aH\u0016J\u001a\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00030'*\u00020 2\u0006\u0010\u0017\u001a\u00020\u0018H\u0016J\n\u0010(\u001a\u00020 *\u00020 R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\nX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\u000bR\u0014\u0010\f\u001a\u00020\u0003X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR&\u0010\u000f\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u0011\u0012\u0004\u0012\u00020\u00020\u0010X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R&\u0010\u0014\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u0011\u0012\u0004\u0012\u00020\u00020\u0010X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0013\u00a8\u0006*"}, d2={"Link/ptms/chemdah/core/database/DatabaseSQLite;", "Link/ptms/chemdah/core/database/Relational;", "Link/ptms/chemdah/taboolib/module/database/SQLite;", "", "()V", "host", "Link/ptms/chemdah/taboolib/module/database/HostSQLite;", "getHost", "()Link/ptms/chemdah/taboolib/module/database/HostSQLite;", "isDuplicateKeyUpdateSupported", "", "()Z", "questKey", "getQuestKey", "()Ljava/lang/String;", "tableUserData", "Link/ptms/chemdah/taboolib/module/database/Table;", "Link/ptms/chemdah/taboolib/module/database/Host;", "getTableUserData", "()Link/ptms/chemdah/taboolib/module/database/Table;", "tableVariables", "getTableVariables", "getQuestId", "player", "Lorg/bukkit/entity/Player;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "getUserId", "newQuestTable", "Link/ptms/chemdah/core/database/QuestTable;", "name", "select", "Link/ptms/chemdah/core/PlayerProfile;", "update", "", "playerProfile", "createQuest", "userId", "createUser", "Ljava/util/concurrent/CompletableFuture;", "init", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nDatabaseSQLite.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DatabaseSQLite.kt\nink/ptms/chemdah/core/database/DatabaseSQLite\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 MapsJVM.kt\nkotlin/collections/MapsKt__MapsJVMKt\n+ 5 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,194:1\n1855#2,2:195\n1855#2:197\n766#2:198\n857#2,2:199\n1855#2,2:201\n1856#2:203\n215#3,2:204\n73#4,2:206\n1#5:208\n*S KotlinDebug\n*F\n+ 1 DatabaseSQLite.kt\nink/ptms/chemdah/core/database/DatabaseSQLite\n*L\n83#1:195,2\n88#1:197\n97#1:198\n97#1:199,2\n99#1:201,2\n88#1:203\n108#1:204,2\n128#1:206,2\n128#1:208\n*E\n"})
public final class DatabaseSQLite
extends Relational<SQLite, String, String> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final HostSQLite host = FileToHostKt.getHost((File)new File(IOKt.getDataFolder(), "data.db"));
    @NotNull
    private final Table<Host<SQLite>, SQLite> tableUserData = new Table(this.getTablePrefix() + "_user_data", (Host)this.getHost(), (Function1)tableUserData.1.INSTANCE);
    @NotNull
    private final Table<Host<SQLite>, SQLite> tableVariables = new Table(this.getTablePrefix() + "_variables", (Host)this.getHost(), (Function1)tableVariables.1.INSTANCE);
    @NotNull
    private final String questKey;
    private final boolean isDuplicateKeyUpdateSupported;
    @NotNull
    private static final ConcurrentHashMap<String, Map<String, String>> cacheQuestId = new ConcurrentHashMap();

    public DatabaseSQLite() {
        this.questKey = "id";
    }

    @NotNull
    public HostSQLite getHost() {
        return this.host;
    }

    @Override
    @NotNull
    public Table<Host<SQLite>, SQLite> getTableUserData() {
        return this.tableUserData;
    }

    @Override
    @NotNull
    public Table<Host<SQLite>, SQLite> getTableVariables() {
        return this.tableVariables;
    }

    @Override
    @NotNull
    public String getQuestKey() {
        return this.questKey;
    }

    @Override
    public boolean isDuplicateKeyUpdateSupported() {
        return this.isDuplicateKeyUpdateSupported;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final PlayerProfile init(@NotNull PlayerProfile $this$init) {
        Object element$iv;
        Intrinsics.checkNotNullParameter((Object)$this$init, (String)"<this>");
        Player player2 = $this$init.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"player");
        String userId2 = this.getUserId(player2);
        Iterable $this$forEach$iv = this.getTableUserData().select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(userId2){
            final /* synthetic */ String $userId;
            {
                this.$userId = $userId;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$select) {
                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                String[] stringArray = new String[]{"key", "value"};
                $this$select.rows(stringArray);
                $this$select.where($this$select.and($this$select.eq("user", (Object)this.$userId), $this$select.eq("mode", (Object)1)));
            }
        }).map((Function1)init.2.INSTANCE);
        boolean $i$f$forEach = false;
        for (Object element$iv2 : $this$forEach$iv) {
            Pair pair = (Pair)element$iv2;
            boolean bl = false;
            String k = (String)pair.component1();
            String v = (String)pair.component2();
            $this$init.getPersistentDataContainer().unchanged(arg_0 -> DatabaseSQLite.init$lambda$1$lambda$0(k, v, arg_0));
        }
        HashMap<String, DataContainer> quests = new HashMap<String, DataContainer>();
        Object $this$forEach$iv2 = QuestDataIsolation.INSTANCE.getKeys();
        boolean $i$f$forEach2 = false;
        Iterator<Object> iterator = $this$forEach$iv2.iterator();
        while (iterator.hasNext()) {
            void $this$forEach$iv3;
            void $this$filterTo$iv$iv;
            Iterable $this$filter$iv;
            element$iv = iterator.next();
            String isolation = (String)element$iv;
            boolean bl = false;
            QuestTable table = this.table(!Intrinsics.areEqual((Object)isolation, (Object)"~") ? isolation : null);
            Table qt = table.getQuest();
            Table qd = table.getQuestData();
            Iterable iterable = qt.select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(userId2){
                final /* synthetic */ String $userId;
                {
                    this.$userId = $userId;
                    super(1);
                }

                public final void invoke(@NotNull ActionSelect $this$select) {
                    Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                    String[] stringArray = new String[]{"id", "quest"};
                    $this$select.rows(stringArray);
                    $this$select.where($this$select.and($this$select.eq("user", (Object)this.$userId), $this$select.eq("mode", (Object)1)));
                }
            }).map((Function1)init.4.2.INSTANCE);
            boolean $i$f$filter = false;
            Iterator iterator2 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Pair pair = (Pair)element$iv$iv;
                boolean bl2 = false;
                String quest2 = (String)pair.component2();
                Intrinsics.checkNotNullExpressionValue((Object)quest2, (String)"quest");
                Template template = ChemdahAPI.INSTANCE.getQuestTemplate(quest2);
                if (!Intrinsics.areEqual((Object)(template != null ? template.getDataIsolation() : null), (Object)table.getSuffix())) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$forEach3 = false;
            for (Object element$iv3 : $this$forEach$iv3) {
                Pair pair = (Pair)element$iv3;
                boolean bl3 = false;
                String id2 = (String)pair.component1();
                String quest3 = (String)pair.component2();
                qd.select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(id2){
                    final /* synthetic */ String $id;
                    {
                        this.$id = $id;
                        super(1);
                    }

                    public final void invoke(@NotNull ActionSelect $this$select) {
                        Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                        String[] stringArray = new String[]{"key", "value"};
                        $this$select.rows(stringArray);
                        $this$select.where($this$select.and($this$select.eq("id", (Object)this.$id), $this$select.eq("mode", (Object)1)));
                    }
                }).forEach((Function1)new Function1<ResultSet, Unit>(quests, quest3){
                    final /* synthetic */ HashMap<String, DataContainer> $quests;
                    final /* synthetic */ String $quest;
                    {
                        this.$quests = $quests;
                        this.$quest = $quest;
                        super(1);
                    }

                    /*
                     * WARNING - void declaration
                     */
                    public final void invoke(@NotNull ResultSet $this$forEach) {
                        Object object;
                        void $this$getOrPut$iv;
                        Intrinsics.checkNotNullParameter((Object)$this$forEach, (String)"$this$forEach");
                        Map map = this.$quests;
                        String string = this.$quest;
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"quest");
                        String key$iv = string;
                        boolean $i$f$getOrPut = false;
                        V value$iv = $this$getOrPut$iv.get(key$iv);
                        if (value$iv == null) {
                            boolean bl = false;
                            DataContainer answer$iv = new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY());
                            $this$getOrPut$iv.put(key$iv, answer$iv);
                            object = answer$iv;
                        } else {
                            object = value$iv;
                        }
                        ((DataContainer)object).unchanged(arg_0 -> init.4.4.2.invoke$lambda$1($this$forEach, arg_0));
                    }

                    private static final void invoke$lambda$1(ResultSet $this_forEach, DataContainer it) {
                        Intrinsics.checkNotNullParameter((Object)$this_forEach, (String)"$this_forEach");
                        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                        String string = $this_forEach.getString("key");
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getString(\"key\")");
                        String string2 = $this_forEach.getString("value");
                        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"getString(\"value\")");
                        it.set(string, string2);
                    }
                });
            }
        }
        $this$forEach$iv2 = quests;
        $i$f$forEach2 = false;
        iterator = $this$forEach$iv2.entrySet().iterator();
        while (iterator.hasNext()) {
            Object it = element$iv = (Map.Entry)iterator.next();
            boolean bl = false;
            $this$init.registerQuest(new Quest((String)it.getKey(), $this$init, (DataContainer)it.getValue()), false);
        }
        return $this$init;
    }

    @Override
    @NotNull
    public QuestTable<SQLite> newQuestTable(@Nullable String name) {
        return new QuestTable.SQLite(this.getHost(), this.getTablePrefix(), !Intrinsics.areEqual((Object)name, (Object)"~") ? name : null);
    }

    @Override
    @NotNull
    public String getUserId(@NotNull Player player2) {
        String string;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        switch (WhenMappings.$EnumSwitchMapping$0[UserIndex.Companion.getINSTANCE().ordinal()]) {
            case 1: {
                String string2 = player2.getName();
                string = string2;
                Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"player.name");
                break;
            }
            case 2: {
                String string3 = player2.getUniqueId().toString();
                string = string3;
                Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"player.uniqueId.toString()");
                break;
            }
            default: {
                throw new NoWhenBranchMatchedException();
            }
        }
        return string;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @Nullable
    public String getQuestId(@NotNull Player player2, @NotNull Quest quest2) {
        Map map;
        void $this$getOrPut$iv;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(quest2.getId());
        if (template == null) {
            return null;
        }
        Template template2 = template;
        String key = quest2.getId() + template2.getDataIsolation();
        ConcurrentMap concurrentMap = cacheQuestId;
        String key$iv = player2.getName();
        boolean $i$f$getOrPut = false;
        Object object = $this$getOrPut$iv.get(key$iv);
        if (object == null) {
            boolean bl = false;
            Map default$iv = new ConcurrentHashMap();
            boolean bl2 = false;
            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
            if (object == null) {
                object = default$iv;
            }
        }
        if ((map = (Map)object).containsKey(key)) {
            Object v = map.get(key);
            Intrinsics.checkNotNull(v);
            return (String)v;
        }
        String string = (String)this.table(template2.getDataIsolation()).getQuest().select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(this, player2, quest2){
            final /* synthetic */ DatabaseSQLite this$0;
            final /* synthetic */ Player $player;
            final /* synthetic */ Quest $quest;
            {
                this.this$0 = $receiver;
                this.$player = $player;
                this.$quest = $quest;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$select) {
                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                String[] stringArray = new String[]{"id"};
                $this$select.rows(stringArray);
                $this$select.where($this$select.and($this$select.eq("user", (Object)this.this$0.getUserId(this.$player)), $this$select.eq("quest", (Object)this.$quest.getId())));
                $this$select.limit(1);
            }
        }).firstOrNull((Function1)getQuestId.questId.2.INSTANCE);
        if (string == null) {
            return null;
        }
        String questId2 = string;
        Intrinsics.checkNotNullExpressionValue((Object)map, (String)"map");
        map.put(key, questId2);
        return questId2;
    }

    @Override
    @NotNull
    public CompletableFuture<String> createUser(@NotNull PlayerProfile $this$createUser, @NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)$this$createUser, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @Override
    public void createQuest(@NotNull PlayerProfile $this$createQuest, @NotNull String userId2, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)$this$createQuest, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)userId2, (String)"userId");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        QuestTable questTable = this.table(quest2.getTemplate().getDataIsolation());
        Table tq = questTable.component1();
        Table td = questTable.component2();
        String[] stringArray = new String[]{"id", "user", "quest", "mode"};
        tq.insert(this.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(userId2, quest2, $this$createQuest, td, this){
            final /* synthetic */ String $userId;
            final /* synthetic */ Quest $quest;
            final /* synthetic */ PlayerProfile $this_createQuest;
            final /* synthetic */ Table<Host<SQLite>, SQLite> $td;
            final /* synthetic */ DatabaseSQLite this$0;
            {
                this.$userId = $userId;
                this.$quest = $quest;
                this.$this_createQuest = $receiver;
                this.$td = $td;
                this.this$0 = $receiver2;
                super(1);
            }

            public final void invoke(@NotNull ActionInsert $this$insert) {
                Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                String string = UUID.randomUUID().toString();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"randomUUID().toString()");
                String randomQuestId = string;
                Object[] objectArray = new Object[]{randomQuestId, this.$userId, this.$quest.getId(), 1};
                $this$insert.value(objectArray);
                $this$insert.onFinally((Function2)new Function2<PreparedStatement, Connection, Unit>(this.$this_createQuest, this.$quest, randomQuestId, this.$td, this.this$0){
                    final /* synthetic */ PlayerProfile $this_createQuest;
                    final /* synthetic */ Quest $quest;
                    final /* synthetic */ String $randomQuestId;
                    final /* synthetic */ Table<Host<SQLite>, SQLite> $td;
                    final /* synthetic */ DatabaseSQLite this$0;
                    {
                        this.$this_createQuest = $receiver;
                        this.$quest = $quest;
                        this.$randomQuestId = $randomQuestId;
                        this.$td = $td;
                        this.this$0 = $receiver2;
                        super(2);
                    }

                    /*
                     * WARNING - void declaration
                     */
                    public final void invoke(@NotNull PreparedStatement $this$onFinally, @NotNull Connection it) {
                        void $this$getOrPut$iv;
                        Intrinsics.checkNotNullParameter((Object)$this$onFinally, (String)"$this$onFinally");
                        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                        String[] stringArray = DatabaseSQLite.access$getCacheQuestId$cp();
                        String key$iv = this.$this_createQuest.getPlayer().getName();
                        boolean $i$f$getOrPut = false;
                        Object object = $this$getOrPut$iv.get(key$iv);
                        if (object == null) {
                            boolean bl = false;
                            Map default$iv = new ConcurrentHashMap<K, V>();
                            boolean bl2 = false;
                            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
                            if (object == null) {
                                object = default$iv;
                            }
                        }
                        Intrinsics.checkNotNullExpressionValue(object, (String)"cacheQuestId.getOrPut(pl\u2026) { ConcurrentHashMap() }");
                        Map map = (Map)object;
                        stringArray = this.$quest.getId() + this.$quest.getTemplate().getDataIsolation();
                        String string = this.$randomQuestId;
                        map.put(stringArray, string);
                        ChangeTracker tracker = this.$quest.getPersistentDataContainer().flush();
                        if (!tracker.getModified().isEmpty()) {
                            stringArray = new String[]{"id", "key", "value", "mode"};
                            this.$td.insert(this.this$0.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(tracker, this.$randomQuestId){
                                final /* synthetic */ ChangeTracker $tracker;
                                final /* synthetic */ String $randomQuestId;
                                {
                                    this.$tracker = $tracker;
                                    this.$randomQuestId = $randomQuestId;
                                    super(1);
                                }

                                /*
                                 * WARNING - void declaration
                                 */
                                public final void invoke(@NotNull ActionInsert $this$insert) {
                                    void $this$forEach$iv;
                                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                    Map<String, Data> map = this.$tracker.getModified();
                                    String string = this.$randomQuestId;
                                    boolean $i$f$forEach = false;
                                    Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                                    while (iterator.hasNext()) {
                                        Map.Entry<K, V> element$iv;
                                        Map.Entry<K, V> entry = element$iv = iterator.next();
                                        boolean bl = false;
                                        String k = (String)entry.getKey();
                                        Data v = (Data)entry.getValue();
                                        Object[] objectArray = new Object[]{string, k, v.getData(), 1};
                                        $this$insert.value(objectArray);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    @NotNull
    public PlayerProfile select(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        UUID uUID = player2.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
        PlayerProfile profile = new PlayerProfile(uUID);
        profile.setup();
        return this.init(profile);
    }

    @Override
    public void update(@NotNull Player player2, @NotNull PlayerProfile playerProfile2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
        this.update(playerProfile2, player2);
        this.updateQuest(playerProfile2, player2);
    }

    private static final void init$lambda$1$lambda$0(String $k, String $v, DataContainer it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Intrinsics.checkNotNullExpressionValue((Object)$k, (String)"k");
        Intrinsics.checkNotNullExpressionValue((Object)$v, (String)"v");
        it.set($k, $v);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010%\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0003R&\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00050\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/database/DatabaseSQLite$Companion;", "", "()V", "cacheQuestId", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "onReleased", "", "e", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @SubscribeEvent
        private final void onReleased(PlayerEvents.Released e) {
            cacheQuestId.remove(e.getPlayer().getName());
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=3, xi=48)
    public final class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] nArray = new int[UserIndex.values().length];
            try {
                nArray[UserIndex.NAME.ordinal()] = 1;
            }
            catch (NoSuchFieldError noSuchFieldError) {
                // empty catch block
            }
            try {
                nArray[UserIndex.UUID.ordinal()] = 2;
            }
            catch (NoSuchFieldError noSuchFieldError) {
                // empty catch block
            }
            $EnumSwitchMapping$0 = nArray;
        }
    }
}

