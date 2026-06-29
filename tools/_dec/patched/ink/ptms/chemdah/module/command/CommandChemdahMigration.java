/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.module.database.Table
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jdk7.AutoCloseableKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  kotlin1822.text.StringsKt
 *  org.bukkit.command.CommandSender
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.core.database.DatabaseSQL;
import ink.ptms.chemdah.core.database.DatabaseSQLite;
import ink.ptms.chemdah.module.command.CommandChemdahMigration;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.module.database.Table;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jdk7.AutoCloseableKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import kotlin1822.text.StringsKt;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandHeader(name="chemdah-migration", aliases={"chmig"}, permission="chemdah.command.migration")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\"\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c7\u0002\u0018\u00002\u00020\u0001:\u0003\u0017\u0018\u0019B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\r\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000f0\u000e2\u0006\u0010\u0010\u001a\u00020\u0011H\u0002J\u0018\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0004H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\u00020\u00068\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0016\u0010\t\u001a\u00020\n8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahMigration;", "", "()V", "DEFAULT_BATCH_SIZE", "", "exportSQL", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getExportSQL", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "discoverQuestSuffixes", "", "", "sqlite", "Link/ptms/chemdah/core/database/DatabaseSQLite;", "runMigration", "", "sender", "Lorg/bukkit/command/CommandSender;", "batchSize", "MigrationState", "MigrationTask", "ValidationResult", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCommandChemdahMigration.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CommandChemdahMigration.kt\nink/ptms/chemdah/module/command/CommandChemdahMigration\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,447:1\n1549#2:448\n1620#2,3:449\n*S KotlinDebug\n*F\n+ 1 CommandChemdahMigration.kt\nink/ptms/chemdah/module/command/CommandChemdahMigration\n*L\n443#1:448\n443#1:449,3\n*E\n"})
public final class CommandChemdahMigration {
    @NotNull
    public static final CommandChemdahMigration INSTANCE = new CommandChemdahMigration();
    private static final int DEFAULT_BATCH_SIZE = 500;
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody(aliases={"export", "dump"})
    @NotNull
    private static final SimpleCommandBody exportSQL = SimpleCommandKt.subCommand((Function1)exportSQL.1.INSTANCE);

    private CommandChemdahMigration() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getExportSQL() {
        return exportSQL;
    }

