package com.kaizen.tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
        setTheme(CharacterTheme.appTheme(this));
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

        // ---- Nhân vật (Ram/Rem) ----
        final Button btnRam = (Button) findViewById(R.id.btn_char_ram);
        final Button btnRem = (Button) findViewById(R.id.btn_char_rem);
        styleCharButtons(btnRam, btnRem);
        btnRam.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!CharacterThemeSpec.RAM.equals(CharacterTheme.current(SettingsActivity.this))) {
                    CharacterTheme.setCharacter(SettingsActivity.this, CharacterThemeSpec.RAM);
                    recreate();
                }
            }
        });
        btnRem.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!CharacterThemeSpec.REM.equals(CharacterTheme.current(SettingsActivity.this))) {
                    CharacterTheme.setCharacter(SettingsActivity.this, CharacterThemeSpec.REM);
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

        // ---- Dọn session cài đặt cũ (v1.3-m3-test5) ----
        ((Button) findViewById(R.id.btn_clear_sessions))
                .setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { clearSessions(); }
                });

        // ---- Nhật ký lỗi cài đặt (v1.3-m4-test1) ----
        ((Button) findViewById(R.id.btn_view_error_log))
                .setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showErrorLog(); }
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

    /** Nút nhân vật đang chọn tô màu chủ đạo, nút còn lại xám. */
    private void styleCharButtons(Button ram, Button rem) {
        boolean isRem = CharacterTheme.isRem(this);
        Button sel = isRem ? rem : ram;
        Button unsel = isRem ? ram : rem;
        sel.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(CharacterTheme.primaryColor(this)));
        sel.setTextColor(0xFFFFFFFF);
        unsel.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF9E9E9E));
        unsel.setTextColor(0xFFFFFFFF);
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
                                // Sakuga: nut OK bang SakugaButton.
                                LinearLayout content = SakugaDialogs.messageLayout(
                                        SettingsActivity.this,
                                        "✓ echo OK!"
                                                + "\n\nls /Android/data:\n" + lsPreview
                                                + "\n\nPermission callback: " + permFlag);
                                final SakugaDialogs.Holder holder = SakugaDialogs.holder();
                                SakugaDialogs.addButtonRow(SettingsActivity.this, content, holder,
                                        new SakugaDialogs.Action("OK", true, null));
                                AlertDialog dialog = new CatBuilder(SettingsActivity.this, R.style.DialogTheme)
                                        .setTitle("Shizuku test")
                                        .setView(content)
                                        .show();
                                holder.dialog = dialog;
                            } else {
                                // Sakuga: nut OK bang SakugaButton.
                                LinearLayout content = SakugaDialogs.messageLayout(
                                        SettingsActivity.this,
                                        "Kết quả: " + (echoResult == null ? "null" : echoResult)
                                                + "\n\nPermission callback: " + permFlag
                                                + "\n\nChụp màn hình này gửi cho Gose nhé!");
                                final SakugaDialogs.Holder holder = SakugaDialogs.holder();
                                SakugaDialogs.addButtonRow(SettingsActivity.this, content, holder,
                                        new SakugaDialogs.Action("OK", true, null));
                                AlertDialog dialog = new CatBuilder(SettingsActivity.this, R.style.DialogTheme)
                                        .setTitle("Shizuku test thất bại")
                                        .setView(content)
                                        .show();
                                holder.dialog = dialog;
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
        // Sakuga: nut Mo app Shizuku/Dong bang SakugaButton.
        LinearLayout content = SakugaDialogs.messageLayout(this,
                "Kaizen Tool chưa xuất hiện trong Shizuku.\n\n"
                        + "Làm theo các bước:\n"
                        + "1. Mở app Shizuku\n"
                        + "2. Bấm \"Bắt đầu\" để chạy (nếu chưa chạy)\n"
                        + "3. Quay lại đây, bấm nút Shizuku\n"
                        + "4. Cấp quyền khi Shizuku hỏi\n\n"
                        + "Nếu vẫn không được: trong app Shizuku, "
                        + "dừng rồi khởi động lại Shizuku.");
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(this, content, holder,
                new SakugaDialogs.Action("Mở app Shizuku", true, new SakugaDialogs.OnAction() {
                    @Override public boolean run(AlertDialog dialog) {
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
                        return true;
                    }
                }),
                new SakugaDialogs.Action("Đóng", false, null));
        AlertDialog dialog = new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Kết nối Shizuku")
                .setView(content)
                .show();
        holder.dialog = dialog;
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

    /** Dọn session cài đặt cũ của app (v1.3-m3-test5) — nhả chỗ kẹt ở "Khác". */
    private void clearSessions() {
        Toast.makeText(this, "Đang dọn session...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String msg = SplitApkInstaller.cleanupOrphanedSessions(
                        SettingsActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this, msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    /** Hiện lỗi cài đặt mới nhất (chỉ 1 lỗi, không danh sách). */
    private void showErrorLog() {
        String err = SplitApkInstaller.getLastError(this);
        if (err == null || err.trim().isEmpty()) {
            Toast.makeText(this, "Chưa có lỗi cài đặt nào",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        TextView tv = new TextView(this);
        tv.setText(err);
        tv.setTextIsSelectable(true);
        tv.setTextSize(14);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        // Sakuga: nut Dong bang SakugaButton.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sv);
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(this, content, holder,
                new SakugaDialogs.Action("Đóng", true, null));
        AlertDialog dialog = new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Lỗi cài đặt gần nhất")
                .setView(content)
                .show();
        holder.dialog = dialog;
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
