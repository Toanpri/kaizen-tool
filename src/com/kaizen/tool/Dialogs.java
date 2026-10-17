package com.kaizen.tool;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
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
        if (a == null || f == null || !f.exists()) return;
        final String current = f.getName();
        final EditText input = makeInput(a, current);
        // pre-fill current name, select the part before the extension
        input.setText(current);
        int dot = current.lastIndexOf('.');
        int end = (f.isFile() && dot > 0) ? dot : current.length();
        input.setSelection(0, end);

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Đổi tên")
                .setView(pad(a, input))
                .setPositiveButton("Lưu", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String newName = input.getText().toString().trim();
                        String err = validateName(newName);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!FileOps.rename(f, newName)) {
                            Toast.makeText(a, "Đổi tên thất bại (có thể tên đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (onDone != null) onDone.run();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public static void showNewFolder(final Activity a, final File parent, final Runnable onDone) {
        if (a == null || parent == null) return;
        final EditText input = makeInput(a, "Thư mục mới");

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Thư mục mới")
                .setView(pad(a, input))
                .setPositiveButton("Tạo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        String err = validateName(name);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!FileOps.mkdir(parent, name)) {
                            Toast.makeText(a, "Tạo thư mục thất bại (có thể đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (onDone != null) onDone.run();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public static void showNewFile(final Activity a, final File parent, final Runnable onDone) {
        if (a == null || parent == null) return;
        final EditText input = makeInput(a, "tệp mới.txt");

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Tệp mới")
                .setView(pad(a, input))
                .setPositiveButton("Tạo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        String err = validateName(name);
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!FileOps.createFile(parent, name)) {
                            Toast.makeText(a, "Tạo tệp thất bại (có thể đã tồn tại)", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (onDone != null) onDone.run();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ------------------------------------------------------------------
    // Properties dialog
    // ------------------------------------------------------------------

    public static void showProperties(final Activity a, final File f) {
        if (a == null || f == null || !f.exists()) return;

        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        layout.setPadding(p, p, p, p);

        TextView tvName = row(a, layout, "Tên: ", f.getName());
        row(a, layout, "Đường dẫn: ", f.getAbsolutePath());
        row(a, layout, "Loại: ", f.isDirectory() ? "Thư mục" : "Tệp");
        final TextView tvSize = row(a, layout, "Kích thước: ",
                f.isDirectory() ? "đang tính..." : FileOps.formatSize(f.length()));
        row(a, layout, "Ngày sửa: ", FileOps.formatDate(f.lastModified()));
        row(a, layout, "Quyền: ", (f.canRead() ? "đọc " : "") + (f.canWrite() ? "ghi" : ""));

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Thuộc tính")
                .setView(layout)
                .setPositiveButton("Đóng", null)
                .show();

        // Folder size is computed on a background thread, then posted to UI.
        if (f.isDirectory()) {
            final String path = f.getAbsolutePath();
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

    // ------------------------------------------------------------------
    // Delete confirmation
    // ------------------------------------------------------------------

    public static void showDeleteConfirm(Activity a, int count, final Runnable onYes) {
        if (a == null) return;
        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Xóa")
                .setMessage("Xóa " + count + " mục đã chọn? Không thể hoàn tác.")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (onYes != null) onYes.run();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
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
        int p = dp(a, 16);
        wrap.setPadding(p, dp(a, 8), p, dp(a, 4));
        wrap.addView(input);
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
