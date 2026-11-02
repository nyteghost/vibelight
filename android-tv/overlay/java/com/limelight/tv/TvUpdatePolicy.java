package com.limelight.tv;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Update trust boundaries shared by transport, archive verification and local tests. */
public final class TvUpdatePolicy {
    private TvUpdatePolicy() {}
    public static final String PACKAGE = "io.github.nyteghost.vibelight.tv.beta";
    public static final String REPOSITORY = "nyteghost/vibelight";
    public static final String BRANCH = "beta/next-features-20261028";
    public static final String APK = "Vibelight-TV-arm64-v8a-beta.apk";
    public static final String MANIFEST = "vibelight-tv-beta.json";
    public static final long MAX_APK = 120L * 1024 * 1024;
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
        "api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com"));
    public static long versionFromTag(String tag) {
        if(tag==null || !tag.matches("tv-beta-[1-9][0-9]{0,9}"))return -1;
        try {long n=Long.parseLong(tag.substring(8));return n<=2000000000?n:-1;}catch(NumberFormatException e){return -1;}
    }
    public static String assetUrl(String tag,String file) {
        if(versionFromTag(tag)<0 || !(APK.equals(file)||MANIFEST.equals(file)))throw new IllegalArgumentException("Unexpected beta asset");
        return "https://github.com/"+REPOSITORY+"/releases/download/"+tag+"/"+file;
    }
    public static boolean allowedTransport(String address) {
        try {URI uri=new URI(address);return "https".equals(uri.getScheme()) && uri.getUserInfo()==null && uri.getFragment()==null &&
            (uri.getPort()==-1||uri.getPort()==443) && HOSTS.contains(uri.getHost());}catch(Exception e){return false;}
    }
    public static void validate(String tag,long version,long installed,String pkg,String abi,int minSdk,int deviceSdk,long size,String sha) {
        if(versionFromTag(tag)!=version || version<=installed)throw new IllegalArgumentException("This beta is not newer than the installed app.");
        if(!PACKAGE.equals(pkg)||!"arm64-v8a".equals(abi))throw new IllegalArgumentException("This update is not the Shield TV beta package.");
        if(minSdk<26 || minSdk>deviceSdk)throw new IllegalArgumentException("This beta requires a different Android version.");
        if(size<1||size>MAX_APK||sha==null||!sha.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid update size or checksum.");
    }
    public static void requireSameSigner(Set<String> installed,Set<String> candidate) {
        if(installed.size()!=1||!installed.equals(candidate))throw new IllegalArgumentException("The signing certificate changed. Update blocked to protect the existing installation. Keep this app installed.");
    }
}
