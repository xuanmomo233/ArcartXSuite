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
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.quest.Quest;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010 \n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0016J\u0010\u0010\u000f\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\u0011H\u0014J\u0010\u0010\u0012\u001a\u00020\f2\u0006\u0010\t\u001a\u00020\nH\u0016J\u0012\u0010\u0013\u001a\u0004\u0018\u00010\u00112\u0006\u0010\u0010\u001a\u00020\u0011H\u0014J\u0018\u0010\u0014\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J\u0018\u0010\u0015\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0016\u001a\u00020\u0011H\u0014J\u000e\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00110\u0018H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/database/DatabaseError;", "Link/ptms/chemdah/core/database/Database;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "releaseQuest", "", "player", "Lorg/bukkit/entity/Player;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "releaseVariable0", "key", "", "select", "selectVariable0", "update", "updateVariable0", "value", "variables", "", "Chemdah"})
public final class DatabaseError
extends Database {
    @NotNull
    private final Throwable cause;

    public DatabaseError(@NotNull Throwable cause) {
        Intrinsics.checkNotNullParameter((Object)cause, (String)"cause");
        this.cause = cause;
        this.cause.printStackTrace();
    }

    @NotNull
    public final Throwable getCause() {
        return this.cause;
    }

    @Override
    @NotNull
    public PlayerProfile select(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    public void update(@NotNull Player player2, @NotNull PlayerProfile playerProfile2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    public void releaseQuest(@NotNull Player player2, @NotNull PlayerProfile playerProfile2, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"playerProfile");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    @Nullable
    protected String selectVariable0(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    protected void updateVariable0(@NotNull String key, @NotNull String value2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    protected void releaseVariable0(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }

    @Override
    @NotNull
    public List<String> variables() {
        throw new IllegalAccessError("Database initialization failed: " + this.cause.getLocalizedMessage());
    }
}

