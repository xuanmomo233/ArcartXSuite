package xuanmo.arcartxsuite.crossserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisNoScriptException;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

/** One bounded, non-destructive replay log shared by all receivers on a Redis channel. */
final class CrossServerRedisStream {
    static final String START = "0-0";
    private static final String APPEND_SCRIPT = loadScript("/redis/cross-server-append.lua");
    private static final String CHECKPOINT_SCRIPT = loadScript("/redis/cross-server-checkpoint.lua");
    private static final long MAX_RETENTION_MS = 365L * 24 * 60 * 60 * 1000;

    private final List<String> keys;
    private final String retention;
    private final String maxBytes;
    private final String maxFrameBytes;
    private final String readerLease;
    private final String nodeId;
    private String appendSha;
    private String checkpointSha;

    CrossServerRedisStream(CrossServerGlobalConfiguration config, int frameBytes) {
        String stream = config.redisChannel() + ":stream:v1";
        keys = List.of(stream, stream + ":index", stream + ":meta", stream + ":readers", stream + ":leases");
        retention = Long.toString(Math.min(config.dedupeTtlMs(), MAX_RETENTION_MS));
        maxBytes = Long.toString(config.redisStreamMaxBytes());
        maxFrameBytes = Integer.toString(frameBytes);
        readerLease = Long.toString(config.redisStreamReaderLeaseMs());
        nodeId = config.nodeId();
    }

    // Only called by the publisher's single worker.
    int append(Jedis jedis, List<String> wires) {
        var args = new ArrayList<String>(wires.size() + 3);
        args.add(wires.get(0));
        args.add(retention);
        args.add(maxBytes);
        args.add(maxFrameBytes);
        args.addAll(wires.subList(1, wires.size()));
        Object reply = appendReply(jedis, args);
        if (reply instanceof Long count) {
            if (count < 0 || count > wires.size()) throw new IllegalStateException("Invalid Redis Stream batch reply");
            return count.intValue();
        }
        if (wires.size() == 1 && reply instanceof String id) {
            if ("FULL".equals(id)) return 0;
            if (id.indexOf('-') > 0) return 1;
        }
        throw new IllegalStateException("Invalid Redis Stream append reply");
    }

    private Object appendReply(Jedis jedis, List<String> args) {
        if (appendSha != null) {
            try {
                return jedis.evalsha(appendSha, keys, args);
            } catch (JedisNoScriptException ignored) {
                // Redis may have restarted or changed primary.
            }
        }
        appendSha = jedis.scriptLoad(APPEND_SCRIPT);
        return jedis.evalsha(appendSha, keys, args);
    }

    // Published only after the SQLite inbox and its offset are committed together.
    void checkpoint(Jedis jedis, String cursor) {
        List<String> args = List.of(nodeId, cursor, readerLease);
        if (checkpointSha != null) {
            try {
                jedis.evalsha(checkpointSha, keys, args);
                return;
            } catch (JedisNoScriptException ignored) {
                // The connection may now lead to a promoted replica.
            }
        }
        checkpointSha = jedis.scriptLoad(CHECKPOINT_SCRIPT);
        jedis.evalsha(checkpointSha, keys, args);
    }

    List<StreamEntry> read(Jedis jedis, String after, int count, boolean wait) {
        var params = XReadParams.xReadParams().count(count);
        if (wait) params.block(250);
        var response = jedis.xread(params,
            Map.of(keys.get(0), new StreamEntryID(after)));
        return response == null || response.isEmpty() ? List.of() : response.get(0).getValue();
    }

    private static String loadScript(String path) {
        try (var resource = CrossServerRedisStream.class.getResourceAsStream(path)) {
            if (resource == null) throw new IOException("Missing Redis script " + path);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
