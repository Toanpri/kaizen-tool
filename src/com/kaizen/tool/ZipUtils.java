package com.kaizen.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ZIP core for Kaizen Tool M2. Pure java.util.zip utility.
 *
 * All public operations run on their own background thread and invoke
 * FileOps.OpCallback directly ON THAT BACKGROUND THREAD — the caller
 * must post to the UI thread itself (e.g. Activity.runOnUiThread).
 */
public class ZipUtils {

    public static final int LEVEL_STORE = 0;
    public static final int LEVEL_FAST = 1;
    public static final int LEVEL_NORMAL = 6;
    public static final int LEVEL_MAX = 9;

    private static final int BUFFER_SIZE = 65536; // 64KB: giảm syscall

    /** Reported as the failure message when the user cancels the operation. */
    private static final String CANCELLED = "Đã hủy";

    /** Null-safe cancel poll: a null token means "no cancellation support". */
    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    // ------------------------------------------------------------------
    // Create ZIP
    // ------------------------------------------------------------------

    /**
     * Compress sources (files/folders, recursive, structure preserved) into destZip.
     * If destZip already exists it is auto-renamed ("name (1).zip", ...).
     */
    public static void createZip(final List<File> sources, final File destZip,
                                 final int level, final FileOps.OpCallback cb,
                                 final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sources == null || sources.isEmpty()) {
                        done(cb, false, "Không có mục nào để nén");
                        return;
                    }
                    if (destZip == null) {
                        done(cb, false, "File đích không hợp lệ");
                        return;
                    }
                    for (File s : sources) {
                        if (s == null || !s.exists()) {
                            done(cb, false, "Mục không tồn tại: " + (s != null ? s.getName() : "null"));
                            return;
                        }
                    }

                    File parent = destZip.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    File out = uniqueFile(destZip);

                    // Guard: refuse to zip a folder into itself.
                    for (File s : sources) {
                        if (s.isDirectory() && isInside(out, s)) {
                            done(cb, false, "Không thể nén thư mục vào chính nó");
                            return;
                        }
                    }

