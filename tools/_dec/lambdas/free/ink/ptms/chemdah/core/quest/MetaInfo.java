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

import ink.ptms.chemdah.core.quest.ParamInfo;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import kotlin.Metadata;
import kotlin1822.annotation.AnnotationRetention;
import kotlin1822.annotation.AnnotationTarget;

@kotlin.annotation.Target(allowedTargets={AnnotationTarget.CLASS})
@kotlin.annotation.Retention(value=AnnotationRetention.RUNTIME)
@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.TYPE})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u001b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0087\u0002\u0018\u00002\u00020\u0001BB\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u000e\b\u0002\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00030\u0006\u0012\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\u0006\u0012\u000e\b\u0002\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006R\u0015\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\u0006\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\nR\u0015\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00030\u0006\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\nR\u000f\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u000bR\u0015\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\fR\u000f\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\u0002\u0010\u000b\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/MetaInfo;", "", "source", "", "name", "description", "", "alias", "params", "Link/ptms/chemdah/core/quest/ParamInfo;", "()[Ljava/lang/String;", "()Ljava/lang/String;", "()[Link/ptms/chemdah/core/quest/ParamInfo;", "Chemdah"})
public @interface MetaInfo {
    public String source() default "chemdah";

    public String name();

    public String[] description() default {};

    public String[] alias() default {};

    public ParamInfo[] params() default {};
}

