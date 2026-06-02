package xuanmo.arcartxsuite.chat.transport;

import xuanmo.arcartxsuite.chat.model.ChatEnvelope;

public interface ChatTransport {

    boolean start();

    boolean isActive();

    void send(ChatEnvelope envelope);

    void shutdown();

    String name();
}
