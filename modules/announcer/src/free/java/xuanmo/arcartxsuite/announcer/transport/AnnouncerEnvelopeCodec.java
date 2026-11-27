package xuanmo.arcartxsuite.announcer.transport;

import org.bukkit.configuration.InvalidConfigurationException;

public final class AnnouncerEnvelopeCodec {
    private AnnouncerEnvelopeCodec() {}

    public static String encode(AnnouncerEnvelope envelope) {
        throw new UnsupportedOperationException("Cross-server announcement codec is not included");
    }

    public static AnnouncerEnvelope decode(String content) throws InvalidConfigurationException {
        throw new UnsupportedOperationException("Cross-server announcement codec is not included");
    }
}
