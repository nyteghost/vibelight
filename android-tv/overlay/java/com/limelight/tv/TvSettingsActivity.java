package com.limelight.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.BuildConfig;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.StreamSettings;
import com.limelight.tv.logic.BitrateProfile;
import com.limelight.tv.logic.SettingsCatalog;
import com.limelight.tv.ui.RingView;
import com.limelight.tv.ui.Vibe;
import com.limelight.tv.ui.VibeDialog;
import com.limelight.utils.ServerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Screen 6 — searchable, grouped settings that reuse the existing preference keys. */
public final class TvSettingsActivity extends Activity implements HostService.Listener {
    private final List<SettingsCatalog.Item> items = SettingsCatalog.all();
    private SettingsCatalog.Group group = SettingsCatalog.Group.GENERAL;
    private LinearLayout rail, content;
    private EditText search;
    private TextView heading;
    private SharedPreferences prefs;
    private HostService service;

    private int dp(float v) { return Vibe.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Vibe.applyWindow(this);
        prefs = TvPrefs.get(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Vibe.BG);
        LinearLayout split = Vibe.row(this, 0);
        split.setGravity(Gravity.TOP);
        root.addView(split, new FrameLayout.LayoutParams(-1, -1));

        ScrollView railScroll = new ScrollView(this);

        railScroll.setFocusable(false);
        railScroll.setBackgroundColor(Vibe.BG2);
        railScroll.setVerticalScrollBarEnabled(false);
        rail = Vibe.column(this, 6);
        rail.setPadding(dp(24), dp(28), dp(20), dp(28));
        rail.addView(Vibe.brand(this));
        for (final SettingsCatalog.Group g : SettingsCatalog.Group.values()) {
            final TextView b = Vibe.oneLine(this, g.label, Vibe.LABEL, Vibe.TEXT, false);
            b.setPadding(dp(18), dp(10), dp(18), dp(10));
            b.setMinHeight(dp(52));
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setBackground(Vibe.focusable(this, Vibe.BG2, Vibe.RADIUS_SMALL_DP));
            b.setFocusable(true);
            b.setClickable(true);
            b.setTag("group:" + g.name());
            b.setOnFocusChangeListener((v, has) -> { if (has && search.getText().length() == 0) show(g); });
            b.setOnClickListener(v -> show(g));
            rail.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
        railScroll.addView(rail);
        split.addView(railScroll, new LinearLayout.LayoutParams(dp(260), -1));

        LinearLayout right = Vibe.column(this, 16);
        right.setPadding(dp(40), dp(28), dp(48), 0);
        search = new EditText(this);
        search.setHint("Search settings");
        search.setHintTextColor(Vibe.TEXT2);
        search.setTextColor(Vibe.TEXT);
        search.setTextSize(Vibe.BODY);
        search.setSingleLine(true);
        search.setPadding(dp(20), dp(8), dp(20), dp(8));
        search.setMinHeight(dp(56));
        search.setBackground(Vibe.focusable(this, Vibe.SURFACE, 999));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { render(); }
        });
        right.addView(search, new LinearLayout.LayoutParams(-1, -2));
        heading = Vibe.oneLine(this, "", Vibe.H1, Vibe.TEXT, true);
        right.addView(heading);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        content = Vibe.column(this, 12);
        content.setPadding(0, dp(4), 0, dp(40));
        scroll.addView(content);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        split.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
        render();
        View first = rail.findViewWithTag("group:" + group.name());
        if (first != null) first.requestFocus();
        service = new HostService(this, this);
        service.create();
    }

    @Override protected void onResume() { super.onResume(); service.resume(); render(); }
    @Override protected void onPause() { service.pause(); super.onPause(); }
    @Override protected void onDestroy() { service.destroy(); super.onDestroy(); }
    @Override public void onReady(ComputerManagerService.ComputerManagerBinder binder) {}
    @Override public void onHost(ComputerDetails snapshot) {}
    @Override public void onLost() {}

    private void show(SettingsCatalog.Group g) {
        if (g == group && content.getChildCount() > 0) return;
        group = g;
        render();
    }

    private void render() {
        String q = search.getText().toString();
        List<SettingsCatalog.Item> list = q.trim().isEmpty() ? SettingsCatalog.inGroup(items, group) : SettingsCatalog.search(items, q);
        heading.setText(q.trim().isEmpty() ? group.label : "Results for “" + q.trim() + "”");
        content.removeAllViews();
        if (list.isEmpty()) {
            content.addView(Vibe.state(this, "No settings match", "Try a different word, such as HDR, bitrate or controller.", false));
            return;
        }
        for (SettingsCatalog.Item i : list) content.addView(row(i, !q.trim().isEmpty()), new LinearLayout.LayoutParams(-1, -2));
    }

    private View row(final SettingsCatalog.Item item, boolean showGroup) {
        if (item.type == SettingsCatalog.Type.PROFILE) return profileRow(item);
        LinearLayout r = Vibe.row(this, 20);
        r.setPadding(dp(24), dp(16), dp(24), dp(16));
        r.setBackground(Vibe.focusable(this, Vibe.SURFACE, Vibe.RADIUS_SMALL_DP));
        r.setFocusable(true);
        r.setClickable(true);
        r.setMinimumHeight(dp(64));
        LinearLayout text = Vibe.column(this, 4);
        text.addView(Vibe.oneLine(this, item.title, Vibe.BODY, Vibe.TEXT, true));
        text.addView(Vibe.text(this, (showGroup ? item.group.label + " · " : "") + item.summary, Vibe.LABEL, Vibe.TEXT2, false));
        r.addView(text, Vibe.weight(1));
        TextView value = Vibe.oneLine(this, valueOf(item), Vibe.BODY, Vibe.ACCENT2, true);
        r.addView(value);
        r.setOnClickListener(v -> activate(item, value));
        return r;
    }

    private String valueOf(SettingsCatalog.Item i) {
        switch (i.type) {
            case TOGGLE: return prefs.getBoolean(i.key, Boolean.parseBoolean(i.defaultValue)) ? "On" : "Off";
            case CHOICE: {
                String v = prefs.getString(i.key, i.defaultValue);
                int idx = i.values.indexOf(v);
                return idx >= 0 ? i.labels.get(idx) : v + " (custom)";
            }
            case TEXT: {
                String v = prefs.getString(i.key, "");
                return v.isEmpty() ? "Not set" : v;
            }
            default: return "›";
        }
    }

    private View profileRow(SettingsCatalog.Item item) {
        LinearLayout box = Vibe.column(this, 12);
        box.setPadding(dp(24), dp(16), dp(24), dp(16));
        box.setBackground(Vibe.rounded(this, Vibe.SURFACE, 0, Vibe.RADIUS_SMALL_DP, 0));
        int kbps = prefs.getInt(BitrateProfile.KEY_BITRATE, BitrateProfile.BALANCED.kbps);
        box.addView(Vibe.oneLine(this, item.title + " · " + BitrateProfile.describe(kbps), Vibe.BODY, Vibe.TEXT, true));
        box.addView(Vibe.text(this, item.summary + ". Low latency 35, Balanced 50, Best picture 75 Mbps. Resolution, frame rate and HDR are not changed.", Vibe.LABEL, Vibe.TEXT2, false));
        LinearLayout r = Vibe.row(this, 14);
        for (final BitrateProfile p : BitrateProfile.values()) {
            TextView b = Vibe.button(this, p.label, p.kbps == kbps, v -> {
                TvPrefs.applyBitrateOnly(this, p);
                render();
            });
            b.setPadding(dp(12), b.getPaddingTop(), dp(12), b.getPaddingBottom());
            b.setContentDescription(p.label + (p.kbps == kbps ? ", selected" : ""));
            r.addView(b, Vibe.weight(1));
        }
        box.addView(r);
        return box;
    }

    private void activate(final SettingsCatalog.Item item, final TextView value) {
        switch (item.type) {
            case TOGGLE: {
                boolean now = !prefs.getBoolean(item.key, Boolean.parseBoolean(item.defaultValue));
                if (now && "checkbox_enable_hdr".equals(item.key) && !hdr10()) {
                    VibeDialog.show(this, "HDR10 isn't reported", "Android does not report an HDR10 display right now. Check the Shield's display mode, the HDMI cable and the TV's HDR settings. SDR stays on.");
                    return;
                }
                prefs.edit().putBoolean(item.key, now).apply();
                value.setText(now ? "On" : "Off");
                if (SettingsCatalog.KEY_REDUCE_EFFECTS.equals(item.key)) recreate();
                return;
            }
            case CHOICE: { choose(item, value); return; }
            case TEXT: { editText(item, value); return; }
            case ACTION: { action(item.id); return; }
            default:
        }
    }

    private void choose(final SettingsCatalog.Item item, final TextView value) {
        LinearLayout list = Vibe.column(this, 10);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        String current = prefs.getString(item.key, item.defaultValue);
        TextView focus = null;
        for (int k = 0; k < item.values.size(); k++) {
            final String v = item.values.get(k);
            String label = item.labels.get(k) + supportNote(item.key, v);
            TextView b = Vibe.button(this, (v.equals(current) ? "✓  " : "") + label, v.equals(current), x -> {
                prefs.edit().putString(item.key, v).apply();
                value.setText(valueOf(item));
                if (holder[0] != null) holder[0].dismiss();
            });
            list.addView(b, new LinearLayout.LayoutParams(-1, -2));
            if (v.equals(current) || focus == null) focus = b;
        }
        holder[0] = VibeDialog.show(this, item.title, item.summary, list, new VibeDialog.Choice("Cancel", false, null));
        final TextView f = focus;
        if (f != null) f.post(f::requestFocus);
    }

    /** Marks resolution / frame-rate choices this device's decoders don't advertise. */
    private String supportNote(String key, String value) {
        try {
            if ("list_resolution".equals(key)) {
                String[] wh = value.split("x");
                int w = Integer.parseInt(wh[0]), h = Integer.parseInt(wh[1]);
                int fps = Integer.parseInt(prefs.getString("list_fps", "60"));
                return decoderSupports(w, h, fps) ? "" : "  (not advertised at " + fps + " FPS)";
            }
            if ("list_fps".equals(key)) {
                String[] wh = prefs.getString("list_resolution", "1920x1080").split("x");
                return decoderSupports(Integer.parseInt(wh[0]), Integer.parseInt(wh[1]), Integer.parseInt(value)) ? "" : "  (not advertised at this resolution)";
            }
            if ("video_format".equals(key) && "forceav1".equals(value)) return hasDecoder("video/av01") ? "" : "  (no AV1 decoder)";
        } catch (RuntimeException ignored) {
            // Unknown capability: no note rather than a guess.
        }
        return "";
    }

    private static boolean hasDecoder(String mime) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String t : info.getSupportedTypes()) if (t.equalsIgnoreCase(mime)) return true;
        }
        return false;
    }

    private static boolean decoderSupports(int w, int h, int fps) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String t : info.getSupportedTypes()) {
                if (!t.equalsIgnoreCase("video/hevc") && !t.equalsIgnoreCase("video/avc")) continue;
                MediaCodecInfo.VideoCapabilities v = info.getCapabilitiesForType(t).getVideoCapabilities();
                if (v != null && v.areSizeAndRateSupported(w, h, fps)) return true;
            }
        }
        return false;
    }

    private void editText(final SettingsCatalog.Item item, final TextView value) {
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(Vibe.TEXT);
        e.setTextSize(Vibe.BODY);
        e.setText(prefs.getString(item.key, ""));
        e.setBackground(Vibe.focusable(this, Vibe.SURFACE, Vibe.RADIUS_SMALL_DP));
        e.setPadding(dp(20), dp(10), dp(20), dp(10));
        VibeDialog.show(this, item.title, item.summary, e,
                new VibeDialog.Choice("Cancel", false, null),
                new VibeDialog.Choice("Save", true, () -> {
                    String v = e.getText().toString().trim();
                    if (v.length() > 32) v = v.substring(0, 32);
                    prefs.edit().putString(item.key, v).apply();
                    value.setText(valueOf(item));
                }));
    }

    private void action(String id) {
        switch (id) {
            case "classic": startActivity(new Intent(this, TvHomeActivity.class)); break;
            case "pcs": startActivity(new Intent(this, TvPairingActivity.class)); break;
            case "all-settings": startActivity(new Intent(this, StreamSettings.class)); break;
            case "updates": startActivity(new Intent(this, TvUpdateActivity.class)); break;
            case "display-check": VibeDialog.show(this, "Display capabilities", displayReport()); break;
            case "controllers": VibeDialog.show(this, "Connected controllers", controllerReport()); break;
            case "connection-check": connectionCheck(); break;
            case "about": VibeDialog.show(this, "About Vibelight TV", aboutText()); break;
            default:
        }
    }

    private boolean hdr10() {
        if (Build.VERSION.SDK_INT < 24) return false;
        for (int t : getWindowManager().getDefaultDisplay().getHdrCapabilities().getSupportedHdrTypes())
            if (t == Display.HdrCapabilities.HDR_TYPE_HDR10) return true;
        return false;
    }

    private String displayReport() {
        StringBuilder b = new StringBuilder();
        Display d = getWindowManager().getDefaultDisplay();
        Display.Mode m = d.getMode();
        b.append("Output mode: ").append(m.getPhysicalWidth()).append("×").append(m.getPhysicalHeight())
                .append(" at ").append(Math.round(m.getRefreshRate())).append(" Hz\n");
        b.append("HDR10 display: ").append(hdr10() ? "reported" : "not reported").append("\n\n");
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String t : info.getSupportedTypes()) {
                    if (!t.equalsIgnoreCase("video/hevc") && !t.equalsIgnoreCase("video/avc") && !t.equalsIgnoreCase("video/av01")) continue;
                    MediaCodecInfo.VideoCapabilities v = info.getCapabilitiesForType(t).getVideoCapabilities();
                    boolean fourK = v != null && v.areSizeAndRateSupported(3840, 2160, 60);
                    b.append(info.getName()).append(" · ").append(t.substring(6).toUpperCase(Locale.ROOT))
                            .append(" · 4K60 ").append(fourK ? "advertised" : "not advertised")
                            .append(Build.VERSION.SDK_INT >= 29 ? (info.isHardwareAccelerated() ? " · hardware" : " · software") : " · hardware status unknown")
                            .append('\n');
                }
            }
        } catch (RuntimeException e) {
            b.append("Decoder list incomplete: ").append(e.getMessage()).append('\n');
        }
        b.append("\nThese are what Android advertises, not a playback test.");
        return b.toString();
    }

    private String controllerReport() {
        StringBuilder b = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && (d.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) b.append("• ").append(d.getName()).append('\n');
        }
        if (b.length() == 0) b.append("No game controllers are connected. Pair one in Shield Settings → Remotes & Accessories, or use USB.\n");
        b.append("\nDuring a stream: Back on the remote, or Start + Back + LB + RB on a controller, opens the Vibelight panel.");
        return b.toString();
    }

    private String aboutText() {
        return "Version " + BuildConfig.VERSION_NAME + "\nApp ID " + BuildConfig.APPLICATION_ID
                + "\n\nAn unofficial GPLv3 derivative of Moonlight Android v12.1 (commit 1b1b100e). Pairing, protocol, decoding, audio and input come from Moonlight. Not affiliated with Moonlight or NVIDIA."
                + "\n\nSource and notices: github.com/nyteghost/vibelight → android-tv. No accounts, sign-in, cloud services or telemetry uploads.";
    }

    // ------------------------------------------------------------ connection check

    private void connectionCheck() {
        final LinearLayout box = Vibe.column(this, 8);
        final RingView ring = new RingView(this);
        box.addView(ring, new LinearLayout.LayoutParams(dp(44), dp(44)));
        final TextView out = Vibe.text(this, "Running local checks…", Vibe.BODY, Vibe.TEXT2, false);
        box.addView(out);
        VibeDialog.show(this, "Connection check", "Measured on this device now. Values that can't be measured are listed as unknown.", box,
                new VibeDialog.Choice("Close", true, null));
        final List<ComputerDetails> hosts = new ArrayList<>();
        for (ComputerDetails h : service.hosts().values()) hosts.add(new ComputerDetails(h));
        final String uniqueId = service.binder() == null ? null : service.binder().getUniqueId();
        final Context app = getApplicationContext();
        new Thread(() -> {
            final StringBuilder b = new StringBuilder();
            b.append(networkLine(app)).append('\n');
            if (hosts.isEmpty()) b.append("\nNo PCs are known yet, so no PC was tested.\n");
            for (ComputerDetails h : hosts) {
                b.append('\n').append(h.name).append(": ");
                if (h.state != ComputerDetails.State.ONLINE || h.activeAddress == null) { b.append("not answering (state ").append(h.state).append(")\n"); continue; }
                if (uniqueId == null) { b.append("unknown (host service not ready)\n"); continue; }
                long[] samples = new long[3];
                int ok = 0;
                String err = null;
                for (int k = 0; k < samples.length; k++) {
                    try {
                        NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(h), h.httpsPort, uniqueId, h.serverCert, PlatformBinding.getCryptoProvider(app));
                        long t0 = System.nanoTime();
                        http.getServerInfo(true);
                        samples[ok++] = (System.nanoTime() - t0) / 1_000_000;
                    } catch (Exception e) {
                        err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    }
                }
                if (ok == 0) { b.append("request failed: ").append(err).append('\n'); continue; }
                long[] got = Arrays.copyOf(samples, ok);
                Arrays.sort(got);
                b.append("answered ").append(ok).append("/3 requests, median ").append(got[ok / 2]).append(" ms\n")
                        .append("   (an HTTPS request including PC processing time; not streaming latency)\n");
            }
            b.append("\nStreaming RTT, frame loss and decode time are measured only during a stream: turn on the statistics overlay to see them.");
            runOnUiThread(() -> {
                if (isFinishing()) return;
                ring.setMode(RingView.Mode.DONE);
                out.setTextColor(Vibe.TEXT);
                out.setText(b.toString());
                TextView ports = Vibe.button(this, "Test internet ports (contacts Moonlight's test server)", false, v -> portTest(out));
                box.addView(ports);
            });
        }).start();
    }

    private static String networkLine(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "Network: unknown (no active network reported)";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Network: Ethernet (link speed: unknown — Android doesn't report it)";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            if (info == null) return "Network: Wi-Fi (link speed: unknown)";
            return "Network: Wi-Fi · link " + (info.getLinkSpeed() > 0 ? info.getLinkSpeed() + " Mbps" : "unknown")
                    + " · signal " + (info.getRssi() > -127 ? info.getRssi() + " dBm" : "unknown")
                    + (Build.VERSION.SDK_INT >= 21 ? " · " + (info.getFrequency() > 0 ? info.getFrequency() + " MHz" : "band unknown") : "");
        }
        return "Network: other transport";
    }

    private void portTest(final TextView out) {
        out.append("\n\nTesting ports…");
        new Thread(() -> {
            int r = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, MoonBridge.ML_PORT_FLAG_ALL);
            final String text = r == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE ? "Port test inconclusive (the test server couldn't be reached)."
                    : r == 0 ? "No streaming ports are blocked on the path to the internet test server."
                    : "Blocked on the path to the test server:\n" + MoonBridge.stringifyPortFlags(r, "\n");
            runOnUiThread(() -> { if (!isFinishing()) out.append("\n" + text); });
        }).start();
    }
}
