package com.kaizen.tool;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.1-m3 — Nén siêu tốc bằng binary 7zz native (tự compile từ source 7-Zip
 * bằng Android NDK, giống cách ZArchiver làm).
 *
 * Vì sao nhanh hơn Java:
 *  - 7zz dùng thuật toán native đã tối ưu + bật đa luồng (-mmt=on):
 *    dùng hết các core CPU của máy (Java LZMA2 của commons-compress
 *    chỉ chạy đơn luồng).
 *  - ZIP cũng đi qua 7zz (-tzip -mmt) thay vì java.util.zip.
 *
 * An toàn:
 *  - Mọi mức nén (Lưu trữ/Nhanh/Bình thường/Tối đa) đều được map sang
 *    tham số -mx tương đương của 7-Zip -> chất lượng nén giữ nguyên,
 *    chỉ khác tốc độ.
 *  - Nếu binary không khả dụng (ABI lạ, copy/chmod thất bại, ...) ->
 *    trả về false để caller fallback sang implementation Java cũ.
 *    Không bao giờ "chết cứng": máy nào cũng nén được.
 */
public class Native7z {

    public static final int FMT_ZIP = 0;
    public static final int FMT_7Z = 1;

    private static final String ASSET_DIR = "native7z";
    private static final Pattern PCT_PATTERN = Pattern.compile("(\\d{1,3})%");

    // Cache kết quả pre-flight check (theo process).
    private static Boolean sAvailable = null;
    private static File sBinary = null;

    // ------------------------------------------------------------------
    // Rename / delete entries inside archive (7zz rn / 7zz d)
    // ------------------------------------------------------------------

    /**
     * Đổi tên 1 entry trong archive: 7zz rn.
     * Chỉ dùng cho ZIP/7Z (RAR/TAR: caller tự chặn).
     * @return true nếu native đã nhận job, false nếu không khả dụng.
     */
    public static boolean renameEntry(final Context ctx, final File archiveFile, final int fmt,
                                      final String oldEntry, final String newEntry,
                                      final String password,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        final File binary = ensureBinary(ctx);
        if (binary == null) return false;
        new Thread(new Runnable() {
            @Override public void run() {
                List<String> cmd = new ArrayList<String>();
                cmd.add(binary.getAbsolutePath());
                cmd.add("rn");
                cmd.add("-t" + (fmt == FMT_7Z ? "7z" : "zip"));
                if (password != null && password.length() > 0) cmd.add("-p" + password);
                cmd.add("-y");
                cmd.add("-bso0");
                cmd.add("-bse2");
                cmd.add("-scsUTF-8");
                cmd.add(archiveFile.getAbsolutePath());
                cmd.add(oldEntry);
                cmd.add(newEntry);
                File workDir = archiveFile.getParentFile();
                if (workDir == null) workDir = new File(".");
                String err = runOnce(cmd, workDir, 0, 1, cb, token);
                finishSimple(cb, err == null, err);
            }
        }).start();
        return true;
    }

    /**
     * Xóa các entry khỏi archive: 7zz d.
     * entries là đường dẫn entry đầy đủ trong archive (dir kết thúc bằng "/").
     * @return true nếu native đã nhận job, false nếu không khả dụng.
     */
    public static boolean deleteEntries(final Context ctx, final File archiveFile, final int fmt,
                                        final List<String> entries, final String password,
                                        final FileOps.OpCallback cb, final CancelToken token) {
        final File binary = ensureBinary(ctx);
        if (binary == null || entries == null || entries.isEmpty()) return false;
        new Thread(new Runnable() {
            @Override public void run() {
                List<String> cmd = new ArrayList<String>();
                cmd.add(binary.getAbsolutePath());
                cmd.add("d");
                cmd.add("-t" + (fmt == FMT_7Z ? "7z" : "zip"));
                if (password != null && password.length() > 0) cmd.add("-p" + password);
                cmd.add("-y");
                cmd.add("-bso0");
                cmd.add("-bse2");
                cmd.add("-scsUTF-8");
                cmd.add(archiveFile.getAbsolutePath());
                cmd.addAll(entries);
                File workDir = archiveFile.getParentFile();
                if (workDir == null) workDir = new File(".");
                String err = runOnce(cmd, workDir, 0, 1, cb, token);
                finishSimple(cb, err == null, err);
            }
        }).start();
        return true;
    }

