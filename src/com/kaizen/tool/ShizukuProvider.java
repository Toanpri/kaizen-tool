package com.kaizen.tool;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/**
 * ShizukuProvider — nhận binder từ Shizuku server.
 *
 * Shizuku server sẽ gọi ContentResolver.call() với method "sendBinder"
 * để gửi IBinder qua. Provider này chỉ cần tồn tại (enabled) để
 * Shizuku phát hiện app trong danh sách.
 *
 * Không phụ thuộc lib chính thức (tránh crash như v1.0-m5).
 */
public class ShizukuProvider extends ContentProvider {

    private static final String TAG = "ShizukuProvider";
    private static final String METHOD_SEND_BINDER = "sendBinder";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "ShizukuProvider created");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Log.d(TAG, "call: " + method);
        if (METHOD_SEND_BINDER.equals(method) && extras != null) {
            try {
                // Thử đọc binder từ extras (key "binder")
                IBinder binder = null;

                // Cách 1: trực tiếp từ Bundle (API 18+)
                try {
                    binder = extras.getBinder("binder");
                } catch (Exception e) {
                    Log.w(TAG, "getBinder failed: " + e.getMessage());
                }

                // Cách 2: qua Parcel (fallback)
                if (binder == null) {
                    try {
                        Parcel p = Parcel.obtain();
                        // Không có cách trực tiếp, bỏ qua
                        p.recycle();
                    } catch (Exception e) {
                        Log.w(TAG, "Parcel fallback failed");
                    }
                }

                if (binder != null) {
                    Log.i(TAG, "Binder received!");
                    ShizukuHelper.onBinderReceived(binder);
                } else {
                    Log.w(TAG, "Binder is null in extras");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling sendBinder", e);
            }
        }
        return null;
    }

    // Các method bắt buộc của ContentProvider (không dùng)
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
