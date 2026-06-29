/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util.debug;

import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.util.debug.Debug;
import ink.ptms.chemdah.util.debug.DebugHandler;
import ink.ptms.chemdah.util.debug.DebugWatchCommand;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="chemdah-debug-watch", aliases={"chdebug-watch"})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J \u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0002J \u0010\u000f\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0002R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/util/debug/DebugWatchCommand;", "", "()V", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "handleConsoleWatch", "", "sender", "Lorg/bukkit/command/CommandSender;", "target", "Lorg/bukkit/entity/Player;", "type", "Link/ptms/chemdah/util/debug/Debug;", "handlePlayerWatch", "player", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nDebugWatchCommand.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DebugWatchCommand.kt\nink/ptms/chemdah/util/debug/DebugWatchCommand\n+ 2 MapsJVM.kt\nkotlin/collections/MapsKt__MapsJVMKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,123:1\n73#2,2:124\n1#3:126\n*S KotlinDebug\n*F\n+ 1 DebugWatchCommand.kt\nink/ptms/chemdah/util/debug/DebugWatchCommand\n*L\n61#1:124,2\n61#1:126\n*E\n"})
public final class DebugWatchCommand {
    @NotNull
    public static final DebugWatchCommand INSTANCE = new DebugWatchCommand();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);

    private DebugWatchCommand() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    private final void handlePlayerWatch(Player player2, Player target, Debug type) {
        DebugHandler.WatchState state;
        Map $this$getOrPut$iv;
        ConcurrentMap concurrentMap = DebugHandler.INSTANCE.getWatchStates();
        UUID key$iv = player2.getUniqueId();
        boolean $i$f$getOrPut = false;
        Object object = $this$getOrPut$iv.get(key$iv);
        if (object == null) {
            boolean bl = false;
            UUID uUID = target.getUniqueId();
            Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"target.uniqueId");
            DebugHandler.WatchState default$iv = new DebugHandler.WatchState(uUID, null, 2, null);
            boolean bl2 = false;
            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
            if (object == null) {
                object = default$iv;
            }
        }
        if (!Intrinsics.areEqual((Object)(state = (DebugHandler.WatchState)object).getTarget(), (Object)target.getUniqueId())) {
            $this$getOrPut$iv = DebugHandler.INSTANCE.getWatchStates();
            UUID uUID = player2.getUniqueId();
            Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
            key$iv = uUID;
            UUID uUID2 = target.getUniqueId();
            Intrinsics.checkNotNullExpressionValue((Object)uUID2, (String)"target.uniqueId");
            Object object2 = new Debug[]{type};
            object2 = new DebugHandler.WatchState(uUID2, CollectionsKt.mutableListOf((Object[])object2));
            $this$getOrPut$iv.put(key$iv, object2);
            player2.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5207\u6362\u89c2\u5bdf\u76ee\u6807: &f" + target.getName() + "&7, \u5f00\u542f: &f" + (Object)((Object)type))));
        } else if (state.getTypes().contains((Object)type)) {
            state.getTypes().remove((Object)type);
            player2.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5173\u95ed\u89c2\u5bdf\u7c7b\u578b: &f" + (Object)((Object)type))));
            if (state.getTypes().isEmpty()) {
                DebugHandler.INSTANCE.getWatchStates().remove(player2.getUniqueId());
                player2.sendMessage(UtilKt.colored((String)"&c[Chemdah] &7\u5df2\u53d6\u6d88\u89c2\u5bdf"));
                return;
            }
        } else {
            state.getTypes().add(type);
            player2.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5f00\u542f\u89c2\u5bdf\u7c7b\u578b: &f" + (Object)((Object)type))));
        }
        DebugHandler.WatchState current = DebugHandler.INSTANCE.getWatchStates().get(player2.getUniqueId());
        if (current != null && !((Collection)current.getTypes()).isEmpty()) {
            Player targetPlayer = Bukkit.getPlayer((UUID)current.getTarget());
            StringBuilder stringBuilder = new StringBuilder().append("&c[Chemdah] &7\u89c2\u5bdf\u76ee\u6807: &f");
            Player player3 = targetPlayer;
            Object object3 = player3 != null ? player3.getName() : null;
            if (object3 == null) {
                object3 = current.getTarget();
            }
            player2.sendMessage(UtilKt.colored((String)stringBuilder.append(object3).toString()));
            player2.sendMessage(UtilKt.colored((String)"&c[Chemdah] &7\u89c2\u5bdf\u7c7b\u578b:"));
            for (Debug debug2 : current.getTypes()) {
                player2.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7 - &f" + (Object)((Object)debug2) + " &7(&f" + debug2.getDisplay() + "&7)")));
            }
        }
    }

    private final void handleConsoleWatch(CommandSender sender, Player target, Debug type) {
        DebugHandler.WatchState oldState = DebugHandler.INSTANCE.getConsoleWatchState();
        if (oldState == null || !Intrinsics.areEqual((Object)oldState.getTarget(), (Object)target.getUniqueId())) {
            UUID uUID = target.getUniqueId();
            Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"target.uniqueId");
            Debug[] debugArray = new Debug[]{type};
            DebugHandler.WatchState newState = new DebugHandler.WatchState(uUID, CollectionsKt.mutableListOf((Object[])debugArray));
            DebugHandler.INSTANCE.setConsoleWatchState(newState);
            if (oldState != null) {
                sender.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5207\u6362\u89c2\u5bdf\u76ee\u6807: &f" + target.getName() + "&7, \u5f00\u542f: &f" + (Object)((Object)type))));
            } else {
                sender.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5f00\u59cb\u89c2\u5bdf: &f" + target.getName() + "&7, \u5f00\u542f: &f" + (Object)((Object)type))));
            }
        } else if (oldState.getTypes().contains((Object)type)) {
            oldState.getTypes().remove((Object)type);
            sender.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5173\u95ed\u89c2\u5bdf\u7c7b\u578b: &f" + (Object)((Object)type))));
            if (oldState.getTypes().isEmpty()) {
                DebugHandler.INSTANCE.setConsoleWatchState(null);
                sender.sendMessage(UtilKt.colored((String)"&c[Chemdah] &7\u5df2\u53d6\u6d88\u89c2\u5bdf"));
                return;
            }
        } else {
            oldState.getTypes().add(type);
            sender.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7\u5df2\u5f00\u542f\u89c2\u5bdf\u7c7b\u578b: &f" + (Object)((Object)type))));
        }
        DebugHandler.WatchState current = DebugHandler.INSTANCE.getConsoleWatchState();
        if (current != null && !((Collection)current.getTypes()).isEmpty()) {
            Player targetPlayer = Bukkit.getPlayer((UUID)current.getTarget());
            StringBuilder stringBuilder = new StringBuilder().append("&c[Chemdah] &7\u89c2\u5bdf\u76ee\u6807: &f");
            Player player2 = targetPlayer;
            Object object = player2 != null ? player2.getName() : null;
            if (object == null) {
                object = current.getTarget();
            }
            sender.sendMessage(UtilKt.colored((String)stringBuilder.append(object).toString()));
            sender.sendMessage(UtilKt.colored((String)"&c[Chemdah] &7\u89c2\u5bdf\u7c7b\u578b:"));
            for (Debug debug2 : current.getTypes()) {
                sender.sendMessage(UtilKt.colored((String)("&c[Chemdah] &7 - &f" + (Object)((Object)debug2) + " &7(&f" + debug2.getDisplay() + "&7)")));
            }
        }
    }

    public static final /* synthetic */ void access$handlePlayerWatch(DebugWatchCommand $this, Player player2, Player target, Debug type) {
        $this.handlePlayerWatch(player2, target, type);
    }

    public static final /* synthetic */ void access$handleConsoleWatch(DebugWatchCommand $this, CommandSender sender, Player target, Debug type) {
        $this.handleConsoleWatch(sender, target, type);
    }
}

