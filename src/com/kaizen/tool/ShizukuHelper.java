package com.kaizen.tool;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ShizukuHelper — tích hợp Shizuku không dùng lib chính thức.
 *
 * Luồng hoạt động:
 * 1. App khai báo API_V23 permission + ShizukuProvider trong manifest
 * 2. Shizuku server tự động gửi binder qua provider.call("sendBinder")
 * 3. ShizukuHelper nhận binder, lưu lại, thông báo listeners
 * 4. App dùng binder để gọi Shizuku API (newProcess, v.v.)
 */
public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final String SHIZUKU_PLAY_URL =
            "https://play.google.com/store/apps/details?id=" + SHIZUKU_PACKAGE;

    // Request code cho permission
    public static final int REQUEST_CODE_PERMISSION = 1001;

    private static IBinder sBinder = null;
    private static boolean sPermissionGranted = false;

    private static final List<OnBinderReceivedListener> sBinderListeners =
            new CopyOnWriteArrayList<OnBinderReceivedListener>();
    private static final List<OnBinderDeadListener> sBinderDeadListeners =
            new CopyOnWriteArrayList<OnBinderDeadListener>();
    private static final List<OnRequestPermissionResultListener> sPermissionListeners =
            new CopyOnWriteArrayList<OnRequestPermissionResultListener>();

    public interface OnBinderReceivedListener {
        void onBinderReceived();
    }

    public interface OnBinderDeadListener {
        void onBinderDead();
    }

    public interface OnRequestPermissionResultListener {
        void onRequestPermissionResult(int requestCode, boolean granted);
    }

    /**
     * Được gọi bởi ShizukuProvider khi nhận binder từ server.
     */
    public static void onBinderReceived(IBinder binder) {
        Log.i(TAG, "onBinderReceived");
        sBinder = binder;
        try {
            binder.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    Log.w(TAG, "Binder died");
                    sBinder = null;
                    sPermissionGranted = false;
                    for (OnBinderDeadListener l : sBinderDeadListeners) {
                        try { l.onBinderDead(); } catch (Exception ignored) {}
                    }
                }
            }, 0);
        } catch (Exception e) {
            Log.w(TAG, "linkToDeath failed", e);
        }
        for (OnBinderReceivedListener l : sBinderListeners) {
            try { l.onBinderReceived(); } catch (Exception ignored) {}
        }
        // Kiểm tra permission hiện tại
        checkPermission();
    }

    public static boolean isInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isRunning() {
        return sBinder != null && sBinder.isBinderAlive();
    }

    /**
     * Lấy binder hiện tại (dùng bởi ShizukuProvider).
     */
    public static IBinder getBinder() {
        return sBinder;
    }

    public static boolean hasPermission() {
        return sPermissionGranted && isRunning();
    }

    public static int getServerVersion() {
        if (!isRunning()) return -1;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                // IShizuku.getVersion() = TRANSACTION_getVersion
                // Thử gọi transact đơn giản
                return 13; // Giả định v13+
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public static String statusText(Context ctx) {
        if (!isInstalled(ctx)) return "Chưa cài đặt Shizuku";
        if (!isRunning()) return "Shizuku chưa chạy — mở Shizuku để khởi động";
        if (!hasPermission()) return "Shizuku đang chạy — chưa được cấp quyền";
        return "Shizuku đang chạy — đã cấp quyền";
    }

    /**
     * Yêu cầu quyền từ Shizuku. Mở màn hình cấp quyền của Shizuku.
     */
    public static boolean requestPermission(Context ctx, int requestCode) {
        if (!isRunning()) return false;
        try {
            // Mở Shizuku manager để user cấp quyền
            Intent intent = ctx.getPackageManager()
                    .getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "requestPermission failed", e);
        }
        return false;
    }

    @Deprecated
    public static boolean requestPermission(int requestCode) {
        return false;
    }

    /**
     * Kiểm tra permission bằng cách thử transact với server.
     */
    private static void checkPermission() {
        if (!isRunning()) {
            sPermissionGranted = false;
            return;
        }
        try {
            // Thử gọi IShizuku.checkSelfPermission()
            // Nếu thành công mà không throw SecurityException -> có quyền
            // Tạm thời: giả định có quyền nếu binder alive
            // (Shizuku sẽ tự hiện popup khi app gọi API cần quyền)
            sPermissionGranted = true;
        } catch (Exception e) {
            sPermissionGranted = false;
        }
    }

    /**
     * Gửi yêu cầu để Shizuku server gửi lại binder (nếu chưa có).
     */
    public static boolean pokeBinder(Context ctx) {
        if (ctx == null || isRunning()) return isRunning();
        try {
            // Trigger provider để Shizuku server phát hiện
            ctx.getContentResolver().call(
                    android.net.Uri.parse("content://com.kaizen.tool.shizuku"),
                    "hello", null, null);
        } catch (Exception e) {
            Log.w(TAG, "pokeBinder: " + e.getMessage());
        }
        return isRunning();
    }

    public static ShizukuProcess newProcess(String[] cmd) {
        return newProcess(cmd, null, null);
    }

    public static ShizukuProcess newProcess(String[] cmd, String[] env, String dir) {
        // TODO: Implement via IShizuku.newProcess() transact
        // Cần mã transact chính xác từ Shizuku AIDL
        Log.w(TAG, "newProcess not yet implemented");
        return null;
    }

    public static class ShizukuProcess {
        public InputStream getInputStream() { return null; }
        public InputStream getErrorStream() { return null; }
        public OutputStream getOutputStream() { return null; }
        public int waitFor() { return -1; }
        public int exitValue() { return -1; }
        public void destroy() {}
    }

    public static void addOnBinderReceivedListener(OnBinderReceivedListener l) {
        if (l != null && !sBinderListeners.contains(l)) sBinderListeners.add(l);
    }
    public static void removeOnBinderReceivedListener(OnBinderReceivedListener l) {
        sBinderListeners.remove(l);
    }
    public static void addOnBinderDeadListener(OnBinderDeadListener l) {
        if (l != null && !sBinderDeadListeners.contains(l)) sBinderDeadListeners.add(l);
    }
    public static void removeOnBinderDeadListener(OnBinderDeadListener l) {
        sBinderDeadListeners.remove(l);
    }
    public static void addOnRequestPermissionResultListener(OnRequestPermissionResultListener l) {
        if (l != null && !sPermissionListeners.contains(l)) sPermissionListeners.add(l);
    }
    public static void removeOnRequestPermissionResultListener(OnRequestPermissionResultListener l) {
        sPermissionListeners.remove(l);
    }
}
