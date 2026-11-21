package com.limelight.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.limelight.PcView;
import com.limelight.nvstream.http.ComputerDetails;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Debug-only callback navigation test. Does not pair, store a certificate or contact a host. */
public final class TvNavigationProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    private void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private View findText(View view,String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private void returned(TvHomeActivity home) {
        waitForIdleSync();SystemClock.sleep(400);
        runOnMainSync(()->{
            check(!home.isFinishing(),"TV home was destroyed");
            check(home.hasWindowFocus(),"Pairing did not return foreground focus to TV home");
            check(findText(home.getWindow().getDecorView(),"Play on the big screen")!=null,"TV dashboard not restored");
        });
    }
    private TvPairingActivity open(TvHomeActivity home) throws Exception {
        ActivityMonitor monitor=addMonitor(TvPairingActivity.class.getName(),null,false);
        runOnMainSync(home::openPairingManager);
        TvPairingActivity manager=(TvPairingActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        check(manager!=null,"TV pairing wrapper not opened");
        Field binder=PcView.class.getDeclaredField("managerBinder");binder.setAccessible(true);
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until) {
            final boolean[] ready={false};
            runOnMainSync(()->{try { ready[0]=binder.get(manager)!=null && findText(manager.getWindow().getDecorView(),"Return to Vibelight TV")!=null; }catch(IllegalAccessException e){throw new AssertionError(e);}});
            if(ready[0])return manager;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Core pairing UI/service not ready");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Intent intent=new Intent(getTargetContext(),TvHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            TvHomeActivity home=(TvHomeActivity)startActivitySync(intent);waitForIdleSync();
            TvPairingActivity manager=open(home);
            // Drive the real upstream post-pairing/paired-selection route with an in-memory identity.
            // This proves UI routing only. No authentication success or physical host is simulated.
            Method selected=PcView.class.getDeclaredMethod("doAppList",ComputerDetails.class,boolean.class,boolean.class);selected.setAccessible(true);
            ComputerDetails host=new ComputerDetails();host.uuid="navigation-test-only";host.name="Navigation test";host.state=ComputerDetails.State.ONLINE;
            Field hosts=TvHomeActivity.class.getDeclaredField("hosts");hosts.setAccessible(true);
            Field pending=TvHomeActivity.class.getDeclaredField("pendingHostFocus");pending.setAccessible(true);
            for(boolean newlyPaired:new boolean[]{true,false}) {
                final TvPairingActivity active=manager;
                runOnMainSync(()->{try {((java.util.Map<String,ComputerDetails>)hosts.get(home)).put(host.uuid,host);selected.invoke(active,host,newlyPaired,false);}catch(Exception e){throw new AssertionError(e);}});
                returned(home);
                check(host.uuid.equals(pending.get(home)),"Stale cached host consumed post-pair focus before refresh");
                manager=open(home);
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);returned(home);
            manager=open(home);final TvPairingActivity active=manager;
            runOnMainSync(()->findText(active.getWindow().getDecorView(),"Return to Vibelight TV").performClick());returned(home);
            result.putString("stream","Pairing navigation passed: post-pair callback, paired-host selection, Back and explicit return. No crypto/streaming test.");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);
        }
    }
}
