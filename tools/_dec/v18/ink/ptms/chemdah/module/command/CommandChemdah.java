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
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.module.command.CommandChemdah;
import ink.ptms.chemdah.module.command.CommandChemdahAPI;
import ink.ptms.chemdah.module.command.CommandChemdahPlayerData;
import ink.ptms.chemdah.module.command.CommandChemdahPlayerLevel;
import ink.ptms.chemdah.module.command.CommandChemdahQuest;
import ink.ptms.chemdah.module.command.CommandChemdahScript;
import ink.ptms.chemdah.module.command.CommandChemdahVariables;
import ink.ptms.chemdah.module.command.CommandHelperKt;
import ink.ptms.chemdah.module.party.Party;
import ink.ptms.chemdah.module.party.PartySystem;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.comparisons.ComparisonsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="Chemdah", aliases={"ch"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J'\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020.2\b\b\u0002\u0010/\u001a\u000200H\u0000\u00a2\u0006\u0002\b1R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\b8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0016\u0010\u000b\u001a\u00020\f8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0016\u0010\u000f\u001a\u00020\u00108\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0016\u0010\u0013\u001a\u00020\u00148\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0016\u0010\u0017\u001a\u00020\f8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u000eR\u0016\u0010\u0019\u001a\u00020\u001a8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0016\u0010\u001d\u001a\u00020\f8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u000eR\u0016\u0010\u001f\u001a\u00020\f8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u000eR\u0016\u0010!\u001a\u00020\"8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010$R\u0016\u0010%\u001a\u00020&8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b'\u0010(\u00a8\u00062"}, d2={"Link/ptms/chemdah/module/command/CommandChemdah;", "", "()V", "api", "Link/ptms/chemdah/module/command/CommandChemdahAPI;", "getApi", "()Link/ptms/chemdah/module/command/CommandChemdahAPI;", "data", "Link/ptms/chemdah/module/command/CommandChemdahPlayerData;", "getData", "()Link/ptms/chemdah/module/command/CommandChemdahPlayerData;", "info", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getInfo", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "level", "Link/ptms/chemdah/module/command/CommandChemdahPlayerLevel;", "getLevel", "()Link/ptms/chemdah/module/command/CommandChemdahPlayerLevel;", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "mirror", "getMirror", "quest", "Link/ptms/chemdah/module/command/CommandChemdahQuest;", "getQuest", "()Link/ptms/chemdah/module/command/CommandChemdahQuest;", "reload", "getReload", "save", "getSave", "script", "Link/ptms/chemdah/module/command/CommandChemdahScript;", "getScript", "()Link/ptms/chemdah/module/command/CommandChemdahScript;", "variable", "Link/ptms/chemdah/module/command/CommandChemdahVariables;", "getVariable", "()Link/ptms/chemdah/module/command/CommandChemdahVariables;", "commandInfo", "", "sender", "Lorg/bukkit/command/CommandSender;", "player", "Lorg/bukkit/entity/Player;", "page", "", "commandInfo$Chemdah", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCommandChemdah.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CommandChemdah.kt\nink/ptms/chemdah/module/command/CommandChemdah\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,157:1\n766#2:158\n857#2,2:159\n1549#2:161\n1620#2,3:162\n766#2:165\n857#2,2:166\n1549#2:168\n1620#2,3:169\n1549#2:172\n1620#2,3:173\n1045#2:176\n1855#2,2:177\n*S KotlinDebug\n*F\n+ 1 CommandChemdah.kt\nink/ptms/chemdah/module/command/CommandChemdah\n*L\n100#1:158\n100#1:159,2\n100#1:161\n100#1:162,3\n106#1:165\n106#1:166,2\n106#1:168\n106#1:169,3\n119#1:172\n119#1:173,3\n127#1:176\n128#1:177,2\n*E\n"})
public final class CommandChemdah {
    @NotNull
    public static final CommandChemdah INSTANCE = new CommandChemdah();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody(optional=true)
    @NotNull
    private static final CommandChemdahAPI api = CommandChemdahAPI.INSTANCE;
    @CommandBody
    @NotNull
    private static final CommandChemdahPlayerData data = CommandChemdahPlayerData.INSTANCE;
    @CommandBody
    @NotNull
    private static final CommandChemdahPlayerLevel level = CommandChemdahPlayerLevel.INSTANCE;
    @CommandBody
    @NotNull
    private static final CommandChemdahQuest quest = CommandChemdahQuest.INSTANCE;
    @CommandBody
    @NotNull
    private static final CommandChemdahScript script = CommandChemdahScript.INSTANCE;
    @CommandBody(aliases={"vars"})
    @NotNull
    private static final CommandChemdahVariables variable = CommandChemdahVariables.INSTANCE;
    @CommandBody
    @NotNull
    private static final SimpleCommandBody info = SimpleCommandKt.subCommand((Function1)info.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody save = SimpleCommandKt.subCommand((Function1)save.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody mirror = SimpleCommandKt.subCommand((Function1)mirror.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody reload = SimpleCommandKt.subCommand((Function1)reload.1.INSTANCE);

    private CommandChemdah() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final CommandChemdahAPI getApi() {
        return api;
    }

    @NotNull
    public final CommandChemdahPlayerData getData() {
        return data;
    }

    @NotNull
    public final CommandChemdahPlayerLevel getLevel() {
        return level;
    }

    @NotNull
    public final CommandChemdahQuest getQuest() {
        return quest;
    }

    @NotNull
    public final CommandChemdahScript getScript() {
        return script;
    }

    @NotNull
    public final CommandChemdahVariables getVariable() {
        return variable;
    }

    @NotNull
    public final SimpleCommandBody getInfo() {
        return info;
    }

    @NotNull
    public final SimpleCommandBody getSave() {
        return save;
    }

    @NotNull
    public final SimpleCommandBody getMirror() {
        return mirror;
    }

    @NotNull
    public final SimpleCommandBody getReload() {
        return reload;
    }

    /*
     * WARNING - void declaration
     */
    public final void commandInfo$Chemdah(@NotNull CommandSender sender, @NotNull Player player, int page) {
        String partyHook;
        Object object;
        Object[] $this$mapTo$iv$iv;
        Object[] $this$map$iv;
        boolean bl;
        void $this$filterTo$iv$iv;
        Collection collection;
        void $this$mapTo$iv$iv2;
        Object[] $this$map$iv2;
        boolean bl2;
        void $this$filterTo$iv$iv2;
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        CommandHelperKt.space(sender);
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-header", (Object[])new Object[0]);
        boolean show = false;
        boolean showVars = false;
        List<Quest> quests = ChemdahAPI.INSTANCE.getChemdahProfile(player).getQuests(true);
        Iterable $this$filter$iv = quests;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Iterable destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv2) {
            Quest quest2 = (Quest)element$iv$iv;
            bl2 = false;
            if (!quest2.isOwner(player)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        $this$filter$iv = (List)destination$iv$iv;
        boolean $i$f$map = false;
        $this$filterTo$iv$iv2 = $this$map$iv2;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv2, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv2) {
            Quest quest3 = (Quest)item$iv$iv;
            collection = destination$iv$iv;
            bl2 = false;
            collection.add(quest3.getId());
        }
        List questsGeneric = (List)destination$iv$iv;
        if (!((Collection)questsGeneric).isEmpty()) {
            show = true;
            $this$map$iv2 = new Object[]{questsGeneric.size(), questsGeneric};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-quest", (Object[])$this$map$iv2);
        }
        Iterable $this$filter$iv2 = quests;
        boolean $i$f$filter2 = false;
        destination$iv$iv = $this$filter$iv2;
        Collection destination$iv$iv2 = new ArrayList();
        boolean $i$f$filterTo2 = false;
        for (Object e : $this$filterTo$iv$iv) {
            Quest it2 = (Quest)e;
            bl = false;
            if (!(!it2.isOwner(player))) continue;
            destination$iv$iv2.add(e);
        }
        $this$filter$iv2 = (List)destination$iv$iv2;
        boolean $i$f$map222 = false;
        $this$filterTo$iv$iv = $this$map$iv;
        Iterable destination$iv$iv3 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo2 = false;
        for (Object e : $this$mapTo$iv$iv) {
            void it;
            Quest it2 = (Quest)e;
            collection = destination$iv$iv3;
            bl = false;
            collection.add(it.getId());
        }
        List questsShare = (List)destination$iv$iv3;
        if (!((Collection)questsShare).isEmpty()) {
            show = true;
            $this$map$iv = new Object[]{questsShare.size(), questsShare};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-quest-share", (Object[])$this$map$iv);
        }
        String string = (object = PartySystem.INSTANCE.getHook()) != null && (object = object.getClass()) != null ? ((Class)object).getName() : (partyHook = null);
        if (partyHook != null) {
            show = true;
            Object[] $i$f$map222 = new Object[]{partyHook};
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-party", (Object[])$i$f$map222);
            Party.PartyInfo party = PartySystem.INSTANCE.getParty(player);
            if (party != null) {
                Collection<String> collection2;
                void $this$mapTo$iv$iv3;
                void $this$map$iv3;
                Object item$iv$iv;
                $this$mapTo$iv$iv = new Object[]{party.isLeader(player)};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-party-leader", (Object[])$this$mapTo$iv$iv);
                $this$mapTo$iv$iv = new Object[1];
                destination$iv$iv3 = PartySystem.INSTANCE.getPartyMembers(player, true);
                int n = 0;
                Object[] objectArray = $this$mapTo$iv$iv;
                String string2 = "command-info-party-member";
                collection = sender;
                boolean $i$f$map3 = false;
                item$iv$iv = $this$map$iv3;
                Collection collection3 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv3, (int)10));
                boolean $i$f$mapTo3 = false;
                for (Object item$iv$iv2 : $this$mapTo$iv$iv3) {
                    void it3;
                    Player player2 = (Player)item$iv$iv2;
                    collection2 = collection3;
                    boolean bl3 = false;
                    collection2.add(it3.getName());
                }
                collection2 = (List)collection3;
                objectArray[n] = collection2;
                BukkitLangKt.sendLang((CommandSender)collection, (String)string2, (Object[])$this$mapTo$iv$iv);
            }
        }
        if (ChemdahAPI.INSTANCE.getChemdahProfile(player).getPersistentDataContainer().isNotEmpty()) {
            show = true;
            showVars = true;
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-data", (Object[])new Object[0]);
            Iterable $this$sortedBy$iv = ChemdahAPI.INSTANCE.getChemdahProfile(player).getPersistentDataContainer().entries();
            boolean $i$f$sortedBy = false;
            List vars2 = CollectionsKt.sortedWith((Iterable)$this$sortedBy$iv, (Comparator)new Comparator(){

                public final int compare(T a, T b) {
                    Map.Entry it = (Map.Entry)a;
                    boolean bl = false;
                    Comparable comparable = (Comparable)((Object)((String)it.getKey()));
                    it = (Map.Entry)b;
                    Comparable comparable2 = comparable;
                    bl = false;
                    return ComparisonsKt.compareValues((Comparable)comparable2, (Comparable)((Comparable)((Object)((String)it.getKey()))));
                }
            });
            Iterable $this$forEach$iv = CollectionKt.subList((List)vars2, (int)(page * 12), (int)((page + 1) * 12));
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Map.Entry entry = (Map.Entry)element$iv;
                boolean bl4 = false;
                Object[] objectArray = new Object[]{"    \u00a77" + StringsKt.replace$default((String)((String)entry.getKey()), (String)".", (String)"\u00a7f.\u00a77", (boolean)false, (int)4, null) + " \u00a78= \u00a7f" + ((Data)entry.getValue()).getData()};
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-body", (Object[])objectArray);
            }
        }
        if (!show) {
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-empty", (Object[])new Object[0]);
        }
        if (showVars) {
            Set<Map.Entry<String, Data>> vars3 = ChemdahAPI.INSTANCE.getChemdahProfile(player).getPersistentDataContainer().entries();
            int max2 = (int)Math.ceil((double)vars3.size() / 12.0);
            if (page == 0) {
                if (max2 > 1) {
                    Object[] objectArray = new Object[4];
                    objectArray[0] = 1;
                    objectArray[1] = max2;
                    Intrinsics.checkNotNullExpressionValue((Object)player.getName(), (String)"player.name");
                    objectArray[3] = 2;
                    BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-bottom-0", (Object[])objectArray);
                }
            } else if (page + 1 == max2) {
                Object[] objectArray = new Object[4];
                objectArray[0] = page + 1;
                objectArray[1] = max2;
                Intrinsics.checkNotNullExpressionValue((Object)player.getName(), (String)"player.name");
                objectArray[3] = page;
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-bottom-1", (Object[])objectArray);
            } else {
                Object[] objectArray = new Object[5];
                objectArray[0] = page + 1;
                objectArray[1] = max2;
                Intrinsics.checkNotNullExpressionValue((Object)player.getName(), (String)"player.name");
                objectArray[3] = page;
                objectArray[4] = page + 2;
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-info-bottom-2", (Object[])objectArray);
            }
        }
    }

    public static /* synthetic */ void commandInfo$Chemdah$default(CommandChemdah commandChemdah, CommandSender commandSender, Player player, int n, int n2, Object object) {
        if ((n2 & 4) != 0) {
            n = 0;
        }
        commandChemdah.commandInfo$Chemdah(commandSender, player, n);
    }
}

