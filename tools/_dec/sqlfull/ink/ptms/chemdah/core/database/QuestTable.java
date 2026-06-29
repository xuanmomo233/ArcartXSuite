/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.core.database.QuestTable;
import ink.ptms.chemdah.taboolib.module.database.ColumnBuilder;
import ink.ptms.chemdah.taboolib.module.database.Host;
import ink.ptms.chemdah.taboolib.module.database.HostSQL;
import ink.ptms.chemdah.taboolib.module.database.HostSQLite;
import ink.ptms.chemdah.taboolib.module.database.Table;
import ink.ptms.chemdah.util.StringKt;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\n\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0003:\u0002\u0017\u0018B%\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00028\u00000\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\tJ\u001b\u0010\u0015\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u0005\u0012\u0004\u0012\u00028\u00000\u000fH\u0086\u0002J\u001b\u0010\u0016\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u0005\u0012\u0004\u0012\u00028\u00000\u000fH\u0086\u0002R\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00028\u00000\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR$\u0010\u000e\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u0005\u0012\u0004\u0012\u00028\u00000\u000fX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R$\u0010\u0012\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u0005\u0012\u0004\u0012\u00028\u00000\u000fX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\u0011R\u0013\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\r\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/database/QuestTable;", "T", "Link/ptms/chemdah/taboolib/module/database/ColumnBuilder;", "", "host", "Link/ptms/chemdah/taboolib/module/database/Host;", "prefix", "", "suffix", "(Link/ptms/chemdah/taboolib/module/database/Host;Ljava/lang/String;Ljava/lang/String;)V", "getHost", "()Link/ptms/chemdah/taboolib/module/database/Host;", "getPrefix", "()Ljava/lang/String;", "quest", "Link/ptms/chemdah/taboolib/module/database/Table;", "getQuest", "()Link/ptms/chemdah/taboolib/module/database/Table;", "questData", "getQuestData", "getSuffix", "component1", "component2", "SQL", "SQLite", "Chemdah"})
public abstract class QuestTable<T extends ColumnBuilder> {
    @NotNull
    private final Host<T> host;
    @NotNull
    private final String prefix;
    @Nullable
    private final String suffix;

