/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import java.util.List;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\r\u0018\u00002\u00020\u0001:\u000b\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\rB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents;", "", "()V", "Accept", "Agent", "Collect", "Complete", "DataRemove", "DataSet", "Fail", "Registered", "Restart", "ScoreboardTrack", "Unregistered", "Chemdah"})
public final class QuestEvents {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Accept;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Accept {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Accept$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\n\u0018\u00002\u00020\u0001B\u0017\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\u0002\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u001c\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000f\"\u0004\b\u0010\u0010\u0011\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Accept$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Template;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Template;Link/ptms/chemdah/core/PlayerProfile;)V", "reason", "", "(Link/ptms/chemdah/core/quest/Template;Link/ptms/chemdah/core/PlayerProfile;Ljava/lang/String;)V", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Template;", "getReason", "()Ljava/lang/String;", "setReason", "(Ljava/lang/String;)V", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Template quest;
            @NotNull
            private final PlayerProfile playerProfile;
            @Nullable
            private String reason;

            public Pre(@NotNull Template quest2, @NotNull PlayerProfile playerProfile, @Nullable String reason) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
                this.reason = reason;
            }

            @NotNull
            public final Template getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            @Nullable
            public final String getReason() {
                return this.reason;
            }

            public final void setReason(@Nullable String string) {
                this.reason = string;
            }

