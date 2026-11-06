package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.bukkit.entity.Player;

/** Server-thread presentation extension. Abyss owns rolls, eligibility, and claims. */
@PublicCapability
public interface AbyssRewardGameAccess {
    record Card(int slot, String label, boolean selected, boolean accepted) { }
    record Offer(UUID id, String strategy, String title, int remaining, int revision, long expiresAt, List<Card> cards) {
        public Offer { cards = List.copyOf(cards); }
    }
    @FunctionalInterface interface Strategy {
        /** The callback is bound to this player and displayed selection state; call on the server thread. */
        void present(Player player, Offer offer, IntConsumer pick);
    }

    /** Hidden cards carry a null label when revealUnpicked is false. Built-in IDs are reserved. */
    AbyssAdmissionAccess.Registration register(String id, boolean revealUnpicked, Strategy strategy);
    void openPending(Player player);
}
