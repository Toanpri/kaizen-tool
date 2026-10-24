package com.kaizen.tool;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * M5 — Root filesystem browser backend.
 *
 * <p>Lists directories anywhere on the device (e.g. {@code /}, {@code /data/data})
 * using an elevated shell (Shizuku preferred, {@code su} fallback) via
 * {@link RootShell}. Entries are returned as virtual {@link FileItem}s with the
 * {@code root:} scheme so the UI can distinguish them from regular files:</p>
 * <pre>
 *   virtualPath = "root:/data/data/com.example"
 * </pre>
 * <p>These virtual paths must never be passed to {@code java.io.File} — use
 * {@link #realPath(String)} to convert back when needed.</p>
 *
 * <p>UI integration sketch (for the agent wiring MainActivity):</p>
 * <pre>
 *   // entering root mode:
 *   RootFileProvider.listDirAsync("/", (items, err) -&gt; { ... });
 *   // tap on a dir item:
 *   String next = RootFileProvider.realPath(item.path()) + "/" + item.name;
 *   // back:
 *   String parent = RootFileProvider.parentOf(RootFileProvider.realPath(item.path()));
 * </pre>
 */
public class RootFileProvider {

    /** Filesystem root. */
    public static final String ROOT = "/";

    /** Virtual path scheme prefix for root entries. */
    public static final String SCHEME = "root:";

    public interface ListCallback {
        /** items != null on success; err != null on failure. */
        void onResult(List<FileItem> items, String err);
    }

    // ------------------------------------------------------------------
    // Capability
    // ------------------------------------------------------------------

    /** True when we can browse as root (Shizuku permission or su). */
    public static boolean isAvailable() {
        return RootShell.canElevate();
    }

    // ------------------------------------------------------------------
    // Path helpers
    // ------------------------------------------------------------------

    /** True if the FileItem virtual path belongs to the root browser. */
    public static boolean isRootItem(String virtualPath) {
        return virtualPath != null && virtualPath.startsWith(SCHEME);
    }

    /** "root:/data/data" -> "/data/data". */
    public static String realPath(String virtualPath) {
        if (virtualPath == null) return ROOT;
        if (virtualPath.startsWith(SCHEME)) {
            String p = virtualPath.substring(SCHEME.length());
            return p.isEmpty() ? ROOT : p;
        }
        return virtualPath;
    }

    /** "/data/data" -> "root:/data/data". */
    public static String virtualPath(String absPath) {
        if (absPath == null || absPath.isEmpty()) absPath = ROOT;
        return SCHEME + absPath;
    }

    /** "/data/data" -> "/data". "/" -> null (already at top). */
    public static String parentOf(String absPath) {
        if (absPath == null || absPath.equals(ROOT)) return null;
        String p = absPath;
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        int i = p.lastIndexOf('/');
        if (i <= 0) return ROOT;
        return p.substring(0, i);
    }

    /** Join parent + child name into an absolute path. */
    public static String join(String parent, String name) {
        if (parent == null || parent.isEmpty()) parent = ROOT;
        if (parent.endsWith("/")) return parent + name;
        return parent + "/" + name;
    }

    /** Last segment of a path, for toolbar display. */
    public static String displayName(String absPath) {
        if (absPath == null || absPath.equals(ROOT)) return ROOT;
        String p = absPath;
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /** Blocking directory listing — call from a background thread. */
    public static List<FileItem> listDir(String absPath) throws SecurityException {
        String path = sanitize(absPath);
        // -L: dereference symlinks so the type reflects the target (one command).
        RootShell.ShellResult r = RootShell.execPreferred(
                new String[]{"ls", "-laL", path});
        if (!r.ok()) {
            String err = r.stderr.trim();
            if (err.isEmpty()) err = r.stdout.trim();
            if (err.isEmpty()) err = "Không đọc được thư mục (exit " + r.exitCode + ")";
            throw new SecurityException(err);
        }
        List<FileItem> items = parseLs(path, r.stdout);
        // v1.1-m4: đếm số mục THẬT cho các folder con (script dùng chung
        // với ShizukuFileHelper) thay vì để childCount = -1 hiện "—" mãi.
        // 24/10: bỏ đếm khi liệt kê đúng tầng /Android/data và
        // /Android/obb (hướng của Kazoi — vào data chậm chủ yếu vì
        // chặng này). Tầng sâu hơn vẫn đếm bình thường.
        if (!ShizukuFileHelper.isDataOrObbDir(path)) {
            RootShell.ShellResult cr = RootShell.execPreferred(new String[]{
                    "sh", "-c", ShizukuFileHelper.CHILD_COUNT_SCRIPT, "kz", path});
            if (cr.ok()) {
                ShizukuFileHelper.applyChildCounts(items,
                        ShizukuFileHelper.parseCounts(cr.stdout), path);
            }
        }
        return items;
    }

    /** Async listing with the result posted to the UI thread. */
    public static void listDirAsync(final String absPath, final ListCallback cb) {
        final android.os.Handler ui =
                new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<FileItem> items = null;
                String err = null;
                try {
                    items = listDir(absPath);
                } catch (SecurityException e) {
                    err = e.getMessage();
                } catch (Exception e) {
                    err = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final List<FileItem> fItems = items;
                final String fErr = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(fItems, fErr);
                    }
                });
            }
        }).start();
    }

    /** Blocking existence check. */
    public static boolean exists(String absPath) {
        RootShell.ShellResult r = RootShell.execPreferred(
                new String[]{"sh", "-c", "test -e " + RootShell.quote(sanitize(absPath))});
        return r.ok();
    }

    /** Blocking directory check. */
    public static boolean isDirectory(String absPath) {
        RootShell.ShellResult r = RootShell.execPreferred(
                new String[]{"sh", "-c", "test -d " + RootShell.quote(sanitize(absPath))});
        return r.ok();
    }

    // ------------------------------------------------------------------
    // ls -laL parsing
    // ------------------------------------------------------------------

    /**
     * Parses toybox {@code ls -laL} output. Typical line:
     * <pre>
     * drwxr-xr-x  5 root root 4096 2026-10-16 12:00 dirname
     * -rw-r--r--  1 root root  123 Oct 16 12:00 file.txt
     * </pre>
     * Unparseable lines are skipped defensively.
     */
    static List<FileItem> parseLs(String dirPath, String output) {
        List<FileItem> out = new ArrayList<FileItem>();
        if (output == null) return out;
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("total")) continue;
            // perms(0) links(1) owner(2) group(3) size(4) date(5) time(6) name(7+)
            String[] t = line.split("\\s+");
            if (t.length < 8) continue;
            String perms = t[0];
            if (perms.length() < 10) continue;
            char type = perms.charAt(0);
            if ("bcdlps-".indexOf(type) < 0) continue; // not a file listing line
            boolean isDir = type == 'd';

            // Date tokens vary: "2026-10-16 12:00" (1+1),
            // "Oct 16 12:00" (2+1) or "Oct  5 2024" (2+1 with year).
            String datePart;
            String timePart;
            int nameIdx;
            if (t.length >= 9 && t[6].matches("\\d{1,2}")
                    && (t[7].contains(":") || t[7].matches("\\d{4}"))) {
                datePart = t[5] + " " + t[6];
                timePart = t[7];
                nameIdx = 8;
            } else {
                datePart = t[5];
                timePart = t[6];
                nameIdx = 7;
            }
            if (t.length <= nameIdx) continue;

            StringBuilder nameSb = new StringBuilder();
            for (int i = nameIdx; i < t.length; i++) {
                if (i > nameIdx) nameSb.append(' ');
                nameSb.append(t[i]);
            }
            String name = nameSb.toString();
            if (name.equals(".") || name.equals("..") || name.isEmpty()) continue;

            long size = 0;
            try {
                size = Long.parseLong(t[4]);
            } catch (NumberFormatException ignored) {
                // ignore
            }
            long mtime = parseLsDate(datePart, timePart);

            String abs = join(dirPath, name);
            out.add(new FileItem(virtualPath(abs), name, isDir,
                    isDir ? 0 : size, mtime, -1 /* childCount unknown */));
        }
        return out;
    }

    /** Best-effort parse of ls date+time tokens. Returns 0 on failure. */
    static long parseLsDate(String dateTok, String timeTok) {
        String text = (dateTok + " " + timeTok).trim().replaceAll("\\s+", " ");
        // Patterns that include a year.
        String[] withYear = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
                "MMM dd yyyy",
                "MMM d yyyy",
        };
        for (String p : withYear) {
            long v = tryParse(p, text);
            if (v > 0) return v;
        }
        // Patterns without a year: assume the current year (ls omits the year
        // for recent files). If that lands in the future, go back one year.
        String[] noYear = {
                "MMM dd HH:mm",
                "MMM d HH:mm",
                "MM-dd HH:mm",
        };
        int thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        for (String p : noYear) {
            long v = tryParse(p, text);
            if (v > 0) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTimeInMillis(v);
                c.set(java.util.Calendar.YEAR, thisYear);
                long fixed = c.getTimeInMillis();
                if (fixed > System.currentTimeMillis() + 24L * 3600 * 1000) {
                    c.set(java.util.Calendar.YEAR, thisYear - 1);
                    fixed = c.getTimeInMillis();
                }
                return fixed;
            }
        }
        return 0;
    }

    private static long tryParse(String pattern, String text) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
            sdf.setLenient(false);
            Date d = sdf.parse(text);
            return d != null ? d.getTime() : 0;
        } catch (ParseException ignored) {
            return 0;
        }
    }

    /** Reject null bytes / empty paths; normalize to absolute form. */
    private static String sanitize(String path) {
        if (path == null || path.isEmpty()) return ROOT;
        if (path.indexOf('\0') >= 0) {
            throw new SecurityException("Đường dẫn không hợp lệ");
        }
        return path;
    }
}
