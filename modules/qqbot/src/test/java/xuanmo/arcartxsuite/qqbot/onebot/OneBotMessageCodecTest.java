package xuanmo.arcartxsuite.qqbot.onebot;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class OneBotMessageCodecTest {
    @Test void normalizesBothMessageFormatsAndDecodesTextOnce() {
        var string = JsonParser.parseString("\"[CQ:at,qq=123] A&amp;B &#91;x&#93;\"");
        var array = JsonParser.parseString("[{\"type\":\"at\",\"data\":{\"qq\":\"123\"}},{\"type\":\"text\",\"data\":{\"text\":\" A&B [x]\"}}]");
        assertEquals("@123 A&B [x]", OneBotMessageCodec.text(string));
        assertEquals(OneBotMessageCodec.text(string), OneBotMessageCodec.text(array));
        assertEquals(List.of(123L), OneBotMessageCodec.mentions(string));
        assertEquals(List.of(123L), OneBotMessageCodec.mentions(array));
    }
    @Test void escapedOrArrayTextNeverCreatesAMention() {
        var escaped = JsonParser.parseString("\"&#91;CQ:at,qq=123&#93;\"");
        var text = JsonParser.parseString("[{\"type\":\"text\",\"data\":{\"text\":\"[CQ:at,qq=123]\"}}]");
        assertEquals("[CQ:at,qq=123]", OneBotMessageCodec.text(escaped));
        assertTrue(OneBotMessageCodec.mentions(escaped).isEmpty());
        assertTrue(OneBotMessageCodec.mentions(text).isEmpty());
    }
    @Test void prefersDecodedSegmentsOverRawRepresentation() {
        var event = new OneBotEvent(JsonParser.parseString("{\"raw_message\":\"wrong\",\"message\":[{\"type\":\"text\",\"data\":{\"text\":\"A&B\"}}]}").getAsJsonObject());
        assertEquals("A&B", event.rawMessage());
    }
}
