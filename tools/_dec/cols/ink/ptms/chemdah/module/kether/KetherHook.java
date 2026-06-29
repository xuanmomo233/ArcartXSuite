/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.module.kether.KetherHook;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.module.kether.Kether;
import ink.ptms.chemdah.taboolib.module.kether.PlayerOperator;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/KetherHook;", "", "()V", "init", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nKetherHook.kt\nKotlin\n*S Kotlin\n*F\n+ 1 KetherHook.kt\nink/ptms/chemdah/module/kether/KetherHook\n+ 2 ArrayIntrinsics.kt\nkotlin/ArrayIntrinsicsKt\n*L\n1#1,19:1\n26#2:20\n*S KotlinDebug\n*F\n+ 1 KetherHook.kt\nink/ptms/chemdah/module/kether/KetherHook\n*L\n16#1:20\n*E\n"})
public final class KetherHook {
    @NotNull
    public static final KetherHook INSTANCE = new KetherHook();

    private KetherHook() {
    }

    @Awake(value=LifeCycle.INIT)
    public final void init() {
        Map map = Kether.INSTANCE.getRegisteredPlayerOperator();
        String string = "balance";
        boolean $i$f$emptyArray = false;
        PlayerOperator playerOperator = new PlayerOperator(new PlayerOperator.Reader((Function1)init.1.INSTANCE), null, new PlayerOperator.Method[0], 2, null);
        map.put(string, playerOperator);
    }
}

