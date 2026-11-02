package com.limelight.tv;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.KeyEvent;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Debug-only archive/provider/installer checks. Uses this test APK; no release network or host. */
public final class TvUpdateProbe extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private interface Attempt {void run() throws Exception;}
    private void reject(Attempt a) throws Exception {try{a.run();throw new AssertionError("Unsafe update was accepted");}catch(IOException|IllegalArgumentException expected){}}
    @Override public void onStart(){Bundle result=new Bundle();File fixture=null;
        try {
            Context context=getTargetContext();long version=TvUpdateClient.installedVersion(context);
            fixture=new File(TvUpdateClient.directory(context),"installer-probe.apk");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(context.getApplicationInfo().sourceDir);OutputStream out=new FileOutputStream(fixture)){byte[] data=new byte[65536];int n;while((n=in.read(data))!=-1){out.write(data,0,n);digest.update(data,0,n);}}
            final File apk=fixture;String hash=TvUpdateClient.hex(digest.digest());
            JSONObject metadata=new JSONObject().put("schema",1).put("repository",TvUpdatePolicy.REPOSITORY).put("branch",TvUpdatePolicy.BRANCH).put("channel","beta")
                .put("versionCode",version).put("versionName","0.2.0-beta.test").put("sha256",hash).put("size",apk.length()).put("minSdk",26)
                .put("sourceRevision","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").put("packageName",TvUpdatePolicy.PACKAGE).put("abi","arm64-v8a");
            // A lower version floor exercises verification without downgrading the emulator app.
            TvUpdateClient.Candidate valid=new TvUpdateClient.Candidate("tv-beta-"+version,metadata,version-1,Build.VERSION.SDK_INT);
            TvUpdateClient.verify(context,apk,valid,version-1);
            reject(()->TvUpdateClient.verify(context,apk,valid,version));
            metadata.put("sha256","0000000000000000000000000000000000000000000000000000000000000000");
            TvUpdateClient.Candidate corrupted=new TvUpdateClient.Candidate("tv-beta-"+version,metadata,version-1,Build.VERSION.SDK_INT);
            reject(()->TvUpdateClient.verify(context,apk,corrupted,version-1));
            reject(()->FileProvider.getUriForFile(context,context.getPackageName()+".tv-updates",new File(context.getFilesDir(),"private-pairing-data")));
            ProviderInfo provider=context.getPackageManager().getProviderInfo(new ComponentName(context,TvUpdateFileProvider.class),0);
            check(!provider.exported&&provider.grantUriPermissions,"Update provider is not private/scoped");
            PackageInfo own=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
            check(Arrays.asList(own.requestedPermissions).contains("android.permission.REQUEST_INSTALL_PACKAGES"),"Installer permission missing");
            Intent installer=TvUpdateActivity.installerIntent(context,apk);
            check("content".equals(installer.getData().getScheme()),"APK uses a file URI");
            check((installer.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"Installer read grant missing");
            try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(installer.getData(),"r")){check(fd!=null&&fd.getStatSize()==apk.length(),"Granted APK not readable");}
            TvUpdateActivity activity=(TvUpdateActivity)startActivitySync(new Intent(context,TvUpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            check(activity.getPackageManager().canRequestPackageInstalls(),"Emulator did not grant test install permission");
            runOnMainSync(()->activity.startActivityForResult(installer,72));
            boolean prompt=false;long until=SystemClock.uptimeMillis()+10000;
            while(SystemClock.uptimeMillis()<until){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                if(root!=null&&root.getPackageName()!=null&&root.getPackageName().toString().contains("packageinstaller")){prompt=true;break;}SystemClock.sleep(100);}
            check(prompt,"Android installer confirmation was not reached");
            check(getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),"Cannot cancel installer in test");waitForIdleSync();runOnMainSync(activity::finish);
            result.putString("stream","Update validation passed: real APK hash/package/signature, same-version refusal, hash mismatch, private provider scope, Android installer prompt and cancellation. No network download or actual version upgrade performed.");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally {if(fixture!=null)fixture.delete();}
    }
}
