package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/** Revocation includes reverse hard dependencies, and stops users before their providers. */
final class ModuleRevocations {
    static List<String> order(List<ModuleDescriptor> descriptors, Set<String> denied) {
        var modules = new LinkedHashMap<String, ModuleDescriptor>();
        descriptors.forEach(module -> modules.put(module.id(), module));
        var pending = new LinkedHashSet<String>(denied);
        pending.retainAll(modules.keySet());
        boolean expanded;
        do {
            expanded = false;
            for (var module : modules.values()) {
                if (module.depends().stream().anyMatch(pending::contains)) expanded |= pending.add(module.id());
            }
        } while (expanded);
        List<String> result = new ArrayList<>();
        while (!pending.isEmpty()) {
            String next = pending.stream().filter(id -> pending.stream()
                .noneMatch(other -> !other.equals(id) && modules.get(other).depends().contains(id))).findFirst()
                .orElseThrow(() -> new IllegalStateException("Loaded modules contain a hard-dependency cycle"));
            pending.remove(next);
            result.add(next);
        }
        return result;
    }
    private ModuleRevocations() {}
}
