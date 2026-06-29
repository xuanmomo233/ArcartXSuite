/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.papi;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.PlaceholderHookEvent;
import ink.ptms.chemdah.module.papi.PlaceholderData;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansion;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\u0004J\u001a\u0010\r\u001a\u00020\u00042\b\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u0010\u001a\u00020\u0004H\u0016J\u0016\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0013\u001a\u00020\tR\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R*\u0010\u0007\u001a\u001e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\t0\bj\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\t`\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/module/papi/PlaceholderForLiteral;", "Link/ptms/chemdah/taboolib/platform/compat/PlaceholderExpansion;", "()V", "identifier", "", "getIdentifier", "()Ljava/lang/String;", "placeholderData", "Ljava/util/HashMap;", "Link/ptms/chemdah/module/papi/PlaceholderData;", "Lkotlin1822/collections/HashMap;", "getPlaceholder", "name", "onPlaceholderRequest", "player", "Lorg/bukkit/entity/Player;", "args", "registerPlaceholder", "", "data", "Chemdah"})
public final class PlaceholderForLiteral
implements PlaceholderExpansion {
    @NotNull
    public static final PlaceholderForLiteral INSTANCE = new PlaceholderForLiteral();
    @NotNull
    private static final HashMap<String, PlaceholderData> placeholderData = new HashMap();

    private PlaceholderForLiteral() {
    }

    @NotNull
    public String getIdentifier() {
        return "ch";
    }

    @NotNull
    public String onPlaceholderRequest(@Nullable Player player2, @NotNull String args) {
        String string;
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        Player player3 = player2;
        boolean bl = player3 != null ? ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player3) : false;
        if (bl) {
            String string2;
            String string3 = StringsKt.substringBefore$default((String)args, (char)'_', null, (int)2, null).toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            String name = string3;
            String body = StringsKt.substringAfter$default((String)args, (char)'_', null, (int)2, null);
            PlaceholderData data2 = placeholderData.get(name);
            if (data2 == null) {
                PlaceholderHookEvent event = new PlaceholderHookEvent(player2, ChemdahAPI.INSTANCE.getChemdahProfile(player2), name, body, null, 16, null);
                event.call();
                Object object = event.getResult();
                string2 = object != null ? object.toString() : null;
            } else {
                Object object = data2.getResult(ChemdahAPI.INSTANCE.getChemdahProfile(player2), body);
                string2 = string = object != null ? object.toString() : null;
            }
            if (string2 == null) {
                string = "UNSUPPORTED";
            }
        } else {
            string = "ERROR";
        }
        return string;
    }

    public final void registerPlaceholder(@NotNull String identifier, @NotNull PlaceholderData data2) {
        Intrinsics.checkNotNullParameter((Object)identifier, (String)"identifier");
        Intrinsics.checkNotNullParameter((Object)data2, (String)"data");
        String string = identifier.toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        String name = string;
        if (placeholderData.containsKey(name)) {
            Object[] objectArray = new Object[]{"\u53d8\u91cf " + name + " \u5df2\u88ab\u6ce8\u518c."};
            IOKt.warning((Object[])objectArray);
            return;
        }
        ((Map)placeholderData).put(name, data2);
    }

    @NotNull
    public final PlaceholderData getPlaceholder(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        String string = name.toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        PlaceholderData placeholderData = PlaceholderForLiteral.placeholderData.get(string);
        if (placeholderData == null) {
            StringBuilder stringBuilder = new StringBuilder().append("\u4e0d\u5b58\u5728\u7684\u53d8\u91cf ");
            String string2 = name.toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            throw new NullPointerException(stringBuilder.append(string2).toString());
        }
        return placeholderData;
    }
}

