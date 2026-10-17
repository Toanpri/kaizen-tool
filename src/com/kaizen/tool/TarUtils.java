package com.kaizen.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/**
 * M3 — TAR / compressed-stream core for Kaizen Tool (Apache Commons Compress).
 *
 * Same contract as ZipUtils/SevenZUtils: every public operation spawns its own
 * background thread and invokes FileOps.OpCallback ON THAT THREAD.
 *
 * Supported:
 *  - create: .tar, .tar.gz / .tgz
 *  - extract: .tar, .tar.gz, .tgz, plus single-stream .gz / .bz2 / .xz
 *  - browse: .tar, .tar.gz, .tgz (sequential scan)
 */
public class TarUtils {

    private static final int BUFFER_SIZE = 8192;

    /** Reported as the failure message when the user cancels the operation. */
    private static final String CANCELLED = "Đã hủy";

    /** Null-safe cancel poll: a null token means "no cancellation support". */
    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    // ------------------------------------------------------------------
    // Create TAR / TAR.GZ
    // ------------------------------------------------------------------

    /**
     * Archive sources (files/folders, recursive) into destTar.
     * gzip=true writes a .tar.gz (gzipLevel = Deflater level 0..9, ignored when false).
     * If destTar exists it is auto-renamed.
     */
    public static void createTar(final List<File> sources, final File destTar,
                                 final boolean gzip, final int gzipLevel,
                                 final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sources == null || sources.isEmpty()) {
                        done(cb, false, "Không có mục nào để nén");
                        return;
                    }
                    if (destTar == null) {
                        done(cb, false, "File đích không hợp lệ");
                        return;
                    }
                    for (File s : sources) {
                        if (s == null || !s.exists()) {
                            done(cb, false, "Mục không tồn tại: " + (s != null ? s.getName() : "null"));
                            return;
                        }
                    }

                    File parent = destTar.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    File out = ZipUtils.uniqueFile(destTar);

                    for (File s : sources) {
                        if (s.isDirectory() && isInside(out, s)) {
                            done(cb, false, "Không thể nén thư mục vào chính nó");
                            return;
                        }
                    }

                    final int total = countAll(sources);
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    OutputStream fos = null;
                    OutputStream comp = null;
                    TarArchiveOutputStream tos = null;
                    try {
                        fos = new FileOutputStream(out);
                        if (gzip) {
                            GzipParameters params = new GzipParameters();
                            params.setCompressionLevel(clampLevel(gzipLevel));
                            comp = new GzipCompressorOutputStream(fos, params);
                        } else {
                            comp = fos;
                        }
                        tos = new TarArchiveOutputStream(comp);
                        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                        final TarArchiveOutputStream tout = tos;
                        for (File src : sources) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            String err = addRecursive(src, src.getParentFile(), tout,
                                    total, done, cb, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                        }
                        tout.finish();
                    } finally {
                        // closing tos closes the whole chain (comp -> fos)
                        closeQuietly(tos);
                        if (tos == null) {
                            closeQuietly(comp);
                            if (comp != fos) closeQuietly(fos);
                        }
                    }

