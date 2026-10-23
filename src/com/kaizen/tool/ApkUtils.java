package com.kaizen.tool;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;

/** Tiện ích đọc thông tin và mở cài đặt file APK. */
public class ApkUtils {

    public static class ApkInfo {
        public String label;        // tên app (vd "ZArchiver")
        public String packageName;  // vd "ru.zdevs.zarchiver"
        public String versionName;  // vd "1.0.10"
        public int versionCode;
        public long size;           // bytes của file apk
        public long lastModified;   // ngày sửa file
        public String[] permissions; // quyền yêu cầu (có thể null)
        public Drawable icon;       // có thể null
    }

    /**
     * Trả về versionName của app đã cài (theo packageName), hoặc null nếu chưa cài.
     * Cần QUERY_ALL_PACKAGES trên Android 11+ để thấy mọi app.
     */
    public static String getInstalledVersionName(Context ctx, String packageName) {
        try {
            if (packageName == null) return null;
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(packageName, 0);
            return pi != null ? pi.versionName : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Đọc info từ file APK chưa cài. Trả null nếu lỗi.
     * Bắt buộc set appInfo.sourceDir/publicSourceDir = path trước khi lấy icon/label.
     */
    public static ApkInfo getApkInfo(Context ctx, File apkFile) {
        try {
            if (apkFile == null || !apkFile.isFile() || !apkFile.canRead()) return null;
            String path = apkFile.getAbsolutePath();
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(path,
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            if (pi == null || pi.applicationInfo == null) return null;
            pi.applicationInfo.sourceDir = path;
            pi.applicationInfo.publicSourceDir = path;

            ApkInfo info = new ApkInfo();
            info.packageName = pi.packageName;
            info.permissions = pi.requestedPermissions;
            info.lastModified = apkFile.lastModified();
            info.versionName = pi.versionName != null ? pi.versionName : "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.versionCode = (int) pi.getLongVersionCode();
            } else {
                info.versionCode = pi.versionCode;
            }
            info.size = apkFile.length();
            try {
                info.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
            } catch (Exception e) {
                info.label = info.packageName;
            }
            try {
                info.icon = pm.getApplicationIcon(pi.applicationInfo);
            } catch (Exception e) {
                info.icon = null;
            }
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cổng quyền cài app (dùng chung cho cài APK thường và gói split):
     * API >= 26 cần canRequestPackageInstalls. Đã có quyền (hoặc API < 26)
     * thì trả true. Chưa có thì mở Settings ACTION_MANAGE_UNKNOWN_APP_SOURCES
     * để user bật "Cho phép từ nguồn này" rồi trả false — caller dừng tại đây,
     * user cấp quyền xong thì bấm cài lại.
     */
    public static boolean ensureInstallPermission(Activity a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageManager pm = a.getPackageManager();
            if (!pm.canRequestPackageInstalls()) {
                Uri pkgUri = Uri.parse("package:" + a.getPackageName());
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(settings);
                Toast.makeText(a, "Bật \"Cho phép từ nguồn này\" rồi cài lại",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    /**
     * Mở system installer cho file APK.
     * - API >= 26: chưa có quyền cài -> ensureInstallPermission mở Settings, return.
     * - Dùng content:// URI qua ApkFileProvider cho mọi API.
     */
    public static void installApk(Activity a, File apkFile) {
        try {
            if (apkFile == null || !apkFile.isFile()) return;
            if (!ensureInstallPermission(a)) return;

            Uri uri = ApkFileProvider.uriFor(apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Không tìm thấy trình cài đặt", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(a, "Không thể mở trình cài đặt", Toast.LENGTH_SHORT).show();
        }
    }
}
