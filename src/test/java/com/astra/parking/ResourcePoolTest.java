package com.astra.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class ResourcePoolTest {

    @Test
    void twentyThreadsNeverExceedCapacity() throws Exception {
        int capacity = 5;
        var pool = new ResourcePool<String>(capacity);
        var inside = new AtomicInteger();
        var peak = new AtomicInteger();
        var failures = new CopyOnWriteArrayList<Throwable>();
        var start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 20; t++) {
            String item = "CAR-" + t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2_000; i++) {
                        OptionalInt slot = pool.tryAcquire(item);
                        if (slot.isPresent()) {
                            peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            assertEquals(Optional.of(item), pool.slots().get(slot.getAsInt()));
                            inside.decrementAndGet();
                            pool.release(item);
                        }
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(failures.isEmpty(), failures.toString());
        assertTrue(peak.get() <= capacity, "peak " + peak.get());
        assertTrue(peak.get() > 1, "the test never had concurrent holders");
        assertEquals(capacity, pool.available());
        assertTrue(pool.slots().stream().allMatch(Optional::isEmpty));
    }

    @Test
    void fullPoolRejectsWithoutBlocking() {
        var pool = new ResourcePool<String>(2);

        assertEquals(OptionalInt.of(0), pool.tryAcquire("A"));
        assertEquals(OptionalInt.of(1), pool.tryAcquire("B"));
        assertTrue(pool.tryAcquire("C").isEmpty());
        assertEquals(0, pool.available());
    }

    @Test
    void releaseIsIdempotent() {
        var pool = new ResourcePool<String>(2);
        pool.tryAcquire("A");

        pool.release("A");
        pool.release("A");
        pool.release("NOBODY");

        assertEquals(2, pool.available());
    }

    @Test
    void sameItemKeepsItsSlot() {
        var pool = new ResourcePool<String>(3);
        pool.tryAcquire("A");
        OptionalInt b = pool.tryAcquire("B");

        assertEquals(b, pool.tryAcquire("B"));
        assertEquals(1, pool.available());
    }

    @Test
    void slotsShowWhoHoldsEachSpotAndReuseFreedOnes() {
        var pool = new ResourcePool<String>(3);
        pool.tryAcquire("A");
        pool.tryAcquire("B");
        pool.tryAcquire("C");
        pool.release("B");

        assertEquals(List.of(Optional.of("A"), Optional.empty(), Optional.of("C")), pool.slots());
        assertEquals(OptionalInt.of(1), pool.tryAcquire("D"));
        assertEquals(Optional.of("D"), pool.slots().get(1));
        assertFalse(pool.slots().contains(Optional.of("B")));
    }

    @Test
    void rejectsInvalidCapacityAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> new ResourcePool<String>(0));
        var pool = new ResourcePool<String>(1);
        assertThrows(NullPointerException.class, () -> pool.tryAcquire(null));
        assertThrows(NullPointerException.class, () -> pool.release(null));
    }
}
