/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.messaging;

import ink.ptms.chemdah.core.messaging.Messaging;
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0010\u0010\t\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0010\u0010\n\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/messaging/MessagingError;", "Link/ptms/chemdah/core/messaging/Messaging;", "message", "", "(Ljava/lang/String;)V", "awaitProfileReleased", "", "player", "Ljava/util/UUID;", "markProfileReleased", "markProfileSelected", "Chemdah"})
public final class MessagingError
implements Messaging {
    @NotNull
    private final String message;

    public MessagingError(@NotNull String message2) {
        Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
        this.message = message2;
    }

    @Override
    public void markProfileReleased(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        throw new IllegalAccessError("Messaging initialization failed: " + this.message);
    }

    @Override
    public void markProfileSelected(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        throw new IllegalAccessError("Messaging initialization failed: " + this.message);
    }

    @Override
    public void awaitProfileReleased(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        throw new IllegalAccessError("Messaging initialization failed: " + this.message);
    }
}

