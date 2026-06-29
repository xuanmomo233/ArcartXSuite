/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.papi;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansion;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\u0007\u001a\u00020\u00042\b\u0010\b\u001a\u0004\u0018\u00010\t2\u0006\u0010\n\u001a\u00020\u0004H\u0016R\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/module/papi/PlaceholderForKether;", "Link/ptms/chemdah/taboolib/platform/compat/PlaceholderExpansion;", "()V", "identifier", "", "getIdentifier", "()Ljava/lang/String;", "onPlaceholderRequest", "player", "Lorg/bukkit/entity/Player;", "args", "Chemdah"})
public final class PlaceholderForKether
implements PlaceholderExpansion {
    @NotNull
    public static final PlaceholderForKether INSTANCE = new PlaceholderForKether();

    private PlaceholderForKether() {
    }

    @NotNull
    public String getIdentifier() {
        return "chemdah";
    }

    @NotNull
    public String onPlaceholderRequest(@Nullable Player player, @NotNull String args) {
        String string;
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        if (player == null) {
            return "<NO_PLAYER>";
        }
        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) {
            Object object;
            try {
                object = KetherShell.INSTANCE;
                ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)player);
                List<String> list2 = UtilsForKetherKt.getNamespaceQuest();
                object = String.valueOf(KetherShell.eval$default((KetherShell)object, (String)args, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null).getNow(null));
            }
            catch (Throwable ex) {
                KetherHelperKt.printKetherErrorMessage((Throwable)ex, (boolean)true);
                object = "<ERROR>";
            }
            string = object;
        } else {
            string = "...";
        }
        return string;
    }
}

