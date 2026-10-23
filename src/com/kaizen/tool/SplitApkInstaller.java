package com.kaizen.tool;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;

/**
 * Cài đặt gói APK tách (split APK): .apks / .xapk / .apkm.
 *
 * Các file này là ZIP chứa nhiều APK con (base + các config split theo
 * ngôn ngữ/màn hình/kiến trúc máy). System installer không cài trực tiếp
 * được nên phải dùng PackageInstaller session: giải nén lấy các APK con,
 * ghi tất cả vào MỘT session rồi commit — hệ thống tự ghép và hiện hộp
 * thoại xác nhận cài đặt như khi cài APK thường.
 *
 * XAPK còn có thể kèm file OBB: sau khi cài thành công thì chép OBB vào
 * Android/obb/<package>/ trên bộ nhớ trong.
 */
public class SplitApkInstaller {

    /** Nội dung đã giải nén của một gói, sẵn sàng để cài. */
    public static class Contents {
        public File workDir;                       // thư mục tạm chứa mọi thứ bên dưới
        public final List<File> apks = new ArrayList<File>();  // base + splits
        public final List<File> obbs = new ArrayList<File>();  // có thể rỗng
        public File baseApk;                       // APK chính (để hiện info)
        public String packageName;                 // có thể null nếu không đọc được
    }

    // Gói đang chờ kết quả cài đặt (giữ workDir lại để chép OBB sau khi xong).
    private static Contents sPending;

    /** Lý do extract() thất bại gần nhất (để dialog báo cho user); null nếu không có. */
    public static String lastError;