    public QuestTable(@NotNull Host<T> host, @NotNull String prefix, @Nullable String suffix) {
        Intrinsics.checkNotNullParameter(host, (String)"host");
        Intrinsics.checkNotNullParameter((Object)prefix, (String)"prefix");
        this.host = host;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @NotNull
    public final Host<T> getHost() {
        return this.host;
    }

    @NotNull
    public final String getPrefix() {
        return this.prefix;
    }

    @Nullable
    public final String getSuffix() {
        return this.suffix;
    }

    @NotNull
    public abstract Table<Host<T>, T> getQuest();

    @NotNull
    public abstract Table<Host<T>, T> getQuestData();

    @NotNull
    public final Table<Host<T>, T> component1() {
        return this.getQuest();
    }

    @NotNull
    public final Table<Host<T>, T> component2() {
        return this.getQuestData();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u001f\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\bR&\u0010\t\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u000b\u0012\u0004\u0012\u00020\u00020\nX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR&\u0010\u000e\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u000b\u0012\u0004\u0012\u00020\u00020\nX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\r\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/database/QuestTable$SQL;", "Link/ptms/chemdah/core/database/QuestTable;", "Link/ptms/chemdah/taboolib/module/database/SQL;", "host", "Link/ptms/chemdah/taboolib/module/database/HostSQL;", "prefix", "", "suffix", "(Link/ptms/chemdah/taboolib/module/database/HostSQL;Ljava/lang/String;Ljava/lang/String;)V", "quest", "Link/ptms/chemdah/taboolib/module/database/Table;", "Link/ptms/chemdah/taboolib/module/database/Host;", "getQuest", "()Link/ptms/chemdah/taboolib/module/database/Table;", "questData", "getQuestData", "Chemdah"})
    public static final class SQL
    extends QuestTable<ink.ptms.chemdah.taboolib.module.database.SQL> {
        @NotNull
        private final Table<Host<ink.ptms.chemdah.taboolib.module.database.SQL>, ink.ptms.chemdah.taboolib.module.database.SQL> quest;
        @NotNull
        private final Table<Host<ink.ptms.chemdah.taboolib.module.database.SQL>, ink.ptms.chemdah.taboolib.module.database.SQL> questData;

        public SQL(@NotNull HostSQL host, @NotNull String prefix, @Nullable String suffix) {
            Intrinsics.checkNotNullParameter((Object)host, (String)"host");
            Intrinsics.checkNotNullParameter((Object)prefix, (String)"prefix");
            super((Host)host, prefix, suffix);
            StringBuilder stringBuilder = new StringBuilder().append(prefix).append("_quest");
            String string = suffix;
            if (string == null || (string = StringKt.addPrefix(string, "_")) == null) {
                string = "";
            }
            this.quest = new Table(stringBuilder.append(string).toString(), (Host)host, (Function1)quest.1.INSTANCE);
            StringBuilder stringBuilder2 = new StringBuilder().append(prefix).append("_quest_data");
            String string2 = suffix;
            if (string2 == null || (string2 = StringKt.addPrefix(string2, "_")) == null) {
                string2 = "";
            }
            this.questData = new Table(stringBuilder2.append(string2).toString(), (Host)host, (Function1)questData.1.INSTANCE);
        }

        @Override
        @NotNull
        public Table<Host<ink.ptms.chemdah.taboolib.module.database.SQL>, ink.ptms.chemdah.taboolib.module.database.SQL> getQuest() {
            return this.quest;
        }

        @Override
        @NotNull
        public Table<Host<ink.ptms.chemdah.taboolib.module.database.SQL>, ink.ptms.chemdah.taboolib.module.database.SQL> getQuestData() {
            return this.questData;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u001f\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\bR&\u0010\t\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u000b\u0012\u0004\u0012\u00020\u00020\nX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR&\u0010\u000e\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u000b\u0012\u0004\u0012\u00020\u00020\nX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\r\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/database/QuestTable$SQLite;", "Link/ptms/chemdah/core/database/QuestTable;", "Link/ptms/chemdah/taboolib/module/database/SQLite;", "host", "Link/ptms/chemdah/taboolib/module/database/HostSQLite;", "prefix", "", "suffix", "(Link/ptms/chemdah/taboolib/module/database/HostSQLite;Ljava/lang/String;Ljava/lang/String;)V", "quest", "Link/ptms/chemdah/taboolib/module/database/Table;", "Link/ptms/chemdah/taboolib/module/database/Host;", "getQuest", "()Link/ptms/chemdah/taboolib/module/database/Table;", "questData", "getQuestData", "Chemdah"})
    public static final class SQLite
    extends QuestTable<ink.ptms.chemdah.taboolib.module.database.SQLite> {
        @NotNull
        private final Table<Host<ink.ptms.chemdah.taboolib.module.database.SQLite>, ink.ptms.chemdah.taboolib.module.database.SQLite> quest;
        @NotNull
        private final Table<Host<ink.ptms.chemdah.taboolib.module.database.SQLite>, ink.ptms.chemdah.taboolib.module.database.SQLite> questData;

        public SQLite(@NotNull HostSQLite host, @NotNull String prefix, @Nullable String suffix) {
            Intrinsics.checkNotNullParameter((Object)host, (String)"host");
            Intrinsics.checkNotNullParameter((Object)prefix, (String)"prefix");
            super((Host)host, prefix, suffix);
            StringBuilder stringBuilder = new StringBuilder().append(prefix).append("_quest");
            String string = suffix;
            if (string == null || (string = StringKt.addPrefix(string, "_")) == null) {
                string = "";
            }
            this.quest = new Table(stringBuilder.append(string).toString(), (Host)host, (Function1)quest.1.INSTANCE);
            StringBuilder stringBuilder2 = new StringBuilder().append(prefix).append("_quest_data");
            String string2 = suffix;
            if (string2 == null || (string2 = StringKt.addPrefix(string2, "_")) == null) {
                string2 = "";
            }
            this.questData = new Table(stringBuilder2.append(string2).toString(), (Host)host, (Function1)questData.1.INSTANCE);
        }

        @Override
        @NotNull
        public Table<Host<ink.ptms.chemdah.taboolib.module.database.SQLite>, ink.ptms.chemdah.taboolib.module.database.SQLite> getQuest() {
            return this.quest;
        }

        @Override
        @NotNull
        public Table<Host<ink.ptms.chemdah.taboolib.module.database.SQLite>, ink.ptms.chemdah.taboolib.module.database.SQLite> getQuestData() {
            return this.questData;
        }
    }
}

