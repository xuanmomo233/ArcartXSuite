package xuanmo.arcartxsuite.title.service;

import java.time.Instant;
import java.util.Set;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/** One effective view of a saved state, valid until its next activation or expiry. */
final class TitleStateView {
    final PlayerTitleState source;
    final PlayerTitleState stored;
    final PlayerTitleState effective;
    private final Instant checkedAt;
    private final Instant nextChange;
    private volatile Resolution resolution;
    Set<String> appliedGroups;

    TitleStateView(PlayerTitleState source, Instant now) {
        this.source = source;
        this.stored = source.sanitizeStored(now);
        this.effective = stored.sanitize(now);
        this.checkedAt = now;
        Instant next = null;
        for (var owned : stored.ownedTitles().values()) {
            if (!owned.isActive(now)) next = earlier(next, owned.activatesAt());
            if (owned.expiresAt() != null) next = earlier(next, owned.expiresAt());
        }
        this.nextChange = next;
    }

    boolean matches(PlayerTitleState state, Instant now) {
        return source == state && !now.isBefore(checkedAt) && (nextChange == null || now.isBefore(nextChange));
    }

    ResolvedTitleState resolve(TitleModuleConfiguration configuration, Set<String> groups) {
        Resolution cached = resolution;
        if (cached != null && cached.groups().equals(groups)) return cached.state();
        var state = TitleStateResolver.resolve(effective, configuration, checkedAt, groups);
        resolution = new Resolution(groups, state);
        return state;
    }

    private static Instant earlier(Instant first, Instant second) {
        return first == null || second.isBefore(first) ? second : first;
    }

    private record Resolution(Set<String> groups, ResolvedTitleState state) {}
}
