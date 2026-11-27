package xuanmo.arcartxsuite.crossserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import redis.clients.jedis.JedisPool;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;
import xuanmo.arcartxsuite.module.AxsLog;

/** SQLite admission is independent of the Redis connection and its backpressure. */
final class CrossServerRedisOutbox {
    private static final int BATCH_SIZE = 32;
    private static final int MAX_REQUESTS = 64;
    private static final int MAX_BUFFERED_CHARS = 8 * 1024 * 1024;
    private static final int MAX_DRAIN_BATCH_CHARS = 1024 * 1024;
    final CrossServerGlobalConfiguration configuration;
    private final JedisPool pool;
    private final CrossServerRedisStream stream;
    private final Supplier<Set<String>> modules;
    private final BooleanSupplier transportAllowed;
    private final Path database;
    private final int frameBytes;
    private final byte[] secret;
    private final String scope;
    private final ArrayBlockingQueue<Submission> submissions = new ArrayBlockingQueue<>(MAX_REQUESTS);
    private final Semaphore bufferedChars = new Semaphore(MAX_BUFFERED_CHARS);
    private final Semaphore requestSlots = new Semaphore(MAX_REQUESTS);
    private final AtomicLong nextAdmissionWarning = new AtomicLong();
    private final AtomicLong nextDrainWarning = new AtomicLong();
    private final Thread admission;
    private final Thread drain;
    private final CompletableFuture<Void> admissionStopped = new CompletableFuture<>();
    private final CompletableFuture<Void> drainStopped = new CompletableFuture<>();
    private volatile int drainReservedChars;
    private volatile boolean closed;

    CrossServerRedisOutbox(JedisPool pool, CrossServerGlobalConfiguration configuration, CrossServerRedisStream stream,
                         Path database, int frameBytes, Supplier<Set<String>> modules, BooleanSupplier transportAllowed) {
        this.pool = pool;
        this.configuration = configuration;
        this.stream = stream;
        this.database = database;
        this.frameBytes = frameBytes;
        this.secret = configuration.signatureSecret().getBytes(StandardCharsets.UTF_8);
        this.modules = modules;
        this.transportAllowed = transportAllowed;
        this.scope = configuration.redisHost() + ":" + configuration.redisPort() + "/" + configuration.redisDatabase()
            + "\n" + configuration.redisChannel() + "\n" + configuration.nodeId();
        admission = new Thread(this::admit, "AXS-CrossServer-Outbox-Write");
        drain = new Thread(this::drain, "AXS-CrossServer-Outbox-Redis");
        admission.setDaemon(true);
        drain.setDaemon(true);
        admission.start();
        drain.start();
    }

    int serializationReservation(CrossServerEnvelope envelope) {
        int payloadChars = envelope.payload().length();
        long wireLimit = Math.min(configuration.maxPayloadChars(), 6L * payloadChars + 4_096);
        long reservation = payloadChars + wireLimit;
        return reservation <= MAX_BUFFERED_CHARS ? (int) reservation : 0;
    }

    CompletionStage<Boolean> publish(CrossServerEnvelope envelope, int reserved) {
        return publish(null, envelope, envelope.timestamp(), envelope.module(), reserved);
    }

    CompletionStage<Boolean> publish(String wire, long timestamp, String module) {
        return publish(wire, null, timestamp, module, wire.length());
    }

