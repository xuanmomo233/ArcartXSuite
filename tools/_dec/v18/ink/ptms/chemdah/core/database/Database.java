/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Preconditions
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.PlatformFactory
 *  ink.ptms.chemdah.taboolib.common.platform.Schedule
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.module.configuration.ConfigNode
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerLoginEvent
 *  org.bukkit.event.player.PlayerLoginEvent$Result
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.database;

import com.google.common.base.Preconditions;
import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.database.DatabaseError;
import ink.ptms.chemdah.core.database.DatabaseSQL;
import ink.ptms.chemdah.core.database.DatabaseSQLite;
import ink.ptms.chemdah.core.database.Relational;
import ink.ptms.chemdah.core.database.Type;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.PlatformFactory;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010 \n\u0002\b\u0002\b&\u0018\u0000 \u00182\u00020\u0001:\u0001\u0018B\u0005\u00a2\u0006\u0002\u0010\u0002J \u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH&J\u000e\u0010\u000b\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\rJ\u0010\u0010\u000e\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\rH$J\u0010\u0010\u000f\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\u0006H&J\u0010\u0010\u0010\u001a\u0004\u0018\u00010\r2\u0006\u0010\f\u001a\u00020\rJ\u0012\u0010\u0011\u001a\u0004\u0018\u00010\r2\u0006\u0010\f\u001a\u00020\rH$J\u0018\u0010\u0012\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH&J\u0016\u0010\u0013\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0014\u001a\u00020\rJ\u0018\u0010\u0015\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0014\u001a\u00020\rH$J\u000e\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\r0\u0017H&\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/database/Database;", "", "()V", "releaseQuest", "", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "releaseVariable", "key", "", "releaseVariable0", "select", "selectVariable", "selectVariable0", "update", "updateVariable", "value", "updateVariable0", "variables", "", "Companion", "Chemdah"})
public abstract class Database {
    @NotNull
    public static final Companion Companion = new Companion(null);
    public static Database INSTANCE;
    @ConfigNode(value="database.disable-auto-save")
    private static boolean isDisableAutoSave;
    @ConfigNode(value="database.disable-auto-create-table")
    private static boolean isDisableAutoCreateTable;
    private static boolean isLoadInJoinEvent;
    private static boolean isReleaseInQuitEvent;

    @NotNull
    public abstract PlayerProfile select(@NotNull Player var1);

    public abstract void update(@NotNull Player var1, @NotNull PlayerProfile var2);

    public abstract void releaseQuest(@NotNull Player var1, @NotNull PlayerProfile var2, @NotNull Quest var3);

    @Nullable
    public final String selectVariable(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Preconditions.checkState((key.length() <= 36 ? 1 : 0) != 0, (String)"key.length > 36", (Object[])new Object[0]);
        return this.selectVariable0(key);
    }

    public final void updateVariable(@NotNull String key, @NotNull String value2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        Preconditions.checkState((key.length() <= 36 ? 1 : 0) != 0, (String)"key.length > 36", (Object[])new Object[0]);
        Preconditions.checkState((value2.length() <= 64 ? 1 : 0) != 0, (String)"value.length > 64", (Object[])new Object[0]);
        this.updateVariable0(key, value2);
    }

    public final void releaseVariable(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Preconditions.checkState((key.length() <= 36 ? 1 : 0) != 0, (String)"key.length > 36", (Object[])new Object[0]);
        this.releaseVariable0(key);
    }

    @NotNull
    public abstract List<String> variables();

    @Nullable
    protected abstract String selectVariable0(@NotNull String var1);

    protected abstract void updateVariable0(@NotNull String var1, @NotNull String var2);

    protected abstract void releaseVariable0(@NotNull String var1);

