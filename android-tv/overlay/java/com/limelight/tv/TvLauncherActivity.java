package com.limelight.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.tv.logic.Greeting;
import com.limelight.tv.logic.HostStatus;
import com.limelight.tv.logic.SettingsCatalog;
import com.limelight.tv.ui.Vibe;

import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/** Screen 2 — PC picker. Account-free greeting, large host cards, stable focus. */
public final class TvLauncherActivity extends Activity implements HostService.Listener {
    private HostService service;
    private LinearLayout cards, actions;
    private TextView greeting, hint;
    private View emptyState;
    private final Map<String, Card> byUuid = new LinkedHashMap<>();
    private String focusedUuid;
    private boolean userNavigated;

    private final class Card {
        final String uuid;
        final LinearLayout view;
        final TextView name, chip, detail, initial;
        ComputerDetails host;
        HostStatus status;

        Card(ComputerDetails h) {
            uuid = h.uuid;
            view = Vibe.column(TvLauncherActivity.this, 10);
            view.setPadding(dp(26), dp(24), dp(26), dp(24));
            view.setBackground(Vibe.focusable(TvLauncherActivity.this, Vibe.SURFACE, Vibe.RADIUS_DP));
            view.setFocusable(true);
            view.setClickable(true);
            initial = Vibe.oneLine(TvLauncherActivity.this, "", Vibe.H2, Vibe.BG, true);
            initial.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColors(new int[]{Vibe.ACCENT, Vibe.ACCENT2});
            initial.setBackground(circle);
            view.addView(initial, new LinearLayout.LayoutParams(dp(64), dp(64)));
            name = Vibe.oneLine(TvLauncherActivity.this, "", Vibe.H2, Vibe.TEXT, true);
            view.addView(name);
            chip = Vibe.chip(TvLauncherActivity.this, "", Vibe.TEXT2);
            view.addView(chip, Vibe.wrap());
            detail = Vibe.text(TvLauncherActivity.this, "", Vibe.BODY, Vibe.TEXT2, false);
            view.addView(detail);
            view.setOnClickListener(v -> primary(this));
            Vibe.focusMotion(view, 1.04f);
            final View.OnFocusChangeListener motion = view.getOnFocusChangeListener();
            view.setOnFocusChangeListener((v, has) -> {
                if (motion != null) motion.onFocusChange(v, has);
                if (has) { focusedUuid = uuid; TvPrefs.setLastHost(TvLauncherActivity.this, uuid); renderActions(); }
            });
            update(h);
        }

        void update(ComputerDetails h) {
            host = h;
            status = statusOf(h);
            String n = h.name == null || h.name.isEmpty() ? "PC" : h.name;
            name.setText(n);
            initial.setText(n.substring(0, 1).toUpperCase());
            int dot = status.kind == HostStatus.Kind.READY ? Vibe.ACCENT2 : status.kind == HostStatus.Kind.GAME_RUNNING ? Vibe.ACCENT
                    : status.kind == HostStatus.Kind.PAIRING_NEEDED ? Vibe.TEXT : Vibe.TEXT2;
            TextView fresh = Vibe.chip(TvLauncherActivity.this, status.label, dot);
            chip.setText(fresh.getText());
            detail.setText(status.detail);
        }
    }

    static HostStatus statusOf(ComputerDetails h) {
        HostStatus.Reachability r = h.state == ComputerDetails.State.ONLINE ? HostStatus.Reachability.ONLINE
                : h.state == ComputerDetails.State.OFFLINE ? HostStatus.Reachability.OFFLINE : HostStatus.Reachability.UNKNOWN;
        boolean paired = h.pairState == PairingManager.PairState.PAIRED && h.serverCert != null;
        return HostStatus.of(r, paired, h.runningGameId, h.macAddress != null && !h.macAddress.isEmpty());
    }

    private int dp(float v) { return Vibe.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvPreferences.initialize(this);
        Vibe.applyWindow(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop());
        LinearLayout page = Vibe.column(this, 22);
        page.setPadding(dp(48), dp(32), dp(48), dp(28));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Vibe.row(this, 16);
        top.addView(Vibe.brand(this), Vibe.weight(1));
        top.addView(Vibe.button(this, "Add PC", false, v -> startActivity(new Intent(this, TvAddPcActivity.class))));
        top.addView(Vibe.button(this, "Settings", false, v -> startActivity(new Intent(this, TvSettingsActivity.class))));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout heading = Vibe.column(this, 6);
        greeting = Vibe.oneLine(this, "", Vibe.DISPLAY, Vibe.TEXT, true);
        heading.addView(greeting);
        heading.addView(Vibe.oneLine(this, "Choose a PC to play", Vibe.BODY, Vibe.TEXT2, false));
        page.addView(heading);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(8), dp(12), dp(8), dp(12));
        cards = Vibe.row(this, 24);
        scroller.addView(cards);
        page.addView(scroller, new LinearLayout.LayoutParams(-1, -2));

