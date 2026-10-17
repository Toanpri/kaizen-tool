package com.kaizen.tool;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * RAR extraction via junrar (pure Java, RAR 4.x + RAR 5.x, extract only).
 * RAR creation is proprietary — not supported, by design.
 *
 * Follows the same async pattern as TarUtils/ZipUtils:
 * background thread + FileOps.OpCallback + CancelToken.
 */
public final class RarUtils {

    private RarUtils() {}

    private static final String CANCELLED = "Đã hủy";

    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    /** True when e or any cause in its chain is our CancelledException. */
    private static boolean isCancel(Throwable e) {
        while (e != null) {
            if (e instanceof CancelledException) return true;
            e = e.getCause();
        }
        return false;
    }

    /**
     * Thrown from the counting stream when the user cancels mid-file.
     * Unchecked so it can propagate through junrar's extractFile
     * (which only declares RarException — it wraps it; we detect it
     * via the cause chain).
     */
    private static final class CancelledException extends RuntimeException {
        CancelledException() { super(CANCELLED); }
    }

    /** Counts bytes written; aborts when the token is cancelled. */
    private static final class CancelAwareOutputStream extends FilterOutputStream {
        private final CancelToken token;
        CancelAwareOutputStream(OutputStream out, CancelToken token) {
            super(out);
            this.token = token;
        }
        @Override public void write(int b) throws IOException {
            if (cancelled(token)) throw new CancelledException();
            out.write(b);
        }
        @Override public void write(byte[] b, int off, int len) throws IOException {
            if (cancelled(token)) throw new CancelledException();
            out.write(b, off, len);
        }
    }

    /**
     * Extract a .rar archive into destDir.
     * Skips existing files when overwrite=false.
     */
    public static void extractRar(final File rarFile, final File destDir,
                                  final boolean overwrite,
                                  final FileOps.OpCallback cb,
                                  final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String setupErr = checkArgs(rarFile, destDir);
                    if (setupErr != null) {
                        done(cb, false, setupErr);
                        return;
                    }
                    final String destCanon =
                            destDir.getCanonicalPath() + File.separator;

                    Archive archive = null;
                    try {
                        archive = new Archive(rarFile);
                    } catch (Exception e) {
                        done(cb, false, "Không mở được file RAR: " + e.getMessage());
                        return;
                    }

                    try {
                        List<FileHeader> headers = archive.getFileHeaders();
                        if (headers == null || headers.isEmpty()) {
                            done(cb, false, "File RAR rỗng hoặc không đọc được");
                            return;
                        }
                        // Count real entries (skip nothing — dirs count too for progress).
                        final int total = headers.size();
                        int doneCount = 0;
                        String firstError = null;

                        for (FileHeader hd : headers) {
                            if (cancelled(token)) {
                                firstError = CANCELLED;
                                break;
                            }
                            String name = hd.getFileName();
                            doneCount++;
                            progress(cb, doneCount, total, name);

                            if (hd.isEncrypted()) {
                                if (firstError == null) {
                                    firstError = "File RAR có mật khẩu: " + name;
                                }
                                continue;
                            }

                            File out = safeOutFile(destDir, destCanon, name);
                            if (out == null) {
                                if (firstError == null) {
                                    firstError = "Đường dẫn không hợp lệ trong RAR: " + name;
                                }
                                continue;
                            }

                            if (hd.isDirectory()) {
                                if (!out.isDirectory() && !out.mkdirs() && !out.isDirectory()) {
                                    if (firstError == null) {
                                        firstError = "Không tạo được thư mục: " + name;
                                    }
                                }
                                continue;
                            }

                            if (out.exists() && !overwrite) continue;

                            File parent = out.getParentFile();
                            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                                if (firstError == null) {
                                    firstError = "Không tạo được thư mục cha: " + name;
                                }
                                continue;
                            }

                            OutputStream fos = null;
                            try {
                                fos = new CancelAwareOutputStream(
                                        new FileOutputStream(out), token);
                                archive.extractFile(hd, fos);
                            } catch (Exception e) {
                                if (isCancel(e)) {
                                    firstError = CANCELLED;
                                    // Remove the half-written file.
                                    try { out.delete(); } catch (Exception ignored) {}
                                    break;
                                }
                                if (firstError == null) {
                                    firstError = "Lỗi giải nén " + name + ": " + e.getMessage();
                                }
                                try { out.delete(); } catch (Exception ignored) {}
                            } finally {
                                if (fos != null) {
                                    try { fos.close(); } catch (IOException ignored) {}
                                }
                            }
                            if (CANCELLED.equals(firstError)) break;
                        }

                        if (CANCELLED.equals(firstError)) {
                            done(cb, false, CANCELLED);
                        } else if (firstError != null) {
                            done(cb, false, firstError);
                        } else {
                            done(cb, true, null);
                        }
                    } finally {
                        try { archive.close(); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    done(cb, false, e.getMessage());
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static String checkArgs(File rarFile, File destDir) {
        if (rarFile == null || !rarFile.isFile()) return "Không tìm thấy file RAR";
        if (destDir == null) return "Thư mục đích không hợp lệ";
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            return "Không tạo được thư mục đích";
        }
        return null;
    }

    /**
     * Resolve an entry name under destDir, rejecting path traversal
     * (../, absolute paths, drive letters). Returns null when unsafe.
     */
    private static File safeOutFile(File destDir, String destCanon, String entryName) {
        if (entryName == null) return null;
        String n = entryName.replace('\\', '/');
        // Reject absolute paths and drive letters.
        if (n.startsWith("/") || (n.length() > 2 && n.charAt(1) == ':')) return null;
        // Reject any ".." segment.
        for (String seg : n.split("/")) {
            if ("..".equals(seg)) return null;
        }
        try {
            File out = new File(destDir, n);
            String canon = out.getCanonicalPath();
            if (!canon.startsWith(destCanon)) return null;
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static void progress(FileOps.OpCallback cb, int d, int t, String name) {
        if (cb == null) return;
        try { cb.onProgress(d, t, name); } catch (Exception ignored) {}
    }

    private static void done(FileOps.OpCallback cb, boolean ok, String err) {
        if (cb == null) return;
        try { cb.onDone(ok, err); } catch (Exception ignored) {}
    }
}
