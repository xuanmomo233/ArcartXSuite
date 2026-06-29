/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  kotlin.Metadata
 *  kotlin.jvm.JvmStatic
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.block.Block
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.bukkit;

import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b&\u0018\u0000 \b2\u00020\u0001:\u0001\bB\u0005\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010\u00042\u0006\u0010\u0006\u001a\u00020\u0007H&\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/bukkit/NMS;", "", "()V", "getBlocKData", "", "", "block", "Lorg/bukkit/block/Block;", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nNMS.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NMS.kt\nink/ptms/chemdah/core/bukkit/NMS\n+ 2 MinecraftServerUtil.kt\ntaboolib/module/nms/MinecraftServerUtilKt\n*L\n1#1,22:1\n109#2,2:23\n*S KotlinDebug\n*F\n+ 1 NMS.kt\nink/ptms/chemdah/core/bukkit/NMS\n*L\n20#1:23,2\n*E\n"})
public abstract class NMS {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private static final NMS INSTANCE;

    @NotNull
    public abstract Map<String, Object> getBlocKData(@NotNull Block var1);

    @NotNull
    public static final NMS getINSTANCE() {
        return Companion.getINSTANCE();
    }

    static {
        Object[] parameter$iv = new Object[]{};
        String bind$iv = "{name}Impl";
        boolean $i$f$nmsProxy = false;
        INSTANCE = (NMS)MinecraftServerUtilKt.nmsProxy(NMS.class, (String)bind$iv, (List)CollectionsKt.emptyList(), (Object[])Arrays.copyOf(parameter$iv, parameter$iv.length));
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001c\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\u000e\n\u0000\u0012\u0004\b\u0005\u0010\u0002\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/bukkit/NMS$Companion;", "", "()V", "INSTANCE", "Link/ptms/chemdah/core/bukkit/NMS;", "getINSTANCE$annotations", "getINSTANCE", "()Link/ptms/chemdah/core/bukkit/NMS;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final NMS getINSTANCE() {
            return INSTANCE;
        }

        @JvmStatic
        public static /* synthetic */ void getINSTANCE$annotations() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

