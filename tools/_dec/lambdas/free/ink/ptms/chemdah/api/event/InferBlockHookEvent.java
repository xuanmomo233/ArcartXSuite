/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.api.event;

import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\t\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u000e\u0010\u0004\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007R\u0014\u0010\b\u001a\u00020\t8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\"\u0010\u0004\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/api/event/InferBlockHookEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "id", "", "blockClass", "Ljava/lang/Class;", "Link/ptms/chemdah/core/quest/selector/InferBlock$Block;", "(Ljava/lang/String;Ljava/lang/Class;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getBlockClass", "()Ljava/lang/Class;", "setBlockClass", "(Ljava/lang/Class;)V", "getId", "()Ljava/lang/String;", "Chemdah"})
public final class InferBlockHookEvent
extends BukkitProxyEvent {
    @NotNull
    private final String id;
    @NotNull
    private Class<? extends InferBlock.Block> blockClass;

    public InferBlockHookEvent(@NotNull String id2, @NotNull Class<? extends InferBlock.Block> blockClass) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(blockClass, (String)"blockClass");
        this.id = id2;
        this.blockClass = blockClass;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final Class<? extends InferBlock.Block> getBlockClass() {
        return this.blockClass;
    }

    public final void setBlockClass(@NotNull Class<? extends InferBlock.Block> clazz) {
        Intrinsics.checkNotNullParameter(clazz, (String)"<set-?>");
        this.blockClass = clazz;
    }

    public boolean getAllowCancelled() {
        return false;
    }
}

