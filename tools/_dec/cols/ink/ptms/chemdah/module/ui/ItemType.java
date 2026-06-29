/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.ui;

import kotlin.Metadata;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\b\n\u0002\b\f\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/ui/ItemType;", "", "priority", "", "(Ljava/lang/String;II)V", "getPriority", "()I", "INFO", "FILTER", "QUEST_STARTED", "QUEST_STARTED_SHARED", "QUEST_CAN_START", "QUEST_CANNOT_START", "QUEST_COMPLETE", "QUEST_UNAVAILABLE", "Chemdah"})
public final class ItemType
extends Enum<ItemType> {
    private final int priority;
    public static final /* enum */ ItemType INFO = new ItemType(0);
    public static final /* enum */ ItemType FILTER = new ItemType(0);
    public static final /* enum */ ItemType QUEST_STARTED = new ItemType(11);
    public static final /* enum */ ItemType QUEST_STARTED_SHARED = new ItemType(10);
    public static final /* enum */ ItemType QUEST_CAN_START = new ItemType(9);
    public static final /* enum */ ItemType QUEST_CANNOT_START = new ItemType(8);
    public static final /* enum */ ItemType QUEST_COMPLETE = new ItemType(7);
    public static final /* enum */ ItemType QUEST_UNAVAILABLE = new ItemType(0);
    private static final /* synthetic */ ItemType[] $VALUES;

    private ItemType(int priority) {
        this.priority = priority;
    }

    public final int getPriority() {
        return this.priority;
    }

    public static ItemType[] values() {
        return (ItemType[])$VALUES.clone();
    }

    public static ItemType valueOf(String value2) {
        return Enum.valueOf(ItemType.class, value2);
    }

    static {
        $VALUES = itemTypeArray = new ItemType[]{ItemType.INFO, ItemType.FILTER, ItemType.QUEST_STARTED, ItemType.QUEST_STARTED_SHARED, ItemType.QUEST_CAN_START, ItemType.QUEST_CANNOT_START, ItemType.QUEST_COMPLETE, ItemType.QUEST_UNAVAILABLE};
    }
}

