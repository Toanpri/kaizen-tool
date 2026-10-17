package com.kaizen.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M5 — global (whole-device) filename search.
 *
 * Runs on a background thread, cooperatively cancellable via CancelToken.
 * Both Listener callbacks fire on the WORKER thread — the caller is
 * responsible for posting to the UI thread.
 *
 * Safety rails: max depth 6 from the storage root, skips Android/data and
 * Android/obb (slow + permission-restricted), caps results at 500.
 */
public class GlobalSearch {

    public interface Listener {
        /** A directory is being scanned. Called on the worker thread (throttled). */
        void onProgress(String currentPath);
        /** Scan finished. Called on the worker thread. */
        void onDone(List<File> results, boolean cancelled);
    }

    private static final int MAX_DEPTH = 6;
    private static final int MAX_RESULTS = 500;

    /** Chế độ lọc kết quả. */
    public static final int MODE_BOTH = 0;
    public static final int MODE_FILES = 1;
    public static final int MODE_DIRS = 2;

    public static void run(File root, String keyword, CancelToken token, Listener l) {
        run(root, keyword, MODE_BOTH, token, l);
    }

    public static void run(File root, String keyword, int mode,
                           CancelToken token, Listener l) {
        final List<File> out = new ArrayList<File>();
        final String kw = keyword.toLowerCase(Locale.US);
        final long[] lastProgress = new long[]{0};
        try {
            scan(root, kw, mode, 0, token, out, l, lastProgress);
        } catch (Exception e) {
            // ignore — partial results are still returned
        }
        boolean cancelled = token != null && token.isCancelled();
        l.onDone(out, cancelled);
    }

    private static void scan(File dir, String kw, int mode, int depth, CancelToken token,
                             List<File> out, Listener l, long[] lastProgress) {
        if (cancelled(token)) return;
        if (depth > MAX_DEPTH || out.size() >= MAX_RESULTS) return;
        if (dir == null || !dir.isDirectory()) return;

        String abs;
        try {
            abs = dir.getCanonicalPath();
        } catch (Exception e) {
            return;
        }
        // Skip the two slowest, permission-restricted trees.
        if (abs.contains("/Android/data") || abs.contains("/Android/obb")) return;

        // Throttle progress updates (~4/sec) to avoid flooding the UI thread.
        long now = System.currentTimeMillis();
        if (now - lastProgress[0] > 250) {
            lastProgress[0] = now;
            l.onProgress(abs);
        }

        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (cancelled(token)) return;
            if (out.size() >= MAX_RESULTS) return;
            if (f.getName().toLowerCase(Locale.US).contains(kw)) {
                boolean isDir = f.isDirectory();
                if (mode == MODE_BOTH
                        || (mode == MODE_FILES && !isDir)
                        || (mode == MODE_DIRS && isDir)) {
                    out.add(f);
                }
            }
            if (f.isDirectory()) {
                scan(f, kw, mode, depth + 1, token, out, l, lastProgress);
            }
        }
    }

    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }
}
