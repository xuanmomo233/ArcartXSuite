package xuanmo.arcartxsuite.auth.runtime;

/** Lifecycle of the optional signed proxy ingress. */
interface ProxyAdmissionChannel extends AutoCloseable {
    void start();
    void tick();
    @Override void close();
}
