package com.kaizen.tool;

import java.io.File;

/**
 * M1 — wrapper for java.io.File used by FileAdapter.
 * All fields are filled by the constructor.
 */
public class FileItem {
    public final File file;
    public final boolean isDir;
    public final String name;
    public final long size;
    public final long lastModified;
    /** child count for directories; -1 if the listing failed */
    public final int childCount;
    /** v1.1-m1: package name nếu folder này là /Android/data/<package> */
    public String packageName;
    /** v1.1-m1: tên app hiển thị (từ PackageManager) */
    public String appLabel;
    /** v1.1-m1: true nếu item này liệt kê qua Shizuku (cần copy ra cache trước khi mở) */
    public boolean viaShizuku = false;

    public FileItem(File f) {
        this.file = f;
        this.isDir = f.isDirectory();
        this.name = f.getName();
        this.size = isDir ? 0 : f.length();
        this.lastModified = f.lastModified();
        if (isDir) {
            File[] kids = f.listFiles();
            this.childCount = kids == null ? -1 : kids.length;
        } else {
            this.childCount = -1;
        }
    }

    /** canonical path used as the selection key */
    public String path() {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    /**
     * M2/M3 — virtual entry (e.g. inside an archive). {@code file} is a dummy
     * placeholder built from {@code virtualPath} ("archive:/abs/path.7z!/entry/name")
     * and must never be touched as a real filesystem path. ThumbnailLoader
     * will fail to decode it and fall back to the static icon, which is fine.
     */
    public FileItem(String virtualPath, String name, boolean isDir,
                    long size, long lastModified, int childCount) {
        this.file = new File(virtualPath);
        this.isDir = isDir;
        this.name = name;
        this.size = isDir ? 0 : size;
        this.lastModified = lastModified;
        this.childCount = childCount;
    }
}
