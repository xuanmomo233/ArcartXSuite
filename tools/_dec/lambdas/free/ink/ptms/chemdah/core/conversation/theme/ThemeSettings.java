/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.XSound
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XSound;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b&\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014J\b\u0010\u0015\u001a\u00020\u0016H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u000f\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000e\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "sound", "Link/ptms/chemdah/taboolib/library/xseries/XSound;", "getSound", "()Link/ptms/chemdah/taboolib/library/xseries/XSound;", "soundPitch", "", "getSoundPitch", "()F", "soundVolume", "getSoundVolume", "playSound", "", "session", "Link/ptms/chemdah/core/conversation/Session;", "toString", "", "Chemdah"})
public abstract class ThemeSettings {
    @NotNull
    private final ConfigurationSection root;
    @Nullable
    private final XSound sound;
    private final float soundPitch;
    private final float soundVolume;

    public ThemeSettings(@NotNull ConfigurationSection root2) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        this.sound = XSound.matchXSound((String)String.valueOf(this.root.getString("sound.name"))).orElse(null);
        this.soundPitch = (float)this.root.getDouble("sound.p");
        this.soundVolume = (float)this.root.getDouble("sound.v");
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @Nullable
    public final XSound getSound() {
        return this.sound;
    }

    public final float getSoundPitch() {
        return this.soundPitch;
    }

    public final float getSoundVolume() {
        return this.soundVolume;
    }

    public final void playSound(@NotNull Session session) {
        block1: {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            String[] stringArray = new String[]{"NO_EFFECT:SOUND"};
            if (!session.getConversation().noFlag(stringArray)) break block1;
            XSound xSound = this.sound;
            if (xSound != null) {
                xSound.play((Entity)session.getPlayer(), this.soundPitch, this.soundVolume);
            }
        }
    }

    @NotNull
    public String toString() {
        return "ThemeSettings(root=" + this.root + ", sound=" + this.sound + ", soundPitch=" + this.soundPitch + ", soundVolume=" + this.soundVolume + ')';
    }
}

