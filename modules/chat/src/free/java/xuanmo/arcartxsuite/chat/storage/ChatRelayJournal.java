package xuanmo.arcartxsuite.chat.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** Free Chat has no MySQL network journal implementation. */
public final class ChatRelayJournal {
    ChatRelayJournal(Connections connections) {
        throw new UnsupportedOperationException("Cross-server Chat journal is not included");
    }

    @FunctionalInterface
    interface Connections {
        Connection open() throws SQLException;
    }
}
