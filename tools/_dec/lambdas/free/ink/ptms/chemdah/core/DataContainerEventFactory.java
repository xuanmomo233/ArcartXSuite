/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\bf\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eJ\u0018\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H&J \u0010\b\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\t\u001a\u00020\nH&J\u0018\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H&J\"\u0010\r\u001a\u0004\u0018\u00010\n2\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\t\u001a\u00020\nH&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u000f\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/DataContainerEventFactory;", "", "callPostRemove", "", "container", "Link/ptms/chemdah/core/DataContainer;", "key", "", "callPostSet", "value", "Link/ptms/chemdah/core/Data;", "callPreRemove", "", "callPreSet", "Companion", "Chemdah"})
public interface DataContainerEventFactory {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.core.DataContainerEventFactory$Companion.$$INSTANCE;

    @Nullable
    public Data callPreSet(@NotNull DataContainer var1, @NotNull String var2, @NotNull Data var3);

    public void callPostSet(@NotNull DataContainer var1, @NotNull String var2, @NotNull Data var3);

    public boolean callPreRemove(@NotNull DataContainer var1, @NotNull String var2);

    public void callPostRemove(@NotNull DataContainer var1, @NotNull String var2);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001:\u0002\f\rB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\tJ\u0016\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bR\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/DataContainerEventFactory$Companion;", "", "()V", "EMPTY", "Link/ptms/chemdah/core/DataContainerEventFactory;", "getEMPTY", "()Link/ptms/chemdah/core/DataContainerEventFactory;", "of", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "PlayerImpl", "QuestImpl", "Chemdah"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;
        @NotNull
        private static final DataContainerEventFactory EMPTY;

        private Companion() {
        }

        @NotNull
        public final DataContainerEventFactory getEMPTY() {
            return EMPTY;
        }

        @NotNull
        public final DataContainerEventFactory of(@NotNull PlayerProfile profile) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            return new PlayerImpl(profile);
        }

        @NotNull
        public final DataContainerEventFactory of(@NotNull PlayerProfile profile, @NotNull Quest quest2) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
            return new QuestImpl(profile, quest2);
        }

        static {
            $$INSTANCE = new Companion();
            EMPTY = new DataContainerEventFactory(){

                @NotNull
                public Data callPreSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                    Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                    Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                    return value2;
                }

                public void callPostSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                    Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                    Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                }

                public boolean callPreRemove(@NotNull DataContainer container, @NotNull String key) {
                    Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                    return true;
                }

                public void callPostRemove(@NotNull DataContainer container, @NotNull String key) {
                    Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                }
            };
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J \u0010\r\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016J\u0018\u0010\u0010\u001a\u00020\u00112\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J\"\u0010\u0012\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/DataContainerEventFactory$Companion$PlayerImpl;", "Link/ptms/chemdah/core/DataContainerEventFactory;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/core/PlayerProfile;)V", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "callPostRemove", "", "container", "Link/ptms/chemdah/core/DataContainer;", "key", "", "callPostSet", "value", "Link/ptms/chemdah/core/Data;", "callPreRemove", "", "callPreSet", "Chemdah"})
        public static final class PlayerImpl
        implements DataContainerEventFactory {
            @NotNull
            private final PlayerProfile profile;

            public PlayerImpl(@NotNull PlayerProfile profile) {
                Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
                this.profile = profile;
            }

            @NotNull
            public final PlayerProfile getProfile() {
                return this.profile;
            }

            @Override
            @Nullable
            public Data callPreSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                PlayerEvents.DataSet.Pre event = new PlayerEvents.DataSet.Pre(player2, this.profile, container, key, value2);
                return event.call() ? event.getValue() : null;
            }

            @Override
            public void callPostSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                new PlayerEvents.DataSet.Post(player2, this.profile, container, key, value2).call();
            }

            @Override
            public boolean callPreRemove(@NotNull DataContainer container, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                return new PlayerEvents.DataRemove.Pre(player2, this.profile, container, key).call();
            }

            @Override
            public void callPostRemove(@NotNull DataContainer container, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                new PlayerEvents.DataRemove.Post(player2, this.profile, container, key).call();
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J \u0010\u0011\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0012\u001a\u00020\u0013H\u0016J\u0018\u0010\u0014\u001a\u00020\u00152\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J\"\u0010\u0016\u001a\u0004\u0018\u00010\u00132\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0012\u001a\u00020\u0013H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/DataContainerEventFactory$Companion$QuestImpl;", "Link/ptms/chemdah/core/DataContainerEventFactory;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Quest;)V", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "callPostRemove", "", "container", "Link/ptms/chemdah/core/DataContainer;", "key", "", "callPostSet", "value", "Link/ptms/chemdah/core/Data;", "callPreRemove", "", "callPreSet", "Chemdah"})
        public static final class QuestImpl
        implements DataContainerEventFactory {
            @NotNull
            private final PlayerProfile profile;
            @NotNull
            private final Quest quest;

            public QuestImpl(@NotNull PlayerProfile profile, @NotNull Quest quest2) {
                Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
                Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
                this.profile = profile;
                this.quest = quest2;
            }

            @NotNull
            public final PlayerProfile getProfile() {
                return this.profile;
            }

            @NotNull
            public final Quest getQuest() {
                return this.quest;
            }

            @Override
            @Nullable
            public Data callPreSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                QuestEvents.DataSet.Pre event = new QuestEvents.DataSet.Pre(player2, this.profile, this.quest, container, key, value2);
                return event.call() ? event.getValue() : null;
            }

            @Override
            public void callPostSet(@NotNull DataContainer container, @NotNull String key, @NotNull Data value2) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                new QuestEvents.DataSet.Post(player2, this.profile, this.quest, container, key, value2).call();
            }

            @Override
            public boolean callPreRemove(@NotNull DataContainer container, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                return new QuestEvents.DataRemove.Pre(player2, this.profile, this.quest, container, key).call();
            }

            @Override
            public void callPostRemove(@NotNull DataContainer container, @NotNull String key) {
                Intrinsics.checkNotNullParameter((Object)container, (String)"container");
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Player player2 = this.profile.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
                new QuestEvents.DataRemove.Post(player2, this.profile, this.quest, container, key).call();
            }
        }
    }
}

