package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void keepsItsFields() {
        var message = new Message(MessageType.REPLY, "I-2", "I-1", 18, "I-1#17");

        assertEquals(MessageType.REPLY, message.type());
        assertEquals("I-2", message.from());
        assertEquals("I-1", message.to());
        assertEquals(18, message.timestamp());
        assertEquals("I-1#17", message.requestId());
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new Message(null, "A", "B", 1, "A#1"));
        assertThrows(NullPointerException.class, () -> new Message(MessageType.REQUEST, null, "B", 1, "A#1"));
        assertThrows(NullPointerException.class, () -> new Message(MessageType.REQUEST, "A", null, 1, "A#1"));
        assertThrows(NullPointerException.class, () -> new Message(MessageType.REQUEST, "A", "B", 1, null));
    }

    @Test
    void rejectsNegativeTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new Message(MessageType.REQUEST, "A", "B", -1, "A#1"));
    }
}
