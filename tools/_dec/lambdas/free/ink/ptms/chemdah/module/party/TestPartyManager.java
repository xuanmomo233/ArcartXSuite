/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u0013B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nJ\u000e\u0010\f\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nJ\u000e\u0010\r\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nJ\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00060\u000fJ\u0010\u0010\u0010\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0011\u001a\u00020\nJ\u0016\u0010\u0012\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nR\u001a\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/module/party/TestPartyManager;", "", "()V", "parties", "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/util/UUID;", "Link/ptms/chemdah/module/party/TestPartyManager$TestPartyData;", "addMember", "", "leader", "Lorg/bukkit/entity/Player;", "member", "createParty", "deleteParty", "getAllParties", "", "getPlayerParty", "player", "removeMember", "TestPartyData", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTestPartyManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TestPartyManager.kt\nink/ptms/chemdah/module/party/TestPartyManager\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,112:1\n1#2:113\n288#3,2:114\n*S KotlinDebug\n*F\n+ 1 TestPartyManager.kt\nink/ptms/chemdah/module/party/TestPartyManager\n*L\n81#1:114,2\n*E\n"})
public final class TestPartyManager {
    @NotNull
    public static final TestPartyManager INSTANCE = new TestPartyManager();
    @NotNull
    private static final ConcurrentHashMap<UUID, TestPartyData> parties = new ConcurrentHashMap();

    private TestPartyManager() {
    }

