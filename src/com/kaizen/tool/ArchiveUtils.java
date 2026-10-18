package com.kaizen.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * M3 — central dispatcher for archive formats.
 *
 * Kinds:
 *   KIND_ZIP  — .zip (browsable)
 *   KIND_7Z   — .7z (browsable)
 *   KIND_TAR  — .tar, .tar.gz, .tgz (browsable)
 *   KIND_GZ   — .gz single stream (extract only)
 *   KIND_BZ2  — .bz2 single stream (extract only)
 *   KIND_XZ   — .xz single stream (extract only)
 *   KIND_RAR  — .rar (extract only, via junrar — no browsing)
 *   KIND_REM  — .rem (Kaizen Tool độc quyền: deflate + XOR obfuscation, browsable)
 */
public class ArchiveUtils {

    public static final int KIND_ZIP = 0;
    public static final int KIND_7Z = 1;
    public static final int KIND_TAR = 2;
    public static final int KIND_GZ = 3;
    public static final int KIND_BZ2 = 4;
    public static final int KIND_XZ = 5;
    public static final int KIND_RAR = 6;
    public static final int KIND_REM = 7;

    /** -1 when the name is not a supported archive. Check multi-part extensions first. */
    public static int kindForName(String name) {
        if (name == null) return -1;
        String n = name.toLowerCase();
        if (n.endsWith(".zip")) return KIND_ZIP;
        if (n.endsWith(".7z")) return KIND_7Z;
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar")) return KIND_TAR;
        if (n.endsWith(".gz")) return KIND_GZ;
        if (n.endsWith(".bz2")) return KIND_BZ2;
        if (n.endsWith(".xz")) return KIND_XZ;
        if (n.endsWith(".rar")) return KIND_RAR;
        if (n.endsWith(".rem")) return KIND_REM;
        return -1;
    }

    /** True for formats that can be opened like a folder. */
    public static boolean isBrowsable(int kind) {
        return kind == KIND_ZIP || kind == KIND_7Z || kind == KIND_TAR || kind == KIND_REM;
    }

