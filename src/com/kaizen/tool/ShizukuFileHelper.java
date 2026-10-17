package com.kaizen.tool;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * ShizukuFileHelper — liệt kê file qua Shizuku cho các thư mục
 * bị hạn chế (ví dụ: /Android/data trên Android 11+).
 *
 * Dùng `ls -la` qua Shizuku newProcess rồi parse output.
 */
public class ShizukuFileHelper {

    private static final String TAG = "ShizukuFileHelper";
    /** Lỗi cuối cùng (để UI hiển thị diagnostic) */
    private static String sLastError = null;
    public static String getLastError() { return sLastError; }

    /**
     * Chạy lệnh shell qua Shizuku và trả về output.
     */
    public static String runCommand(String... cmd) {
        if (!ShizukuHelper.isRunning()) {
            return "ERROR: Shizuku not running (binder null)";
        }
        ShizukuHelper.ShizukuProcess proc = null;
        try {
            proc = ShizukuHelper.newProcess(cmd);
            if (proc == null) {
                String err = ShizukuHelper.sLastError;
                return "ERROR: newProcess returned null"
                        + (err != null ? " — " + err : " (no details)");
            }
            InputStream is = proc.getInputStream();
            if (is == null) {
                return "ERROR: null input stream";
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            proc.waitFor();
            String result = sb.toString();
            return result.isEmpty() ? "ERROR: empty output" : result;
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (proc != null) {
                try { proc.destroy(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Liệt kê file trong thư mục qua `ls -la`.
     * Trả về list FileItem, hoặc null nếu thất bại.
     */
    public static List<FileItem> listFiles(String path) {
        String output = runCommand("ls", "-la", path);
        if (output == null) {
            sLastError = "runCommand returned null";
            return null;
        }
        if (output.startsWith("ERROR:")) {
            sLastError = output;
            return null;
        }
        sLastError = null;

        List<FileItem> result = new ArrayList<FileItem>();
        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // Bỏ qua dòng "total ..."
            if (line.startsWith("total")) continue;

            // Format: drwxr-xr-x 2 root root 4096 2024-01-01 12:00 dirname
            // Tách: permissions, links, owner, group, size, date..., name
            // Tên file là phần sau cùng (có thể chứa khoảng trắng)
            try {
                FileItem item = parseLsLine(line, path);
                if (item != null) {
                    // Đánh dấu là qua Shizuku để biết cần copy ra cache trước khi mở
                    item.viaShizuku = true;
                    result.add(item);
                }
            } catch (Exception e) {
                Log.w(TAG, "parse failed for: " + line);
            }
        }
        return result;
    }

    /**
     * Parse một dòng `ls -la` thành FileItem.
     * Dòng mẫu: "-rw-r--r-- 1 root root 1234 2024-01-15 10:30 filename.txt"
     */
    private static FileItem parseLsLine(String line, String parentPath) {
        // Hỗ trợ 2 format date của ls:
        // - ISO: perms(0) links(1) owner(2) group(3) size(4) YYYY-MM-DD(5) HH:MM(6) name(7+)
        // - Truyền thống: perms(0) links(1) owner(2) group(3) size(4) Mon(5) day(6) time(7) name(8+)
        String[] parts = line.split("\\s+");
        if (parts.length < 8) return null;

        String perms = parts[0];
        String name;
        String sizeStr = parts[4];
        // Detect ISO date: parts[5] dạng YYYY-MM-DD
        boolean isIso = parts[5].matches("\\d{4}-\\d{2}-\\d{2}");
        int nameIdx = isIso ? 7 : 8;
        if (parts.length <= nameIdx) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = nameIdx; i < parts.length; i++) {
            if (i > nameIdx) sb.append(' ');
            sb.append(parts[i]);
        }
        name = sb.toString().trim();
        // Bỏ qua . và ..
        if (name.equals(".") || name.equals("..")) return null;

        boolean isDir = perms.charAt(0) == 'd';
        boolean isLink = perms.charAt(0) == 'l';
        // Nếu là symlink, tên có dạng "link -> target"
        int arrow = name.indexOf(" -> ");
        if (arrow > 0) name = name.substring(0, arrow);

        long size = 0;
        try { size = Long.parseLong(sizeStr); } catch (Exception ignored) {}

        java.io.File f = new java.io.File(parentPath, name);
        String fullPath = f.getAbsolutePath();
        // Dùng constructor virtual (không chạm filesystem thật)
        return new FileItem(fullPath, name, isDir, size, 0, 0);
    }

    /**
     * v1.1-m1: Nếu parentPath là /Android/data hoặc /Android/obb,
     * tra PackageManager để lấy tên app + package cho từng folder.
     * Hiển thị giống MT Manager: tên package + tên app + icon app.
     */
    public static void enrichWithAppInfo(android.content.Context ctx,
                                       List<FileItem> items, String parentPath) {
        if (ctx == null || items == null || parentPath == null) return;
        // Chỉ enrich cho /Android/data và /Android/obb
        boolean isData = parentPath.endsWith("/Android/data")
                || parentPath.endsWith("/Android/data/");
        boolean isObb = parentPath.endsWith("/Android/obb")
                || parentPath.endsWith("/Android/obb/");
        if (!isData && !isObb) return;

        android.content.pm.PackageManager pm = ctx.getPackageManager();
        for (FileItem item : items) {
            if (!item.isDir) continue;
            try {
                android.content.pm.ApplicationInfo ai =
                        pm.getApplicationInfo(item.name, 0);
                item.packageName = item.name;
                CharSequence label = pm.getApplicationLabel(ai);
                item.appLabel = label != null ? label.toString() : item.name;
            } catch (Exception ignored) {
                // Không phải package đã cài (folder lạ) -> giữ nguyên
            }
        }
    }

    /**
     * v1.1-m1: Copy file từ vị trí Shizuku-only ra cache của app để mở được.
     * Dùng `cat src > dst` qua Shizuku. Trả về File trong cache, hoặc null nếu thất bại.
     */
    public static java.io.File copyToCache(android.content.Context ctx,
                                          String srcPath, String fileName) {
        if (!ShizukuHelper.isRunning()) return null;
        try {
            java.io.File cacheDir = new java.io.File(ctx.getCacheDir(), "shizuku");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            // Tên file an toàn (tránh path traversal)
            String safeName = new java.io.File(fileName).getName();
            java.io.File dst = new java.io.File(cacheDir, safeName);

            // Dùng cat qua Shizuku để đọc, ghi ra file trong app
            ShizukuHelper.ShizukuProcess proc =
                    ShizukuHelper.newProcess(new String[]{"cat", srcPath}, null, null);
            if (proc == null) {
                sLastError = "copyToCache: newProcess null";
                return null;
            }
            java.io.InputStream is = proc.getInputStream();
            if (is == null) {
                sLastError = "copyToCache: null input stream";
                return null;
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            is.close();
            proc.waitFor();
            proc.destroy();
            sLastError = null;
            return dst;
        } catch (Exception e) {
            sLastError = "copyToCache: " + e.getMessage();
            Log.w(TAG, "copyToCache failed", e);
            return null;
        }
    }

    /**
     * v1.1-m1: Xóa file/folder qua Shizuku (rm -rf).
     */
    public static boolean delete(String path) {
        String out = runCommand("rm", "-rf", path);
        if (out != null && out.startsWith("ERROR:")) {
            sLastError = out;
            return false;
        }
        // Kiểm tra còn tồn tại không
        String check = runCommand("ls", "-ld", path);
        boolean gone = check != null && check.startsWith("ERROR:");
        if (!gone) sLastError = "Vẫn còn tồn tại sau khi xóa";
        return gone;
    }

    /**
     * v1.1-m1: Copy qua Shizuku (cp -r). Trả về true nếu thành công.
     */
    public static boolean copy(String src, String dst) {
        String out = runCommand("cp", "-r", src, dst);
        if (out != null && out.startsWith("ERROR:")) {
            sLastError = out;
            return false;
        }
        sLastError = null;
        return true;
    }

    /**
     * v1.1-m1: Di chuyển qua Shizuku (mv). Trả về true nếu thành công.
     */
    public static boolean move(String src, String dst) {
        String out = runCommand("mv", src, dst);
        if (out != null && out.startsWith("ERROR:")) {
            sLastError = out;
            return false;
        }
        sLastError = null;
        return true;
    }

    /**
     * v1.1-m1: Tạo thư mục qua Shizuku (mkdir -p).
     */
    public static boolean mkdir(String path) {
        String out = runCommand("mkdir", "-p", path);
        if (out != null && out.startsWith("ERROR:")) {
            sLastError = out;
            return false;
        }
        sLastError = null;
        return true;
    }

    /**
     * Kiểm tra thư mục có cần Shizuku không (listFiles thường trả null).
     */
    public static boolean needsShizuku(String path) {
        java.io.File f = new java.io.File(path);
        // Nếu File API đọc được thì không cần Shizuku
        String[] list = f.list();
        if (list != null) return false;
        // Không đọc được + Shizuku đang chạy -> thử Shizuku
        return ShizukuHelper.isRunning() && ShizukuHelper.hasPermission();
    }
}
