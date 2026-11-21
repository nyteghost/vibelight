package com.limelight.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.limelight.BuildConfig;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.*;

/** Foreground, opt-in update flow. Android owns final installation and user approval. */
public final class TvUpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TvUpdateClient client;
    private Future<?> task;
    private long generation;
    private boolean busy;
    private TvUpdateClient.Candidate candidate;
    private File downloaded;
    private TextView status;
    private ProgressBar progress;
    private Button check,download,install,permission,cancel;
    private boolean compatible(){return Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(12));d.setStroke(dp(2),stroke);return d;}
    private Button button(LinearLayout layout,String text,Runnable action) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(18);b.setTextColor(Color.rgb(226,232,240));b.setPadding(dp(20),dp(12),dp(20),dp(12));b.setFocusable(true);
        StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_focused},shape(0xff263053,0xffa5b4fc));bg.addState(new int[]{},shape(0xff0d1424,0xff26334d));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));layout.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void text(LinearLayout layout,String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(12));layout.addView(v);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff060918);setContentView(scroll);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(48),dp(24),dp(48),dp(24));scroll.addView(body);
        text(body,"Beta updates",28,0xffe2e8f0);text(body,"Installed: "+BuildConfig.VERSION_NAME,16,0xffa5b4fc);
        text(body,"Check when you choose. Download a verified Shield beta, then approve installation in Android. Your pairing and settings stay with the existing app.",18,0xff94a3b8);
        status=new TextView(this);status.setTextSize(18);status.setTextColor(0xffe2e8f0);status.setPadding(0,dp(12),0,dp(12));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        check=button(body,"Check for beta updates",this::checkLatest);
        download=button(body,"Download beta update",this::confirmDownload);
        install=button(body,"Install downloaded update",this::installDownloaded);
        permission=button(body,"Allow Vibelight TV to install updates",()->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){status.setText("Open Shield Settings → Apps → Special app access → Install unknown apps and allow Vibelight TV Beta.");}});
        cancel=button(body,"Cancel download / check",this::cancelTask);
        button(body,"Back to Vibelight TV",this::finish);
        controls();check.requestFocus();
        if(!compatible()){status.setText("In-app downloads target the Shield arm64 build. This emulator build can test the interface and installer validation only.");return;}
        // Local-only recovery: opening this screen never checks the network automatically.
        begin((connection,token)->{
            TvUpdateClient.Candidate saved=null;try{saved=connection.saved(this);}catch(Exception ignored){}
            final TvUpdateClient.Candidate restore=saved;
            done(token,()->{candidate=restore;if(restore!=null){downloaded=new File(cacheDirectory(),"beta-"+restore.versionCode+".apk");status.setText("Downloaded and verified: "+restore.versionName+". Ready to install.");}else status.setText("Ready to check for a newer beta.");});
        });
    }
    private File cacheDirectory(){return new File(getCacheDir(),"tv-updates");}
    private void controls() {
        check.setEnabled(!busy&&compatible());download.setVisibility(candidate!=null&&downloaded==null?View.VISIBLE:View.GONE);download.setEnabled(!busy);
        install.setVisibility(downloaded!=null?View.VISIBLE:View.GONE);install.setEnabled(!busy);
        permission.setVisibility(downloaded!=null&&!getPackageManager().canRequestPackageInstalls()?View.VISIBLE:View.GONE);
        cancel.setVisibility(busy?View.VISIBLE:View.GONE);progress.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    private interface Operation {void run(TvUpdateClient connection,long token) throws Exception;}
    private void begin(Operation run) {
        busy=true;generation++;client=new TvUpdateClient();final TvUpdateClient connection=client;progress.setIndeterminate(true);controls();long token=generation;
        task=worker.submit(()->{try{run.run(connection,token);}catch(Exception e){post(token,()->{busy=false;status.setText(e instanceof CancellationException?"Update cancelled. Nothing was installed.":e.getMessage()==null?"The update could not be completed. Check your connection and retry.":e.getMessage());controls();});}});
    }
    private void post(long token,Runnable run){runOnUiThread(()->{if(token==generation&&!isFinishing()&&!isDestroyed())run.run();});}
    private void done(long token,Runnable run){post(token,()->{busy=false;run.run();controls();});}
    private void cancelTask(){generation++;if(client!=null)client.cancel();if(task!=null)task.cancel(true);busy=false;status.setText("Update cancelled. Nothing was installed.");controls();check.requestFocus();}
    private void checkLatest() {
        candidate=null;downloaded=null;status.setText("Checking tested Vibelight TV beta releases…");
        begin((connection,token)->{TvUpdateClient.Candidate next=connection.latest(TvUpdateClient.installedVersion(this),Build.VERSION.SDK_INT);
            done(token,()->{candidate=next;status.setText(next==null?"You are up to date with published, tested TV betas.":"Beta "+next.versionName+" is available ("+Math.round(next.size/1048576.0)+" MB). Download when ready.");if(next!=null)download.post(download::requestFocus);});});
    }
    private void confirmDownload() {
        if(candidate==null)return;
        new AlertDialog.Builder(this).setTitle("Download beta update?").setMessage(candidate.versionName+"\n\nThis downloads the Shield APK from nyteghost/vibelight. Android will ask you to approve installation afterward.")
            .setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->downloadSelected()).show();
    }
    private void downloadSelected() {
        final TvUpdateClient.Candidate selected=candidate;status.setText("Downloading beta update…");
        begin((connection,token)->{File file=connection.download(this,selected,(bytes,total)->post(token,()->{progress.setIndeterminate(false);progress.setProgress((int)(100*bytes/total));status.setText("Downloading… "+100*bytes/total+"%");}));
            done(token,()->{downloaded=file;status.setText("Downloaded and verified: "+selected.versionName+". Choose Install to continue.");install.post(install::requestFocus);});});
    }
    static Intent installerIntent(Context context,File file) {
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".tv-updates",file);
        Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
        intent.setClipData(ClipData.newRawUri("Vibelight TV beta",uri));
        return intent;
    }
    private void installDownloaded() {
        if(candidate==null||downloaded==null)return;
        if(TvSessionState.active()){status.setText("End the current stream before installing an update. The verified download will stay available.");return;}
        if(!getPackageManager().canRequestPackageInstalls()) {status.setText("Allow Vibelight TV Beta to install updates using the button below, then return here and choose Install. Android will still ask for confirmation.");controls();permission.requestFocus();return;}
        final File file=downloaded;final TvUpdateClient.Candidate selected=candidate;status.setText("Verifying the downloaded APK again…");
        begin((connection,token)->{TvUpdateClient.verify(this,file,selected,TvUpdateClient.installedVersion(this));done(token,()->{try{if(TvSessionState.active()){status.setText("End the current stream before installing this update.");return;}startActivityForResult(installerIntent(this,file),72);status.setText("Finish installation in Android. If cancelled, the verified download stays available.");}catch(ActivityNotFoundException|SecurityException e){status.setText("Android could not open its installer. Check install permission in Shield settings, then retry.");}});});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==72){status.setText(result==RESULT_OK?"Android reported installation complete. Reopen Vibelight TV to see the new version.":"Installation was cancelled or did not complete. Your current app is still installed; you can retry.");controls();}}
    @Override protected void onResume(){super.onResume();if(check!=null)controls();}
    @Override protected void onDestroy(){generation++;if(client!=null)client.cancel();if(task!=null)task.cancel(true);worker.shutdownNow();super.onDestroy();}
}
