/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.module.configuration.Config
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.party;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.PartyHookEvent;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.api.event.plugin.CollectEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.addon.AddonParty;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.party.Party;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0092\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\"\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0013\u001a\u00020\u0014H\u0003J\u0010\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0017H\u0003J\u0010\u0010\u0018\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0019H\u0003J\u0010\u0010\u001a\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u001bH\u0003J\u0010\u0010\u001c\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u001dH\u0003J\u0010\u0010\u001e\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u001fH\u0003J\u0010\u0010 \u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020!H\u0003J\u0010\u0010\"\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020#H\u0003J\u0010\u0010$\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020%H\u0003J\u0010\u0010&\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020'H\u0003J\b\u0010(\u001a\u00020\u0014H\u0016J&\u0010)\u001a\u00020\u00142\f\u0010*\u001a\b\u0012\u0004\u0012\u00020,0+2\u0006\u0010-\u001a\u00020.2\b\b\u0002\u0010/\u001a\u000200J\u001a\u00101\u001a\b\u0012\u0004\u0012\u00020.02*\u00020,2\b\b\u0002\u00103\u001a\u000200J\f\u00104\u001a\u0004\u0018\u000105*\u00020.J\u001a\u00106\u001a\b\u0012\u0004\u0012\u00020.02*\u00020.2\b\b\u0002\u00103\u001a\u000200R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001e\u0010\b\u001a\u0004\u0018\u00010\t8FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u00020\u0010\u0012\u0004\u0012\u00020\t0\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00067"}, d2={"Link/ptms/chemdah/module/party/PartySystem;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "hook", "Link/ptms/chemdah/module/party/Party;", "getHook", "()Link/ptms/chemdah/module/party/Party;", "setHook", "(Link/ptms/chemdah/module/party/Party;)V", "hooks", "Ljava/util/concurrent/ConcurrentHashMap;", "", "retry", "", "onEnable", "", "onObjectEventsCompilePost", "e", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Post;", "onObjectiveEventsCompilePre", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Pre;", "onObjectiveEventsContinuePost", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Post;", "onObjectiveEventsContinuePre", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Pre;", "onObjectiveEventsRestartPost", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Restart$Post;", "onQuestEventsAcceptPost", "Link/ptms/chemdah/api/event/collect/QuestEvents$Accept$Post;", "onQuestEventsCompilePost", "Link/ptms/chemdah/api/event/collect/QuestEvents$Complete$Post;", "onQuestEventsFailPost", "Link/ptms/chemdah/api/event/collect/QuestEvents$Fail$Post;", "onQuestEventsRestartPost", "Link/ptms/chemdah/api/event/collect/QuestEvents$Restart$Post;", "reload", "shareQuests", "quests", "", "Link/ptms/chemdah/core/quest/Quest;", "sharer", "Lorg/bukkit/entity/Player;", "isLeader", "", "getMembers", "", "self", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "getPartyMembers", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPartySystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PartySystem.kt\nink/ptms/chemdah/module/party/PartySystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,216:1\n766#2:217\n857#2,2:218\n1855#2,2:220\n1855#2,2:222\n1855#2,2:224\n1855#2,2:226\n1855#2,2:228\n1855#2,2:230\n1855#2,2:232\n1855#2,2:234\n*S KotlinDebug\n*F\n+ 1 PartySystem.kt\nink/ptms/chemdah/module/party/PartySystem\n*L\n81#1:217\n81#1:218,2\n119#1:220,2\n150#1:222,2\n157#1:224,2\n164#1:226,2\n171#1:228,2\n178#1:230,2\n185#1:232,2\n192#1:234,2\n*E\n"})
public final class PartySystem
implements Module {
    @NotNull
    public static final PartySystem INSTANCE = new PartySystem();
    @Config(value="module/party.yml")
    private static Configuration conf;
    @NotNull
    private static final ConcurrentHashMap<String, Party> hooks;
    private static int retry;
    @Nullable
    private static Party hook;

    private PartySystem() {
    }

    @NotNull
    public final Configuration getConf() {
        Configuration configuration = conf;
        if (configuration != null) {
            return configuration;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"conf");
        return null;
    }

    @Nullable
    public final Party getHook() {
        if (hook != null || retry > 10) {
            return hook;
        }
        int n = retry;
        retry = n + 1;
        String string = this.getConf().getString("default.plugin", "");
        Intrinsics.checkNotNull((Object)string);
        String id2 = string;
        if (hooks.containsKey(id2)) {
            hook = hooks.get(id2);
            return hooks.get(id2);
        }
        PartyHookEvent $this$_get_hook__u24lambda_u240 = new PartyHookEvent(id2);
        boolean bl = false;
        $this$_get_hook__u24lambda_u240.call();
        Party party = $this$_get_hook__u24lambda_u240.getParty();
        if (party != null) {
            hook = party;
            ((Map)hooks).put(id2, party);
        }
        return party;
    }

    public final void setHook(@Nullable Party party) {
        hook = party;
    }

    @Override
    public void reload() {
        this.getConf().reload();
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final Set<Player> getMembers(@NotNull Quest $this$getMembers, boolean self) {
        void $this$filterTo$iv$iv;
        Player player2;
        HashSet<Player> members;
        block5: {
            block6: {
                AddonParty partyAddon;
                Intrinsics.checkNotNullParameter((Object)$this$getMembers, (String)"<this>");
                members = new HashSet<Player>();
                player2 = $this$getMembers.getProfile().getPlayer();
                AddonParty addonParty = partyAddon = AddonParty.Companion.party($this$getMembers.getTemplate());
                boolean bl = addonParty != null ? addonParty.getShare() : false;
                if (!bl) break block5;
                if (!partyAddon.getShareOnlyLeader()) break block6;
                Party.PartyInfo partyInfo = this.getParty(player2);
                boolean bl2 = partyInfo != null ? partyInfo.isLeader(player2) : false;
                if (!bl2) break block5;
            }
            members.addAll(PartySystem.getPartyMembers$default(this, player2, false, 1, null));
        }
        if (self) {
            members.add(player2);
        }
        Iterable $this$filter$iv = members;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Player it = (Player)element$iv$iv;
            boolean bl = false;
            if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        return CollectionsKt.toSet((Iterable)((List)destination$iv$iv));
    }

    public static /* synthetic */ Set getMembers$default(PartySystem partySystem, Quest quest2, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = false;
        }
        return partySystem.getMembers(quest2, bl);
    }

    @Nullable
    public final Party.PartyInfo getParty(@NotNull Player $this$getParty) {
        Intrinsics.checkNotNullParameter((Object)$this$getParty, (String)"<this>");
        Party party = this.getHook();
        return party != null ? party.getParty($this$getParty) : null;
    }

    @NotNull
    public final Set<Player> getPartyMembers(@NotNull Player $this$getPartyMembers, boolean self) {
        Intrinsics.checkNotNullParameter((Object)$this$getPartyMembers, (String)"<this>");
        HashSet<Player> members = new HashSet<Player>();
        Party.PartyInfo team = this.getParty($this$getPartyMembers);
        if (team != null) {
            members.addAll((Collection)team.getMembers());
            Player leader = team.getLeader();
            if (leader != null && members.contains(leader)) {
                members.add(leader);
            }
            if (!self) {
                members.remove($this$getPartyMembers);
            }
        }
        return members;
    }

    public static /* synthetic */ Set getPartyMembers$default(PartySystem partySystem, Player player2, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = false;
        }
        return partySystem.getPartyMembers(player2, bl);
    }

    public final void shareQuests(@NotNull List<Quest> quests, @NotNull Player sharer, boolean isLeader) {
        Intrinsics.checkNotNullParameter(quests, (String)"quests");
        Intrinsics.checkNotNullParameter((Object)sharer, (String)"sharer");
        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(sharer)) {
            Iterable $this$forEach$iv = PlayerProfile.getQuests$default(ChemdahAPI.INSTANCE.getChemdahProfile(sharer), false, 1, null);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                AddonParty partyAddon;
                Quest quest2 = (Quest)element$iv;
                boolean bl = false;
                if (AddonParty.Companion.party(quest2.getTemplate()) == null || !partyAddon.getShare() || partyAddon.getShareOnlyLeader() && !isLeader) continue;
                quests.add(quest2);
            }
        }
    }

    public static /* synthetic */ void shareQuests$default(PartySystem partySystem, List list2, Player player2, boolean bl, int n, Object object) {
        if ((n & 4) != 0) {
            bl = false;
        }
        partySystem.shareQuests(list2, player2, bl);
    }

    @Awake(value=LifeCycle.ENABLE)
    private final void onEnable() {
        ChemdahAPI.INSTANCE.getEventFactory().prepareQuestCollect(new CollectEvent(){

            /*
             * WARNING - void declaration
             */
            public void invoke(@NotNull PlayerProfile profile, @NotNull List<Quest> quests) {
                void $this$forEach$iv;
                void $this$filterTo$iv$iv;
                Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
                Intrinsics.checkNotNullParameter(quests, (String)"quests");
                Party.PartyInfo partyInfo = PartySystem.INSTANCE.getParty(profile.getPlayer());
                if (partyInfo == null) {
                    return;
                }
                Party.PartyInfo team = partyInfo;
                Player leader = team.getLeader();
                if (leader != null && !Intrinsics.areEqual((Object)leader.getUniqueId(), (Object)profile.getUniqueId())) {
                    PartySystem.INSTANCE.shareQuests(quests, leader, true);
                }
                Iterable $this$filter$iv = team.getMembers();
                boolean $i$f$filter = false;
                Iterable iterable = $this$filter$iv;
                Collection destination$iv$iv = new ArrayList<E>();
                boolean $i$f$filterTo = false;
                for (T element$iv$iv : $this$filterTo$iv$iv) {
                    Player it = (Player)element$iv$iv;
                    boolean bl = false;
                    Player player2 = leader;
                    if (!(!Intrinsics.areEqual((Object)it.getName(), (Object)(player2 != null ? player2.getName() : null)) && !Intrinsics.areEqual((Object)it.getUniqueId(), (Object)profile.getUniqueId()))) continue;
                    destination$iv$iv.add(element$iv$iv);
                }
                $this$filter$iv = (List)destination$iv$iv;
                boolean $i$f$forEach = false;
                for (E element$iv : $this$forEach$iv) {
                    Player it = (Player)element$iv;
                    boolean bl = false;
                    PartySystem.shareQuests$default(PartySystem.INSTANCE, quests, it, false, 4, null);
                }
            }
        });
    }

    @SubscribeEvent
    private final void onQuestEventsFailPost(QuestEvents.Fail.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getQuest().getTemplate(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.QUEST_FAILED, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onQuestEventsRestartPost(QuestEvents.Restart.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getQuest().getTemplate(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.QUEST_RESTART, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onQuestEventsAcceptPost(QuestEvents.Accept.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getQuest().getTemplate(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.QUEST_ACCEPTED, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onQuestEventsCompilePost(QuestEvents.Complete.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getQuest().getTemplate(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.QUEST_COMPLETED, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onObjectiveEventsRestartPost(ObjectiveEvents.Restart.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getTask(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.TASK_RESTARTED, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onObjectiveEventsContinuePost(ObjectiveEvents.Continue.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getTask(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.TASK_CONTINUED, "party", null, 8, null);
        }
    }

    @SubscribeEvent
    private final void onObjectEventsCompilePost(ObjectiveEvents.Complete.Post e) {
        Iterable $this$forEach$iv = PartySystem.getMembers$default(this, e.getQuest(), false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player member = (Player)element$iv;
            boolean bl = false;
            QuestContainer.agent$default(e.getTask(), ChemdahAPI.INSTANCE.getChemdahProfile(member), AgentType.TASK_COMPLETED, "party", null, 8, null);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    private final void onObjectiveEventsContinuePre(ObjectiveEvents.Continue.Pre e) {
        int requireMembers;
        int n;
        if (!e.getQuest().isOwner(e.getPlayerProfile().getPlayer())) {
            AddonParty addonParty = AddonParty.Companion.party(e.getQuest().getTemplate());
            boolean bl = addonParty != null ? !addonParty.getCanContinue() : false;
            if (bl) {
                AddonParty addonParty2 = AddonParty.Companion.party(e.getTask());
                boolean bl2 = addonParty2 != null ? !addonParty2.getCanContinue() : false;
                if (bl2) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        AddonParty addonParty = AddonParty.Companion.party(e.getTask());
        if (addonParty != null) {
            n = addonParty.getRequireMembers();
        } else {
            AddonParty addonParty3 = AddonParty.Companion.party(e.getQuest().getTemplate());
            n = requireMembers = addonParty3 != null ? addonParty3.getRequireMembers() : 0;
        }
        if (requireMembers > 0 && requireMembers > PartySystem.getMembers$default(this, e.getQuest(), false, 1, null).size()) {
            e.setCancelled(true);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    private final void onObjectiveEventsCompilePre(ObjectiveEvents.Complete.Pre e) {
        int requireMembers;
        int n;
        AddonParty addonParty = AddonParty.Companion.party(e.getTask());
        if (addonParty != null) {
            n = addonParty.getRequireMembers();
        } else {
            AddonParty addonParty2 = AddonParty.Companion.party(e.getQuest().getTemplate());
            n = requireMembers = addonParty2 != null ? addonParty2.getRequireMembers() : 0;
        }
        if (requireMembers > 0 && requireMembers > PartySystem.getMembers$default(this, e.getQuest(), false, 1, null).size()) {
            e.setCancelled(true);
        }
    }

    static {
        hooks = new ConcurrentHashMap();
        Module.Companion.register(INSTANCE);
    }
}

