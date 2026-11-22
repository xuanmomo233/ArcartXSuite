package xuanmo.arcartxsuite.bridge;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xuanmo.arcartxsuite.api.bridge.ContentModerator.ModerationResult;

final class ModerationRules {
    private final boolean cancelOnMatch;
    private final String replacement;
    private final ModerationWords words;
    private final List<Pattern> patterns;
    private final ModerationWords whitelist;
    private final Set<Integer> ignoredCharacters = new HashSet<>();
    private final Set<Integer> foldedIgnoredCharacters = new HashSet<>();
    private final List<String> ignoredSequences;
    private final boolean ignoreSeparators;
    private final Function<String, List<Span>> redact;

    ModerationRules(boolean cancelOnMatch, String replacement, List<String> words, List<Pattern> patterns,
                    List<String> whitelist, List<String> ignoredPunctuation, boolean ignoreSeparators,
                    Function<String, List<Span>> redact) {
        this.cancelOnMatch = cancelOnMatch;
        this.replacement = replacement;
        this.words = new ModerationWords(words);
        this.patterns = patterns;
        this.whitelist = new ModerationWords(whitelist);
        this.ignoreSeparators = ignoreSeparators;
        this.redact = redact;
        ignoredSequences = ignoredPunctuation.stream()
            .filter(value -> value.codePointCount(0, value.length()) > 1)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        for (String value : ignoredPunctuation) {
            if (value.codePointCount(0, value.length()) == 1) {
                int point = value.codePointAt(0);
                ignoredCharacters.add(Character.toLowerCase(point));
                foldedIgnoredCharacters.add(Character.toLowerCase(ModerationWords.foldWidth(point)));
            }
        }
    }

    ModerationResult filter(String text, ModerationWords remote) {
        View loose = compact(text, true);
        Matches matches = new Matches(text);
        scanWords(whitelist, text, loose, matches::allow);
        matches.finishWhitelist();

        if (scanWords(words, text, loose, matches::hit) || scanWords(remote, text, loose, matches::hit)) {
            return ModerationResult.block(text);
        }
        if (!patterns.isEmpty()) {
            View configured = compact(text, false);
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(configured.text());
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    if (isBoundary(configured.text(), start) && isBoundary(configured.text(), end)
                        && matches.hit(configured.start(start), configured.end(start, end))) {
                        return ModerationResult.block(text);
                    }
                }
            }
        }
        if (redact != null) {
            for (Span span : redact.apply(text)) {
                if (matches.hit(span.start(), span.end())) return ModerationResult.block(text);
            }
        }
        return ModerationResult.pass(matches.replace());
    }

    private static boolean scanWords(ModerationWords words, String text, View loose,
                                     ModerationWords.MatchVisitor visitor) {
        return words.scan(text, visitor) || (loose.offsets() != null && words.scan(loose.text(),
            (start, end) -> visitor.visit(loose.start(start), loose.end(start, end))));
    }

    private static boolean isBoundary(String text, int index) {
        return index == 0 || index == text.length()
            || !Character.isSurrogatePair(text.charAt(index - 1), text.charAt(index));
    }

    private View compact(String input, boolean literal) {
        StringBuilder text = null;
        int[] offsets = null;
        for (int start = 0; start < input.length(); ) {
            int point = input.codePointAt(start);
            int end = start + Character.charCount(point);
            int ignored = ignoredLength(input, start, point, literal);
            if (ignored > 0) {
                if (text == null) {
                    text = new StringBuilder(input.length()).append(input, 0, start);
                    offsets = new int[input.length()];
                    for (int i = 0; i < start; i++) offsets[i] = i;
                }
                start += ignored;
                continue;
            }
            if (text != null) {
                for (int i = start; i < end; i++) {
                    offsets[text.length()] = i;
                    text.append(input.charAt(i));
                }
            }
            start = end;
        }
        return new View(text == null ? input : text.toString(), offsets, input.length());
    }

    private int ignoredLength(String input, int start, int point, boolean literal) {
        for (String sequence : ignoredSequences) {
            if (input.regionMatches(true, start, sequence, 0, sequence.length())) return sequence.length();
        }
        boolean ignored = literal
            ? foldedIgnoredCharacters.contains(Character.toLowerCase(ModerationWords.foldWidth(point)))
                || (ignoreSeparators && isSeparator(point))
            : ignoredCharacters.contains(Character.toLowerCase(point));
        return ignored ? Character.charCount(point) : 0;
    }

    private static boolean isSeparator(int point) {
        if (Character.isWhitespace(point) || Character.isSpaceChar(point)) return true;
        return switch (Character.getType(point)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    record Span(int start, int end) {}

    private record View(String text, int[] offsets, int originalLength) {
        int start(int index) {
            if (offsets == null) return index;
            return index == text.length() ? originalLength : offsets[index];
        }

        int end(int start, int end) {
            if (offsets == null) return end;
            return start == end ? start(end) : offsets[end - 1] + 1;
        }
    }

    private final class Matches {
        private final String text;
        private int[] allowedThrough;
        private int[] ends;

        private Matches(String text) {
            this.text = text;
        }

        boolean allow(int start, int end) {
            if (allowedThrough == null) allowedThrough = new int[text.length()];
            allowedThrough[start] = Math.max(allowedThrough[start], end);
            return false;
        }

        void finishWhitelist() {
            if (allowedThrough == null) return;
            for (int i = 1; i < allowedThrough.length; i++) {
                allowedThrough[i] = Math.max(allowedThrough[i], allowedThrough[i - 1]);
            }
        }

        boolean hit(int start, int end) {
            if (allowedThrough != null && start < allowedThrough.length
                && allowedThrough[start] >= Math.max(start + 1, end)) return false;
            if (cancelOnMatch) return true;
            if (ends == null) ends = new int[text.length() + 1];
            // One-based values distinguish a zero-width regex hit at offset 0 from no hit.
            ends[start] = Math.max(ends[start], end + 1);
            return false;
        }

        String replace() {
            if (ends == null) return text;
            StringBuilder output = new StringBuilder(text.length());
            int copied = 0;
            for (int start = 0; start < ends.length; start++) {
                if (ends[start] == 0) continue;
                int end = ends[start] - 1;
                for (int next = start + 1; next < end; next++) {
                    end = Math.max(end, ends[next] - 1);
                }
                output.append(text, copied, start).append(replacement);
                copied = end;
                start = Math.max(start, end - 1);
            }
            return output.append(text, copied, text.length()).toString();
        }
    }
}
