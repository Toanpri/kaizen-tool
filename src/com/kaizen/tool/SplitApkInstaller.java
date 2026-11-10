package com.kaizen.tool;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
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
 * được nên phải dùng PackageInstaller session: ghi tất cả APK con vào MỘT
 * session rồi commit — hệ thống tự ghép và hiện hộp thoại xác nhận cài đặt
 * như khi cài APK thường.
 *
 * v1.3-m3-test3: STREAM, không bung hết ra cache nữa. Trước đây extract()
 * bung toàn bộ APK + OBB ra cache (gói 3.9GB -> ngốn ~3.9GB cache) rồi
 * install() chép thêm ~3.9GB vào session — máy chỉ còn 6GB trống là kẹt.
 * Giờ chỉ liệt kê entry trong ZIP, bung CHỈ base.apk ra cache để đọc info
 * (icon/tên/phiên bản), lúc cài thì stream từng APK trực tiếp từ ZIP vào
 * session, OBB cũng stream thẳng sang Android/obb sau khi cài xong.
 *
 * XAPK còn có thể kèm file OBB: sau khi cài thành công thì chép OBB vào
 * Android/obb/<package>/ trên bộ nhớ trong.
 */
public class SplitApkInstaller {

    /** Một entry trong gói (APK hoặc OBB) — chỉ mô tả, chưa bung ra. */
    public static class BundleEntry {
        public String name;       // basename, duy nhất trong session / thư mục OBB
        public String entryName;  // tên entry đầy đủ trong ZIP
        public long size;         // kích thước chưa nén, -1 nếu không rõ
        public BundleEntry(String name, String entryName, long size) {
            this.name = name;
            this.entryName = entryName;
            this.size = size;
        }
    }

    /** Nội dung gói, sẵn sàng để cài — KHÔNG bung hết ra cache nữa. */
    public static class Contents {
        public File bundle;                        // file .apks/.xapk/.apkm gốc
        public File workDir;                       // thư mục tạm (chỉ chứa base.apk)
        public final List<BundleEntry> apks = new ArrayList<BundleEntry>();  // base + splits
        public final List<BundleEntry> obbs = new ArrayList<BundleEntry>();  // có thể rỗng
        public File baseApk;                       // base.apk đã bung (để hiện info)
        public String packageName;                 // có thể null nếu không đọc được
    }

    // Gói đang chờ kết quả cài đặt (giữ workDir lại để chép OBB sau khi xong).
    private static Contents sPending;

    // Session cài đặt gần nhất (để dọn khi cài lỗi/hủy — v1.3-m3-test5).
    private static int sLastSessionId = -1;

