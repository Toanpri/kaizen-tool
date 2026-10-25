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
     *
     * v1.1-m3 fix: phân biệt thành công/thất bại bằng EXIT CODE, không phải
     * bằng "output có rỗng không". Các lệnh như rm/mkdir/touch/cp/mv khi
     * thành công thì stdout RỖNG là bình thường — trước đây bị nhầm thành
     * "ERROR: empty output" (false positive, vd: toast lỗi khi xóa file
     * qua Shizuku dù file đã xóa thành công).
     *
     * Quy ước: chuỗi bắt đầu bằng "ERROR:" = thất bại (sLastError-safe);
     * ngược lại = stdout nguyên xi (kể cả rỗng = thành công).
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
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                String stderr = readFully(proc.getErrorStream());
                if (stderr.length() > 200) stderr = stderr.substring(0, 200);
                String msg = "ERROR: exit " + exitCode;
                String trimmed = stderr.trim();
                if (!trimmed.isEmpty()) msg += ": " + trimmed;
                return msg;
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (proc != null) {
                try { proc.destroy(); } catch (Exception ignored) {}
            }
        }
    }

    /** Đọc toàn bộ stream thành String (null-safe). */
    private static String readFully(InputStream is) {
        if (is == null) return "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 24/10: true CHỈ khi path chính là /Android/data hoặc /Android/obb
     * (đúng 1 tầng đó, không tính cây con bên trong). Sửa 19:17 cùng ngày:
     * bản đầu dùng contains() nên bỏ đếm cả cây con — sai, vì folder
     * trong folder trong data vẫn cần đếm (vd: files/audio, files/config).
     * Chuẩn hóa trailing slash trước khi so sánh.
     */
    public static boolean isDataOrObbDir(String path) {
        if (path == null) return false;
        String p = path;
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p.endsWith("/Android/data") || p.endsWith("/Android/obb");
    }

    /**
     * Liệt kê file trong thư mục qua `ls -la`.
     * Trả về list FileItem, hoặc null nếu thất bại.
     */
    public static List<FileItem> listFiles(String path) {
        // v1.2-m1: thử lại tối đa 3 lần khi ls lỗi. Lỗi kiểu "Transport
        // endpoint is not connected" (Shizuku/FUSE chập chờn, hay gặp
        // ngay sau thao tác nặng như dán file lớn) thường tự hết sau
        // vài trăm ms — trước đây lỗi 1 lần là danh sách trắng luôn,
        // trông như thư mục bị xóa sạch.
        String output = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            output = runCommand("ls", "-la", path);
            if (output != null && !output.startsWith("ERROR:")) break;
            if (attempt < 2) {
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
        }
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
        // 24/10: KHÔNG đếm con khi liệt kê đúng tầng /Android/data và
        // /Android/obb — các folder app trong data không cần số mục.
        // Đây chính là chặng chậm nhất khi vào data (script chạy ls cho
        // từng folder con, data có hàng trăm folder app). Tầng sâu hơn
        // (vd: data/<pkg>/files) vẫn đếm bình thường.
        if (!isDataOrObbDir(path)) {
            applyChildCounts(result, countChildren(path), path);
        }
        return result;
    }

    /**
     * v1.1-m4: script đếm số mục con của MỌI folder con trong $1, in ra
     * mỗi dòng "số<TAB>tên". Dùng chung cho cả ShizukuFileHelper và
     * RootFileProvider. Đếm cả file ẩn (ls -A) cho khớp cách đếm của
     * java.io.File.listFiles() ở đường liệt kê thường.
     */
    static final String CHILD_COUNT_SCRIPT =
            "for e in \"$1\"/* \"$1\"/.[!.]*; do "
            + "[ -d \"$e\" ] || continue; "
            + "n=$(ls -1A \"$e\" 2>/dev/null | wc -l); "
            + "printf '%s\\t%s\\n' \"$n\" \"${e##*/}\"; "
            + "done";

    /** Parse output của CHILD_COUNT_SCRIPT thành map tên -> số mục. */
    static java.util.Map<String, Integer> parseCounts(String out) {
        java.util.Map<String, Integer> map =
                new java.util.HashMap<String, Integer>();
        if (out == null) return map;
        for (String line : out.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                int n = Integer.parseInt(line.substring(0, tab).trim());
                String name = line.substring(tab + 1).trim();
                if (!name.isEmpty()) map.put(name, n);
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    /** Chạy CHILD_COUNT_SCRIPT cho parentPath qua Shizuku. */
    private static java.util.Map<String, Integer> countChildren(String parentPath) {
        String out = runCommand("sh", "-c", CHILD_COUNT_SCRIPT, "kz", parentPath);
        if (out == null || out.startsWith("ERROR:")) return null;
        return parseCounts(out);
    }

    /**
     * Thay các FileItem folder bằng bản có childCount thật (nếu đếm được).
     * 24/10: khi parent là .../Android thì bỏ qua 2 mục data/obb — bản
     * thân folder data/obb cũng không cần đếm (hướng của Kazoi).
     */
    static void applyChildCounts(List<FileItem> items,
                                 java.util.Map<String, Integer> counts,
                                 String parentPath) {
        if (items == null || counts == null || counts.isEmpty()) return;
        boolean parentIsAndroid = false;
        if (parentPath != null) {
            String p = parentPath;
            while (p.endsWith("/") && p.length() > 1) {
                p = p.substring(0, p.length() - 1);
            }
            parentIsAndroid = p.endsWith("/Android");
        }
        for (int i = 0; i < items.size(); i++) {
            FileItem it = items.get(i);
            if (!it.isDir) continue;
            if (parentIsAndroid
                    && (it.name.equals("data") || it.name.equals("obb"))) continue;
            Integer c = counts.get(it.name);
            if (c == null) continue;
            FileItem ni = new FileItem(it.file.getAbsolutePath(), it.name,
                    true, 0, it.lastModified, c.intValue());
            ni.viaShizuku = it.viaShizuku;
            ni.packageName = it.packageName;
            ni.appLabel = it.appLabel;
            ni.perms = it.perms;
            items.set(i, ni);
        }
    }

    /**
     * Parse một dòng `ls -la` thành FileItem.
     * Dòng mẫu: "-rw-r--r-- 1 root root 1234 2024-01-15 10:30 filename.txt"
     */
    private static FileItem parseLsLine(String line, String parentPath) {
        // Hỗ trợ 2 format date của ls (cùng layout với RootFileProvider):
        // - ISO: perms(0) links(1) owner(2) group(3) size(4) YYYY-MM-DD(5) HH:MM(6) name(7+)
        // - Truyền thống: perms(0) links(1) owner(2) group(3) size(4) Mon(5) day(6) HH:MM|YYYY(7) name(8+)
        String[] parts = line.split("\\s+");
        if (parts.length < 8) return null;

        String perms = parts[0];
        String name;
        String sizeStr = parts[4];
        String datePart;
        String timePart;
        int nameIdx;
        if (parts.length >= 9 && parts[6].matches("\\d{1,2}")
                && (parts[7].contains(":") || parts[7].matches("\\d{4}"))) {
            datePart = parts[5] + " " + parts[6];
            timePart = parts[7];
            nameIdx = 8;
        } else {
            datePart = parts[5];
            timePart = parts[6];
            nameIdx = 7;
        }
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

        // v1.1-m4 fix: TRƯỚC ĐÂY lastModified bị hardcode 0 (mọi file qua
        // Shizuku đều hiện 01/01/1970) và childCount hardcode 0 (mọi folder
        // đều hiện "Trống"). Giờ parse ngày thật bằng parser dùng chung với
        // RootFileProvider; childCount = -1 (chưa rõ — ls của thư mục cha
        // không cho biết, hiện "—" thay vì nói dối là trống).
        long mtime = RootFileProvider.parseLsDate(datePart, timePart);

        java.io.File f = new java.io.File(parentPath, name);
        String fullPath = f.getAbsolutePath();
        // Dùng constructor virtual (không chạm filesystem thật)
        // v1.2-m4: giữ lại chuỗi quyền từ ls để dialog Thuộc tính dùng
        FileItem it = new FileItem(fullPath, name, isDir, size, mtime, -1);
        it.perms = perms;
        return it;
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
