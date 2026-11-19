package xuanmo.arcartxsuite.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.bridge.ModerationRules.Span;

class ContentModerationTest {
    @Test
    void replacesSplitAndFullWidthWordsInOriginalText() {
        var rules = rules(false, false, List.of("spam", "𐐨字"), List.of(), List.of());
        assertEquals("😀 【$\\】 and $\\。", rules.filter("😀 【Ｓ．Ｐ.Ａ.Ｍ】 and 𐐀……字。", ModerationWords.EMPTY).text());
    }

    @Test
    void optionalUnicodeSeparatorsApplyToWordsWhileRegexUsesConfiguredPunctuation() {
        String input = "s\t—p。am / X\tY";
        var patterns = List.of(Pattern.compile("X\\sY"));
        var strict = rules(false, false, List.of("spam"), patterns, List.of());
        var loose = rules(false, true, List.of("spam"), patterns, List.of());
        assertEquals("s\t—p。am / $\\", strict.filter(input, ModerationWords.EMPTY).text());
        assertEquals("$\\ / $\\", loose.filter(input, ModerationWords.EMPTY).text());
    }

    @Test
    void mergesOverlappingMatchesFromWordsPatternsAndRedact() {
        var rules = new ModerationRules(false, "*", List.of("ab"), List.of(Pattern.compile("ef")), List.of(),
            List.of("."), false, text -> List.of(new Span(3, 6)));
        assertEquals("⟨*⟩", rules.filter("⟨a.b.c.d.ef⟩", new ModerationWords(List.of("cde"))).text());
    }

    @Test
    void confirmsSuffixMatchesAndWhitelistCoverageInCancelMode() {
        var rules = rules(true, false, List.of("ass", "class"), List.of(), List.of("class"));
        assertFalse(rules.filter("class", ModerationWords.EMPTY).blocked());
        assertTrue(rules.filter("class a.s.s", ModerationWords.EMPTY).blocked());
        assertTrue(rules.filter("cl.ass assassin", ModerationWords.EMPTY).blocked());
        var suffix = rules(true, false, List.of("he", "sher"), List.of(), List.of());
        assertTrue(suffix.filter("she", ModerationWords.EMPTY).blocked());
    }

    @Test
    void retainsLiteralPunctuationInRulesAndPreservesUnmatchedRegexText() {
        var rules = rules(false, false, List.of("c++"), List.of(Pattern.compile("12")), List.of());
        assertEquals("c $\\, abc.$\\...", rules.filter("c C++, abc.1.2...", ModerationWords.EMPTY).text());
        var boundaries = rules(false, false, List.of(), List.of(Pattern.compile("(?=)")), List.of());
        assertEquals("$\\😀$\\", boundaries.filter("😀", ModerationWords.EMPTY).text());
    }

    @Test
    void cloudSourcesKeepTheirLastSuccessfulWordsAndAreRemovedWhenDisabled(@TempDir Path dir) throws Exception {
        Path first = Files.writeString(dir.resolve("first.txt"), "\uFEFF# test\n云端甲\n跳过词\n");
        Path second = Files.writeString(dir.resolve("second.json"), "{\"data\":[\"云端乙\"]}");
        List<String> sources = List.of(first.toUri().toString(), second.toUri().toString());
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var cloud = new ModerationCloudWords(sources, List.of("跳过词"), logger, null);
        cloud.refresh();
        var rules = rules(false, false, List.of(), List.of(), List.of());
        assertEquals("$\\-$\\-跳过词", rules.filter("云端甲-云端乙-跳过词", cloud.matcher()).text());

        Files.writeString(first, "{");
        Files.writeString(second, "[\"云端丙\"]");
        cloud.refresh();
        assertEquals("$\\-云端乙-$\\", rules.filter("云端甲-云端乙-云端丙", cloud.matcher()).text());

        var reduced = new ModerationCloudWords(List.of(sources.get(1)), List.of(), logger, cloud);
        assertEquals("云端甲-$\\", rules.filter("云端甲-云端丙", reduced.matcher()).text());
        var disabled = new ModerationCloudWords(List.of(), List.of(), logger, cloud);
        assertEquals("云端甲-云端丙", rules.filter("云端甲-云端丙", disabled.matcher()).text());
    }

    private static ModerationRules rules(boolean cancel, boolean ignoreSeparators, List<String> words,
                                         List<Pattern> patterns, List<String> whitelist) {
        return new ModerationRules(cancel, "$\\", words, patterns, whitelist, List.of(".", "……", " ", "+"),
            ignoreSeparators, null);
    }
}