        emptyState = Vibe.state(this, "Looking for PCs on your network…",
                "Make sure Vibebeam or Sunshine is running on your PC. If nothing appears, add it by IP address.", false);
        Vibe.pulse(emptyState);
        page.addView(emptyState, new LinearLayout.LayoutParams(-2, -2));

        actions = Vibe.row(this, 16);
        page.addView(actions);
        hint = Vibe.oneLine(this, "", Vibe.LABEL, Vibe.TEXT2, false);
        page.addView(hint);
        setContentView(root);

        service = new HostService(this, this);
        service.create();
        renderActions();
    }

    private android.graphics.drawable.Drawable backdrop() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF141A3A, Vibe.BG, Vibe.BG});
        return g;
    }

    @Override protected void onResume() {
        super.onResume();
        greeting.setText(Greeting.forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                TvPrefs.get(this).getString(SettingsCatalog.KEY_DISPLAY_NAME, "")));
        service.resume();
    }

    @Override protected void onPause() { service.pause(); super.onPause(); }

    @Override protected void onDestroy() { service.destroy(); super.onDestroy(); }

    @Override public void onReady(ComputerManagerService.ComputerManagerBinder binder) { renderActions(); }

    @Override public void onLost() { hint.setText("The host service stopped. Reopen Vibelight to retry."); }

    @Override public void onHost(ComputerDetails snapshot) {
        Card c = byUuid.get(snapshot.uuid);
        if (c == null) {
            c = new Card(snapshot);
            byUuid.put(snapshot.uuid, c);
            cards.addView(c.view, new LinearLayout.LayoutParams(dp(300), -2));
            emptyState.setVisibility(View.GONE);
        } else {
            c.update(snapshot);
        }
        // Focus never moves under the user: once they press a key, discovery updates
        // only change card contents. Before that, the remembered host (or the first
        // card) takes focus.
        if (!userNavigated) {
            String last = TvPrefs.lastHost(this);
            boolean cardFocused = focusedUuid != null && byUuid.containsKey(focusedUuid);
            if (snapshot.uuid.equals(last) || !cardFocused) c.view.requestFocus();
        }
        if (snapshot.uuid.equals(focusedUuid)) renderActions();
    }

    private void renderActions() {
        actions.removeAllViews();
        Card c = focusedUuid == null ? null : byUuid.get(focusedUuid);
        if (c == null) {
            actions.addView(Vibe.button(this, "Add PC by IP address", false, v -> startActivity(new Intent(this, TvAddPcActivity.class))));
            actions.addView(Vibe.button(this, "Classic dashboard", false, v -> startActivity(new Intent(this, TvHomeActivity.class))));
            hint.setText("");
            return;
        }
        HostStatus s = c.status;
        if (s.canResume) actions.addView(Vibe.button(this, "Resume game", true, v -> openLibrary(c, true)));
        if (s.canOpenLibrary) actions.addView(Vibe.button(this, "Open library", !s.canResume, v -> openLibrary(c, false)));
        if (s.canPair) actions.addView(Vibe.button(this, "Pair this PC", true, v -> pair(c)));
        if (s.canWake && s.kind != HostStatus.Kind.CHECKING) actions.addView(Vibe.button(this, "Wake PC", true, v -> wake(c)));
        actions.addView(Vibe.button(this, "Classic dashboard", false, v -> startActivity(new Intent(this, TvHomeActivity.class))));
        hint.setText(s.kind == HostStatus.Kind.OFFLINE ? "No MAC address is known for this PC, so it can't be woken from here."
                : s.kind == HostStatus.Kind.CHECKING ? "Waiting for " + c.name.getText() + " to answer…" : "");
    }

    private void primary(Card c) {
        switch (c.status.kind) {
            case READY: case GAME_RUNNING: openLibrary(c, false); break;
            case PAIRING_NEEDED: pair(c); break;
            case OFFLINE_WAKEABLE: wake(c); break;
            default: hint.setText(c.status.detail); break;
        }
    }

    private void openLibrary(Card c, boolean resume) {
        startActivity(new Intent(this, TvLibraryActivity.class)
                .putExtra(TvLibraryActivity.EXTRA_HOST, c.uuid)
                .putExtra(TvLibraryActivity.EXTRA_RESUME, resume));
    }

    private void pair(Card c) {
        startActivity(new Intent(this, TvPairActivity.class).putExtra(TvPairActivity.EXTRA_HOST, c.uuid));
    }

    private void wake(final Card c) {
        final ComputerDetails h = new ComputerDetails(c.host);
        hint.setText("Sending a wake packet to " + c.name.getText() + "…");
        new Thread(() -> {
            String msg;
            try {
                WakeOnLanSender.sendWolPacket(h);
                msg = "Wake packet sent. " + h.name + " will appear online once it starts and Vibebeam is running.";
            } catch (IOException e) {
                msg = "Couldn't send the wake packet: " + e.getMessage();
            }
            final String text = msg;
            runOnUiThread(() -> hint.setText(text));
        }).start();
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) userNavigated = true;
        return super.dispatchKeyEvent(event);
    }
}
