/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.comparisons.ComparisonsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.apache.commons.lang3.time.DateFormatUtils
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.module.command.CommandChemdahQuest;
import ink.ptms.chemdah.module.command.CommandHelperKt;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.comparisons.ComparisonsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/*
 * Illegal identifiers - consider using --renameillegalidents true
 */
@CommandHeader(name="ChemdahQuest", aliases={"chq"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J'\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&2\b\b\u0002\u0010'\u001a\u00020(H\u0000\u00a2\u0006\u0002\b)J%\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&2\u0006\u0010*\u001a\u00020&H\u0000\u00a2\u0006\u0002\b)R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0006R\u0016\u0010\u000b\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0006R\u0016\u0010\r\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0016\u0010\u000f\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0006R\u0016\u0010\u0011\u001a\u00020\u00128\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0016\u0010\u0015\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0006R\u0016\u0010\u0017\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0006R\u0016\u0010\u0019\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0006R\u0016\u0010\u001b\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0006R\u0016\u0010\u001d\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0006R\u0016\u0010\u001f\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0006\u00a8\u0006+"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahQuest;", "", "()V", "accept", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getAccept", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "complete", "getComplete", "complete-task", "getComplete-task", "failure", "getFailure", "fakeComplete", "getFakeComplete", "info", "getInfo", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "restart", "getRestart", "stop", "getStop", "track", "getTrack", "trigger", "getTrigger", "triggerAll", "getTriggerAll", "ui", "getUi", "commandInfo", "", "sender", "Lorg/bukkit/command/CommandSender;", "player", "", "page", "", "commandInfo$Chemdah", "questName", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCommandChemdahQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CommandChemdahQuest.kt\nink/ptms/chemdah/module/command/CommandChemdahQuest\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,353:1\n1855#2:354\n1045#2:355\n1855#2,2:356\n1856#2:358\n1855#2,2:359\n*S KotlinDebug\n*F\n+ 1 CommandChemdahQuest.kt\nink/ptms/chemdah/module/command/CommandChemdahQuest\n*L\n292#1:354\n305#1:355\n306#1:356,2\n292#1:358\n348#1:359,2\n*E\n"})
public final class CommandChemdahQuest {
    @NotNull
    public static final CommandChemdahQuest INSTANCE = new CommandChemdahQuest();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody accept = SimpleCommandKt.subCommand((Function1)accept.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody failure = SimpleCommandKt.subCommand((Function1)failure.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody complete = SimpleCommandKt.subCommand((Function1)complete.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody fakeComplete = SimpleCommandKt.subCommand((Function1)fakeComplete.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody complete-task = SimpleCommandKt.subCommand((Function1)complete-task.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody restart = SimpleCommandKt.subCommand((Function1)restart.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody stop = SimpleCommandKt.subCommand((Function1)stop.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody trigger = SimpleCommandKt.subCommand((Function1)trigger.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody triggerAll = SimpleCommandKt.subCommand((Function1)triggerAll.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody info = SimpleCommandKt.subCommand((Function1)info.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody ui = SimpleCommandKt.subCommand((Function1)ui.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody track = SimpleCommandKt.subCommand((Function1)track.1.INSTANCE);

    private CommandChemdahQuest() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getAccept() {
        return accept;
    }

    @NotNull
    public final SimpleCommandBody getFailure() {
        return failure;
    }

    @NotNull
    public final SimpleCommandBody getComplete() {
        return complete;
    }

    @NotNull
    public final SimpleCommandBody getFakeComplete() {
        return fakeComplete;
    }

    @NotNull
    public final SimpleCommandBody getComplete-task() {
        return complete-task;
    }

    @NotNull
    public final SimpleCommandBody getRestart() {
        return restart;
    }

    @NotNull
    public final SimpleCommandBody getStop() {
        return stop;
    }

    @NotNull
    public final SimpleCommandBody getTrigger() {
        return trigger;
    }

    @NotNull
    public final SimpleCommandBody getTriggerAll() {
        return triggerAll;
    }

    @NotNull
    public final SimpleCommandBody getInfo() {
        return info;
    }

    @NotNull
    public final SimpleCommandBody getUi() {
        return ui;
    }

    @NotNull
    public final SimpleCommandBody getTrack() {
        return track;
    }

    /*
     * WARNING - void declaration
     */
    public final void commandInfo$Chemdah(@NotNull CommandSender sender, @NotNull String player, int page) {
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Player player2 = Bukkit.getPlayerExact((String)player);
        Intrinsics.checkNotNull((Object)player2);
        Player playerExact = player2;
        List<Quest> quests = ChemdahAPI.INSTANCE.getChemdahProfile(playerExact).getQuests(true);
        if (quests.isEmpty()) {
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-empty", (Object[])new Object[0]);
            return;
        }
        CommandHelperKt.space(sender);
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-header", (Object[])new Object[0]);
        Iterable $this$forEach$iv = CollectionKt.subList(quests, (int)(page * 3), (int)((page + 1) * 3));
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            void $this$forEach$iv2;
            Iterable $this$sortedBy$iv;
            Object object;
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            if (quest2.isOwner(playerExact)) {
                object = new Object[]{quest2.getId()};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-name", (Object[])object);
            } else {
                object = new Object[]{quest2.getId()};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-name-share", (Object[])object);
            }
            object = new Object[1];
            Intrinsics.checkNotNullExpressionValue((Object)DateFormatUtils.format((long)quest2.getStartTime(), (String)"yyyy/MM/dd HH:mm:ss"), (String)"format(quest.startTime, \"yyyy/MM/dd HH:mm:ss\")");
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-start-at", (Object[])object);
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-data", (Object[])new Object[0]);
            object = quest2.getPersistentDataContainer().entries();
            boolean $i$f$sortedBy = false;
            $this$sortedBy$iv = CollectionsKt.sortedWith((Iterable)$this$sortedBy$iv, (Comparator)new Comparator(){

                public final int compare(T a, T b) {
                    Map.Entry entry = (Map.Entry)a;
                    boolean bl = false;
                    Comparable comparable = (Comparable)((Object)((String)entry.getKey()));
                    entry = (Map.Entry)b;
                    Comparable comparable2 = comparable;
                    bl = false;
                    return ComparisonsKt.compareValues((Comparable)comparable2, (Comparable)((Comparable)((Object)((String)entry.getKey()))));
                }
            });
            boolean $i$f$forEach2 = false;
            for (Object element$iv2 : $this$forEach$iv2) {
                Map.Entry e = (Map.Entry)element$iv2;
                boolean bl2 = false;
                Object[] objectArray = new Object[]{"      \u00a77" + StringsKt.replace$default((String)((String)e.getKey()), (String)".", (String)"\u00a7f.\u00a77", (boolean)false, (int)4, null) + " \u00a78= \u00a7f" + ((Data)e.getValue()).getData()};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-body", (Object[])objectArray);
            }
        }
        int max2 = (int)Math.ceil((double)quests.size() / 3.0);
        if (page == 0) {
            if (max2 > 1) {
                Object[] objectArray = new Object[]{1, max2, player, 2};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-bottom-0", (Object[])objectArray);
            }
        } else if (page + 1 == max2) {
            Object[] objectArray = new Object[]{page + 1, max2, player, page};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-bottom-1", (Object[])objectArray);
        } else {
            Object[] objectArray = new Object[]{page + 1, max2, player, page, page + 2};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-bottom-2", (Object[])objectArray);
        }
    }

    public static /* synthetic */ void commandInfo$Chemdah$default(CommandChemdahQuest commandChemdahQuest, CommandSender commandSender, String string, int n, int n2, Object object) {
        if ((n2 & 4) != 0) {
            n = 0;
        }
        commandChemdahQuest.commandInfo$Chemdah(commandSender, string, n);
    }

    public final void commandInfo$Chemdah(@NotNull CommandSender sender, @NotNull String player, @NotNull String questName) {
        Object[] objectArray;
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)questName, (String)"questName");
        Player player2 = Bukkit.getPlayerExact((String)player);
        Intrinsics.checkNotNull((Object)player2);
        Player playerExact = player2;
        Quest quest2 = ChemdahAPI.INSTANCE.getChemdahProfile(playerExact).getQuestById(questName, true);
        if (quest2 == null) {
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-empty", (Object[])new Object[0]);
            return;
        }
        CommandHelperKt.space(sender);
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-header", (Object[])new Object[0]);
        if (quest2.isOwner(playerExact)) {
            objectArray = new Object[]{quest2.getId()};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-name", (Object[])objectArray);
        } else {
            objectArray = new Object[]{quest2.getId()};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-name-share", (Object[])objectArray);
        }
        objectArray = new Object[1];
        Intrinsics.checkNotNullExpressionValue((Object)DateFormatUtils.format((long)quest2.getStartTime(), (String)"yyyy/MM/dd HH:mm:ss"), (String)"format(quest.startTime, \"yyyy/MM/dd HH:mm:ss\")");
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-start-at", (Object[])objectArray);
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-data", (Object[])new Object[0]);
        Iterable $this$forEach$iv = quest2.getPersistentDataContainer().entries();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Map.Entry e = (Map.Entry)element$iv;
            boolean bl = false;
            Object[] objectArray2 = new Object[]{"      \u00a77" + StringsKt.replace$default((String)((String)e.getKey()), (String)".", (String)"\u00a7f.\u00a77", (boolean)false, (int)4, null) + " \u00a78= \u00a7f" + ((Data)e.getValue()).getData()};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-quest-info-body", (Object[])objectArray2);
        }
    }
}

