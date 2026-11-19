package xuanmo.arcartxsuite.cloud;

/** Temporary caller-owned arrays; registry and module loaders clone the data they retain. */
final class CloudPayload implements AutoCloseable {
    byte[] key;
    byte[] signature;
    byte[] jar;
    byte[] seed;
    @Override public void close() { wipe(key); wipe(signature); wipe(jar); wipe(seed); }
    private static void wipe(byte[] data) { if (data != null) java.util.Arrays.fill(data, (byte) 0); }
}
