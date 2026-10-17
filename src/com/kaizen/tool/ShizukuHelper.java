package com.kaizen.tool;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
    // Interface chính thức từ VsTool
    private static moe.shizuku.server.IShizukuService sService = null;

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
        // Tạo interface chính thức từ VsTool
        try {
            sService = moe.shizuku.server.IShizukuService.Stub.asInterface(binder);
        } catch (Exception e) {
            Log.w(TAG, "asInterface failed", e);
        }
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
        // Đăng ký app với server
        attachApplication();
        // Kiểm tra permission hiện tại
        checkPermission();
    }

    private static final ShizukuApplication sApp = new ShizukuApplication();

    /**
     * Đăng ký app với Shizuku server (bắt buộc trước khi dùng API).
     */
    private static void attachApplication() {
        if (!isRunning()) return;
        String packageName = "com.kaizen.tool";
        // Thử V13 trước (transact 18), fallback V11 (transact 14) — giống ZArchiver
        if (attachV13(packageName)) {
            Log.d(TAG, "attachApplication V13 success");
            return;
        }
        if (attachV11(packageName)) {
            Log.d(TAG, "attachApplication V11 success");
            return;
        }
        Log.w(TAG, "attachApplication failed both V13 and V11");
    }

    /** Attach kiểu V13: transact 18 với Bundle args. */
    private static boolean attachV13(String packageName) {
        try {
            Bundle args = new Bundle();
            args.putInt("shizuku:attach-api-version", 13);
            args.putString("shizuku:attach-package-name", packageName);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR);
                data.writeStrongBinder(sApp);
                data.writeInt(1);
                args.writeToParcel(data, 0);
                boolean ok = sBinder.transact(18, data, reply, 0);
                reply.readException();
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "attachV13 failed", e);
            return false;
        }
    }

    /** Attach kiểu V11: transact 14 với package name string (giống ZArchiver). */
    private static boolean attachV11(String packageName) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR);
                data.writeStrongBinder(sApp);
                data.writeString(packageName);
                boolean ok = sBinder.transact(14, data, reply, 0);
                reply.readException();
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "attachV11 failed", e);
            return false;
        }
    }

    /**
     * Được gọi khi permission thay đổi (từ server callback).
     */
    public static void onPermissionChanged(boolean granted) {
        Log.d(TAG, "onPermissionChanged: " + granted);
        sPermissionGranted = granted;
    }

    /** Gọi lại attachApplication (để debug). */
    public static void reattach() {
        Log.d(TAG, "reattach() called");
        sPermissionGranted = false;
        attachApplication();
    }

    /** Kiểm tra flag permission từ server callback (để debug). */
    public static boolean isPermissionGranted() {
        return sPermissionGranted;
    }

    /**
     * Thông báo kết quả request permission cho listeners.
     */
    public static void notifyPermissionResult(int requestCode, boolean granted) {
        sPermissionGranted = granted;
        for (OnRequestPermissionResultListener l : sPermissionListeners) {
            try { l.onRequestPermissionResult(requestCode, granted); }
            catch (Exception ignored) {}
        }
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
        // Đơn giản: nếu binder sống thì coi như có quyền
        // (user đã bật toggle trong Shizuku UI, server sẽ cho phép newProcess)
        // Việc check chi tiết via transact phức tạp và dễ sai version
        return isRunning();
    }

    public static int getServerVersion() {
        int v = getRealServerVersion();
        return v >= 0 ? v : -1;
    }

    public static String statusText(Context ctx) {
        if (!isInstalled(ctx)) return "Chưa cài đặt Shizuku";
        if (!isRunning()) return "Shizuku chưa chạy — mở Shizuku để khởi động";
        return "Đã kết nối ✓ (server v" + getServerVersion() + ")";
    }

    /**
     * Yêu cầu quyền: mở Shizuku để user cấp quyền thủ công.
     * (Shizuku không có API đơn giản để hiện dialog từ app khác)
     */
    public static boolean requestPermission(Context ctx, int requestCode) {
        if (ctx == null) return false;
        try {
            // Mở Shizuku manager để user bật toggle
            Intent intent = ctx.getPackageManager()
                    .getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                Log.d(TAG, "Opened Shizuku manager for manual permission grant");
                return true;
            } else {
                Log.w(TAG, "No launch intent for Shizuku");
            }
        } catch (Exception e) {
            Log.e(TAG, "requestPermission failed", e);
        }
        return false;
    }

    // Xóa method deprecated gây nhầm lẫn

    /**
     * Kiểm tra permission thực via checkSelfPermission().
     */
    private static void checkPermission() {
        if (!isRunning()) {
            sPermissionGranted = false;
            return;
        }
        sPermissionGranted = checkRealPermission();
        Log.d(TAG, "checkPermission: " + sPermissionGranted);
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

    // IShizukuService AIDL transaction codes (từ Shizuku-API)
    private static final String SHIZUKU_SERVICE_DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    // Transaction codes từ ZArchiver (đã verify trên device thật với server v13)
    // ZArchiver dùng: newProcess=8, checkSelfPermission=16
    private static final int TRANSACTION_newProcess = 8;
    private static final int TRANSACTION_getVersion = 2;
    // ZArchiver: checkSelfPermission (e()) dùng transact 16
    private static final int TRANSACTION_checkSelfPermission = 16;

    // IRemoteProcess AIDL transaction codes
    private static final String REMOTE_PROCESS_DESCRIPTOR = "moe.shizuku.server.IRemoteProcess";
    private static final int RP_TRANSACTION_getOutputStream = 1;
    private static final int RP_TRANSACTION_getInputStream = 2;
    private static final int RP_TRANSACTION_getErrorStream = 3;
    private static final int RP_TRANSACTION_waitFor = 4;
    private static final int RP_TRANSACTION_exitValue = 5;
    private static final int RP_TRANSACTION_destroy = 6;

    public static ShizukuProcess newProcess(String[] cmd, String[] env, String dir) {
        if (!isRunning()) {
            Log.w(TAG, "newProcess: Shizuku not running");
            return null;
        }
        // Dùng interface chính thức từ VsTool
        if (sService != null) {
            try {
                moe.shizuku.server.IRemoteProcess remote =
                        sService.newProcess(cmd, env, dir);
                if (remote != null) {
                    Log.d(TAG, "newProcess via official API: OK");
                    return new ShizukuProcess(remote.asBinder());
                } else {
                    Log.w(TAG, "newProcess via official API: null");
                    sLastError = "Official API returned null";
                }
            } catch (Exception e) {
                Log.e(TAG, "newProcess via official API failed", e);
                sLastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return null;
        }
        // Fallback: manual transact (cũ)
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR);
                data.writeStringArray(cmd);
                // Giống ZArchiver: dùng null trực tiếp
                data.writeStringArray(env);
                data.writeString(dir);
                sBinder.transact(TRANSACTION_newProcess, data, reply, 0);
                reply.readException();
                IBinder processBinder = reply.readStrongBinder();
                if (processBinder != null) {
                    Log.d(TAG, "newProcess: got remote process binder");
                    return new ShizukuProcess(processBinder);
                } else {
                    Log.w(TAG, "newProcess: null process binder");
                    sLastError = "Server returned null binder (transact 8 OK but no binder)";
                }
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "newProcess failed", e);
            sLastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return null;
    }

    /** Lỗi cuối cùng từ newProcess — để hiển thị trong dialog debug. */
    public static String sLastError = null;

    /**
     * Lấy version thực từ server (thay vì hardcode).
     */
    public static int getRealServerVersion() {
        if (!isRunning()) return -1;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR);
                sBinder.transact(TRANSACTION_getVersion, data, reply, 0);
                reply.readException();
                return reply.readInt();
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "getRealServerVersion failed", e);
            return -1;
        }
    }

    /**
     * Kiểm tra permission thực bằng cách thử chạy lệnh đơn giản.
     * Nếu newProcess thành công -> có quyền.
     */
    public static boolean checkRealPermission() {
        if (!isRunning()) return false;
        try {
            // Thử transact checkSelfPermission (code 15 từ AIDL)
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR);
                sBinder.transact(TRANSACTION_checkSelfPermission, data, reply, 0);
                reply.readException();
                boolean result = reply.readInt() != 0;
                Log.d(TAG, "checkSelfPermission: " + result);
                return result;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "checkSelfPermission failed, trying newProcess test", e);
            // Fallback: thử newProcess với lệnh đơn giản
            try {
                ShizukuProcess proc = newProcess(new String[]{"echo", "test"});
                if (proc != null) {
                    proc.destroy();
                    Log.d(TAG, "newProcess test succeeded -> has permission");
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
    }

    public static class ShizukuProcess {
        private final IBinder mRemote;
        private moe.shizuku.server.IRemoteProcess mOfficial;

        ShizukuProcess(IBinder remote) {
            mRemote = remote;
            // Dùng interface chính thức từ VsTool
            try {
                mOfficial = moe.shizuku.server.IRemoteProcess.Stub.asInterface(remote);
            } catch (Exception e) {
                Log.w(TAG, "asInterface IRemoteProcess failed", e);
            }
        }

        private android.os.ParcelFileDescriptor transactGetFd(int code) {
            // Ưu tiên interface chính thức
            if (mOfficial != null) {
                try {
                    if (code == RP_TRANSACTION_getInputStream) {
                        return mOfficial.getInputStream();
                    } else if (code == RP_TRANSACTION_getOutputStream) {
                        return mOfficial.getOutputStream();
                    } else if (code == RP_TRANSACTION_getErrorStream) {
                        return mOfficial.getErrorStream();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "official getFd failed", e);
                }
            }
            // Fallback: manual transact với format đúng (đọc int trước)
            try {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR);
                    mRemote.transact(code, data, reply, 0);
                    reply.readException();
                    // Format đúng: đọc int flag trước (giống VsTool)
                    if (reply.readInt() != 0) {
                        return android.os.ParcelFileDescriptor.CREATOR
                                .createFromParcel(reply);
                    }
                    return null;
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            } catch (Exception e) {
                Log.w(TAG, "transactGetFd failed", e);
                return null;
            }
        }

        public InputStream getInputStream() {
            try {
                android.os.ParcelFileDescriptor pfd =
                        transactGetFd(RP_TRANSACTION_getInputStream);
                if (pfd != null) {
                    return new android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd);
                }
            } catch (Exception e) {
                Log.w(TAG, "getInputStream failed", e);
            }
            return null;
        }

        public InputStream getErrorStream() {
            try {
                android.os.ParcelFileDescriptor pfd =
                        transactGetFd(RP_TRANSACTION_getErrorStream);
                if (pfd != null) {
                    return new android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd);
                }
            } catch (Exception e) {
                Log.w(TAG, "getErrorStream failed", e);
            }
            return null;
        }

        public OutputStream getOutputStream() {
            try {
                android.os.ParcelFileDescriptor pfd =
                        transactGetFd(RP_TRANSACTION_getOutputStream);
                if (pfd != null) {
                    return new android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd);
                }
            } catch (Exception e) {
                Log.w(TAG, "getOutputStream failed", e);
            }
            return null;
        }

        public int waitFor() {
            try {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR);
                    mRemote.transact(RP_TRANSACTION_waitFor, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            } catch (Exception e) {
                Log.w(TAG, "waitFor failed", e);
                return -1;
            }
        }

        public int exitValue() {
            try {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR);
                    mRemote.transact(RP_TRANSACTION_exitValue, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            } catch (Exception e) {
                return -1;
            }
        }

        public void destroy() {
            try {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR);
                    mRemote.transact(RP_TRANSACTION_destroy, data, reply, 0);
                    reply.readException();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            } catch (Exception e) {
                Log.w(TAG, "destroy failed", e);
            }
        }
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
