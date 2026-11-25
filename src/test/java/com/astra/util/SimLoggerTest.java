package com.astra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.core.SimConfig;
import com.astra.util.SimLogger.Category;
import com.astra.util.SimLogger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class SimLoggerTest {

    private static final String TIME = "\\d{2}:\\d{2}:\\d{2}\\.\\d{3}";

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        SimLogger.shutdown();
    }

    @Test
    void eachCategoryHasExactFormat() throws Exception {
        Path file = init("DEBUG");

        SimLogger.log(Category.ENGINE, Level.DEBUG, "tick 3");
        SimLogger.info(Category.LIGHT, "I-1 GREEN(NS) -> YELLOW(NS)");
        SimLogger.warn(Category.CMD, "#4 PAUSE");
        SimLogger.error(Category.NET, "link down");
        SimLogger.info(Category.STATS, "throughput 12");
        SimLogger.lamport(17, "CAR-42", "SEND_REQUEST");
        SimLogger.ricart("CAR-42 requested access at 17");
        SimLogger.shutdown();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(7, lines.size(), lines.toString());
        assertMatches(TIME + " \\[ENGINE\\] tick 3", lines.get(0));
        assertMatches(TIME + " \\[LIGHT\\] I-1 GREEN\\(NS\\) -> YELLOW\\(NS\\)", lines.get(1));
        assertMatches(TIME + " \\[CMD\\] #4 PAUSE", lines.get(2));
        assertMatches(TIME + " \\[NET\\] link down", lines.get(3));
        assertMatches(TIME + " \\[STATS\\] throughput 12", lines.get(4));
        assertEquals("[LAMPORT 17] CAR-42 -> SEND_REQUEST", lines.get(5));
        assertEquals("[RICART] CAR-42 requested access at 17", lines.get(6));
    }

    @Test
    void linesBelowLevelAreDroppedAndNotCounted() throws Exception {
        Path file = init("WARN");

        SimLogger.info(Category.ENGINE, "hidden");
        SimLogger.warn(Category.ENGINE, "shown");
        SimLogger.lamport(1, "I-1", "TICK");
        assertEquals(1, SimLogger.lineCount(Category.ENGINE));
        assertEquals(1, SimLogger.lineCount(Category.LAMPORT));
        SimLogger.shutdown();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), lines.toString());
        assertMatches(TIME + " \\[ENGINE\\] shown", lines.get(0));
    }

    @Test
    @Timeout(30)
    void concurrentWritersProduceWholeLines() throws Exception {
        Path file = init("INFO");
        int threads = 8;
        int perThread = 5_000;
        var start = new CountDownLatch(1);
        List<Thread> writers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread writer = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    SimLogger.info(Category.ENGINE, "T" + id + "-" + i);
                }
            });
            writers.add(writer);
            writer.start();
        }
        start.countDown();
        for (Thread writer : writers) {
            writer.join();
        }
        assertEquals(threads * perThread, SimLogger.lineCount(Category.ENGINE));

        SimLogger.shutdown();
        assertEquals(0, SimLogger.pendingCount());

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(threads * perThread, lines.size());
        Pattern whole = Pattern.compile(TIME + " \\[ENGINE\\] T[0-7]-\\d{1,4}");
        var distinct = new HashSet<String>();
        for (String line : lines) {
            assertMatches(whole, line);
            distinct.add(line.substring(line.indexOf(']') + 2));
        }
        assertEquals(threads * perThread, distinct.size());
    }

    @Test
    void shutdownLeavesNothingQueued() {
        init("INFO");
        for (int i = 0; i < 1_000; i++) {
            SimLogger.info(Category.STATS, "sample " + i);
        }

        SimLogger.shutdown();

        assertEquals(0, SimLogger.pendingCount());
    }

    @Test
    void createsMissingLogFolder() throws Exception {
        Path file = dir.resolve("nested/deeper/astra.log");
        var props = new Properties();
        props.setProperty("log.file", file.toString());
        props.setProperty("log.console", "false");
        SimLogger.init(SimConfig.from(props));

        SimLogger.info(Category.ENGINE, "hola");
        SimLogger.shutdown();

        assertTrue(Files.exists(file));
        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
    }

    private Path init(String level) {
        Path file = dir.resolve("logs/astra.log");
        var props = new Properties();
        props.setProperty("log.file", file.toString());
        props.setProperty("log.console", "false");
        props.setProperty("log.level", level);
        SimLogger.init(SimConfig.from(props));
        return file;
    }

    private static void assertMatches(String regex, String line) {
        assertMatches(Pattern.compile(regex), line);
    }

    private static void assertMatches(Pattern pattern, String line) {
        assertTrue(pattern.matcher(line).matches(), "unexpected line: '" + line + "'");
    }
}
