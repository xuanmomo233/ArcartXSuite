/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.party;

import cn.mcres.iTeamPro.manager.TeamInfo;
import cn.mcres.iTeamPro.manager.TeamManager;
import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.github.Shawhoi.nyteam.NyTeam;
import com.gmail.nossr50.party.PartyManager;
import com.pxpmc.team.TeamMain;
import com.pxpmc.team.api.Team;
import de.HyChrod.Party.Caching.Partydata;
import de.HyChrod.Party.Utilities.PartyAPI;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.api.player.PlayerGroup;
import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayer;
import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayerManager;
import de.simonsator.partyandfriends.spigot.api.party.PlayerParty;
import fw.teams.Fwteam;
import ink.ptms.chemdah.api.event.PartyHookEvent;
import ink.ptms.chemdah.module.party.Party;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.party.AbstractParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.serverct.ersha.dungeon.DungeonPlus;
import org.serverct.ersha.dungeon.common.team.type.PlayerStateType;
import sky_bai.bukkit.baiteam.BaiTeam;
import su.nightexpress.quantumrpg.api.QuantumAPI;
import su.nightexpress.quantumrpg.modules.list.party.PartyManager;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\b\u00c6\u0002\u0018\u00002\u00020\u0001:\r\u0007\b\t\n\u000b\f\r\u000e\u000f\u0010\u0011\u0012\u0013B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0003\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/module/party/PartyHook;", "", "()V", "onPartyHook", "", "e", "Link/ptms/chemdah/api/event/PartyHookEvent;", "BaiTeamHook", "CustomGoHook", "DungeonPlusHook", "DungeonXLHook", "FriendsPremiumHook", "ITeamProHook", "MMOCoreHook", "McMMOHook", "NyTeamHook", "PartiesHook", "PartyAndFriendsHook", "PxTeamHook", "QuantumHook", "Chemdah"})
public final class PartyHook {
    @NotNull
    public static final PartyHook INSTANCE = new PartyHook();

    private PartyHook() {
    }

