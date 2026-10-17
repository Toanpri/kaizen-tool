package com.kaizen.tool;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ShizukuHelper — TẠM THỜI stub để fix crash.
 * Shizuku sẽ được làm lại từ đầu sau.
 */
public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final String SHIZUKU_PLAY_URL =
            "https://play.google.com/store/apps/details?id=" + SHIZUKU_PACKAGE;

    public interface OnBinderReceivedListener {
        void onBinderReceived();
    }

    public interface OnBinderDeadListener {
        void onBinderDead();
    }

    public interface OnRequestPermissionResultListener {
        void onRequestPermissionResult(int requestCode, boolean granted);
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
        return false; // tạm thời
    }

    public static boolean hasPermission() {
        return false; // tạm thời
    }

    public static int getServerVersion() {
        return -1;
    }

    public static String statusText(Context ctx) {
        if (!isInstalled(ctx)) return "Chưa cài đặt Shizuku";
        return "Shizuku đang bảo trì — quay lại sau";
    }

    public static boolean requestPermission(int requestCode) {
        return false;
    }

    public static boolean pokeBinder(Context ctx) {
        return false;
    }

    public static ShizukuProcess newProcess(String[] cmd) {
        return null;
    }

    public static ShizukuProcess newProcess(String[] cmd, String[] env, String dir) {
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

    public static void addOnBinderReceivedListener(OnBinderReceivedListener l) {}
    public static void removeOnBinderReceivedListener(OnBinderReceivedListener l) {}
    public static void addOnBinderDeadListener(OnBinderDeadListener l) {}
    public static void removeOnBinderDeadListener(OnBinderDeadListener l) {}
    public static void addOnRequestPermissionResultListener(OnRequestPermissionResultListener l) {}
    public static void removeOnRequestPermissionResultListener(OnRequestPermissionResultListener l) {}
}
