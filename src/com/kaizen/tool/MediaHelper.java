package com.kaizen.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Helper: nhận diện file media + liệt kê file cùng loại trong folder
 * (dùng cho next/prev trong trình xem).
 */
public class MediaHelper {

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;

    private static final String[] IMG = {
            "jpg", "jpeg", "png", "gif", "webp", "bmp"};
    private static final String[] VID = {
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv"};
    private static final String[] AUD = {
            "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus"};

    /** -1 nếu không phải media. */
    public static int mediaTypeOf(String name) {
        if (name == null) return -1;
        String n = name.toLowerCase(Locale.US);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return -1;
        String ext = n.substring(dot + 1);
        for (String e : IMG) if (e.equals(ext)) return TYPE_IMAGE;
        for (String e : VID) if (e.equals(ext)) return TYPE_VIDEO;
        for (String e : AUD) if (e.equals(ext)) return TYPE_AUDIO;
        return -1;
    }

    /** Liệt kê file cùng loại media trong dir, sắp xếp theo tên. */
    public static List<File> listMedia(File dir, int type) {
        List<File> out = new ArrayList<File>();
        if (dir == null || !dir.isDirectory()) return out;
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (f.isFile() && mediaTypeOf(f.getName()) == type) out.add(f);
        }
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }
}
