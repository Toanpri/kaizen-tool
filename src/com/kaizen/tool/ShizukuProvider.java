package com.kaizen.tool;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * ShizukuProvider — nhận binder từ Shizuku server.
 *
 * Dựa trên rikka.shizuku.ShizukuProvider (Apache 2.0), rút gọn:
 * - Bỏ Sui dependency (gây crash)
 * - Bỏ multi-process support (không cần)
 * - Giữ nguyên protocol: METHOD_SEND_BINDER + BinderContainer
 *
 * Shizuku server tự động gửi binder khi khởi động cho mọi app
 * khai báo moe.shizuku.manager.permission.API_V23.
 */
public class ShizukuProvider extends ContentProvider {

    private static final String TAG = "ShizukuProvider";

    public static final String METHOD_SEND_BINDER = "sendBinder";
    public static final String METHOD_GET_BINDER = "getBinder";

    private static final String EXTRA_BINDER =
            "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.multiprocess)
            throw new IllegalStateException("android:multiprocess must be false");
        if (!info.exported)
            throw new IllegalStateException("android:exported must be true");
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "ShizukuProvider created");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (extras == null) return null;

        // Quan trọng: set class loader để đọc BinderContainer Parcelable
        extras.setClassLoader(BinderContainer.class.getClassLoader());

        Bundle reply = new Bundle();
        if (METHOD_SEND_BINDER.equals(method)) {
            handleSendBinder(extras);
        } else if (METHOD_GET_BINDER.equals(method)) {
            IBinder binder = ShizukuHelper.getBinder();
            if (binder != null && binder.pingBinder()) {
                reply.putParcelable(EXTRA_BINDER, new BinderContainer(binder));
            } else {
                return null;
            }
        }
        return reply;
    }

    private void handleSendBinder(Bundle extras) {
        // Nếu đã có binder sống thì bỏ qua
        IBinder existing = ShizukuHelper.getBinder();
        if (existing != null && existing.pingBinder()) {
            Log.d(TAG, "sendBinder called but binder already alive");
            return;
        }

        BinderContainer container = extras.getParcelable(EXTRA_BINDER);
        if (container != null && container.binder != null) {
            Log.i(TAG, "Binder received from Shizuku server!");
            ShizukuHelper.onBinderReceived(container.binder);
        } else {
            Log.w(TAG, "sendBinder: no binder in extras");
        }
    }

    // Các method bắt buộc (không dùng)
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
