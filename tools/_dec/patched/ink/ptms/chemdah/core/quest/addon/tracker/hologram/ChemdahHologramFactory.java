/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker.hologram;

import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologramFactory;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologramHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.impl.ArmorStandHologramHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.impl.TextDisplayHologramHandler;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\u0004H\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramFactory;", "", "()V", "handler", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramHandler;", "getHandler", "()Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramHandler;", "handler$delegate", "Lkotlin1822/Lazy;", "selectVanillaHandler", "Chemdah"})
public final class ChemdahHologramFactory {
    @NotNull
    public static final ChemdahHologramFactory INSTANCE = new ChemdahHologramFactory();
    @NotNull
    private static final Lazy handler$delegate = LazyMakerKt.unsafeLazy((Function0)handler.2.INSTANCE);

    private ChemdahHologramFactory() {
    }

    @NotNull
    public final ChemdahHologramHandler getHandler() {
        Lazy lazy = handler$delegate;
        return (ChemdahHologramHandler)lazy.getValue();
    }

    private final ChemdahHologramHandler selectVanillaHandler() {
        ChemdahHologramHandler chemdahHologramHandler;
        if (MinecraftVersion.INSTANCE.getVersionId() >= 11904) {
            ChemdahHologramHandler chemdahHologramHandler2;
            try {
                chemdahHologramHandler2 = new TextDisplayHologramHandler();
            }
            catch (Throwable e) {
                AdapterKt.console().sendMessage("\u00a7c[Chemdah] \u00a77Failed to load TextDisplay hologram handler, fallback to ArmorStand");
                e.printStackTrace();
                chemdahHologramHandler2 = new ArmorStandHologramHandler();
            }
            chemdahHologramHandler = chemdahHologramHandler2;
        } else {
            chemdahHologramHandler = new ArmorStandHologramHandler();
        }
        return chemdahHologramHandler;
    }

    public static final /* synthetic */ ChemdahHologramHandler access$selectVanillaHandler(ChemdahHologramFactory $this) {
        return $this.selectVanillaHandler();
    }
}

