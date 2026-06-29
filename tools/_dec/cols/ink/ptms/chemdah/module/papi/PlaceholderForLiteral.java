/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.papi;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.PlaceholderHookEvent;
import ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansion;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\u0007\u001a\u00020\u00042\b\u0010\b\u001a\u0004\u0018\u00010\t2\u0006\u0010\n\u001a\u00020\u0004H\u0016R\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/module/papi/PlaceholderForLiteral;", "Link/ptms/chemdah/taboolib/platform/compat/PlaceholderExpansion;", "()V", "identifier", "", "getIdentifier", "()Ljava/lang/String;", "onPlaceholderRequest", "player", "Lorg/bukkit/entity/Player;", "args", "Chemdah"})
public final class PlaceholderForLiteral
implements PlaceholderExpansion {
    @NotNull
    public static final PlaceholderForLiteral INSTANCE = new PlaceholderForLiteral();

    private PlaceholderForLiteral() {
    }

    @NotNull
    public String getIdentifier() {
        return "ch";
    }

    @NotNull
    public String onPlaceholderRequest(@Nullable Player player, @NotNull String args) {
        Object object;
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        Player player2 = player;
        boolean bl = player2 != null ? ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player2) : false;
        if (bl) {
            String string = StringsKt.substringBefore$default((String)args, (char)'_', null, (int)2, null).toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            String name = string;
            String body = StringsKt.substringAfter$default((String)args, (char)'_', null, (int)2, null);
            PlaceholderHookEvent event = new PlaceholderHookEvent(player, ChemdahAPI.INSTANCE.getChemdahProfile(player), name, body, null, 16, null);
            event.call();
            object = event.getResult();
            if (object == null || (object = object.toString()) == null) {
                object = "UNSUPPORTED";
            }
        } else {
            object = "ERROR";
        }
        return object;
    }
}

