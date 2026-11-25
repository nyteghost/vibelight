package com.limelight.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.tv.ui.RingView;
import com.limelight.tv.ui.Vibe;

import java.net.URI;

/** Manual IP entry for PCs that discovery can't see (other VLANs, mDNS blocked). */
public final class TvAddPcActivity extends Activity implements HostService.Listener {
    private HostService service;
    private EditText address;
    private TextView result;
    private RingView ring;
    private TextView add;
    private volatile boolean busy;

    private int dp(float v) { return Vibe.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Vibe.applyWindow(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Vibe.BG);
        LinearLayout col = Vibe.column(this, 20);
        col.setPadding(dp(48), dp(40), dp(48), dp(40));
        col.addView(Vibe.brand(this));
        col.addView(Vibe.text(this, "Add a PC by IP address", Vibe.H1, Vibe.TEXT, true));
        col.addView(Vibe.text(this, "Enter the PC's local address, for example 192.168.1.20. Add :port only if you changed Vibebeam's port.", Vibe.BODY, Vibe.TEXT2, false));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("192.168.1.20");
        address.setHintTextColor(Vibe.TEXT2);
        address.setTextColor(Vibe.TEXT);
        address.setTextSize(Vibe.H2);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_DONE);
        address.setPadding(dp(24), dp(10), dp(24), dp(10));
        address.setBackground(Vibe.focusable(this, Vibe.SURFACE, Vibe.RADIUS_SMALL_DP));
        address.setOnEditorActionListener((v, id, e) -> { submit(); return true; });
        col.addView(address, new LinearLayout.LayoutParams(dp(560), -2));
        LinearLayout row = Vibe.row(this, 16);
        add = Vibe.button(this, "Add PC", true, v -> submit());
        row.addView(add);
        row.addView(Vibe.button(this, "Back", false, v -> finish()));
        ring = new RingView(this);
        ring.setVisibility(android.view.View.GONE);
        row.addView(ring, new LinearLayout.LayoutParams(dp(36), dp(36)));
        col.addView(row);
        result = Vibe.text(this, "", Vibe.BODY, Vibe.TEXT2, false);
        col.addView(result);
        root.addView(col, Vibe.frame(-1, -2, Gravity.TOP));
        setContentView(root);
        address.requestFocus();
        service = new HostService(this, this);
        service.create();
    }

    @Override protected void onResume() { super.onResume(); service.resume(); }
    @Override protected void onPause() { service.pause(); super.onPause(); }
    @Override protected void onDestroy() { service.destroy(); super.onDestroy(); }
    @Override public void onReady(ComputerManagerService.ComputerManagerBinder binder) {}
    @Override public void onHost(ComputerDetails snapshot) {}
    @Override public void onLost() { result.setText("The host service stopped. Reopen Vibelight to retry."); }

    /** Parses "host", "host:port", "[v6]:port" the same way the stock screen does. */
    static ComputerDetails.AddressTuple parse(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return null;
        try {
            URI uri = new URI("moonlight://" + s);
            if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
            int port = uri.getPort() == -1 ? NvHTTP.DEFAULT_HTTP_PORT : uri.getPort();
            return new ComputerDetails.AddressTuple(uri.getHost(), port);
        } catch (Exception e) {
            return null;
        }
    }

    private void submit() {
        if (busy) return;
        final ComputerManagerService.ComputerManagerBinder binder = service.binder();
        if (binder == null) { result.setText("Still connecting to the host service. Try again in a moment."); return; }
        final ComputerDetails.AddressTuple tuple = parse(address.getText().toString());
        if (tuple == null) { result.setTextColor(Vibe.ERROR); result.setText("That doesn't look like an address. Use a form like 192.168.1.20."); return; }
        busy = true;
        ring.setVisibility(android.view.View.VISIBLE);
        ring.setMode(RingView.Mode.WORKING);
        result.setTextColor(Vibe.TEXT2);
        result.setText("Contacting " + tuple.address + "…");
        new Thread(() -> {
            boolean ok;
            String err = null;
            try {
                ComputerDetails d = new ComputerDetails();
                d.manualAddress = tuple;
                ok = binder.addComputerBlocking(d);
            } catch (InterruptedException e) {
                ok = false; err = "Cancelled.";
                Thread.currentThread().interrupt();
            } catch (IllegalArgumentException e) {
                ok = false; err = "That address isn't valid.";
            }
            final boolean success = ok;
            final String why = err;
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing()) return;
                ring.setMode(success ? RingView.Mode.DONE : RingView.Mode.FAILED);
                if (success) {
                    result.setTextColor(Vibe.ACCENT2);
                    result.setText("Added. It will appear in the PC picker.");
                    address.postDelayed(this::finish, Vibe.reducedEffects(this) ? 300 : 900);
                } else {
                    result.setTextColor(Vibe.ERROR);
                    result.setText(why != null ? why : "No PC answered at " + tuple.address + ":" + tuple.port
                            + ". Check that the PC is on, Vibebeam is running and TCP ports 47984 and 47989 are reachable from this network.");
                }
            });
        }).start();
    }
}
