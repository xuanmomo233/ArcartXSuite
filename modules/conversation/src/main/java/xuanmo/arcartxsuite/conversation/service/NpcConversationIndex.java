package xuanmo.arcartxsuite.conversation.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The worlds in which each native Chemdah NPC binding applies. */
final class NpcConversationIndex {
    record Binding(String npcId, List<String> worlds) {}

    private final Map<String, Set<String>> byNpc;

    NpcConversationIndex(List<Binding> bindings) {
        var rules = new TreeMap<String, Set<String>>(String.CASE_INSENSITIVE_ORDER);
        for (var binding : bindings) {
            var previous = rules.get(binding.npcId());
            if (binding.worlds().isEmpty()) {
                rules.put(binding.npcId(), Set.of());
            } else if (previous == null) {
                rules.put(binding.npcId(), new HashSet<>(binding.worlds()));
            } else if (!previous.isEmpty()) {
                previous.addAll(binding.worlds());
            }
        }
        rules.replaceAll((id, worlds) -> Set.copyOf(worlds));
        byNpc = Collections.unmodifiableMap(rules);
    }

    boolean contains(String npcId, String world) {
        var worlds = byNpc.get(npcId);
        return worlds != null && (worlds.isEmpty() || worlds.contains(world));
    }

    boolean isEmpty() {
        return byNpc.isEmpty();
    }
}
