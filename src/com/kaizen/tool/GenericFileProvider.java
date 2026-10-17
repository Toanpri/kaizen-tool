package com.kaizen.tool;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * ContentProvider tối giản để serve file bất kỳ (share file).
 * Không dùng androidx FileProvider — chỉ cần openFile + getType.
 * Authority: com.kaizen.tool.fileprovider
 */
public class GenericFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kaizen.tool.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * uri có dạng: content://com.kaizen.tool.fileprovider/<absolute_path>
     * Serve mọi file tồn tại và readable; từ chối path chứa "..".
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || path.contains("..")) {
            throw new FileNotFoundException("Invalid path");
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Đoán MIME theo đuôi file; fallback "application/octet-stream".
     */
    @Override
    public String getType(Uri uri) {
        String ext = null;
        String path = uri.getPath();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                ext = path.substring(dot + 1).toLowerCase(Locale.US);
            }
        }
        String mime = ext == null ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    /**
     * Trả về DISPLAY_NAME + SIZE — nhiều app nhận (Gmail, Zalo, Drive...)
     * query 2 cột này để hiện tên/kích thước file; trả null là share fail.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String path = uri.getPath();
        if (path == null) return null;
        File file = new File(path);
        MatrixCursor cursor = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 1);
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
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

    /**
     * Build URI dạng content://com.kaizen.tool.fileprovider/<absolute_path>.
     * Dùng Uri.Builder thay vì Uri.parse nối chuỗi thô — file tên có '#', '?',
     * khoảng trắng hay tiếng Việt sẽ bị parse sai (cắt path) nếu nối chuỗi.
     */
    public static Uri uriFor(File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(f.getAbsolutePath())
                .build();
    }
}
