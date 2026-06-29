package xuanmo.arcartxsuite.questgps.chemdah.database;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.database.QuestTable;
import ink.ptms.chemdah.core.database.Relational;
import ink.ptms.chemdah.core.database.UserIndex;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.database.ActionInsert;
import ink.ptms.chemdah.taboolib.module.database.ActionSelect;
import ink.ptms.chemdah.taboolib.module.database.ActionUpdate;
import ink.ptms.chemdah.taboolib.module.database.ColumnOptionSQL;
import ink.ptms.chemdah.taboolib.module.database.ColumnTypeSQL;
import ink.ptms.chemdah.taboolib.module.database.FileToHostKt;
import ink.ptms.chemdah.taboolib.module.database.Host;
import ink.ptms.chemdah.taboolib.module.database.HostSQL;
import ink.ptms.chemdah.taboolib.module.database.JoinFilter;
import ink.ptms.chemdah.taboolib.module.database.SQL;
import ink.ptms.chemdah.taboolib.module.database.Table;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import org.bukkit.entity.Player;

/**
 * QuestGPS 自带的 Chemdah MySQL 数据库实现。
 * <p>
 * 完整复刻 Chemdah 付费版 {@code DatabaseSQL} 的逻辑（表结构、读写流程、SQL DSL 调用），
 * 直接继承免费版 JAR 中已有的 {@link Relational}，从而无需依赖
 * {@code Chemdah-1.1.33-FREE-patched.jar} 即可在免费版上使用 MySQL 存档。
 * <p>
 * 连接参数沿用 Chemdah 自身配置（{@code config.yml} 的 {@code database.source.SQL}），
 * 与付费版行为一致。{@code taboolib.module.database.*} 与 {@code kotlin1822.*} 由 TabooLib
 * 在运行时重定位提供，本模块仅在编译期使用同名占位类，并在打包时排除。
 */
public final class DatabaseMySQL extends Relational<SQL, Long, Long> {

    private final HostSQL host =
        FileToHostKt.getHost((ConfigurationSection) Chemdah.INSTANCE.getConf(), "database.source.SQL");

    private final Table<Host<SQL>, SQL> tableUser =
        new Table<>(getTablePrefix() + "_user", getHost(), tableUserColumns());
    private final Table<Host<SQL>, SQL> tableUserData =
        new Table<>(getTablePrefix() + "_user_data", getHost(), tableUserDataColumns());
    private final Table<Host<SQL>, SQL> tableVariables =
        new Table<>(getTablePrefix() + "_variables", getHost(), tableVariablesColumns());

    private final String questKey = "quest";
    private final boolean duplicateKeyUpdateSupported = true;

    @Override
    public HostSQL getHost() {
        return host;
    }

    public Table<Host<SQL>, SQL> getTableUser() {
        return tableUser;
    }

    @Override
    public Table<Host<SQL>, SQL> getTableUserData() {
        return tableUserData;
    }

    @Override
    public Table<Host<SQL>, SQL> getTableVariables() {
        return tableVariables;
    }

    @Override
    public String getQuestKey() {
        return questKey;
    }

    @Override
    public boolean isDuplicateKeyUpdateSupported() {
        return duplicateKeyUpdateSupported;
    }

    @Override
    public Relational<SQL, Long, Long> setup() {
        super.setup();
        if (!Database.Companion.isDisableAutoCreateTable()) {
            Table.createTable$default(tableUser, getDataSource(), false, 2, null);
        }
        return this;
    }

    public void updateUserTime(long userId) {
        tableUser.update(getDataSource(), new Function1<ActionUpdate, Unit>() {
            @Override
            public Unit invoke(ActionUpdate action) {
                action.where(action.eq("id", userId));
                action.set("time", new Date());
                return Unit.INSTANCE;
            }
        });
    }

