package xuanmo.arcartxsuite.tab.transport;

public interface TabTransport {

    boolean start();

    boolean isActive();

    void send(TabServerSnapshot snapshot);

    void shutdown();

    String name();
}