    private final void runMigration(CommandSender sender, int batchSize) {
        sender.sendMessage("\u00a7c[Chemdah] \u00a77\u51c6\u5907\u8fc1\u79fb SQLite -> SQL\uff0c\u6279\u6b21\u5927\u5c0f \u00a7f" + batchSize + "\u00a77\uff0c\u5efa\u8bae\u5728\u505c\u670d\u6216\u65e0\u73a9\u5bb6\u72b6\u6001\u4e0b\u6267\u884c\u3002");
        ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(sender, batchSize){
            final /* synthetic */ CommandSender $sender;
            final /* synthetic */ int $batchSize;
            {
                this.$sender = $sender;
                this.$batchSize = $batchSize;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submitAsync) {
                Intrinsics.checkNotNullParameter((Object)$this$submitAsync, (String)"$this$submitAsync");
                try {
                    new MigrationTask(this.$sender, this.$batchSize).run();
                    this.$sender.sendMessage("\u00a7a[Chemdah] \u00a77\u8fc1\u79fb\u5b8c\u6210\uff0c\u5168\u90e8\u6570\u636e\u5df2\u63d0\u4ea4\u3002");
                }
                catch (Throwable ex) {
                    StringBuilder stringBuilder = new StringBuilder().append("\u00a7c[Chemdah] \u00a77\u8fc1\u79fb\u5931\u8d25\uff1a");
                    String string = ex.getMessage();
                    if (string == null) {
                        string = ex.getClass().getSimpleName();
                    }
                    this.$sender.sendMessage(stringBuilder.append(string).toString());
                    ex.printStackTrace();
                }
            }
        }), (int)7, null);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * WARNING - void declaration
     */
    private final Set<String> discoverQuestSuffixes(DatabaseSQLite sqlite) {
        void $this$mapTo$iv$iv;
        Object object;
        String prefix = sqlite.getTablePrefix();
        String questPrefix = prefix + "_quest";
        String questDataPrefix = prefix + "_quest_data";
        Set suffixes = new LinkedHashSet();
        ((Collection)suffixes).add(null);
        Object object2 = sqlite.getDataSource().getConnection();
        Object object3 = null;
        try {
            Connection conn = (Connection)object2;
            boolean bl = false;
            AutoCloseable autoCloseable = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table'");
            Throwable throwable = null;
            try {
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl2 = false;
                object = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)object;
                    boolean bl3 = false;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        if (StringsKt.equals((String)name, (String)questPrefix, (boolean)true) || StringsKt.equals((String)name, (String)questDataPrefix, (boolean)true)) {
                            ((Collection)suffixes).add(null);
                            continue;
                        }
                        Intrinsics.checkNotNullExpressionValue((Object)name, (String)"name");
                        if (StringsKt.startsWith((String)name, (String)(questPrefix + '_'), (boolean)true)) {
                            ((Collection)suffixes).add(StringsKt.removePrefix((String)name, (CharSequence)(questPrefix + '_')));
                            continue;
                        }
                        if (!StringsKt.startsWith((String)name, (String)(questDataPrefix + '_'), (boolean)true)) continue;
                        ((Collection)suffixes).add(StringsKt.removePrefix((String)name, (CharSequence)(questDataPrefix + '_')));
                    }
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)object, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            conn = Unit.INSTANCE;
        }
        catch (Throwable conn) {
            object3 = conn;
            throw conn;
        }
        finally {
            AutoCloseableKt.closeFinally((AutoCloseable)object2, (Throwable)object3);
        }
        object2 = suffixes;
        Set set2 = sqlite.getTableQuest().keySet();
        Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"sqlite.tableQuest.keys");
        Iterable $this$map$iv = set2;
        boolean $i$f$map = false;
        Iterable bl = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            object = (String)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl4 = false;
            collection.add(Intrinsics.areEqual((Object)it, (Object)"~") ? null : it);
        }
        object3 = (List)destination$iv$iv;
        CollectionsKt.addAll((Collection)object2, (Iterable)object3);
        return suffixes;
    }

    public static final /* synthetic */ void access$runMigration(CommandChemdahMigration $this, CommandSender sender, int batchSize) {
        $this.runMigration(sender, batchSize);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0006\u0010\u0016\u001a\u00020\u0017J\u000e\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0019\u001a\u00020\u0014J\u0006\u0010\u001a\u001a\u00020\u001bR\u0016\u0010\t\u001a\n \u000b*\u0004\u0018\u00010\n0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u001a\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\u0014\u0012\u0004\u0012\u00020\u00150\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahMigration$MigrationState;", "", "sqlite", "Link/ptms/chemdah/core/database/DatabaseSQLite;", "target", "Link/ptms/chemdah/core/database/DatabaseSQL;", "targetConn", "Ljava/sql/Connection;", "(Link/ptms/chemdah/core/database/DatabaseSQLite;Link/ptms/chemdah/core/database/DatabaseSQL;Ljava/sql/Connection;)V", "insertUser", "Ljava/sql/PreparedStatement;", "kotlin1822.jvm.PlatformType", "getSqlite", "()Link/ptms/chemdah/core/database/DatabaseSQLite;", "getTarget", "()Link/ptms/chemdah/core/database/DatabaseSQL;", "getTargetConn", "()Ljava/sql/Connection;", "userIds", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "close", "", "getOrCreateUserId", "rawUser", "userSize", "", "Chemdah"})
    private static final class MigrationState {
        @NotNull
        private final DatabaseSQLite sqlite;
        @NotNull
        private final DatabaseSQL target;
        @NotNull
        private final Connection targetConn;
        @NotNull
        private final ConcurrentHashMap<String, Long> userIds;
        private final PreparedStatement insertUser;

        public MigrationState(@NotNull DatabaseSQLite sqlite, @NotNull DatabaseSQL target, @NotNull Connection targetConn) {
            Intrinsics.checkNotNullParameter((Object)sqlite, (String)"sqlite");
            Intrinsics.checkNotNullParameter((Object)target, (String)"target");
            Intrinsics.checkNotNullParameter((Object)targetConn, (String)"targetConn");
            this.sqlite = sqlite;
            this.target = target;
            this.targetConn = targetConn;
            this.userIds = new ConcurrentHashMap();
            this.insertUser = this.targetConn.prepareStatement("INSERT INTO `" + this.target.getTableUser().getName() + "` (`name`,`uuid`,`time`) VALUES (?,?,?)", 1);
        }

        @NotNull
        public final DatabaseSQLite getSqlite() {
            return this.sqlite;
        }

        @NotNull
        public final DatabaseSQL getTarget() {
            return this.target;
        }

        @NotNull
        public final Connection getTargetConn() {
            return this.targetConn;
        }

        public final long getOrCreateUserId(@NotNull String rawUser) {
            Intrinsics.checkNotNullParameter((Object)rawUser, (String)"rawUser");
            Long l = this.userIds.computeIfAbsent(rawUser, arg_0 -> MigrationState.getOrCreateUserId$lambda$0((Function1)new Function1<String, Long>(this, rawUser){
                final /* synthetic */ MigrationState this$0;
                final /* synthetic */ String $rawUser;
                {
                    this.this$0 = $receiver;
                    this.$rawUser = $rawUser;
                    super(1);
                }

                /*
                 * WARNING - Removed try catching itself - possible behaviour change.
                 */
                @NotNull
                public final Long invoke(@NotNull String it) {
                    Long l;
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    MigrationState.access$getInsertUser$p(this.this$0).setString(1, this.$rawUser);
                    MigrationState.access$getInsertUser$p(this.this$0).setString(2, this.$rawUser);
                    MigrationState.access$getInsertUser$p(this.this$0).setTimestamp(3, Timestamp.from(Instant.now()));
                    MigrationState.access$getInsertUser$p(this.this$0).executeUpdate();
                    AutoCloseable autoCloseable = MigrationState.access$getInsertUser$p(this.this$0).getGeneratedKeys();
                    String string = this.$rawUser;
                    Throwable throwable = null;
                    try {
                        ResultSet rs = (ResultSet)autoCloseable;
                        boolean bl = false;
                        if (!rs.next()) {
                            throw new IllegalStateException(("\u65e0\u6cd5\u83b7\u53d6\u7528\u6237\u4e3b\u952e\uff08user=" + string + '\uff09').toString());
                        }
                        l = rs.getLong(1);
                    }
                    catch (Throwable throwable2) {
                        throwable = throwable2;
                        throw throwable2;
                    }
                    finally {
                        AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
                    }
                    return l;
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)l, (String)"fun getOrCreateUserId(ra\u2026}\n            }\n        }");
            return ((Number)l).longValue();
        }

        public final int userSize() {
            return this.userIds.size();
        }

        public final void close() {
            this.insertUser.close();
        }

        private static final Long getOrCreateUserId$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Long)$tmp0.invoke(p0);
        }

        public static final /* synthetic */ PreparedStatement access$getInsertUser$p(MigrationState $this) {
            return $this.insertUser;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0082\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\"\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0000\n\u0002\u0010%\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\b\b\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\b\u0002\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0002J\u0018\u0010\u0014\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0002J \u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00112\u000e\u0010\u0018\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00130\u0019H\u0002J2\u0010\u001a\u001a\u00020\u00162\u0006\u0010\u001b\u001a\u00020\u001c2\f\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00130\u001e2\u0012\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0013\u0012\u0004\u0012\u00020\u000f0 H\u0002J6\u0010!\u001a\u00020\u00162\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010#\u001a\u00020$2\b\u0010%\u001a\u0004\u0018\u00010\u00132\u0012\u0010\u001f\u001a\u000e\u0012\u0004\u0012\u00020\u0013\u0012\u0004\u0012\u00020\u000f0&H\u0002J.\u0010'\u001a\u000e\u0012\u0004\u0012\u00020\u0013\u0012\u0004\u0012\u00020\u000f0&2\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010#\u001a\u00020$2\b\u0010%\u001a\u0004\u0018\u00010\u0013H\u0002J\u0018\u0010(\u001a\u00020\u00162\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010#\u001a\u00020$H\u0002J\u0018\u0010)\u001a\u00020\u00162\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010#\u001a\u00020$H\u0002J\u0018\u0010*\u001a\u00020\u00162\u0006\u0010+\u001a\u00020\u00052\u0006\u0010,\u001a\u00020\u0013H\u0002J\u0016\u0010-\u001a\u00020\u00162\f\u0010.\u001a\b\u0012\u0004\u0012\u0002000/H\u0002J\u0006\u00101\u001a\u00020\u0016J\u0012\u00102\u001a\u00020\u00132\b\u0010%\u001a\u0004\u0018\u00010\u0013H\u0002J6\u00103\u001a\b\u0012\u0004\u0012\u0002000/2\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010\u0017\u001a\u00020\u00112\u0006\u0010#\u001a\u00020$2\u000e\u0010\u0018\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00130\u0019H\u0002J0\u00104\u001a\u0002002\u0006\u0010\"\u001a\u00020\u00112\u0006\u0010\u0017\u001a\u00020\u00112\u0006\u00105\u001a\u00020\u00132\u0006\u0010\f\u001a\u00020\u00132\u0006\u00106\u001a\u000207H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u00068"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahMigration$MigrationTask;", "", "sender", "Lorg/bukkit/command/CommandSender;", "batchSize", "", "(Lorg/bukkit/command/CommandSender;I)V", "progressInterval", "sqlite", "Link/ptms/chemdah/core/database/DatabaseSQLite;", "sqliteFile", "Ljava/io/File;", "target", "Link/ptms/chemdah/core/database/DatabaseSQL;", "countActiveRows", "", "conn", "Ljava/sql/Connection;", "table", "", "countRows", "ensureTargetEmpty", "", "targetConn", "questSuffixes", "", "flushQuestBatch", "insert", "Ljava/sql/PreparedStatement;", "pending", "", "questIdMap", "", "migrateQuestDataTable", "sourceConn", "state", "Link/ptms/chemdah/module/command/CommandChemdahMigration$MigrationState;", "suffix", "", "migrateQuestTable", "migrateUserData", "migrateVariables", "reportProgress", "current", "label", "reportValidation", "results", "", "Link/ptms/chemdah/module/command/CommandChemdahMigration$ValidationResult;", "run", "suffixLabel", "validateCounts", "validateTable", "source", "hasMode", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nCommandChemdahMigration.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CommandChemdahMigration.kt\nink/ptms/chemdah/module/command/CommandChemdahMigration$MigrationTask\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,447:1\n1855#2,2:448\n1855#2,2:450\n1855#2,2:452\n1855#2,2:454\n1855#2,2:456\n1855#2,2:458\n1855#2,2:460\n*S KotlinDebug\n*F\n+ 1 CommandChemdahMigration.kt\nink/ptms/chemdah/module/command/CommandChemdahMigration$MigrationTask\n*L\n88#1:448,2\n219#1:450,2\n283#1:452,2\n287#1:454,2\n304#1:456,2\n316#1:458,2\n340#1:460,2\n*E\n"})
    private static final class MigrationTask {
        @NotNull
        private final CommandSender sender;
        private final int batchSize;
        @NotNull
        private final File sqliteFile;
        @NotNull
        private final DatabaseSQLite sqlite;
        @NotNull
        private final DatabaseSQL target;
        private final int progressInterval;

        public MigrationTask(@NotNull CommandSender sender, int batchSize) {
            Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
            this.sender = sender;
            this.batchSize = batchSize;
            this.sqliteFile = new File(IOKt.getDataFolder(), "data.db");
            this.sqlite = new DatabaseSQLite();
            this.target = new DatabaseSQL();
            this.progressInterval = RangesKt.coerceAtLeast((int)(this.batchSize * 20), (int)10000);
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public final void run() {
            if (!this.sqliteFile.exists()) {
                throw new IllegalStateException(("\u672a\u627e\u5230 SQLite \u6570\u636e\u6587\u4ef6\uff1a" + this.sqliteFile.getAbsolutePath()).toString());
            }
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u68c0\u6d4b\u5230 SQLite \u6587\u4ef6\uff0c\u5f00\u59cb\u521d\u59cb\u5316\u6570\u636e\u6e90...");
            this.sqlite.setup();
            this.target.setup();
            Set questSuffixes = INSTANCE.discoverQuestSuffixes(this.sqlite);
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u53d1\u73b0 " + questSuffixes.size() + " \u4e2a\u4efb\u52a1\u5206\u8868\uff1a" + CollectionsKt.joinToString$default((Iterable)questSuffixes, null, null, null, (int)0, null, (Function1)run.1.INSTANCE, (int)31, null));
            AutoCloseable autoCloseable = this.sqlite.getDataSource().getConnection();
            Throwable throwable = null;
            try {
                Connection sourceConn = (Connection)autoCloseable;
                boolean bl = false;
                AutoCloseable autoCloseable2 = this.target.getDataSource().getConnection();
                Throwable throwable2 = null;
                try {
                    Connection targetConn = (Connection)autoCloseable2;
                    boolean bl2 = false;
                    targetConn.setAutoCommit(false);
                    Intrinsics.checkNotNullExpressionValue((Object)targetConn, (String)"targetConn");
                    MigrationState state = new MigrationState(this.sqlite, this.target, targetConn);
                    try {
                        this.ensureTargetEmpty(targetConn, questSuffixes);
                        Intrinsics.checkNotNullExpressionValue((Object)sourceConn, (String)"sourceConn");
                        this.migrateVariables(sourceConn, state);
                        this.migrateUserData(sourceConn, state);
                        Iterable $this$forEach$iv = questSuffixes;
                        boolean $i$f$forEach = false;
                        for (Object element$iv : $this$forEach$iv) {
                            String suffix = (String)element$iv;
                            boolean bl3 = false;
                            Map<String, Long> questIdMap = this.migrateQuestTable(sourceConn, state, suffix);
                            this.migrateQuestDataTable(sourceConn, state, suffix, questIdMap);
                        }
                        List<ValidationResult> validations = this.validateCounts(sourceConn, targetConn, state, questSuffixes);
                        targetConn.commit();
                        state.close();
                        this.reportValidation(validations);
                    }
                    catch (Exception e) {
                        targetConn.rollback();
                        state.close();
                        throw e;
                    }
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final void migrateVariables(Connection sourceConn, MigrationState state) {
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u8fc1\u79fb\u5168\u5c40\u53d8\u91cf...");
            PreparedStatement insert = state.getTargetConn().prepareStatement("INSERT INTO `" + state.getTarget().getTableVariables().getName() + "` (`name`,`data`,`mode`) VALUES (?,?,?)", 2);
            int processed = 0;
            AutoCloseable autoCloseable = sourceConn.prepareStatement("SELECT `name`,`data`,`mode` FROM `" + state.getSqlite().getTableVariables().getName() + '`');
            Throwable throwable = null;
            try {
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                stmt.setFetchSize(this.batchSize);
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    while (rs.next()) {
                        insert.setString(1, rs.getString("name"));
                        insert.setString(2, rs.getString("data"));
                        insert.setInt(3, rs.getInt("mode"));
                        insert.addBatch();
                        this.reportProgress(++processed, "\u5168\u5c40\u53d8\u91cf");
                        if (processed % this.batchSize != 0) continue;
                        insert.executeBatch();
                    }
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            insert.executeBatch();
            insert.close();
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u5168\u5c40\u53d8\u91cf\u8fc1\u79fb\u5b8c\u6210\uff0c\u5408\u8ba1 " + processed + " \u6761\u3002");
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final void migrateUserData(Connection sourceConn, MigrationState state) {
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u8fc1\u79fb\u7528\u6237\u6570\u636e\uff08\u91c7\u7528\u5206\u6279\u5199\u5165\uff09...");
            PreparedStatement insert = state.getTargetConn().prepareStatement("INSERT INTO `" + state.getTarget().getTableUserData().getName() + "` (`user`,`key`,`value`,`mode`) VALUES (?,?,?,?)", 2);
            int processed = 0;
            AutoCloseable autoCloseable = sourceConn.prepareStatement("SELECT `user`,`key`,`value`,`mode` FROM `" + state.getSqlite().getTableUserData().getName() + '`');
            Throwable throwable = null;
            try {
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                stmt.setFetchSize(this.batchSize);
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    while (rs.next()) {
                        String string = rs.getString("user");
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"rs.getString(\"user\")");
                        long userId2 = state.getOrCreateUserId(string);
                        insert.setLong(1, userId2);
                        insert.setString(2, rs.getString("key"));
                        insert.setString(3, rs.getString("value"));
                        insert.setInt(4, rs.getInt("mode"));
                        insert.addBatch();
                        this.reportProgress(++processed, "\u7528\u6237\u6570\u636e");
                        if (processed % this.batchSize != 0) continue;
                        insert.executeBatch();
                    }
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            insert.executeBatch();
            insert.close();
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u7528\u6237\u6570\u636e\u8fc1\u79fb\u5b8c\u6210\uff0c\u5408\u8ba1 " + processed + " \u6761\u3002");
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final Map<String, Long> migrateQuestTable(Connection sourceConn, MigrationState state, String suffix) {
            Table questTable = state.getSqlite().table(suffix).getQuest();
            Table targetQuestTable = state.getTarget().table(suffix).getQuest();
            String suffixLabel = this.suffixLabel(suffix);
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u8fc1\u79fb\u4efb\u52a1\u8868 (\u00a7f" + suffixLabel + "\u00a77)...");
            HashMap questIdMap = new HashMap();
            AutoCloseable autoCloseable = sourceConn.prepareStatement("SELECT `id`,`user`,`quest`,`mode` FROM `" + questTable.getName() + '`');
            Throwable throwable = null;
            try {
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                stmt.setFetchSize(this.batchSize);
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    PreparedStatement insert = state.getTargetConn().prepareStatement("INSERT INTO `" + targetQuestTable.getName() + "` (`user`,`quest`,`mode`) VALUES (?,?,?)", 1);
                    ArrayList pending = new ArrayList(this.batchSize);
                    int processed = 0;
                    while (rs.next()) {
                        String oldQuestId = rs.getString("id");
                        String string = rs.getString("user");
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"rs.getString(\"user\")");
                        long userId2 = state.getOrCreateUserId(string);
                        insert.setLong(1, userId2);
                        insert.setString(2, rs.getString("quest"));
                        insert.setInt(3, rs.getInt("mode"));
                        insert.addBatch();
                        ((Collection)pending).add(oldQuestId);
                        this.reportProgress(++processed, "\u4efb\u52a1\u8868(" + suffixLabel + ')');
                        if (pending.size() < this.batchSize) continue;
                        Intrinsics.checkNotNullExpressionValue((Object)insert, (String)"insert");
                        this.flushQuestBatch(insert, pending, questIdMap);
                    }
                    if (!((Collection)pending).isEmpty()) {
                        Intrinsics.checkNotNullExpressionValue((Object)insert, (String)"insert");
                        this.flushQuestBatch(insert, pending, questIdMap);
                    }
                    insert.close();
                    this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u4efb\u52a1\u8868 (\u00a7f" + suffixLabel + "\u00a77) \u8fc1\u79fb\u5b8c\u6210\uff0c\u5408\u8ba1 " + processed + " \u6761\u3002");
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            return questIdMap;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final void flushQuestBatch(PreparedStatement insert, List<String> pending, Map<String, Long> questIdMap) {
            insert.executeBatch();
            AutoCloseable autoCloseable = insert.getGeneratedKeys();
            Throwable throwable = null;
            try {
                ResultSet keys = (ResultSet)autoCloseable;
                boolean bl = false;
                Iterable $this$forEach$iv = pending;
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    String legacy = (String)element$iv;
                    boolean bl2 = false;
                    if (!keys.next()) {
                        throw new IllegalStateException(("\u65e0\u6cd5\u8bfb\u53d6\u4efb\u52a1\u4e3b\u952e\u6620\u5c04\uff08legacy=" + legacy + '\uff09').toString());
                    }
                    questIdMap.put(legacy, keys.getLong(1));
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable2) {
                throwable = throwable2;
                throw throwable2;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            pending.clear();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final void migrateQuestDataTable(Connection sourceConn, MigrationState state, String suffix, Map<String, Long> questIdMap) {
            Table questDataTable = state.getSqlite().table(suffix).getQuestData();
            Table targetQuestDataTable = state.getTarget().table(suffix).getQuestData();
            String suffixLabel = this.suffixLabel(suffix);
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u8fc1\u79fb\u4efb\u52a1\u6570\u636e\u8868 (\u00a7f" + suffixLabel + "\u00a77)...");
            PreparedStatement insert = state.getTargetConn().prepareStatement("INSERT INTO `" + targetQuestDataTable.getName() + "` (`quest`,`key`,`value`,`mode`) VALUES (?,?,?,?)", 2);
            int processed = 0;
            int skipped = 0;
            AutoCloseable autoCloseable = sourceConn.prepareStatement("SELECT `" + state.getSqlite().getQuestKey() + "` as `quest`,`key`,`value`,`mode` FROM `" + questDataTable.getName() + '`');
            Throwable throwable = null;
            try {
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                stmt.setFetchSize(this.batchSize);
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    while (rs.next()) {
                        Long newQuestId = questIdMap.get(rs.getString("quest"));
                        if (newQuestId == null) {
                            ++skipped;
                            continue;
                        }
                        insert.setLong(1, newQuestId);
                        insert.setString(2, rs.getString("key"));
                        insert.setString(3, rs.getString("value"));
                        insert.setInt(4, rs.getInt("mode"));
                        insert.addBatch();
                        this.reportProgress(++processed, "\u4efb\u52a1\u6570\u636e\u8868(" + suffixLabel + ')');
                        if (processed % this.batchSize != 0) continue;
                        insert.executeBatch();
                    }
                    Unit unit = Unit.INSTANCE;
                }
                catch (Throwable throwable3) {
                    throwable2 = throwable3;
                    throw throwable3;
                }
                finally {
                    AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                }
                Unit unit = Unit.INSTANCE;
            }
            catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
            insert.executeBatch();
            insert.close();
            if (skipped > 0) {
                this.sender.sendMessage("\u00a7e[Chemdah] \u00a77\u4efb\u52a1\u6570\u636e\u8868 (\u00a7f" + suffixLabel + "\u00a77) \u8df3\u8fc7 \u00a7f" + skipped + " \u00a77\u6761\uff08\u627e\u4e0d\u5230\u5bf9\u5e94\u4efb\u52a1\uff09\u3002");
            }
            this.sender.sendMessage("\u00a7c[Chemdah] \u00a77\u4efb\u52a1\u6570\u636e\u8868 (\u00a7f" + suffixLabel + "\u00a77) \u8fc1\u79fb\u5b8c\u6210\uff0c\u5408\u8ba1 " + processed + " \u6761\u3002");
        }

        private final void ensureTargetEmpty(Connection targetConn, Set<String> questSuffixes) {
            String it;
            Object[] objectArray = new String[]{this.target.getTableUser().getName(), this.target.getTableUserData().getName(), this.target.getTableVariables().getName()};
            List tables = CollectionsKt.mutableListOf((Object[])objectArray);
            Iterable $this$forEach$iv = questSuffixes;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                it = (String)element$iv;
                boolean bl = false;
                ((Collection)tables).add(this.target.table(it).getQuest().getName());
                ((Collection)tables).add(this.target.table(it).getQuestData().getName());
            }
            $this$forEach$iv = tables;
            $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                it = (String)element$iv;
                boolean bl = false;
                long count2 = this.countRows(targetConn, it);
                if (count2 <= 0L) continue;
                throw new IllegalStateException(("\u76ee\u6807\u8868 " + it + " \u975e\u7a7a\uff08\u5f53\u524d\u884c\u6570 " + count2 + "\uff09\uff0c\u4e3a\u907f\u514d\u810f\u6570\u636e\u7ec8\u6b62\u8fc1\u79fb\u3002").toString());
            }
        }

        private final List<ValidationResult> validateCounts(Connection sourceConn, Connection targetConn, MigrationState state, Set<String> questSuffixes) {
            List results = new ArrayList();
            ((Collection)results).add(this.validateTable(sourceConn, targetConn, state.getSqlite().getTableVariables().getName(), state.getTarget().getTableVariables().getName(), true));
            ((Collection)results).add(this.validateTable(sourceConn, targetConn, state.getSqlite().getTableUserData().getName(), state.getTarget().getTableUserData().getName(), true));
            Iterable $this$forEach$iv = questSuffixes;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                String suffix = (String)element$iv;
                boolean bl = false;
                String sourceQuest = state.getSqlite().table(suffix).getQuest().getName();
                String sourceQuestData = state.getSqlite().table(suffix).getQuestData().getName();
                String targetQuest = state.getTarget().table(suffix).getQuest().getName();
                String targetQuestData = state.getTarget().table(suffix).getQuestData().getName();
                ((Collection)results).add(this.validateTable(sourceConn, targetConn, sourceQuest, targetQuest, true));
                ((Collection)results).add(this.validateTable(sourceConn, targetConn, sourceQuestData, targetQuestData, true));
            }
            long userCount = this.countRows(targetConn, state.getTarget().getTableUser().getName());
            if (userCount != (long)state.userSize()) {
                throw new IllegalStateException(("\u7528\u6237\u8868\u884c\u6570\u4e0d\u4e00\u81f4\uff0c\u671f\u671b " + state.userSize() + " \u5b9e\u9645 " + userCount).toString());
            }
            Iterable $this$forEach$iv2 = results;
            boolean $i$f$forEach2 = false;
            for (Object element$iv : $this$forEach$iv2) {
                ValidationResult it = (ValidationResult)element$iv;
                boolean bl = false;
                if (it.getExpected() == it.getActual() && (it.getExpectedActive() == null || Intrinsics.areEqual((Object)it.getExpectedActive(), (Object)it.getActualActive()))) continue;
                StringBuilder stringBuilder = new StringBuilder().append("\u8868 ").append(it.getTable()).append(" \u6821\u9a8c\u5931\u8d25\uff0c\u671f\u671b ").append(it.getExpected()).append('/');
                Object object = it.getExpectedActive();
                if (object == null) {
                    object = "-";
                }
                StringBuilder stringBuilder2 = stringBuilder.append(object).append(" \u5b9e\u9645 ").append(it.getActual()).append('/');
                Object object2 = it.getActualActive();
                if (object2 == null) {
                    object2 = "-";
                }
                String string = stringBuilder2.append(object2).toString();
                throw new IllegalStateException(string.toString());
            }
            return results;
        }

        private final ValidationResult validateTable(Connection sourceConn, Connection targetConn, String source, String target, boolean hasMode) {
            long expected = this.countRows(sourceConn, source);
            long actual = this.countRows(targetConn, target);
            Long expectedActive = hasMode ? Long.valueOf(this.countActiveRows(sourceConn, source)) : null;
            Long actualActive = hasMode ? Long.valueOf(this.countActiveRows(targetConn, target)) : null;
            return new ValidationResult(target, expected, actual, expectedActive, actualActive);
        }

        private final void reportValidation(List<ValidationResult> results) {
            this.sender.sendMessage("\u00a7a[Chemdah] \u00a77\u8fc1\u79fb\u5b8c\u6210\uff0c\u6b63\u5728\u8f93\u51fa\u6821\u9a8c\u7ed3\u679c\uff1a");
            Iterable $this$forEach$iv = results;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                ValidationResult it = (ValidationResult)element$iv;
                boolean bl = false;
                String active = it.getExpectedActive() != null ? "\uff0c\u6d3b\u8dc3\u884c " + it.getExpectedActive() + " -> " + it.getActualActive() : "";
                this.sender.sendMessage("\u00a78- \u00a7f" + it.getTable() + " \u00a77\u884c\u6570 " + it.getExpected() + " -> " + it.getActual() + active);
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final long countRows(Connection conn, String table) {
            AutoCloseable autoCloseable = conn.prepareStatement("SELECT COUNT(*) FROM `" + table + '`');
            Throwable throwable = null;
            try {
                long l;
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    l = rs.next() ? rs.getLong(1) : 0L;
                }
                catch (Throwable throwable3) {
                    try {
                        try {
                            throwable2 = throwable3;
                            throw throwable3;
                        }
                        catch (Throwable throwable4) {
                            AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, throwable2);
                            throw throwable4;
                        }
                    }
                    catch (Throwable throwable5) {
                        throwable = throwable5;
                        throw throwable5;
                    }
                }
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                long l2 = l;
                return l2;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private final long countActiveRows(Connection conn, String table) {
            AutoCloseable autoCloseable = conn.prepareStatement("SELECT COUNT(*) FROM `" + table + "` WHERE `mode`=1");
            Throwable throwable = null;
            try {
                long l;
                PreparedStatement stmt = (PreparedStatement)autoCloseable;
                boolean bl = false;
                AutoCloseable autoCloseable2 = stmt.executeQuery();
                Throwable throwable2 = null;
                try {
                    ResultSet rs = (ResultSet)autoCloseable2;
                    boolean bl2 = false;
                    l = rs.next() ? rs.getLong(1) : 0L;
                }
                catch (Throwable throwable3) {
                    try {
                        try {
                            throwable2 = throwable3;
                            throw throwable3;
                        }
                        catch (Throwable throwable4) {
                            AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, throwable2);
                            throw throwable4;
                        }
                    }
                    catch (Throwable throwable5) {
                        throwable = throwable5;
                        throw throwable5;
                    }
                }
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable2, (Throwable)throwable2);
                long l2 = l;
                return l2;
            }
            finally {
                AutoCloseableKt.closeFinally((AutoCloseable)autoCloseable, (Throwable)throwable);
            }
        }

        private final String suffixLabel(String suffix) {
            String string = suffix;
            if (string == null) {
                string = "~";
            }
            return string;
        }

        private final void reportProgress(int current, String label) {
            if (current == 0) {
                return;
            }
            if (current % this.progressInterval == 0) {
                this.sender.sendMessage("\u00a77[Chemdah] \u00a77" + label + " \u5df2\u8fc1\u79fb \u00a7f" + current + " \u00a77\u6761...");
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0015\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0014\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0005H\u00c6\u0003J\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\rJ\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\rJD\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0019J\u0013\u0010\u001a\u001a\u00020\u001b2\b\u0010\u001c\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001d\u001a\u00020\u001eH\u00d6\u0001J\t\u0010\u001f\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0015\u0010\b\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u000e\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000bR\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u000e\u001a\u0004\b\u0010\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006 "}, d2={"Link/ptms/chemdah/module/command/CommandChemdahMigration$ValidationResult;", "", "table", "", "expected", "", "actual", "expectedActive", "actualActive", "(Ljava/lang/String;JJLjava/lang/Long;Ljava/lang/Long;)V", "getActual", "()J", "getActualActive", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getExpected", "getExpectedActive", "getTable", "()Ljava/lang/String;", "component1", "component2", "component3", "component4", "component5", "copy", "(Ljava/lang/String;JJLjava/lang/Long;Ljava/lang/Long;)Link/ptms/chemdah/module/command/CommandChemdahMigration$ValidationResult;", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
    private static final class ValidationResult {
        @NotNull
        private final String table;
        private final long expected;
        private final long actual;
        @Nullable
        private final Long expectedActive;
        @Nullable
        private final Long actualActive;

        public ValidationResult(@NotNull String table, long expected, long actual, @Nullable Long expectedActive, @Nullable Long actualActive) {
            Intrinsics.checkNotNullParameter((Object)table, (String)"table");
            this.table = table;
            this.expected = expected;
            this.actual = actual;
            this.expectedActive = expectedActive;
            this.actualActive = actualActive;
        }

        public /* synthetic */ ValidationResult(String string, long l, long l2, Long l3, Long l4, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 8) != 0) {
                l3 = null;
            }
            if ((n & 0x10) != 0) {
                l4 = null;
            }
            this(string, l, l2, l3, l4);
        }

        @NotNull
        public final String getTable() {
            return this.table;
        }

        public final long getExpected() {
            return this.expected;
        }

        public final long getActual() {
            return this.actual;
        }

        @Nullable
        public final Long getExpectedActive() {
            return this.expectedActive;
        }

        @Nullable
        public final Long getActualActive() {
            return this.actualActive;
        }

        @NotNull
        public final String component1() {
            return this.table;
        }

        public final long component2() {
            return this.expected;
        }

        public final long component3() {
            return this.actual;
        }

        @Nullable
        public final Long component4() {
            return this.expectedActive;
        }

        @Nullable
        public final Long component5() {
            return this.actualActive;
        }

        @NotNull
        public final ValidationResult copy(@NotNull String table, long expected, long actual, @Nullable Long expectedActive, @Nullable Long actualActive) {
            Intrinsics.checkNotNullParameter((Object)table, (String)"table");
            return new ValidationResult(table, expected, actual, expectedActive, actualActive);
        }

        public static /* synthetic */ ValidationResult copy$default(ValidationResult validationResult, String string, long l, long l2, Long l3, Long l4, int n, Object object) {
            if ((n & 1) != 0) {
                string = validationResult.table;
            }
            if ((n & 2) != 0) {
                l = validationResult.expected;
            }
            if ((n & 4) != 0) {
                l2 = validationResult.actual;
            }
            if ((n & 8) != 0) {
                l3 = validationResult.expectedActive;
            }
            if ((n & 0x10) != 0) {
                l4 = validationResult.actualActive;
            }
            return validationResult.copy(string, l, l2, l3, l4);
        }

        @NotNull
        public String toString() {
            return "ValidationResult(table=" + this.table + ", expected=" + this.expected + ", actual=" + this.actual + ", expectedActive=" + this.expectedActive + ", actualActive=" + this.actualActive + ')';
        }

        public int hashCode() {
            int result = this.table.hashCode();
            result = result * 31 + Long.hashCode(this.expected);
            result = result * 31 + Long.hashCode(this.actual);
            result = result * 31 + (this.expectedActive == null ? 0 : ((Object)this.expectedActive).hashCode());
            result = result * 31 + (this.actualActive == null ? 0 : ((Object)this.actualActive).hashCode());
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ValidationResult)) {
                return false;
            }
            ValidationResult validationResult = (ValidationResult)other;
            if (!Intrinsics.areEqual((Object)this.table, (Object)validationResult.table)) {
                return false;
            }
            if (this.expected != validationResult.expected) {
                return false;
            }
            if (this.actual != validationResult.actual) {
                return false;
            }
            if (!Intrinsics.areEqual((Object)this.expectedActive, (Object)validationResult.expectedActive)) {
                return false;
            }
            return Intrinsics.areEqual((Object)this.actualActive, (Object)validationResult.actualActive);
        }
    }
}

