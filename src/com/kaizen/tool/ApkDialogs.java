package com.kaizen.tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Dialog khi tap file .apk: hiện info app + nút Cài đặt / Hủy. */
public class ApkDialogs {

    /** Đọc info trên background thread (getPackageArchiveInfo có thể chậm), rồi show dialog trên UI. */
    public static void showInstall(final Activity a, final File apkFile) {
        if (a == null || apkFile == null || !apkFile.isFile()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ApkUtils.ApkInfo info = ApkUtils.getApkInfo(a, apkFile);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                && a.isDestroyed()) return;
                        showDialog(a, apkFile, info);
                    }
                });
            }
        }).start();
    }

    private static final int PINK = 0xFFF06292;
    private static final int GRAY = 0xFF757575;
    private static final int DARK = 0xFF212121;

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void addInfoRow(Activity a, LinearLayout parent,
                                   String label, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        row.setLayoutParams(lp);

        TextView lb = new TextView(a);
        lb.setText(label);
        lb.setTextSize(13);
        lb.setTextColor(GRAY);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                dp(a, 110), LinearLayout.LayoutParams.WRAP_CONTENT);
        lb.setLayoutParams(llp);
        row.addView(lb);

        TextView vl = new TextView(a);
        vl.setText(value);
        vl.setTextSize(14);
        vl.setTextColor(DARK);
        vl.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(vl);

        parent.addView(row);
    }

    /** Rút gọn "android.permission.CAMERA" -> "CAMERA". */
    private static String shortPerm(String p) {
        if (p == null) return "";
        int i = p.lastIndexOf('.');
        return i >= 0 ? p.substring(i + 1) : p;
    }

    private static void showDialog(final Activity a, final File apkFile,
                                   final ApkUtils.ApkInfo info) {
        int pad = dp(a, 20);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        // Popup tai mèo: CatPopup.apply() sẽ thêm tai overlay + nền bo tròn.
        root.setPadding(pad, dp(a, 12), pad, dp(a, 12));

        // ---- Header: icon lớn + tên + package ----
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        ImageView iconView = new ImageView(a);
        int iconPx = dp(a, 84);
        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(iconPx, iconPx);
        iconLp.setMargins(0, 0, dp(a, 14), 0);
        iconView.setLayoutParams(iconLp);
        Drawable icon = (info != null) ? info.icon : null;
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            try {
                iconView.setImageDrawable(a.getPackageManager().getDefaultActivityIcon());
            } catch (Exception ignored) {
            }
        }
        header.addView(iconView);

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(texts);

        String name, pkg, verLine;
        long size;
        if (info != null && info.label != null && !info.label.isEmpty()) {
            name = info.label;
            pkg = info.packageName != null ? info.packageName : "";
            verLine = "Phi\u00ean b\u1ea3n "
                    + (info.versionName != null && !info.versionName.isEmpty()
                            ? info.versionName : "?")
                    + " (" + info.versionCode + ")";
            size = info.size;
        } else {
            name = apkFile.getName();
            pkg = "";
            verLine = "";
            size = apkFile.length();
        }

        TextView nameTv = new TextView(a);
        nameTv.setText(name);
        nameTv.setTextSize(20);
        nameTv.setTextColor(DARK);
        nameTv.getPaint().setFakeBoldText(true);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(nameTv);

        if (!pkg.isEmpty()) {
            TextView pkgTv = new TextView(a);
            pkgTv.setText(pkg);
            pkgTv.setTextSize(13);
            pkgTv.setTextColor(GRAY);
            pkgTv.setSingleLine(true);
            pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(a, 2);
            pkgTv.setLayoutParams(plp);
            texts.addView(pkgTv);
        }

        // ---- Banner "Đã cài đặt" nếu app đã có trên máy ----
        String installedVer = (info != null)
                ? ApkUtils.getInstalledVersionName(a, info.packageName) : null;
        if (installedVer != null) {
            TextView banner = new TextView(a);
            banner.setText("\u0110\u00e3 c\u00e0i \u0111\u1eb7t phi\u00ean b\u1ea3n "
                    + installedVer);
            banner.setTextSize(14);
            banner.setTextColor(PINK);
            banner.setBackgroundDrawable(
                    a.getResources().getDrawable(R.drawable.banner_pink_light));
            int bp = dp(a, 10);
            banner.setPadding(bp, bp, bp, bp);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(a, 14);
            banner.setLayoutParams(blp);
            root.addView(banner);
        }

        // ---- Divider ----
        android.view.View div = new android.view.View(a);
        div.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 1));
        dlp.topMargin = dp(a, 14);
        div.setLayoutParams(dlp);
        root.addView(div);

        // ---- Thông tin chi tiết ----
        if (!verLine.isEmpty()) addInfoRow(a, root, "Phi\u00ean b\u1ea3n", verLine);
        addInfoRow(a, root, "K\u00edch th\u01b0\u1edbc", FileOps.formatSize(size));
        long lm = (info != null) ? info.lastModified : apkFile.lastModified();
        if (lm > 0) {
            String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",
                    java.util.Locale.getDefault())
                    .format(new java.util.Date(lm));
            addInfoRow(a, root, "Ng\u00e0y s\u1eeda", date);
        }
        if (info != null && info.permissions != null && info.permissions.length > 0) {
            StringBuilder sb = new StringBuilder();
            int show = Math.min(3, info.permissions.length);
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(shortPerm(info.permissions[i]));
            }
            if (info.permissions.length > show) {
                sb.append(" (+").append(info.permissions.length - show).append(")");
            }
            addInfoRow(a, root, "Quy\u1ec1n", sb.toString());
        }

        // ---- Nút bấm ----
        LinearLayout btnRow = new LinearLayout(a);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 18);
        btnRow.setLayoutParams(rlp);
        root.addView(btnRow);

        final AlertDialog dlg =
                new CatBuilder(a, R.style.DialogTheme).setView(root).create();
        // Window trong suốt để hiện hình mèo (tai không bị nền dialog mặc định che).
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }

        TextView cancel = new TextView(a);
        cancel.setText("H\u1ee6Y");
        cancel.setTextSize(14);
        cancel.setTextColor(GRAY);
        cancel.getPaint().setFakeBoldText(true);
        cancel.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        cancel.setBackgroundDrawable(a.getResources().getDrawable(
                android.R.drawable.list_selector_background));
        cancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { dlg.dismiss(); }
        });
        btnRow.addView(cancel);

        TextView install = new TextView(a);
        install.setText(installedVer != null ? "C\u00c0I \u0110\u1eb6T L\u1ea0I" : "C\u00c0I \u0110\u1eb6T");
        install.setTextSize(14);
        install.setTextColor(0xFFFFFFFF);
        install.getPaint().setFakeBoldText(true);
        install.setBackgroundDrawable(
                a.getResources().getDrawable(R.drawable.btn_pink));
        install.setPadding(dp(a, 24), dp(a, 12), dp(a, 24), dp(a, 12));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(dp(a, 8), 0, 0, 0);
        install.setLayoutParams(ilp);
        install.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                dlg.dismiss();
                ApkUtils.installApk(a, apkFile);
            }
        });
        btnRow.addView(install);

        dlg.show();
        CatPopup.apply(dlg);
    }

    // ------------------------------------------------------------------
    // Gói split: .apks / .xapk / .apkm
    // ------------------------------------------------------------------

    /**
     * Dialog cài gói split: giải nén nền (đọc info APK chính + đếm file con)
     * rồi hiện dialog. Contents giải nén được giữ lại để cài luôn khi user
     * bấm CÀI ĐẶT; user hủy thì discard() dọn thư mục tạm.
     */
    public static void showInstallBundle(final Activity a, final File bundleFile) {
        if (a == null || bundleFile == null || !bundleFile.isFile()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final SplitApkInstaller.Contents c =
                        SplitApkInstaller.extract(a, bundleFile);
                final ApkUtils.ApkInfo info = (c != null && c.baseApk != null)
                        ? ApkUtils.getApkInfo(a, c.baseApk) : null;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean dead = a.isFinishing()
                                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                        && a.isDestroyed());
                        if (dead) {
                            SplitApkInstaller.discard(c);
                            return;
                        }
                        if (c == null) {
                            String why = SplitApkInstaller.lastError;
                            Toast.makeText(a,
                                    "Không đọc được gói này"
                                            + (why != null ? ": " + why : ""),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        showBundleDialog(a, bundleFile, c, info);
                    }
                });
            }
        }).start();
    }

    private static void showBundleDialog(final Activity a, final File bundleFile,
                                         final SplitApkInstaller.Contents c,
                                         final ApkUtils.ApkInfo info) {
        int pad = dp(a, 20);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(a, 12), pad, dp(a, 12));

        // ---- Header: icon + tên + package (đọc từ APK chính trong gói) ----
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        ImageView iconView = new ImageView(a);
        int iconPx = dp(a, 84);
        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(iconPx, iconPx);
        iconLp.setMargins(0, 0, dp(a, 14), 0);
        iconView.setLayoutParams(iconLp);
        Drawable icon = (info != null) ? info.icon : null;
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            try {
                iconView.setImageDrawable(a.getPackageManager().getDefaultActivityIcon());
            } catch (Exception ignored) {
            }
        }
        header.addView(iconView);

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(texts);

        String name, pkg;
        if (info != null && info.label != null && !info.label.isEmpty()) {
            name = info.label;
            pkg = info.packageName != null ? info.packageName : "";
        } else {
            name = bundleFile.getName();
            pkg = c.packageName != null ? c.packageName : "";
        }

        TextView nameTv = new TextView(a);
        nameTv.setText(name);
        nameTv.setTextSize(20);
        nameTv.setTextColor(DARK);
        nameTv.getPaint().setFakeBoldText(true);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(nameTv);

        if (!pkg.isEmpty()) {
            TextView pkgTv = new TextView(a);
            pkgTv.setText(pkg);
            pkgTv.setTextSize(13);
            pkgTv.setTextColor(GRAY);
            pkgTv.setSingleLine(true);
            pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(pkgTv);
        }

        // ---- Banner "Đã cài đặt" ----
        String installedVer = (info != null)
                ? ApkUtils.getInstalledVersionName(a, info.packageName) : null;
        if (installedVer != null) {
            TextView banner = new TextView(a);
            banner.setText("Đã cài đặt phiên bản " + installedVer);
            banner.setTextSize(14);
            banner.setTextColor(PINK);
            banner.setBackgroundDrawable(
                    a.getResources().getDrawable(R.drawable.banner_pink_light));
            int bp = dp(a, 10);
            banner.setPadding(bp, bp, bp, bp);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(a, 14);
            banner.setLayoutParams(blp);
            root.addView(banner);
        }

        // ---- Divider ----
        android.view.View div = new android.view.View(a);
        div.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 1));
        dlp.topMargin = dp(a, 14);
        div.setLayoutParams(dlp);
        root.addView(div);

        // ---- Thông tin chi tiết ----
        if (info != null && info.versionName != null && !info.versionName.isEmpty()) {
            addInfoRow(a, root, "Phiên bản",
                    info.versionName + " (" + info.versionCode + ")");
        }
        addInfoRow(a, root, "Kích thước", FileOps.formatSize(bundleFile.length()));
        addInfoRow(a, root, "APK con", c.apks.size() + " file (base + split)");
        if (!c.obbs.isEmpty()) {
            addInfoRow(a, root, "OBB",
                    c.obbs.size() + " file — chép vào Android/obb sau khi cài");
        }

        // ---- Nút bấm ----
        LinearLayout btnRow = new LinearLayout(a);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 18);
        btnRow.setLayoutParams(rlp);
        root.addView(btnRow);

        final AlertDialog dlg =
                new CatBuilder(a, R.style.DialogTheme).setView(root).create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }

        // transferred[0] = true khi quyền sở hữu Contents đã qua cho
        // SplitApkInstaller.install(); dialog đóng mà chưa chuyển giao
        // (HỦY / Back / chạm ngoài) thì onDismiss dọn thư mục tạm.
        final boolean[] transferred = {false};
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) {
                if (!transferred[0]) SplitApkInstaller.discard(c);
            }
        });

        TextView cancel = new TextView(a);
        cancel.setText("HỦY");
        cancel.setTextSize(14);
        cancel.setTextColor(GRAY);
        cancel.getPaint().setFakeBoldText(true);
        cancel.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        cancel.setBackgroundDrawable(a.getResources().getDrawable(
                android.R.drawable.list_selector_background));
        cancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                dlg.dismiss();
            }
        });
        btnRow.addView(cancel);

        TextView install = new TextView(a);
        install.setText(installedVer != null ? "CÀI ĐẶT LẠI" : "CÀI ĐẶT");
        install.setTextSize(14);
        install.setTextColor(0xFFFFFFFF);
        install.getPaint().setFakeBoldText(true);
        install.setBackgroundDrawable(
                a.getResources().getDrawable(R.drawable.btn_pink));
        install.setPadding(dp(a, 24), dp(a, 12), dp(a, 24), dp(a, 12));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(dp(a, 8), 0, 0, 0);
        install.setLayoutParams(ilp);
        install.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                transferred[0] = true;
                dlg.dismiss();
                SplitApkInstaller.install(a, c);
            }
        });
        btnRow.addView(install);

        dlg.show();
        CatPopup.apply(dlg);
    }
}
