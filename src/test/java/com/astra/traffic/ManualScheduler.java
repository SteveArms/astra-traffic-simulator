package com.astra.traffic;

import java.time.Duration;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Scheduler de tiempo manual para tests: las tareas solo corren al llamar a {@link #advance}. */
public final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {

    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private long nowNanos;
    private long sequence;
    private boolean shutdown;

    /** Corre en orden las tareas que vencen dentro de {@code step}; fuera del lock para no bloquear al semáforo. */
    public void advance(Duration step) {
        long target;
        synchronized (this) {
            target = nowNanos + step.toNanos();
        }
        while (true) {
            Task due;
            synchronized (this) {
                due = queue.peek();
                if (due == null || due.atNanos > target) {
                    nowNanos = target;
                    return;
                }
                queue.poll();
                nowNanos = due.atNanos;
            }
            due.run();
        }
    }

    @Override
    public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (shutdown) {
            throw new RejectedExecutionException("shut down");
        }
        var task = new Task(command, nowNanos + unit.toNanos(Math.max(0, delay)), sequence++);
        queue.add(task);
        return task;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        queue.clear();
        return List.of();
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    private final class Task implements ScheduledFuture<Object>, Comparable<Delayed> {

        private final Runnable command;
        private final long atNanos;
        private final long seq;
        private volatile boolean cancelled;
        private volatile boolean done;

        Task(Runnable command, long atNanos, long seq) {
            this.command = command;
            this.atNanos = atNanos;
            this.seq = seq;
        }

        void run() {
            if (!cancelled) {
                command.run();
            }
            done = true;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            synchronized (ManualScheduler.this) {
                return unit.convert(atNanos - nowNanos, TimeUnit.NANOSECONDS);
            }
        }

        @Override
        public int compareTo(Delayed other) {
            Task o = (Task) other;
            int byTime = Long.compare(atNanos, o.atNanos);
            return byTime != 0 ? byTime : Long.compare(seq, o.seq);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (ManualScheduler.this) {
                queue.remove(this);
            }
            cancelled = true;
            return !done;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
