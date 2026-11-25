package com.limelight.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.tv.logic.Library;
import com.limelight.tv.logic.RecentGames;
import com.limelight.tv.ui.ArtworkLoader;
import com.limelight.tv.ui.Vibe;
import com.limelight.tv.ui.VibeDialog;
import com.limelight.utils.ServerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Screen 3 — game library: cinematic spotlight, recent row, wide banners, search and sort. */
public final class TvLibraryActivity extends Activity implements HostService.Listener {
    public static final String EXTRA_HOST = "vibe_host_uuid";
    public static final String EXTRA_RESUME = "vibe_resume";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private HostService service;
    private String uuid;
    private ComputerDetails host;
    private final Map<Integer, NvApp> apps = new HashMap<>();
    private final List<Library.Game> games = new ArrayList<>();
    private Library.Sort sort = Library.Sort.NAME;
    private String query = "";
    private long loadGeneration;
    private boolean loaded, autoResumeDone;

    private ImageView backdrop, spotArt;
    private TextView spotTitle, spotMeta, hostLine, sortButton;
    private LinearLayout spotButtons, rows;
    private EditText search;
    private NvApp spotlight;

    private int dp(float v) { return Vibe.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Vibe.applyWindow(this);
        uuid = getIntent().getStringExtra(EXTRA_HOST);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Vibe.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.45f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Vibe.BG, 0xD9060918, 0x99060918}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = Vibe.column(this, 18);
        page.setPadding(dp(48), dp(28), dp(48), dp(0));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Vibe.row(this, 16);
        top.addView(Vibe.brand(this));
        hostLine = Vibe.oneLine(this, "", Vibe.BODY, Vibe.TEXT2, false);
        top.addView(hostLine, Vibe.weight(1));
        search = new EditText(this);
        search.setHint("Search games");
        search.setHintTextColor(Vibe.TEXT2);
        search.setTextColor(Vibe.TEXT);
        search.setTextSize(Vibe.BODY);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(20), dp(8), dp(20), dp(8));
        search.setBackground(Vibe.focusable(this, Vibe.SURFACE, 999));
        search.setMinHeight(dp(56));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { query = e.toString(); renderRows(); }
        });
        top.addView(search, new LinearLayout.LayoutParams(dp(260), -2));
        sortButton = Vibe.button(this, "Sort: A–Z", false, v -> {
            sort = sort == Library.Sort.NAME ? Library.Sort.RECENT : Library.Sort.NAME;
            sortButton.setText(sort == Library.Sort.NAME ? "Sort: A–Z" : "Sort: Recent");
            renderRows();
        });
        top.addView(sortButton);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout spot = Vibe.row(this, 32);
        LinearLayout text = Vibe.column(this, 12);
        spotTitle = Vibe.text(this, "", Vibe.DISPLAY, Vibe.TEXT, true);
        spotTitle.setMaxLines(2);
        text.addView(spotTitle);
        spotMeta = Vibe.oneLine(this, "", Vibe.BODY, Vibe.TEXT2, false);
        text.addView(spotMeta);
        spotButtons = Vibe.row(this, 16);
        text.addView(spotButtons);
        spot.addView(text, Vibe.weight(1));
        spotArt = new ImageView(this);
        spotArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        spotArt.setClipToOutline(true);
        spotArt.setBackground(Vibe.rounded(this, Vibe.SURFACE, Vibe.OUTLINE, Vibe.RADIUS_DP, 1));
        spot.addView(spotArt, new LinearLayout.LayoutParams(dp(150), dp(200)));
        page.addView(spot, new LinearLayout.LayoutParams(-1, dp(210)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = Vibe.column(this, 14);
        rows.setPadding(0, dp(4), 0, dp(32));
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        rows.addView(loading());
        service = new HostService(this, this);
        service.create();
    }

    private View loading() {
        View v = Vibe.state(this, "Loading your games…", "Asking your PC for its game list over its paired connection.", false);
        Vibe.pulse(v);
        return v;
    }

    @Override protected void onResume() { super.onResume(); service.resume(); if (loaded) renderRows(); }
    @Override protected void onPause() { service.pause(); super.onPause(); }
    @Override protected void onDestroy() { loadGeneration++; service.destroy(); io.shutdownNow(); super.onDestroy(); }

    @Override public void onReady(ComputerManagerService.ComputerManagerBinder binder) {
        host = service.fresh(uuid);
        if (host == null) { showProblem("This PC is no longer known", "Return to the PC picker and choose it again."); return; }
        hostLine.setText("·  " + host.name);
        load();
    }

    @Override public void onLost() { showProblem("Host service stopped", "Reopen Vibelight to retry."); }

    @Override public void onHost(ComputerDetails snapshot) {
        if (!snapshot.uuid.equals(uuid)) return;
        int before = host == null ? 0 : host.runningGameId;
        host = snapshot;
        if (loaded && before != snapshot.runningGameId) { renderSpotlight(spotlight); }
    }

    private void showProblem(String title, String detail) {
        rows.removeAllViews();
        rows.addView(Vibe.state(this, title, detail, true));
        TextView back = Vibe.button(this, "Back to PCs", true, v -> finish());
        rows.addView(back, Vibe.wrap());
        back.requestFocus();
    }

    private void load() {
        if (host.state != ComputerDetails.State.ONLINE && host.state != ComputerDetails.State.UNKNOWN) {
            showProblem(host.name + " is offline", "Wake it from the PC picker, or check that it is on and Vibebeam is running."); return;
        }
        if (host.pairState != PairingManager.PairState.PAIRED || host.serverCert == null) {
            rows.removeAllViews();
            rows.addView(Vibe.state(this, "Pair " + host.name + " first", "Pairing uses a four-digit PIN and happens once.", false));
            TextView pair = Vibe.button(this, "Pair now", true, v -> { startActivity(new Intent(this, TvPairActivity.class).putExtra(TvPairActivity.EXTRA_HOST, uuid)); finish(); });
            rows.addView(pair, Vibe.wrap());
            pair.requestFocus();
            return;
        }
        final ComputerDetails h = new ComputerDetails(host);
        final String uniqueId = service.binder().getUniqueId();
        final long gen = ++loadGeneration;
        io.execute(() -> {
            try {
                final List<NvApp> list = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(h), h.httpsPort, uniqueId, h.serverCert,
                        PlatformBinding.getCryptoProvider(this)).getAppList();
                runOnUiThread(() -> {
                    if (gen != loadGeneration || isFinishing()) return;
                    apps.clear(); games.clear();
                    for (NvApp a : list) { apps.put(a.getAppId(), a); games.add(new Library.Game(a.getAppId(), a.getAppName(), a.isHdrSupported())); }
                    loaded = true;
                    renderRows();
                    maybeAutoResume();
                });
            } catch (Exception e) {
                final String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (gen != loadGeneration || isFinishing()) return;
                    rows.removeAllViews();
                    rows.addView(Vibe.state(this, "Couldn't load games", "Reason reported: " + why + "\nIf the PC asks for a two-factor code, that extension isn't supported on Android TV yet.", true));
                    LinearLayout r = Vibe.row(this, 16);
                    TextView retry = Vibe.button(this, "Try again", true, v -> { rows.removeAllViews(); rows.addView(loading()); load(); });
                    r.addView(retry);
                    r.addView(Vibe.button(this, "Back to PCs", false, v -> finish()));
                    rows.addView(r);
                    retry.requestFocus();
                });
            }
        });
    }

    private void maybeAutoResume() {
        if (autoResumeDone || !getIntent().getBooleanExtra(EXTRA_RESUME, false)) return;
        autoResumeDone = true;
        NvApp running = host == null ? null : apps.get(host.runningGameId);
        if (running != null) launch(running);
    }

    private void renderRows() {
        if (!loaded) return;
        View keep = getCurrentFocus();
        Object keepTag = keep == null ? null : keep.getTag();
        rows.removeAllViews();
        firstCard = null;
        if (games.isEmpty()) {
            rows.addView(Vibe.state(this, "No games yet", "Add a game or the Desktop entry in Vibebeam's web UI, then come back.", false));
            return;
        }
        List<RecentGames.Entry> recent = RecentGames.forHost(TvPrefs.recent(this), uuid);
        if (query.trim().isEmpty() && sort == Library.Sort.NAME && !recent.isEmpty()) {
            List<Library.Game> recentGames = new ArrayList<>();
            for (RecentGames.Entry e : recent) {
                NvApp a = apps.get(e.appId);
                if (a != null) recentGames.add(new Library.Game(a.getAppId(), a.getAppName(), a.isHdrSupported()));
            }
            if (!recentGames.isEmpty()) addRow("Recent", recentGames, "recent");
        }
        List<Library.Game> view = Library.view(games, query, sort, recent);
        if (view.isEmpty()) {
            rows.addView(Vibe.state(this, "No matches", "Nothing in this library matches “" + query.trim() + "”.", false));
        } else {
            addRow(query.trim().isEmpty() ? "All games" : "Results", view, "all");
        }
        View restore = keepTag == null ? null : rows.findViewWithTag(keepTag);
        if (restore != null) restore.requestFocus();
        else if (!search.hasFocus() && firstCard != null) firstCard.requestFocus();
    }

    private void addRow(String title, List<Library.Game> list, String rowKey) {
        rows.addView(Vibe.oneLine(this, title, Vibe.H2, Vibe.TEXT, true));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(6), dp(10), dp(6), dp(10));
        LinearLayout r = Vibe.row(this, 20);
        hs.addView(r);
        for (Library.Game g : list) {
            NvApp app = apps.get(g.id);
            if (app == null) continue;
            View card = banner(app, rowKey);
            if (firstCard == null) firstCard = card;
            r.addView(card, new LinearLayout.LayoutParams(dp(240), dp(135)));
        }
        rows.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    private View firstCard;

    private View banner(final NvApp app, String rowKey) {
        final FrameLayout card = new FrameLayout(this);
        card.setTag(rowKey + ":" + app.getAppId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setForeground(Vibe.focusable(this, 0, Vibe.RADIUS_SMALL_DP));
        card.setBackground(Vibe.rounded(this, Vibe.SURFACE, 0, Vibe.RADIUS_SMALL_DP, 0));
        card.setClipToOutline(true);
        final ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0.35f);
        card.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        final ImageView fg = new ImageView(this);
        fg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(fg, new FrameLayout.LayoutParams(-1, -1));
        final TextView name = Vibe.oneLine(this, app.getAppName(), Vibe.LABEL, Vibe.TEXT, true);
        name.setPadding(dp(14), dp(22), dp(14), dp(10));
        name.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6060918, 0x00060918}));
        card.addView(name, Vibe.frame(-1, -2, Gravity.BOTTOM));
        if (app.isHdrSupported()) {
            TextView hdr = Vibe.oneLine(this, "HDR", Vibe.LABEL, Vibe.BG, true);
            hdr.setPadding(dp(10), dp(2), dp(10), dp(2));
            hdr.setBackground(Vibe.rounded(this, Vibe.ACCENT2, 0, 999, 0));
            FrameLayout.LayoutParams lp = Vibe.frame(-2, -2, Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(8), dp(8), 0);
            card.addView(hdr, lp);
        }
        if (host != null && host.runningGameId == app.getAppId()) {
            TextView run = Vibe.oneLine(this, "Running", Vibe.LABEL, Vibe.BG, true);
            run.setPadding(dp(10), dp(2), dp(10), dp(2));
            run.setBackground(Vibe.rounded(this, Vibe.ACCENT, 0, 999, 0));
            FrameLayout.LayoutParams lp = Vibe.frame(-2, -2, Gravity.TOP | Gravity.START);
            lp.setMargins(dp(8), dp(8), 0, 0);
            card.addView(run, lp);
        }
        // Clean text fallback until (or unless) host art arrives.
        name.setGravity(Gravity.START);
        ArtworkLoader.get(this).load(this, fg, host, service.binder().getUniqueId(), app, bmp -> {
            if (bmp != null) bg.setImageBitmap(bmp);
            if (bmp != null && spotlight == app) renderSpotlight(app);
        });
        card.setOnClickListener(v -> launch(app));
        Vibe.focusMotion(card, 1.06f);
        final View.OnFocusChangeListener motion = card.getOnFocusChangeListener();
        card.setOnFocusChangeListener((v, has) -> {
            if (motion != null) motion.onFocusChange(v, has);
            if (has) renderSpotlight(app);
        });
        return card;
    }

    private void renderSpotlight(final NvApp app) {
        spotlight = app;
        spotButtons.removeAllViews();
        if (app == null) return;
        spotTitle.setText(app.getAppName());
        boolean running = host != null && host.runningGameId == app.getAppId();
        StringBuilder meta = new StringBuilder();
        if (running) meta.append("Running now");
        if (app.isHdrSupported()) meta.append(meta.length() > 0 ? "  ·  " : "").append("HDR capable (reported by your PC)");
        for (RecentGames.Entry e : RecentGames.forHost(TvPrefs.recent(this), uuid)) {
            if (e.appId == app.getAppId()) {
                meta.append(meta.length() > 0 ? "  ·  " : "").append("Last played ")
                        .append(android.text.format.DateUtils.getRelativeTimeSpanString(e.startedAt));
                break;
            }
        }
        spotMeta.setText(meta.length() == 0 ? (host == null ? "" : "On " + host.name) : meta);
        spotButtons.addView(Vibe.button(this, running ? "Resume" : "Play", true, v -> launch(app)));
        if (running) spotButtons.addView(Vibe.button(this, "Quit game…", false, v -> confirmQuit(app)));
        Bitmap art = host == null ? null : ArtworkLoader.get(this).cached(host.uuid, app.getAppId());
        spotArt.setImageBitmap(art);
        // A 24 px wide copy scaled up with filtering gives a cheap, static soft backdrop.
        if (art != null) backdrop.setImageBitmap(Bitmap.createScaledBitmap(art, 24, Math.max(1, 24 * art.getHeight() / Math.max(1, art.getWidth())), true));
        else backdrop.setImageDrawable(null);
    }

    private void launch(final NvApp app) {
        ComputerDetails fresh = service.fresh(uuid);
        if (fresh == null || fresh.state != ComputerDetails.State.ONLINE || fresh.pairState != PairingManager.PairState.PAIRED || fresh.serverCert == null) {
            VibeDialog.show(this, "Connection changed", "Your PC's status changed. Return to the PC picker and check it is online and paired.",
                    new VibeDialog.Choice("Back to PCs", true, this::finish), new VibeDialog.Choice("Stay", false, null));
            return;
        }
        if (fresh.runningGameId != 0 && fresh.runningGameId != app.getAppId()) {
            final NvApp running = apps.get(fresh.runningGameId);
            final String runningName = running == null ? "Another game" : running.getAppName();
            final ComputerDetails h = fresh;
            VibeDialog.show(this, runningName + " is running", "Quit it first to start " + app.getAppName() + ". Unsaved progress in " + runningName + " may be lost.",
                    new VibeDialog.Choice("Cancel", false, null),
                    running == null ? new VibeDialog.Choice("Back", false, null) : new VibeDialog.Choice("Resume " + runningName, false, () -> start(running, h)),
                    new VibeDialog.Choice("Quit and play", true, () -> quit(running == null ? app : running, h, () -> runOnUiThread(() -> {
                        ComputerDetails after = service.fresh(uuid);
                        if (after != null) start(app, after);
                    }))));
            return;
        }
        start(app, fresh);
    }

    private void start(NvApp app, ComputerDetails h) {
        if (service.binder() == null) return;
        ServerHelper.doStart(this, app, h, service.binder());
    }

    private void confirmQuit(final NvApp app) {
        final ComputerDetails fresh = service.fresh(uuid);
        if (fresh == null) return;
        VibeDialog.show(this, "Quit " + app.getAppName() + "?", "The game closes on " + fresh.name + ". Unsaved progress may be lost.",
                new VibeDialog.Choice("Keep it running", false, null),
                new VibeDialog.Choice("Quit game", true, () -> quit(app, fresh, () -> runOnUiThread(() -> {
                    if (service.binder() != null) service.binder().invalidateStateForComputer(uuid);
                }))));
    }

    private void quit(NvApp app, ComputerDetails h, Runnable done) {
        if (service.binder() == null) return;
        ServerHelper.doQuit(this, h, app, service.binder(), done);
    }
}
