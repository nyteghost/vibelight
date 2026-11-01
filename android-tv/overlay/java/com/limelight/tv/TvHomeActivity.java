package com.limelight.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.limelight.AppView;
import com.limelight.BuildConfig;
import com.limelight.PcView;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.*;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.StreamSettings;
import com.limelight.utils.ServerHelper;
import java.util.*;
import java.util.concurrent.*;

/** Vibelight's TV launcher. The pinned Android core owns every streaming and pairing operation. */
public final class TvHomeActivity extends Activity {
    private static final int BG = Color.rgb(6,9,24), PANEL = Color.rgb(13,20,36),
        TEXT = Color.rgb(226,232,240), MUTED = Color.rgb(148,163,184), ACCENT = Color.rgb(165,180,252);
    private final Map<String,ComputerDetails> hosts = new TreeMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ComputerManagerService.ComputerManagerBinder binder;
    private boolean bound, foreground, polling;
    private long generation;
    private LinearLayout content, shell;
    private String page = "Home", signature = "";
    private TextView serviceStatus;
    private ComputerDetails libraryHost;
    private final ServiceConnection service = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder value) {
            final ComputerManagerService.ComputerManagerBinder connected = (ComputerManagerService.ComputerManagerBinder)value;
            io.execute(() -> {
                connected.waitForReady();
                if (Thread.currentThread().isInterrupted()) return;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !bound) return;
                    binder = connected; startPolling();
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null; polling = false;
            if (serviceStatus != null) serviceStatus.setText("Host service disconnected. Reopen the app to retry.");
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TvPreferences.initialize(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        shell = new LinearLayout(this); shell.setBackgroundColor(BG); setContentView(shell);
        LinearLayout rail = new LinearLayout(this); rail.setOrientation(LinearLayout.VERTICAL); rail.setPadding(dp(20),dp(28),dp(16),dp(20));
        rail.setBackgroundColor(PANEL); shell.addView(rail,new LinearLayout.LayoutParams(dp(190),-1));
        rail.addView(label("Vibelight TV",24,TEXT,true)); rail.addView(label("SHIELD · BETA",12,ACCENT,true));
        for (String name : new String[]{"Home","Recent","Profiles","Performance","Controllers","Diagnostics","Settings","About"}) {
            Button button = action(name, () -> show(name));
            rail.addView(button,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); shell.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(32),dp(28),dp(32),dp(28)); scroll.addView(content);
        show("Home");
        bound = bindService(new Intent(this,ComputerManagerService.class),service,BIND_AUTO_CREATE);
        if (!bound) serviceStatus.setText("Host service unavailable. Reopen Vibelight TV to retry.");
    }
    @Override protected void onResume() { super.onResume(); foreground = true; startPolling(); if (!page.equals("Library")) show(page); }
    @Override protected void onPause() {
        foreground = false; generation++;
        if (binder != null && polling) { binder.stopPolling(); polling = false; }
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (bound) { unbindService(service); bound=false; }
        io.shutdownNow(); super.onDestroy();
    }
    private void startPolling() {
        if (!foreground || binder == null || polling) return;
        polling = true;
        binder.startPolling(details -> {
            ComputerDetails snapshot = new ComputerDetails(details);
            runOnUiThread(() -> {
                if (!foreground || !polling || snapshot.uuid == null) return;
                hosts.put(snapshot.uuid,snapshot);
                String next = hostsSignature();
                if (!next.equals(signature)) {
                    signature = next;
                    if (page.equals("Home") || page.equals("Recent")) show(page);
                }
            });
        });
    }
    private String hostsSignature() {
        StringBuilder s = new StringBuilder();
        for (ComputerDetails h:hosts.values()) s.append(h.uuid).append(h.name).append(h.state).append(h.pairState).append(h.runningGameId);
        return s.toString();
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(4),dp(4),dp(8));
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private GradientDrawable shape(int fill,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(12));d.setStroke(dp(2),stroke);return d;
    }
    private Button action(String text,Runnable run) {
        Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setTextColor(TEXT);b.setAllCaps(false);
        b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setMinHeight(dp(52));b.setFocusable(true);b.setTag(text);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(38,48,83),ACCENT));
        background.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(38,48,83),ACCENT));
        background.addState(new int[]{},shape(PANEL,Color.rgb(38,51,77)));b.setBackground(background);
        b.setOnClickListener(v->run.run());return b;
    }
    private void button(String text,Runnable run) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));content.addView(action(text,run),lp);
    }
    private void note(String text) { content.addView(label(text,16,MUTED,false)); }
    private void heading(String text) { content.addView(label(text,24,TEXT,true)); }
    private void message(String title,String text) { new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK",null).show(); }
    private void show(String name) {
        Object focused=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        generation++; page=name; content.removeAllViews();
        serviceStatus=label("VIBELIGHT TV  ·  "+BuildConfig.VERSION_NAME,12,ACCENT,true);content.addView(serviceStatus);
        switch(name) {
            case "Home": case "Recent": home(name.equals("Recent")); break;
            case "Profiles": profiles(); break;
            case "Performance": performance(); break;
            case "Controllers": controllers(); break;
            case "Diagnostics": diagnostics(); break;
            case "Settings": settings(); break;
            case "About": about(); break;
        }
        if(focused!=null) { View restore=shell.findViewWithTag(focused);if(restore!=null)restore.requestFocus(); }
    }
    private void home(boolean recent) {
        heading(recent?"Recent launches":"Play on the big screen");
        note("Your PCs, games and desktop. Wired 4K60 is the starting target; actual performance depends on the full streaming path.");
        button("Add PC by IP address",()->startActivity(new Intent(this,AddComputerManually.class)));
        button("Pair & manage PCs",()->startActivity(new Intent(this,PcView.class)));
        ArrayList<ComputerDetails> list=new ArrayList<>(hosts.values());
        list.sort((a,b)->Long.compare(TvPreferences.prefs(this).getLong("vibe_recent_"+b.uuid,0),TvPreferences.prefs(this).getLong("vibe_recent_"+a.uuid,0)));
        int shown=0;
        for(ComputerDetails h:list) {
            if(recent && !TvPreferences.prefs(this).contains("vibe_recent_"+h.uuid))continue;
            shown++;
            String state=h.state==ComputerDetails.State.ONLINE?"Online":h.state==ComputerDetails.State.OFFLINE?"Offline":"Checking";
            button(h.name+"\n"+state+" · "+(h.pairState==PairingManager.PairState.PAIRED?"Paired":"Pairing required"),()->openHost(h));
        }
        if(shown==0) note(recent?"No recent launches yet.":"Searching your local network. If discovery finds nothing, add your PC's LAN IP. Pairing stays local to this app.");
        note("Select a paired PC to open its game tiles. Back returns here. Profiles apply only to the next new stream.");
    }
    private void openHost(ComputerDetails h) {
        if(binder==null) { message("Still connecting","Wait for the host service, then select your PC again.");return; }
        if(h.state!=ComputerDetails.State.ONLINE) { message("PC unavailable","Check the PC is awake, Vibebeam is running, and both devices can reach the same LAN. No host setting was changed.");return; }
        if(h.pairState!=PairingManager.PairState.PAIRED || h.serverCert==null) {
            message("Pair this PC","Open Pair & manage PCs, select this PC, and enter its PIN in Vibebeam's host web interface. Vibelight retains the paired certificate.");return;
        }
        final String uniqueId=binder.getUniqueId();
        libraryHost=h;page="Library";generation++;long request=generation;
        content.removeAllViews();heading(h.name);note("Loading your host's applications securely…");
        io.execute(()->{
            try {
                List<NvApp> apps=new NvHTTP(ServerHelper.getCurrentAddressFromComputer(h),h.httpsPort,uniqueId,h.serverCert,PlatformBinding.getCryptoProvider(this)).getAppList();
                runOnUiThread(()->{
                    if(!foreground || request!=generation)return;
                    content.removeAllViews();heading(h.name);note("Choose a game or the host's configured Desktop entry.");
                    button("Full library & session controls",()->openFullLibrary(h));
                    GridLayout grid=new GridLayout(this);grid.setColumnCount(2);content.addView(grid,new LinearLayout.LayoutParams(-1,-2));
                    for(NvApp app:apps) {
                        Button tile=action(app.getAppName()+"\n"+(app.isHdrSupported()?"HDR capable":"Stream from PC"),()->launch(h,app));
                        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(122);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(tile,lp);
                    }
                    if(apps.isEmpty())note("No applications were returned. Configure a Desktop or game entry in Vibebeam.");
                    button("Back to PCs",()->show("Home"));
                    if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();
                });
            } catch(Exception error) { runOnUiThread(()->{if(!foreground||request!=generation)return;content.removeAllViews();heading("Could not load applications");note("Check the host, pairing permissions and LAN connection. Host 2FA extensions are not implemented in this Android core.");button("Try again",()->openHost(h));button("Pair & manage PCs",()->startActivity(new Intent(this,PcView.class)));button("Back to PCs",()->show("Home"));}); }
        });
    }
    private void openFullLibrary(ComputerDetails h) {
        Intent i=new Intent(this,AppView.class);i.putExtra(AppView.NAME_EXTRA,h.name);i.putExtra(AppView.UUID_EXTRA,h.uuid);startActivity(i);
    }
    private void launch(ComputerDetails h,NvApp app) {
        ComputerDetails current=binder==null?null:binder.getComputer(h.uuid);
        if(current==null || current.state!=ComputerDetails.State.ONLINE || current.pairState!=PairingManager.PairState.PAIRED || current.serverCert==null) {message("Connection changed","Return to Home and check the PC and pairing before trying again.");return;}
        if(current.runningGameId!=0 && current.runningGameId!=app.getAppId()) {message("Another app is running","Use Full library & session controls to resume it or explicitly quit it before launching another game.");return;}
        TvPreferences.prefs(this).edit().putLong("vibe_recent_"+h.uuid,System.currentTimeMillis()).apply();
        ServerHelper.doStart(this,app,current,binder);
    }
    private void profiles() {
        heading("Streaming profiles");
        note("Current: "+TvPreferences.prefs(this).getString("vibe_tv_profile","Custom")+" · "+TvPreferences.prefs(this).getInt("seekbar_bitrate_kbps",50000)/1000+" Mbps. Advanced edits can override this label.");
        for(TvPolicy.Profile p:TvPolicy.Profile.values())button(p.label+" · 4K60 · "+p.kbps/1000+" Mbps",()->new AlertDialog.Builder(this).setTitle("Use "+p.label+"?").setMessage("Sets 3840×2160, 60 FPS, "+p.kbps/1000+" Mbps, automatic HEVC/H.264 selection, "+p.pacing+" frame pacing and SDR. Applies to the next stream. Other advanced settings stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Use profile",(d,w)->{TvPreferences.profile(this,p);show("Profiles");}).show());
        note("HEVC is selected by the core when compatible; H.264 remains the fallback. Best Picture does not automatically enable HDR.");
        button("HDR10 request: "+(TvPreferences.prefs(this).getBoolean("checkbox_enable_hdr",false)?"On":"Off"),()->{
            if(!hdr10()) {message("HDR10 unavailable","Android does not report an HDR10 display. Check Shield output mode, HDMI path and TV settings. SDR remains available.");return;}
            boolean enabled=TvPreferences.prefs(this).getBoolean("checkbox_enable_hdr",false);
            new AlertDialog.Builder(this).setTitle(enabled?"Use SDR?":"Request HDR10?").setMessage("HDR also requires a compatible HEVC Main10 decoder, host capture/encoder and HDR application. Display support alone does not prove end-to-end HDR.").setNegativeButton("Cancel",null).setPositiveButton("Apply next stream",(d,w)->{TvPreferences.prefs(this).edit().putBoolean("checkbox_enable_hdr",!enabled).apply();show("Profiles");}).show();
        });
        button("Advanced streaming settings",()->startActivity(new Intent(this,StreamSettings.class)));
    }
    private void performance() {
        heading("Performance & Smart Local");
        boolean smart=TvPreferences.prefs(this).getBoolean("vibe_smart_local",true);
        note("Smart Local: "+(smart?"On":"Off")+". Offline rules only; no model, cloud API, subscription or GPU inference.");
        button(smart?"Turn Smart Local off":"Turn Smart Local on",()->{TvPreferences.prefs(this).edit().putBoolean("vibe_smart_local",!smart).apply();TvTelemetry.reset();show("Performance");});
        if(smart) {
            note(TvTelemetry.guidance());
            if(TvTelemetry.canReduce()&&!TvPreferences.prefs(this).contains("vibe_undo_bitrate"))button("Apply 20% less bitrate next stream",()->{
                new AlertDialog.Builder(this).setTitle("Reduce next-stream bitrate?").setMessage("Trades some picture detail for lower bandwidth. This changes the global TV bitrate only; the active stream and host stay unchanged. Undo is available here. Automatic health rollback is not implemented.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{if(!TvTelemetry.canReduce()||!TvPreferences.reduce(this))message("Not applied","Evidence expired, bitrate is already at its limit, or preferences could not be saved.");show("Performance");}).show();
            });
        }
        if(TvPreferences.prefs(this).contains("vibe_undo_bitrate"))button("Undo bitrate recommendation",()->{if(!TvPreferences.undo(this))message("Undo not applied","The bitrate was edited since Apply, or preferences could not be saved. Restore it manually in Advanced settings.");show("Performance");});
        button("Performance overlay: "+(TvPreferences.prefs(this).getBoolean("checkbox_enable_perf_overlay",false)?"On":"Off"),()->{boolean old=TvPreferences.prefs(this).getBoolean("checkbox_enable_perf_overlay",false);TvPreferences.prefs(this).edit().putBoolean("checkbox_enable_perf_overlay",!old).apply();show("Performance");});
        note("Overlay: received/rendered FPS, decoder name, frame loss, RTT and RTT variance, host processing time when reported, and decode time. Frame loss is not packet loss. RTT variance is not one-way jitter. Actual bitrate and frame-time pacing graphs are not instrumented. Samples are memory-only and expire after five minutes.");
        note("Automatic in-stream bitrate changes and health-triggered rollback are future work. This beta uses explicit next-stream adjustments only.");
    }
    private void controllers() {
        heading("Controllers & remote");note("D-pad / left stick: move focus. A / Cross / Select: open. Back / B / Circle: return. Hold Back during a stream for the core's disconnect flow. Keyboard: Ctrl+Alt+Shift+Q disconnects. Verify these on your controller before a long session.");
        note("Pair Xbox/PlayStation controllers in Shield Settings → Remotes & Accessories, or use USB. Basic controller input is inherited; rumble, touchpad and motion support vary by controller and Android firmware.");
        for(int id:InputDevice.getDeviceIds()) {InputDevice d=InputDevice.getDevice(id);if(d!=null&&(d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD)note("Detected: "+d.getName());}
        button("Advanced controller & keyboard settings",()->startActivity(new Intent(this,StreamSettings.class)));
    }
    private boolean hdr10() {
        if(Build.VERSION.SDK_INT<24)return false;
        for(int type:getWindowManager().getDefaultDisplay().getHdrCapabilities().getSupportedHdrTypes())if(type==Display.HdrCapabilities.HDR_TYPE_HDR10)return true;return false;
    }
    private void diagnostics() {
        heading("Local device checks");
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        note("Network transport: "+(caps==null?"Unknown":caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":"Other")+". Transport type does not measure speed, latency or packet loss.");
        note("Connected display HDR10: "+(hdr10()?"Reported":"Not reported"));
        try {for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(codec.isEncoder())continue;
            for(String type:codec.getSupportedTypes())if(type.equalsIgnoreCase("video/hevc")||type.equalsIgnoreCase("video/avc")) {
                boolean fourk=codec.getCapabilitiesForType(type).getVideoCapabilities().areSizeAndRateSupported(3840,2160,60);
                note(codec.getName()+" · "+type+" · 4K60 "+(fourk?"advertised":"not advertised")+(Build.VERSION.SDK_INT>=29?" · "+(codec.isHardwareAccelerated()?"hardware":"software"):" · hardware status unknown"));
            }
        }}catch(RuntimeException e){note("Decoder enumeration incomplete. Try Advanced settings or a different codec.");}
        note("These are advertised capabilities, not a playback test. PyroWave is unavailable. No synthetic latency values or background network probes are shown.");
        button("Refresh local checks",()->show("Diagnostics"));
    }
    private void settings() {heading("Settings");button("Streaming profiles",()->show("Profiles"));button("Advanced streaming, audio & input",()->startActivity(new Intent(this,StreamSettings.class)));button("Pair & manage PCs",()->startActivity(new Intent(this,PcView.class)));note("Updates are downloaded as beta APKs from this repository's Android TV Actions run. Install over the existing app only when the signing certificate matches. No automatic downloads or host changes.");}
    private void about() {heading("Vibelight TV Beta");note("Independent Android TV client for NVIDIA Shield TV Pro. Application ID: "+BuildConfig.APPLICATION_ID+"\nBuild: "+BuildConfig.VERSION_NAME);note("Unofficial GPLv3 Moonlight Android derivative. Protocol, pairing, MediaCodec, audio and input foundation: Moonlight Android v12.1, commit 1b1b100e63e8. Not affiliated with Moonlight or NVIDIA.");note("Source, notices and Shield guide: github.com/nyteghost/vibelight → beta/next-features-20261028 → android-tv. Windows and Vibebeam host installations are independent.");note("Physical Shield playback, 4K60/HDR, controllers, reconnect/cleanup and latency comparison remain manual release gates. No performance improvement over Moonlight is claimed.");}
    @Override public void onBackPressed() {if(!page.equals("Home"))show("Home");else super.onBackPressed();}
}