    private synchronized CompletionStage<Boolean> publish(String wire, CrossServerEnvelope envelope,
                                                           long timestamp, String module, int reserved) {
        if (closed || !transportAllowed.getAsBoolean()) return CompletableFuture.completedFuture(false);
        if (!requestSlots.tryAcquire()) {
            warn(nextAdmissionWarning, "本地发件箱暂存队列已满，消息尚未受理");
            return CompletableFuture.completedFuture(false);
        }
        if (!bufferedChars.tryAcquire(reserved)) {
            requestSlots.release();
            warn(nextAdmissionWarning, "本地发件箱暂存达到内存上限，消息尚未受理"
                + " (请求=" + reserved + ", 剩余=" + bufferedChars.availablePermits()
                + ", Redis 排空占用=" + drainReservedChars + "，单位为字符)");
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        long expiresAt = CrossServerReplayStore.expiry(timestamp, configuration.dedupeTtlMs());
        if (!submissions.offer(new Submission(wire, envelope, expiresAt, module, reserved, result))) {
            bufferedChars.release(reserved);
            requestSlots.release();
            warn(nextAdmissionWarning, "本地发件箱暂存队列已满，消息尚未受理");
            result.complete(false);
        }
        return result;
    }

    private void admit() {
        try (var store = new CrossServerOutboxStore(database)) {
            while (!closed) {
                var batch = new ArrayList<Submission>(BATCH_SIZE);
                batch.add(submissions.take());
                submissions.drainTo(batch, BATCH_SIZE - 1);
                writeBatch(store, batch);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (SQLException failure) {
            warn(nextAdmissionWarning, "本地发件箱写连接关闭失败");
        } finally {
            Submission pending;
            while ((pending = submissions.poll()) != null) complete(pending, false);
            admissionStopped.complete(null);
        }
    }

    private void writeBatch(CrossServerOutboxStore store, List<Submission> batch) {
        boolean[] committed = new boolean[batch.size()];
        try {
            var writes = new ArrayList<CrossServerOutboxStore.Write>(batch.size());
            var positions = new ArrayList<Integer>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                Submission entry = batch.get(index);
                if (entry.expiresAt() < System.currentTimeMillis()) continue;
                String wire = entry.wire();
                if (wire == null) {
                    CrossServerEnvelope envelope = entry.envelope();
                    if (configuration.shouldSign()) envelope = CrossServerSigner.withSignature(envelope, secret);
                    wire = CrossServerEnvelopeCodec.encodeWithin(envelope, configuration.maxPayloadChars());
                }
                if (wire == null) continue;
                writes.add(new CrossServerOutboxStore.Write(entry.module(), entry.expiresAt(),
                    CrossServerEnvelopeCodec.utf8Length(wire), wire));
                positions.add(index);
            }
            if (!writes.isEmpty()) {
                boolean[] admitted = store.admit(scope, writes, System.currentTimeMillis(),
                    configuration.redisOutboxMaxBytes(), frameBytes);
                for (int index = 0; index < admitted.length; index++) committed[positions.get(index)] = admitted[index];
                LockSupport.unpark(drain);
            }
        } catch (IOException | SQLException | RuntimeException failure) {
            reset(store, failure);
            warn(nextAdmissionWarning, "本地发件箱写入失败，未确认受理: " + failure.getClass().getSimpleName());
        } finally {
            for (int index = 0; index < committed.length; index++) {
                if (!committed[index]) warn(nextAdmissionWarning, "发件箱容量不足、签名帧过期或信封超限，消息未受理");
                complete(batch.get(index), committed[index]);
            }
        }
    }

    private void complete(Submission submission, boolean committed) {
        bufferedChars.release(submission.reserved());
        requestSlots.release();
        submission.result().complete(committed);
    }

    private void drain() {
        try (var store = new CrossServerOutboxStore(database)) {
            long nextCleanup = 0;
            while (!closed) {
                try {
                    long now = System.currentTimeMillis();
                    if (now >= nextCleanup) {
                        if (store.purgeExpired(now) > 0) warn(nextDrainWarning, "发件箱中有未确认消息超过签名有效期");
                        nextCleanup = now + 1_000;
                    }
                    var heads = store.peek(scope, modules.get(), now, BATCH_SIZE);
                    if (heads.isEmpty()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(150));
                        continue;
                    }
                    if (!drainBatch(store, heads)) Thread.sleep(100);
                } catch (IOException | SQLException failure) {
                    reset(store, failure);
                    warn(nextDrainWarning, "发件箱读取或确认失败，保留记录重试");
                    Thread.sleep(250);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (SQLException failure) {
            warn(nextDrainWarning, "本地发件箱读连接关闭失败");
        } finally {
            drainStopped.complete(null);
        }
    }

    private boolean drainBatch(CrossServerOutboxStore store, List<CrossServerOutboxStore.Head> heads)
            throws IOException, SQLException, InterruptedException {
        int reserved = heads.get(0).wireChars();
        bufferedChars.acquire(reserved);
        drainReservedChars = reserved;
        try {
            int count = 1;
            // Leave room for new writes while Redis holds a batch. A large persisted frame can still drain alone.
            while (count < heads.size()) {
                int next = heads.get(count).wireChars();
                if (next > MAX_DRAIN_BATCH_CHARS - reserved || !bufferedChars.tryAcquire(next)) break;
                reserved += next;
                drainReservedChars = reserved;
                count++;
            }
            var frames = store.frames(heads.subList(0, count));
            if (frames.isEmpty()) return true;
            int confirmed = appendAndConfirm(frames);
            if (confirmed == 0) return false;
            store.remove(frames, confirmed);
            return true;
        } finally {
            drainReservedChars = 0;
            bufferedChars.release(reserved);
        }
    }

    private int appendAndConfirm(List<CrossServerOutboxStore.Frame> frames) {
        long firstExpiry = frames.get(0).head().expiresAt();
        if (closed || !transportAllowed.getAsBoolean() || System.currentTimeMillis() > firstExpiry
                || !modules.get().contains(frames.get(0).head().module())) return 0;
        try (var jedis = pool.getResource()) {
            long now = System.currentTimeMillis();
            Set<String> activeModules = modules.get();
            var wires = new ArrayList<String>(frames.size());
            for (var frame : frames) {
                if (closed || now > frame.head().expiresAt() || !activeModules.contains(frame.head().module())) break;
                wires.add(frame.wire());
            }
            if (wires.isEmpty() || !transportAllowed.getAsBoolean()) return 0;
            int accepted = stream.append(jedis, wires);
            if (accepted == 0) {
                warn(nextDrainWarning, "Redis Stream 等待慢节点，保留待写消息");
                return 0;
            }
            int required = configuration.redisOutboxReplicaAcks();
            if (required == 0) return accepted;
            while (!closed && System.currentTimeMillis() <= firstExpiry) {
                if (jedis.waitReplicas(required, 500) >= required) return accepted;
                warn(nextDrainWarning, "Redis 写入尚未获得副本确认，保留发件箱");
            }
        } catch (RuntimeException failure) {
            if (!closed) warn(nextDrainWarning, "Redis 发件箱投递暂未确认，保留同一签名帧重试: "
                + failure.getClass().getSimpleName());
        }
        return 0;
    }

    synchronized void shutdown() {
        closed = true;
        admission.interrupt();
        drain.interrupt();
    }

    java.util.concurrent.CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(admissionStopped, drainStopped);
    }

    private static void reset(CrossServerOutboxStore store, Exception failure) {
        try { store.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
    }

    private static void warn(AtomicLong next, String message) {
        long now = System.currentTimeMillis();
        long before = next.get();
        if (now >= before && next.compareAndSet(before, now + 10_000)) {
            AxsLog.logger().warning("[CrossServer] " + message);
        }
    }

    private record Submission(String wire, CrossServerEnvelope envelope, long expiresAt, String module, int reserved,
                              CompletableFuture<Boolean> result) {}
}