                    if (firstError[0] != null) {
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
    // Extract TAR / TAR.GZ / TGZ
    // ------------------------------------------------------------------

    /**
     * Extract a tar archive. gzip=true for .tar.gz / .tgz.
     * overwrite=false -> conflicting files are auto-renamed.
     * Path-traversal protection: entries escaping destDir are skipped.
     */
    public static void extractTar(final File tarFile, final boolean gzip, final File destDir,
                                  final boolean overwrite, final FileOps.OpCallback cb,
                                  final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String setupErr = checkExtractArgs(tarFile, destDir);
                    if (setupErr != null) {
                        done(cb, false, setupErr);
                        return;
                    }
                    final String destCanon = destDir.getCanonicalPath() + File.separator;
                    final int total = listEntries(tarFile, gzip).size();
                    final int[] done = new int[1];
                    final String[] firstError = new String[1];

                    TarArchiveInputStream tis = null;
                    try {
                        tis = openTarInput(tarFile, gzip);
                        TarArchiveEntry e;
                        while ((e = tis.getNextTarEntry()) != null) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            String name = e.getName();
                            progress(cb, done[0] + 1, total, name);
                            String err = extractCurrent(tis, e, destDir, destCanon, overwrite, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(tis);
                    }

                    done(cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Extract selected entries (used by the TAR browser)
    // ------------------------------------------------------------------

    /** Extract only the named entries. Names are exact entry names. */
    public static void extractEntries(final File tarFile, final boolean gzip,
                                      final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String setupErr = checkExtractArgs(tarFile, destDir);
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

                    TarArchiveInputStream tis = null;
                    try {
                        tis = openTarInput(tarFile, gzip);
                        TarArchiveEntry e;
                        while ((e = tis.getNextTarEntry()) != null && done[0] < total) {
                            if (cancelled(token)) {
                                firstError[0] = CANCELLED;
                                break;
                            }
                            if (!wanted.contains(e.getName())) continue;
                            String name = e.getName();
                            progress(cb, done[0] + 1, total, name);
                            String err = extractCurrent(tis, e, destDir, destCanon, overwrite, token);
                            if (err != null) {
                                firstError[0] = err;
                                break;
                            }
                            done[0]++;
                        }
                    } finally {
                        closeQuietly(tis);
                    }

                    done(cb, firstError[0] == null, firstError[0]);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Decompress single-stream files (.gz / .bz2 / .xz)
    // ------------------------------------------------------------------

    /**
     * kind: ArchiveUtils.KIND_GZ / KIND_BZ2 / KIND_XZ.
     * Output name = source name minus the extension, in destDir.
     */
    public static void extractSingle(final File srcFile, final int kind,
                                     final File destDir, final boolean overwrite,
                                     final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (srcFile == null || !srcFile.isFile()) {
                        done(cb, false, "File không tồn tại");
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
                    String outName = stripSingleExtension(srcFile.getName(), kind);
                    if (outName.isEmpty()) outName = srcFile.getName() + ".out";
                    File out = new File(destDir, outName);
                    if (out.exists() && !overwrite) {
                        out = ZipUtils.uniqueFile(out);
                    }
                    if (cancelled(token)) {
                        done(cb, false, CANCELLED);
                        return;
                    }

                    final long total = srcFile.length();
                    long written = 0;

                    InputStream fis = null;
                    InputStream cis = null;
                    OutputStream os = null;
                    try {
                        fis = new FileInputStream(srcFile);
                        cis = wrapDecompressor(fis, kind);
                        os = new FileOutputStream(out);
                        byte[] buf = new byte[BUFFER_SIZE];
                        int n;
                        long sinceCheck = 0;
                        while ((n = cis.read(buf)) != -1) {
                            os.write(buf, 0, n);
                            written += n;
                            sinceCheck += n;
                            if (sinceCheck >= 65536) {
                                sinceCheck = 0;
                                if (cancelled(token)) {
                                    done(cb, false, CANCELLED);
                                    return;
                                }
                            }
                            progress(cb, (int) Math.min(written, Integer.MAX_VALUE),
                                    (int) Math.min(total, Integer.MAX_VALUE), outName);
                        }
                    } finally {
                        closeQuietly(os);
                        closeQuietly(cis);
                        closeQuietly(fis);
                    }
                    done(cb, true, null);
                } catch (Exception e) {
                    done(cb, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // List entries (never throws)
    // ------------------------------------------------------------------

    /** Sequential scan of a tar archive. Returns an empty list on any error. */
    public static List<ArchiveEntryInfo> listEntries(File tarFile, boolean gzip) {
        List<ArchiveEntryInfo> list = new ArrayList<ArchiveEntryInfo>();
        if (tarFile == null || !tarFile.isFile()) return list;
        TarArchiveInputStream tis = null;
        try {
            tis = openTarInput(tarFile, gzip);
            TarArchiveEntry e;
            while ((e = tis.getNextTarEntry()) != null) {
                String name = e.getName();
                if (name == null) continue;
                ArchiveEntryInfo info = new ArchiveEntryInfo();
                info.name = name;
                info.isDir = e.isDirectory();
                long size = e.getSize();
                info.size = size < 0 ? 0 : size;
                java.util.Date d = e.getModTime();
                info.time = d != null ? d.getTime() : 0;
                list.add(info);
            }
        } catch (Exception ignored) {
            return new ArrayList<ArchiveEntryInfo>();
        } finally {
            closeQuietly(tis);
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static TarArchiveInputStream openTarInput(File tarFile, boolean gzip)
            throws Exception {
        InputStream fis = new FileInputStream(tarFile);
        InputStream cis = gzip ? new GzipCompressorInputStream(fis) : fis;
        return new TarArchiveInputStream(cis);
    }

    private static InputStream wrapDecompressor(InputStream in, int kind) throws Exception {
        if (kind == ArchiveUtils.KIND_GZ) {
            return new GzipCompressorInputStream(in);
        } else if (kind == ArchiveUtils.KIND_BZ2) {
            return new BZip2CompressorInputStream(in);
        } else if (kind == ArchiveUtils.KIND_XZ) {
            return new XZCompressorInputStream(in);
        }
        throw new IllegalArgumentException("Định dạng không hỗ trợ");
    }

    private static String stripSingleExtension(String name, int kind) {
        String lower = name.toLowerCase();
        if (kind == ArchiveUtils.KIND_GZ && lower.endsWith(".gz")) {
            return name.substring(0, name.length() - 3);
        }
        if (kind == ArchiveUtils.KIND_BZ2 && lower.endsWith(".bz2")) {
            return name.substring(0, name.length() - 4);
        }
        if (kind == ArchiveUtils.KIND_XZ && lower.endsWith(".xz")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static String checkExtractArgs(File tarFile, File destDir) {
        if (tarFile == null || !tarFile.isFile()) {
            return "File TAR không tồn tại";
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
     * Extract the CURRENT entry of tis (reader positioned on it).
     * Returns null on success, error message on failure.
     */
    private static String extractCurrent(TarArchiveInputStream tis, TarArchiveEntry e,
                                         File destDir, String destCanon, boolean overwrite,
                                         CancelToken token) {
        try {
            String name = e.getName();
            File out = new File(destDir, name);
            String outCanon;
            try {
                outCanon = out.getCanonicalPath();
            } catch (Exception ex) {
                return null; // skip silently
            }
            if (!outCanon.startsWith(destCanon)) {
                return null; // path traversal -> skip silently
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
                os = new FileOutputStream(out);
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                long sinceCheck = 0;
                while ((n = tis.read(buf)) != -1) {
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
            java.util.Date d = e.getModTime();
            if (d != null && d.getTime() > 0) out.setLastModified(d.getTime());
        } catch (Exception ex) {
            return "Giải nén thất bại: " + e.getName() + " — " + msg(ex);
        }
        return null;
    }

    private static String addRecursive(File src, File baseDir, TarArchiveOutputStream tos,
                                       int total, int[] done, FileOps.OpCallback cb,
                                       CancelToken token) {
        if (cancelled(token)) return CANCELLED;
        String rel = relativePath(baseDir, src);
        try {
            if (src.isDirectory() && !rel.endsWith("/")) {
                rel = rel + "/";
            }
            TarArchiveEntry entry = new TarArchiveEntry(src, rel);
            // preserve mtime explicitly (constructor already copies it, but be safe)
            entry.setModTime(src.lastModified());
            tos.putArchiveEntry(entry);
            if (!src.isDirectory()) {
                InputStream in = null;
                try {
                    in = new FileInputStream(src);
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    long sinceCheck = 0;
                    while ((n = in.read(buf)) != -1) {
                        tos.write(buf, 0, n);
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
            tos.closeArchiveEntry();
        } catch (Exception e) {
            return "Nén thất bại: " + src.getName() + " — " + msg(e);
        }
        progress(cb, ++done[0], total, rel);
        if (src.isDirectory()) {
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (cancelled(token)) return CANCELLED;
                    String err = addRecursive(child, baseDir, tos, total, done, cb, token);
                    if (err != null) return err;
                }
            }
        }
        return null;
    }

    private static String relativePath(File baseDir, File f) {
        String base = baseDir.getAbsolutePath();
        String full = f.getAbsolutePath();
        String rel = full.startsWith(base) ? full.substring(base.length()) : f.getName();
        while (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace(File.separatorChar, '/');
    }

    private static int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 9) return 9;
        return level;
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
