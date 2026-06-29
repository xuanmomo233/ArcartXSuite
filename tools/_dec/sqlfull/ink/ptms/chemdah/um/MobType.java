/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.Mob;
import kotlin.Metadata;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H&R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005R\u0012\u0010\u0006\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u0012\u0010\n\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\tR\u0012\u0010\f\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\t\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0014\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/MobType;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "entityType", "getEntityType", "id", "getId", "spawn", "Link/ptms/chemdah/um/Mob;", "location", "Lorg/bukkit/Location;", "level", "", "common"})
public interface MobType {
    @NotNull
    public String getId();

    @NotNull
    public String getDisplayName();

    @NotNull
    public String getEntityType();

    @NotNull
    public ConfigurationSection getConfig();

    @NotNull
    public Mob spawn(@NotNull Location var1, double var2);
}

