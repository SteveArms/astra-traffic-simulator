package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.PriorityQueue;
import org.junit.jupiter.api.Test;

class RequestTest {

    @Test
    void lowerTimestampGoesFirst() {
        assertTrue(new Request(3, "Z").compareTo(new Request(5, "A")) < 0);
        assertTrue(new Request(5, "A").compareTo(new Request(3, "Z")) > 0);
    }

    @Test
    void tieOnTimestampIsBrokenByProcessId() {
        var a = new Request(7, "CAR-1");
        var b = new Request(7, "CAR-2");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void priorityQueueServesByTimestampThenProcessId() {
        var queue = new PriorityQueue<Request>();
        queue.addAll(List.of(new Request(9, "A"), new Request(4, "C"), new Request(4, "B"), new Request(1, "Z")));

        assertEquals(new Request(1, "Z"), queue.poll());
        assertEquals(new Request(4, "B"), queue.poll());
        assertEquals(new Request(4, "C"), queue.poll());
        assertEquals(new Request(9, "A"), queue.poll());
    }

    @Test
    void compareToIsConsistentWithEquals() {
        List<Request> samples = List.of(
                new Request(4, "A"), new Request(4, "A"), new Request(4, "B"), new Request(5, "A"));
        for (Request x : samples) {
            for (Request y : samples) {
                assertEquals(x.equals(y), x.compareTo(y) == 0, x + " vs " + y);
                assertEquals(Integer.signum(x.compareTo(y)), -Integer.signum(y.compareTo(x)), x + " vs " + y);
            }
        }
        assertEquals(new Request(4, "A").hashCode(), new Request(4, "A").hashCode());
        assertNotEquals(new Request(4, "A"), new Request(4, "B"));
    }

    @Test
    void rejectsInvalidFields() {
        assertThrows(NullPointerException.class, () -> new Request(1, null));
        assertThrows(IllegalArgumentException.class, () -> new Request(-1, "A"));
    }
}
