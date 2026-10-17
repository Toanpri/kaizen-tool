package com.kaizen.tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * M5 — Settings screen: theme (light/dark), language (vi/en),
 * clear thumbnail cache, Shizuku status (real, via ShizukuHelper).
 */
public class SettingsActivity extends Activity {

    private static final int SHIZUKU_REQ = 1001;

    private TextView shizukuStatus;
    private Button shizukuBtn;

    private final ShizukuHelper.OnBinderReceivedListener binderReceived =
            new ShizukuHelper.OnBinderReceivedListener() {
                @Override public void onBinderReceived() {
                    refreshShizukuUi();
                }
            };
    private final ShizukuHelper.OnBinderDeadListener binderDead =
            new ShizukuHelper.OnBinderDeadListener() {
                @Override public void onBinderDead() {
                    refreshShizukuUi();
                    Toast.makeText(SettingsActivity.this,
                            "Mất kết nối Shizuku", Toast.LENGTH_SHORT).show();
                }
            };
    private final ShizukuHelper.OnRequestPermissionResultListener permResult =
            new ShizukuHelper.OnRequestPermissionResultListener() {
                @Override public void onRequestPermissionResult(int requestCode,
                                                               boolean granted) {
                    if (requestCode != SHIZUKU_REQ) return;
                    refreshShizukuUi();
                    Toast.makeText(SettingsActivity.this,
                            granted ? "Đã cấp quyền Shizuku"
                                    : "Chưa được cấp quyền Shizuku",
                            Toast.LENGTH_SHORT).show();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.apply(this);
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.settings_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        // ---- Theme ----
        final RadioButton light = (RadioButton) findViewById(R.id.theme_light);
        final RadioButton dark = (RadioButton) findViewById(R.id.theme_dark);
        if (AppSettings.isDark(this)) dark.setChecked(true);
        else light.setChecked(true);
        ((RadioGroup) findViewById(R.id.theme_group))
                .setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(RadioGroup g, int id) {
                        boolean wantDark = id == R.id.theme_dark;
                        if (wantDark != AppSettings.isDark(SettingsActivity.this)) {
                            AppSettings.setDark(SettingsActivity.this, wantDark);
                            recreate();
                        }
                    }
                });

