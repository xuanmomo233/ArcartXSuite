/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.level;

import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0010\u001a\u00020\u0011H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/level/PlayerLevel;", "", "level", "", "experience", "(II)V", "getExperience", "()I", "getLevel", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "", "Chemdah"})
public final class PlayerLevel {
    private final int level;
    private final int experience;

    public PlayerLevel(int level, int experience) {
        this.level = level;
        this.experience = experience;
    }

    public final int getLevel() {
        return this.level;
    }

    public final int getExperience() {
        return this.experience;
    }

    public final int component1() {
        return this.level;
    }

    public final int component2() {
        return this.experience;
    }

    @NotNull
    public final PlayerLevel copy(int level, int experience) {
        return new PlayerLevel(level, experience);
    }

    public static /* synthetic */ PlayerLevel copy$default(PlayerLevel playerLevel, int n, int n2, int n3, Object object) {
        if ((n3 & 1) != 0) {
            n = playerLevel.level;
        }
        if ((n3 & 2) != 0) {
            n2 = playerLevel.experience;
        }
        return playerLevel.copy(n, n2);
    }

    @NotNull
    public String toString() {
        return "PlayerLevel(level=" + this.level + ", experience=" + this.experience + ')';
    }

    public int hashCode() {
        int result = Integer.hashCode(this.level);
        result = result * 31 + Integer.hashCode(this.experience);
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerLevel)) {
            return false;
        }
        PlayerLevel playerLevel = (PlayerLevel)other;
        if (this.level != playerLevel.level) {
            return false;
        }
        return this.experience == playerLevel.experience;
    }
}

