package xuanmo.arcartxsuite.bridge;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aho–Corasick index, built once per vocabulary and shared by the message handlers. */
final class ModerationWords {
    static final ModerationWords EMPTY = new ModerationWords(List.of());

    private final Node root = new Node();

    ModerationWords(Collection<String> words) {
        for (String word : words) {
            if (word.isBlank()) continue;
            Node node = root;
            for (int i = 0; i < word.length(); ) {
                int point = word.codePointAt(i);
                node = node.children.computeIfAbsent(fold(point), key -> new Node());
                i += Character.charCount(point);
            }
            node.wordLength = word.length();
        }

        var queue = new ArrayDeque<Node>();
        for (Node child : root.children.values()) {
            child.failure = root;
            queue.addLast(child);
        }
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            for (var entry : node.children.entrySet()) {
                Node child = entry.getValue();
                child.failure = advance(node.failure, entry.getKey());
                child.suffix = child.failure.wordLength == 0 ? child.failure.suffix : child.failure;
                queue.addLast(child);
            }
        }
    }

    /** The visitor returns true to stop at a match. Offsets are UTF-16 indices. */
    boolean scan(String text, MatchVisitor visitor) {
        if (root.children.isEmpty()) return false;
        Node node = root;
        for (int end = 0; end < text.length(); ) {
            int point = text.codePointAt(end);
            end += Character.charCount(point);
            node = advance(node, fold(point));
            for (Node match = node; match != null; match = match.suffix) {
                if (match.wordLength != 0 && visitor.visit(end - match.wordLength, end)) return true;
            }
        }
        return false;
    }

    private Node advance(Node node, int key) {
        Node next = node.children.get(key);
        while (next == null && node != root) {
            node = node.failure;
            next = node.children.get(key);
        }
        return next == null ? root : next;
    }

    private static int fold(int value) {
        return Character.toLowerCase(foldWidth(value));
    }

    static int foldWidth(int value) {
        if (value >= '\uFF01' && value <= '\uFF5E') value -= 0xFEE0;
        if (value == '\u3000') value = ' ';
        return value;
    }

    @FunctionalInterface
    interface MatchVisitor {
        boolean visit(int start, int end);
    }

    private static final class Node {
        private final Map<Integer, Node> children = new HashMap<>(2);
        private Node failure;
        private Node suffix;
        private int wordLength;
    }
}
