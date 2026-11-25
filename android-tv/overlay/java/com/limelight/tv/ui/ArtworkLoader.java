package com.limelight.tv.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.tv.logic.ArtBudget;
import com.limelight.utils.ServerHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Host-provided box art only (the host's /appasset), loaded off the UI thread,
 * decoded at a bounded size, held in a bounded memory cache and a bounded disk
 * cache. Results for a view that has since been rebound are discarded.
 */
public final class ArtworkLoader {
    public interface Callback { void onResult(Bitmap bitmap); }

    private static ArtworkLoader instance;
    private static final int MAX_DOWNLOAD_BYTES = 6 * 1024 * 1024;

    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final File dir;

    private ArtworkLoader(Context c) {
        long budget = ArtBudget.memoryBytes(Runtime.getRuntime().maxMemory());
        memory = new LruCache<String, Bitmap>((int) Math.min(Integer.MAX_VALUE, budget)) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getAllocationByteCount(); }
        };
        dir = new File(c.getCacheDir(), "vibe-art");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public static synchronized ArtworkLoader get(Context c) {
        if (instance == null) instance = new ArtworkLoader(c.getApplicationContext());
        return instance;
    }

    private static String key(String host, int app) { return host.replaceAll("[^A-Za-z0-9-]", "_") + "-" + app; }

    public Bitmap cached(String hostUuid, int appId) { return memory.get(key(hostUuid, appId)); }

    /**
     * Loads art for {@code target}; the target's tag is used as a binding token so a
     * recycled or rebound view never receives another game's art.
     */
    public void load(final Activity activity, final ImageView target, final ComputerDetails host, final String uniqueId,
                     final NvApp app, final Callback done) {
        final String k = key(host.uuid, app.getAppId());
        target.setTag(k);
        Bitmap hit = memory.get(k);
        if (hit != null) { target.setImageBitmap(hit); if (done != null) done.onResult(hit); return; }
        final ComputerDetails copy = new ComputerDetails(host);
        pool.execute(() -> {
            Bitmap bmp = null;
            try {
                File f = new File(dir, k);
                byte[] data = f.isFile() ? readAll(new java.io.FileInputStream(f)) : null;
                if (data == null && copy.serverCert != null) {
                    NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(copy), copy.httpsPort, uniqueId,
                            copy.serverCert, PlatformBinding.getCryptoProvider(activity));
                    InputStream in = http.getBoxArt(app);
                    data = in == null ? null : readAll(in);
                    if (data != null && data.length > 0) store(f, data);
                }
                if (data != null && data.length > 0) bmp = decode(data);
            } catch (IOException | RuntimeException ignored) {
                // Missing art falls back to the text card; nothing is invented.
            }
            final Bitmap result = bmp;
            if (result != null) memory.put(k, result);
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || !k.equals(target.getTag())) return;
                if (result != null) target.setImageBitmap(result);
                if (done != null) done.onResult(result);
            });
        });
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = s.read(buf)) > 0) {
                total += n;
                if (total > MAX_DOWNLOAD_BYTES) throw new IOException("Artwork too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = ArtBudget.sampleSize(o.outWidth, o.outHeight);
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    private synchronized void store(File f, byte[] data) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        } catch (IOException ignored) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File x : files) total += x.length();
        if (total <= ArtBudget.MAX_DISK_BYTES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File x : files) {
            if (total <= ArtBudget.MAX_DISK_BYTES * 3 / 4) break;
            total -= x.length();
            //noinspection ResultOfMethodCallIgnored
            x.delete();
        }
    }

    /** Drops cached art for a host whose library changed. */
    public void forget(String hostUuid, int appId) {
        String k = key(hostUuid, appId);
        memory.remove(k);
        //noinspection ResultOfMethodCallIgnored
        new File(dir, k).delete();
    }
}