    static {
        isLoadInJoinEvent = true;
        isReleaseInQuitEvent = true;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\n\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0014\u001a\u00020\u0015H\u0003J\u000e\u0010\u0016\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u0018J\u0010\u0010\u0019\u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020\u001bH\u0003J\u0010\u0010\u001c\u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020\u001dH\u0003J\u0010\u0010\u001e\u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020\u001fH\u0003J\u0010\u0010 \u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020!H\u0003J\u0014\u0010\"\u001a\b\u0012\u0004\u0012\u00020$0#2\u0006\u0010\u0017\u001a\u00020\u0018J\u0006\u0010%\u001a\u00020\u0015J\b\u0010&\u001a\u00020\u0015H\u0003R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001e\u0010\t\u001a\u00020\n8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\u000b\"\u0004\b\f\u0010\rR\u001e\u0010\u000e\u001a\u00020\n8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000b\"\u0004\b\u000f\u0010\rR\u001a\u0010\u0010\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u000b\"\u0004\b\u0011\u0010\rR\u001a\u0010\u0012\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u000b\"\u0004\b\u0013\u0010\r\u00a8\u0006'"}, d2={"Link/ptms/chemdah/core/database/Database$Companion;", "", "()V", "INSTANCE", "Link/ptms/chemdah/core/database/Database;", "getINSTANCE", "()Link/ptms/chemdah/core/database/Database;", "setINSTANCE", "(Link/ptms/chemdah/core/database/Database;)V", "isDisableAutoCreateTable", "", "()Z", "setDisableAutoCreateTable", "(Z)V", "isDisableAutoSave", "setDisableAutoSave", "isLoadInJoinEvent", "setLoadInJoinEvent", "isReleaseInQuitEvent", "setReleaseInQuitEvent", "cancel", "", "loadProfile", "player", "Lorg/bukkit/entity/Player;", "onJoin", "e", "Lorg/bukkit/event/player/PlayerJoinEvent;", "onLogin", "Lorg/bukkit/event/player/PlayerLoginEvent;", "onQuit", "Lorg/bukkit/event/player/PlayerQuitEvent;", "onReleased", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "releaseProfile", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "setup", "update200", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nDatabase.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Database.kt\nink/ptms/chemdah/core/database/Database$Companion\n+ 2 PlatformFactory.kt\ntaboolib/common/platform/PlatformFactory\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,261:1\n132#2:262\n766#3:263\n857#3,2:264\n1855#3,2:266\n766#3:268\n857#3,2:269\n1855#3,2:271\n*S KotlinDebug\n*F\n+ 1 Database.kt\nink/ptms/chemdah/core/database/Database$Companion\n*L\n121#1:262\n247#1:263\n247#1:264,2\n247#1:266,2\n256#1:268\n256#1:269,2\n256#1:271,2\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Database getINSTANCE() {
            Database database = INSTANCE;
            if (database != null) {
                return database;
            }
            Intrinsics.throwUninitializedPropertyAccessException((String)"INSTANCE");
            return null;
        }

        public final void setINSTANCE(@NotNull Database database) {
            Intrinsics.checkNotNullParameter((Object)database, (String)"<set-?>");
            INSTANCE = database;
        }

        public final boolean isDisableAutoSave() {
            return isDisableAutoSave;
        }

        public final void setDisableAutoSave(boolean bl) {
            isDisableAutoSave = bl;
        }

        public final boolean isDisableAutoCreateTable() {
            return isDisableAutoCreateTable;
        }

        public final void setDisableAutoCreateTable(boolean bl) {
            isDisableAutoCreateTable = bl;
        }

        public final boolean isLoadInJoinEvent() {
            return isLoadInJoinEvent;
        }

        public final void setLoadInJoinEvent(boolean bl) {
            isLoadInJoinEvent = bl;
        }

        public final boolean isReleaseInQuitEvent() {
            return isReleaseInQuitEvent;
        }

        public final void setReleaseInQuitEvent(boolean bl) {
            isReleaseInQuitEvent = bl;
        }

        /*
         * WARNING - void declaration
         */
        public final void setup() {
            Database database;
            Companion companion;
            if (INSTANCE != null) {
                return;
            }
            Companion companion2 = this;
            try {
                Database database2;
                Database impl;
                companion = companion2;
                PlatformFactory this_$iv = PlatformFactory.INSTANCE;
                boolean $i$f$getAPIOrNull = false;
                Object v = this_$iv.getAwokenMap().get(Database.class.getName());
                if (!(v instanceof Database)) {
                    v = null;
                }
                if ((impl = (Database)v) == null) {
                    Relational<Object, Object, Object> relational;
                    Type type = Type.Companion.getINSTANCE();
                    if (type == Type.LOCAL) {
                        relational = new DatabaseSQLite().setup();
                    } else if (type == Type.SQL) {
                        relational = new DatabaseSQL().setup();
                    } else {
                        throw new IllegalStateException(LocaleKt.t((String)"\n                                \u6ca1\u6709\u627e\u5230\u81ea\u5b9a\u4e49\u7684\u6570\u636e\u5e93\u5b9e\u73b0\u3002\n                                No custom database implementation found.\n                            ").toString());
                    }
                    database2 = relational;
                } else {
                    database2 = database;
                }
                database = database2;
            }
            catch (NoClassDefFoundError type) {
                companion = companion2;
                database = new DatabaseError(new IllegalStateException(LocaleKt.t((String)"\n                            \u6ca1\u6709\u627e\u5230\u6570\u636e\u5e93\u5b9e\u73b0\u3002\n                            Database implementation not found.\n                        ")));
            }
            catch (Throwable _) {
                void e;
                companion = companion2;
                database = new DatabaseError((Throwable)e);
            }
            companion.setINSTANCE(database);
        }

        public final void loadProfile(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            PlayerProfile profile = this.getINSTANCE().select(player);
            Map map = ChemdahAPI.INSTANCE.getPlayerProfile();
            String string = player.getName();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
            map.put(string, profile);
            new PlayerEvents.Selected(player, profile).call();
        }

        @NotNull
        public final CompletableFuture<Void> releaseProfile(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            return new PlayerEvents.Released(player).wait();
        }

        @SubscribeEvent
        private final void onLogin(PlayerLoginEvent e) {
            if (INSTANCE == null) {
                e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                Player player = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                e.setKickMessage(BukkitLangKt.asLangText((CommandSender)((CommandSender)player), (String)"database-not-ready", (Object[])new Object[0]));
            } else if (this.getINSTANCE() instanceof DatabaseError) {
                e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                Player player = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                e.setKickMessage(BukkitLangKt.asLangText((CommandSender)((CommandSender)player), (String)"database-error", (Object[])new Object[0]));
            }
        }

