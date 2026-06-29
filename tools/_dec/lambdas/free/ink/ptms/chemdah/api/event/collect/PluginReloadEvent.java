/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0006\u0018\u00002\u00020\u0001:\u0004\u0003\u0004\u0005\u0006B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/api/event/collect/PluginReloadEvent;", "", "()V", "Conversation", "Module", "Quest", "WizardModel", "Chemdah"})
public final class PluginReloadEvent {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002R\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/api/event/collect/PluginReloadEvent$Conversation;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "()V", "allowCancelled", "", "getAllowCancelled", "()Z", "Chemdah"})
    public static final class Conversation
    extends BukkitProxyEvent {
        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002R\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/api/event/collect/PluginReloadEvent$Module;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "()V", "allowCancelled", "", "getAllowCancelled", "()Z", "Chemdah"})
    public static final class Module
    extends BukkitProxyEvent {
        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u00038VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0002\u0010\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/api/event/collect/PluginReloadEvent$Quest;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "isInt", "", "(Z)V", "allowCancelled", "getAllowCancelled", "()Z", "Chemdah"})
    public static final class Quest
    extends BukkitProxyEvent {
        private final boolean isInt;

        public Quest(boolean isInt) {
            this.isInt = isInt;
        }

        public final boolean isInt() {
            return this.isInt;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002R\u0014\u0010\u0003\u001a\u00020\u00048VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/api/event/collect/PluginReloadEvent$WizardModel;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "()V", "allowCancelled", "", "getAllowCancelled", "()Z", "Chemdah"})
    public static final class WizardModel
    extends BukkitProxyEvent {
        public boolean getAllowCancelled() {
            return false;
        }
    }
}

