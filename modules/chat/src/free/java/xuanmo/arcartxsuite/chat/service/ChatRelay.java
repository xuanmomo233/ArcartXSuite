package xuanmo.arcartxsuite.chat.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.chat.storage.ChatRelayJournal;

/** Inert type retained for the shared Chat service signatures. */
final class ChatRelay implements AutoCloseable {
    ChatRelay(ChatRelayJournal journal, String node, Consumer<Runnable> onMain,
              Delivery delivery, Runnable dispatchAdmitted, Logger logger, BooleanSupplier authorized) {
        throw new UnsupportedOperationException("Cross-server Chat relay is not included");
    }

    void start(Set<UUID> audience) {}
    void audienceChanged(Set<UUID> audience) {}
    boolean isRecovery(long timestamp) { return false; }
    Set<UUID> previousAudience() { return Set.of(); }
    boolean waitingForRejoin(Set<UUID> online) { return false; }
    static boolean fits(String payload) { return false; }
    CompletionStage<Boolean> publish(UUID id, String payload) { return CompletableFuture.completedFuture(false); }
    @Override public void close() {}
    CompletionStage<Void> stopped() { return CompletableFuture.completedFuture(null); }

    @FunctionalInterface
    interface Delivery {
        CompletionStage<Boolean> admit(String payload, String source, long timestamp);
    }
}
