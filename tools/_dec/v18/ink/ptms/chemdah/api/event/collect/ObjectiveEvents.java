/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0005\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents;", "", "()V", "Complete", "Continue", "Restart", "Chemdah"})
public final class ObjectiveEvents {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Complete {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000b\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0014\u0010\u000b\u001a\u00020\f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Continue {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000b\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0014\u0010\u000b\u001a\u00020\f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Restart;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class Restart {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000b\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0014\u0010\u000b\u001a\u00020\f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Restart$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Post(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001B)\u0012\n\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0015\u0010\u0002\u001a\u0006\u0012\u0002\b\u00030\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Restart$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/quest/objective/Objective;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Link/ptms/chemdah/core/PlayerProfile;)V", "getObjective", "()Link/ptms/chemdah/core/quest/objective/Objective;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Objective<?> objective;
            @NotNull
            private final Task task;
            @NotNull
            private final Quest quest;
            @NotNull
            private final PlayerProfile playerProfile;

            public Pre(@NotNull Objective<?> objective2, @NotNull Task task, @NotNull Quest quest2, @NotNull PlayerProfile playerProfile) {
                Intrinsics.checkNotNullParameter(objective2, (String)"objective");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
                this.objective = objective2;
                this.task = task;
                this.quest = quest2;
                this.playerProfile = playerProfile;
            }

            @NotNull
            public final Objective<?> getObjective() {
                return this.objective;
            }

            @NotNull
            public final Task getTask() {
                return this.task;
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
}

