package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.util.LogSettings;
import com.astra.util.SimLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class LamportClockTest {

    @TempDir
    Path dir;

    private Path logFile;

    @BeforeEach
    void setUp() {
        logFile = dir.resolve("astra.log");
        SimLogger.init(new Settings(logFile.toString()));
    }

    @AfterEach
    void tearDown() {
        SimLogger.shutdown();
    }

    @Test
    void startsAtZeroAndTickIncrements() {
        var clock = new LamportClock("I-1");

        assertEquals(0, clock.current());
        assertEquals(1, clock.tick("LOCAL"));
        assertEquals(2, clock.send("SEND"));
        assertEquals(2, clock.current());
    }

    @Test
    @Timeout(30)
    void concurrentTicksAreDistinctAndConsecutive() throws Exception {
        var clock = new LamportClock("I-1");
        int threads = 16;
        int perThread = 1_000;
        int[][] seen = new int[threads][perThread];
        var start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    seen[id][i] = clock.tick("T" + id);
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        int[] all = Arrays.stream(seen).flatMapToInt(Arrays::stream).sorted().toArray();
        for (int i = 0; i < all.length; i++) {
            assertEquals(i + 1, all[i], "missing or repeated timestamp");
        }
        assertEquals(threads * perThread, clock.current());
        for (int[] values : seen) {
            for (int i = 1; i < values.length; i++) {
                assertTrue(values[i] > values[i - 1], "a thread saw its clock go back");
            }
        }
    }

    @Test
    void receiveWithGreaterRemoteJumpsPastIt() {
        var clock = new LamportClock("I-1");
        clock.tick("LOCAL");

        assertEquals(11, clock.receive(10, "RECV"));
    }

    @Test
    void receiveWithSmallerRemoteOnlyIncrements() {
        var clock = new LamportClock("I-1");
        for (int i = 0; i < 5; i++) {
            clock.tick("LOCAL");
        }

        assertEquals(6, clock.receive(2, "RECV"));
    }

    @Test
    void receiveWithEqualRemoteIncrements() {
        var clock = new LamportClock("I-1");
        for (int i = 0; i < 5; i++) {
            clock.tick("LOCAL");
        }

        assertEquals(6, clock.receive(5, "RECV"));
    }

    @Test
    void receiveRejectsNegativeTimestamp() {
        var clock = new LamportClock("I-1");

        assertThrows(IllegalArgumentException.class, () -> clock.receive(-1, "RECV"));
    }

    @Test
    void sendHappensBeforeLaterEventsOfReceiver() {
        var a = new LamportClock("A");
        var b = new LamportClock("B");
        for (int i = 0; i < 3; i++) {
            a.tick("LOCAL");
        }

        int sent = a.send("REQUEST");
        int received = b.receive(sent, "REQUEST");
        int after = b.tick("LOCAL");

        assertTrue(sent < received);
        assertTrue(received < after);
        assertTrue(sent < after);
    }

    @Test
    void logsEachEventWithEvidenceFormat() throws Exception {
        var a = new LamportClock("CAR-42");
        a.tick("ARRIVE");
        a.send("REQUEST");
        a.receive(9, "REPLY");
        SimLogger.shutdown();

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals(List.of(
                "[LAMPORT 1] CAR-42 -> ARRIVE",
                "[LAMPORT 2] CAR-42 -> REQUEST",
                "[LAMPORT 10] CAR-42 -> REPLY"), lines);
    }

    private record Settings(String logFile) implements LogSettings {

        @Override
        public boolean logConsole() {
            return false;
        }

        @Override
        public SimLogger.Level logLevel() {
            return SimLogger.Level.INFO;
        }
    }
}
