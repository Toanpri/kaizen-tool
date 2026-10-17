package com.kaizen.tool;

/**
 * M3 — common archive entry descriptor shared by ZIP / 7Z / TAR browsers.
 * {@code name} is the full entry path inside the archive using '/' separators.
 */
public class ArchiveEntryInfo {
    public String name;
    public boolean isDir;
    public long size;
    public long time;

    public ArchiveEntryInfo() {
    }

    public ArchiveEntryInfo(String name, boolean isDir, long size, long time) {
        this.name = name;
        this.isDir = isDir;
        this.size = size;
        this.time = time;
    }
}