    public final boolean createParty(@NotNull Player leader) {
        Intrinsics.checkNotNullParameter((Object)leader, (String)"leader");
        if (this.getPlayerParty(leader) != null) {
            return false;
        }
        Map map = parties;
        UUID uUID = leader.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"leader.uniqueId");
        UUID uUID2 = uUID;
        UUID uUID3 = leader.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID3, (String)"leader.uniqueId");
        TestPartyData testPartyData = new TestPartyData(uUID3, new LinkedHashSet());
        map.put(uUID2, testPartyData);
        return true;
    }

    public final boolean addMember(@NotNull Player leader, @NotNull Player member) {
        Intrinsics.checkNotNullParameter((Object)leader, (String)"leader");
        Intrinsics.checkNotNullParameter((Object)member, (String)"member");
        TestPartyData testPartyData = parties.get(leader.getUniqueId());
        if (testPartyData == null) {
            return false;
        }
        TestPartyData party = testPartyData;
        if (this.getPlayerParty(member) != null) {
            return false;
        }
        if (Intrinsics.areEqual((Object)leader.getUniqueId(), (Object)member.getUniqueId())) {
            return false;
        }
        Set<UUID> set2 = party.getMembers();
        UUID uUID = member.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"member.uniqueId");
        return set2.add(uUID);
    }

    public final boolean removeMember(@NotNull Player leader, @NotNull Player member) {
        Intrinsics.checkNotNullParameter((Object)leader, (String)"leader");
        Intrinsics.checkNotNullParameter((Object)member, (String)"member");
        TestPartyData testPartyData = parties.get(leader.getUniqueId());
        if (testPartyData == null) {
            return false;
        }
        TestPartyData party = testPartyData;
        return party.getMembers().remove(member.getUniqueId());
    }

    public final boolean deleteParty(@NotNull Player leader) {
        Intrinsics.checkNotNullParameter((Object)leader, (String)"leader");
        return parties.remove(leader.getUniqueId()) != null;
    }

    @Nullable
    public final TestPartyData getPlayerParty(@NotNull Player player2) {
        Object v2;
        block2: {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            UUID uUID = player2.getUniqueId();
            Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
            UUID uuid = uUID;
            TestPartyData testPartyData = parties.get(uuid);
            if (testPartyData != null) {
                TestPartyData it = testPartyData;
                boolean bl = false;
                return it;
            }
            Collection<TestPartyData> collection = parties.values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"parties.values");
            Iterable $this$firstOrNull$iv = collection;
            boolean $i$f$firstOrNull = false;
            for (Object element$iv : $this$firstOrNull$iv) {
                TestPartyData it = (TestPartyData)element$iv;
                boolean bl = false;
                if (!it.getMembers().contains(uuid)) continue;
                v2 = element$iv;
                break block2;
            }
            v2 = null;
        }
        return v2;
    }

    @NotNull
    public final List<TestPartyData> getAllParties() {
        Collection<TestPartyData> collection = parties.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"parties.values");
        return CollectionsKt.toList((Iterable)collection);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010#\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001b\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005H\u00c6\u0003J#\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012J\b\u0010\u0014\u001a\u0004\u0018\u00010\u0013J\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/module/party/TestPartyManager$TestPartyData;", "", "leaderId", "Ljava/util/UUID;", "members", "", "(Ljava/util/UUID;Ljava/util/Set;)V", "getLeaderId", "()Ljava/util/UUID;", "getMembers", "()Ljava/util/Set;", "component1", "component2", "copy", "equals", "", "other", "getAllPlayers", "", "Lorg/bukkit/entity/Player;", "getLeader", "hashCode", "", "toString", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nTestPartyManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TestPartyManager.kt\nink/ptms/chemdah/module/party/TestPartyManager$TestPartyData\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,112:1\n1603#2,9:113\n1855#2:122\n1856#2:124\n1612#2:125\n1#3:123\n1#3:126\n*S KotlinDebug\n*F\n+ 1 TestPartyManager.kt\nink/ptms/chemdah/module/party/TestPartyManager$TestPartyData\n*L\n101#1:113,9\n101#1:122\n101#1:124\n101#1:125\n101#1:123\n*E\n"})
    public static final class TestPartyData {
        @NotNull
        private final UUID leaderId;
        @NotNull
        private final Set<UUID> members;

        public TestPartyData(@NotNull UUID leaderId, @NotNull Set<UUID> members) {
            Intrinsics.checkNotNullParameter((Object)leaderId, (String)"leaderId");
            Intrinsics.checkNotNullParameter(members, (String)"members");
            this.leaderId = leaderId;
            this.members = members;
        }

        @NotNull
        public final UUID getLeaderId() {
            return this.leaderId;
        }

        @NotNull
        public final Set<UUID> getMembers() {
            return this.members;
        }

        @Nullable
        public final Player getLeader() {
            return Bukkit.getPlayer((UUID)this.leaderId);
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final List<Player> getMembers() {
            void $this$mapNotNullTo$iv$iv;
            Iterable $this$mapNotNull$iv = this.members;
            boolean $i$f$mapNotNull = false;
            Iterable iterable = $this$mapNotNull$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$mapNotNullTo = false;
            void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv$iv$iv.iterator();
            while (iterator.hasNext()) {
                Player it$iv$iv;
                Object element$iv$iv$iv;
                Object element$iv$iv = element$iv$iv$iv = iterator.next();
                boolean bl = false;
                UUID it = (UUID)element$iv$iv;
                boolean bl2 = false;
                if (Bukkit.getPlayer((UUID)it) == null) continue;
                boolean bl3 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            return (List)destination$iv$iv;
        }

        @NotNull
        public final List<Player> getAllPlayers() {
            List all = new ArrayList();
            Player player2 = this.getLeader();
            if (player2 != null) {
                Player it = player2;
                boolean bl = false;
                all.add(it);
            }
            all.addAll((Collection)this.getMembers());
            return all;
        }

        @NotNull
        public final UUID component1() {
            return this.leaderId;
        }

        @NotNull
        public final Set<UUID> component2() {
            return this.members;
        }

        @NotNull
        public final TestPartyData copy(@NotNull UUID leaderId, @NotNull Set<UUID> members) {
            Intrinsics.checkNotNullParameter((Object)leaderId, (String)"leaderId");
            Intrinsics.checkNotNullParameter(members, (String)"members");
            return new TestPartyData(leaderId, members);
        }

        public static /* synthetic */ TestPartyData copy$default(TestPartyData testPartyData, UUID uUID, Set set2, int n, Object object) {
            if ((n & 1) != 0) {
                uUID = testPartyData.leaderId;
            }
            if ((n & 2) != 0) {
                set2 = testPartyData.members;
            }
            return testPartyData.copy(uUID, set2);
        }

        @NotNull
        public String toString() {
            return "TestPartyData(leaderId=" + this.leaderId + ", members=" + this.members + ')';
        }

        public int hashCode() {
            int result = this.leaderId.hashCode();
            result = result * 31 + ((Object)this.members).hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestPartyData)) {
                return false;
            }
            TestPartyData testPartyData = (TestPartyData)other;
            if (!Intrinsics.areEqual((Object)this.leaderId, (Object)testPartyData.leaderId)) {
                return false;
            }
            return Intrinsics.areEqual(this.members, testPartyData.members);
        }
    }
}

