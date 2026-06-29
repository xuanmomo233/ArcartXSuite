/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.database.ActionInsert
 *  ink.ptms.chemdah.taboolib.module.database.ActionSelect
 *  ink.ptms.chemdah.taboolib.module.database.ActionUpdate
 *  ink.ptms.chemdah.taboolib.module.database.FileToHostKt
 *  ink.ptms.chemdah.taboolib.module.database.Host
 *  ink.ptms.chemdah.taboolib.module.database.HostSQL
 *  ink.ptms.chemdah.taboolib.module.database.JoinFilter
 *  ink.ptms.chemdah.taboolib.module.database.SQL
 *  ink.ptms.chemdah.taboolib.module.database.Table
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
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

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.database.DatabaseSQL;
import ink.ptms.chemdah.core.database.QuestTable;
import ink.ptms.chemdah.core.database.Relational;
import ink.ptms.chemdah.core.database.UserIndex;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.database.ActionInsert;
import ink.ptms.chemdah.taboolib.module.database.ActionSelect;
import ink.ptms.chemdah.taboolib.module.database.ActionUpdate;
import ink.ptms.chemdah.taboolib.module.database.FileToHostKt;
import ink.ptms.chemdah.taboolib.module.database.Host;
import ink.ptms.chemdah.taboolib.module.database.HostSQL;
import ink.ptms.chemdah.taboolib.module.database.JoinFilter;
import ink.ptms.chemdah.taboolib.module.database.SQL;
import ink.ptms.chemdah.taboolib.module.database.Table;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 02\u0014\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00030\u0001:\u00010B\u0005\u00a2\u0006\u0002\u0010\u0004J\u001f\u0010\u0019\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dH\u0016\u00a2\u0006\u0002\u0010\u001eJ\u0017\u0010\u001f\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u001a\u001a\u00020\u001bH\u0016\u00a2\u0006\u0002\u0010 J\u0018\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00020\"2\b\u0010#\u001a\u0004\u0018\u00010\rH\u0016J\u0010\u0010$\u001a\u00020%2\u0006\u0010\u001a\u001a\u00020\u001bH\u0016J\u001a\u0010&\u001a\u0014\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00030\u0001H\u0016J\u0018\u0010'\u001a\u00020(2\u0006\u0010\u001a\u001a\u00020\u001b2\u0006\u0010)\u001a\u00020%H\u0016J\u000e\u0010*\u001a\u00020(2\u0006\u0010+\u001a\u00020\u0003J\u001c\u0010,\u001a\u00020(*\u00020%2\u0006\u0010+\u001a\u00020\u00032\u0006\u0010\u001c\u001a\u00020\u001dH\u0016J\u001a\u0010-\u001a\b\u0012\u0004\u0012\u00020\u00030.*\u00020%2\u0006\u0010\u001a\u001a\u00020\u001bH\u0016J\n\u0010/\u001a\u00020%*\u00020%R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\nX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\u000bR\u0014\u0010\f\u001a\u00020\rX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR#\u0010\u0010\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u0012\u0012\u0004\u0012\u00020\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R&\u0010\u0015\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u0012\u0012\u0004\u0012\u00020\u00020\u0011X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0014R&\u0010\u0017\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u0012\u0012\u0004\u0012\u00020\u00020\u0011X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0014\u00a8\u00061"}, d2={"Link/ptms/chemdah/core/database/DatabaseSQL;", "Link/ptms/chemdah/core/database/Relational;", "Link/ptms/chemdah/taboolib/module/database/SQL;", "", "()V", "host", "Link/ptms/chemdah/taboolib/module/database/HostSQL;", "getHost", "()Link/ptms/chemdah/taboolib/module/database/HostSQL;", "isDuplicateKeyUpdateSupported", "", "()Z", "questKey", "", "getQuestKey", "()Ljava/lang/String;", "tableUser", "Link/ptms/chemdah/taboolib/module/database/Table;", "Link/ptms/chemdah/taboolib/module/database/Host;", "getTableUser", "()Link/ptms/chemdah/taboolib/module/database/Table;", "tableUserData", "getTableUserData", "tableVariables", "getTableVariables", "getQuestId", "player", "Lorg/bukkit/entity/Player;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/quest/Quest;)Ljava/lang/Long;", "getUserId", "(Lorg/bukkit/entity/Player;)Ljava/lang/Long;", "newQuestTable", "Link/ptms/chemdah/core/database/QuestTable;", "name", "select", "Link/ptms/chemdah/core/PlayerProfile;", "setup", "update", "", "playerProfile", "updateUserTime", "userId", "createQuest", "createUser", "Ljava/util/concurrent/CompletableFuture;", "init", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nDatabaseSQL.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DatabaseSQL.kt\nink/ptms/chemdah/core/database/DatabaseSQL\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 5 MapsJVM.kt\nkotlin/collections/MapsKt__MapsJVMKt\n+ 6 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,280:1\n1855#2,2:281\n1855#2:283\n766#2:284\n857#2,2:285\n1855#2:287\n1856#2:295\n1856#2:296\n361#3,7:288\n215#4,2:297\n73#5,2:299\n1#6:301\n*S KotlinDebug\n*F\n+ 1 DatabaseSQL.kt\nink/ptms/chemdah/core/database/DatabaseSQL\n*L\n121#1:281,2\n126#1:283\n137#1:284\n137#1:285,2\n140#1:287\n140#1:295\n126#1:296\n141#1:288,7\n144#1:297,2\n176#1:299,2\n176#1:301\n*E\n"})
public final class DatabaseSQL
extends Relational<SQL, Long, Long> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final HostSQL host = FileToHostKt.getHost((ConfigurationSection)((ConfigurationSection)Chemdah.INSTANCE.getConf()), (String)"database.source.SQL");
    @NotNull
    private final Table<Host<SQL>, SQL> tableUser = new Table(this.getTablePrefix() + "_user", (Host)this.getHost(), (Function1)tableUser.1.INSTANCE);
    @NotNull
    private final Table<Host<SQL>, SQL> tableUserData = new Table(this.getTablePrefix() + "_user_data", (Host)this.getHost(), (Function1)tableUserData.1.INSTANCE);
    @NotNull
    private final Table<Host<SQL>, SQL> tableVariables = new Table(this.getTablePrefix() + "_variables", (Host)this.getHost(), (Function1)tableVariables.1.INSTANCE);
    @NotNull
    private final String questKey;
    private final boolean isDuplicateKeyUpdateSupported;
    @NotNull
    private static final ConcurrentHashMap<String, Long> cacheUserId = new ConcurrentHashMap();
    @NotNull
    private static final ConcurrentHashMap<String, Map<String, Long>> cacheQuestId = new ConcurrentHashMap();

    public DatabaseSQL() {
        this.questKey = "quest";
        this.isDuplicateKeyUpdateSupported = true;
    }

    @NotNull
    public HostSQL getHost() {
        return this.host;
    }

    @NotNull
    public final Table<Host<SQL>, SQL> getTableUser() {
        return this.tableUser;
    }

    @Override
    @NotNull
    public Table<Host<SQL>, SQL> getTableUserData() {
        return this.tableUserData;
    }

    @Override
    @NotNull
    public Table<Host<SQL>, SQL> getTableVariables() {
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

    @Override
    @NotNull
    public Relational<SQL, Long, Long> setup() {
        super.setup();
        if (!Database.Companion.isDisableAutoCreateTable()) {
            Table.createTable$default(this.tableUser, (DataSource)this.getDataSource(), (boolean)false, (int)2, null);
        }
        return this;
    }

    public final void updateUserTime(long userId2) {
        this.tableUser.update(this.getDataSource(), (Function1)new Function1<ActionUpdate, Unit>(userId2){
            final /* synthetic */ long $userId;
            {
                this.$userId = $userId;
                super(1);
            }

            public final void invoke(@NotNull ActionUpdate $this$update) {
                Intrinsics.checkNotNullParameter((Object)$this$update, (String)"$this$update");
                $this$update.where($this$update.eq("id", (Object)this.$userId));
                $this$update.set("time", (Object)new Date());
            }
        });
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final PlayerProfile init(@NotNull PlayerProfile $this$init) {
        Object object;
        Intrinsics.checkNotNullParameter((Object)$this$init, (String)"<this>");
        Iterable $this$forEach$iv = this.getTableUserData().select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(this, $this$init){
            final /* synthetic */ DatabaseSQL this$0;
            final /* synthetic */ PlayerProfile $this_init;
            {
                this.this$0 = $receiver;
                this.$this_init = $receiver2;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$select) {
                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                String[] stringArray = new String[]{"key", "value"};
                $this$select.rows(stringArray);
                $this$select.where($this$select.and($this$select.eq("user", (Object)this.this$0.getUserId(this.$this_init.getPlayer())), $this$select.eq("mode", (Object)1)));
            }
        }).map((Function1)init.2.INSTANCE);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            object = (Pair)element$iv;
            boolean bl = false;
            String k = (String)object.component1();
            String v = (String)object.component2();
            $this$init.getPersistentDataContainer().unchanged(arg_0 -> DatabaseSQL.init$lambda$1$lambda$0(k, v, arg_0));
        }
        Long userId2 = this.getUserId($this$init.getPlayer());
        HashMap quests = new HashMap();
        Collection collection = this.getTableQuest().values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"tableQuest.values");
        Object $this$forEach$iv2 = collection;
        boolean $i$f$forEach2 = false;
        object = $this$forEach$iv2.iterator();
        while (object.hasNext()) {
            void $this$forEach$iv3;
            void $this$filterTo$iv$iv;
            Iterable $this$filter$iv;
            Object element$iv = object.next();
            QuestTable table = (QuestTable)element$iv;
            boolean bl = false;
            Table qt = table.getQuest();
            Table qd = table.getQuestData();
            Iterable iterable = qt.select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(qt, qd, userId2){
                final /* synthetic */ Table<Host<SQL>, SQL> $qt;
                final /* synthetic */ Table<Host<SQL>, SQL> $qd;
                final /* synthetic */ Long $userId;
                {
                    this.$qt = $qt;
                    this.$qd = $qd;
                    this.$userId = $userId;
                    super(1);
                }

                public final void invoke(@NotNull ActionSelect $this$select) {
                    Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                    String[] stringArray = new String[]{this.$qt.getName() + ".quest", this.$qd.getName() + ".key", this.$qd.getName() + ".value"};
                    $this$select.rows(stringArray);
                    $this$select.where($this$select.and($this$select.and($this$select.eq("user", (Object)this.$userId), $this$select.eq(this.$qt.getName() + ".mode", (Object)1)), $this$select.eq(this.$qd.getName() + ".mode", (Object)1)));
                    $this$select.innerJoin(this.$qd.getName(), (Function1)new Function1<JoinFilter, Unit>(this.$qt, this.$qd){
                        final /* synthetic */ Table<Host<SQL>, SQL> $qt;
                        final /* synthetic */ Table<Host<SQL>, SQL> $qd;
                        {
                            this.$qt = $qt;
                            this.$qd = $qd;
                            super(1);
                        }

                        public final void invoke(@NotNull JoinFilter $this$innerJoin) {
                            Intrinsics.checkNotNullParameter((Object)$this$innerJoin, (String)"$this$innerJoin");
                            $this$innerJoin.append($this$innerJoin.eq(this.$qt.getName() + ".id", (Object)$this$innerJoin.pre((Object)(this.$qd.getName() + ".quest"))));
                        }
                    });
                }
            }).map((Function1)new Function1<ResultSet, Pair<? extends String, ? extends Pair<? extends String, ? extends String>>>(qt, qd){
                final /* synthetic */ Table<Host<SQL>, SQL> $qt;
                final /* synthetic */ Table<Host<SQL>, SQL> $qd;
                {
                    this.$qt = $qt;
                    this.$qd = $qd;
                    super(1);
                }

                @NotNull
                public final Pair<String, Pair<String, String>> invoke(@NotNull ResultSet $this$map) {
                    Intrinsics.checkNotNullParameter((Object)$this$map, (String)"$this$map");
                    return TuplesKt.to((Object)$this$map.getString(this.$qt.getName() + ".quest"), (Object)TuplesKt.to((Object)$this$map.getString(this.$qd.getName() + ".key"), (Object)$this$map.getString(this.$qd.getName() + ".value")));
                }
            });
            boolean $i$f$filter = false;
            Iterator iterator = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Pair pair = (Pair)element$iv$iv;
                boolean bl2 = false;
                String quest2 = (String)pair.component1();
                Intrinsics.checkNotNullExpressionValue((Object)quest2, (String)"quest");
                Template template = ChemdahAPI.INSTANCE.getQuestTemplate(quest2);
                if (!Intrinsics.areEqual((Object)(template != null ? template.getDataIsolation() : null), (Object)table.getSuffix())) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$forEach3 = false;
            for (Object element$iv2 : $this$forEach$iv3) {
                Object object2;
                void $this$getOrPut$iv;
                Pair pair = (Pair)element$iv2;
                boolean bl3 = false;
                String quest3 = (String)pair.component1();
                Pair data2 = (Pair)pair.component2();
                Map bl2 = quests;
                Intrinsics.checkNotNullExpressionValue((Object)quest3, (String)"quest");
                String key$iv = quest3;
                boolean $i$f$getOrPut = false;
                Object value$iv = $this$getOrPut$iv.get(key$iv);
                if (value$iv == null) {
                    boolean bl4 = false;
                    DataContainer answer$iv = new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY());
                    $this$getOrPut$iv.put(key$iv, answer$iv);
                    object2 = answer$iv;
                } else {
                    object2 = value$iv;
                }
                ((DataContainer)object2).unchanged(arg_0 -> DatabaseSQL.init$lambda$6$lambda$5$lambda$4(data2, arg_0));
            }
        }
        $this$forEach$iv2 = quests;
        $i$f$forEach2 = false;
        object = $this$forEach$iv2.entrySet().iterator();
        while (object.hasNext()) {
            Map.Entry element$iv;
            Map.Entry it = element$iv = (Map.Entry)object.next();
            boolean bl = false;
            $this$init.registerQuest(new Quest((String)it.getKey(), $this$init, (DataContainer)it.getValue()), false);
        }
        return $this$init;
    }

    @Override
    @NotNull
    public QuestTable<SQL> newQuestTable(@Nullable String name) {
        return new QuestTable.SQL(this.getHost(), this.getTablePrefix(), !Intrinsics.areEqual((Object)name, (Object)"~") ? name : null);
    }

    @Override
    @Nullable
    public Long getUserId(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        if (cacheUserId.containsKey(player.getName())) {
            Long l = cacheUserId.get(player.getName());
            Intrinsics.checkNotNull((Object)l);
            return l;
        }
        Long l = (Long)this.tableUser.select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(player){
            final /* synthetic */ Player $player;
            {
                this.$player = $player;
                super(1);
            }

            public final void invoke(@NotNull ActionSelect $this$select) {
                Intrinsics.checkNotNullParameter((Object)$this$select, (String)"$this$select");
                String[] stringArray = new String[]{"id"};
                $this$select.rows(stringArray);
                switch (getUserId.userId.WhenMappings.$EnumSwitchMapping$0[UserIndex.Companion.getINSTANCE().ordinal()]) {
                    case 1: {
                        $this$select.where($this$select.eq("uuid", (Object)this.$player.getName()));
                        break;
                    }
                    case 2: {
                        $this$select.where($this$select.eq("uuid", (Object)this.$player.getUniqueId().toString()));
                    }
                }
                $this$select.limit(1);
            }
        }).firstOrNull((Function1)getUserId.userId.2.INSTANCE);
        if (l == null) {
            return null;
        }
        long userId2 = l;
        Map map = cacheUserId;
        String string = player.getName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
        String string2 = string;
        Long l2 = userId2;
        map.put(string2, l2);
        return userId2;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @Nullable
    public Long getQuestId(@NotNull Player player, @NotNull Quest quest2) {
        Map map;
        void $this$getOrPut$iv;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        String key = quest2.getId() + quest2.getTemplate().getDataIsolation();
        ConcurrentMap concurrentMap = cacheQuestId;
        String key$iv = player.getName();
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
            return (Long)v;
        }
        Long l = (Long)this.table(quest2.getTemplate().getDataIsolation()).getQuest().select(this.getDataSource(), (Function1)new Function1<ActionSelect, Unit>(this, player, quest2){
            final /* synthetic */ DatabaseSQL this$0;
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
        if (l == null) {
            return null;
        }
        long questId2 = l;
        Long l2 = questId2;
        Intrinsics.checkNotNullExpressionValue((Object)map, (String)"map");
        map.put(key, l2);
        return questId2;
    }

    @Override
    @NotNull
    public CompletableFuture<Long> createUser(@NotNull PlayerProfile $this$createUser, @NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)$this$createUser, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        CompletableFuture<Long> future = new CompletableFuture<Long>();
        String[] stringArray = new String[]{"name", "uuid", "time"};
        this.tableUser.insert(this.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(player, $this$createUser, this, future){
            final /* synthetic */ Player $player;
            final /* synthetic */ PlayerProfile $this_createUser;
            final /* synthetic */ DatabaseSQL this$0;
            final /* synthetic */ CompletableFuture<Long> $future;
            {
                this.$player = $player;
                this.$this_createUser = $receiver;
                this.this$0 = $receiver2;
                this.$future = $future;
                super(1);
            }

            public final void invoke(@NotNull ActionInsert $this$insert) {
                Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                Object[] objectArray = new Object[]{this.$player.getName(), this.$player.getUniqueId().toString(), new Date()};
                $this$insert.value(objectArray);
                $this$insert.onFinally((Function2)new Function2<PreparedStatement, Connection, Unit>(this.$player, this.$this_createUser, this.this$0, this.$future){
                    final /* synthetic */ Player $player;
                    final /* synthetic */ PlayerProfile $this_createUser;
                    final /* synthetic */ DatabaseSQL this$0;
                    final /* synthetic */ CompletableFuture<Long> $future;
                    {
                        this.$player = $player;
                        this.$this_createUser = $receiver;
                        this.this$0 = $receiver2;
                        this.$future = $future;
                        super(2);
                    }

                    /*
                     * WARNING - void declaration
                     */
                    public final void invoke(@NotNull PreparedStatement $this$onFinally, @NotNull Connection it) {
                        void $this$forEach$iv;
                        Intrinsics.checkNotNullParameter((Object)$this$onFinally, (String)"$this$onFinally");
                        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                        String[] $this$invoke_u24lambda_u240 = $this$onFinally.getGeneratedKeys();
                        boolean bl = false;
                        $this$invoke_u24lambda_u240.next();
                        long userId2 = Coerce.toLong((Object)$this$invoke_u24lambda_u240.getObject(1));
                        Map map = DatabaseSQL.access$getCacheUserId$cp();
                        String string = this.$player.getName();
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
                        $this$invoke_u24lambda_u240 = string;
                        Object object = userId2;
                        map.put($this$invoke_u24lambda_u240, object);
                        ChangeTracker tracker = this.$this_createUser.getPersistentDataContainer().flush();
                        if (!tracker.getModified().isEmpty()) {
                            $this$invoke_u24lambda_u240 = new String[]{"user", "key", "value", "mode"};
                            this.this$0.getTableUserData().insert(this.this$0.getDataSource(), $this$invoke_u24lambda_u240, (Function1)new Function1<ActionInsert, Unit>(tracker, userId2){
                                final /* synthetic */ ChangeTracker $tracker;
                                final /* synthetic */ long $userId;
                                {
                                    this.$tracker = $tracker;
                                    this.$userId = $userId;
                                    super(1);
                                }

                                /*
                                 * WARNING - void declaration
                                 */
                                public final void invoke(@NotNull ActionInsert $this$insert) {
                                    void $this$forEach$iv;
                                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                    Map<String, Data> map = this.$tracker.getModified();
                                    long l = this.$userId;
                                    boolean $i$f$forEach = false;
                                    Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                                    while (iterator.hasNext()) {
                                        Map.Entry<K, V> element$iv;
                                        Map.Entry<K, V> entry = element$iv = iterator.next();
                                        boolean bl = false;
                                        String key = (String)entry.getKey();
                                        Data value2 = (Data)entry.getValue();
                                        Object[] objectArray = new Object[]{l, key, value2.getData(), 1};
                                        $this$insert.value(objectArray);
                                    }
                                }
                            });
                        }
                        $this$invoke_u24lambda_u240 = PlayerProfile.getQuests$default(this.$this_createUser, false, 1, null);
                        object = this.this$0;
                        PlayerProfile playerProfile = this.$this_createUser;
                        boolean $i$f$forEach = false;
                        for (T element$iv : $this$forEach$iv) {
                            Quest it2 = (Quest)element$iv;
                            boolean bl2 = false;
                            ((DatabaseSQL)object).createQuest(playerProfile, userId2, it2);
                        }
                        this.$future.complete(userId2);
                    }
                });
            }
        });
        return future;
    }

    @Override
    public void createQuest(@NotNull PlayerProfile $this$createQuest, long userId2, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)$this$createQuest, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        QuestTable questTable = this.table(quest2.getTemplate().getDataIsolation());
        Table tq = questTable.component1();
        Table td = questTable.component2();
        String[] stringArray = new String[]{"user", "quest", "mode"};
        tq.insert(this.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(userId2, quest2, $this$createQuest, td, this){
            final /* synthetic */ long $userId;
            final /* synthetic */ Quest $quest;
            final /* synthetic */ PlayerProfile $this_createQuest;
            final /* synthetic */ Table<Host<SQL>, SQL> $td;
            final /* synthetic */ DatabaseSQL this$0;
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
                Object[] objectArray = new Object[]{this.$userId, this.$quest.getId(), 1};
                $this$insert.value(objectArray);
                $this$insert.onFinally((Function2)new Function2<PreparedStatement, Connection, Unit>(this.$this_createQuest, this.$quest, this.$td, this.this$0){
                    final /* synthetic */ PlayerProfile $this_createQuest;
                    final /* synthetic */ Quest $quest;
                    final /* synthetic */ Table<Host<SQL>, SQL> $td;
                    final /* synthetic */ DatabaseSQL this$0;
                    {
                        this.$this_createQuest = $receiver;
                        this.$quest = $quest;
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
                        Object $this$invoke_u24lambda_u240 = $this$onFinally.getGeneratedKeys();
                        boolean bl = false;
                        $this$invoke_u24lambda_u240.next();
                        long questId2 = Coerce.toLong((Object)$this$invoke_u24lambda_u240.getObject(1));
                        $this$invoke_u24lambda_u240 = DatabaseSQL.access$getCacheQuestId$cp();
                        String key$iv = this.$this_createQuest.getPlayer().getName();
                        boolean $i$f$getOrPut = false;
                        Object object = $this$getOrPut$iv.get(key$iv);
                        if (object == null) {
                            boolean bl2 = false;
                            Map default$iv = new ConcurrentHashMap<K, V>();
                            boolean bl3 = false;
                            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
                            if (object == null) {
                                object = default$iv;
                            }
                        }
                        Intrinsics.checkNotNullExpressionValue(object, (String)"cacheQuestId.getOrPut(pl\u2026) { ConcurrentHashMap() }");
                        Map map = (Map)object;
                        String[] stringArray = this.$quest.getId() + this.$quest.getTemplate().getDataIsolation();
                        Long l = questId2;
                        map.put(stringArray, l);
                        ChangeTracker tracker = this.$quest.getPersistentDataContainer().flush();
                        if (!tracker.getModified().isEmpty()) {
                            stringArray = new String[]{"quest", "key", "value", "mode"};
                            this.$td.insert(this.this$0.getDataSource(), stringArray, (Function1)new Function1<ActionInsert, Unit>(tracker, questId2){
                                final /* synthetic */ ChangeTracker $tracker;
                                final /* synthetic */ long $questId;
                                {
                                    this.$tracker = $tracker;
                                    this.$questId = $questId;
                                    super(1);
                                }

                                /*
                                 * WARNING - void declaration
                                 */
                                public final void invoke(@NotNull ActionInsert $this$insert) {
                                    void $this$forEach$iv;
                                    Intrinsics.checkNotNullParameter((Object)$this$insert, (String)"$this$insert");
                                    Map<String, Data> map = this.$tracker.getModified();
                                    long l = this.$questId;
                                    boolean $i$f$forEach = false;
                                    Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                                    while (iterator.hasNext()) {
                                        Map.Entry<K, V> element$iv;
                                        Map.Entry<K, V> entry = element$iv = iterator.next();
                                        boolean bl = false;
                                        String key = (String)entry.getKey();
                                        Data value2 = (Data)entry.getValue();
                                        Object[] objectArray = new Object[]{l, key, value2.getData(), 1};
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
    public PlayerProfile select(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        UUID uUID = player.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
        PlayerProfile playerProfile = new PlayerProfile(uUID);
        playerProfile.setup();
        Long user = this.getUserId(player);
        if (user == null) {
            return playerProfile;
        }
        ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, user){
            final /* synthetic */ DatabaseSQL this$0;
            final /* synthetic */ Long $user;
            {
                this.this$0 = $receiver;
                this.$user = $user;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submitAsync) {
                Intrinsics.checkNotNullParameter((Object)$this$submitAsync, (String)"$this$submitAsync");
                this.this$0.updateUserTime(this.$user);
            }
        }), (int)7, null);
        return this.init(playerProfile);
    }

    @Override
    public void update(@NotNull Player player, @NotNull PlayerProfile playerProfile) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
        Long userId2 = this.getUserId(player);
        if (userId2 == null) {
            this.createUser(playerProfile, player);
        } else {
            this.update(playerProfile, player);
            this.updateQuest(playerProfile, player);
        }
    }

    private static final void init$lambda$1$lambda$0(String $k, String $v, DataContainer it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Intrinsics.checkNotNullExpressionValue((Object)$k, (String)"k");
        Intrinsics.checkNotNullExpressionValue((Object)$v, (String)"v");
        it.set($k, $v);
    }

    private static final void init$lambda$6$lambda$5$lambda$4(Pair $data, DataContainer it) {
        Intrinsics.checkNotNullParameter((Object)$data, (String)"$data");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object object = $data.getFirst();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"data.first");
        String string = (String)object;
        Object object2 = $data.getSecond();
        Intrinsics.checkNotNullExpressionValue((Object)object2, (String)"data.second");
        it.set(string, object2);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010%\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0003R&\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00070\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00070\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/database/DatabaseSQL$Companion;", "", "()V", "cacheQuestId", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "", "cacheUserId", "onReleased", "", "e", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @SubscribeEvent
        private final void onReleased(PlayerEvents.Released e) {
            cacheUserId.remove(e.getPlayer().getName());
            cacheQuestId.remove(e.getPlayer().getName());
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

