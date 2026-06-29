/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.chemdah.module.wizard.WizardInfo;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/wizard/WizardInfoLoader;", "", "()V", "load", "Link/ptms/chemdah/module/wizard/WizardInfo;", "section", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "Chemdah"})
public class WizardInfoLoader {
    @NotNull
    public WizardInfo load(@NotNull ConfigurationSection section) {
        Intrinsics.checkNotNullParameter((Object)section, (String)"section");
        return new WizardInfo(section);
    }
}