    public static boolean isBundleName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm");
    }

    public static boolean isBundle(File f) {
        return f != null && isBundleName(f.getName());
    }

    // ------------------------------------------------------------------
    // Giải nén
    // ------------------------------------------------------------------

    /**
     * Giải nén gói vào cache (split-install/<timestamp>/). Chỉ lấy các entry
     * .apk, .obb và manifest.json (của XAPK, để biết package name); tên file
     * được rút về basename nên entry độc hại chứa "../" cũng không thoát
     * ra ngoài thư mục tạm được. Trả null nếu lỗi hoặc không có APK nào.
     */
    public static Contents extract(Context ctx, File bundle) {
        lastError = null;
        if (ctx == null || bundle == null || !bundle.isFile()) return null;
        File root = new File(ctx.getCacheDir(), "split-install");
        cleanupStale(root);
        File work = new File(root, String.valueOf(System.currentTimeMillis()));
        if (!work.mkdirs()) return null;

        Contents c = new Contents();
        c.workDir = work;
        String manifestJson = null;
        java.util.Set<String> seenApkNames = new java.util.HashSet<String>();
        java.util.zip.ZipFile zip = null;
        try {
            // Đọc theo mục lục trung tâm (ZipFile), KHÔNG đọc tuần tự bằng
            // ZipInputStream: gói của APKCombo ghi entry kiểu data-descriptor
            // khiến cách đọc tuần tự trích lệch byte -> APK con hỏng, hệ thống
            // báo INSTALL_PARSE_FAILED_NOT_APK (đã gặp trên máy 23/10/2026).
            zip = new java.util.zip.ZipFile(bundle);
            byte[] buf = new byte[64 * 1024];
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String raw = e.getName().replace('\\', '/');
                String lower = raw.toLowerCase(Locale.US);
                String base = raw.substring(raw.lastIndexOf('/') + 1);
                if (base.isEmpty()) continue;
                if (lower.endsWith(".apk")) {
                    // Tên APK con phải duy nhất trong session, trùng thì bỏ bản sau.
                    if (!seenApkNames.add(base.toLowerCase(Locale.US))) continue;
                    File out = new File(work, base);
                    writeEntry(zip.getInputStream(e), out, buf);
                    if (e.getSize() >= 0 && out.length() != e.getSize()) {
                        lastError = "Trích xuất thiếu byte: " + base;
                        discard(c);
                        return null;
                    }
                    c.apks.add(out);
                } else if (lower.endsWith(".obb")) {
                    File odir = new File(work, "obb");
                    if (!odir.exists()) odir.mkdirs();
                    File out = new File(odir, base);
                    writeEntry(zip.getInputStream(e), out, buf);
                    c.obbs.add(out);
                } else if (lower.endsWith("manifest.json") && manifestJson == null) {
                    manifestJson = readEntry(zip.getInputStream(e), buf, 1 << 20);
                }
            }
        } catch (Exception ex) {
            lastError = ex.getMessage();
            discard(c);
            return null;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Exception ignored) { }
            }
        }

        if (c.apks.isEmpty()) {
            lastError = "Không có APK nào trong gói";
            discard(c);
            return null;
        }
        c.baseApk = pickBaseApk(c.apks);
        // Chỉ APK chính mới bắt buộc parse được tại đây (chốt chặn file hỏng
        // do trích xuất). Split con thì để hệ thống phán quyết khi cài:
        // getPackageArchiveInfo khắt khe hơn trình cài thật với một số split
        // cấu hình (đã gặp config.armeabi_v7a của APKCombo, 23/10/2026).
        if (c.baseApk != null && ApkUtils.getApkInfo(ctx, c.baseApk) == null) {
            lastError = "APK chính không đọc được: " + c.baseApk.getName();
            discard(c);
            return null;
        }
        c.packageName = parsePackageName(manifestJson);
        if (c.packageName == null && c.baseApk != null) {
            ApkUtils.ApkInfo info = ApkUtils.getApkInfo(ctx, c.baseApk);
            if (info != null) c.packageName = info.packageName;
        }
        return c;
    }

    /** APK chính = base.apk nếu có; không thì file không phải config/split; cuối cùng là file to nhất. */
    private static File pickBaseApk(List<File> apks) {
        File largest = null;
        for (File f : apks) {
            if (f.getName().equalsIgnoreCase("base.apk")) return f;
            if (largest == null || f.length() > largest.length()) largest = f;
        }
        for (File f : apks) {
            String n = f.getName().toLowerCase(Locale.US);
            if (!n.contains("config.") && !n.startsWith("split")) return f;
        }
        return largest;
    }

    /** XAPK manifest.json có trường "package_name" — trích bằng quét chuỗi đơn giản. */
    private static String parsePackageName(String json) {
        if (json == null) return null;
        int i = json.indexOf("\"package_name\"");
        if (i < 0) return null;
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 <= q1) return null;
        return json.substring(q1 + 1, q2);
    }

    /** Ghi một entry ra file; đóng cả stream đầu vào khi xong. */
    private static void writeEntry(InputStream in, File out, byte[] buf)
            throws java.io.IOException {
        try {
            OutputStream os = new FileOutputStream(out);
            try {
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } finally {
                os.close();
            }
        } finally {
            in.close();
        }
    }

    private static String readEntry(InputStream in, byte[] buf, int cap)
            throws java.io.IOException {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > cap) break;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    // ------------------------------------------------------------------
    // Cài đặt qua PackageInstaller session
    // ------------------------------------------------------------------

    /**
     * Cài gói đã giải nén. Nhận quyền sở hữu Contents: dù thành công hay
     * thất bại, workDir cũng sẽ bị dọn khi quá trình kết thúc (qua receiver).
     */
    public static void install(final Activity a, final Contents c) {
        if (a == null || c == null) return;
        if (sPending != null) {
            Toast.makeText(a, "Đang cài một gói khác, đợi chút đã",
                    Toast.LENGTH_SHORT).show();
            discard(c);
            return;
        }
        if (!ApkUtils.ensureInstallPermission(a)) {
            // Đã mở Settings cấp quyền; user cấp xong thì bấm cài lại.
            discard(c);
            return;
        }
        sPending = c;

        final ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage("Đang chuẩn bị cài đặt...");
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    PackageInstaller installer = a.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int sessionId = installer.createSession(params);
                    PackageInstaller.Session session = installer.openSession(sessionId);
                    try {
                        byte[] buf = new byte[64 * 1024];
                        for (File f : c.apks) {
                            OutputStream out = session.openWrite(f.getName(), 0, f.length());
                            try {
                                InputStream in = new FileInputStream(f);
                                try {
                                    int n;
                                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                                } finally {
                                    in.close();
                                }
                                session.fsync(out);
                            } finally {
                                out.close();
                            }
                        }
                        Intent intent = new Intent(InstallResultReceiver.ACTION);
                        intent.setPackage(a.getPackageName());
                        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            flags |= PendingIntent.FLAG_MUTABLE;
                        }
                        PendingIntent pi = PendingIntent.getBroadcast(
                                a, sessionId, intent, flags);
                        session.commit(pi.getIntentSender());
                    } catch (Exception e) {
                        try { session.abandon(); } catch (Exception ignored) { }
                        throw e;
                    } finally {
                        try { session.close(); } catch (Exception ignored) { }
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }

                final String err = error;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try { pd.dismiss(); } catch (Exception ignored) { }
                        if (err != null) {
                            sPending = null;
                            discard(c);
                            Toast.makeText(a, "Không cài được: " + err,
                                    Toast.LENGTH_LONG).show();
                        }
                        // Thành công: hộp thoại xác nhận của hệ thống sẽ hiện,
                        // kết quả cuối về qua InstallResultReceiver.
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Kết quả cài đặt (InstallResultReceiver gọi vào)
    // ------------------------------------------------------------------

    static void onInstallResult(final Context ctx, int status, String sysMsg) {
        final Contents c = sPending;
        sPending = null;
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(ctx, "Cài đặt thành công", Toast.LENGTH_SHORT).show();
            if (c == null) return;
            if (!c.obbs.isEmpty() && c.packageName != null) {
                // Chép OBB nền rồi mới dọn thư mục tạm.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = copyObbs(c);
                        discard(c);
                        if (ok) {
                            toastOnMain(ctx, "Đã chép OBB cho " + c.packageName);
                        } else {
                            toastOnMain(ctx, "Cài xong nhưng chép OBB thất bại");
                        }
                    }
                }).start();
            } else {
                if (!c.obbs.isEmpty()) {
                    Toast.makeText(ctx,
                            "Lưu ý: gói có OBB nhưng không rõ package, chưa chép OBB",
                            Toast.LENGTH_LONG).show();
                }
                discard(c);
            }
        } else {
            String detail = friendlyError(status);
            if (sysMsg != null && !sysMsg.trim().isEmpty()) {
                String m = sysMsg.trim();
                if (m.length() > 220) m = m.substring(0, 220) + "...";
                detail += " — " + m;
            }
            Toast.makeText(ctx, "Cài đặt thất bại: " + detail,
                    Toast.LENGTH_LONG).show();
            if (c != null) discard(c);
        }
    }

    private static String friendlyError(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "đã hủy";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "bị hệ thống chặn";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "xung đột với bản đã cài (thường do khác chữ ký)";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "không tương thích với máy";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "gói không hợp lệ";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "không đủ bộ nhớ";
            default:
                return "mã " + status;
        }
    }

    /** Chép các file OBB vào Android/obb/<package>/ trên bộ nhớ trong. */
    private static boolean copyObbs(Contents c) {
        try {
            File obbDir = new File(Environment.getExternalStorageDirectory(),
                    "Android/obb/" + c.packageName);
            if (!obbDir.exists() && !obbDir.mkdirs()) return false;
            byte[] buf = new byte[64 * 1024];
            for (File obb : c.obbs) {
                File out = new File(obbDir, obb.getName());
                InputStream in = new FileInputStream(obb);
                try {
                    OutputStream os = new FileOutputStream(out);
                    try {
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    } finally {
                        os.close();
                    }
                } finally {
                    in.close();
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void toastOnMain(final Context ctx, final String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Dọn dẹp
    // ------------------------------------------------------------------

    /** Xóa thư mục tạm của một gói đã giải nén (gọi khi hủy/kết thúc). */
    public static void discard(Contents c) {
        if (c != null && c.workDir != null) deleteRecursive(c.workDir);
    }

    private static void cleanupStale(File root) {
        try {
            File[] dirs = root.listFiles();
            if (dirs == null) return;
            long now = System.currentTimeMillis();
            for (File d : dirs) {
                try {
                    long t = Long.parseLong(d.getName());
                    if (now - t > 60 * 60 * 1000L) deleteRecursive(d);
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
