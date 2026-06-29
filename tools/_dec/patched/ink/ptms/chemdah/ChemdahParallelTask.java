/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah;

import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/ChemdahParallelTask;", "", "()V", "CONVERSATION_WATCH", "", "DATABASE_SETUP", "KETHER_INIT", "MESSAGING_SETUP", "METRICS", "MODULE_SETUP", "QUEST_COMPONENTS_INIT", "QUEST_WATCH", "Chemdah"})
public final class ChemdahParallelTask {
    @NotNull
    public static final ChemdahParallelTask INSTANCE = new ChemdahParallelTask();
    @NotNull
    public static final String DATABASE_SETUP = "chemdah_database_setup";
    @NotNull
    public static final String MESSAGING_SETUP = "chemdah_messaging_setup";
    @NotNull
    public static final String MODULE_SETUP = "chemdah_module_setup";
    @NotNull
    public static final String QUEST_COMPONENTS_INIT = "chemdah_quest_components_init";
    @NotNull
    public static final String QUEST_WATCH = "chemdah_quest_watch";
    @NotNull
    public static final String CONVERSATION_WATCH = "chemdah_conversation_watch";
    @NotNull
    public static final String KETHER_INIT = "chemdah_kether_init";
    @NotNull
    public static final String METRICS = "chemdah_metrics";

    private ChemdahParallelTask() {
    }
}

