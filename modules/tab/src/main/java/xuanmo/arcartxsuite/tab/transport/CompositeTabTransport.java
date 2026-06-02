package xuanmo.arcartxsuite.tab.transport;

import java.util.ArrayList;
import java.util.List;

public final class CompositeTabTransport implements TabTransport {

    private final List<TabTransport> transports;

    public CompositeTabTransport(List<TabTransport> transports) {
        this.transports = List.copyOf(transports == null ? List.of() : transports);
    }

    @Override
    public boolean start() {
        boolean active = false;
        for (TabTransport transport : transports) {
            if (transport.start()) {
                active = true;
            }
        }
        return active;
    }

    @Override
    public boolean isActive() {
        for (TabTransport transport : transports) {
            if (transport.isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void send(TabServerSnapshot snapshot) {
        for (TabTransport transport : transports) {
            if (transport.isActive()) {
                transport.send(snapshot);
            }
        }
    }

    @Override
    public void shutdown() {
        for (TabTransport transport : transports) {
            transport.shutdown();
        }
    }

    @Override
    public String name() {
        List<String> names = new ArrayList<>();
        for (TabTransport transport : transports) {
            if (transport.isActive()) {
                names.add(transport.name());
            }
        }
        return names.isEmpty() ? "none" : String.join("+", names);
    }
}