            public Pre(@NotNull Template quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this(quest2, playerProfile, null);
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\n\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Agent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "agentType", "Link/ptms/chemdah/core/quest/AgentType;", "restrict", "", "(Link/ptms/chemdah/core/quest/QuestContainer;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/AgentType;Ljava/lang/String;)V", "getAgentType", "()Link/ptms/chemdah/core/quest/AgentType;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuestContainer", "()Link/ptms/chemdah/core/quest/QuestContainer;", "getRestrict", "()Ljava/lang/String;", "Chemdah"})
    public static final class Agent
    extends BukkitProxyEvent {
        @NotNull
        private final QuestContainer questContainer;
        @NotNull
        private final PlayerProfile playerProfile;
        @NotNull
        private final AgentType agentType;
        @NotNull
        private final String restrict;

        public Agent(@NotNull QuestContainer questContainer, @NotNull PlayerProfile playerProfile, @NotNull AgentType agentType, @NotNull String restrict) {
            Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
            Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
            Intrinsics.checkNotNullParameter((Object)((Object)agentType), (String)"agentType");
            Intrinsics.checkNotNullParameter((Object)restrict, (String)"restrict");
            this.questContainer = questContainer;
            this.playerProfile = playerProfile;
            this.agentType = agentType;
            this.restrict = restrict;
        }

        @NotNull
        public final QuestContainer getQuestContainer() {
            return this.questContainer;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        @NotNull
        public final AgentType getAgentType() {
            return this.agentType;
        }

        @NotNull
        public final String getRestrict() {
            return this.restrict;
        }
    }

    @Deprecated(message="\u8bf7\u4f7f\u7528 ChemdahAPI.eventFactory")
    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B\u001b\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Collect;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quests", "", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Ljava/util/List;Link/ptms/chemdah/core/PlayerProfile;)V", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuests", "()Ljava/util/List;", "Chemdah"})
    public static final class Collect
    extends BukkitProxyEvent {
        @NotNull
        private final List<Quest> quests;
        @NotNull
        private final PlayerProfile playerProfile;

        public Collect(@NotNull List<Quest> quests, @NotNull PlayerProfile playerProfile) {
            Intrinsics.checkNotNullParameter(quests, (String)"quests");
            Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
            this.quests = quests;
            this.playerProfile = playerProfile;
        }

        @NotNull
        public final List<Quest> getQuests() {
            return this.quests;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Complete;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Complete {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Complete$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Complete$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataRemove;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class DataRemove {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\r\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fR\u0014\u0010\r\u001a\u00020\u000e8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataRemove$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final Quest quest;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;

            public Post(@NotNull Player player, @NotNull PlayerProfile playerProfile, @NotNull Quest quest2, @NotNull DataContainer dataContainer, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                this.player = player;
                this.playerProfile = playerProfile;
                this.quest = quest2;
                this.dataContainer = dataContainer;
                this.key = key;
            }

            @NotNull
            public final Player getPlayer() {
                return this.player;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final DataContainer getDataContainer() {
                return this.dataContainer;
            }

            @NotNull
            public final String getKey() {
                return this.key;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataRemove$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;)V", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final Quest quest;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;

            public Pre(@NotNull Player player, @NotNull PlayerProfile playerProfile, @NotNull Quest quest2, @NotNull DataContainer dataContainer, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                this.player = player;
                this.playerProfile = playerProfile;
                this.quest = quest2;
                this.dataContainer = dataContainer;
                this.key = key;
            }

            @NotNull
            public final Player getPlayer() {
                return this.player;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final DataContainer getDataContainer() {
                return this.dataContainer;
            }

            @NotNull
            public final String getKey() {
                return this.key;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataSet;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class DataSet {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000f\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\r\u00a2\u0006\u0002\u0010\u000eR\u0014\u0010\u000f\u001a\u00020\u00108VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0011\u0010\f\u001a\u00020\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001e\u00a8\u0006\u001f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataSet$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "value", "Link/ptms/chemdah/core/Data;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;Link/ptms/chemdah/core/Data;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getValue", "()Link/ptms/chemdah/core/Data;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final Quest quest;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;
            @NotNull
            private final Data value;

            public Post(@NotNull Player player, @NotNull PlayerProfile playerProfile, @NotNull Quest quest2, @NotNull DataContainer dataContainer, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.player = player;
                this.playerProfile = playerProfile;
                this.quest = quest2;
                this.dataContainer = dataContainer;
                this.key = key;
                this.value = value2;
            }

            @NotNull
            public final Player getPlayer() {
                return this.player;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final DataContainer getDataContainer() {
                return this.dataContainer;
            }

            @NotNull
            public final String getKey() {
                return this.key;
            }

            @NotNull
            public final Data getValue() {
                return this.value;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0010\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\r\u00a2\u0006\u0002\u0010\u000eR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u001a\u0010\f\u001a\u00020\rX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u001a\"\u0004\b\u001b\u0010\u001c\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$DataSet$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "value", "Link/ptms/chemdah/core/Data;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;Link/ptms/chemdah/core/Data;)V", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getValue", "()Link/ptms/chemdah/core/Data;", "setValue", "(Link/ptms/chemdah/core/Data;)V", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final Quest quest;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;
            @NotNull
            private Data value;

            public Pre(@NotNull Player player, @NotNull PlayerProfile playerProfile, @NotNull Quest quest2, @NotNull DataContainer dataContainer, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.player = player;
                this.playerProfile = playerProfile;
                this.quest = quest2;
                this.dataContainer = dataContainer;
                this.key = key;
                this.value = value2;
            }

            @NotNull
            public final Player getPlayer() {
                return this.player;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final DataContainer getDataContainer() {
                return this.dataContainer;
            }

            @NotNull
            public final String getKey() {
                return this.key;
            }

            @NotNull
            public final Data getValue() {
                return this.value;
            }

            public final void setValue(@NotNull Data data2) {
                Intrinsics.checkNotNullParameter((Object)data2, (String)"<set-?>");
                this.value = data2;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Fail;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Fail {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Fail$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Fail$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Registered;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
    public static final class Registered
    extends BukkitProxyEvent {
        @NotNull
        private final Quest quest;
        @NotNull
        private final PlayerProfile playerProfile;

        public Registered(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
            Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
            Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
            this.quest = quest2;
            this.playerProfile = playerProfile;
        }

        @NotNull
        public final Quest getQuest() {
            return this.quest;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Restart;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Restart {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Restart$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }

            public boolean getAllowCancelled() {
                return false;
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Restart$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @NotNull
            public final PlayerProfile getPlayerProfile() {
                return this.playerProfile;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u001b\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007R\u0014\u0010\b\u001a\u00020\t8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$ScoreboardTrack;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "content", "", "", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Ljava/util/List;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getContent", "()Ljava/util/List;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
    public static final class ScoreboardTrack
    extends BukkitProxyEvent {
        @NotNull
        private final List<String> content;
        @NotNull
        private final PlayerProfile playerProfile;

        public ScoreboardTrack(@NotNull List<String> content, @NotNull PlayerProfile playerProfile) {
            Intrinsics.checkNotNullParameter(content, (String)"content");
            Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
            this.content = content;
            this.playerProfile = playerProfile;
        }

        @NotNull
        public final List<String> getContent() {
            return this.content;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/QuestEvents$Unregistered;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
    public static final class Unregistered
    extends BukkitProxyEvent {
        @NotNull
        private final Quest quest;
        @NotNull
        private final PlayerProfile playerProfile;

        public Unregistered(@NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
            Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
            Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
            this.quest = quest2;
            this.playerProfile = playerProfile;
        }

        @NotNull
        public final Quest getQuest() {
            return this.quest;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }
}

