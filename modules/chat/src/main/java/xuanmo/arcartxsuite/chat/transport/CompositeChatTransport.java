package xuanmo.arcartxsuite.chat.transport;

import java.util.ArrayList;
import java.util.List;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;

public final class CompositeChatTransport implements ChatTransport {

    private final List<ChatTransport> transports;

    public CompositeChatTransport(List<ChatTransport> transports) {
        this.transports = List.copyOf(transports == null ? List.of() : transports);
    }

    @Override
    public boolean start() {
        boolean active = false;
        for (ChatTransport transport : transports) {
            if (transport.start()) {
                active = true;
            }
        }
        return active;
    }

    @Override
    public boolean isActive() {
        for (ChatTransport transport : transports) {
            if (transport.isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void send(ChatEnvelope envelope) {
        for (ChatTransport transport : transports) {
            if (transport.isActive()) {
                transport.send(envelope);
            }
        }
    }

    @Override
    public void shutdown() {
        for (ChatTransport transport : transports) {
            transport.shutdown();
        }
    }

    @Override
    public String name() {
        List<String> names = new ArrayList<>();
        for (ChatTransport transport : transports) {
            if (transport.isActive()) {
                names.add(transport.name());
            }
        }
        return names.isEmpty() ? "none" : String.join("+", names);
    }
}
