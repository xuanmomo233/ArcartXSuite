package kotlin1822;

/**
 * 编译期占位：运行时由 TabooLib 重定位的 {@code kotlin1822.Pair} 提供。
 * 打包时通过 build.gradle.kts 排除，不会进入最终 JAR。
 */
public final class Pair<A, B> {
    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public A component1() {
        return first;
    }

    public B component2() {
        return second;
    }
}
