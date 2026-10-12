package xuanmo.arcartxsuite.api.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.*;

class ModuleUiResourceTest {
    private static final Set<Integer> FONT_SIZES = Set.of(36, 48, 54, 64, 72, 81, 100);
    private static final Set<String> SCROLL_LAYOUTS = Set.of("Canvas", "HStack", "VStack", "HGrid");
    private static final Pattern SEND = Pattern.compile("\\bPacket\\.send\\s*\\(");
    private static final Pattern INTERACTION = Pattern.compile("\\b(?:Packet\\.send|Chat\\.say|Chat\\.setMessage|self\\.setItemIcon)\\s*\\(");
    private static final Pattern FIELD = Pattern.compile("\\bpacket\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]");
    private static final Pattern DATA = Pattern.compile("\\bdata\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]");
    private static final Pattern VAL = Pattern.compile("\\bval\\.([\\p{L}_][\\p{L}\\p{N}_]*)");

    private Path root() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return Files.isDirectory(current.resolve("modules")) ? current : current.getParent();
    }

    private List<String[]> contracts() throws Exception {
        try (var stream = getClass().getResourceAsStream("/module-ui-contracts.tsv")) {
            assertNotNull(stream);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.startsWith("#") && !line.isBlank())
                        .map(line -> line.split("\t", -1)).toList();
            }
        }
    }

    @Test
    void contractInventoryCoversAllModuleVisualResources() throws Exception {
        var actual = new TreeSet<String>();
        try (var files = Files.walk(root().resolve("modules"))) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String relative = root().relativize(path).toString().replace('\\', '/');
                if (relative.matches("modules/[^/]+/src/main/resources/arcartx/ui/[^/]+\\.yml")
                        || relative.matches("modules/[^/]+/src/main/resources/[^/]+/card/[^/]+\\.yml")
                        || relative.matches("modules/combateffect/src/main/resources/arcartx/damage_display/[^/]+\\.yml")) {
                    actual.add(relative);
                }
            });
        }
        actual.add("modules/chat/src/main/resources/chat/ui/axs_chat_completion.yml");
        var expected = new TreeSet<String>();
        for (var row : contracts()) assertTrue(expected.add(row[0]), "Duplicate inventory: " + row[0]);
        assertEquals(expected, actual, "Every module UI must have a protocol regression contract");
    }

    @TestFactory
    Stream<DynamicTest> resourcesKeepLayoutAndPacketContracts() throws Exception {
        return contracts().stream().map(contract -> DynamicTest.dynamicTest(contract[0], () -> {
            var yaml = new YamlConfiguration();
            try (var reader = Files.newBufferedReader(root().resolve(contract[0]), StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            String kind = contract[7];
            if ("damage".equals(kind)) {
                damageContract(yaml, contract);
                return;
            }
            boolean card = "card".equals(kind);
            var cardRoot = yaml.getConfigurationSection("root_control");
            if (card) {
                assertNotNull(cardRoot);
                assertEquals(contract[3], cardRoot.getString("type"));
                assertTrue(cardRoot.contains("attribute.width"));
                assertTrue(cardRoot.contains("attribute.height"));
            } else assertNotNull(yaml.getConfigurationSection("ui"));
            List<String> scripts = new ArrayList<>();
            strings(yaml, scripts);
            List<String> calls = new ArrayList<>();
            Set<String> fields = new TreeSet<>();
            for (String script : scripts) {
                calls.addAll(calls(script, card ? INTERACTION : SEND));
                var matcher = (card ? DATA : FIELD).matcher(script);
                while (matcher.find()) fields.add(matcher.group(1));
            }
            calls.sort(String::compareTo);
            assertEquals(contract[1], hash(calls), "Action, parameter order, count or item-icon binding changed");
            assertEquals(contract[2], hash(fields), "Server payload field contract changed");
            if (!card) {
                assertTrue(keys(yaml.getConfigurationSection("ui.packetHandler")).containsAll(csv(contract[3])), "Packet handler removed");
                assertTrue(keys(yaml.getConfigurationSection("ui.action")).containsAll(csv(contract[4])), "Lifecycle handler removed");
                assertEquals(Boolean.parseBoolean(contract[6]), yaml.getBoolean("ui.isHud"), "HUD classification changed");
            }

            var refs = new HashMap<String, ConfigurationSection>();
            var adaptives = new ArrayList<ConfigurationSection>();
            if (card) visit(yaml, refs, adaptives);
            else visit(yaml.getConfigurationSection("controls"), refs, adaptives);
            assertTrue(refs.keySet().containsAll(csv(contract[5])), "Dynamic control alias removed");
            for (String script : scripts) {
                var matcher = VAL.matcher(script);
                while (matcher.find()) assertTrue(refs.containsKey(matcher.group(1)), "Unresolved alias: " + matcher.group(1));
            }
            if (!card && !yaml.getBoolean("ui.isHud")) {
                assertTrue(adaptives.stream().anyMatch(node -> node.getInt("attribute.width") == 1920
                        && node.getInt("attribute.height") == 1080), "Missing 1920x1080 Adaptive design root");
            }
        }));
    }

    private void damageContract(YamlConfiguration yaml, String[] contract) throws Exception {
        var assets = new ArrayList<String>();
        var properties = new TreeSet<String>();
        for (String id : new TreeSet<>(yaml.getKeys(false))) {
            var style = yaml.getConfigurationSection(id);
            assertNotNull(style);
            List<String> textures = style.getStringList("textures");
            int precision = style.getInt("decimalPlaces");
            assets.add(id + "\t" + precision + "\t" + String.join(",", textures));
            for (String key : style.getKeys(false)) properties.add(id + "." + key);
            assertEquals(id.endsWith("-dodged") ? 10 : 11, textures.size());
            for (String dimension : List.of("textureWidth", "textureHeight")) {
                double value = style.getDouble(dimension);
                assertTrue(value > 0 && value <= 120, id + ": " + dimension);
            }
            double minimum = style.getDouble("randomMin"), maximum = style.getDouble("randomMax");
            assertTrue(minimum >= 0 && minimum <= maximum && maximum <= 1.5, id);
        }
        assertEquals(contract[1], hash(assets), "Damage style ID, precision or texture sequence changed");
        assertEquals(contract[2], hash(properties), "Damage style property schema changed");
    }

    private void visit(ConfigurationSection controls, Map<String, ConfigurationSection> refs,
                       List<ConfigurationSection> adaptives) {
        assertNotNull(controls, "Controls missing");
        for (String key : controls.getKeys(false)) {
            var node = controls.getConfigurationSection(key);
            assertNotNull(node, key);
            String type = node.getString("type");
            assertNotNull(type, node.getCurrentPath());
            var attr = node.getConfigurationSection("attribute");
            assertNotNull(attr, node.getCurrentPath());
            String ref = node.getString("val");
            if (ref != null) assertNull(refs.put(ref, node), "Duplicate alias: " + ref);
            if ("Adaptive".equals(type)) adaptives.add(node);
            if ("Canvas".equals(type)) {
                assertTrue(attr.contains("width"), node.getCurrentPath());
                assertTrue(attr.contains("height"), node.getCurrentPath());
            }
            if ("HStack".equals(type)) assertTrue(attr.contains("height"), node.getCurrentPath());
            if ("VStack".equals(type)) assertTrue(attr.contains("width"), node.getCurrentPath());
            if ("Text".equals(type)) {
                assertTrue(FONT_SIZES.contains(attr.getInt("fontSize")), node.getCurrentPath());
                for (String forbidden : Set.of("width", "lineSpace", "shadow")) {
                    assertFalse(attr.contains(forbidden), node.getCurrentPath() + ": " + forbidden);
                }
            }
            var children = node.getConfigurationSection("children");
            if ("Scroll".equals(type)) {
                assertNotNull(children, node.getCurrentPath());
                assertEquals(1, children.getKeys(false).size(), node.getCurrentPath());
                String child = children.getKeys(false).iterator().next();
                assertTrue(SCROLL_LAYOUTS.contains(children.getString(child + ".type")), node.getCurrentPath());
            }
            if (children != null) visit(children, refs, adaptives);
        }
    }

    private void strings(Object value, List<String> result) {
        if (value instanceof String string) result.add(string);
        else if (value instanceof ConfigurationSection section) section.getValues(false).values().forEach(child -> strings(child, result));
        else if (value instanceof Map<?, ?> map) map.values().forEach(child -> strings(child, result));
        else if (value instanceof List<?> list) list.forEach(child -> strings(child, result));
    }

    private List<String> calls(String script, Pattern pattern) {
        List<String> result = new ArrayList<>();
        var matcher = pattern.matcher(script);
        while (matcher.find()) {
            int depth = 1;
            char quote = 0;
            boolean escaped = false;
            int end = matcher.end();
            for (; end < script.length() && depth > 0; end++) {
                char c = script.charAt(end);
                if (escaped) escaped = false;
                else if (quote != 0 && c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                else if (quote == 0 && (c == '\'' || c == '"')) quote = c;
                else if (quote == 0 && c == '(') depth++;
                else if (quote == 0 && c == ')') depth--;
            }
            assertEquals(0, depth, "Unbalanced Packet.send expression");
            StringBuilder normalized = new StringBuilder();
            quote = 0;
            escaped = false;
            for (int i = matcher.start(); i < end; i++) {
                char c = script.charAt(i);
                if (escaped) escaped = false;
                else if (quote != 0 && c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                else if (quote == 0 && (c == '\'' || c == '"')) quote = c;
                if (quote != 0 || !Character.isWhitespace(c) || c == '\'' || c == '"') normalized.append(c);
            }
            result.add(normalized.toString());
        }
        return result;
    }

    private Set<String> keys(ConfigurationSection section) {
        return section == null ? Set.of() : section.getKeys(false);
    }

    private Set<String> csv(String source) {
        return source.isEmpty() ? Set.of() : new HashSet<>(List.of(source.split(",")));
    }

    private String hash(Iterable<String> strings) throws Exception {
        List<String> list = new ArrayList<>();
        strings.forEach(list::add);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", list).getBytes(StandardCharsets.UTF_8)));
    }
}