    // Dialog chờ hệ thống hiện hộp thoại xác nhận sau khi commit
    // (v1.3-m3-test7) + watchdog báo khi hệ thống im lặng quá lâu.
    private static ProgressDialog sWaitingDialog;
    private static android.os.Handler sWatchdogHandler;
    private static Runnable sWatchdog;
    private static boolean sUserCancelled;

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
        return extract(ctx, bundle, null);
    }

    /**
     * "Giải nén" gói: thực chất chỉ LIỆT KÊ các entry .apk/.obb trong ZIP và
     * bung CHỈ base.apk ra cache để đọc info. Không bung cả gói nữa nên gói
     * vài GB cũng chỉ tốn chỗ bằng base.apk. Trả null nếu lỗi hoặc không có
     * APK nào (lý do trong lastError).
     */
    public static Contents extract(Context ctx, File bundle, CancelToken cancel) {
        lastError = null;
        if (ctx == null || bundle == null || !bundle.isFile()) return null;
        File root = new File(ctx.getCacheDir(), "split-install");
        cleanupStale(root);
        File work = new File(root, String.valueOf(System.currentTimeMillis()));
        if (!work.mkdirs()) return null;

        Contents c = new Contents();
        c.bundle = bundle;
        c.workDir = work;
        String manifestJson = null;
        java.util.Set<String> seenApkNames = new java.util.HashSet<String>();
        byte[] buf = new byte[64 * 1024];
        java.util.zip.ZipFile zip = null;
        try {
            // Đọc theo mục lục trung tâm (ZipFile), KHÔNG đọc tuần tự bằng
            // ZipInputStream: gói của APKCombo ghi entry kiểu data-descriptor
            // khiến cách đọc tuần tự trích lệch byte -> APK con hỏng, hệ thống
            // báo INSTALL_PARSE_FAILED_NOT_APK (đã gặp trên máy 23/10/2026).
            zip = new java.util.zip.ZipFile(bundle);
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                if (cancel != null && cancel.isCancelled()) {
                    lastError = "Đã hủy";
                    discard(c);
                    return null;
                }
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String raw = e.getName().replace('\\', '/');
                String lower = raw.toLowerCase(Locale.US);
                String base = raw.substring(raw.lastIndexOf('/') + 1);
                if (base.isEmpty()) continue;
                if (lower.endsWith(".apk")) {
                    // Tên APK con phải duy nhất trong session, trùng thì bỏ bản sau.
                    if (!seenApkNames.add(base.toLowerCase(Locale.US))) continue;
                    c.apks.add(new BundleEntry(base, e.getName(), e.getSize()));
                } else if (lower.endsWith(".obb")) {
                    c.obbs.add(new BundleEntry(base, e.getName(), e.getSize()));
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
        // Chỉ bung base.apk ra cache để đọc info (icon/tên/phiên bản) —
        // base.apk thường chỉ vài trăm MB, rẻ hơn bung cả gói rất nhiều.
        BundleEntry baseEntry = pickBaseApk(c.apks);
        if (baseEntry == null) {
            lastError = "Không chọn được APK chính";
            discard(c);
            return null;
        }
        try {
            zip = new java.util.zip.ZipFile(bundle);
            ZipEntry ze = zip.getEntry(baseEntry.entryName);
            if (ze == null) {
                lastError = "Không tìm thấy APK chính trong gói";
                discard(c);
                return null;
            }
            File out = new File(work, baseEntry.name);
            writeEntry(zip.getInputStream(ze), out, buf, cancel);
            if (ze.getSize() >= 0 && out.length() != ze.getSize()) {
                lastError = "Trích xuất thiếu byte: " + baseEntry.name;
                discard(c);
                return null;
            }
            c.baseApk = out;
        } catch (Exception ex) {
            lastError = ex.getMessage();
            discard(c);
            return null;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Exception ignored) { }
            }
        }
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
    private static BundleEntry pickBaseApk(List<BundleEntry> apks) {
        BundleEntry largest = null;
        for (BundleEntry f : apks) {
            if (f.name.equalsIgnoreCase("base.apk")) return f;
            if (largest == null || f.size > largest.size) largest = f;
        }
        for (BundleEntry f : apks) {
            String n = f.name.toLowerCase(Locale.US);
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
    private static void writeEntry(InputStream in, File out, byte[] buf,
                                   CancelToken cancel)
            throws java.io.IOException {
        try {
            OutputStream os = new FileOutputStream(out);
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    // v1.3-m3-test2: dừng bung giữa chừng khi user hủy dialog.
                    if (cancel != null && cancel.isCancelled()) {
                        throw new java.io.IOException("Đã hủy");
                    }
                    os.write(buf, 0, n);
                }
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

        // v1.3-m3-test6: dialog "đang cài đặt" hủy được bằng vuốt back
        // (kéo hủy của điện thoại), chạm ngoài KHÔNG hủy — giống dialog
        // "đang bung" (Kazoi 10/11). Hủy thì dừng luồng stream thật qua
        // CancelToken; session đã tạo sẽ bị abandon nên không kẹt chỗ.
        final CancelToken cancelToken = new CancelToken();
        final boolean[] committed = {false};
        final ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage("Đang chuẩn bị cài đặt...");
        pd.setCancelable(true);
        pd.setCanceledOnTouchOutside(false);
        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface d) {
                if (committed[0]) {
                    // Đã commit: vuốt back = hủy cài đặt — abandon session
                    // để không kẹt staging. Kết quả về qua receiver sẽ bị
                    // lược toast (sUserCancelled) vì user đã biết mình hủy.
                    sUserCancelled = true;
                    abandonSessionQuietly(a, sLastSessionId);
                    removeWatchdog();
                    sPending = null;
                    discard(c);
                    sWaitingDialog = null;
                    Toast.makeText(a, "Đã hủy cài đặt",
                            Toast.LENGTH_SHORT).show();
                } else {
                    cancelToken.cancel();
                }
            }
        });
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                java.util.zip.ZipFile zip = null;
                try {
                    // v1.3-m3-test4: kiểm tra chỗ trống TRƯỚC khi tạo session.
                    // Cài dở giữa chừng vì hết chỗ sẽ để lại session staging
                    // (~tổng APK) mà hệ thống không nhả ngay — máy Kazoi
                    // kẹt 2,2GB sau lần hủy ở khúc "đang cài đặt" 10/11.
                    // Cần: /data chứa cả staging + bản đã cài (2x APK),
                    // bộ nhớ ngoài chứa OBB.
                    long totalApk = 0, totalObb = 0;
                    for (BundleEntry be : c.apks) if (be.size > 0) totalApk += be.size;
                    for (BundleEntry obb : c.obbs) if (obb.size > 0) totalObb += obb.size;
                    long margin = 256L * 1024 * 1024;
                    long dataNeed = 2 * totalApk + margin;
                    long dataFree = freeBytes(Environment.getDataDirectory());
                    if (dataFree >= 0 && dataFree < dataNeed) {
                        throw new java.io.IOException("Không đủ bộ nhớ trong: cần ~"
                                + FileOps.formatSize(dataNeed) + ", máy còn "
                                + FileOps.formatSize(dataFree));
                    }
                    if (totalObb > 0) {
                        long extNeed = totalObb + margin;
                        long extFree = freeBytes(
                                Environment.getExternalStorageDirectory());
                        if (extFree >= 0 && extFree < extNeed) {
                            throw new java.io.IOException(
                                    "Không đủ chỗ cho OBB: cần ~"
                                    + FileOps.formatSize(extNeed) + ", máy còn "
                                    + FileOps.formatSize(extFree));
                        }
                    }

                    PackageInstaller installer = a.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int sessionId = installer.createSession(params);
                    sLastSessionId = sessionId;
                    PackageInstaller.Session session = installer.openSession(sessionId);
                    try {
                        // v1.3-m3-test3: stream từng APK trực tiếp từ ZIP vào
                        // session, không qua file cache nữa.
                        zip = new java.util.zip.ZipFile(c.bundle);
                        byte[] buf = new byte[64 * 1024];
                        for (BundleEntry be : c.apks) {
                            ZipEntry ze = zip.getEntry(be.entryName);
                            if (ze == null) {
                                throw new java.io.IOException(
                                        "Thiếu APK trong gói: " + be.name);
                            }
                            OutputStream out = session.openWrite(
                                    be.name, 0, ze.getSize());
                            try {
                                InputStream in = zip.getInputStream(ze);
                                try {
                                    int n;
                                    while ((n = in.read(buf)) > 0) {
                                        if (cancelToken.isCancelled()) {
                                            throw new java.io.IOException("Đã hủy");
                                        }
                                        out.write(buf, 0, n);
                                    }
                                } finally {
                                    in.close();
                                }
                                session.fsync(out);
                            } finally {
                                out.close();
                            }
                        }
                        if (cancelToken.isCancelled()) {
                            throw new java.io.IOException("Đã hủy");
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
                        committed[0] = true;
                    } catch (Exception e) {
                        try { session.abandon(); } catch (Exception ignored) { }
                        throw e;
                    } finally {
                        try { session.close(); } catch (Exception ignored) { }
                        if (zip != null) {
                            try { zip.close(); } catch (Exception ignored) { }
                        }
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }

                final String err = error;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err != null) {
                            try { pd.dismiss(); } catch (Exception ignored) { }
                            sPending = null;
                            discard(c);
                            if ("Đã hủy".equals(err)) {
                                Toast.makeText(a, "Đã hủy cài đặt",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(a, "Không cài được: " + err,
                                        Toast.LENGTH_LONG).show();
                            }
                            return;
                        }
                        // Commit xong: KHÔNG tắt dialog nữa — chuyển thành
                        // trạng thái chờ hệ thống hiện hộp thoại xác nhận
                        // (v1.3-m3-test7). Trước đây tắt luôn nên nếu hệ
                        // thống không hiện hộp thoại, user chờ trong im lặng
                        // 3-5 phút mà không biết làm gì (máy Kazoi 10/11:
                        // session commit xong nhưng không có hộp thoại, không
                        // kết quả; phải tự vào Dọn session thì mới báo
                        // INSTALL_FAILED_ABORTED).
                        sWaitingDialog = pd;
                        try {
                            pd.setMessage("Đã gửi yêu cầu cài đặt — chờ hộp thoại xác nhận của hệ thống...");
                        } catch (Exception ignored) { }
                        startWatchdog(a.getApplicationContext());
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
        removeWatchdog();
        if (sWaitingDialog != null) {
            try { sWaitingDialog.dismiss(); } catch (Exception ignored) { }
            sWaitingDialog = null;
        }
        final Contents c = sPending;
        sPending = null;
        final int sid = sLastSessionId;
        sLastSessionId = -1;
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
            // User tự vuốt back hủy sau khi commit: đã toast "Đã hủy cài
            // đặt" ở listener rồi, không toast thất bại nữa.
            if (sUserCancelled) {
                sUserCancelled = false;
            } else {
                String detail = friendlyError(status);
                if (sysMsg != null && !sysMsg.trim().isEmpty()) {
                    String m = sysMsg.trim();
                    if (m.length() > 220) m = m.substring(0, 220) + "...";
                    detail += " — " + m;
                }
                Toast.makeText(ctx, "Cài đặt thất bại: " + detail,
                        Toast.LENGTH_LONG).show();
            }
            // v1.3-m3-test5: cài lỗi/hủy thì thử hủy session luôn — không thì
            // staging (~tổng APK) kẹt lại ở mục "Khác" (máy Kazoi kẹt 1,6GB
            // sau lần hủy 10/11, khởi động lại cũng không nhả).
            abandonSessionQuietly(ctx, sid);
            if (c != null) discard(c);
        }
    }

    /**
     * Watchdog sau commit (v1.3-m3-test7): nếu 60s mà hệ thống vẫn chưa
     * trả kết quả (hộp thoại xác nhận không hiện), báo user biết để hủy
     * rồi thử lại thay vì chờ trong im lặng.
     */
    private static void startWatchdog(final Context appCtx) {
        removeWatchdog();
        sWatchdogHandler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        sWatchdog = new Runnable() {
            @Override public void run() {
                if (sPending != null) {
                    Toast.makeText(appCtx,
                            "Hệ thống chưa hiện hộp thoại xác nhận. Bấm quay lại để hủy rồi thử lại, hoặc vào Cài đặt → Dọn session.",
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        sWatchdogHandler.postDelayed(sWatchdog, 60 * 1000);
    }

    private static void removeWatchdog() {
        if (sWatchdogHandler != null && sWatchdog != null) {
            sWatchdogHandler.removeCallbacks(sWatchdog);
        }
        sWatchdogHandler = null;
        sWatchdog = null;
    }

    /** Hủy một session cài đặt (best-effort, không throw). */
    private static void abandonSessionQuietly(Context ctx, int sessionId) {
        if (ctx == null || sessionId < 0) return;
        try {
            ctx.getPackageManager().getPackageInstaller()
                    .abandonSession(sessionId);
        } catch (Exception ignored) { }
    }

    /**
     * Dọn các session cài đặt cũ của chính app này (v1.3-m3-test5).
     * Chỉ gọi khi không có gói nào đang cài (người dùng bấm nút trong
     * Cài đặt). Trả về thông báo kết quả để hiện toast.
     */
    public static String cleanupOrphanedSessions(Context ctx) {
        if (ctx == null) return "Không dọn được";
        int found = 0, done = 0;
        try {
            PackageInstaller installer =
                    ctx.getPackageManager().getPackageInstaller();
            java.util.List<PackageInstaller.SessionInfo> sessions =
                    installer.getMySessions();
            if (sessions != null) {
                found = sessions.size();
                for (PackageInstaller.SessionInfo si : sessions) {
                    try {
                        installer.abandonSession(si.getSessionId());
                        done++;
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception e) {
            return "Lỗi khi liệt kê session: " + e.getMessage();
        }
        if (found == 0) return "Không có session cài đặt cũ";
        if (done == 0) {
            return "Tìm thấy " + found + " session nhưng không hủy được";
        }
        return "Đã dọn " + done + "/" + found + " session cài đặt cũ";
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

    /** Chép các file OBB vào Android/obb/<package>/ trên bộ nhớ trong.
     * v1.3-m3-test3: stream trực tiếp từ ZIP, không qua file cache nữa. */
    private static boolean copyObbs(Contents c) {
        java.util.zip.ZipFile zip = null;
        try {
            File obbDir = new File(Environment.getExternalStorageDirectory(),
                    "Android/obb/" + c.packageName);
            if (!obbDir.exists() && !obbDir.mkdirs()) return false;
            zip = new java.util.zip.ZipFile(c.bundle);
            byte[] buf = new byte[64 * 1024];
            for (BundleEntry obb : c.obbs) {
                ZipEntry ze = zip.getEntry(obb.entryName);
                if (ze == null) return false;
                File out = new File(obbDir, obb.name);
                // v1.3-m3-test4: chép dở mà lỗi thì xóa file OBB dở ngay,
                // không để nó ngốn chỗ (gói to, OBB vài GB).
                boolean okOne = false;
                InputStream in = null;
                OutputStream os = null;
                try {
                    in = zip.getInputStream(ze);
                    os = new FileOutputStream(out);
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    os.close();
                    os = null;
                    okOne = true;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (os != null) {
                        try { os.close(); } catch (Exception ignored) { }
                    }
                    if (in != null) {
                        try { in.close(); } catch (Exception ignored) { }
                    }
                    if (!okOne) out.delete();
                }
                if (!okOne) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Exception ignored) { }
            }
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

    /** Chỗ trống còn lại (byte) của một phân vùng; -1 nếu không đọc được. */
    private static long freeBytes(File path) {
        try {
            android.os.StatFs sf = new android.os.StatFs(path.getAbsolutePath());
            return sf.getAvailableBytes();
        } catch (Exception e) {
            return -1;
        }
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