    /** Kết thúc op không tạo file mới (rn/d): chỉ báo cb, không xóa gì. */
    private static void finishSimple(FileOps.OpCallback cb, boolean ok, String err) {
        if (cb != null) {
            try {
                cb.onDone(ok, ok ? null : err);
            } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Add files into an existing archive (7zz a) — v1.1-m7: dán vào archive
    // ------------------------------------------------------------------

    /**
     * Thêm file/thư mục vào archive ĐÃ CÓ, tại thư mục trong archive
     * entryPrefix ("" = gốc; khác rỗng thì kết thúc bằng "/").
     *
     * Cách làm: chép sources vào thư mục staging trong cache theo đúng
     * cấu trúc prefix, rồi chạy "7zz a" với cwd = staging -> entry đích
     * khớp prefix. Entry trùng tên trong archive bị 7zz ghi đè (update) —
     * đúng ngữ nghĩa "dán" của ZArchiver.
     *
     * KHÔNG dùng finish() của compress: op này tuyệt đối không được xóa
     * archive của user khi lỗi/hủy (7zz tự ghi file tạm rồi mới thay thế).
     *
     * @return true nếu native đã nhận job, false nếu không khả dụng.
     */
    public static boolean addToArchive(final Context ctx, final File archiveFile, final int fmt,
                                       final List<File> sources, final String entryPrefix,
                                       final String password,
                                       final FileOps.OpCallback cb, final CancelToken token) {
        if (sources == null || sources.isEmpty() || archiveFile == null) return false;
        for (File s : sources) {
            if (s == null || !s.exists()) return false;
        }
        final File binary = ensureBinary(ctx);
        if (binary == null) return false;
        new Thread(new Runnable() {
            @Override public void run() {
                File staging = new File(ctx.getCacheDir(),
                        "kaizen-add-" + System.currentTimeMillis());
                try {
                    // Chặn tự dán archive vào chính nó / vào thư mục chứa nó.
                    for (File s : sources) {
                        if (s.isDirectory() && isInside(archiveFile, s)) {
                            finishSimple(cb, false,
                                    "Không thể dán thư mục chứa chính archive vào archive");
                            return;
                        }
                        try {
                            if (s.getCanonicalPath().equals(archiveFile.getCanonicalPath())) {
                                finishSimple(cb, false,
                                        "Không thể tự dán archive vào chính nó");
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    File targetDir = (entryPrefix == null || entryPrefix.isEmpty())
                            ? staging : new File(staging, entryPrefix);
                    if (!targetDir.exists() && !targetDir.mkdirs()) {
                        finishSimple(cb, false, "Không tạo được thư mục tạm");
                        return;
                    }
                    int done = 0;
                    for (File s : sources) {
                        if (cancelled(token)) {
                            finishSimple(cb, false, "Đã hủy");
                            return;
                        }
                        String copyErr = copyRecursiveSync(s, new File(targetDir, s.getName()));
                        if (copyErr != null) {
                            finishSimple(cb, false, copyErr);
                            return;
                        }
                        done++;
                        if (cb != null) {
                            try { cb.onProgress(done, sources.size(), s.getName()); }
                            catch (Exception ignored) {}
                        }
                    }
                    List<String> cmd = new ArrayList<String>();
                    cmd.add(binary.getAbsolutePath());
                    cmd.add("a");                       // add/update vào archive có sẵn
                    cmd.add("-t" + (fmt == FMT_7Z ? "7z" : "zip"));
                    cmd.add("-mx=" + mapLevel(fmt, 6));
                    cmd.add("-mmt=on");
                    cmd.add("-bsp1");
                    cmd.add("-bso1");
                    cmd.add("-bb1");
                    cmd.add("-bse2");
                    cmd.add("-scsUTF-8");
                    if (password != null && password.length() > 0) {
                        cmd.add("-p" + password);
                        if (fmt == FMT_ZIP) cmd.add("-mem=AES256");
                    }
                    cmd.add("-y");
                    cmd.add(archiveFile.getAbsolutePath());
                    if (entryPrefix == null || entryPrefix.isEmpty()) {
                        for (File s : sources) {
                            String name = s.getName();
                            // Tên bắt đầu bằng - hoặc @ sẽ bị 7zz hiểu nhầm là switch.
                            if (name.startsWith("-") || name.startsWith("@")) name = "./" + name;
                            cmd.add(name);
                        }
                    } else {
                        // Thêm cả cây thư mục cấp 1 của prefix — phần con bên
                        // trong (gồm thư mục đích) đi theo đệ quy.
                        String first = entryPrefix;
                        int slash = first.indexOf('/');
                        if (slash >= 0) first = first.substring(0, slash);
                        if (first.startsWith("-") || first.startsWith("@")) first = "./" + first;
                        cmd.add(first);
                    }
                    String err = runOnce(cmd, staging, 0, 1, cb, token);
                    if (err != null && err.startsWith("Nén thất bại: ")) {
                        err = "Thêm vào archive thất bại: "
                                + err.substring("Nén thất bại: ".length());
                    }
                    finishSimple(cb, err == null, err);
                } catch (Exception e) {
                    finishSimple(cb, false,
                            e.getMessage() != null ? e.getMessage() : e.toString());
                } finally {
                    deleteRecursiveSync(staging);
                }
            }
        }).start();
        return true;
    }

    /** Chép đệ quy đồng bộ (cho staging dán vào archive). null = OK. */
    private static String copyRecursiveSync(File src, File dst) {
        try {
            if (src.isDirectory()) {
                if (!dst.exists() && !dst.mkdirs()) {
                    return "Không tạo được thư mục tạm: " + src.getName();
                }
                File[] kids = src.listFiles();
                if (kids != null) {
                    for (File k : kids) {
                        String err = copyRecursiveSync(k, new File(dst, k.getName()));
                        if (err != null) return err;
                    }
                }
                return null;
            }
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            InputStream in = new java.io.FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } finally {
                try { in.close(); } catch (Exception ignored) {}
                try { out.close(); } catch (Exception ignored) {}
            }
            return null;
        } catch (Exception e) {
            return "Lỗi chép file tạm: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** true neu f la symlink (check thuan Java, chay duoc tu API 21). */
    private static boolean isSymlink(File f) {
        try {
            File parent = f.getParentFile();
            if (parent == null) return false;
            File canonDir = parent.getCanonicalFile();
            File canonFile = f.getCanonicalFile();
            return !new File(canonDir, f.getName()).equals(canonFile);
        } catch (Exception e) {
            return false;
        }
    }

    /** true neu name co segment dung bang ".." (tach theo '/'). */
    private static boolean hasDotDotSegment(String name) {
        for (String seg : name.split("/")) {
            if ("..".equals(seg)) return true;
        }
        return false;
    }

    /**
     * Pentest 26/10: duyet cay outRoot, xoa moi symlink va moi path co
     * canonical nam ngoai outRoot. Tra so entry da xoa.
     */
    private static int purgeUnsafe(File root) {
        int[] removed = new int[1];
        String base;
        try {
            base = root.getCanonicalPath();
        } catch (Exception e) {
            return 0;
        }
        purgeUnsafeWalk(root, base, removed);
        return removed[0];
    }

    private static void purgeUnsafeWalk(File dir, String base, int[] removed) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            try {
                if (isSymlink(k)) {
                    if (k.delete()) removed[0]++;
                    continue;
                }
                String canon = k.getCanonicalPath();
                if (!canon.equals(base) && !canon.startsWith(base + File.separator)) {
                    deleteRecursiveSync(k);
                    removed[0]++;
                    continue;
                }
                if (k.isDirectory()) purgeUnsafeWalk(k, base, removed);
            } catch (Exception ignored) {}
        }
    }

    /** Xóa đệ quy đồng bộ (dọn staging). */
    private static void deleteRecursiveSync(File f) {
        if (f == null || !f.exists()) return;
        // Pentest 26/10: khong follow symlink — xoa chinh link, khong
        // xoa noi dung dich (nguy hiem nhat khi don tmpDir sau giai nen).
        if (isSymlink(f)) {
            try { f.delete(); } catch (Exception ignored) {}
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursiveSync(k);
            }
        }
        try { f.delete(); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Pre-flight: binary có sẵn sàng không?
    // ------------------------------------------------------------------

    /** ABI hiện tại có binary tương ứng trong assets không? (v1.1-m3: arm64-v8a) */
    private static String supportedAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String abi : abis) {
                if ("arm64-v8a".equals(abi)) return "arm64-v8a";
            }
        }
        return null;
    }

    /**
     * Copy binary từ assets ra filesDir/bin/7zz (lần đầu hoặc khi app update),
     * chmod 700, verify chạy được. Trả về File binary hoặc null nếu không khả dụng.
     *
     * v1.1-m3 fix: ƯU TIÊN binary trong nativeLibraryDir
     * (đóng gói ở lib/arm64-v8a/lib7zz.so trong APK — PackageManager bung ra
     * với quyền exec đúng; chạy binary từ files/bin bị SELinux chặn
     * với "Permission denied" error=13 trên một số máy).
     * Fallback assets -> files/bin giữ lại cho trường hợp lạ.
     */
    public static synchronized File ensureBinary(Context ctx) {
        if (sBinary != null && sBinary.canExecute()) return sBinary;
        try {
            try {
                String nativeLibDir = ctx.getApplicationInfo().nativeLibraryDir;
                if (nativeLibDir != null) {
                    File sysLib = new File(nativeLibDir, "lib7zz.so");
                    if (sysLib.exists() && sysLib.canExecute()) {
                        sBinary = sysLib;
                        return sysLib;
                    }
                }
            } catch (Exception ignored) {}
            // Fallback: copy từ assets (cho trường hợp nativeLibraryDir không có).
            String abi = supportedAbi();
            if (abi == null) return null;
            File binDir = new File(ctx.getFilesDir(), "bin");
            if (!binDir.exists() && !binDir.mkdirs()) return null;
            File out = new File(binDir, "7zz");

            // Copy lại khi: chưa có, không exec được, hoặc app vừa update
            // (versionCode đổi -> binary trong assets có thể mới hơn).
            // Dùng marker file thay vì openFd (asset bị aapt nén thì openFd throw).
            int vcode = 0;
            try {
                vcode = ctx.getPackageManager()
                        .getPackageInfo(ctx.getPackageName(), 0).versionCode;
            } catch (Exception ignored) {}
            File marker = new File(binDir, "7zz.version");
            boolean fresh = out.exists() && out.canExecute() && marker.exists()
                    && String.valueOf(vcode).equals(readAll(marker));
            if (!fresh) {
                InputStream in = null;
                OutputStream os = null;
                try {
                    in = ctx.getAssets().open(ASSET_DIR + "/" + abi + "/7zz");
                    os = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                } finally {
                    if (in != null) try { in.close(); } catch (Exception ignored) {}
                    if (os != null) try { os.close(); } catch (Exception ignored) {}
                }
                writeAll(marker, String.valueOf(vcode));
            }
            // chmod 700 — bắt buộc để Linux cho phép exec file trong app data.
            Process p = new ProcessBuilder("chmod", "700", out.getAbsolutePath())
                    .redirectErrorStream(true).start();
            p.waitFor();
            if (!out.canExecute()) return null;
            sBinary = out;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(File f) {
        try {
            InputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[256];
            int n;
            while ((n = in.read(b)) != -1) bos.write(b, 0, n);
            in.close();
            return bos.toString("UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeAll(File f, String s) {
        try {
            OutputStream os = new FileOutputStream(f);
            os.write(s.getBytes("UTF-8"));
            os.close();
        } catch (Exception ignored) {}
    }

    /** Native có dùng được không (đã cache)? */
    public static boolean isAvailable(Context ctx) {
        if (sAvailable == null) {
            sAvailable = ensureBinary(ctx) != null;
        }
        return sAvailable;
    }

    // ------------------------------------------------------------------
    // Compress
    // ------------------------------------------------------------------

    /**
     * Nén bằng 7zz native trên background thread, báo progress qua cb.
     *
     * @param fmt      FMT_ZIP hoặc FMT_7Z (REM không qua đây — giữ Java)
     * @param level    0=Lưu trữ, 1=Nhanh, 6=Bình thường, 9=Tối đa
     * @param password null/rỗng = không mật khẩu
     * @return true nếu native đã nhận job (thành công hay lỗi đều báo qua cb),
     *         false nếu native không khả dụng -> caller fallback sang Java.
     */
    public static boolean compress(final Context ctx, final int fmt,
                                   final List<File> sources, final File destFile,
                                   final int level, final String password,
                                   final FileOps.OpCallback cb, final CancelToken token) {
        // ---- guard giống bản Java ----
        if (sources == null || sources.isEmpty()) return false;
        if (destFile == null) return false;
        for (File s : sources) {
            if (s == null || !s.exists()) return false;
        }
        final File binary = ensureBinary(ctx);
        if (binary == null) return false; // fallback Java

        // Guard: không nén thư mục vào chính nó.
        final File out = ZipUtils.uniqueFile(destFile);
        for (File s : sources) {
            if (s.isDirectory() && isInside(out, s)) {
                // Báo lỗi trực tiếp, không fallback (lỗi logic, Java cũng lỗi).
                new Thread(new Runnable() {
                    @Override public void run() {
                        if (cb != null) cb.onDone(false, "Không thể nén thư mục vào chính nó");
                    }
                }).start();
                return true;
            }
        }

        new Thread(new Runnable() {
            @Override public void run() {
                runNative(ctx, binary, fmt, sources, out, level, password, cb, token);
            }
        }).start();
        return true;
    }

    private static void runNative(Context ctx, File binary, int fmt,
                                  List<File> sources, File out, int level,
                                  String password,
                                  FileOps.OpCallback cb, CancelToken token) {
        // Nhóm sources theo thư mục cha -> chạy 7zz với cwd = thư mục cha,
        // truyền tên tương đối -> entry trong archive gọn như bản Java.
        Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
        for (File s : sources) {
            File parent = s.getParentFile();
            if (parent == null) parent = new File(".");
            String key = parent.getAbsolutePath();
            List<String> names = groups.get(key);
            if (names == null) {
                names = new ArrayList<String>();
                groups.put(key, names);
            }
            String name = s.getName();
            // Tên bắt đầu bằng - hoặc @ sẽ bị 7zz hiểu nhầm là switch/listfile.
            if (name.startsWith("-") || name.startsWith("@")) name = "./" + name;
            names.add(name);
        }

        String type = (fmt == FMT_7Z) ? "7z" : "zip";
        String mx = mapLevel(fmt, level);
        boolean hasPw = password != null && password.length() > 0;

        int groupIndex = 0;
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            if (cancelled(token)) {
                finish(cb, false, "Đã hủy", out);
                return;
            }
            List<String> cmd = new ArrayList<String>();
            cmd.add(binary.getAbsolutePath());
            cmd.add("a");                       // add
            cmd.add("-t" + type);
            cmd.add("-mx=" + mx);
            cmd.add("-mmt=on");                  // ĐA LUỒNG: chìa khóa tốc độ
            cmd.add("-bsp1");                    // progress -> stdout
            cmd.add("-bso1");                    // bật output thường (để -bb1 in tên file)
            cmd.add("-bb1");                     // in "+ <file>" mỗi khi bắt đầu nén 1 file
            cmd.add("-bse2");                    // lỗi -> stderr
            cmd.add("-scsUTF-8");                // tên file UTF-8
            if (hasPw) {
                cmd.add("-p" + password);
                if (fmt == FMT_ZIP) cmd.add("-mem=AES256");
            }
            cmd.add("-y");                       // yes cho mọi prompt
            cmd.add(out.getAbsolutePath());
            cmd.addAll(e.getValue());

            String err = runOnce(cmd, new File(e.getKey()), groupIndex,
                    groups.size(), cb, token);
            if (err != null) {
                finish(cb, false, err, out);
                return;
            }
            groupIndex++;
        }
        if (!out.exists() || out.length() == 0) {
            finish(cb, false, "Nén thất bại: không tạo được file", out);
            return;
        }
        finish(cb, true, null, out);
    }

    /**
     * Map mức nén của app sang -mx của 7-Zip.
     * 7z: 0=Copy, 1=Fastest, 5=Normal, 9=Ultra | zip: 0=Store ... 9=Max.
     */
    private static String mapLevel(int fmt, int level) {
        if (level <= 0) return "0";          // Lưu trữ
        if (level <= 1) return "1";          // Nhanh
        if (fmt == FMT_7Z) {
            return level >= 9 ? "9" : "5";   // Tối đa=Ultra(9), Bình thường=Normal(5)
        }
        return String.valueOf(Math.min(9, level)); // zip: giữ nguyên 1..9
    }

    /**
     * Chạy 1 lệnh 7zz, parse % từ stdout (-bsp1), hỗ trợ cancel.
     * Trả về null nếu OK, ngược lại là message lỗi.
     */
    private static String runOnce(List<String> cmd, File workDir, int groupIndex,
                                  int groupCount, FileOps.OpCallback cb,
                                  CancelToken token) {
        Process proc = null;
        try {
            proc = new ProcessBuilder(cmd)
                    .directory(workDir)
                    .redirectErrorStream(false)
                    // 27/10: stdin lay tu /dev/null — 7zz can mat khau ma khong co
                    // -p se gap EOF va thoat bao loi ngay, thay vi cho nhap mai.
                    .redirectInput(new File("/dev/null"))
                    .start();
            final Process p = proc;

            // Thread canh cancel -> destroy process (kể cả lúc 7zz không ra output).
            final CancelToken tok = token;
            Thread watcher = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        while (true) {
                            Thread.sleep(200);
                            boolean done;
                            try {
                                p.exitValue();
                                done = true;
                            } catch (IllegalThreadStateException itse) {
                                done = false;
                            }
                            if (done) return;
                            if (tok != null && tok.isCancelled()) {
                                try { p.destroy(); } catch (Exception ignored) {}
                                return;
                            }
                        }
                    } catch (InterruptedException ignored) {}
                }
            });
            watcher.setDaemon(true);
            watcher.start();

            InputStream stdout = p.getInputStream();
            InputStream stderr = p.getErrorStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

            // Đọc stderr trên thread riêng để không block.
            final InputStream se = stderr;
            final ByteArrayOutputStream eb = errBuf;
            Thread errThread = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        byte[] b = new byte[4096];
                        int n;
                        while ((n = se.read(b)) != -1) {
                            synchronized (eb) {
                                if (eb.size() < 4096) eb.write(b, 0, n);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });
            errThread.setDaemon(true);
            errThread.start();

            StringBuilder cur = new StringBuilder();
            int lastPct = -1;
            String currentFile = null;
            // Decode UTF-8 chuẩn qua InputStreamReader (tên file tiếng Việt là
            // UTF-8 nhiều byte; đọc byte thô rồi ép (char) sẽ vỡ thành mojibake
            // kiểu "§©"). Reader tự nối đúng các byte bị chia cắt giữa 2 lần đọc.
            java.io.InputStreamReader reader =
                    new java.io.InputStreamReader(stdout, "UTF-8");
            int ch;
            while ((ch = reader.read()) != -1) {
                if (token != null && token.isCancelled()) {
                    p.destroy();
                    try { reader.close(); } catch (Exception ignored) {}
                    return "Đã hủy";
                }
                char c = (char) ch;
                // 7zz trên Linux/Android cập nhật dòng % bằng '\b' (backspace),
                // không phải '\r' như trên Windows (xem ClosePrint trong
                // PercentPrinter.cpp) — phải tách theo '\b' mới thấy % live.
                if (c == '\r' || c == '\n' || c == '\b') {
                    String chunk = cur.toString();
                    // v1.1-m3: với -bb1, 7zz in "+ <đường dẫn>" mỗi khi bắt đầu
                    // nén 1 file -> hiện tên file đang nén lên dialog.
                    boolean isFile = chunk.startsWith("+ ") && chunk.length() > 2;
                    if (isFile) {
                        currentFile = chunk.substring(2).trim();
                    }
                    int pct = parsePct(chunk, lastPct);
                    if (pct >= 0 && pct != lastPct) {
                        lastPct = pct;
                        progress(cb, pct, groupIndex, groupCount, currentFile);
                    } else if (isFile && lastPct >= 0) {
                        // Tên file đổi nhưng % chưa kịp đổi -> cập nhật tên ngay.
                        progress(cb, lastPct, groupIndex, groupCount, currentFile);
                    }
                    cur.setLength(0);
                } else {
                    cur.append(c);
                    if (cur.length() > 256) cur.delete(0, cur.length() - 256);
                }
            }
            // Đoạn cuối chưa có \r
            int pct = parsePct(cur.toString(), lastPct);
            if (pct >= 0 && pct != lastPct) {
                progress(cb, pct, groupIndex, groupCount, currentFile);
            }

            int exit = p.waitFor();
            try { errThread.join(1000); } catch (InterruptedException ignored) {}
            try { stdout.close(); } catch (Exception ignored) {}
            try { stderr.close(); } catch (Exception ignored) {}

            if (token != null && token.isCancelled()) return "Đã hủy";
            if (exit != 0) {
                String errMsg;
                synchronized (eb) {
                    errMsg = eb.toString().trim();
                }
                if (errMsg.length() > 300) errMsg = errMsg.substring(0, 300);
                if (errMsg.isEmpty()) errMsg = "7zz thoát với mã " + exit;
                return "Nén thất bại: " + errMsg;
            }
            return null;
        } catch (Exception ex) {
            if (proc != null) {
                try { proc.destroy(); } catch (Exception ignored) {}
            }
            String m = ex.getMessage();
            return "Nén thất bại: " + (m != null ? m : ex.toString());
        }
    }

    /** Parse "12%" từ chunk progress của 7zz; -1 nếu không có % mới. */
    private static int parsePct(String chunk, int lastPct) {
        Matcher m = PCT_PATTERN.matcher(chunk);
        int found = -1;
        while (m.find()) {
            try {
                found = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        if (found < 0 || found > 100 || found == lastPct) return -1;
        return found;
    }

    /**
     * Báo progress dạng done/total để dialog hiện % đúng.
     * Nhiều group (hiếm) -> chia đều % theo group.
     */
    private static void progress(FileOps.OpCallback cb, int pct,
                                 int groupIndex, int groupCount, String name) {
        if (cb == null) return;
        int overall = groupCount <= 1 ? pct
                : Math.min(100, (groupIndex * 100 + pct) / groupCount);
        try {
            cb.onProgress(overall, 100, name);
        } catch (Exception ignored) {}
    }

    private static void finish(FileOps.OpCallback cb, boolean ok, String err, File out) {
        if (!ok && out != null && out.exists()) {
            out.delete(); // dọn file dở (hủy hoặc lỗi) — giống bản Java
        }
        if (cb != null) {
            try {
                cb.onDone(ok, ok ? null : err);
            } catch (Exception ignored) {}
        }
    }

    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    /** out có nằm trong dir không (chống nén thư mục vào chính nó). */
    private static boolean isInside(File out, File dir) {
        try {
            String o = out.getCanonicalPath();
            String d = dir.getCanonicalPath();
            return o.equals(d) || o.startsWith(d + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Fallback: ZIP mà java.util.zip không đọc được (fix bug 20/10).
    //
    // Ví dụ: ZIP nén bằng XZ (compression method 95) -> ZipFile throw
    // "invalid CEN header (bad compression method: 95)", app khác
    // (ZArchiver) mở được vì dùng 7-Zip native. Ta fallback sang 7zz
    // của mình cho cả list lẫn extract.
    // ------------------------------------------------------------------

    /**
     * List entries của ZIP bằng 7zz (đồng bộ — caller tự chạy trên
     * background thread). Trả về null nếu native không khả dụng hoặc
     * 7zz báo lỗi (để caller quyết định tiếp).
     */
    public static java.util.List<ZipUtils.ZipEntryInfo> listZipEntries(
            Context ctx, File zipFile) {
        File binary = ensureBinary(ctx);
        if (binary == null || zipFile == null || !zipFile.isFile()) return null;
        List<String> cmd = new ArrayList<String>();
        cmd.add(binary.getAbsolutePath());
        cmd.add("l");
        cmd.add("-slt");
        cmd.add("-scsUTF-8");
        cmd.add("-bso1");
        cmd.add("-bse2");
        cmd.add(zipFile.getAbsolutePath());
        File workDir = zipFile.getParentFile();
        if (workDir == null) workDir = new File(".");
        String out = runCapture(cmd, workDir);
        if (out == null) return null;
        return parseSltList(out);
    }

    /**
     * Giải nén ZIP bằng 7zz (đồng bộ — caller tự chạy trên background
     * thread). entryNames null/rỗng = bung toàn bộ.
     * @return null nếu thành công, ngược lại là message lỗi.
     */
    public static String extractZipSync(Context ctx, File zipFile,
            List<String> entryNames, File destDir, boolean overwrite,
            String stripPrefix,
            FileOps.OpCallback cb, CancelToken token) {
        return extractZipSync(ctx, zipFile, entryNames, destDir, overwrite,
                stripPrefix, null, cb, token);
    }

    /**
     * Nhu tren, them mat khau (null/rong = khong dung -p).
     * @return null nếu thành công, ngược lại là message lỗi.
     */
    public static String extractZipSync(Context ctx, File zipFile,
            List<String> entryNames, File destDir, boolean overwrite,
            String stripPrefix, String password,
            FileOps.OpCallback cb, CancelToken token) {
        File binary = ensureBinary(ctx);
        if (binary == null) return "7zz native không khả dụng";
        try {
            if (!destDir.exists() && !destDir.mkdirs())
                return "Không tạo được thư mục đích";
            // v1.2-m1: cần bỏ tiền tố thư mục cha thì bung vào thư mục
            // tạm nằm trong đích rồi dời nội dung lên, vì 7zz luôn giữ
            // nguyên đường dẫn đầy đủ của entry.
            final boolean useStrip = stripPrefix != null && !stripPrefix.isEmpty()
                    && entryNames != null && !entryNames.isEmpty();
            final File tmpDir = useStrip
                    ? new File(destDir, ".kz-strip-" + System.currentTimeMillis())
                    : null;
            final File outRoot = useStrip ? tmpDir : destDir;
            List<String> cmd = new ArrayList<String>();
            cmd.add(binary.getAbsolutePath());
            cmd.add("x");
            cmd.add("-tzip");
            cmd.add("-o" + outRoot.getAbsolutePath());
            cmd.add(overwrite ? "-aoa" : "-aou");
            cmd.add("-y");
            // 27/10: mat khau cho ZipCrypto (va moi loai 7zz mo duoc) — 1 argv
            // rieng nen mat khau co khoang trang hay bat dau '-' van an toan.
            if (password != null && !password.isEmpty()) {
                cmd.add("-p" + password);
            }
            cmd.add("-bsp1");
            cmd.add("-bso1");
            cmd.add("-bb1");
            cmd.add("-bse2");
            cmd.add("-scsUTF-8");
            cmd.add(zipFile.getAbsolutePath());
            // Pentest 26/10: guard ten entry truoc khi dua cho 7zz (giong
            // duong nen): ten bat dau -/@ -> prefix ./ ; entry co segment
            // .. thi bo qua. Neu loc xong khong con entry nao thi bao loi
            // chu khong chay 7zz "khong ten" (no se bung toan bo archive,
            // sai ngu nghia entry da chon).
            java.util.List<String> safeNames = new java.util.ArrayList<String>();
            if (entryNames != null) {
                for (String nm : entryNames) {
                    if (nm == null || nm.length() == 0) continue;
                    if (hasDotDotSegment(nm)) continue;
                    if (nm.startsWith("-") || nm.startsWith("@")) nm = "./" + nm;
                    safeNames.add(nm);
                }
                if (!entryNames.isEmpty() && safeNames.isEmpty()) {
                    if (tmpDir != null) deleteRecursiveSync(tmpDir);
                    return "Không có entry hợp lệ để giải nén";
                }
            }
            for (String nm : safeNames) cmd.add(nm);
            File workDir = zipFile.getParentFile();
            if (workDir == null) workDir = new File(".");
            String r = runOnce(cmd, workDir, 0, 1, cb, token);
            if (r != null) {
                if (tmpDir != null) deleteRecursiveSync(tmpDir);
                return r;
            }
            // Pentest 26/10: 7zz khong co validation phia app — duyet
            // outRoot, xoa symlink va moi path thoat khoi outRoot truoc
            // khi doi len (strip) hoac tra ket qua. Entry bi loai chi bao
            // qua progress, khong fail ca lan giai nen (giong duong Java).
            int removed = purgeUnsafe(outRoot);
            if (removed > 0) {
                progress(cb, 100, 0, 1, "Đã loại bỏ " + removed
                        + " entry không an toàn (symlink/thoát thư mục đích)");
            }
            if (useStrip) {
                File inner = new File(tmpDir, stripPrefix);
                String merr = moveContentsUp(inner, destDir, overwrite);
                deleteRecursiveSync(tmpDir);
                return merr;
            }
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }

    /** Dời toàn bộ nội dung của srcDir vào dstDir (gộp khi trùng tên). null = OK. */
    private static String moveContentsUp(File srcDir, File dstDir, boolean overwrite) {
        if (srcDir == null || !srcDir.isDirectory()) return null;
        File[] kids = srcDir.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            // Pentest 26/10: khong bao gio follow symlink khi doi len —
            // symlink noi bo cung co the tao vong lap (link -> chinh no).
            if (isSymlink(k)) continue;
            File target = new File(dstDir, k.getName());
            if (k.isDirectory()) {
                if (!target.exists() && !target.mkdirs()) {
                    return "Không tạo được thư mục: " + target.getName();
                }
                String err = moveContentsUp(k, target, overwrite);
                if (err != null) return err;
            } else {
                if (target.exists()) {
                    if (!overwrite) continue;
                    target.delete();
                }
                if (!k.renameTo(target)) {
                    // Khác phân vùng (hiếm) -> chép dữ liệu rồi xóa nguồn
                    try {
                        java.io.FileInputStream in = new java.io.FileInputStream(k);
                        java.io.FileOutputStream out =
                                new java.io.FileOutputStream(target);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        out.close();
                        in.close();
                        k.delete();
                    } catch (Exception e) {
                        return e.getMessage() != null ? e.getMessage() : e.toString();
                    }
                }
            }
        }
        return null;
    }

    /** Chạy lệnh và gom toàn bộ stdout (đồng bộ). null nếu exit != 0. */
    private static String runCapture(List<String> cmd, File workDir) {
        Process proc = null;
        try {
            proc = new ProcessBuilder(cmd).directory(workDir)
                    // 27/10: giong runOnce — stdin EOF ngay de khong treo o prompt mat khau.
                    .redirectInput(new File("/dev/null"))
                    .redirectErrorStream(true).start();
            java.io.InputStreamReader reader =
                    new java.io.InputStreamReader(proc.getInputStream(), "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > 4 * 1024 * 1024) break; // guard archive khủng
            }
            int exit = proc.waitFor();
            String out = sb.toString();
            if (exit != 0) return null;
            if (out.contains("ERROR:") || out.contains("Can not open the file as archive"))
                return null;
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            if (proc != null) try { proc.destroy(); } catch (Exception ignored) {}
        }
    }

    /**
     * Parse output của `7zz l -slt`. Mỗi entry là 1 block bắt đầu bằng
     * "Path = ..."; block của chính archive có thêm dòng "Type = ..."
     * nên loại được. QUAN TRỌNG (fix 20/10): 7-Zip chỉ in đúng 1 dòng
     * "----------" sau phần archive props — các item cách nhau bằng
     * DÒNG TRỐNG (xem List.cpp: PrintItemInfo kết thúc bằng MY_ENDL).
     * Bản trước split theo "----------" nên gộp mọi file thành 1 entry.
     */
    private static java.util.List<ZipUtils.ZipEntryInfo> parseSltList(String out) {
        java.util.List<ZipUtils.ZipEntryInfo> list =
                new java.util.ArrayList<ZipUtils.ZipEntryInfo>();
        String[] lines = out.split("\n");
        String path = null, folder = null, sizeStr = null, modStr = null;
        boolean hasType = false;
        for (int i = 0; i <= lines.length; i++) {
            String ln = (i < lines.length) ? lines[i].trim() : "";
            boolean isSep = ln.isEmpty() || "----------".equals(ln);
            boolean isNewPath = ln.startsWith("Path = ");
            if ((isSep || (isNewPath && path != null)) && path != null) {
                // flush block hiện tại
                if (!hasType) {
                    ZipUtils.ZipEntryInfo info = new ZipUtils.ZipEntryInfo();
                    info.name = path;
                    info.isDir = "+".equals(folder) || path.endsWith("/");
                    try {
                        info.size = Long.parseLong(sizeStr.trim());
                    } catch (Exception e) {
                        info.size = 0;
                    }
                    info.time = parseSltTime(modStr);
                    list.add(info);
                }
                path = null; folder = null; sizeStr = null; modStr = null;
                hasType = false;
            }
            if (isSep) continue;
            if (isNewPath) {
                path = ln.substring(7);
            } else if (ln.startsWith("Folder = ")) {
                folder = ln.substring(9).trim();
            } else if (ln.startsWith("Size = ")) {
                sizeStr = ln.substring(7).trim();
            } else if (ln.startsWith("Modified = ")) {
                modStr = ln.substring(11).trim();
            } else if (ln.startsWith("Type = ")) {
                hasType = true;
            }
        }
        return list;
    }

    /** Parse "Modified = 2026-10-20 08:09:00" -> millis, lỗi thì 0. */
    private static long parseSltTime(String s) {
        if (s == null || s.length() < 10) return 0;
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            java.util.Locale.US);
            java.util.Date d = f.parse(s.trim());
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
