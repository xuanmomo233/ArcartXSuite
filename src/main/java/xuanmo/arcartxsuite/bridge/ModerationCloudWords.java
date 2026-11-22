package xuanmo.arcartxsuite.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** One refresh task owns one dictionary, so old tasks cannot publish into a reloaded configuration. */
final class ModerationCloudWords {
    private final List<String> urls;
    private final Set<String> ignored;
    private final Logger logger;
    private final Set<String> failing = new HashSet<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    ModerationCloudWords(List<String> urls, List<String> ignored, Logger logger, ModerationCloudWords previous) {
        this.urls = urls.stream().map(String::strip).filter(url -> !url.isEmpty()).distinct().toList();
        this.ignored = new HashSet<>();
        ignored.forEach(word -> this.ignored.add(normalize(word)));
        this.logger = logger;

        if (previous == null) {
            snapshot = new Snapshot(Map.of(), ModerationWords.EMPTY);
        } else if (this.urls.equals(previous.urls) && this.ignored.equals(previous.ignored)) {
            snapshot = previous.snapshot;
        } else {
            Map<String, Set<String>> retained = new HashMap<>(previous.snapshot.sources());
            retained.keySet().retainAll(this.urls);
            snapshot = compile(retained);
        }
    }

    ModerationWords matcher() {
        return snapshot.matcher();
    }

    boolean hasSources() {
        return !urls.isEmpty();
    }

    void refresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<String, Set<String>> sources = new HashMap<>(snapshot.sources());
            for (String url : urls) {
                try {
                    sources.put(url, parse(download(url)));
                    if (failing.remove(url)) logger.info("云端敏感词库拉取已恢复：" + url);
                } catch (Exception e) {
                    String message = "拉取云端敏感词库失败：" + url + " | " + e.getMessage();
                    if (failing.add(url)) logger.warning(message);
                    else logger.fine(message);
                }
            }
            if (!sources.equals(snapshot.sources())) snapshot = compile(sources);
        } finally {
            refreshing.set(false);
        }
    }

    private Snapshot compile(Map<String, Set<String>> sources) {
        Set<String> words = new HashSet<>();
        for (Set<String> source : sources.values()) words.addAll(source);
        words.removeAll(ignored);
        return new Snapshot(Map.copyOf(sources), words.isEmpty() ? ModerationWords.EMPTY : new ModerationWords(words));
    }

    private static String download(String url) throws IOException {
        var connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (connection instanceof HttpURLConnection http) http.disconnect();
        }
    }

    private static Set<String> parse(String content) throws IOException {
        String text = (content.startsWith("\uFEFF") ? content.substring(1) : content).strip();
        if (text.isEmpty()) throw new IOException("响应内容为空");
        Set<String> words = new LinkedHashSet<>();
        if (text.startsWith("{") || text.startsWith("[")) {
            for (JsonElement entry : wordArray(JsonParser.parseString(text))) {
                if (!entry.isJsonNull()) addWord(words, entry.getAsString());
            }
        } else {
            for (String line : text.split("\\R")) addWord(words, line);
        }
        return Set.copyOf(words);
    }

    private static JsonArray wordArray(JsonElement json) throws IOException {
        if (json.isJsonArray()) return json.getAsJsonArray();
        if (json.isJsonObject()) {
            for (String key : List.of("words", "data", "list")) {
                JsonElement value = json.getAsJsonObject().get(key);
                if (value != null && value.isJsonArray()) return value.getAsJsonArray();
            }
        }
        throw new IOException("JSON 缺少 words/data/list 数组字段");
    }

    private static void addWord(Set<String> words, String value) {
        String word = normalize(value);
        if (!word.isEmpty() && !word.startsWith("#")) words.add(word);
    }

    private static String normalize(String word) {
        return word.strip().toLowerCase(Locale.ROOT);
    }

    private record Snapshot(Map<String, Set<String>> sources, ModerationWords matcher) {}
}
