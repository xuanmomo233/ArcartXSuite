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

    static String encodeWithin(CrossServerEnvelope envelope, int maxChars) {
        var output = new BoundedJson(maxChars);
        try {
            GSON.toJson(envelope, CrossServerEnvelope.class, output);
            return output.text.toString();
        } catch (FrameTooLong ignored) {
            return null;
        }
    }

    private static final class BoundedJson implements Appendable {
        private final StringBuilder text = new StringBuilder();
        private final int limit;

        private BoundedJson(int limit) {
            this.limit = limit;
        }

        @Override
        public Appendable append(CharSequence value) {
            return append(value, 0, value == null ? 4 : value.length());
        }

        @Override
        public Appendable append(CharSequence value, int start, int end) {
            if (end - start > limit - text.length()) throw new FrameTooLong();
            text.append(value, start, end);
            return this;
        }

        @Override
        public Appendable append(char value) {
            if (text.length() >= limit) throw new FrameTooLong();
            text.append(value);
            return this;
        }
    }

    private static final class FrameTooLong extends RuntimeException {}

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

    static int utf8Length(String text) {
        int bytes = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value < 0x80) {
                bytes++;
            } else if (value < 0x800) {
                bytes += 2;
            } else if (!Character.isSurrogate(value)) {
                bytes += 3;
            } else if (Character.isHighSurrogate(value) && index + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes++; // StandardCharsets.UTF_8 replaces an isolated surrogate with '?'.
            }
        }
        return bytes;
    }
}