    /** True when the tar stream is gzip-compressed (.tar.gz / .tgz). */
    public static boolean isTarGzip(File f) {
        if (f == null) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".tar.gz") || n.endsWith(".tgz");
    }

    public static String kindLabel(int kind) {
        switch (kind) {
            case KIND_ZIP: return "ZIP";
            case KIND_7Z: return "7Z";
            case KIND_TAR: return "TAR";
            case KIND_GZ: return "GZ";
            case KIND_BZ2: return "BZ2";
            case KIND_XZ: return "XZ";
            case KIND_RAR: return "RAR";
            case KIND_REM: return "REM";
            default: return "?";
        }
    }

    // ------------------------------------------------------------------
    // List / extract dispatch
    // ------------------------------------------------------------------

    /** Unified entry listing. Returns an empty list on any error. */
    public static List<ArchiveEntryInfo> listEntries(File archiveFile, int kind) {
        if (kind == KIND_ZIP) {
            return zipToCommon(ZipUtils.listEntries(archiveFile));
        } else if (kind == KIND_7Z) {
            return SevenZUtils.listEntries(archiveFile);
        } else if (kind == KIND_TAR) {
            return TarUtils.listEntries(archiveFile, isTarGzip(archiveFile));
        } else if (kind == KIND_REM) {
            return RemArchive.listEntries(archiveFile);
        }
        return new ArrayList<ArchiveEntryInfo>();
    }

    /** Extract the whole archive. */
    public static void extractAll(File archiveFile, int kind, File destDir,
                                  boolean overwrite, FileOps.OpCallback cb,
                                  CancelToken token) {
        extractAll(archiveFile, kind, destDir, overwrite, null, cb, token);
    }

    /** Extract với mật khẩu (cho ZIP AES / REM mã hóa). */
    public static void extractAll(File archiveFile, int kind, File destDir,
                                  boolean overwrite, String password, FileOps.OpCallback cb,
                                  CancelToken token) {
        if (kind == KIND_ZIP) {
            if (password != null || ZipUtils.isEncryptedZip(archiveFile)) {
                ZipUtils.extractZipEncrypted(archiveFile, destDir, overwrite, password, cb, token);
            } else {
                ZipUtils.extractZip(archiveFile, destDir, overwrite, cb, token);
            }
        } else if (kind == KIND_7Z) {
            SevenZUtils.extract7z(archiveFile, destDir, overwrite, cb, token);
        } else if (kind == KIND_TAR) {
            TarUtils.extractTar(archiveFile, isTarGzip(archiveFile), destDir, overwrite, cb, token);
        } else if (kind == KIND_GZ || kind == KIND_BZ2 || kind == KIND_XZ) {
            TarUtils.extractSingle(archiveFile, kind, destDir, overwrite, cb, token);
        } else if (kind == KIND_RAR) {
            RarUtils.extractRar(archiveFile, destDir, overwrite, cb, token);
        } else if (kind == KIND_REM) {
            RemArchive.extractRem(archiveFile, destDir, overwrite, password, cb, token);
        } else if (cb != null) {
            cb.onDone(false, "Định dạng không hỗ trợ");
        }
    }

    /** Extract selected entries (archive browser mode). */
    public static void extractEntries(File archiveFile, int kind, List<String> entryNames,
                                      File destDir, boolean overwrite, FileOps.OpCallback cb,
                                      CancelToken token) {
        extractEntries(archiveFile, kind, entryNames, destDir, overwrite, null, cb, token);
    }

    /** Extract entries với mật khẩu (cho ZIP AES / REM mã hóa). */
    public static void extractEntries(File archiveFile, int kind, List<String> entryNames,
                                      File destDir, boolean overwrite, String password,
                                      FileOps.OpCallback cb, CancelToken token) {
        // v1.1-m3 fix: nếu entry là thư mục (vd "Sao chép" folder trong archive),
        // phải bung kèm toàn bộ file con bên trong — nếu không chỉ tạo vỏ folder rỗng.
        List<String> expanded = expandDirEntries(archiveFile, kind, entryNames);
        if (kind == KIND_ZIP) {
            if (password != null || ZipUtils.isEncryptedZip(archiveFile)) {
                // TODO: extractEntries cho ZIP mã hóa — hiện tại giải nén toàn bộ
                ZipUtils.extractZipEncrypted(archiveFile, destDir, overwrite, password, cb, token);
            } else {
                ZipUtils.extractEntries(archiveFile, expanded, destDir, overwrite, cb, token);
            }
        } else if (kind == KIND_7Z) {
            SevenZUtils.extractEntries(archiveFile, expanded, destDir, overwrite, cb, token);
        } else if (kind == KIND_TAR) {
            TarUtils.extractEntries(archiveFile, isTarGzip(archiveFile),
                    expanded, destDir, overwrite, cb, token);
        } else if (kind == KIND_REM) {
            RemArchive.extractEntries(archiveFile, expanded, destDir, overwrite, password, cb, token);
        } else if (cb != null) {
            cb.onDone(false, "Định dạng không hỗ trợ");
        }
    }

    /**
     * Bung các entry thư mục thành: chính nó + toàn bộ entry con bên trong.
     * Entry file giữ nguyên. Trả về list mới (giữ thứ tự, không trùng).
     */
    private static List<String> expandDirEntries(File archiveFile, int kind,
                                                 List<String> entryNames) {
        if (entryNames == null || entryNames.isEmpty()) return entryNames;
        boolean hasDir = false;
        for (String n : entryNames) {
            if (n != null && n.endsWith("/")) { hasDir = true; break; }
        }
        if (!hasDir) return entryNames; // toàn file lẻ — khỏi liệt kê lại archive
        List<ArchiveEntryInfo> all;
        try {
            all = listEntries(archiveFile, kind);
        } catch (Exception e) {
            return entryNames;
        }
        if (all == null || all.isEmpty()) return entryNames;
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (String n : entryNames) {
            if (n == null) continue;
            if (n.endsWith("/")) {
                // Thêm chính thư mục nếu archive có entry tường minh cho nó
                // (nhiều ZIP không lưu entry folder riêng — chỉ có file con).
                for (ArchiveEntryInfo e : all) {
                    if (e.name.equals(n)) { out.add(n); break; }
                }
                // Rồi toàn bộ con cháu bên trong.
                for (ArchiveEntryInfo e : all) {
                    if (e.name.startsWith(n) && !e.name.equals(n)) {
                        out.add(e.name);
                    }
                }
            } else {
                out.add(n);
            }
        }
        return new java.util.ArrayList<String>(out);
    }

    /** Archive có yêu cầu mật khẩu khi giải nén không (ZIP AES / REM mã hóa). */
    public static boolean needsPassword(File archiveFile, int kind) {
        try {
            if (kind == KIND_ZIP) return ZipUtils.isEncryptedZip(archiveFile);
            if (kind == KIND_REM) return RemArchive.needsPassword(archiveFile);
        } catch (Exception ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static List<ArchiveEntryInfo> zipToCommon(List<ZipUtils.ZipEntryInfo> in) {
        List<ArchiveEntryInfo> out = new ArrayList<ArchiveEntryInfo>(in.size());
        for (ZipUtils.ZipEntryInfo e : in) {
            out.add(new ArchiveEntryInfo(e.name, e.isDir, e.size, e.time));
        }
        return out;
    }
}
