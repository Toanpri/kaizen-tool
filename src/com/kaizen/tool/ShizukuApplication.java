package com.kaizen.tool;

import android.os.Bundle;

import moe.shizuku.server.IShizukuApplication;

/**
 * IShizukuApplication — dùng Stub chính thức từ VsTool
 * (copy từ APK VsTool, đã verify hoạt động).
 */
public class ShizukuApplication extends IShizukuApplication.Stub {

    @Override
    public void bindApplication(Bundle bundle) {
        if (bundle != null) {
            boolean granted = bundle.getBoolean(
                    "shizuku:attach-reply-permission-granted", false);
            ShizukuHelper.onPermissionChanged(granted);
        }
    }

    @Override
    public void dispatchRequestPermissionResult(int requestCode, Bundle bundle) {
        boolean granted = false;
        if (bundle != null) {
            granted = bundle.getBoolean(
                    "shizuku:request-permission-reply-allowed", false);
        }
        ShizukuHelper.onPermissionChanged(granted);
        ShizukuHelper.notifyPermissionResult(requestCode, granted);
    }

    @Override
    public void showPermissionConfirmation(int i, int i2, String str, int i3) {
        // Không dùng
    }
}
