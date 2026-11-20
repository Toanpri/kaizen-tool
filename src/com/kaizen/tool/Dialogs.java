package com.kaizen.tool;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dialogs for Kaizen Tool M1 (pure android.app / android.widget, no AndroidX).
 */
public class Dialogs {

    // ------------------------------------------------------------------
    // Input dialogs: rename / new folder / new file
    // ------------------------------------------------------------------

    public static void showRename(final Activity a, final File f, final Runnable onDone) {
        showRename(a, f, false, onDone);
    }

    // Pentest 26/10 (cung ho voi bug create m6): viaShizuku=true khi item
    // nam trong thu muc can Shizuku (Android/data, obb) — java.io khong
    // thay file o do nen guard exists() cu lam dialog khong mo, va
    // FileOps.rename cung fail o do. Doi ten qua mv cua Shizuku.
    // Luu y: f.isFile() tra false voi path Shizuku (khong stat duoc)
    // nen phan chon ten se chon ca ten thay vi tru phan mo rong.
    public static void showRename(final Activity a, final File f, final boolean viaShizuku, final Runnable onDone) {
        if (a == null || f == null) return;
        if (!viaShizuku && !f.exists()) return;
        final String current = f.getName();
        final EditText input = makeInput(a, current);
        // pre-fill current name, select the part before the extension
        input.setText(current);
        int dot = current.lastIndexOf('.');
        int end = (f.isFile() && dot > 0) ? dot : current.length();
        input.setSelection(0, end);

        // Sakuga: nut Luu/Huy bang SakugaButton (thay nut AlertDialog goc).
        // Luu that bai (ten sai / doi ten that bai) -> tra false de giu
        // dialog mo cho user sua lai (nut goc tu dong dong bat ke).
        LinearLayout content = pad(a, input);
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(a, content, holder,
                new SakugaDialogs.Action("Lưu", true, new SakugaDialogs.OnAction() {
                    @Override public boolean run(AlertDialog dialog) {
                        String newName = input.getText().toString().trim();
                        String err = validateName(newName);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        boolean ok;
                        if (viaShizuku) {
                            String parent = f.getParent();
                            ok = parent != null && ShizukuFileHelper.move(
                                    f.getAbsolutePath(),
                                    new File(parent, newName).getAbsolutePath());
                        } else {
                            ok = FileOps.rename(f, newName);
                        }
                        if (!ok) {
                            Toast.makeText(a, "Đổi tên thất bại (có thể tên đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        if (onDone != null) onDone.run();
                        return true;
                    }
                }),
                new SakugaDialogs.Action("Hủy", false, null));
        AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Đổi tên")
                .setView(content)
                .show();
        holder.dialog = dialog;
    }

    public static void showNewFolder(final Activity a, final File parent, final Runnable onDone) {
        showNewFolder(a, parent, false, onDone);
    }

    // v1.2-m6: viaShizuku=true khi thư mục hiện tại cần Shizuku
    // (Android/data, obb): mkdir qua shell Shizuku thay vì java.io
    // (không có quyền ghi ở đó). Chạy đồng bộ trong onClick như
    // nhánh thường — chỉ 1 lệnh shell nhanh, cùng pattern FileOps.
    public static void showNewFolder(final Activity a, final File parent, final boolean viaShizuku, final Runnable onDone) {
        if (a == null || parent == null) return;
        final EditText input = makeInput(a, "Thư mục mới");

        // Sakuga: nut Tao/Huy bang SakugaButton (xem showRename).
        LinearLayout content = pad(a, input);
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(a, content, holder,
                new SakugaDialogs.Action("Tạo", true, new SakugaDialogs.OnAction() {
                    @Override public boolean run(AlertDialog dialog) {
                        String name = input.getText().toString().trim();
                        String err = validateName(name);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        boolean ok;
                        if (viaShizuku) {
                            ok = ShizukuFileHelper.mkdir(new File(parent, name).getAbsolutePath());
                        } else {
                            ok = FileOps.mkdir(parent, name);
                        }
                        if (!ok) {
                            Toast.makeText(a, "Tạo thư mục thất bại (có thể đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        if (onDone != null) onDone.run();
                        return true;
                    }
                }),
                new SakugaDialogs.Action("Hủy", false, null));
        AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Thư mục mới")
                .setView(content)
                .show();
        holder.dialog = dialog;
    }

    public static void showNewFile(final Activity a, final File parent, final Runnable onDone) {
        showNewFile(a, parent, false, onDone);
    }

    // v1.2-m6: viaShizuku=true khi thư mục hiện tại cần Shizuku
    // (Android/data, obb): touch qua shell Shizuku thay vì java.io
    // (không có quyền ghi ở đó). Xem ghi chú ở showNewFolder.
    public static void showNewFile(final Activity a, final File parent, final boolean viaShizuku, final Runnable onDone) {
        if (a == null || parent == null) return;
        final EditText input = makeInput(a, "tệp mới.txt");

        // Sakuga: nut Tao/Huy bang SakugaButton (xem showRename).
        LinearLayout content = pad(a, input);
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(a, content, holder,
                new SakugaDialogs.Action("Tạo", true, new SakugaDialogs.OnAction() {
                    @Override public boolean run(AlertDialog dialog) {
                        String name = input.getText().toString().trim();
                        String err = validateName(name);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        boolean ok;
                        if (viaShizuku) {
                            ok = ShizukuFileHelper.touch(new File(parent, name).getAbsolutePath());
                        } else {
                            ok = FileOps.createFile(parent, name);
                        }
                        if (!ok) {
                            Toast.makeText(a, "Tạo tệp thất bại (có thể đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        if (onDone != null) onDone.run();
                        return true;
                    }
                }),
                new SakugaDialogs.Action("Hủy", false, null));
        AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Tệp mới")
                .setView(content)
                .show();
        holder.dialog = dialog;
    }

    // ------------------------------------------------------------------
    // Properties dialog
    // ------------------------------------------------------------------

    public static void showProperties(final Activity a, final File f) {
        showProperties(a, f, false, f != null && f.isDirectory());
    }

    /**
     * v1.1-m1: version hỗ trợ file từ Shizuku.
     * @param viaShizuku true nếu file từ Shizuku (bỏ qua f.exists(), dùng isDir truyền vào)
     * @param isDir true nếu là thư mục (chỉ dùng khi viaShizuku=true)
     */
    public static void showProperties(final Activity a, final File f,
                                     final boolean viaShizuku, final boolean isDir) {
        // Đường thường: File API đọc được, dùng trực tiếp.
        showProperties(a, f, viaShizuku, isDir,
                f != null ? f.length() : 0,
                f != null ? f.lastModified() : 0, null);
    }

    /**
     * v1.2-m4: overload cho FileItem — đường Shizuku dùng size/ngày/quyền
     * đã parse từ `ls -la` lúc liệt kê, thay vì File API (không stat được
     * Android/data nên trả 0 B / epoch / trống).
     */
    public static void showProperties(final Activity a, final FileItem item) {
        if (a == null || item == null) return;
        showProperties(a, item.file, item.viaShizuku, item.isDir,
                item.size, item.lastModified, item.perms);
    }

    /**
     * Bản đầy đủ của showProperties. Khi viaShizuku=true thì size/date/perms
     * lấy từ knownSize/knownLastModified/knownPerms (đã parse từ ls);
     * khi false thì dùng File API như cũ. perms=null -> dùng
     * canRead/canWrite như cũ (đường thường).
     */
    public static void showProperties(final Activity a, final File f,
                                     final boolean viaShizuku, final boolean isDir,
                                     final long knownSize, final long knownLastModified,
                                     final String knownPerms) {
        if (a == null || f == null) return;
        if (!viaShizuku && !f.exists()) return;
        boolean isDirectory = viaShizuku ? isDir : f.isDirectory();

        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        layout.setPadding(p, p, p, p);

        TextView tvName = row(a, layout, "Tên: ", f.getName());
        row(a, layout, "Đường dẫn: ", f.getAbsolutePath());
        row(a, layout, "Loại: ", isDirectory ? "Thư mục" : "Tệp");
        final TextView tvSize = row(a, layout, "Kích thước: ",
                isDirectory ? "đang tính..."
                        : FileOps.formatSize(viaShizuku ? knownSize : f.length()));
        long mtime = viaShizuku ? knownLastModified : f.lastModified();
        row(a, layout, "Ngày sửa: ", mtime > 0 ? FileOps.formatDate(mtime) : "—");
        String permsText;
        if (viaShizuku) {
            permsText = knownPerms != null ? knownPerms : "—";
        } else {
            permsText = (f.canRead() ? "đọc " : "") + (f.canWrite() ? "ghi" : "");
        }
        row(a, layout, "Quyền: ", permsText);

        // v1.1-m1: checkbox "Vô hiệu hoá quét đa phương tiện" (.nomedia) như ZArchiver
        // -> ẩn ảnh/video trong thư mục khỏi Gallery
        if (isDirectory) {
            // v1.1-m3 fix: ĐỪNG dùng CheckBox có text (chữ bị lệch khỏi ô vuông
            // trên một số máy) — thay bằng LinearLayout ngang: CheckBox trần
            // (chỉ ô vuông) + TextView. Bấm vào chữ cũng toggle như bấm ô vuông.
            // Sakuga test (1.3-m4-sakuga-test1): CheckBox goc -> SakugaCheckBox
            // (tick ve dan + burst hong + chu ポン), interface giong CheckBox.
            LinearLayout nomediaRow = new LinearLayout(a);
            nomediaRow.setOrientation(LinearLayout.HORIZONTAL);
            nomediaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int cp = dp(a, 8);
            nomediaRow.setPadding(0, cp, 0, 0);

            final SakugaCheckBox nomediaBox = new SakugaCheckBox(a);

            TextView nomediaLabel = new TextView(a);
            nomediaLabel.setText("Vô hiệu hoá quét đa phương tiện");
            nomediaLabel.setTextColor(0xFF333333);
            nomediaLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);
            nomediaLabel.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    nomediaBox.toggle();
                }
            });

            nomediaRow.addView(nomediaBox);
            nomediaRow.addView(nomediaLabel);
            final java.io.File nomediaFile = new java.io.File(f, ".nomedia");
            final boolean isShizukuPath =
                    ShizukuFileHelper.needsShizuku(f.getAbsolutePath());
            // Kiểm tra trạng thái hiện tại
            boolean hasNomedia;
            if (isShizukuPath && ShizukuHelper.isRunning()) {
                String out = ShizukuFileHelper.runCommand(
                        "ls", nomediaFile.getAbsolutePath());
                hasNomedia = out != null && !out.startsWith("ERROR:");
            } else {
                hasNomedia = nomediaFile.exists();
            }
            nomediaBox.setChecked(hasNomedia);
            nomediaBox.setOnCheckedChangeListener(
                    new SakugaCheckBox.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(
                        SakugaCheckBox buttonView, final boolean isChecked) {
                    if (isShizukuPath && ShizukuHelper.isRunning()) {
                        // Chạy background để không block UI
                        new Thread(new Runnable() {
                            @Override public void run() {
                                if (isChecked) {
                                    ShizukuFileHelper.runCommand(
                                            "touch", nomediaFile.getAbsolutePath());
                                } else {
                                    ShizukuFileHelper.runCommand(
                                            "rm", nomediaFile.getAbsolutePath());
                                }
                                // Báo MediaScanner quét lại
                                try {
                                    a.sendBroadcast(new android.content.Intent(
                                            android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                            android.net.Uri.fromFile(f)));
                                } catch (Exception ignored) {}
                            }
                        }).start();
                    } else {
                        try {
                            if (isChecked) {
                                nomediaFile.createNewFile();
                            } else {
                                nomediaFile.delete();
                            }
                        } catch (Exception e) {
                            android.widget.Toast.makeText(a,
                                    "Lỗi: " + e.getMessage(),
                                    android.widget.Toast.LENGTH_SHORT).show();
                            // Hoàn tác checkbox nếu thất bại
                            nomediaBox.setChecked(!isChecked);
                        }
                        // Báo cho MediaScanner quét lại
                        try {
                            a.sendBroadcast(new android.content.Intent(
                                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    android.net.Uri.fromFile(f)));
                        } catch (Exception ignored) {}
                    }
                }
            });
            layout.addView(nomediaRow);
        }

        // Sakuga test (1.3-m4-sakuga-test1): nut ĐÓNG bang SakugaButton
        // (thay nut dialog goc). Bam -> burst + ドン -> done() dap tick
        // -> dong dialog sau 320ms de thay tick.
        final SakugaButton closeBtn = new SakugaButton(a);
        closeBtn.setText("ĐÓNG");
        LinearLayout closeRow = new LinearLayout(a);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        closeRow.setGravity(Gravity.END);
        closeRow.setPadding(0, dp(a, 12), 0, 0);
        closeRow.addView(closeBtn);
        layout.addView(closeRow);

        final AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Thuộc tính")
                .setView(layout)
                .show();
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeBtn.done();
                v.postDelayed(new Runnable() {
                    @Override public void run() { dialog.dismiss(); }
                }, 320);
            }
        });

        // Folder size is computed on a background thread, then posted to UI.
        // v1.2-m4 fix: đường Shizuku trước đây bị bỏ qua (kẹt "đang tính..."
        // mãi) — giờ tính bằng `du -sb` qua Shizuku (1 lệnh, không đi bộ
        // đệ quy từng file như dirSize). Lỗi -> "không tính được", không kẹt.
        if (isDirectory) {
            final String path = f.getAbsolutePath();
            if (viaShizuku) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String text = shizukuDirSizeText(path);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                tvSize.setText(text);
                            }
                        });
                    }
                }).start();
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final long size = dirSize(new File(path));
                        final String text = FileOps.formatSize(size);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                tvSize.setText(text);
                            }
                        });
                    }
                }).start();
            }
        }
    }

    /** v1.2-m4: kích thước folder qua Shizuku bằng `du` (1 lệnh shell). */
    private static String shizukuDirSizeText(String path) {
        String out = ShizukuFileHelper.runCommand(
                "sh", "-c", "du -sb \"$1\"", "kz", path);
        if (out != null && !out.startsWith("ERROR:")) {
            try {
                return FileOps.formatSize(
                        Long.parseLong(out.trim().split("\\s+")[0]));
            } catch (Exception ignored) {}
        }
        // Fallback: du -s (số block 1K) — vẫn là số thật, không kẹt.
        String out2 = ShizukuFileHelper.runCommand(
                "sh", "-c", "du -s \"$1\"", "kz", path);
        if (out2 != null && !out2.startsWith("ERROR:")) {
            try {
                return FileOps.formatSize(
                        Long.parseLong(out2.trim().split("\\s+")[0]) * 1024);
            } catch (Exception ignored) {}
        }
        return "không tính được";
    }

    // ------------------------------------------------------------------
    // Delete confirmation
    // ------------------------------------------------------------------

    public static void showDeleteConfirm(Activity a, int count, final Runnable onYes) {
        if (a == null) return;
        // Sakuga: nut Xoa/Huy bang SakugaButton (thay nut AlertDialog goc).
        LinearLayout content = SakugaDialogs.messageLayout(a,
                "Xóa " + count + " mục đã chọn? Không thể hoàn tác.");
        final SakugaDialogs.Holder holder = SakugaDialogs.holder();
        SakugaDialogs.addButtonRow(a, content, holder,
                new SakugaDialogs.Action("Xóa", true, new SakugaDialogs.OnAction() {
                    @Override public boolean run(AlertDialog dialog) {
                        if (onYes != null) onYes.run();
                        return true;
                    }
                }),
                new SakugaDialogs.Action("Hủy", false, null));
        AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Xóa")
                .setView(content)
                .show();
        holder.dialog = dialog;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static EditText makeInput(Activity a, String hint) {
        EditText input = new EditText(a);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        return input;
    }

    private static LinearLayout pad(Activity a, EditText input) {
        LinearLayout wrap = new LinearLayout(a);
        // FIX 20/11 (test3): LinearLayout mac dinh la HORIZONTAL — quen
        // set VERTICAL lam hang nut Sakuga bi day ra ngoai man hinh
        // (EditText MATCH_PARENT chiem het chieu ngang) nen dialog Doi ten
        // / Tao thu muc / Tao tep khong thay nut Luu/Tao/Huy.
        wrap.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        wrap.setPadding(p, dp(a, 8), p, dp(a, 4));
        // 26/10 (feedback Kazoi, dialog Doi ten): EditText mac dinh
        // WRAP_CONTENT nen gach chan chi om theo chu ("b" = 1 ky tu
        // -> vung bam ti hon, kho sua ten). Cho full width dialog.
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private static TextView row(Activity a, LinearLayout layout, String label, String value) {
        TextView labelView = new TextView(a);
        labelView.setText(label);
        labelView.setTextSize(14);

        TextView valueView = new TextView(a);
        valueView.setText(value);
        valueView.setTextSize(14);
        valueView.setPadding(0, 0, 0, dp(a, 8));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        labelView.setGravity(Gravity.TOP);
        row.addView(labelView);
        row.addView(valueView);
        layout.addView(row);
        return valueView;
    }

    private static int dp(Activity a, int dp) {
        float density = a.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    /** Returns error message or null if the name is valid. */
    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Tên không được để trống";
        }
        if (name.contains("/")) {
            return "Tên không được chứa ký tự /";
        }
        return null;
    }

    private static long dirSize(File dir) {
        long size = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    size += dirSize(child);
                } else {
                    size += child.length();
                }
            }
        }
        return size;
    }
}
