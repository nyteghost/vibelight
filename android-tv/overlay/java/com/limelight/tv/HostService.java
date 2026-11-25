package com.limelight.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Binds the existing Moonlight host service for one Activity. Polling runs only
 * while the Activity is in the foreground (same lifecycle as the classic
 * dashboard and the stock PC manager), callbacks carry copied snapshots on the
 * UI thread, and callbacks from an earlier foreground period are dropped.
 */
public final class HostService {
    public interface Listener {
        void onReady(ComputerManagerService.ComputerManagerBinder binder);
        void onHost(ComputerDetails snapshot);
        void onLost();
    }

    private final Activity activity;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, ComputerDetails> hosts = new LinkedHashMap<>();
    private ComputerManagerService.ComputerManagerBinder binder;
    private boolean bound, foreground, polling;
    private long generation;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder value) {
            final ComputerManagerService.ComputerManagerBinder connected = (ComputerManagerService.ComputerManagerBinder) value;
            io.execute(() -> {
                connected.waitForReady();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !bound) return;
                    binder = connected;
                    listener.onReady(connected);
                    startPolling();
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null; polling = false;
            listener.onLost();
        }
    };

    public HostService(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void create() {
        bound = activity.bindService(new Intent(activity, ComputerManagerService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) listener.onLost();
    }

    public void resume() { foreground = true; startPolling(); }

    public void pause() {
        foreground = false;
        generation++;
        if (binder != null && polling) { binder.stopPolling(); polling = false; }
    }

    public void destroy() {
        if (bound) { activity.unbindService(connection); bound = false; }
        io.shutdownNow();
    }

    public ComputerManagerService.ComputerManagerBinder binder() { return binder; }

    public Map<String, ComputerDetails> hosts() { return hosts; }

    /** Latest copy from the service itself, used immediately before launching. */
    public ComputerDetails fresh(String uuid) {
        if (binder == null || uuid == null) return null;
        ComputerDetails d = binder.getComputer(uuid);
        return d == null ? null : new ComputerDetails(d);
    }

    /** Pauses polling (e.g. while pairing) without leaving the foreground. */
    public void suspendPolling() {
        if (binder != null && polling) { binder.stopPolling(); polling = false; generation++; }
    }

    public void resumePolling() { startPolling(); }

    private void startPolling() {
        if (!foreground || binder == null || polling) return;
        polling = true;
        final long gen = ++generation;
        binder.startPolling(details -> {
            final ComputerDetails snapshot = new ComputerDetails(details);
            activity.runOnUiThread(() -> {
                if (!foreground || !polling || gen != generation || snapshot.uuid == null) return;
                hosts.put(snapshot.uuid, snapshot);
                listener.onHost(snapshot);
            });
        });
    }
}
