/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.impl5.Mob;
import ink.ptms.chemdah.um.impl5.MobConfiguration;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.core.mobs.ActiveMob;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0018H\u0016R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\fR\u0014\u0010\r\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\fR\u0014\u0010\u000f\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/um/impl5/MobType;", "Link/ptms/chemdah/um/MobType;", "source", "Lio/lumine/mythic/api/mobs/MythicMob;", "(Lio/lumine/mythic/api/mobs/MythicMob;)V", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "entityType", "getEntityType", "id", "getId", "getSource", "()Lio/lumine/mythic/api/mobs/MythicMob;", "spawn", "Link/ptms/chemdah/um/Mob;", "location", "Lorg/bukkit/Location;", "level", "", "implementation-v5"})
public final class MobType
implements ink.ptms.chemdah.um.MobType {
    @NotNull
    private final MythicMob source;

    public MobType(@NotNull MythicMob source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
    }

    @NotNull
    public final MythicMob getSource() {
        return this.source;
    }

    @Override
    @NotNull
    public String getId() {
        String string = this.source.getInternalName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getInternalName(...)");
        return string;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        String string = this.source.getDisplayName().get();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"get(...)");
        return string;
    }

    @Override
    @NotNull
    public String getEntityType() {
        return this.source.getEntityType().name();
    }

    @Override
    @NotNull
    public ConfigurationSection getConfig() {
        MythicConfig mythicConfig = this.source.getConfig();
        Intrinsics.checkNotNullExpressionValue((Object)mythicConfig, (String)"getConfig(...)");
        return new MobConfiguration(mythicConfig);
    }

    @Override
    @NotNull
    public ink.ptms.chemdah.um.Mob spawn(@NotNull Location location, double level) {
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        ActiveMob activeMob = this.source.spawn(UtilsKt.toMythic(location), level);
        Intrinsics.checkNotNullExpressionValue((Object)activeMob, (String)"spawn(...)");
        return new Mob(activeMob);
    }
}

