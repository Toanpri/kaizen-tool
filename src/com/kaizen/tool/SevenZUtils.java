package com.kaizen.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.tukaani.xz.LZMA2Options;

/**
 * M3 — 7z core for Kaizen Tool (Apache Commons Compress, pure Java).
 *
 * Mirrors the ZipUtils contract: every public operation spawns its own
 * background thread and invokes FileOps.OpCallback ON THAT THREAD — the
 * caller must post results to the UI thread itself.
 */
public class SevenZUtils {

    private static final int BUFFER_SIZE = 65536; // 64KB: giảm syscall

    /** Reported as the failure message when the user cancels the operation. */
    private static final String CANCELLED = "Đã hủy";

    /** Null-safe cancel poll: a null token means "no cancellation support". */
    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    // ------------------------------------------------------------------
    // Create 7z
    // ------------------------------------------------------------------

    /**
     * Compress sources (files/folders, recursive, structure preserved) into dest7z.
     * level: 0 = store (COPY), 1 = fast (LZMA2 preset 1),
     *        6 = normal (LZMA2 preset 5), 9 = max (LZMA2 preset 9).
     * If dest7z exists it is auto-renamed ("name (1).7z", ...).
     */
    public static void create7z(final List<File> sources, final File dest7z,
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
                    if (dest7z == null) {
                        done(cb, false, "File đích không hợp lệ");
                        return;
                    }
                    for (File s : sources) {
                        if (s == null || !s.exists()) {
                            done(cb, false, "Mục không tồn tại: " + (s != null ? s.getName() : "null"));
                            return;
                        }
                    }

                    File parent = dest7z.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    File out = ZipUtils.uniqueFile(dest7z);

                    // Guard: refuse to archive a folder into itself.
                    for (File s : sources) {
                        if (s.isDirectory() && isInside(out, s)) {
                            done(cb, false, "Không thể nén thư mục vào chính nó");
                            return;
                        }
                    }

                    final int total = countAll(sources);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    SevenZOutputFile szo = null;
                    try {
                        szo = new SevenZOutputFile(out);
                        if (level <= 0) {
                            szo.setContentCompression(SevenZMethod.COPY);
                        } else {
                            int preset = level <= 1 ? 1 : (level <= 6 ? 5 : 9);
                            szo.setContentMethods(Collections.singletonList(
                                    new SevenZMethodConfiguration(
                                            SevenZMethod.LZMA2, new LZMA2Options(preset))));
                        }
                        for (File src : sources) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            String err = addRecursive(src, src.getParentFile(), szo,
                                    total, done, cb, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                        }
                        szo.finish();
                    } finally {
                        closeQuietly(szo);
                    }

                    if (firstError[0] != null) {
                        out.delete(); // best effort: drop the incomplete archive
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
    // Extract whole 7z
    // ------------------------------------------------------------------

    /**
     * Extract all entries of sevenZFile into destDir.
     * overwrite=false -> conflicting files are auto-renamed.
     * Path-traversal protection: entries escaping destDir are skipped.
     */
    public static void extract7z(final File sevenZFile, final File destDir,
                                 final boolean overwrite, final FileOps.OpCallback cb,
                                 final CancelToken token) {
        extract7z(sevenZFile, destDir, overwrite, null, cb, token);
    }

    public static void extract7z(final File sevenZFile, final File destDir,
                                 final boolean overwrite, final String password,
                                 final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String setupErr = checkExtractArgs(sevenZFile, destDir);
                    if (setupErr != null) {
                        done(cb, false, setupErr);
                        return;
                    }
                    final String destCanon = destDir.getCanonicalPath() + File.separator;
                    final int total = listEntries(sevenZFile).size();
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    SevenZFile szf = null;
                    try {
                        szf = openSevenZ(sevenZFile, password);
                        SevenZArchiveEntry e;
                        while ((e = szf.getNextEntry()) != null) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            String name = e.getName();
                            progress(cb, done[0] + 1, total, name);
                            String err = extractCurrent(szf, e, destDir, destCanon, overwrite, "", token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(szf);
                    }

                    done(cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Extract selected entries (used by the 7z browser)
    // ------------------------------------------------------------------

    /** Extract only the named entries into destDir. Names are exact entry names. */
    public static void extractEntries(final File sevenZFile, final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final String stripPrefix,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        extractEntries(sevenZFile, entryNames, destDir, overwrite, stripPrefix, null, cb, token);
    }

    public static void extractEntries(final File sevenZFile, final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final String stripPrefix, final String password,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String setupErr = checkExtractArgs(sevenZFile, destDir);
                    if (setupErr != null) {
                        done(cb, false, setupErr);
                        return;
                    }
                    if (entryNames == null || entryNames.isEmpty()) {
                        done(cb, false, "Không có mục nào để giải nén");
                        return;
                    }
                    final String destCanon = destDir.getCanonicalPath() + File.separator;
                    final Set<String> wanted = new HashSet<String>(entryNames);
                    final int total = entryNames.size();
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    SevenZFile szf = null;
                    try {
                        szf = openSevenZ(sevenZFile, password);
                        SevenZArchiveEntry e;
                        while ((e = szf.getNextEntry()) != null && done[0] < total) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            if (!wanted.contains(e.getName())) continue;
                            String name = e.getName();
                            progress(cb, done[0] + 1, total, name);
                            String err = extractCurrent(szf, e, destDir, destCanon, overwrite, stripPrefix, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(szf);
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

    /** List entries of a 7z file. Returns an empty list on any error. */
    public static List<ArchiveEntryInfo> listEntries(File sevenZFile) {
        List<ArchiveEntryInfo> list = new ArrayList<ArchiveEntryInfo>();
        if (sevenZFile == null || !sevenZFile.isFile()) return list;
        SevenZFile szf = null;
        try {
            szf = new SevenZFile(sevenZFile);
            SevenZArchiveEntry e;
            while ((e = szf.getNextEntry()) != null) {
                String name = e.getName();
                if (name == null) continue;
                ArchiveEntryInfo info = new ArchiveEntryInfo();
                info.name = name;
                info.isDir = e.isDirectory();
                long size = e.getSize();
                info.size = size < 0 ? 0 : size;
                java.util.Date d = e.getLastModifiedDate();
                info.time = d != null ? d.getTime() : 0;
                list.add(info);
            }
        } catch (Exception ignored) {
            return new ArrayList<ArchiveEntryInfo>();
        } finally {
            closeQuietly(szf);
        }
        return list;
    }

    /** Mo 7z, co mat khau thi dung ban co mat khau (SevenZFile 1.26). */
    private static SevenZFile openSevenZ(File f, String password) throws java.io.IOException {
        if (password != null && !password.isEmpty()) {
            return new SevenZFile(f, password.toCharArray());
        }
        return new SevenZFile(f);
    }

    /**
     * 7z co yeu cau mat khau khong (header ma hoa, hoac entry noi dung ma hoa).
     * Do bang probe: mo khong mat khau, entry nao doi mat khau khi mo stream
     * thi ket luan co ma hoa. Header ma hoa thi constructor nem ngay.
     */
    public static boolean needsPassword(File sevenZFile) {
        if (sevenZFile == null || !sevenZFile.isFile()) return false;
        SevenZFile szf = null;
        try {
            szf = new SevenZFile(sevenZFile);
            SevenZArchiveEntry e;
            while ((e = szf.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                try {
                    java.io.InputStream in = szf.getInputStream(e);
                    closeQuietly(in);
                } catch (PasswordRequiredException pre) {
                    return true;
                } catch (Exception ignored) {
                    // Entry loi kieu khac — khong phai chuyen mat khau.
                }
            }
            return false;
        } catch (Exception ex) {
            // Khong mo duoc header: neu dung la file 7z thi nhieu kha nang
            // header ma hoa -> hoi mat khau, sai thi luc giai nen se bao loi.
            return has7zMagic(sevenZFile);
        } finally {
            closeQuietly(szf);
        }
    }

    private static boolean has7zMagic(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] m = new byte[6];
                if (in.read(m) != 6) return false;
                return m[0] == '7' && m[1] == 'z' && m[2] == (byte) 0xBC
                        && m[3] == (byte) 0xAF && m[4] == (byte) 0x27 && m[5] == (byte) 0x1C;
            } finally {
                try { in.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static String checkExtractArgs(File sevenZFile, File destDir) {
        if (sevenZFile == null || !sevenZFile.isFile()) {
            return "File 7z không tồn tại";
        }
        if (destDir == null) {
            return "Thư mục đích không hợp lệ";
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            return "Không tạo được thư mục đích";
        }
        return null;
    }

    /**
     * Extract the CURRENT entry of szf (the reader must be positioned on it).
     * Returns null on success, error message on failure.
     */
    private static String extractCurrent(SevenZFile szf, SevenZArchiveEntry e,
                                         File destDir, String destCanon, boolean overwrite,
                                         String stripPrefix, CancelToken token) {
        try {
            String name = ArchiveUtils.stripPrefix(e.getName(), stripPrefix);
            if (name == null || name.isEmpty()) return null; // chính gốc đã chọn
            File out = new File(destDir, name);
            String outCanon;
            try {
                outCanon = out.getCanonicalPath();
            } catch (Exception ex) {
                return null; // skip silently: cannot resolve safely
            }
            if (!outCanon.startsWith(destCanon)) {
                return null; // path traversal attempt -> skip silently
            }

            if (e.isDirectory()) {
                if (!out.exists() && !out.mkdirs()) {
                    return "Không tạo được thư mục: " + name;
                }
                return null;
            }
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return "Không tạo được thư mục: " + name;
            }
            if (out.exists() && !overwrite) {
                out = ZipUtils.uniqueFile(out);
                parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
            }
            OutputStream os = null;
            try {
                os = new java.io.BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE);
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                long sinceCheck = 0;
                while ((n = szf.read(buf, 0, buf.length)) > 0) {
                    os.write(buf, 0, n);
                    sinceCheck += n;
                    if (sinceCheck >= 65536) {
                        sinceCheck = 0;
                        if (cancelled(token)) return CANCELLED;
                    }
                }
            } finally {
                closeQuietly(os);
            }
            java.util.Date d = e.getLastModifiedDate();
            if (d != null && d.getTime() > 0) out.setLastModified(d.getTime());
        } catch (Exception ex) {
            return "Giải nén thất bại: " + e.getName() + " — " + msg(ex);
        }
        return null;
    }

    /**
     * Add src (file or folder) recursively. Entry names are relative to baseDir
     * with '/' separators and no leading slash.
     */
    private static String addRecursive(File src, File baseDir, SevenZOutputFile szo,
                                       int total, int[] done, FileOps.OpCallback cb,
                                       CancelToken token) {
        if (cancelled(token)) return CANCELLED;
        String rel = relativePath(baseDir, src);
        try {
            SevenZArchiveEntry entry = szo.createArchiveEntry(src, rel);
            szo.putArchiveEntry(entry);
            if (!src.isDirectory()) {
                InputStream in = null;
                try {
                    in = new java.io.BufferedInputStream(new FileInputStream(src), BUFFER_SIZE);
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    long sinceCheck = 0;
                    while ((n = in.read(buf)) != -1) {
                        szo.write(buf, 0, n);
                        sinceCheck += n;
                        if (sinceCheck >= 65536) {
                            sinceCheck = 0;
                            if (cancelled(token)) return CANCELLED;
                        }
                    }
                } finally {
                    closeQuietly(in);
                }
            }
            szo.closeArchiveEntry();
        } catch (Exception e) {
            return "Nén thất bại: " + src.getName() + " — " + msg(e);
        }
        progress(cb, ++done[0], total, rel);
        if (src.isDirectory()) {
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (cancelled(token)) return CANCELLED;
                    String err = addRecursive(child, baseDir, szo, total, done, cb, token);
                    if (err != null) return err;
                }
            }
        }
        return null;
    }

    /** Path of f relative to baseDir, using '/' as separator, no leading slash. */
    private static String relativePath(File baseDir, File f) {
        String base = baseDir.getAbsolutePath();
        String full = f.getAbsolutePath();
        String rel = full.startsWith(base) ? full.substring(base.length()) : f.getName();
        while (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace(File.separatorChar, '/');
    }

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
}
