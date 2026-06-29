/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import java.io.File;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/api/event/collect/TemplateEvents;", "", "()V", "ControlHook", "Load", "Chemdah"})
public final class TemplateEvents {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010$\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0012\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\u0002\u0010\tR\u0014\u0010\n\u001a\u00020\u000b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u001c\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013R\u001d\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/api/event/collect/TemplateEvents$ControlHook;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "template", "Link/ptms/chemdah/core/quest/Template;", "type", "", "map", "", "", "(Link/ptms/chemdah/core/quest/Template;Ljava/lang/String;Ljava/util/Map;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "control", "Link/ptms/chemdah/core/quest/addon/data/Control;", "getControl", "()Link/ptms/chemdah/core/quest/addon/data/Control;", "setControl", "(Link/ptms/chemdah/core/quest/addon/data/Control;)V", "getMap", "()Ljava/util/Map;", "getTemplate", "()Link/ptms/chemdah/core/quest/Template;", "getType", "()Ljava/lang/String;", "Chemdah"})
    public static final class ControlHook
    extends BukkitProxyEvent {
        @NotNull
        private final Template template;
        @NotNull
        private final String type;
        @NotNull
        private final Map<String, Object> map;
        @Nullable
        private Control control;

        public ControlHook(@NotNull Template template, @NotNull String type, @NotNull Map<String, ? extends Object> map) {
            Intrinsics.checkNotNullParameter((Object)template, (String)"template");
            Intrinsics.checkNotNullParameter((Object)type, (String)"type");
            Intrinsics.checkNotNullParameter(map, (String)"map");
            this.template = template;
            this.type = type;
            this.map = map;
        }

        @NotNull
        public final Template getTemplate() {
            return this.template;
        }

        @NotNull
        public final String getType() {
            return this.type;
        }

        @NotNull
        public final Map<String, Object> getMap() {
            return this.map;
        }

        public boolean getAllowCancelled() {
            return false;
        }

        @Nullable
        public final Control getControl() {
            return this.control;
        }

        public final void setControl(@Nullable Control control) {
            this.control = control;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001f\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/TemplateEvents$Load;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "file", "Ljava/io/File;", "id", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Ljava/io/File;Ljava/lang/String;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getFile", "()Ljava/io/File;", "getId", "()Ljava/lang/String;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "Chemdah"})
    public static final class Load
    extends BukkitProxyEvent {
        @Nullable
        private final File file;
        @NotNull
        private final String id;
        @NotNull
        private final ConfigurationSection root;

        public Load(@Nullable File file, @NotNull String id2, @NotNull ConfigurationSection root2) {
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            this.file = file;
            this.id = id2;
            this.root = root2;
        }

        @Nullable
        public final File getFile() {
            return this.file;
        }

        @NotNull
        public final String getId() {
            return this.id;
        }

        @NotNull
        public final ConfigurationSection getRoot() {
            return this.root;
        }
    }
}