    @Override
    public Long getUserId(Player player) {
        Object result = tableUser.select(getDataSource(), new Function1<ActionSelect, Unit>() {
            @Override
            public Unit invoke(ActionSelect action) {
                action.rows(new String[]{"id"});
                if (UserIndex.Companion.getINSTANCE() == UserIndex.NAME) {
                    action.where(action.eq("uuid", player.getName()));
                } else {
                    action.where(action.eq("uuid", player.getUniqueId().toString()));
                }
                action.limit(1);
                return Unit.INSTANCE;
            }
        }).firstOrNull(new Function1<ResultSet, Long>() {
            @Override
            public Long invoke(ResultSet rs) {
                try {
                    return rs.getLong("id");
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        return (Long) result;
    }

    @Override
    public Long getQuestId(Player player, Quest quest) {
        Object result = table(quest.getTemplate().getDataIsolation()).getQuest()
            .select(getDataSource(), new Function1<ActionSelect, Unit>() {
                @Override
                public Unit invoke(ActionSelect action) {
                    action.rows(new String[]{"id"});
                    action.where(action.and(
                        action.eq("user", getUserId(player)),
                        action.eq("quest", quest.getId())));
                    action.limit(1);
                    return Unit.INSTANCE;
                }
            }).firstOrNull(new Function1<ResultSet, Long>() {
                @Override
                public Long invoke(ResultSet rs) {
                    try {
                        return rs.getLong("id");
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
        return (Long) result;
    }

    @Override
    public QuestTable<SQL> newQuestTable(String name) {
        return new QuestTable.SQL(getHost(), getTablePrefix(), !"~".equals(name) ? name : null);
    }

    @Override
    public CompletableFuture<Long> createUser(PlayerProfile profile, Player player) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        tableUser.insert(getDataSource(), new String[]{"name", "uuid", "time"},
            new Function1<ActionInsert, Unit>() {
                @Override
                public Unit invoke(ActionInsert action) {
                    action.value(new Object[]{
                        player.getName(), player.getUniqueId().toString(), new Date()});
                    action.onFinally(new Function2<PreparedStatement, Connection, Unit>() {
                        @Override
                        public Unit invoke(PreparedStatement statement, Connection connection) {
                            try {
                                ResultSet keys = statement.getGeneratedKeys();
                                keys.next();
                                long userId = keys.getLong(1);
                                ChangeTracker tracker = profile.getPersistentDataContainer().flush();
                                if (!tracker.getModified().isEmpty()) {
                                    getTableUserData().insert(getDataSource(),
                                        new String[]{"user", "key", "value", "mode"},
                                        new Function1<ActionInsert, Unit>() {
                                            @Override
                                            public Unit invoke(ActionInsert dataAction) {
                                                for (Map.Entry<String, Data> entry
                                                        : tracker.getModified().entrySet()) {
                                                    dataAction.value(new Object[]{
                                                        userId, entry.getKey(),
                                                        entry.getValue().getData(), 1});
                                                }
                                                return Unit.INSTANCE;
                                            }
                                        });
                                }
                                for (Quest quest : profile.getQuests(false)) {
                                    createQuest(profile, userId, quest);
                                }
                                future.complete(userId);
                            } catch (SQLException ex) {
                                future.completeExceptionally(ex);
                            }
                            return Unit.INSTANCE;
                        }
                    });
                    return Unit.INSTANCE;
                }
            });
        return future;
    }

    @Override
    public void createQuest(PlayerProfile profile, Long userId, Quest quest) {
        QuestTable<SQL> questTable = table(quest.getTemplate().getDataIsolation());
        Table<Host<SQL>, SQL> questTableData = questTable.component2();
        questTable.component1().insert(getDataSource(), new String[]{"user", "quest", "mode"},
            new Function1<ActionInsert, Unit>() {
                @Override
                public Unit invoke(ActionInsert action) {
                    action.value(new Object[]{userId, quest.getId(), 1});
                    action.onFinally(new Function2<PreparedStatement, Connection, Unit>() {
                        @Override
                        public Unit invoke(PreparedStatement statement, Connection connection) {
                            try {
                                ResultSet keys = statement.getGeneratedKeys();
                                keys.next();
                                long questId = keys.getLong(1);
                                ChangeTracker tracker = quest.getPersistentDataContainer().flush();
                                if (!tracker.getModified().isEmpty()) {
                                    questTableData.insert(getDataSource(),
                                        new String[]{"quest", "key", "value", "mode"},
                                        new Function1<ActionInsert, Unit>() {
                                            @Override
                                            public Unit invoke(ActionInsert dataAction) {
                                                for (Map.Entry<String, Data> entry
                                                        : tracker.getModified().entrySet()) {
                                                    dataAction.value(new Object[]{
                                                        questId, entry.getKey(),
                                                        entry.getValue().getData(), 1});
                                                }
                                                return Unit.INSTANCE;
                                            }
                                        });
                                }
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }
                            return Unit.INSTANCE;
                        }
                    });
                    return Unit.INSTANCE;
                }
            });
    }

    @Override
    public PlayerProfile select(Player player) {
        PlayerProfile profile = new PlayerProfile(player.getUniqueId());
        profile.setup();
        Long userId = getUserId(player);
        if (userId == null) {
            return profile;
        }
        long resolved = userId;
        CompletableFuture.runAsync(() -> updateUserTime(resolved));
        return init(profile);
    }

    @Override
    public void update(Player player, PlayerProfile profile) {
        Long userId = getUserId(player);
        if (userId == null) {
            createUser(profile, player);
        } else {
            update(profile, player);
            updateQuest(profile, player);
        }
    }

    public PlayerProfile init(PlayerProfile profile) {
        final Long userId = getUserId(profile.getPlayer());
        List<?> userData = getTableUserData().select(getDataSource(), new Function1<ActionSelect, Unit>() {
            @Override
            public Unit invoke(ActionSelect action) {
                action.rows(new String[]{"key", "value"});
                action.where(action.and(
                    action.eq("user", getUserId(profile.getPlayer())),
                    action.eq("mode", 1)));
                return Unit.INSTANCE;
            }
        }).map(new Function1<ResultSet, Pair<String, String>>() {
            @Override
            public Pair<String, String> invoke(ResultSet rs) {
                try {
                    return TuplesKt.to(rs.getString("key"), rs.getString("value"));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        for (Object element : userData) {
            @SuppressWarnings("unchecked")
            Pair<String, String> pair = (Pair<String, String>) element;
            final String key = pair.component1();
            final String value = pair.component2();
            profile.getPersistentDataContainer().unchanged(container -> container.set(key, value));
        }

        Map<String, DataContainer> quests = new HashMap<>();
        for (QuestTable<SQL> table : getTableQuest().values()) {
            final Table<Host<SQL>, SQL> questTable = table.getQuest();
            final Table<Host<SQL>, SQL> questData = table.getQuestData();
            final String suffix = table.getSuffix();
            List<?> rows = questTable.select(getDataSource(), new Function1<ActionSelect, Unit>() {
                @Override
                public Unit invoke(ActionSelect action) {
                    action.rows(new String[]{
                        questTable.getName() + ".quest",
                        questData.getName() + ".key",
                        questData.getName() + ".value"});
                    action.where(action.and(
                        action.and(
                            action.eq("user", userId),
                            action.eq(questTable.getName() + ".mode", 1)),
                        action.eq(questData.getName() + ".mode", 1)));
                    action.innerJoin(questData.getName(), new Function1<JoinFilter, Unit>() {
                        @Override
                        public Unit invoke(JoinFilter filter) {
                            filter.append(filter.eq(
                                questTable.getName() + ".id",
                                filter.pre(questData.getName() + ".quest")));
                            return Unit.INSTANCE;
                        }
                    });
                    return Unit.INSTANCE;
                }
            }).map(new Function1<ResultSet, Pair<String, Pair<String, String>>>() {
                @Override
                public Pair<String, Pair<String, String>> invoke(ResultSet rs) {
                    try {
                        return TuplesKt.to(
                            rs.getString(questTable.getName() + ".quest"),
                            TuplesKt.to(
                                rs.getString(questData.getName() + ".key"),
                                rs.getString(questData.getName() + ".value")));
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            for (Object element : rows) {
                @SuppressWarnings("unchecked")
                Pair<String, Pair<String, String>> pair = (Pair<String, Pair<String, String>>) element;
                String questName = pair.component1();
                Template template = ChemdahAPI.INSTANCE.getQuestTemplate(questName);
                if (template == null || !Objects.equals(template.getDataIsolation(), suffix)) {
                    continue;
                }
                final Pair<String, String> data = pair.component2();
                DataContainer container = quests.get(questName);
                if (container == null) {
                    container = new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY());
                    quests.put(questName, container);
                }
                container.unchanged(target -> target.set(data.getFirst(), data.getSecond()));
            }
        }
        for (Map.Entry<String, DataContainer> entry : quests.entrySet()) {
            profile.registerQuest(new Quest(entry.getKey(), profile, entry.getValue()), false);
        }
        return profile;
    }

    private static Function1<Table<Host<SQL>, SQL>, Unit> tableUserColumns() {
        return new Function1<Table<Host<SQL>, SQL>, Unit>() {
            @Override
            public Unit invoke(Table<Host<SQL>, SQL> table) {
                Table.add$default(table, null, idColumn(), 1, null)
                    .add("name", typeColumn(ColumnTypeSQL.VARCHAR, 36, 0, options(ColumnOptionSQL.UNIQUE_KEY), 4))
                    .add("uuid", typeColumn(ColumnTypeSQL.VARCHAR, 36, 0, options(ColumnOptionSQL.UNIQUE_KEY), 4))
                    .add("time", typeColumn(ColumnTypeSQL.DATE, 0, 0, null, 14));
                return Unit.INSTANCE;
            }
        };
    }

    private static Function1<Table<Host<SQL>, SQL>, Unit> tableUserDataColumns() {
        return new Function1<Table<Host<SQL>, SQL>, Unit>() {
            @Override
            public Unit invoke(Table<Host<SQL>, SQL> table) {
                Table.add$default(table, null, idColumn(), 1, null)
                    .add("user", typeColumn(ColumnTypeSQL.INT, 16, 0, options(ColumnOptionSQL.KEY), 4))
                    .add("key", typeColumn(ColumnTypeSQL.VARCHAR, 64, 0, options(ColumnOptionSQL.KEY), 4))
                    .add("value", typeColumn(ColumnTypeSQL.VARCHAR, 64, 0, null, 12))
                    .add("mode", typeColumn(ColumnTypeSQL.BOOL, 0, 0, null, 14));
                return Unit.INSTANCE;
            }
        };
    }

    private static Function1<Table<Host<SQL>, SQL>, Unit> tableVariablesColumns() {
        return new Function1<Table<Host<SQL>, SQL>, Unit>() {
            @Override
            public Unit invoke(Table<Host<SQL>, SQL> table) {
                Table.add$default(table, null, idColumn(), 1, null)
                    .add("name", typeColumn(ColumnTypeSQL.VARCHAR, 64, 0, options(ColumnOptionSQL.UNIQUE_KEY), 4))
                    .add("data", typeColumn(ColumnTypeSQL.VARCHAR, 64, 0, null, 12))
                    .add("mode", typeColumn(ColumnTypeSQL.BOOL, 0, 0, null, 14));
                return Unit.INSTANCE;
            }
        };
    }

    private static Function1<SQL, Unit> idColumn() {
        return new Function1<SQL, Unit>() {
            @Override
            public Unit invoke(SQL sql) {
                sql.id();
                return Unit.INSTANCE;
            }
        };
    }

    private static Function1<SQL, Unit> typeColumn(ColumnTypeSQL type, int length, int index,
                                                   Function1<SQL, Unit> columnBuilder, int flags) {
        return new Function1<SQL, Unit>() {
            @Override
            public Unit invoke(SQL sql) {
                SQL.type$default(sql, type, length, index, columnBuilder, flags, null);
                return Unit.INSTANCE;
            }
        };
    }

    private static Function1<SQL, Unit> options(ColumnOptionSQL... columnOptions) {
        return new Function1<SQL, Unit>() {
            @Override
            public Unit invoke(SQL sql) {
                sql.options(columnOptions);
                return Unit.INSTANCE;
            }
        };
    }
}
