package com.limelight.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.tv.logic.BitrateProfile;
import com.limelight.tv.logic.ConnectionStages;
import com.limelight.tv.logic.PanelGeometry;
import com.limelight.tv.logic.RecentGames;
import com.limelight.tv.logic.SettingsCatalog;
import com.limelight.tv.ui.RingView;
import com.limelight.tv.ui.Vibe;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Vibelight's connection screen and in-stream side panel, hosted inside the
 * upstream Game activity through small checked hooks. Views only: no Compose in
 * the stream activity. When the panel is closed nothing here runs per frame.
 */
public final class TvStreamUi {
    private TvStreamUi() {}

    private static final WeakHashMap<Activity, TvStreamUi.Holder> HOLDERS = new WeakHashMap<>();
    private static volatile boolean inputBlocked;

    /** Read by the patched ControllerHandler: no controller packets while the panel owns input. */
    public static boolean inputBlocked() { return inputBlocked; }

    static final class Holder {
        final Activity activity;
        final ConnectionStages stages;
        final String hostName, appName;
        FrameLayout connectView;
        RingView ring;
        LinearLayout stageList;
        TextView title, detail;
        LinearLayout actions;
        boolean connected, failed, finishedByUs;
        FrameLayout panelRoot;
        LinearLayout panel;
        boolean panelOpen;
        boolean backDown;
        long lastHatMove;

        Holder(Activity a) {
            activity = a;
            Intent i = a.getIntent();
            hostName = nonEmpty(i.getStringExtra(Game.EXTRA_PC_NAME), "your PC");
            appName = nonEmpty(i.getStringExtra(Game.EXTRA_APP_NAME), "");
            stages = new ConnectionStages(appName);
        }
    }

    private static String nonEmpty(String s, String d) { return s == null || s.isEmpty() ? d : s; }

    private static Holder holder(Activity a) {
        synchronized (HOLDERS) { return HOLDERS.get(a); }
    }

    // ---------------------------------------------------------------- connection screen

