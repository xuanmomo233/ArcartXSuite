/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.ui.ItemType;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/module/ui/UITemplate;", "", "template", "Link/ptms/chemdah/core/quest/Template;", "itemType", "Link/ptms/chemdah/module/ui/ItemType;", "(Link/ptms/chemdah/core/quest/Template;Link/ptms/chemdah/module/ui/ItemType;)V", "getItemType", "()Link/ptms/chemdah/module/ui/ItemType;", "getTemplate", "()Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
public final class UITemplate {
    @NotNull
    private final Template template;
    @NotNull
    private final ItemType itemType;

    public UITemplate(@NotNull Template template, @NotNull ItemType itemType) {
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        Intrinsics.checkNotNullParameter((Object)((Object)itemType), (String)"itemType");
        this.template = template;
        this.itemType = itemType;
    }

    @NotNull
    public final Template getTemplate() {
        return this.template;
    }

    @NotNull
    public final ItemType getItemType() {
        return this.itemType;
    }
}

