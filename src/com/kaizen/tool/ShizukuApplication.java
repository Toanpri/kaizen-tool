package com.kaizen.tool;

import android.os.Bundle;
import android.util.Log;

import moe.shizuku.server.IShizukuApplication;

/**
 * IShizukuApplication — dùng Stub chính thức từ VsTool
 * (copy từ APK VsTool, đã verify hoạt động).
 */
public class ShizukuApplication extends IShizukuApplication.Stub {

    private static final String TAG = "ShizukuApplication";

    @Override
    public void bindApplication(Bundle bundle) {
        Log.d(TAG, "bindApplication called");
        if (bundle != null) {
            boolean granted = bundle.getBoolean(
                    "shizuku:attach-reply-permission-granted", false);
            Log.d(TAG, "Permission granted from bind: " + granted);
            ShizukuHelper.onPermissionChanged(granted);
        }
    }

    @Override
    public void dispatchRequestPermissionResult(int requestCode, Bundle bundle) {
        Log.d(TAG, "dispatchRequestPermissionResult: " + requestCode);
        boolean granted = false;
        if (bundle != null) {
            granted = bundle.getBoolean(
                    "shizuku:request-permission-reply-allowed", false);
        }
        Log.d(TAG, "Permission result: " + granted);
        ShizukuHelper.onPermissionChanged(granted);
        ShizukuHelper.notifyPermissionResult(requestCode, granted);
    }

    @Override
    public void showPermissionConfirmation(int i, int i2, String str, int i3) {
        // Không dùng
    }
}