    /** Replaces the upstream connection spinner. Called right after setContentView. */
    public static void attach(Activity a) {
        final Holder h = new Holder(a);
        synchronized (HOLDERS) { HOLDERS.put(a, h); }
        inputBlocked = false;
        Context c = a;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Vibe.BG);
        root.setClickable(true);
        LinearLayout col = Vibe.column(c, 18);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        h.ring = new RingView(c);
        col.addView(h.ring, new LinearLayout.LayoutParams(Vibe.dp(c, 132), Vibe.dp(c, 132)));
        h.title = Vibe.text(c, "Connecting to " + h.hostName, Vibe.H1, Vibe.TEXT, true);
        h.title.setGravity(Gravity.CENTER);
        col.addView(h.title);
        boolean pinned = a.getIntent().getByteArrayExtra(Game.EXTRA_SERVER_CERT) != null;
        h.detail = Vibe.text(c, (h.appName.isEmpty() ? "" : h.appName + "  ·  ")
                + (pinned ? "Paired · using this PC's saved certificate" : "Certificate not saved; pairing will be checked"), Vibe.BODY, Vibe.TEXT2, false);
        h.detail.setGravity(Gravity.CENTER);
        col.addView(h.detail);
        h.stageList = Vibe.column(c, 6);
        h.stageList.setPadding(Vibe.dp(c, 28), Vibe.dp(c, 18), Vibe.dp(c, 28), Vibe.dp(c, 18));
        h.stageList.setBackground(Vibe.rounded(c, Vibe.BG2, Vibe.OUTLINE, Vibe.RADIUS_DP, 1));
        col.addView(h.stageList);
        h.actions = Vibe.row(c, 16);
        h.actions.setGravity(Gravity.CENTER);
        col.addView(h.actions);
        root.addView(col, Vibe.frame(-2, -2, Gravity.CENTER));
        h.connectView = root;
        a.addContentView(root, new ViewGroup.LayoutParams(-1, -1));
        renderStages(h);
        showConnectingActions(h);
    }

    private static void renderStages(Holder h) {
        Context c = h.activity;
        h.stageList.removeAllViews();
        for (ConnectionStages.Step s : h.stages.steps()) {
            String glyph; int color;
            switch (s.state) {
                case DONE: glyph = "✓"; color = Vibe.ACCENT2; break;
                case ACTIVE: glyph = "●"; color = Vibe.TEXT; break;
                case FAILED: glyph = "✕"; color = Vibe.ERROR; break;
                default: glyph = "○"; color = Vibe.TEXT2; break;
            }
            TextView t = Vibe.oneLine(c, glyph + "   " + s.label, Vibe.BODY, color, s.state == ConnectionStages.State.ACTIVE);
            h.stageList.addView(t);
        }
    }

    private static void showConnectingActions(final Holder h) {
        h.actions.removeAllViews();
        TextView cancel = Vibe.button(h.activity, "Cancel", false, v -> cancel(h));
        h.actions.addView(cancel);
        cancel.requestFocus();
    }

    private static void cancel(Holder h) {
        h.stages.invalidate();
        h.finishedByUs = true;
        h.activity.finish();
    }

    public static void stageStarting(Activity a, final String stage) {
        final Holder h = holder(a);
        if (h == null) return;
        final long gen = h.stages.generation();
        a.runOnUiThread(() -> {
            if (gen != h.stages.generation() || h.connected || h.failed) return;
            h.stages.starting(stage);
            renderStages(h);
        });
    }

    public static void stageComplete(Activity a, final String stage) {
        final Holder h = holder(a);
        if (h == null) return;
        final long gen = h.stages.generation();
        a.runOnUiThread(() -> {
            if (gen != h.stages.generation() || h.connected || h.failed) return;
            h.stages.complete(stage);
            renderStages(h);
        });
    }

    /** Stream is up: record truthful history and remove the connection screen. */
    public static void connectionStarted(final Activity a) {
        final Holder h = holder(a);
        recordRecent(a);
        if (h == null) return;
        a.runOnUiThread(() -> {
            if (h.failed) return;
            h.connected = true;
            h.stages.connected();
            renderStages(h);
            h.ring.setMode(RingView.Mode.DONE);
            final View v = h.connectView;
            if (v == null) return;
            if (Vibe.reducedEffects(a)) removeView(v);
            else v.animate().alpha(0f).setDuration(220).withEndAction(() -> removeView(v)).start();
            h.connectView = null;
            refocusStream(a);
        });
    }

    private static void removeView(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private static void recordRecent(Activity a) {
        Intent i = a.getIntent();
        String host = i.getStringExtra(Game.EXTRA_PC_UUID);
        int appId = i.getIntExtra(Game.EXTRA_APP_ID, 0);
        String name = i.getStringExtra(Game.EXTRA_APP_NAME);
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(a);
        synchronized (TvStreamUi.class) {
            List<RecentGames.Entry> cur = RecentGames.decode(p.getString(TvPrefs.KEY_RECENT_GAMES, ""));
            p.edit().putString(TvPrefs.KEY_RECENT_GAMES, RecentGames.encode(RecentGames.record(cur, host, appId, name, System.currentTimeMillis()))).apply();
        }
    }

    /**
     * Replaces the upstream failure dialog with an actionable screen. Text comes from
     * the core (stage, error code, port findings); nothing is guessed.
     * @return true when handled here
     */
    public static boolean failed(final Activity a, final String title, final String message, final String stage, final int errorCode) {
        final Holder h = holder(a);
        if (h == null || h.finishedByUs) return h != null;
        closePanel(h, false);
        h.failed = true;
        h.stages.failed(stage);
        if (h.connectView == null) {
            // Failure after the stream started: bring the screen back over the stream.
            attachFailureOnly(h);
        }
        renderStages(h);
        h.ring.setMode(RingView.Mode.FAILED);
        h.title.setText(title);
        h.title.setTextColor(Vibe.ERROR);
        String what = stage == null ? "" : "While " + h.stages.describeStage(stage).toLowerCase() + ".\n";
        h.detail.setText(what + message);
        h.actions.removeAllViews();
        TextView retry = Vibe.button(a, "Try again", true, v -> retry(h));
        h.actions.addView(retry);
        h.actions.addView(Vibe.button(a, "Back to PCs", false, v -> backToPcs(h)));
        retry.requestFocus();
        return true;
    }

    private static void attachFailureOnly(Holder h) {
        Context c = h.activity;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Vibe.SCRIM);
        root.setClickable(true);
        LinearLayout col = Vibe.column(c, 18);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        h.ring = new RingView(c);
        col.addView(h.ring, new LinearLayout.LayoutParams(Vibe.dp(c, 96), Vibe.dp(c, 96)));
        h.title = Vibe.text(c, "", Vibe.H1, Vibe.TEXT, true);
        h.title.setGravity(Gravity.CENTER);
        col.addView(h.title);
        h.detail = Vibe.text(c, "", Vibe.BODY, Vibe.TEXT2, false);
        h.detail.setGravity(Gravity.CENTER);
        col.addView(h.detail, new LinearLayout.LayoutParams(Vibe.dp(c, 720), -2));
        h.stageList = Vibe.column(c, 6);
        h.actions = Vibe.row(c, 16);
        h.actions.setGravity(Gravity.CENTER);
        col.addView(h.actions);
        root.addView(col, Vibe.frame(-2, -2, Gravity.CENTER));
        h.connectView = root;
        h.activity.addContentView(root, new ViewGroup.LayoutParams(-1, -1));
    }

    private static void retry(Holder h) {
        h.stages.invalidate();
        h.finishedByUs = true;
        Intent again = new Intent(h.activity.getIntent());
        h.activity.finish();
        h.activity.startActivity(again);
    }

    private static void backToPcs(Holder h) {
        h.stages.invalidate();
        h.finishedByUs = true;
        Intent home = new Intent(h.activity, TvLauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        h.activity.finish();
        h.activity.startActivity(home);
    }

    // ---------------------------------------------------------------- input routing

    /** Called first by Game.handleKeyDown/handleKeyUp. Returns true when consumed here. */
    public static boolean onKey(Activity a, KeyEvent e) {
        Holder h = holder(a);
        if (h == null) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();

        // Before the stream starts or after a failure, the connection screen owns input.
        if (!h.connected || h.failed) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B) {
                if (!down) { if (h.failed) backToPcs(h); else cancel(h); }
                return true;
            }
            return navigate(h.connectView, e);
        }

        if (h.panelOpen) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) closePanel(h, true);
                return true;
            }
            return navigate(h.panel, e);
        }

        // Remote Back (not a game controller's Back/Select) opens the panel instead of
        // ending the stream at once. Disconnect is the panel's first action.
        if (code == KeyEvent.KEYCODE_BACK && !ControllerHandler.isGameControllerDevice(e.getDevice())
                && (e.getSource() & InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE) {
            if (down) { h.backDown = true; return true; }
            if (h.backDown) { h.backDown = false; openPanel(h); }
            return true;
        }
        return false;
    }

    /** Called first by Game.handleMotionEvent. */
    public static boolean onMotion(Activity a, MotionEvent e) {
        Holder h = holder(a);
        if (h == null) return false;
        View host = !h.connected || h.failed ? h.connectView : h.panelOpen ? h.panel : null;
        if (host == null) return false;
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) {
                x = e.getAxisValue(MotionEvent.AXIS_X); y = e.getAxisValue(MotionEvent.AXIS_Y);
                if (Math.abs(x) < 0.7f) x = 0; if (Math.abs(y) < 0.7f) y = 0;
            }
            long now = SystemClock.uptimeMillis();
            if ((x != 0 || y != 0) && now - h.lastHatMove > 220) {
                h.lastHatMove = now;
                int dir = Math.abs(y) >= Math.abs(x) ? (y > 0 ? View.FOCUS_DOWN : View.FOCUS_UP) : (x > 0 ? View.FOCUS_RIGHT : View.FOCUS_LEFT);
                moveFocus(host, dir);
            }
        }
        return true;
    }

    private static boolean navigate(View container, KeyEvent e) {
        if (container == null) return true;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int dir;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_SPACE:
                if (!down) {
                    View f = container.findFocus();
                    if (f != null) f.performClick();
                }
                return true;
            default:
                return true; // swallow everything else while the overlay owns input
        }
        if (down) moveFocus(container, dir);
        return true;
    }

    private static void moveFocus(View container, int dir) {
        View f = container.findFocus();
        if (f == null) { container.requestFocus(); return; }
        View next = f.focusSearch(dir);
        if (next != null && isDescendant(container, next)) next.requestFocus();
    }

    private static boolean isDescendant(View parent, View v) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    private static void refocusStream(Activity a) {
        if (a instanceof Game) {
            View s = ((Game) a).vibeStreamView();
            if (s != null) s.requestFocus();
        }
    }

    // ---------------------------------------------------------------- side panel

    /** Patched quit combo (Start+Back+LB+RB). Returns true when the panel was opened instead. */
    public static boolean onQuitCombo(Activity a) {
        Holder h = holder(a);
        if (h == null || !h.connected || h.failed) return false;
        if (!PreferenceManager.getDefaultSharedPreferences(a).getBoolean(SettingsCatalog.KEY_PANEL_COMBO, true)) return false;
        a.runOnUiThread(() -> openPanel(h));
        return true;
    }

    static void openPanel(final Holder h) {
        if (h.panelOpen || !h.connected || h.failed) return;
        final Activity a = h.activity;
        if (a instanceof Game) ((Game) a).vibeReleaseInput();
        inputBlocked = true;
        h.panelOpen = true;
        if (h.panelRoot == null) buildPanel(h);
        refreshPanel(h);
        h.panelRoot.setVisibility(View.VISIBLE);
        View slide = (View) h.panel.getParent();
        if (!Vibe.reducedEffects(a)) {
            slide.setTranslationX(slide.getLayoutParams().width);
            slide.animate().translationX(0).setDuration(160).start();
        } else {
            slide.setTranslationX(0);
        }
        View first = h.panel.findViewWithTag("first");
        if (first != null) first.requestFocus();
    }

    static void closePanel(Holder h, boolean animate) {
        if (!h.panelOpen) return;
        h.panelOpen = false;
        inputBlocked = false;
        if (h.panelRoot != null) h.panelRoot.setVisibility(View.GONE);
        refocusStream(h.activity);
    }

    private static void buildPanel(final Holder h) {
        Activity a = h.activity;
        FrameLayout root = new FrameLayout(a);
        root.setVisibility(View.GONE);
        LinearLayout panel = Vibe.column(a, 12);
        panel.setPadding(Vibe.dp(a, 28), Vibe.dp(a, 28), Vibe.dp(a, 28), Vibe.dp(a, 28));
        panel.setBackground(Vibe.rounded(a, 0xF00D1424, Vibe.OUTLINE, 0, 0));
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        int width = PanelGeometry.width(screen, Vibe.dp(a, 380), Vibe.dp(a, 280));
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        root.addView(scroll, Vibe.frame(width, -1, Gravity.END));
        h.panel = panel;
        h.panelRoot = root;
        a.addContentView(root, new ViewGroup.LayoutParams(-1, -1));
    }

    private static void refreshPanel(final Holder h) {
        final Activity a = h.activity;
        LinearLayout p = h.panel;
        p.removeAllViews();
        p.addView(Vibe.brand(a));
        p.addView(Vibe.oneLine(a, h.appName.isEmpty() ? h.hostName : h.appName + " · " + h.hostName, Vibe.BODY, Vibe.TEXT2, false));

        TextView resume = Vibe.button(a, "Resume", true, v -> closePanel(h, true));
        resume.setTag("first");
        p.addView(resume, full());
        p.addView(Vibe.button(a, "Disconnect (game keeps running)", false, v -> { closePanel(h, false); h.finishedByUs = true; a.finish(); }), full());
        p.addView(Vibe.button(a, "Quit game…", false, v -> confirmQuit(h)), full());

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(a);
        int kbps = prefs.getInt(BitrateProfile.KEY_BITRATE, BitrateProfile.BALANCED.kbps);
        p.addView(Vibe.text(a, "Quality — applies to the next stream", Vibe.LABEL, Vibe.TEXT2, true));
        for (final BitrateProfile prof : BitrateProfile.values()) {
            String mark = prof.kbps == kbps ? "✓  " : "";
            p.addView(Vibe.button(a, mark + prof.label + " · " + prof.kbps / 1000 + " Mbps", false, v -> {
                TvPrefs.applyBitrateOnly(a, prof);
                Toast.makeText(a, prof.label + " will be used for the next stream", Toast.LENGTH_SHORT).show();
                refreshPanel(h);
                View again = h.panel.findViewWithTag("first");
                if (again != null) again.requestFocus();
            }), full());
        }
        final boolean stats = prefs.getBoolean("checkbox_enable_perf_overlay", false);
        p.addView(Vibe.button(a, "Statistics overlay: " + (stats ? "On" : "Off"), false, v -> {
            prefs.edit().putBoolean("checkbox_enable_perf_overlay", !stats).apply();
            if (a instanceof Game) ((Game) a).vibeSetPerfOverlay(!stats);
            refreshPanel(h);
        }), full());
        p.addView(Vibe.text(a, "Back closes this panel. Your game is not paused.", Vibe.LABEL, Vibe.TEXT2, false));
    }

    private static LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }

    private static void confirmQuit(final Holder h) {
        final Activity a = h.activity;
        LinearLayout p = h.panel;
        p.removeAllViews();
        p.addView(Vibe.text(a, "Quit " + (h.appName.isEmpty() ? "this game" : h.appName) + "?", Vibe.H2, Vibe.TEXT, true));
        p.addView(Vibe.text(a, "The game closes on " + h.hostName + ". Unsaved progress may be lost.", Vibe.BODY, Vibe.TEXT2, false));
        TextView no = Vibe.button(a, "Keep playing", true, v -> { refreshPanel(h); View f = h.panel.findViewWithTag("first"); if (f != null) f.requestFocus(); });
        p.addView(no, full());
        p.addView(Vibe.button(a, "Quit game", false, v -> quitGame(h)), full());
        no.requestFocus();
    }

    private static void quitGame(final Holder h) {
        final Activity a = h.activity;
        final Context app = a.getApplicationContext();
        final Intent i = a.getIntent();
        closePanel(h, false);
        h.finishedByUs = true;
        a.finish();
        new Thread(() -> {
            String msg;
            try {
                X509Certificate cert = null;
                byte[] der = i.getByteArrayExtra(Game.EXTRA_SERVER_CERT);
                if (der != null) cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
                NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple(i.getStringExtra(Game.EXTRA_HOST), i.getIntExtra(Game.EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT)),
                        i.getIntExtra(Game.EXTRA_HTTPS_PORT, 0), i.getStringExtra(Game.EXTRA_UNIQUEID), cert, PlatformBinding.getCryptoProvider(app));
                msg = http.quitApp() ? "Quit " + nonEmpty(i.getStringExtra(Game.EXTRA_APP_NAME), "the game") : "The PC did not quit the game";
            } catch (Exception e) {
                msg = "Could not quit the game: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final String text = msg;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
        }).start();
    }

    /** Game destroyed: drop references and never leave controller input blocked. */
    public static void detach(Activity a) {
        synchronized (HOLDERS) { HOLDERS.remove(a); }
        inputBlocked = false;
    }
}