        @SubscribeEvent
        private final void onJoin(PlayerJoinEvent e) {
            if (this.isLoadInJoinEvent()) {
                long l = Chemdah.INSTANCE.getConf().getLong("join-select-delay", 20L);
                ExecutorKt.submit$default((boolean)false, (boolean)true, (long)l, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e){
                    final /* synthetic */ PlayerJoinEvent $e;
                    {
                        this.$e = $e;
                        super(1);
                    }

                    /*
                     * WARNING - Removed try catching itself - possible behaviour change.
                     */
                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        block4: {
                            block5: {
                                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                                if (!this.$e.getPlayer().isOnline()) break block5;
                                try {
                                    Player player = this.$e.getPlayer();
                                    Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                                    Database.Companion.loadProfile(player);
                                }
                                catch (Throwable ex) {
                                    try {
                                        ex.printStackTrace();
                                    }
                                    catch (Throwable throwable) {
                                        ExecutorKt.submit$default((boolean)false, (boolean)false, (long)40L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this.$e){
                                            final /* synthetic */ PlayerJoinEvent $e;
                                            {
                                                this.$e = $e;
                                                super(1);
                                            }

                                            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                                                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                                                if (this.$e.getPlayer().isOnline()) {
                                                    Player player = this.$e.getPlayer();
                                                    Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                                                    if (ChemdahAPI.INSTANCE.getNonChemdahProfileLoaded(player)) {
                                                        this.$e.getPlayer().kickPlayer(LocaleKt.t((String)"\n                                            \u65e0\u6cd5\u52a0\u8f7d\u60a8\u7684\u6570\u636e\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002\n                                            Unable to load your data, please log in again.\n                                        "));
                                                    }
                                                }
                                            }
                                        }), (int)11, null);
                                        throw throwable;
                                    }
                                    ExecutorKt.submit$default((boolean)false, (boolean)false, (long)40L, (long)0L, (Function1)((Function1)new /* invalid duplicate definition of identical inner class */), (int)11, null);
                                    break block4;
                                }
                                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)40L, (long)0L, (Function1)((Function1)new /* invalid duplicate definition of identical inner class */), (int)11, null);
                                break block4;
                            }
                            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                                \u73a9\u5bb6 " + this.$e.getPlayer().getName() + " \u5df2\u79bb\u7ebf\uff0c\u8df3\u8fc7\u6570\u636e\u52a0\u8f7d\u3002\n                                Player " + this.$e.getPlayer().getName() + " is offline, skipping data loading.\n                            "))};
                            IOKt.warning((Object[])objectArray);
                        }
                    }
                }), (int)9, null);
            }
        }

        @SubscribeEvent(priority=EventPriority.MONITOR)
        private final void onQuit(PlayerQuitEvent e) {
            if (this.isReleaseInQuitEvent()) {
                Player player = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                this.releaseProfile(player);
            }
        }

        @SubscribeEvent(priority=EventPriority.MONITOR)
        private final void onReleased(PlayerEvents.Released e) {
            PlayerProfile profile;
            if (this.isDisableAutoSave()) {
                return;
            }
            PlayerProfile playerProfile = profile = ChemdahAPI.INSTANCE.getPlayerProfile().get(e.getPlayer().getName());
            boolean bl = playerProfile != null ? playerProfile.isDataChanged() : false;
            if (bl) {
                e.await(() -> Companion.onReleased$lambda$0(profile, e));
                ChemdahAPI.INSTANCE.getPlayerProfile().remove(e.getPlayer().getName());
            }
        }

        /*
         * WARNING - void declaration
         */
        @Schedule(async=true, period=200L)
        private final void update200() {
            void $this$filterTo$iv$iv;
            if (this.isDisableAutoSave()) {
                return;
            }
            Collection collection = Bukkit.getOnlinePlayers();
            Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
            Iterable $this$filter$iv = collection;
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Player it = (Player)element$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            Iterable $this$forEach$iv = (List)destination$iv$iv;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                ChemdahAPI.INSTANCE.getChemdahProfile(it).push();
            }
        }

        /*
         * WARNING - void declaration
         */
        @Awake(value=LifeCycle.DISABLE)
        private final void cancel() {
            void $this$filterTo$iv$iv;
            if (this.isDisableAutoSave()) {
                return;
            }
            Collection collection = Bukkit.getOnlinePlayers();
            Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
            Iterable $this$filter$iv = collection;
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Player it = (Player)element$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            Iterable $this$forEach$iv = (List)destination$iv$iv;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                ChemdahAPI.INSTANCE.getChemdahProfile(it).push();
            }
        }

        private static final void onReleased$lambda$0(PlayerProfile $profile, PlayerEvents.Released $e) {
            Intrinsics.checkNotNullParameter((Object)((Object)$e), (String)"$e");
            $profile.push($e.getPlayer());
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

