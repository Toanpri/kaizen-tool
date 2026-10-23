package com.kaizen.tool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Nhận kết quả của PackageInstaller session (xem SplitApkInstaller).
 * - STATUS_PENDING_USER_ACTION: mở intent xác nhận của hệ thống để user bấm Cài.
 * - Các trạng thái cuối (thành công/thất bại): chuyển cho SplitApkInstaller xử lý.
 * Khai báo trong AndroidManifest với exported=false; PendingIntent gửi kết
 * quả mang danh tính của chính app nên vẫn nhận được.
 */
public class InstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.kaizen.tool.INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception ignored) { }
            }
            return;
        }
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        SplitApkInstaller.onInstallResult(context.getApplicationContext(), status, msg);
    }
}