        // ---- Language ----
        final RadioButton vi = (RadioButton) findViewById(R.id.lang_vi);
        final RadioButton en = (RadioButton) findViewById(R.id.lang_en);
        if ("en".equals(AppSettings.getLang(this))) en.setChecked(true);
        else vi.setChecked(true);
        ((RadioGroup) findViewById(R.id.lang_group))
                .setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(RadioGroup g, int id) {
                        String want = id == R.id.lang_en ? "en" : "vi";
                        if (!want.equals(AppSettings.getLang(SettingsActivity.this))) {
                            AppSettings.setLang(SettingsActivity.this, want);
                            recreate();
                        }
                    }
                });

        // ---- Clear cache ----
        ((Button) findViewById(R.id.btn_clear_cache))
                .setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { clearCache(); }
                });

        // ---- Shizuku (real status via ShizukuHelper) ----
        shizukuStatus = (TextView) findViewById(R.id.shizuku_status);
        shizukuBtn = (Button) findViewById(R.id.btn_shizuku);
        shizukuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onShizukuButton(); }
        });
        refreshShizukuUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShizukuHelper.addOnBinderReceivedListener(binderReceived);
        ShizukuHelper.addOnBinderDeadListener(binderDead);
        ShizukuHelper.addOnRequestPermissionResultListener(permResult);
        refreshShizukuUi();
    }

    @Override
    protected void onPause() {
        ShizukuHelper.removeOnBinderReceivedListener(binderReceived);
        ShizukuHelper.removeOnBinderDeadListener(binderDead);
        ShizukuHelper.removeOnRequestPermissionResultListener(permResult);
        super.onPause();
    }

    /** Update Shizuku status text + button for the 4 real states. */
    private void refreshShizukuUi() {
        if (shizukuStatus == null || shizukuBtn == null) return;
        if (!ShizukuHelper.isInstalled(this)) {
            shizukuStatus.setText("Trạng thái: Chưa cài đặt Shizuku");
            shizukuBtn.setText("Cài đặt Shizuku");
        } else if (!ShizukuHelper.isRunning()) {
            shizukuStatus.setText("Trạng thái: Đã cài nhưng Shizuku chưa chạy");
            shizukuBtn.setText("Hướng dẫn khởi động");
        } else if (ShizukuHelper.hasPermission()) {
            int v = ShizukuHelper.getServerVersion();
            shizukuStatus.setText("Trạng thái: Đã kết nối ✓"
                    + (v > 0 ? " (server v" + v + ")" : ""));
            shizukuBtn.setText("Kiểm tra lại");
        } else {
            shizukuStatus.setText("Trạng thái: Đang chạy — chưa được cấp quyền");
            shizukuBtn.setText("Cấp quyền");
        }
    }

    /** Button action depends on current Shizuku state. */
    private void onShizukuButton() {
        if (!ShizukuHelper.isInstalled(this)) {
            // Mở Play Store
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(ShizukuHelper.SHIZUKU_PLAY_URL)));
            } catch (Exception e) {
                Toast.makeText(this, "Không mở được Play Store",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!ShizukuHelper.isRunning()) {
            // Thử chọc Shizuku gửi binder trước
            Toast.makeText(this, "Đang kết nối tới Shizuku...",
                    Toast.LENGTH_SHORT).show();
            if (ShizukuHelper.pokeBinder(this)) {
                // Đợi 1.5s rồi kiểm tra lại
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override public void run() {
                        refreshShizukuUi();
                        if (ShizukuHelper.isRunning()) {
                            onShizukuButton(); // thử lại
                        } else {
                            showShizukuStartGuide();
                        }
                    }
                }, 1500);
            } else {
                showShizukuStartGuide();
            }
            return;
        }
        if (ShizukuHelper.hasPermission()) {
            refreshShizukuUi();
            // Test Shizuku: echo + ls /Android/data
            new Thread(new Runnable() {
                @Override public void run() {
                    final String echoResult = ShizukuFileHelper.runCommand("echo", "hello-shizuku");
                    final String lsResult = ShizukuFileHelper.runCommand(
                            "ls", "-la", "/storage/emulated/0/Android/data");
                    final boolean permFlag = ShizukuHelper.isPermissionGranted();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            boolean echoOk = echoResult != null
                                    && echoResult.contains("hello-shizuku");
                            String lsPreview = lsResult == null ? "null"
                                    : lsResult.length() > 200
                                            ? lsResult.substring(0, 200) + "..."
                                            : lsResult;
                            if (echoOk) {
                                new CatBuilder(SettingsActivity.this, R.style.DialogTheme)
                                        .setTitle("Shizuku test")
                                        .setMessage("✓ echo OK!"
                                                + "\n\nls /Android/data:\n" + lsPreview
                                                + "\n\nPermission callback: " + permFlag)
                                        .setPositiveButton("OK", null)
                                        .show();
                            } else {
                                new CatBuilder(SettingsActivity.this, R.style.DialogTheme)
                                        .setTitle("Shizuku test thất bại")
                                        .setMessage("Kết quả: " + (echoResult == null ? "null" : echoResult)
                                                + "\n\nPermission callback: " + permFlag
                                                + "\n\nChụp màn hình này gửi cho Gose nhé!")
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        // Xin quyền — mở app Shizuku để user bật toggle
        if (!ShizukuHelper.requestPermission(this, SHIZUKU_REQ)) {
            Toast.makeText(this, "Không mở được app Shizuku",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Hướng dẫn khởi động Shizuku khi đã cài nhưng chưa chạy. */
    private void showShizukuStartGuide() {
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Kết nối Shizuku")
                .setMessage("Kaizen Tool chưa xuất hiện trong Shizuku.\n\n"
                        + "Làm theo các bước:\n"
                        + "1. Mở app Shizuku\n"
                        + "2. Bấm \"Bắt đầu\" để chạy (nếu chưa chạy)\n"
                        + "3. Quay lại đây, bấm nút Shizuku\n"
                        + "4. Cấp quyền khi Shizuku hỏi\n\n"
                        + "Nếu vẫn không được: trong app Shizuku, "
                        + "dừng rồi khởi động lại Shizuku.")
                .setPositiveButton("Mở app Shizuku",
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    Intent i = getPackageManager()
                                            .getLaunchIntentForPackage(
                                                    ShizukuHelper.SHIZUKU_PACKAGE);
                                    if (i != null) startActivity(i);
                                } catch (Exception e) {
                                    Toast.makeText(SettingsActivity.this,
                                            "Không mở được app Shizuku",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void clearCache() {
        int deleted = 0;
        try {
            File cache = getCacheDir();
            File[] files = cache.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (deleteRecursive(f)) deleted++;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        Toast.makeText(this, "Đã xóa cache (" + deleted + " mục)",
                Toast.LENGTH_SHORT).show();
    }

    private static boolean deleteRecursive(File f) {
        boolean ok = true;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) ok &= deleteRecursive(k);
            }
        }
        return f.delete() && ok;
    }
}
