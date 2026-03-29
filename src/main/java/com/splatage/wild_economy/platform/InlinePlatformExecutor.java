package com.splatage.wild_economy.platform;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class InlinePlatformExecutor implements PlatformExecutor, AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed;

    public InlinePlatformExecutor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "wild-economy-harness-platform");
            thread.setDaemon(true);
            return thread;
        });
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public void runOnPlayer(final Player player, final Runnable task) {
        Objects.requireNonNull(task, "task");
        task.run();
    }

    @Override
    public void runOnLocation(final Location location, final Runnable task) {
        Objects.requireNonNull(task, "task");
        task.run();
    }

    @Override
    public void runGlobalRepeating(final Runnable task, final long initialDelayTicks, final long periodTicks) {
        Objects.requireNonNull(task, "task");
        if (this.closed.get()) {
            throw new IllegalStateException("Platform executor is already closed");
        }
        final long initialDelayMillis = Math.max(0L, initialDelayTicks) * 50L;
        final long periodMillis = Math.max(1L, periodTicks) * 50L;
        this.scheduler.scheduleAtFixedRate(task, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancelPluginTasks() {
        this.scheduler.shutdownNow();
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.scheduler.shutdownNow();
    }
}
