/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common.platform;

import ink.ptms.chemdah.taboolib.common.platform.Platform;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface PlatformSide {
    public Platform[] value();
}

