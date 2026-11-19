package xuanmo.arcartxsuite.api.util;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记录正在替换的模块文件及原文。由宿主类加载器持有，模块停用后也可供新实例接续。
 * 这里只登记状态，调用者负责线程调度和文件 I/O。
 */
public final class ActiveFileReplacement implements AutoCloseable {
    private static final ConcurrentMap<Path, ActiveFileReplacement> ACTIVE = new ConcurrentHashMap<>();

    private final Path path;
    private final String previousText;
    private final CompletableFuture<Void> finished = new CompletableFuture<>();

    private ActiveFileReplacement(Path path, String previousText) {
        this.path = path;
        this.previousText = previousText;
    }

    public static ActiveFileReplacement begin(Path path, String previousText) {
        var replacement = new ActiveFileReplacement(path.toAbsolutePath().normalize(), previousText);
        if (ACTIVE.putIfAbsent(replacement.path, replacement) != null) {
            throw new IllegalStateException("文件仍在保存：" + path.getFileName());
        }
        return replacement;
    }

    public static ActiveFileReplacement current(Path path) {
        return ACTIVE.get(path.toAbsolutePath().normalize());
    }

    public String previousText() { return previousText; }

    public CompletionStage<Void> finished() { return finished.minimalCompletionStage(); }

    @Override
    public void close() {
        ACTIVE.remove(path, this);
        finished.complete(null);
    }
}
