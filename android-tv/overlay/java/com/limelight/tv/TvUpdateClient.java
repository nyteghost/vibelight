package com.limelight.tv;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Manual, credential-free beta downloads. All files remain in the app's private cache. */
public final class TvUpdateClient {
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    public static final class Candidate {
        public final String tag, versionName, sha256, revision;
        public final long versionCode, size;
        public final int minSdk;
        Candidate(String tag,JSONObject m,long installed,int deviceSdk) throws Exception {
            if(m.getInt("schema")!=1 || !TvUpdatePolicy.REPOSITORY.equals(m.getString("repository")) ||
                !TvUpdatePolicy.BRANCH.equals(m.getString("branch")) || !"beta".equals(m.getString("channel")))throw new IOException("Unexpected update manifest.");
            versionCode=integer(m,"versionCode");size=integer(m,"size");long minimum=integer(m,"minSdk");if(minimum<26||minimum>100)throw new IOException("Invalid minimum Android version.");minSdk=(int)minimum;
            versionName=m.getString("versionName");sha256=m.getString("sha256");revision=m.getString("sourceRevision");this.tag=tag;
            TvUpdatePolicy.validate(tag,versionCode,installed,m.getString("packageName"),m.getString("abi"),minSdk,deviceSdk,size,sha256);
            if(versionName.length()>100 || !versionName.contains("beta") || !revision.matches("[a-f0-9]{40}"))throw new IOException("Invalid beta version information.");
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("schema",1).put("repository",TvUpdatePolicy.REPOSITORY).put("branch",TvUpdatePolicy.BRANCH).put("channel","beta")
                .put("versionCode",versionCode).put("versionName",versionName).put("sha256",sha256).put("size",size).put("minSdk",minSdk)
                .put("packageName",TvUpdatePolicy.PACKAGE).put("abi","arm64-v8a").put("sourceRevision",revision);
        }
    }
    private static long integer(JSONObject m,String key) throws JSONException,IOException {
        Object n=m.get(key);if(!(n instanceof Integer||n instanceof Long))throw new IOException("Non-integer update metadata.");return ((Number)n).longValue();
    }
    public interface Progress {void received(long bytes,long total);}
    public void cancel() {cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();}
    private void checkCancelled() {if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException();}
    private HttpURLConnection connect(String address) throws Exception {
        for(int redirects=0;redirects<6;redirects++) {
            checkCancelled();
            if(!TvUpdatePolicy.allowedTransport(address))throw new IOException("Untrusted download destination.");
            HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();active=connection;
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","Vibelight-TV-Beta-Updater");connection.setRequestProperty("Accept",address.startsWith("https://api.github.com/")?"application/vnd.github+json":"application/octet-stream");
            int status=connection.getResponseCode();
            if(status>=300&&status<=399) {
                String location=connection.getHeaderField("Location");
                if(location==null){connection.disconnect();throw new IOException("Download redirect missing.");}
                address=new URL(new URL(address),location).toString();connection.disconnect();continue;
            }
            if(status!=200) {connection.disconnect();throw new IOException(status==403||status==429?"GitHub's request limit was reached. Try again later.":"The beta download is unavailable (HTTP "+status+").");}
            return connection;
        }
        throw new IOException("Too many update redirects.");
    }
    private String text(String url,int limit) throws Exception {
        HttpURLConnection c=connect(url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int n;
            while((n=in.read(bytes))!=-1){checkCancelled();if(out.size()+n>limit)throw new IOException("Update metadata is too large.");out.write(bytes,0,n);}
            return out.toString("UTF-8");
        } finally {c.disconnect();active=null;}
    }
    public Candidate latest(long installed,int sdk) throws Exception {
        JSONObject release=null;long best=installed;
        // Bounded pagination avoids an old high version disappearing behind unrelated desktop releases.
        for(int page=1;page<=3;page++) {
            JSONArray releases=new JSONArray(text("https://api.github.com/repos/"+TvUpdatePolicy.REPOSITORY+"/releases?per_page=100&page="+page,2*1024*1024));
            for(int i=0;i<releases.length();i++) {
                JSONObject r=releases.getJSONObject(i);long code=TvUpdatePolicy.versionFromTag(r.optString("tag_name"));
                if(r.optBoolean("draft",true)||!r.optBoolean("prerelease",false)||code<=best)continue;
                boolean apk=false,manifest=false;
                JSONArray assets=r.getJSONArray("assets");
                for(int j=0;j<assets.length();j++){JSONObject a=assets.getJSONObject(j);String name=a.getString("name");
                    if(TvUpdatePolicy.APK.equals(name))apk=TvUpdatePolicy.assetUrl(r.getString("tag_name"),name).equals(a.getString("browser_download_url"));
                    if(TvUpdatePolicy.MANIFEST.equals(name))manifest=TvUpdatePolicy.assetUrl(r.getString("tag_name"),name).equals(a.getString("browser_download_url"));}
                if(apk&&manifest){best=code;release=r;}
            }
            if(releases.length()<100)break;
            if(page==3)throw new IOException("The release catalog is too large to check safely. Open the project releases page or retry later.");
        }
        if(release==null)return null;
        String tag=release.getString("tag_name");
        Candidate c=new Candidate(tag,new JSONObject(text(TvUpdatePolicy.assetUrl(tag,TvUpdatePolicy.MANIFEST),65536)),installed,sdk);
        if(!c.revision.equals(release.getString("target_commitish")))throw new IOException("The release and source revision do not match.");
        return c;
    }
    static File directory(Context context) throws IOException {
        File d=new File(context.getCacheDir(),"tv-updates");if(!d.isDirectory()&&!d.mkdirs())throw new IOException("Cannot create the update cache.");return d;
    }
    public File download(Context context,Candidate candidate,Progress progress) throws Exception {
        File dir=directory(context);File partial=File.createTempFile("download-",".part",dir);File complete=new File(dir,"beta-"+candidate.versionCode+".apk");
        HttpURLConnection c=null;
        try {
            c=connect(TvUpdatePolicy.assetUrl(candidate.tag,TvUpdatePolicy.APK));
            if(c.getContentLengthLong()>0&&c.getContentLengthLong()!=candidate.size)throw new IOException("Download size does not match its manifest.");
            long count=0,lastNotice=0;
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)) {
                byte[] data=new byte[65536];int n;
                while((n=in.read(data))!=-1){checkCancelled();count+=n;if(count>candidate.size)throw new IOException("Download exceeds the expected size.");out.write(data,0,n);
                    if(count-lastNotice>=256*1024){progress.received(count,candidate.size);lastNotice=count;}}
                out.getFD().sync();
            }
            if(count!=candidate.size)throw new IOException("Download was interrupted. Check your connection and retry.");
            verify(context,partial,candidate,installedVersion(context));checkCancelled();
            if(complete.exists()&&!complete.delete())throw new IOException("Cannot replace the previous update file.");
            if(!partial.renameTo(complete))throw new IOException("Cannot save the verified update.");
            File receipt=new File(dir,"receipt.json");
            try(FileOutputStream out=new FileOutputStream(receipt)){out.write(candidate.json().put("tag",candidate.tag).toString().getBytes("UTF-8"));out.getFD().sync();}
            File[] obsolete=dir.listFiles();
            if(obsolete!=null)for(File f:obsolete)if(!f.equals(complete)&&f.isFile()&&(f.getName().matches("beta-[0-9]+[.]apk")||f.getName().startsWith("download-")))f.delete();
            return complete;
        } finally {partial.delete();if(c!=null)c.disconnect();active=null;}
    }
    public static long installedVersion(Context c) throws PackageManager.NameNotFoundException {
        PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return version(p);
    }
    private static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private static int signingFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static Set<String> signers(PackageInfo p) throws Exception {
        Signature[] certificates=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
        Set<String> set=new HashSet<>();if(certificates!=null)for(Signature s:certificates)set.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return set;
    }
    static void verifyArchive(Context context,File file,long expected,long installed) throws Exception {
        PackageManager pm=context.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),signingFlags());
        if(candidate==null||!TvUpdatePolicy.PACKAGE.equals(candidate.packageName)||version(candidate)!=expected||expected<=installed)throw new IOException("The APK package or version is invalid. Nothing was installed.");
        PackageInfo own=pm.getPackageInfo(context.getPackageName(),signingFlags());TvUpdatePolicy.requireSameSigner(signers(own),signers(candidate));
        if(candidate.applicationInfo==null||candidate.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("This APK requires a newer Android version.");
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file)) {
            if(zip.getEntry("lib/arm64-v8a/libmoonlight-core.so")==null && Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"))throw new IOException("The APK does not contain the Shield streaming library.");
        }
    }
    static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    public static void verify(Context context,File file,Candidate c,long installed) throws Exception {
        if(file.length()!=c.size)throw new IOException("Update file size mismatch.");
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] data=new byte[65536];int n;while((n=in.read(data))!=-1){if(Thread.currentThread().isInterrupted())throw new CancellationException();md.update(data,0,n);}}
        if(!hex(md.digest()).equals(c.sha256))throw new IOException("Update checksum mismatch. Download again; nothing was installed.");
        verifyArchive(context,file,c.versionCode,installed);
    }
    public Candidate saved(Context context) throws Exception {
        File receipt=new File(directory(context),"receipt.json");if(!receipt.isFile()||receipt.length()>65536)return null;
        String json;try(InputStream in=new FileInputStream(receipt);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Invalid saved update.");out.write(b,0,n);}json=out.toString("UTF-8");}
        JSONObject m=new JSONObject(json);
        Candidate c=new Candidate(m.getString("tag"),m,installedVersion(context),Build.VERSION.SDK_INT);
        verify(context,new File(directory(context),"beta-"+c.versionCode+".apk"),c,installedVersion(context));return c;
    }
}