                    final int total = countAll(sources);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    ZipOutputStream zos = null;
                    try {
                        zos = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE));
                        // NOTE: LEVEL_STORE is implemented as DEFLATED level 0
                        // (no compression) instead of method STORED, because STORED
                        // requires pre-computed size + CRC per entry.
                        zos.setMethod(ZipOutputStream.DEFLATED);
                        zos.setLevel(clampLevel(level));
                        for (File src : sources) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            String err = zipRecursive(src, src.getParentFile(), zos,
                                    total, done, cb, firstError, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                        }
                    } finally {
                        closeQuietly(zos);
                    }

                    if (firstError[0] != null) {
                        // Best effort: remove the incomplete archive.
                        out.delete();
                        done(cb, false, firstError[0]);
                        return;
                    }
                    done(cb, true, null);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Extract whole ZIP
    // ------------------------------------------------------------------

    /**
     * Extract all entries of zipFile into destDir.
     * overwrite=false -> conflicting files are auto-renamed.
     * Zip-Slip protection: entries escaping destDir are skipped.
     */
    public static void extractZip(final File zipFile, final File destDir,
                                  final boolean overwrite, final FileOps.OpCallback cb,
                                  final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (zipFile == null || !zipFile.isFile()) {
                        done(cb, false, "File ZIP không tồn tại");
                        return;
                    }
                    if (destDir == null) {
                        done(cb, false, "Thư mục đích không hợp lệ");
                        return;
                    }
                    if (!destDir.exists() && !destDir.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    final String destCanon = destDir.getCanonicalPath() + File.separator;
                    final int total = countZipEntries(zipFile);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    ZipFile zf = null;
                    try {
                        zf = new ZipFile(zipFile);
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            ZipEntry e = en.nextElement();
                            String name = e.getName();
                            progress(cb, done[0] + 1, total, name);

                            File out = new File(destDir, name);
                            String outCanon;
                            try {
                                outCanon = out.getCanonicalPath();
                            } catch (Exception ex) {
                                // Skip silently: cannot resolve safely.
                                done[0]++;
                                continue;
                            }
                            if (!outCanon.startsWith(destCanon)) {
                                // Zip-Slip attempt -> skip with a light note.
                                progress(cb, done[0] + 1, total, "Bỏ qua (không an toàn): " + name);
                                done[0]++;
                                continue;
                            }

                            String err = extractOne(zf, e, out, overwrite, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(zf);
                    }

                    done(cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Extract selected entries (used by ZIP browser)
    // ------------------------------------------------------------------

    /**
     * Extract only the named entries into destDir.
     * entryNames are exact entry names inside the zip.
     */
    public static void extractEntries(final File zipFile, final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (zipFile == null || !zipFile.isFile()) {
                        done(cb, false, "File ZIP không tồn tại");
                        return;
                    }
                    if (entryNames == null || entryNames.isEmpty()) {
                        done(cb, false, "Không có mục nào để giải nén");
                        return;
                    }
                    if (destDir == null) {
                        done(cb, false, "Thư mục đích không hợp lệ");
                        return;
                    }
                    if (!destDir.exists() && !destDir.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    final String destCanon = destDir.getCanonicalPath() + File.separator;
                    final int total = entryNames.size();
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    ZipFile zf = null;
                    try {
                        zf = new ZipFile(zipFile);
                        for (String name : entryNames) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            progress(cb, done[0] + 1, total, name);
                            ZipEntry e = zf.getEntry(name);
                            if (e == null) {
                                firstError[0] = "Không tìm thấy mục trong ZIP: " + name;
                                break;
                            }

                            File out = new File(destDir, name);
                            String outCanon;
                            try {
                                outCanon = out.getCanonicalPath();
                            } catch (Exception ex) {
                                done[0]++;
                                continue;
                            }
                            if (!outCanon.startsWith(destCanon)) {
                                progress(cb, done[0] + 1, total, "Bỏ qua (không an toàn): " + name);
                                done[0]++;
                                continue;
                            }

                            String err = extractOne(zf, e, out, overwrite, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(zf);
                    }

                    done(cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // List entries (never throws)
    // ------------------------------------------------------------------

    public static class ZipEntryInfo {
        public String name;
        public boolean isDir;
        public long size;
        public long time;
    }

    /** List entries of a zip. Returns an empty list on any error. */
    public static List<ZipEntryInfo> listEntries(File zipFile) {
        List<ZipEntryInfo> list = new ArrayList<ZipEntryInfo>();
        if (zipFile == null || !zipFile.isFile()) return list;
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                ZipEntryInfo info = new ZipEntryInfo();
                info.name = e.getName();
                info.isDir = e.isDirectory();
                info.size = e.getSize() < 0 ? 0 : e.getSize();
                info.time = e.getTime();
                list.add(info);
            }
        } catch (Exception ignored) {
            return new ArrayList<ZipEntryInfo>();
        } finally {
            closeQuietly(zf);
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 9) return 9;
        return level;
    }

    /**
     * Add src (file or folder) recursively. Entry names are relative to baseDir
     * with '/' separators; folder entries end with '/'.
     */
    private static String zipRecursive(File src, File baseDir, ZipOutputStream zos,
                                       int total, int[] done, FileOps.OpCallback cb,
                                       String[] firstError, CancelToken token) {
        if (cancelled(token)) return CANCELLED;
        String rel = relativePath(baseDir, src);
        if (src.isDirectory()) {
            String entryName = rel + "/";
            try {
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(src.lastModified());
                zos.putNextEntry(entry);
                zos.closeEntry();
            } catch (Exception e) {
                return "Nén thất bại: " + src.getName() + " — " + msg(e);
            }
            progress(cb, ++done[0], total, entryName);
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (cancelled(token)) return CANCELLED;
                    String err = zipRecursive(child, baseDir, zos, total, done, cb, firstError, token);
                    if (err != null) return err;
                }
            }
            return null;
        }
        // single file
        try {
            ZipEntry entry = new ZipEntry(rel);
            entry.setTime(src.lastModified());
            zos.putNextEntry(entry);
            InputStream in = null;
            try {
                in = new java.io.BufferedInputStream(new FileInputStream(src), BUFFER_SIZE);
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                long sinceCheck = 0;
                while ((n = in.read(buf)) != -1) {
                    zos.write(buf, 0, n);
                    sinceCheck += n;
                    if (sinceCheck >= 65536) {
                        sinceCheck = 0;
                        if (cancelled(token)) return CANCELLED;
                    }
                }
            } finally {
                closeQuietly(in);
            }
            zos.closeEntry();
        } catch (Exception e) {
            return "Nén thất bại: " + src.getName() + " — " + msg(e);
        }
        progress(cb, ++done[0], total, rel);
        return null;
    }

    /** Path of f relative to baseDir, using '/' as separator. */
    private static String relativePath(File baseDir, File f) {
        String base = baseDir.getAbsolutePath();
        String full = f.getAbsolutePath();
        String rel = full.startsWith(base) ? full.substring(base.length()) : f.getName();
        while (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace(File.separatorChar, '/');
    }

    /** Extract one entry. Returns null on success, error message on failure. */
    private static String extractOne(ZipFile zf, ZipEntry e, File out, boolean overwrite,
                                     CancelToken token) {
        try {
            if (e.isDirectory()) {
                if (!out.exists() && !out.mkdirs()) {
                    return "Không tạo được thư mục: " + e.getName();
                }
                return null;
            }
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return "Không tạo được thư mục: " + e.getName();
            }
            if (out.exists() && !overwrite) {
                out = uniqueFile(out);
                parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
            }
            InputStream in = null;
            OutputStream os = null;
            try {
                in = zf.getInputStream(e);
                os = new java.io.BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE);
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                long sinceCheck = 0;
                while ((n = in.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    sinceCheck += n;
                    if (sinceCheck >= 65536) {
                        sinceCheck = 0;
                        if (cancelled(token)) return CANCELLED;
                    }
                }
            } finally {
                closeQuietly(in);
                closeQuietly(os);
            }
            long t = e.getTime();
            if (t > 0) out.setLastModified(t);
        } catch (Exception ex) {
            return "Giải nén thất bại: " + e.getName() + " — " + msg(ex);
        }
        return null;
    }

    private static int countZipEntries(File zipFile) {
        int count = 0;
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                en.nextElement();
                count++;
            }
        } catch (Exception ignored) {
            // count stays 0; extract phase will report the real error
        } finally {
            closeQuietly(zf);
        }
        return count;
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

    /**
     * If "file" exists, return "name (1).ext", "name (2).ext", ... in the same dir.
     * Works for both files and folders.
     */
    public static File uniqueFile(File file) {
        File parent = file.getParentFile();
        String name = file.getName();
        File candidate = file;
        if (!candidate.exists()) return candidate;
        String base;
        String ext;
        if (file.isDirectory()) {
            base = name;
            ext = "";
        } else {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                base = name.substring(0, dot);
                ext = name.substring(dot);
            } else {
                base = name;
                ext = "";
            }
        }
        int i = 1;
        do {
            candidate = new File(parent, base + " (" + i + ")" + ext);
            i++;
        } while (candidate.exists());
        return candidate;
    }

    /** True if child is inside (or equal to) ancestor. */
    private static boolean isInside(File child, File ancestor) {
        String a = ancestor.getAbsolutePath() + File.separator;
        return child.getAbsolutePath().startsWith(a);
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static void progress(FileOps.OpCallback cb, int done, int total, String name) {
        if (cb != null) cb.onProgress(done, total, name);
    }

    private static void done(FileOps.OpCallback cb, boolean ok, String err) {
        if (cb != null) cb.onDone(ok, ok ? null : err);
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

    // ------------------------------------------------------------------
    // WinZip AES-256 encryption (AE-2) — create + extract
    //
    // Format per WinZip AES spec:
    //   - entry method = 99, extra field 0x9901 (len 7):
    //       version=2 (AE-2), vendor "AE", strength=3 (256-bit), method=8 (deflate)
    //   - entry data = salt(16) || pwVerify(2) || AES-CTR(ciphertext) || mac(10)
    //   - keys = PBKDF2WithHmacSHA1(password, salt, 1000, 66 bytes)
    //       -> encKey[0..32], macKey[32..64], pwVerify[64..66]
    //   - CTR: 128-bit little-endian counter starting at 1, AES/ECB keystream
    //   - mac = HMAC-SHA1(macKey, ciphertext)[0..10]
    // Compatible with 7-Zip / WinZip / WinRAR (AES-256 ZIP).
    // ------------------------------------------------------------------

    private static final int AES_METHOD = 99;
    private static final int AES_EXTRA_ID = 0x9901;
    private static final int AES_SALT_LEN = 16;
    private static final int AES_ITER = 1000;
    private static final int AES_MAC_LEN = 10;

    /** Entry đã mã hóa AES hay chưa (quét central directory, method == 99). */
    public static boolean isEncryptedZip(File zipFile) {
        java.io.RandomAccessFile raf = null;
        try {
            raf = new java.io.RandomAccessFile(zipFile, "r");
            long cdOff = findEocdCentralDirOffset(raf);
            if (cdOff < 0) return false;
            raf.seek(cdOff);
            byte[] buf = new byte[46];
            while (true) {
                if (raf.read(buf) != 46) break;
                long sig = readLeInt(buf, 0);
                if (sig != 0x02014b50L) break;
                int method = readLeShort(buf, 10);
                if (method == AES_METHOD) return true;
                int nameLen = readLeShort(buf, 28);
                int extraLen = readLeShort(buf, 30);
                int commentLen = readLeShort(buf, 32);
                raf.seek(raf.getFilePointer() + nameLen + extraLen + commentLen);
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(raf);
        }
        return false;
    }

    /**
     * Nén có mã hóa AES-256 (WinZip AE-2). Chạy trên background thread riêng.
     * Thư mục giữ nguyên không mã hóa (chuẩn WinZip).
     */
    public static void createZipEncrypted(final List<File> sources, final File destZip,
                                          final int level, final String password,
                                          final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sources == null || sources.isEmpty()) {
                        done(cb, false, "Không có mục nào để nén");
                        return;
                    }
                    if (password == null || password.isEmpty()) {
                        done(cb, false, "Chưa nhập mật khẩu");
                        return;
                    }
                    File parent = destZip.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }
                    File out = uniqueFile(destZip);
                    for (File s : sources) {
                        if (s.isDirectory() && isInside(out, s)) {
                            done(cb, false, "Không thể nén thư mục vào chính nó");
                            return;
                        }
                    }

                    List<AesEntry> entries = new ArrayList<AesEntry>();
                    for (File src : sources) {
                        collectAes(src, src.getParentFile(), entries);
                    }
                    final int total = entries.size();

                    java.io.DataOutputStream dos = null;
                    List<CentralRec> central = new ArrayList<CentralRec>();
                    try {
                        countedSize.set(0);
                        dos = new java.io.DataOutputStream(new CountingOutputStream(
                                new java.io.BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE)));
                        int doneCount = 0;
                        for (AesEntry e : entries) {
                            if (cancelled(token)) throw new CancelledAes();
                            CentralRec rec = writeAesLocalEntry(dos, e, level, password, token);
                            central.add(rec);
                            doneCount++;
                            progress(cb, doneCount, total, e.name);
                        }
                        long cdOff = countedSize.get();
                        for (CentralRec r : central) writeCentralRec(dos, r);
                        long cdSize = countedSize.get() - cdOff;
                        writeEocd(dos, central.size(), cdSize, cdOff);
                        dos.flush();
                    } finally {
                        closeQuietly(dos);
                    }
                    done(cb, true, null);
                } catch (CancelledAes ex) {
                    done(cb, false, CANCELLED);
                } catch (Exception e) {
                    done(cb, false, msg(e));
                }
            }
        }).start();
    }

    // ---- AES writer internals ----

    private static class CancelledAes extends Exception {}

    private static class AesEntry {
        String name; // '/' separated, dirs end with '/'
        File file;
        boolean isDir;
    }

    private static class CentralRec {
        String name;
        int method;
        long crc;
        long compSize;
        long uncompSize;
        long localOff;
        int flags;
        int dosTime;
        int dosDate;
        byte[] extra;
    }

    /** Đếm số byte đã ghi (DataOutputStream không cho biết). */
    private static final java.util.concurrent.atomic.AtomicLong countedSize =
            new java.util.concurrent.atomic.AtomicLong();

    private static class CountingOutputStream extends java.io.FilterOutputStream {
        CountingOutputStream(java.io.OutputStream out) { super(out); }
        @Override public void write(int b) throws java.io.IOException {
            out.write(b); countedSize.incrementAndGet();
        }
        @Override public void write(byte[] b, int off, int len) throws java.io.IOException {
            out.write(b, off, len); countedSize.addAndGet(len);
        }
    }

    private static void collectAes(File src, File baseDir, List<AesEntry> out) {
        String rel = relativePath(baseDir, src);
        if (src.isDirectory()) {
            AesEntry e = new AesEntry();
            e.name = rel.endsWith("/") ? rel : rel + "/";
            e.file = src;
            e.isDir = true;
            out.add(e);
            File[] kids = src.listFiles();
            if (kids != null) {
                java.util.Arrays.sort(kids);
                for (File k : kids) collectAes(k, baseDir, out);
            }
        } else {
            AesEntry e = new AesEntry();
            e.name = rel;
            e.file = src;
            e.isDir = false;
            out.add(e);
        }
    }

    private static CentralRec writeAesLocalEntry(java.io.DataOutputStream dos, AesEntry e,
                                                 int level, String password,
                                                 CancelToken token) throws Exception {
        byte[] nameBytes = e.name.getBytes("UTF-8");
        int dosT = dosTime(e.file.lastModified());
        int dosD = dosDate(e.file.lastModified());

        CentralRec rec = new CentralRec();
        rec.name = e.name;
        rec.dosTime = dosT;
        rec.dosDate = dosD;

        if (e.isDir) {
            rec.method = 0;
            rec.flags = 0x0800; // UTF-8
            rec.crc = 0;
            rec.compSize = 0;
            rec.uncompSize = 0;
            rec.extra = new byte[0];
            rec.localOff = countedSize.get();
            writeLocalHeader(dos, 20, rec.flags, 0, dosT, dosD, 0, 0, 0, nameBytes, rec.extra);
            return rec;
        }

        // Deflate ra temp file (tránh OOM với file lớn)
        File tmp = File.createTempFile("kaes", ".bin");
        long rawLen;
        try {
            rawLen = deflateToFile(e.file, tmp, clampLevel(level), token);
        } catch (Exception ex) {
            tmp.delete();
            throw ex;
        }

        byte[] salt = new byte[AES_SALT_LEN];
        new java.security.SecureRandom().nextBytes(salt);
        byte[][] keys = deriveAesKeys(password, salt);
        byte[] keyEnc = keys[0], keyMac = keys[1], pwVerify = keys[2];

        long dataLen = AES_SALT_LEN + 2 + rawLen + AES_MAC_LEN;
        byte[] extra = buildAesExtra();

        rec.method = AES_METHOD;
        rec.flags = 0x0001 | 0x0800; // encrypted + UTF-8
        rec.crc = 0; // AE-2: CRC = 0
        rec.compSize = dataLen;
        rec.uncompSize = e.file.length();
        rec.extra = extra;
        rec.localOff = countedSize.get();

        writeLocalHeader(dos, 51, rec.flags, AES_METHOD, dosT, dosD, 0, dataLen, dataLen,
                nameBytes, extra);

        // salt || pwVerify || ciphertext || mac
        dos.write(salt);
        dos.write(pwVerify);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(keyMac, "HmacSHA1"));
        AesCtr ctr = new AesCtr(keyEnc);
        java.io.InputStream in = null;
        try {
            in = new java.io.BufferedInputStream(new FileInputStream(tmp), BUFFER_SIZE);
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelled(token)) throw new CancelledAes();
                ctr.process(buf, 0, n);
                mac.update(buf, 0, n);
                dos.write(buf, 0, n);
            }
        } finally {
            closeQuietly(in);
            tmp.delete();
        }
        byte[] fullMac = mac.doFinal();
        dos.write(fullMac, 0, AES_MAC_LEN);
        return rec;
    }

    private static void writeLocalHeader(java.io.DataOutputStream dos, int verNeeded, int flags,
                                         int method, int dosT, int dosD, long crc,
                                         long compSize, long uncompSize,
                                         byte[] nameBytes, byte[] extra) throws java.io.IOException {
        dos.writeInt(Integer.reverseBytes(0x04034b50));
        dos.writeShort(Short.reverseBytes((short) verNeeded));
        dos.writeShort(Short.reverseBytes((short) flags));
        dos.writeShort(Short.reverseBytes((short) method));
        dos.writeShort(Short.reverseBytes((short) dosT));
        dos.writeShort(Short.reverseBytes((short) dosD));
        dos.writeInt(Integer.reverseBytes((int) crc));
        dos.writeInt(Integer.reverseBytes((int) compSize));
        dos.writeInt(Integer.reverseBytes((int) uncompSize));
        dos.writeShort(Short.reverseBytes((short) nameBytes.length));
        dos.writeShort(Short.reverseBytes((short) extra.length));
        dos.write(nameBytes);
        dos.write(extra);
    }

    private static void writeCentralRec(java.io.DataOutputStream dos, CentralRec r)
            throws java.io.IOException {
        byte[] nameBytes = r.name.getBytes("UTF-8");
        dos.writeInt(Integer.reverseBytes(0x02014b50));
        dos.writeShort(Short.reverseBytes((short) 63)); // version made by 6.3
        dos.writeShort(Short.reverseBytes((short) (r.method == AES_METHOD ? 51 : 20)));
        dos.writeShort(Short.reverseBytes((short) r.flags));
        dos.writeShort(Short.reverseBytes((short) r.method));
        dos.writeShort(Short.reverseBytes((short) r.dosTime));
        dos.writeShort(Short.reverseBytes((short) r.dosDate));
        dos.writeInt(Integer.reverseBytes((int) r.crc));
        dos.writeInt(Integer.reverseBytes((int) r.compSize));
        dos.writeInt(Integer.reverseBytes((int) r.uncompSize));
        dos.writeShort(Short.reverseBytes((short) nameBytes.length));
        dos.writeShort(Short.reverseBytes((short) r.extra.length));
        dos.writeShort(0); // comment len
        dos.writeShort(0); // disk number
        dos.writeShort(0); // internal attrs
        dos.writeInt(0);   // external attrs
        dos.writeInt(Integer.reverseBytes((int) r.localOff));
        dos.write(nameBytes);
        dos.write(r.extra);
    }

    private static void writeEocd(java.io.DataOutputStream dos, int count, long cdSize, long cdOff)
            throws java.io.IOException {
        dos.writeInt(Integer.reverseBytes(0x06054b50));
        dos.writeShort(0); // disk
        dos.writeShort(0); // cd disk
        dos.writeShort(Short.reverseBytes((short) count));
        dos.writeShort(Short.reverseBytes((short) count));
        dos.writeInt(Integer.reverseBytes((int) cdSize));
        dos.writeInt(Integer.reverseBytes((int) cdOff));
        dos.writeShort(0); // comment len
    }

    private static byte[] buildAesExtra() {
        // 0x9901, len 7: version=2 (AE-2), vendor "AE", strength=3 (256), method=8 (deflate)
        return new byte[]{(byte) 0x01, (byte) 0x99, 0x07, 0x00,
                0x02, 0x00, 0x41, 0x45, 0x03, 0x08, 0x00};
    }

    private static byte[][] deriveAesKeys(String password, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory f =
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        javax.crypto.spec.PBEKeySpec spec =
                new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, AES_ITER, (32 + 32 + 2) * 8);
        byte[] d = f.generateSecret(spec).getEncoded();
        spec.clearPassword();
        byte[][] out = new byte[3][];
        out[0] = java.util.Arrays.copyOfRange(d, 0, 32);
        out[1] = java.util.Arrays.copyOfRange(d, 32, 64);
        out[2] = java.util.Arrays.copyOfRange(d, 64, 66);
        java.util.Arrays.fill(d, (byte) 0);
        return out;
    }

    /** AES-CTR thủ công: counter 128-bit little-endian bắt đầu từ 1 (chuẩn WinZip). */
    private static class AesCtr {
        private final javax.crypto.Cipher ecb;
        private final byte[] counter = new byte[16];
        private final byte[] ks = new byte[16];
        private int ksPos = 16;

        AesCtr(byte[] keyEnc) throws Exception {
            ecb = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
            ecb.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(keyEnc, "AES"));
            counter[0] = 1;
        }

        void process(byte[] b, int off, int len) throws Exception {
            for (int i = 0; i < len; i++) {
                if (ksPos == 16) {
                    byte[] out = ecb.doFinal(counter);
                    System.arraycopy(out, 0, ks, 0, 16);
                    for (int j = 0; j < 16; j++) { // little-endian ++
                        counter[j]++;
                        if (counter[j] != 0) break;
                    }
                    ksPos = 0;
                }
                b[off + i] ^= ks[ksPos++];
            }
        }
    }

    private static long deflateToFile(File src, File tmp, int level, CancelToken token)
            throws java.io.IOException {
        java.util.zip.Deflater def = new java.util.zip.Deflater(level, true);
        java.io.InputStream in = null;
        java.io.OutputStream os = null;
        try {
            in = new java.io.BufferedInputStream(new FileInputStream(src), BUFFER_SIZE);
            os = new java.io.BufferedOutputStream(new FileOutputStream(tmp), BUFFER_SIZE);
            byte[] ibuf = new byte[BUFFER_SIZE];
            byte[] obuf = new byte[BUFFER_SIZE];
            long total = 0;
            int n;
            int c;
            while ((n = in.read(ibuf)) != -1) {
                if (cancelled(token)) throw new java.io.IOException(CANCELLED);
                def.setInput(ibuf, 0, n);
                while ((c = def.deflate(obuf)) > 0) {
                    os.write(obuf, 0, c);
                    total += c;
                }
            }
            def.finish();
            do {
                c = def.deflate(obuf);
                if (c > 0) {
                    os.write(obuf, 0, c);
                    total += c;
                }
            } while (!def.finished());
            os.flush();
            return total;
        } finally {
            def.end();
            closeQuietly(in);
            closeQuietly(os);
        }
    }

    private static int dosTime(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        return (c.get(java.util.Calendar.HOUR_OF_DAY) << 11)
                | (c.get(java.util.Calendar.MINUTE) << 5)
                | (c.get(java.util.Calendar.SECOND) / 2);
    }

    private static int dosDate(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        return ((c.get(java.util.Calendar.YEAR) - 1980) << 9)
                | ((c.get(java.util.Calendar.MONTH) + 1) << 5)
                | c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    // ---- AES extract ----

    /**
     * Giải nén ZIP (hỗ trợ entry AES-256). password null -> entry mã hóa sẽ báo lỗi.
     * Chạy trên background thread riêng.
     */
    public static void extractZipEncrypted(final File zipFile, final File destDir,
                                           final boolean overwrite, final String password,
                                           final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.RandomAccessFile raf = null;
                try {
                    if (zipFile == null || !zipFile.isFile()) {
                        done(cb, false, "File ZIP không tồn tại");
                        return;
                    }
                    if (!destDir.exists() && !destDir.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }
                    final String destCanon = destDir.getCanonicalPath() + File.separator;

                    raf = new java.io.RandomAccessFile(zipFile, "r");
                    long cdOff = findEocdCentralDirOffset(raf);
                    if (cdOff < 0) {
                        done(cb, false, "File ZIP không hợp lệ");
                        return;
                    }
                    List<CdEntry> entries = readCentralDir(raf, cdOff);
                    final int total = entries.size();
                    int doneCount = 0;
                    for (CdEntry e : entries) {
                        if (cancelled(token)) {
                            done(cb, false, CANCELLED);
                            return;
                        }
                        String err = extractAesEntry(raf, e, destDir, destCanon, overwrite,
                                password, token);
                        if (err != null) {
                            done(cb, false, err);
                            return;
                        }
                        doneCount++;
                        progress(cb, doneCount, total, e.name);
                    }
                    done(cb, true, null);
                } catch (Exception ex) {
                    done(cb, false, msg(ex));
                } finally {
                    closeQuietly(raf);
                }
            }
        }).start();
    }

    private static class CdEntry {
        String name;
        int method;
        long compSize;
        long localOff;
        boolean isDir;
    }

    private static long findEocdCentralDirOffset(java.io.RandomAccessFile raf)
            throws java.io.IOException {
        long len = raf.length();
        long start = Math.max(0, len - 65557);
        int scan = (int) (len - start);
        byte[] buf = new byte[scan];
        raf.seek(start);
        raf.readFully(buf);
        for (int i = scan - 22; i >= 0; i--) {
            if (readLeInt(buf, i) == 0x06054b50L) {
                return readLeInt(buf, i + 16);
            }
        }
        return -1;
    }

    private static List<CdEntry> readCentralDir(java.io.RandomAccessFile raf, long cdOff)
            throws java.io.IOException {
        List<CdEntry> out = new ArrayList<CdEntry>();
        raf.seek(cdOff);
        byte[] h = new byte[46];
        while (true) {
            int r = raf.read(h);
            if (r != 46 || readLeInt(h, 0) != 0x02014b50L) break;
            int nameLen = readLeShort(h, 28);
            int extraLen = readLeShort(h, 30);
            int commentLen = readLeShort(h, 32);
            byte[] nameB = new byte[nameLen];
            raf.readFully(nameB);
            raf.skipBytes(extraLen + commentLen);
            CdEntry e = new CdEntry();
            e.name = new String(nameB, "UTF-8");
            e.method = readLeShort(h, 10);
            e.compSize = readLeInt(h, 20);
            e.localOff = readLeInt(h, 42);
            e.isDir = e.name.endsWith("/");
            out.add(e);
        }
        return out;
    }

    private static String extractAesEntry(java.io.RandomAccessFile raf, CdEntry e,
                                           File destDir, String destCanon, boolean overwrite,
                                           String password, CancelToken token) {
        try {
            File out = new File(destDir, e.name);
            String outCanon = out.getCanonicalPath();
            if (!outCanon.startsWith(destCanon)) return "Bỏ qua (ngoài thư mục đích): " + e.name;
            if (e.isDir) {
                if (!out.exists() && !out.mkdirs()) return "Không tạo được thư mục: " + e.name;
                return null;
            }
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return "Không tạo được thư mục: " + e.name;
            }
            if (out.exists() && !overwrite) out = uniqueFile(out);

            // Đọc local header để tìm vị trí data
            raf.seek(e.localOff);
            byte[] lh = new byte[30];
            raf.readFully(lh);
            if (readLeInt(lh, 0) != 0x04034b50L) return "Header lỗi: " + e.name;
            int nameLen = readLeShort(lh, 26);
            int extraLen = readLeShort(lh, 28);
            long dataOff = e.localOff + 30 + nameLen + extraLen;
            raf.seek(dataOff);

            java.io.OutputStream os = null;
            try {
                os = new java.io.BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE);
                if (e.method == AES_METHOD) {
                    if (password == null || password.isEmpty()) {
                        return "File được mã hóa — cần mật khẩu: " + e.name;
                    }
                    String err = decryptAesEntry(raf, e.compSize, password, os, token);
                    if (err != null) return err + ": " + e.name;
                } else if (e.method == 8) {
                    inflateFromRaf(raf, e.compSize, os, token);
                } else if (e.method == 0) {
                    copyFromRaf(raf, e.compSize, os, token);
                } else {
                    return "Phương thức nén không hỗ trợ (" + e.method + "): " + e.name;
                }
            } finally {
                closeQuietly(os);
            }
            return null;
        } catch (Exception ex) {
            return "Giải nén thất bại: " + e.name + " — " + msg(ex);
        }
    }

    /** Giải mã 1 entry AES: salt || pwVerify || ciphertext || mac(10), trả về lỗi hoặc null. */
    private static String decryptAesEntry(java.io.RandomAccessFile raf, long dataLen,
                                           String password, java.io.OutputStream os,
                                           CancelToken token) throws Exception {
        if (dataLen < AES_SALT_LEN + 2 + AES_MAC_LEN) return "Dữ liệu AES lỗi";
        byte[] salt = new byte[AES_SALT_LEN];
        raf.readFully(salt);
        byte[] wantVerify = new byte[2];
        raf.readFully(wantVerify);
        byte[][] keys = deriveAesKeys(password, salt);
        if (wantVerify[0] != keys[2][0] || wantVerify[1] != keys[2][1]) {
            return "Sai mật khẩu";
        }
        long cipherLen = dataLen - AES_SALT_LEN - 2 - AES_MAC_LEN;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(keys[1], "HmacSHA1"));
        AesCtr ctr = new AesCtr(keys[0]);
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        byte[] ibuf = new byte[BUFFER_SIZE];
        byte[] obuf = new byte[BUFFER_SIZE];
        long remain = cipherLen;
        try {
            while (remain > 0) {
                if (cancelled(token)) return CANCELLED;
                int n = raf.read(ibuf, 0, (int) Math.min(ibuf.length, remain));
                if (n < 0) return "Dữ liệu AES bị cắt ngắn";
                remain -= n;
                mac.update(ibuf, 0, n);
                ctr.process(ibuf, 0, n); // giải mã tại chỗ (CTR đối xứng)
                inf.setInput(ibuf, 0, n);
                int c;
                while ((c = inf.inflate(obuf)) > 0) {
                    os.write(obuf, 0, c);
                }
            }
            inf.end();
            byte[] wantMac = new byte[AES_MAC_LEN];
            raf.readFully(wantMac);
            byte[] gotMac = mac.doFinal();
            for (int i = 0; i < AES_MAC_LEN; i++) {
                if (wantMac[i] != gotMac[i]) return "Sai mật khẩu hoặc file hỏng";
            }
            return null;
        } finally {
            try { inf.end(); } catch (Exception ignored) {}
        }
    }

    private static void inflateFromRaf(java.io.RandomAccessFile raf, long len,
                                        java.io.OutputStream os, CancelToken token)
            throws Exception {
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        byte[] ibuf = new byte[BUFFER_SIZE];
        byte[] obuf = new byte[BUFFER_SIZE];
        long remain = len;
        try {
            while (remain > 0) {
                if (cancelled(token)) throw new java.io.IOException(CANCELLED);
                int n = raf.read(ibuf, 0, (int) Math.min(ibuf.length, remain));
                if (n < 0) break;
                remain -= n;
                inf.setInput(ibuf, 0, n);
                int c;
                while ((c = inf.inflate(obuf)) > 0) os.write(obuf, 0, c);
            }
        } finally {
            try { inf.end(); } catch (Exception ignored) {}
        }
    }

    private static void copyFromRaf(java.io.RandomAccessFile raf, long len,
                                     java.io.OutputStream os, CancelToken token)
            throws java.io.IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long remain = len;
        while (remain > 0) {
            if (cancelled(token)) throw new java.io.IOException(CANCELLED);
            int n = raf.read(buf, 0, (int) Math.min(buf.length, remain));
            if (n < 0) break;
            remain -= n;
            os.write(buf, 0, n);
        }
    }

    private static int readLeShort(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private static long readLeInt(byte[] b, int o) {
        return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8)
                | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24);
    }
}
