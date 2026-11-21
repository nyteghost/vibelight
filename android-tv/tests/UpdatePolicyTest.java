import com.limelight.tv.TvUpdatePolicy;
import com.limelight.tv.TvSessionState;
import java.util.*;
public class UpdatePolicyTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError();}
    static void reject(Runnable r){checks++;try{r.run();throw new AssertionError("Unsafe candidate accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        check(!TvSessionState.active());TvSessionState.enter();TvSessionState.enter();TvSessionState.leave();check(TvSessionState.active());TvSessionState.leave();check(!TvSessionState.active());TvSessionState.leave();check(!TvSessionState.active());
        String hash="a".repeat(64);
        check(TvUpdatePolicy.versionFromTag("tv-beta-10701")==10701);
        for(String s:new String[]{"v1.0","tv-beta--1","tv-beta-0","tv-beta-3000000000","tv-beta-123/escape"})check(TvUpdatePolicy.versionFromTag(s)==-1);
        check(TvUpdatePolicy.allowedTransport("https://github.com/nyteghost/vibelight/releases/download/tv-beta-10701/test.apk"));
        check(TvUpdatePolicy.allowedTransport("https://release-assets.githubusercontent.com/asset?signature=abc"));
        for(String u:new String[]{"http://github.com/a","https://github.com.evil.test/a","https://user@github.com/a","https://github.com:444/a","file:///tmp/app.apk","https://evil.test/app.apk","https://github.com/a#fragment"})check(!TvUpdatePolicy.allowedTransport(u));
        check(TvUpdatePolicy.assetUrl("tv-beta-10701",TvUpdatePolicy.APK).contains("/nyteghost/vibelight/releases/download/tv-beta-10701/"));
        reject(()->TvUpdatePolicy.assetUrl("tv-beta-10701","../app.apk"));
        TvUpdatePolicy.validate("tv-beta-10701",10701,10601,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,15000000,hash);checks++;
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10701,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10801,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10702",10701,10601,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10601,"com.limelight","arm64-v8a",26,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10601,TvUpdatePolicy.PACKAGE,"x86",26,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10601,TvUpdatePolicy.PACKAGE,"arm64-v8a",31,30,1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10601,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,TvUpdatePolicy.MAX_APK+1,hash));
        reject(()->TvUpdatePolicy.validate("tv-beta-10701",10701,10601,TvUpdatePolicy.PACKAGE,"arm64-v8a",26,30,1,"invalid"));
        TvUpdatePolicy.requireSameSigner(Set.of("a"),Set.of("a"));checks++;
        reject(()->TvUpdatePolicy.requireSameSigner(Set.of("a"),Set.of("b")));
        reject(()->TvUpdatePolicy.requireSameSigner(Set.of(),Set.of()));
        reject(()->TvUpdatePolicy.requireSameSigner(Set.of("a","b"),Set.of("a","b")));
        System.out.println(checks+" update trust-boundary checks passed");
    }
}
