package com.limelight.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.tv.ui.RingView;
import com.limelight.tv.ui.Vibe;
import com.limelight.utils.ServerHelper;

import java.io.FileNotFoundException;
import java.net.UnknownHostException;

/**
 * Screen 5 — pairing. Reuses Moonlight's PairingManager exactly as the stock PC
 * manager does: same PIN generation, same certificate pinning, same state
 * invalidation and the same pause of host polling while pairing. No new crypto.
 */
public final class TvPairActivity extends Activity implements HostService.Listener {
    public static final String EXTRA_HOST = "vibe_host_uuid";

    private HostService service;
    private String uuid;
    private TextView title, pin, instruction, status;
    private RingView ring;
    private LinearLayout buttons;
    private volatile long generation;
    private boolean started;

    private int dp(float v) { return Vibe.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Vibe.applyWindow(this);
        uuid = getIntent().getStringExtra(EXTRA_HOST);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Vibe.BG);
        LinearLayout col = Vibe.column(this, 20);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(Vibe.brand(this));
        title = Vibe.text(this, "Pair with your PC", Vibe.H1, Vibe.TEXT, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        pin = Vibe.oneLine(this, "• • • •", 96, Vibe.ACCENT2, true);
        pin.setLetterSpacing(0.35f);
        pin.setGravity(Gravity.CENTER);
        pin.setPadding(dp(48), dp(12), dp(36), dp(12));
        pin.setBackground(Vibe.rounded(this, Vibe.BG2, Vibe.OUTLINE, Vibe.RADIUS_DP, 1));
        col.addView(pin, Vibe.wrap());
        instruction = Vibe.text(this, "Preparing a PIN…", Vibe.BODY, Vibe.TEXT2, false);
        instruction.setGravity(Gravity.CENTER);
        col.addView(instruction, new LinearLayout.LayoutParams(dp(760), -2));
        LinearLayout statusRow = Vibe.row(this, 14);
        ring = new RingView(this);
        statusRow.addView(ring, new LinearLayout.LayoutParams(dp(36), dp(36)));
        status = Vibe.oneLine(this, "Connecting to the host service…", Vibe.BODY, Vibe.TEXT, false);
        statusRow.addView(status);
        col.addView(statusRow);
        buttons = Vibe.row(this, 16);
        col.addView(buttons);
        root.addView(col, Vibe.frame(-2, -2, Gravity.CENTER));
        setContentView(root);
        showButtons(false);
        service = new HostService(this, this);
        service.create();
    }

    private void showButtons(boolean failed) {
        buttons.removeAllViews();
        if (failed) {
            TextView again = Vibe.button(this, "Try again", true, v -> { started = false; begin(); });
            buttons.addView(again);
            buttons.addView(Vibe.button(this, "Back", false, v -> finish()));
            again.requestFocus();
        } else {
            TextView cancel = Vibe.button(this, "Cancel", false, v -> cancel());
            buttons.addView(cancel);
            cancel.requestFocus();
        }
    }

    private void cancel() {
        generation++;
        finish();
    }

    @Override protected void onResume() { super.onResume(); service.resume(); }
    @Override protected void onPause() { service.pause(); super.onPause(); }
    @Override protected void onDestroy() { generation++; service.destroy(); super.onDestroy(); }

    @Override public void onReady(ComputerManagerService.ComputerManagerBinder binder) { begin(); }
    @Override public void onHost(ComputerDetails snapshot) {}
    @Override public void onLost() { fail("The host service stopped", "Reopen Vibelight to retry."); }

    private void begin() {
        if (started) return;
        final ComputerManagerService.ComputerManagerBinder binder = service.binder();
        final ComputerDetails host = service.fresh(uuid);
        if (binder == null || host == null) { fail("This PC is no longer known", "Go back and choose it again."); return; }
        title.setText("Pair with " + host.name);
        if (host.state == ComputerDetails.State.OFFLINE || host.activeAddress == null) {
            fail(host.name + " is offline", "Turn it on, start Vibebeam, then try again."); return;
        }
        started = true;
        ring.setMode(RingView.Mode.WORKING);
        showButtons(false);
        status.setTextColor(Vibe.TEXT);
        status.setText("Waiting for the PIN on " + host.name + "…");
        final long gen = ++generation;
        final String pinStr = PairingManager.generatePinString();
        pin.setText(pinStr);
        instruction.setText("On your PC, open Vibebeam or Sunshine in a browser and enter this PIN.");
        service.suspendPolling();
        new Thread(() -> {
            String message = null;
            boolean success = false;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(host), host.httpsPort, binder.getUniqueId(),
                        host.serverCert, PlatformBinding.getCryptoProvider(this));
                if (http.getPairState() == PairState.PAIRED) {
                    success = true;
                } else {
                    PairingManager pm = http.getPairingManager();
                    PairState result = pm.pair(http.getServerInfo(true), pinStr);
                    if (result == PairState.PIN_WRONG) message = "The PIN entered on the PC didn't match.";
                    else if (result == PairState.FAILED) message = host.runningGameId != 0
                            ? "Pairing failed. The PC is running a game; quit it and try again." : "Pairing failed.";
                    else if (result == PairState.ALREADY_IN_PROGRESS) message = "Another device is pairing with this PC right now.";
                    else if (result == PairState.PAIRED) {
                        success = true;
                        // Same as the stock PC manager: pin the certificate, then force a fresh state read.
                        ComputerDetails stored = binder.getComputer(host.uuid);
                        if (stored != null) stored.serverCert = pm.getPairedCert();
                        binder.invalidateStateForComputer(host.uuid);
                    }
                }
            } catch (UnknownHostException e) {
                message = "The PC's address couldn't be resolved.";
            } catch (FileNotFoundException e) {
                message = "The PC rejected the pairing request (404).";
            } catch (Exception e) {
                message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String m = message;
            final boolean ok = success;
            runOnUiThread(() -> {
                service.resumePolling();
                // A cancelled attempt never navigates; a late success is still stored above
                // because the PC now trusts this device.
                if (gen != generation || isFinishing()) return;
                if (ok) succeed(host);
                else fail("Pairing didn't finish", m == null ? "Unknown result." : m);
            });
        }).start();
    }

    private void succeed(final ComputerDetails host) {
        ring.setMode(RingView.Mode.DONE);
        status.setText("Paired with " + host.name + ". Opening your games…");
        instruction.setText("");
        buttons.removeAllViews();
        long delay = Vibe.reducedEffects(this) ? 300 : 900;
        final long gen = generation;
        pin.postDelayed(() -> {
            if (gen != generation || isFinishing()) return;
            startActivity(new Intent(this, TvLibraryActivity.class).putExtra(TvLibraryActivity.EXTRA_HOST, host.uuid));
            finish();
        }, delay);
    }

    private void fail(String what, String why) {
        started = false;
        ring.setMode(RingView.Mode.FAILED);
        status.setTextColor(Vibe.ERROR);
        status.setText(what);
        instruction.setText(why);
        showButtons(true);
    }
}
