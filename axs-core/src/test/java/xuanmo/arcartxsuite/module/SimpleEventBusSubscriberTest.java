package xuanmo.arcartxsuite.module;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleEventBusSubscriberTest {
    @Test void distinguishesPublishersFromActualConsumersAndHonorsUnsubscribe() {
        var bus=new SimpleEventBus(Logger.getAnonymousLogger());
        bus.registerPublisher("axs.auth.presence_day_started");
        assertFalse(bus.hasSubscribers("axs.auth.presence_day_started"));
        String id=bus.subscribe("axs.auth.*",ignored->{});
        assertTrue(bus.hasSubscribers("axs.auth.presence_day_started"));
        assertFalse(bus.hasSubscribers("axs.other.event"));
        bus.unsubscribe(id);
        assertFalse(bus.hasSubscribers("axs.auth.presence_day_started"));
    }
}
