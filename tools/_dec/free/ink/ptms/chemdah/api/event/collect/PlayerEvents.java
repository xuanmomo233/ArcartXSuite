/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.scenes.ScenesBlockData;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\f\u0018\u00002\u00020\u0001:\n\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\fB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents;", "", "()V", "DataRemove", "DataSet", "LevelChange", "Released", "ScenesBlockBreak", "ScenesBlockInteract", "Selected", "Track", "Trigger", "Updated", "Chemdah"})
public final class PlayerEvents {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataRemove;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class DataRemove {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0014\u0010\u000b\u001a\u00020\f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataRemove$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;

            public Post(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull DataContainer dataContainer, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                this.player = player2;
                this.playerProfile = playerProfile2;
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

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\n\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataRemove$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;)V", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;

            public Pre(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull DataContainer dataContainer, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                this.player = player2;
                this.playerProfile = playerProfile2;
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
            public final DataContainer getDataContainer() {
                return this.dataContainer;
            }

            @NotNull
            public final String getKey() {
                return this.key;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataSet;", "", "()V", "Post", "Pre", "Chemdah"})
    public static final class DataSet {

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\r\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fR\u0014\u0010\r\u001a\u00020\u000e8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataSet$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "value", "Link/ptms/chemdah/core/Data;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;Link/ptms/chemdah/core/Data;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getValue", "()Link/ptms/chemdah/core/Data;", "Chemdah"})
        public static final class Post
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;
            @NotNull
            private final Data value;

            public Post(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull DataContainer dataContainer, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.player = player2;
                this.playerProfile = playerProfile2;
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

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u001a\u0010\n\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$DataSet$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "dataContainer", "Link/ptms/chemdah/core/DataContainer;", "key", "", "value", "Link/ptms/chemdah/core/Data;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/DataContainer;Ljava/lang/String;Link/ptms/chemdah/core/Data;)V", "getDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getKey", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getValue", "()Link/ptms/chemdah/core/Data;", "setValue", "(Link/ptms/chemdah/core/Data;)V", "Chemdah"})
        public static final class Pre
        extends BukkitProxyEvent {
            @NotNull
            private final Player player;
            @NotNull
            private final PlayerProfile playerProfile;
            @NotNull
            private final DataContainer dataContainer;
            @NotNull
            private final String key;
            @NotNull
            private Data value;

            public Pre(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull DataContainer dataContainer, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
                Intrinsics.checkNotNullParameter((Object)dataContainer, (String)"dataContainer");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.player = player2;
                this.playerProfile = playerProfile2;
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

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0011\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\u0007\u0012\u0006\u0010\n\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u000bR\u001a\u0010\n\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001a\u0010\t\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\r\"\u0004\b\u0011\u0010\u000fR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$LevelChange;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "option", "Link/ptms/chemdah/module/level/LevelOption;", "oldLevel", "", "oldExperience", "newLevel", "newExperience", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/module/level/LevelOption;IIII)V", "getNewExperience", "()I", "setNewExperience", "(I)V", "getNewLevel", "setNewLevel", "getOldExperience", "getOldLevel", "getOption", "()Link/ptms/chemdah/module/level/LevelOption;", "getPlayer", "()Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class LevelChange
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final LevelOption option;
        private final int oldLevel;
        private final int oldExperience;
        private int newLevel;
        private int newExperience;

        public LevelChange(@NotNull Player player2, @NotNull LevelOption option, int oldLevel, int oldExperience, int newLevel, int newExperience) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)option, (String)"option");
            this.player = player2;
            this.option = option;
            this.oldLevel = oldLevel;
            this.oldExperience = oldExperience;
            this.newLevel = newLevel;
            this.newExperience = newExperience;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final LevelOption getOption() {
            return this.option;
        }

        public final int getOldLevel() {
            return this.oldLevel;
        }

        public final int getOldExperience() {
            return this.oldExperience;
        }

        public final int getNewLevel() {
            return this.newLevel;
        }

        public final void setNewLevel(int n) {
            this.newLevel = n;
        }

        public final int getNewExperience() {
            return this.newExperience;
        }

        public final void setNewExperience(int n) {
            this.newExperience = n;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000<\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016J\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00180\u000bR\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR2\u0010\t\u001a\u001a\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u000b0\nj\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u000b`\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "(Lorg/bukkit/entity/Player;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "awaitList", "Ljava/util/ArrayList;", "Ljava/util/concurrent/CompletableFuture;", "Lkotlin1822/collections/ArrayList;", "getAwaitList", "()Ljava/util/ArrayList;", "setAwaitList", "(Ljava/util/ArrayList;)V", "getPlayer", "()Lorg/bukkit/entity/Player;", "await", "", "runnable", "Ljava/lang/Runnable;", "wait", "Ljava/lang/Void;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nPlayerEvents.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerEvents.kt\nink/ptms/chemdah/api/event/collect/PlayerEvents$Released\n+ 2 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,169:1\n37#2,2:170\n*S KotlinDebug\n*F\n+ 1 PlayerEvents.kt\nink/ptms/chemdah/api/event/collect/PlayerEvents$Released\n*L\n54#1:170,2\n*E\n"})
    public static final class Released
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private ArrayList<CompletableFuture<?>> awaitList;

        public Released(@NotNull Player player2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            this.player = player2;
            this.awaitList = new ArrayList();
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        public boolean getAllowCancelled() {
            return false;
        }

        @NotNull
        public final ArrayList<CompletableFuture<?>> getAwaitList() {
            return this.awaitList;
        }

        public final void setAwaitList(@NotNull ArrayList<CompletableFuture<?>> arrayList) {
            Intrinsics.checkNotNullParameter(arrayList, (String)"<set-?>");
            this.awaitList = arrayList;
        }

        public final void await(@NotNull Runnable runnable) {
            Intrinsics.checkNotNullParameter((Object)runnable, (String)"runnable");
            ((Collection)this.awaitList).add(CompletableFuture.runAsync(() -> Released.await$lambda$0(runnable)));
        }

        @NotNull
        public final CompletableFuture<Void> wait() {
            this.call();
            Collection $this$toTypedArray$iv = this.awaitList;
            boolean $i$f$toTypedArray = false;
            Collection thisCollection$iv = $this$toTypedArray$iv;
            CompletableFuture[] completableFutureArray = thisCollection$iv.toArray(new CompletableFuture[0]);
            CompletableFuture<Void> completableFuture = CompletableFuture.allOf(Arrays.copyOf(completableFutureArray, completableFutureArray.length));
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"allOf(*awaitList.toTypedArray())");
            return completableFuture;
        }

        private static final void await$lambda$0(Runnable $runnable) {
            Intrinsics.checkNotNullParameter((Object)$runnable, (String)"$runnable");
            try {
                $runnable.run();
            }
            catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$ScenesBlockBreak;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "blockData", "Link/ptms/chemdah/module/scenes/ScenesBlockData;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/module/scenes/ScenesBlockData;)V", "getBlockData", "()Link/ptms/chemdah/module/scenes/ScenesBlockData;", "getPlayer", "()Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class ScenesBlockBreak
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final ScenesBlockData blockData;

        public ScenesBlockBreak(@NotNull Player player2, @NotNull ScenesBlockData blockData) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)blockData, (String)"blockData");
            this.player = player2;
            this.blockData = blockData;
            this.setCancelled(true);
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final ScenesBlockData getBlockData() {
            return this.blockData;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$ScenesBlockInteract;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "blockData", "Link/ptms/chemdah/module/scenes/ScenesBlockData;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/module/scenes/ScenesBlockData;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getBlockData", "()Link/ptms/chemdah/module/scenes/ScenesBlockData;", "getPlayer", "()Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class ScenesBlockInteract
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final ScenesBlockData blockData;

        public ScenesBlockInteract(@NotNull Player player2, @NotNull ScenesBlockData blockData) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)blockData, (String)"blockData");
            this.player = player2;
            this.blockData = blockData;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final ScenesBlockData getBlockData() {
            return this.blockData;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$Selected;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
    public static final class Selected
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final PlayerProfile playerProfile;

        public Selected(@NotNull Player player2, @NotNull PlayerProfile playerProfile2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
            this.player = player2;
            this.playerProfile = playerProfile2;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\n\u0018\u00002\u00020\u0001B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$Track;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "trackingQuest", "Link/ptms/chemdah/core/quest/Template;", "cancel", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Template;Z)V", "getCancel", "()Z", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getTrackingQuest", "()Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Track
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final PlayerProfile playerProfile;
        @Nullable
        private final Template trackingQuest;
        private final boolean cancel;

        public Track(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @Nullable Template trackingQuest, boolean cancel2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
            this.player = player2;
            this.playerProfile = playerProfile2;
            this.trackingQuest = trackingQuest;
            this.cancel = cancel2;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final PlayerProfile getPlayerProfile() {
            return this.playerProfile;
        }

        @Nullable
        public final Template getTrackingQuest() {
            return this.trackingQuest;
        }

        public final boolean getCancel() {
            return this.cancel;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$Trigger;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "value", "", "(Lorg/bukkit/entity/Player;Ljava/lang/String;)V", "getPlayer", "()Lorg/bukkit/entity/Player;", "getValue", "()Ljava/lang/String;", "Chemdah"})
    public static final class Trigger
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final String value;

        public Trigger(@NotNull Player player2, @NotNull String value2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            this.player = player2;
            this.value = value2;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final String getValue() {
            return this.value;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/PlayerEvents$Updated;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getPlayer", "()Lorg/bukkit/entity/Player;", "getPlayerProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
    public static final class Updated
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final PlayerProfile playerProfile;

        public Updated(@NotNull Player player2, @NotNull PlayerProfile playerProfile2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
            this.player = player2;
            this.playerProfile = playerProfile2;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
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

