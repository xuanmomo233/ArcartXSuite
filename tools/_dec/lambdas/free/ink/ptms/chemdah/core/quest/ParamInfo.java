/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.annotation.Retention
 *  kotlin.annotation.Target
 *  kotlin1822.annotation.AnnotationRetention
 *  kotlin1822.annotation.AnnotationTarget
 */
package ink.ptms.chemdah.core.quest;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import kotlin.Metadata;
import kotlin.annotation.Retention;
import kotlin.annotation.Target;
import kotlin1822.annotation.AnnotationRetention;
import kotlin1822.annotation.AnnotationTarget;

@Target(allowedTargets={AnnotationTarget.CLASS})
@Retention(value=AnnotationRetention.RUNTIME)
@java.lang.annotation.Retention(value=RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(value={ElementType.TYPE})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u001b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0005\b\u0087\u0002\u0018\u00002\u00020\u0001B4\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0005\u001a\u00020\u0006\u0012\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\b\u0012\b\b\u0002\u0010\t\u001a\u00020\u0003R\u000f\u0010\t\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u000f\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\u0002\u0010\nR\u0015\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\b\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\u000bR\u000f\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\fR\u000f\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\n\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/ParamInfo;", "", "name", "", "type", "required", "", "options", "", "description", "()Ljava/lang/String;", "()[Ljava/lang/String;", "()Z", "Chemdah"})
public @interface ParamInfo {
    public String name();

    public String type();

    public boolean required() default false;

    public String[] options() default {};

    public String description() default "";
}

