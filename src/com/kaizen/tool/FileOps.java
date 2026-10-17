package com.kaizen.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.os.Handler;
import android.os.Looper;

/**
 * File operations for Kaizen Tool M1.
 * copyFiles / moveFiles / deleteFiles run on a background thread and
 * post progress + completion to the UI thread.
 * rename / mkdir / createFile are synchronous.
 */
public class FileOps {

    public interface OpCallback {
        void onProgress(int done, int total, String name);
        void onDone(boolean ok, String err);   // err = null if ok
    }

    private static final int BUFFER_SIZE = 8192;

    // ------------------------------------------------------------------
    // Public async operations
    // ------------------------------------------------------------------

    public static void copyFiles(final List<File> srcs, final File dstDir, final OpCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler ui = new Handler(Looper.getMainLooper());
                try {
                    if (srcs == null || srcs.isEmpty()) {
                        postDone(ui, cb, false, "Không có mục nào để sao chép");
                        return;
                    }
                    if (dstDir == null) {
                        postDone(ui, cb, false, "Thư mục đích không hợp lệ");
                        return;
                    }
                    if (!dstDir.exists() && !dstDir.mkdirs()) {
                        postDone(ui, cb, false, "Không tạo được thư mục đích");
                        return;
                    }
                    final int total = countAll(srcs);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];
                    for (File src : srcs) {
                        if (src == null || !src.exists()) {
                            firstError[0] = "Mục không tồn tại: " + (src != null ? src.getName() : "null");
                            break;
                        }
                        File dst = resolveUnique(dstDir, src.getName());
                        // Guard: don't copy a folder into itself
                        if (src.isDirectory() && isInside(dst, src)) {
                            firstError[0] = "Không thể sao chép thư mục vào chính nó";
                            break;
                        }
                        String err = copyRecursive(src, dst, ui, cb, total, done);
                        if (err != null) {
                            firstError[0] = err;
                            break;
                        }
                    }
                    postDone(ui, cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    postDone(ui, cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    public static void moveFiles(final List<File> srcs, final File dstDir, final OpCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler ui = new Handler(Looper.getMainLooper());
                try {
                    if (srcs == null || srcs.isEmpty()) {
                        postDone(ui, cb, false, "Không có mục nào để di chuyển");
                        return;
                    }
                    if (dstDir == null) {
                        postDone(ui, cb, false, "Thư mục đích không hợp lệ");
                        return;
                    }
                    if (!dstDir.exists() && !dstDir.mkdirs()) {
                        postDone(ui, cb, false, "Không tạo được thư mục đích");
                        return;
                    }
                    final int total = countAll(srcs);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];
                    for (File src : srcs) {
                        if (src == null || !src.exists()) {
                            firstError[0] = "Mục không tồn tại: " + (src != null ? src.getName() : "null");
                            break;
                        }
                        File dst = resolveUnique(dstDir, src.getName());
                        if (dst.equals(src)) {
                            // moving to the same place: count as done
                            postProgress(ui, cb, ++done[0], total, src.getName());
                            continue;
                        }
                        if (src.isDirectory() && isInside(dst, src)) {
                            firstError[0] = "Không thể di chuyển thư mục vào chính nó";
                            break;
                        }
                        // Fast path: renameTo (same filesystem)
                        boolean renamed = src.renameTo(dst);
                        if (!renamed) {
                            // Slow path: copy then delete
                            String err = copyRecursive(src, dst, ui, cb, total, done);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            deleteRecursive(src); // best effort cleanup
                        } else {
                            // renamed: count all items of the moved tree as done
                            int cnt = countAll(java.util.Collections.singletonList(src));
                            done[0] += cnt;
                            postProgress(ui, cb, done[0], total, src.getName());
                        }
                    }
                    postDone(ui, cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    postDone(ui, cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    public static void deleteFiles(final List<File> files, final OpCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler ui = new Handler(Looper.getMainLooper());
                try {
                    if (files == null || files.isEmpty()) {
                        postDone(ui, cb, false, "Không có mục nào để xóa");
                        return;
                    }
                    final int total = countAll(files);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];
                    for (File f : files) {
                        if (f == null || !f.exists()) continue;
                        String err = deleteRecursiveWithProgress(f, ui, cb, total, done);
                        if (err != null) {
                            firstError[0] = err;
                            break;
                        }
                    }
                    postDone(ui, cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    postDone(ui, cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Public synchronous operations
    // ------------------------------------------------------------------

    public static boolean rename(File f, String newName) {
        if (f == null || newName == null) return false;
        String name = newName.trim();
        if (name.isEmpty() || name.contains("/")) return false;
        if (!f.exists()) return false;
        File parent = f.getParentFile();
        if (parent == null) return false;
        File dst = new File(parent, name);
        if (dst.exists()) return false; // don't silently overwrite
        return f.renameTo(dst);
    }

    public static boolean mkdir(File parent, String name) {
        if (parent == null || name == null) return false;
        String n = name.trim();
        if (n.isEmpty() || n.contains("/")) return false;
        File dir = new File(parent, n);
        if (dir.exists()) return false;
        return dir.mkdir();
    }

    public static boolean createFile(File parent, String name) {
        if (parent == null || name == null) return false;
        String n = name.trim();
        if (n.isEmpty() || n.contains("/")) return false;
        File f = new File(parent, n);
        if (f.exists()) return false;
        try {
            return f.createNewFile();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------------

    public static String formatSize(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes / 1024.0;
        String unit = "KB";
        if (v >= 1024) {
            v /= 1024.0;
            unit = "MB";
            if (v >= 1024) {
                v /= 1024.0;
                unit = "GB";
            }
        }
        return String.format(Locale.US, "%.1f %s", v, unit);
    }

    public static String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Copy one file or directory tree. Returns null on success, error message on failure. */
    private static String copyRecursive(File src, File dst,
                                       Handler ui, OpCallback cb, int total, int[] done) {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                return "Không tạo được thư mục: " + dst.getName();
            }
            postProgress(ui, cb, ++done[0], total, src.getName());
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    String err = copyRecursive(child, new File(dst, child.getName()), ui, cb, total, done);
                    if (err != null) return err;
                }
            }
            return null;
        }
        // single file
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            return "Sao chép thất bại: " + src.getName() + " — "
                    + (e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
        postProgress(ui, cb, ++done[0], total, src.getName());
        return null;
    }

    private static String deleteRecursiveWithProgress(File f,
                                                      Handler ui, OpCallback cb, int total, int[] done) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    String err = deleteRecursiveWithProgress(child, ui, cb, total, done);
                    if (err != null) return err;
                }
            }
        }
        if (!f.delete()) {
            return "Xóa thất bại: " + f.getName();
        }
        postProgress(ui, cb, ++done[0], total, f.getName());
        return null;
    }

    /** Best-effort recursive delete without progress (used after move-copy). */
    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }

    /** Count files + folders recursively for progress totals. */
    private static int countAll(List<File> files) {
        int count = 0;
        for (File f : files) {
            if (f == null || !f.exists()) continue;
            count++;
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    count += countAll(java.util.Arrays.asList(children));
                }
            }
        }
        return count;
    }

    /** If "name" exists in dir, return "name (1).ext", "name (2).ext", ... */
    private static File resolveUnique(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;
        int dot = name.lastIndexOf('.');
        String base;
        String ext;
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot); // includes the dot
        } else {
            base = name;
            ext = "";
        }
        int i = 1;
        do {
            candidate = new File(dir, base + " (" + i + ")" + ext);
            i++;
        } while (candidate.exists());
        return candidate;
    }

    /** True if child is inside (or equal to) ancestor by canonical-ish path. */
    private static boolean isInside(File child, File ancestor) {
        String a = ancestor.getAbsolutePath() + File.separator;
        return child.getAbsolutePath().startsWith(a);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static void postProgress(final Handler ui, final OpCallback cb,
                                     final int done, final int total, final String name) {
        if (cb == null) return;
        ui.post(new Runnable() {
            @Override
            public void run() {
                cb.onProgress(done, total, name);
            }
        });
    }

    private static void postDone(final Handler ui, final OpCallback cb,
                                 final boolean ok, final String err) {
        if (cb == null) return;
        ui.post(new Runnable() {
            @Override
            public void run() {
                cb.onDone(ok, ok ? null : err);
            }
        });
    }
}
