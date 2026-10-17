package com.kaizen.tool;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * ContentProvider tối giản để serve file .apk cho system installer.
 * Không dùng androidx FileProvider — chỉ cần openFile + getType.
 * Authority: com.kaizen.tool.apkprovider
 */
public class ApkFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kaizen.tool.apkprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * uri có dạng: content://com.kaizen.tool.apkprovider/<absolute_path>
     * Chỉ serve file .apk tồn tại và readable; từ chối path chứa "..".
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || path.contains("..")) {
            throw new FileNotFoundException("Invalid path");
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile() || !file.canRead()
                || !file.getName().toLowerCase().endsWith(".apk")) {
            throw new FileNotFoundException("APK not found: " + path);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    /** Build URI dạng content://com.kaizen.tool.apkprovider/<absolute_path> */
    public static Uri uriFor(File apkFile) {
        return Uri.parse("content://" + AUTHORITY + apkFile.getAbsolutePath());
    }
}
