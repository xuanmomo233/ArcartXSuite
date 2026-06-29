/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.command.CommandSender
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.module.command.CommandHelperKt;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0000\u00a8\u0006\u0004"}, d2={"space", "", "sender", "Lorg/bukkit/command/CommandSender;", "Chemdah"})
public final class CommandHelperKt {
    public static final void space(@NotNull CommandSender sender) {
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        new RawMessage(null, 1, null).sendTo(AdapterKt.adaptCommandSender((Object)sender), (Function1)space.1.INSTANCE);
    }
}

