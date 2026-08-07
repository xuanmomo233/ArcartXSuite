package xuanmo.arcartxsuite.crossserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

/**
 * 跨服信封 JSON 编解码工具，基于 Gson 序列化 {@link CrossServerEnvelope}。
 * <p>
 * 解码时校验信封非空且 module 字段存在，否则抛出 IllegalArgumentException。
 */
public final class CrossServerEnvelopeCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CrossServerEnvelopeCodec() {
    }

    public static String encode(CrossServerEnvelope envelope) {
        return GSON.toJson(envelope);
    }

    public static CrossServerEnvelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty cross-server envelope");
        }
        CrossServerEnvelope envelope = GSON.fromJson(json, CrossServerEnvelope.class);
        if (envelope == null || envelope.module() == null || envelope.module().isBlank()) {
            throw new IllegalArgumentException("invalid cross-server envelope");
        }
        return envelope;
    }
}