    @SubscribeEvent
    private final void onPartyHook(PartyHookEvent e) {
        Party party;
        switch (e.getPlugin()) {
            case "BaiTeam": {
                party = BaiTeamHook.INSTANCE;
                break;
            }
            case "CustomGo": {
                party = CustomGoHook.INSTANCE;
                break;
            }
            case "DungeonPlus": {
                party = DungeonPlusHook.INSTANCE;
                break;
            }
            case "DungeonXL": {
                party = DungeonXLHook.INSTANCE;
                break;
            }
            case "FriendsPremium": {
                party = FriendsPremiumHook.INSTANCE;
                break;
            }
            case "iTeamPro": {
                party = ITeamProHook.INSTANCE;
                break;
            }
            case "mcMMO": {
                party = McMMOHook.INSTANCE;
                break;
            }
            case "MMOCore": {
                party = MMOCoreHook.INSTANCE;
                break;
            }
            case "NyTeam": {
                party = NyTeamHook.INSTANCE;
                break;
            }
            case "PxTeam": {
                party = PxTeamHook.INSTANCE;
                break;
            }
            case "Parties": {
                party = PartiesHook.INSTANCE;
                break;
            }
            case "PartyAndFriends": {
                party = PartyAndFriendsHook.INSTANCE;
                break;
            }
            case "PRORPG": 
            case "QuantumRPG": {
                party = QuantumHook.INSTANCE;
                break;
            }
            default: {
                return;
            }
        }
        e.setParty(party);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$BaiTeamHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nPartyHook.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$BaiTeamHook\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,304:1\n288#2,2:305\n*S KotlinDebug\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$BaiTeamHook\n*L\n57#1:305,2\n*E\n"})
    public static final class BaiTeamHook
    implements Party {
        @NotNull
        public static final BaiTeamHook INSTANCE = new BaiTeamHook();

        private BaiTeamHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Object v1;
            block2: {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Set set2 = BaiTeam.getTeamManager().getTeams();
                Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"getTeamManager().teams");
                Iterable $this$firstOrNull$iv = set2;
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    sky_bai.bukkit.baiteam.team.Team it = (sky_bai.bukkit.baiteam.team.Team)element$iv;
                    boolean bl = false;
                    if (!(Intrinsics.areEqual((Object)it.getLeaderName(), (Object)player.getName()) || it.getMemberNames().contains(player.getName()))) continue;
                    v1 = element$iv;
                    break block2;
                }
                v1 = null;
            }
            sky_bai.bukkit.baiteam.team.Team team = v1;
            if (team == null) {
                return null;
            }
            sky_bai.bukkit.baiteam.team.Team team2 = team;
            return new Party.PartyInfo(team2){
                final /* synthetic */ sky_bai.bukkit.baiteam.team.Team $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return this.$team.getLeader();
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    Set set2 = this.$team.getMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"team.members");
                    Iterable iterable = set2;
                    sky_bai.bukkit.baiteam.team.Team team = this.$team;
                    boolean $i$f$filter = false;
                    void var4_4 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Player it = (Player)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)team.getLeader().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    return CollectionsKt.toList((Iterable)((List)destination$iv$iv));
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$CustomGoHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nPartyHook.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$CustomGoHook\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,304:1\n288#2,2:305\n*S KotlinDebug\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$CustomGoHook\n*L\n75#1:305,2\n*E\n"})
    public static final class CustomGoHook
    implements Party {
        @NotNull
        public static final CustomGoHook INSTANCE = new CustomGoHook();

        private CustomGoHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Object v2;
            block2: {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, Fwteam.class, (String)"teamlist", (boolean)true, (boolean)false, (boolean)false, null, (int)28, null);
                Intrinsics.checkNotNull((Object)object);
                Set teams = (Set)object;
                Iterable $this$firstOrNull$iv = teams;
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    Fwteam it = (Fwteam)element$iv;
                    boolean bl = false;
                    Object object2 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)it, (String)"plist", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object2);
                    if (!(((Set)object2).contains(player.getUniqueId()) || Intrinsics.areEqual((Object)player.getUniqueId(), (Object)Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)it, (String)"leader", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null)))) continue;
                    v2 = element$iv;
                    break block2;
                }
                v2 = null;
            }
            Fwteam fwteam = v2;
            if (fwteam == null) {
                return null;
            }
            Fwteam team = fwteam;
            return new Party.PartyInfo(team){
                final /* synthetic */ Fwteam $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)this.$team, (String)"leader", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object);
                    return Bukkit.getPlayer((UUID)((UUID)object));
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)this.$team, (String)"plist", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object);
                    Iterable iterable = (Iterable)object;
                    Fwteam fwteam = this.$team;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        UUID it = (UUID)element$iv$iv;
                        boolean bl = false;
                        Object object2 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)fwteam, (String)"leader", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                        Intrinsics.checkNotNull((Object)object2);
                        if (!(!Intrinsics.areEqual((Object)it, (Object)object2))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    Iterable $i$f$filter22 = $this$mapNotNull$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        UUID it = (UUID)element$iv$iv;
                        boolean bl2 = false;
                        if (Bukkit.getPlayer((UUID)it) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv2.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$DungeonPlusHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class DungeonPlusHook
    implements Party {
        @NotNull
        public static final DungeonPlusHook INSTANCE = new DungeonPlusHook();

        private DungeonPlusHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Party.PartyInfo partyInfo;
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            try {
                org.serverct.ersha.dungeon.common.team.Team team = DungeonPlus.INSTANCE.getTeamManager().getTeam(player);
                if (team == null) {
                    return null;
                }
                org.serverct.ersha.dungeon.common.team.Team team2 = team;
                partyInfo = new Party.PartyInfo(team2){
                    final /* synthetic */ org.serverct.ersha.dungeon.common.team.Team $team;
                    {
                        this.$team = $team;
                    }

                    @NotNull
                    public Player getLeader() {
                        return this.$team.getLeader();
                    }

                    /*
                     * WARNING - void declaration
                     */
                    @NotNull
                    public List<Player> getMembers() {
                        void $this$filterTo$iv$iv;
                        void $this$filter$iv;
                        Iterable iterable = this.$team.getPlayers(PlayerStateType.ALL);
                        org.serverct.ersha.dungeon.common.team.Team team = this.$team;
                        boolean $i$f$filter = false;
                        void var4_4 = $this$filter$iv;
                        Collection destination$iv$iv = new ArrayList<E>();
                        boolean $i$f$filterTo = false;
                        for (T element$iv$iv : $this$filterTo$iv$iv) {
                            Player it = (Player)element$iv$iv;
                            boolean bl = false;
                            if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)team.getLeader()))) continue;
                            destination$iv$iv.add(element$iv$iv);
                        }
                        return (List)destination$iv$iv;
                    }
                };
            }
            catch (Error ex) {
                throw new IllegalStateException("Outdated DungeonPlus (required: >1.1.3)".toString());
            }
            return partyInfo;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$DungeonXLHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class DungeonXLHook
    implements Party {
        @NotNull
        public static final DungeonXLHook INSTANCE = new DungeonXLHook();

        private DungeonXLHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            PlayerGroup playerGroup = DungeonsXL.getInstance().getPlayerGroup(player);
            if (playerGroup == null) {
                return null;
            }
            PlayerGroup team = playerGroup;
            return new Party.PartyInfo(team){
                final /* synthetic */ PlayerGroup $team;
                {
                    this.$team = $team;
                }

                @NotNull
                public Player getLeader() {
                    Player player = this.$team.getLeader();
                    Intrinsics.checkNotNullExpressionValue((Object)player, (String)"team.leader");
                    return player;
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    Collection collection = this.$team.getMembers().getOnlinePlayers();
                    Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"team.members.onlinePlayers");
                    Iterable iterable = collection;
                    PlayerGroup playerGroup = this.$team;
                    boolean $i$f$filter = false;
                    void var4_4 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Player it = (Player)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)playerGroup.getLeader().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    return (List)destination$iv$iv;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$FriendsPremiumHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class FriendsPremiumHook
    implements Party {
        @NotNull
        public static final FriendsPremiumHook INSTANCE = new FriendsPremiumHook();

        private FriendsPremiumHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Partydata partydata = PartyAPI.getParty((UUID)player.getUniqueId());
            if (partydata == null) {
                return null;
            }
            Partydata team = partydata;
            return new Party.PartyInfo(team){
                final /* synthetic */ Partydata $team;
                {
                    this.$team = $team;
                }

                /*
                 * WARNING - void declaration
                 */
                @Nullable
                public Player getLeader() {
                    Object v1;
                    block2: {
                        void $this$firstOrNull$iv;
                        List list2 = this.$team.getAll();
                        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.all");
                        Iterable iterable = list2;
                        Partydata partydata = this.$team;
                        boolean $i$f$firstOrNull = false;
                        for (T element$iv : $this$firstOrNull$iv) {
                            Partydata.Member it = (Partydata.Member)element$iv;
                            boolean bl = false;
                            if (!partydata.isLeader(it)) continue;
                            v1 = element$iv;
                            break block2;
                        }
                        v1 = null;
                    }
                    Partydata.Member member = v1;
                    UUID uUID = member != null ? member.getUuid() : null;
                    if (uUID == null) {
                        return null;
                    }
                    return Bukkit.getPlayer((UUID)uUID);
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$team.getAll();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.all");
                    Iterable iterable = list2;
                    Partydata partydata = this.$team;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Partydata.Member it = (Partydata.Member)element$iv$iv;
                        boolean bl = false;
                        if (!(!partydata.isLeader(it))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    Iterable $i$f$filter22 = $this$mapNotNull$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        Partydata.Member it = (Partydata.Member)element$iv$iv;
                        boolean bl2 = false;
                        if (Bukkit.getPlayer((UUID)it.getUuid()) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv2.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$ITeamProHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class ITeamProHook
    implements Party {
        @NotNull
        public static final ITeamProHook INSTANCE = new ITeamProHook();

        private ITeamProHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            TeamInfo teamInfo = TeamManager.getTeam((Player)player);
            if (teamInfo == null) {
                return null;
            }
            TeamInfo team = teamInfo;
            return new Party.PartyInfo(team){
                final /* synthetic */ TeamInfo $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return this.$team.getLeader();
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$team.getMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.members");
                    Iterable iterable = list2;
                    TeamInfo teamInfo = this.$team;
                    boolean $i$f$filter = false;
                    void var4_4 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Player it = (Player)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)teamInfo.getLeader().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    return (List)destination$iv$iv;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$MMOCoreHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nPartyHook.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$MMOCoreHook\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,304:1\n1#2:305\n*E\n"})
    public static final class MMOCoreHook
    implements Party {
        @NotNull
        public static final MMOCoreHook INSTANCE = new MMOCoreHook();

        private MMOCoreHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Object[] $i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22;
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            try {
                boolean $i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22 = false;
                $i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22 = Result.constructor-impl((Object)MMOCore.plugin.dataProvider);
            }
            catch (Throwable throwable) {
                $i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
            }
            if (Result.isFailure-impl((Object)$i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22)) {
                $i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22 = new Object[]{"Outdated MMOCore (required: >1.10.X)"};
                IOKt.severe((Object[])$i$a$-runCatching-PartyHook$MMOCoreHook$getParty$22);
                return null;
            }
            PlayerData playerData = MMOCore.plugin.dataProvider.getDataManager().get((OfflinePlayer)player);
            if (playerData == null) {
                return null;
            }
            PlayerData data2 = playerData;
            AbstractParty abstractParty = MMOCore.plugin.partyModule.getParty(data2);
            net.Indyuce.mmocore.party.provided.Party party = abstractParty instanceof net.Indyuce.mmocore.party.provided.Party ? (net.Indyuce.mmocore.party.provided.Party)abstractParty : null;
            if (party == null) {
                return null;
            }
            net.Indyuce.mmocore.party.provided.Party party2 = party;
            return new Party.PartyInfo(party2){
                final /* synthetic */ net.Indyuce.mmocore.party.provided.Party $party;
                {
                    this.$party = $party;
                }

                @Nullable
                public Player getLeader() {
                    return this.$party.getOwner().getPlayer();
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$party.getMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"party.members");
                    Iterable iterable = list2;
                    net.Indyuce.mmocore.party.provided.Party party = this.$party;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (Object element$iv$iv : $this$filterTo$iv$iv) {
                        PlayerData it = (PlayerData)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)party.getOwner().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$map$iv = (List)destination$iv$iv;
                    boolean $i$f$map = false;
                    Iterable $i$f$filter22 = $this$map$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (T item$iv$iv : $this$mapTo$iv$iv) {
                        void it;
                        Object element$iv$iv;
                        element$iv$iv = (PlayerData)item$iv$iv;
                        Collection collection = destination$iv$iv2;
                        boolean bl = false;
                        collection.add(it.getPlayer());
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$McMMOHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class McMMOHook
    implements Party {
        @NotNull
        public static final McMMOHook INSTANCE = new McMMOHook();

        private McMMOHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            com.gmail.nossr50.datatypes.party.Party party = PartyManager.getParty((Player)player);
            if (party == null) {
                return null;
            }
            com.gmail.nossr50.datatypes.party.Party team = party;
            return new Party.PartyInfo(team){
                final /* synthetic */ com.gmail.nossr50.datatypes.party.Party $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return Bukkit.getPlayer((UUID)this.$team.getLeader().getUniqueId());
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$team.getOnlineMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.onlineMembers");
                    Iterable iterable = list2;
                    com.gmail.nossr50.datatypes.party.Party party = this.$team;
                    boolean $i$f$filter = false;
                    void var4_4 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Player it = (Player)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)party.getLeader().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    return (List)destination$iv$iv;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$NyTeamHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class NyTeamHook
    implements Party {
        @NotNull
        public static final NyTeamHook INSTANCE = new NyTeamHook();

        private NyTeamHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            com.github.Shawhoi.nyteam.data.TeamInfo teamInfo = NyTeam.getNyTeamAPI().getPlayerTeam(player.getName());
            if (teamInfo == null) {
                return null;
            }
            com.github.Shawhoi.nyteam.data.TeamInfo team = teamInfo;
            return new Party.PartyInfo(team){
                final /* synthetic */ com.github.Shawhoi.nyteam.data.TeamInfo $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return Bukkit.getPlayerExact((String)this.$team.getTeamCaptain());
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    ArrayList arrayList = this.$team.getTeamMate();
                    Intrinsics.checkNotNullExpressionValue((Object)arrayList, (String)"team.teamMate");
                    Iterable iterable = arrayList;
                    com.github.Shawhoi.nyteam.data.TeamInfo teamInfo = this.$team;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        String it = (String)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it, (Object)teamInfo.getTeamCaptain()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    Iterable $i$f$filter22 = $this$mapNotNull$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        String it = (String)element$iv$iv;
                        boolean bl2 = false;
                        if (Bukkit.getPlayerExact((String)it) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv2.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$PartiesHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class PartiesHook
    implements Party {
        @NotNull
        public static final PartiesHook INSTANCE = new PartiesHook();

        private PartiesHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Object object = Parties.getApi().getPartyPlayer(player.getUniqueId());
            if (object == null || (object = object.getPartyId()) == null) {
                return null;
            }
            Object id2 = object;
            com.alessiodp.parties.api.interfaces.Party party = Parties.getApi().getParty((UUID)id2);
            if (party == null) {
                return null;
            }
            com.alessiodp.parties.api.interfaces.Party team = party;
            return new Party.PartyInfo(team){
                final /* synthetic */ com.alessiodp.parties.api.interfaces.Party $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    UUID uUID = this.$team.getLeader();
                    if (uUID == null) {
                        return null;
                    }
                    return Bukkit.getPlayer((UUID)uUID);
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    Set set2 = this.$team.getOnlineMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"team.onlineMembers");
                    Iterable iterable = set2;
                    com.alessiodp.parties.api.interfaces.Party party = this.$team;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        PartyPlayer it = (PartyPlayer)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)party.getLeader(), (Object)it.getPlayerUUID()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    Iterable $i$f$filter22 = $this$mapNotNull$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        PartyPlayer it = (PartyPlayer)element$iv$iv;
                        boolean bl2 = false;
                        if (Bukkit.getPlayer((UUID)it.getPlayerUUID()) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv2.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$PartyAndFriendsHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class PartyAndFriendsHook
    implements Party {
        @NotNull
        public static final PartyAndFriendsHook INSTANCE = new PartyAndFriendsHook();

        private PartyAndFriendsHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            PAFPlayer p = PAFPlayerManager.getInstance().getPlayer(player.getUniqueId());
            PlayerParty playerParty = de.simonsator.partyandfriends.spigot.api.party.PartyManager.getInstance().getParty(p);
            if (playerParty == null) {
                return null;
            }
            PlayerParty team = playerParty;
            return new Party.PartyInfo(team){
                final /* synthetic */ PlayerParty $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return Bukkit.getPlayer((UUID)this.$team.getLeader().getUniqueId());
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$team.getAllPlayers();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.allPlayers");
                    Iterable iterable = list2;
                    PlayerParty playerParty = this.$team;
                    boolean $i$f$filter22 = false;
                    void var4_6 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        PAFPlayer it = (PAFPlayer)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)playerParty.getLeader().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    Iterable $i$f$filter22 = $this$mapNotNull$iv;
                    Collection destination$iv$iv2 = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        PAFPlayer it = (PAFPlayer)element$iv$iv;
                        boolean bl2 = false;
                        if (Bukkit.getPlayer((UUID)it.getUniqueId()) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv2.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv2;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$PxTeamHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class PxTeamHook
    implements Party {
        @NotNull
        public static final PxTeamHook INSTANCE = new PxTeamHook();

        private PxTeamHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Team team = TeamMain.getTeamAPI().getTeam(player);
            if (team == null) {
                return null;
            }
            Team team2 = team;
            return new Party.PartyInfo(team2){
                final /* synthetic */ Team $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    return this.$team.getCaptain();
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$filterTo$iv$iv;
                    void $this$filter$iv;
                    List list2 = this.$team.getTeamList();
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"team.teamList");
                    Iterable iterable = list2;
                    Team team = this.$team;
                    boolean $i$f$filter = false;
                    void var4_4 = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        Player it = (Player)element$iv$iv;
                        boolean bl = false;
                        if (!(!Intrinsics.areEqual((Object)it.getUniqueId(), (Object)team.getCaptain().getUniqueId()))) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    return (List)destination$iv$iv;
                }
            };
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/party/PartyHook$QuantumHook;", "Link/ptms/chemdah/module/party/Party;", "()V", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nPartyHook.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$QuantumHook\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,304:1\n288#2,2:305\n*S KotlinDebug\n*F\n+ 1 PartyHook.kt\nink/ptms/chemdah/module/party/PartyHook$QuantumHook\n*L\n181#1:305,2\n*E\n"})
    public static final class QuantumHook
    implements Party {
        @NotNull
        public static final QuantumHook INSTANCE = new QuantumHook();

        private QuantumHook() {
        }

        @Override
        @Nullable
        public Party.PartyInfo getParty(@NotNull Player player) {
            Object v2;
            block2: {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                su.nightexpress.quantumrpg.modules.list.party.PartyManager partyManager = QuantumAPI.getModuleManager().getPartyManager();
                Intrinsics.checkNotNullExpressionValue((Object)partyManager, (String)"getModuleManager().partyManager");
                Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)partyManager, (String)"parties", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                Intrinsics.checkNotNull((Object)object);
                Map teams = (Map)object;
                Iterable $this$firstOrNull$iv = teams.values();
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    PartyManager.Party it = (PartyManager.Party)element$iv;
                    boolean bl = false;
                    if (!it.isMember(player)) continue;
                    v2 = element$iv;
                    break block2;
                }
                v2 = null;
            }
            PartyManager.Party party = v2;
            if (party == null) {
                return null;
            }
            PartyManager.Party team = party;
            return new Party.PartyInfo(team){
                final /* synthetic */ PartyManager.Party $team;
                {
                    this.$team = $team;
                }

                @Nullable
                public Player getLeader() {
                    PartyManager.PartyMember partyMember = this.$team.getLeader();
                    return partyMember != null ? partyMember.getPlayer() : null;
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public List<Player> getMembers() {
                    void $this$mapNotNullTo$iv$iv;
                    Iterable $this$filterTo$iv$iv;
                    Collection collection = this.$team.getMembers();
                    Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"team.members");
                    Iterable $this$filter$iv = collection;
                    boolean $i$f$filter = false;
                    Iterable iterable = $this$filter$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$filterTo = false;
                    for (T element$iv$iv : $this$filterTo$iv$iv) {
                        PartyManager.PartyMember it = (PartyManager.PartyMember)element$iv$iv;
                        boolean bl = false;
                        if (!(!it.isLeader())) continue;
                        destination$iv$iv.add(element$iv$iv);
                    }
                    Iterable $this$mapNotNull$iv = (List)destination$iv$iv;
                    boolean $i$f$mapNotNull = false;
                    $this$filterTo$iv$iv = $this$mapNotNull$iv;
                    destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Player it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        PartyManager.PartyMember it = (PartyManager.PartyMember)element$iv$iv;
                        boolean bl2 = false;
                        if (it.getPlayer() == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv.add(it$iv$iv);
                    }
                    return (List)destination$iv$iv;
                }
            };
        }
    }
}

