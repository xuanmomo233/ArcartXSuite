/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.Adyeshach
 *  ink.ptms.adyeshach.core.AdyeshachHologram
 *  ink.ptms.adyeshach.core.AdyeshachHologramHandler
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker.hologram.impl;

import ink.ptms.adyeshach.core.Adyeshach;
import ink.ptms.adyeshach.core.AdyeshachHologram;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologram;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologramHandler;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\rB\u0005\u00a2\u0006\u0002\u0010\u0002J&\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u0016J\b\u0010\f\u001a\u00020\u000bH\u0016\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/impl/AdyeshachHologramHandler;", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramHandler;", "()V", "createHologram", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "player", "Lorg/bukkit/entity/Player;", "location", "Lorg/bukkit/Location;", "content", "", "", "getName", "AdyeshachHologramImpl", "Chemdah"})
public final class AdyeshachHologramHandler
implements ChemdahHologramHandler {
    @Override
    @NotNull
    public ChemdahHologram createHologram(@NotNull Player player2, @NotNull Location location, @NotNull List<String> content) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter(content, (String)"content");
        AdyeshachHologram hologram = ink.ptms.adyeshach.core.AdyeshachHologramHandler.createHologram$default((ink.ptms.adyeshach.core.AdyeshachHologramHandler)Adyeshach.INSTANCE.api().getHologramHandler(), (Player)player2, (Location)location, content, (boolean)false, (int)8, null);
        return new AdyeshachHologramImpl(hologram);
    }

    @Override
    @NotNull
    public String getName() {
        return "Adyeshach";
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u0005\u001a\u00020\u0006H\u0016J\u0010\u0010\u0007\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u0016\u0010\n\u001a\u00020\u00062\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fH\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/impl/AdyeshachHologramHandler$AdyeshachHologramImpl;", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "adyHologram", "Link/ptms/adyeshach/core/AdyeshachHologram;", "(Link/ptms/adyeshach/core/AdyeshachHologram;)V", "remove", "", "teleport", "location", "Lorg/bukkit/Location;", "update", "content", "", "", "Chemdah"})
    public static final class AdyeshachHologramImpl
    implements ChemdahHologram {
        @NotNull
        private final AdyeshachHologram adyHologram;

        public AdyeshachHologramImpl(@NotNull AdyeshachHologram adyHologram) {
            Intrinsics.checkNotNullParameter((Object)adyHologram, (String)"adyHologram");
            this.adyHologram = adyHologram;
        }

        @Override
        public void teleport(@NotNull Location location) {
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            this.adyHologram.teleport(location);
        }

        @Override
        public void update(@NotNull List<String> content) {
            Intrinsics.checkNotNullParameter(content, (String)"content");
            this.adyHologram.update(content);
        }

        @Override
        public void remove() {
            this.adyHologram.remove();
        }
    }
}

