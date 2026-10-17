package com.kaizen.tool;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async thumbnail loader for images, videos, and APKs.
 * - LruCache keyed by file path (+lastModified) to avoid reloads on scroll
 * - Background thread pool for decoding; results posted to UI thread
 * - View-tag guard prevents recycled rows from showing wrong thumbnails
 */
public class ThumbnailLoader {

    private static final int THUMB_PX = 144; // target thumbnail size

    private final Context appContext;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService executor;
    private final Handler uiHandler;

    public ThumbnailLoader(Context context) {
        this.appContext = context.getApplicationContext();
        // Use ~1/8 of max memory for thumbnails
        int maxMemKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheKb = maxMemKb / 8;
        this.cache = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap bmp) {
                return bmp.getByteCount() / 1024;
            }
        };
        this.executor = Executors.newFixedThreadPool(3);
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    /** Cache key includes lastModified so edited files refresh. */
    private static String keyFor(FileItem item) {
        return item.path() + "@" + item.lastModified;
    }

    /**
     * Load a thumbnail for image/video/apk files, or set the fallback icon
     * immediately for other types. Must be called on the UI thread.
     */
    public void load(FileItem item, ImageView view, int fallbackResId) {
        String path = item.path();
        view.setTag(path);

        if (!isPreviewable(item)) {
            view.setImageResource(fallbackResId);
            return;
        }

        String key = keyFor(item);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        // Show fallback while loading
        view.setImageResource(fallbackResId);

        executor.execute(new LoadTask(item, view, key, fallbackResId));
    }

    private static boolean isPreviewable(FileItem item) {
        if (item.isDir) return false;
        String ext = extOf(item.name);
        return isImageExt(ext) || isVideoExt(ext) || ext.equals("apk");
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static boolean isImageExt(String ext) {
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("webp") || ext.equals("bmp") || ext.equals("gif");
    }

    private static boolean isVideoExt(String ext) {
        return ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi")
                || ext.equals("mov") || ext.equals("3gp") || ext.equals("webm");
    }

    private class LoadTask implements Runnable {
        private final FileItem item;
        private final ImageView view;
        private final String key;
        private final String path;
        private final int fallbackResId;

        LoadTask(FileItem item, ImageView view, String key, int fallbackResId) {
            this.item = item;
            this.view = view;
            this.key = key;
            this.path = item.path();
            this.fallbackResId = fallbackResId;
        }

        @Override
        public void run() {
            Bitmap bmp = null;
            try {
                String ext = extOf(item.name);
                if (isImageExt(ext)) {
                    bmp = decodeImageThumb(path);
                } else if (isVideoExt(ext)) {
                    bmp = decodeVideoThumb(path);
                } else if (ext.equals("apk")) {
                    bmp = decodeApkIcon(path);
                }
            } catch (Exception e) {
                bmp = null; // fallback below
            } catch (OutOfMemoryError e) {
                cache.evictAll();
                bmp = null;
            }

            final Bitmap result = bmp;
            if (result != null) {
                cache.put(key, result);
            }
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    // View may have been recycled — only set if tag still matches
                    if (path.equals(view.getTag())) {
                        if (result != null) {
                            view.setImageBitmap(result);
                        } else {
                            view.setImageResource(fallbackResId);
                        }
                    }
                }
            });
        }
    }

    // ---------- decoders ----------

    /** Decode a downsampled bitmap for an image file. */
    private static Bitmap decodeImageThumb(String path) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

        opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight);
        opts.inJustDecodeBounds = false;
        // Use RGB_565 to halve memory (no alpha needed for photos)
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int calcSampleSize(int w, int h) {
        int sample = 1;
        while (w / (sample * 2) >= THUMB_PX && h / (sample * 2) >= THUMB_PX) {
            sample *= 2;
        }
        return sample;
    }

    /** Grab one frame from a video file. */
    private static Bitmap decodeVideoThumb(String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            Bitmap frame = mmr.getFrameAtTime(1_000_000); // 1 second in
            if (frame == null) {
                frame = mmr.getFrameAtTime(0);
            }
            if (frame == null) return null;
            // Downscale if too large
            int w = frame.getWidth(), h = frame.getHeight();
            if (Math.max(w, h) > THUMB_PX * 2) {
                float scale = (THUMB_PX * 2f) / Math.max(w, h);
                Bitmap small = Bitmap.createScaledBitmap(
                        frame, (int) (w * scale), (int) (h * scale), true);
                if (small != frame) frame.recycle();
                frame = small;
            }
            return frame;
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    /** Extract the app icon from an APK file. */
    private Bitmap decodeApkIcon(String path) {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(
                    path, PackageManager.GET_ACTIVITIES);
            if (pi == null || pi.applicationInfo == null) return null;
            ApplicationInfo appInfo = pi.applicationInfo;
            appInfo.sourceDir = path;
            appInfo.publicSourceDir = path;
            Drawable d = pm.getApplicationIcon(appInfo);
            return drawableToBitmap(d);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) return b;
        }
        int w = Math.max(d.getIntrinsicWidth(), 1);
        int h = Math.max(d.getIntrinsicHeight(), 1);
        // Cap at 256px to bound memory
        if (Math.max(w, h) > 256) {
            float s = 256f / Math.max(w, h);
            w = (int) (w * s);
            h = (int) (h * s);
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return bmp;
    }

    /** Call from Activity.onDestroy to stop background work. */
    public void shutdown() {
        executor.shutdownNow();
        cache.evictAll();
    }
}
