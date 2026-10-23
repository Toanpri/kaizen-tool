package com.kaizen.tool;

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * M3 — archive dialogs for Kaizen Tool (pink DialogTheme, pure android.app).
 * Replaces the M2 ZipDialogs with format-aware versions (ZIP / 7Z).
 *
 * Long operations run on a background thread; progress and completion are
 * posted back to the UI thread with runOnUiThread.
 */
public class ArchiveDialogs {

    // Compress formats (indices into FMT_EXTS / FMT_LABELS)
    // M5: bỏ nén TAR / TAR.GZ — chỉ giữ ZIP, 7Z và REM (format độc quyền Kaizen Tool).
    private static final int FMT_ZIP = 0;
    private static final int FMT_7Z = 1;
    private static final int FMT_REM = 2;

    private static final String[] FMT_LABELS = new String[]{"ZIP", "7Z", "REM"};
    private static final String[] FMT_EXTS = new String[]{".zip", ".7z", ".rem"};

    // ------------------------------------------------------------------
    // Compress dialog (format + name + level)
    // ------------------------------------------------------------------

    /**
     * Dialog: format RadioGroup, EditText for archive name,
     * Spinner for compression level, button "Nén".
     */
    // ------------------------------------------------------------------
    // Compress dialog — kiểu ZArchiver ("Tạo tệp lưu trữ")
    // ------------------------------------------------------------------

    private static final long[] SPLIT_SIZES = new long[]{
            0, 10L * 1024 * 1024, 50L * 1024 * 1024, 100L * 1024 * 1024,
            500L * 1024 * 1024, 1024L * 1024 * 1024
    };
    private static final String[] SPLIT_LABELS = new String[]{
            "Không", "10MB", "50MB", "100MB", "500MB", "1GB"
    };

    /**
     * Dialog "Tạo tệp lưu trữ" kiểu ZArchiver: tên file, định dạng (ZIP/7Z/REM),
     * mức nén, mã hóa + mật khẩu, chia tập, xóa nguồn sau khi nén.
     */
    public static void showCompress(final Activity a, final List<File> sources,
                                    final File currentDir, final Runnable onDone) {
        if (a == null || sources == null || sources.isEmpty()) return;

        final android.view.View v =
                a.getLayoutInflater().inflate(R.layout.dialog_compress, null);
        final EditText nameInput = (EditText) v.findViewById(R.id.cmp_name);
        final Spinner fmtSpinner = (Spinner) v.findViewById(R.id.cmp_format);
        final Spinner levelSpinner = (Spinner) v.findViewById(R.id.cmp_level);
        final Spinner encSpinner = (Spinner) v.findViewById(R.id.cmp_encrypt);
        final android.view.View pwRow = v.findViewById(R.id.cmp_pw_row);
        final EditText pwInput = (EditText) v.findViewById(R.id.cmp_password);
        final Button pwEye = (Button) v.findViewById(R.id.cmp_pw_eye);
        final Spinner splitSpinner = (Spinner) v.findViewById(R.id.cmp_split);
        final CheckBox delSrcBox = (CheckBox) v.findViewById(R.id.cmp_delete_src);

        // --- format ---
        ArrayAdapter<String> fmtAdapter = new ArrayAdapter<String>(a,
                android.R.layout.simple_spinner_item, FMT_LABELS);
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fmtSpinner.setAdapter(fmtAdapter);

        // --- level ---
        final String[] levelLabels = new String[]{
                "Lưu trữ", "Nhanh", "Bình thường", "Tối đa"
        };
        final int[] levelValues = new int[]{
                ZipUtils.LEVEL_STORE, ZipUtils.LEVEL_FAST,
                ZipUtils.LEVEL_NORMAL, ZipUtils.LEVEL_MAX
        };
        ArrayAdapter<String> levelAdapter = new ArrayAdapter<String>(a,
                android.R.layout.simple_spinner_item, levelLabels);
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        levelSpinner.setAdapter(levelAdapter);
        levelSpinner.setSelection(2); // "Bình thường" = 6

        // --- encryption options (đổi label theo format) ---
        final String[][] encOptions = new String[][]{
                {"Không", "Dữ liệu (AES-256)"},      // ZIP
                {"Không"},                            // 7Z: chưa hỗ trợ mã hóa khi ghi
                {"Không", "Dữ liệu (mật khẩu)"}       // REM: XOR key từ mật khẩu
        };
        final ArrayAdapter<String> encAdapter = new ArrayAdapter<String>(a,
                android.R.layout.simple_spinner_item,
                new java.util.ArrayList<String>());
        encAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encSpinner.setAdapter(encAdapter);

        // --- split ---
        ArrayAdapter<String> splitAdapter = new ArrayAdapter<String>(a,
                android.R.layout.simple_spinner_item, SPLIT_LABELS);
        splitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        splitSpinner.setAdapter(splitAdapter);

        // --- name ---
        nameInput.setText(defaultArchiveName(sources, FMT_ZIP));
        selectBaseName(nameInput, FMT_ZIP);

        final Runnable refreshEnc = new Runnable() {
            @Override public void run() {
                int fmt = fmtSpinner.getSelectedItemPosition();
                if (fmt < 0 || fmt >= encOptions.length) fmt = FMT_ZIP;
                encAdapter.clear();
                for (String o : encOptions[fmt]) encAdapter.add(o);
                encAdapter.notifyDataSetChanged();
                encSpinner.setEnabled(encOptions[fmt].length > 1);
                encSpinner.setSelection(0);
                pwRow.setVisibility(View.GONE);
                pwInput.setText("");
            }
        };
        fmtSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                if (position < 0 || position >= FMT_EXTS.length) return;
                String cur = nameInput.getText().toString().trim();
                nameInput.setText(replaceExtension(cur, FMT_EXTS[position]));
                selectBaseName(nameInput, position);
                refreshEnc.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        refreshEnc.run();

        encSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                boolean needPw = position > 0;
                pwRow.setVisibility(needPw ? View.VISIBLE : View.GONE);
                if (!needPw) pwInput.setText("");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        pwEye.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) {
                int type = pwInput.getInputType();
                boolean showing = (type & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0;
                pwInput.setInputType(showing
                        ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
                pwInput.setSelection(pwInput.getText().length());
                pwEye.setText(showing ? "\uD83D\uDC41" : "\uD83D\uDE45");
            }
        });

        final AlertDialog dlg = new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Tạo tệp lưu trữ")
                .setView(v)
                .setPositiveButton("OK", null) // gắn listener sau để validate không tắt dialog
                .setNegativeButton("HUỶ", null)
                .create();
        dlg.show();
        CatPopup.apply(dlg); // nền mèo (trừ dialog tiến trình Rem)
        Button okBtn = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) {
                final int fmt = fmtSpinner.getSelectedItemPosition();
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(a, "Tên file nén không được để trống", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (name.contains("/")) {
                    Toast.makeText(a, "Tên file không được chứa ký tự /", Toast.LENGTH_SHORT).show();
                    return;
                }
                String ext = FMT_EXTS[fmt < 0 ? FMT_ZIP : fmt];
                if (!name.toLowerCase().endsWith(ext)) {
                    name = stripKnownExtension(name) + ext;
                }
                final boolean encrypted = encSpinner.getSelectedItemPosition() > 0;
                final String password = pwInput.getText().toString();
                if (encrypted && password.isEmpty()) {
                    Toast.makeText(a, "Chưa nhập mật khẩu", Toast.LENGTH_SHORT).show();
                    return;
                }
                File dir = currentDir != null ? currentDir : a.getFilesDir();
                final File dest = new File(dir, name);
                final int level = levelValues[levelSpinner.getSelectedItemPosition()];
                final long splitSize = SPLIT_SIZES[splitSpinner.getSelectedItemPosition()];
                final boolean deleteSrc = delSrcBox.isChecked();
                final String fmtLabel = FMT_LABELS[fmt < 0 ? FMT_ZIP : fmt];
                final int fmtFinal = fmt < 0 ? FMT_ZIP : fmt;
                final String pwFinal = encrypted ? password : null;
                final String finalName = name;
                final String finalExt = ext;

                dlg.dismiss();
                splitDestDir = dir;
                destName = finalName;
                destExt = finalExt;
                runArchiveOp(a, "Đang nén (" + fmtLabel + ")...", new Op() {
                    @Override
                    public void start(FileOps.OpCallback cb, CancelToken token) {
                        FileOps.OpCallback wrapped = (splitSize > 0 || deleteSrc)
                                ? new SplitDeleteCallback(a, cb, token, splitSize, deleteSrc, sources)
                                : cb;
                        if (fmtFinal == FMT_7Z) {
                            // v1.1-m3: thử 7zz native (đa luồng, siêu nhanh) trước,
                            // không được thì fallback sang Java.
                            if (!Native7z.compress(a, Native7z.FMT_7Z, sources, dest,
                                    level, pwFinal, wrapped, token)) {
                                SevenZUtils.create7z(sources, dest, level, wrapped, token);
                            }
                        } else if (fmtFinal == FMT_REM) {
                            RemArchive.createRem(sources, dest, level, pwFinal, wrapped, token);
                        } else if (pwFinal != null) {
                            if (!Native7z.compress(a, Native7z.FMT_ZIP, sources, dest,
                                    level, pwFinal, wrapped, token)) {
                                ZipUtils.createZipEncrypted(sources, dest, level, pwFinal, wrapped, token);
                            }
                        } else {
                            if (!Native7z.compress(a, Native7z.FMT_ZIP, sources, dest,
                                    level, null, wrapped, token)) {
                                ZipUtils.createZip(sources, dest, level, wrapped, token);
                            }
                        }
                    }
                }, "Đã nén xong: " + dest.getName(), onDone);
            }
        });
    }

    /**
     * Callback bọc sau khi nén xong: chia volume (nếu chọn) rồi xóa nguồn (nếu chọn).
     * Tìm file nén vừa tạo = file mới nhất có tên bắt đầu bằng base name trong thư mục đích.
     */
    private static class SplitDeleteCallback implements FileOps.OpCallback {
        private final Activity a;
        private final FileOps.OpCallback inner;
        private final CancelToken token;
        private final long splitSize;
        private final boolean deleteSrc;
        private final List<File> sources;

        SplitDeleteCallback(Activity a, FileOps.OpCallback inner, CancelToken token,
                            long splitSize, boolean deleteSrc, List<File> sources) {
            this.a = a;
            this.inner = inner;
            this.token = token;
            this.splitSize = splitSize;
            this.deleteSrc = deleteSrc;
            this.sources = sources;
        }

        @Override public void onProgress(int done, int total, String name) {
            if (inner != null) inner.onProgress(done, total, name);
        }

        @Override public void onDone(boolean ok, String err) {
            if (!ok) {
                if (inner != null) inner.onDone(false, err);
                return;
            }
            // Tạm xong nén -> xử lý split + xóa nguồn trên thread hiện tại (đã là background)
            try {
                if (splitSize > 0) {
                    String splitErr = splitNewestArchive();
                    if (splitErr != null) {
                        if (inner != null) inner.onDone(false, splitErr);
                        return;
                    }
                }
                if (deleteSrc) {
                    final java.util.concurrent.CountDownLatch latch =
                            new java.util.concurrent.CountDownLatch(1);
                    final boolean[] delOk = new boolean[1];
                    final String[] delErr = new String[1];
                    FileOps.deleteFiles(sources, new FileOps.OpCallback() {
                        @Override public void onProgress(int d, int t, String n) {
                            if (inner != null) inner.onProgress(d, t, "Đang xóa nguồn: " + n);
                        }
                        @Override public void onDone(boolean ok2, String err2) {
                            delOk[0] = ok2;
                            delErr[0] = err2;
                            latch.countDown();
                        }
                    });
                    latch.await();
                    if (!delOk[0]) {
                        if (inner != null) inner.onDone(false, delErr[0]);
                        return;
                    }
                }
            } catch (Exception e) {
                if (inner != null) inner.onDone(false, e.getMessage());
                return;
            }
            if (inner != null) inner.onDone(true, null);
        }

        /** Chia file nén vừa tạo thành các tập. Trả về lỗi hoặc null. */
        private String splitNewestArchive() {
            // create*() dùng uniqueFile() nên tên có thể đổi (" (1)") —
            // tìm file mới nhất khớp base name + ext trong thư mục đích.
            File dir = destDirOfSources();
            if (dir == null) return "Chia tập: không xác định được thư mục đích";
            String baseLower = stripKnownExtension(destName).toLowerCase();
            String extLower = destExt.toLowerCase();
            File[] files = dir.listFiles();
            File best = null;
            long bestT = 0;
            long now = System.currentTimeMillis();
            if (files != null) {
                for (File f : files) {
                    if (!f.isFile()) continue;
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(extLower) && n.startsWith(baseLower)
                            && (now - f.lastModified()) < 180000) {
                        if (f.lastModified() >= bestT) {
                            bestT = f.lastModified();
                            best = f;
                        }
                    }
                }
            }
            if (best == null) return "Chia tập: không tìm thấy file nén vừa tạo";
            return splitFile(best, splitSize);
        }

        private File destDirOfSources() {
            // dest đã có trong closure của showCompress — truyền qua field
            return splitDestDir;
        }
    }

    // Field phụ cho SplitDeleteCallback (được gán trước khi tạo callback)
    private static File splitDestDir;
    private static String destName;
    private static String destExt;

    /**
     * Chia file thành các tập partSize bytes.
     * ZIP: foo.z01, foo.z02, ..., foo.zip (tập cuối giữ .zip).
     * 7Z/REM: foo.7z.001, foo.7z.002, ... / foo.rem.001, ...
     * Trả về null nếu OK, hoặc message lỗi.
     */
    static String splitFile(File src, long partSize) {
        if (src == null || !src.isFile()) return "File nguồn không tồn tại";
        if (partSize <= 0) return "Kích thước tập không hợp lệ";
        long len = src.length();
        int parts = (int) ((len + partSize - 1) / partSize);
        if (parts <= 1) return null; // nhỏ hơn 1 tập -> không cần chia
        String name = src.getName();
        boolean isZip = name.toLowerCase().endsWith(".zip");
        String base = isZip ? name.substring(0, name.length() - 4) : name;
        java.io.InputStream in = null;
        java.util.List<File> tmps = new java.util.ArrayList<File>();
        java.util.List<String> finals = new java.util.ArrayList<String>();
        try {
            in = new java.io.BufferedInputStream(new java.io.FileInputStream(src), 65536);
            byte[] buf = new byte[65536];
            for (int i = 1; i <= parts; i++) {
                String partName;
                if (isZip) {
                    partName = (i == parts) ? name
                            : base + ".z" + String.format(java.util.Locale.US, "%02d", i);
                } else {
                    partName = name + "."
                            + String.format(java.util.Locale.US, "%03d", i);
                }
                // Ghi ra temp trước: tập cuối ZIP trùng tên file gốc,
                // không được ghi đè trực tiếp rồi mới xóa gốc.
                File tmp = new File(src.getParentFile(), partName + ".part");
                java.io.OutputStream os = null;
                try {
                    os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp), 65536);
                    long remain = Math.min(partSize, len - (long) (i - 1) * partSize);
                    while (remain > 0) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, remain));
                        if (n < 0) break;
                        os.write(buf, 0, n);
                        remain -= n;
                    }
                } finally {
                    if (os != null) try { os.close(); } catch (Exception ignored) {}
                }
                tmps.add(tmp);
                finals.add(partName);
            }
        } catch (Exception e) {
            for (File t : tmps) t.delete();
            return "Chia tập thất bại: " + e.getMessage();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
        src.delete();
        for (int i = 0; i < tmps.size(); i++) {
            File dest = new File(src.getParentFile(), finals.get(i));
            if (dest.exists()) dest.delete();
            if (!tmps.get(i).renameTo(dest)) {
                return "Chia tập: không đổi tên được " + finals.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Archive tap options: Open / Extract / Cancel
    // ------------------------------------------------------------------

    /**
     * onOpen may be null for non-browsable formats (GZ/BZ2/XZ) — then the
     * "Mở" button is hidden.
     */
    public static void showArchiveOptions(final Activity a, final File archiveFile,
                                          final int kind,
                                          final Runnable onOpen, final Runnable onExtract) {
        if (a == null || archiveFile == null) return;
        AlertDialog.Builder b = new CatBuilder(a, R.style.DialogTheme)
                .setTitle(archiveFile.getName());
        if (onOpen != null && ArchiveUtils.isBrowsable(kind)) {
            b.setPositiveButton("Mở", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    onOpen.run();
                }
            });
        }
        b.setNeutralButton("Giải nén", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                if (onExtract != null) onExtract.run();
            }
        });
        b.setNegativeButton("Hủy", null);
        b.show();
    }

    // ------------------------------------------------------------------
    // Extract whole archive dialog
    // ------------------------------------------------------------------

    public static void showExtract(final Activity a, final File archiveFile, final int kind,
                                   final File defaultDestDir, final Runnable onDone) {
        if (a == null || archiveFile == null) return;
        showExtractCommon(a, archiveFile, kind, archiveFile.getName(), new ExtractConfig() {
            @Override
            public void run(final File destDir, final boolean overwrite, final String password,
                            final FileOps.OpCallback cb, final CancelToken token) {
                ArchiveUtils.extractAll(archiveFile, kind, destDir, overwrite, password, cb, token);
            }
        }, defaultDestDir, "Đã giải nén xong", onDone);
    }

    // ------------------------------------------------------------------
    // v1.1-m2: giải nén trực tiếp (không dialog nhập đường dẫn) — kiểu ZArchiver
    // ------------------------------------------------------------------

    /**
     * Giải nén thẳng vào destDir, không hỏi đường dẫn.
     * overwrite=false, password=null (hỏi mật khẩu nếu archive mã hóa).
     */
    public static void extractDirect(final Activity a, final File archiveFile, final int kind,
                                     final File destDir, final Runnable onDone) {
        if (a == null || archiveFile == null || destDir == null) return;
        // v1.2-m1: đích ở vùng Shizuku (Android/data...) — File API không
        // chạm được nên trước đây luôn văng "Không tạo được thư mục đích".
        if (destNeedsShizuku(destDir)) {
            if (ArchiveUtils.needsPassword(archiveFile, kind)) {
                askPassword(a, new PasswordListener() {
                    @Override public void onPassword(String pw) {
                        if (pw == null) return;
                        runExtractViaShizuku(a, archiveFile, kind, null, destDir, pw,
                                "Đã giải nén xong", onDone);
                    }
                });
            } else {
                runExtractViaShizuku(a, archiveFile, kind, null, destDir, null,
                        "Đã giải nén xong", onDone);
            }
            return;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            Toast.makeText(a, "Không tạo được thư mục đích", Toast.LENGTH_SHORT).show();
            return;
        }
        checkSpaceThenRun(a, archiveFile, kind, null, destDir, new Runnable() {
            @Override public void run() {
                final ExtractConfig config = new ExtractConfig() {
                    @Override
                    public void run(final File d, final boolean overwrite, final String password,
                                    final FileOps.OpCallback cb, final CancelToken token) {
                        ArchiveUtils.extractAll(archiveFile, kind, d, overwrite, password, cb, token);
                    }
                };
                if (ArchiveUtils.needsPassword(archiveFile, kind)) {
                    askPassword(a, new PasswordListener() {
                        @Override public void onPassword(String pw) {
                            if (pw == null) return; // user hủy
                            startExtract(a, destDir, false, pw, config, "Đã giải nén xong", onDone);
                        }
                    });
                } else {
                    startExtract(a, destDir, false, null, config, "Đã giải nén xong", onDone);
                }
            }
        });
    }

    /**
     * Dialog 3 lựa chọn giải nén kiểu ZArchiver:
     * "Giải nén tại đây" / "Giải nén vào ./&lt;tên&gt;/" / "Giải nén...".
     * @param onPickDest chạy khi user chọn "Giải nén..." (để host vào chế độ chọn thư mục)
     */
    public static void showExtractChoices(final Activity a, final File archiveFile, final int kind,
                                          final File currentDir, final Runnable onPickDest,
                                          final Runnable onDone) {
        if (a == null || archiveFile == null) return;
        final String base = stripExtension(archiveFile.getName());
        final String[] items = new String[]{
                "Giải nén tại đây",
                "Giải nén vào ./ " + base + "/",
                "Giải nén..."
        };
        new CatBuilder(a, R.style.DialogTheme)
                .setTitle(archiveFile.getName())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            extractDirect(a, archiveFile, kind, currentDir, onDone);
                        } else if (which == 1) {
                            File sub = new File(currentDir, base);
                            extractDirect(a, archiveFile, kind, sub, onDone);
                        } else {
                            if (onPickDest != null) onPickDest.run();
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** "Gemini Translate.zip" -> "Gemini Translate" */
    public static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        // giữ nguyên nếu là file ẩn kiểu ".nomedia" (dot ở đầu)
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------------
    // Extract selected entries (archive browser mode)
    // ------------------------------------------------------------------

    public static void showExtractEntries(final Activity a, final File archiveFile,
                                          final int kind, final List<String> entryNames,
                                          final File defaultDestDir, final Runnable onDone) {
        if (a == null || archiveFile == null || entryNames == null || entryNames.isEmpty()) return;
        showExtractCommon(a, archiveFile, kind,
                entryNames.size() + " mục trong " + ArchiveUtils.kindLabel(kind),
                new ExtractConfig() {
                    @Override
                    public void run(final File destDir, final boolean overwrite, final String password,
                                    final FileOps.OpCallback cb, final CancelToken token) {
                        ArchiveUtils.extractEntries(archiveFile, kind, entryNames,
                                destDir, overwrite, password,
                                ArchiveUtils.commonParentPrefix(entryNames), cb, token);
                    }
                }, defaultDestDir, "Đã giải nén xong", onDone);
    }

    /**
     * Giải nén các entry đã chọn THẲNG vào destDir (không hiện dialog nhập
     * đường dẫn — dùng cho chế độ "Sao chép" chọn chỗ bằng FAB).
     */
    public static void extractEntriesDirect(final Activity a, final File archiveFile, final int kind,
                                            final List<String> entryNames, final File destDir,
                                            final Runnable onDone) {
        if (a == null || archiveFile == null || entryNames == null || entryNames.isEmpty()
                || destDir == null) return;
        // v1.2-m1 (theo ý Kazoi): đích đã có mục trùng tên thì HỎI trước
        // "Ghi đè không?" — Có thì đè, Hủy thì thôi, không tự ý xử lý.
        resolveEntryOverwrite(a, entryNames, destDir, new OverwriteChoice() {
            @Override public void onProceed() {
                doExtractEntriesDirect(a, archiveFile, kind, entryNames, destDir, onDone);
            }
            @Override public void onCancel() { /* hủy hẳn, không đụng vào đích */ }
        });
    }

    private static void doExtractEntriesDirect(final Activity a, final File archiveFile,
                                               final int kind, final List<String> entryNames,
                                               final File destDir,
                                               final Runnable onDone) {
        // v1.2-m1: đích ở vùng Shizuku (Android/data...) — đi đường vòng
        // qua cache rồi đẩy bằng shell, thay vì chết ở mkdirs như trước.
        if (destNeedsShizuku(destDir)) {
            if (ArchiveUtils.needsPassword(archiveFile, kind)) {
                askPassword(a, new PasswordListener() {
                    @Override public void onPassword(String pw) {
                        if (pw == null) return;
                        runExtractViaShizuku(a, archiveFile, kind, entryNames, destDir, pw,
                                "Đã sao chép xong", onDone);
                    }
                });
            } else {
                runExtractViaShizuku(a, archiveFile, kind, entryNames, destDir, null,
                        "Đã sao chép xong", onDone);
            }
            return;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            Toast.makeText(a, "Không tạo được thư mục đích", Toast.LENGTH_SHORT).show();
            return;
        }
        checkSpaceThenRun(a, archiveFile, kind, entryNames, destDir, new Runnable() {
            @Override public void run() {
                final ExtractConfig config = new ExtractConfig() {
                    @Override
                    public void run(final File d, final boolean overwrite, final String password,
                                    final FileOps.OpCallback cb, final CancelToken token) {
                        ArchiveUtils.extractEntries(archiveFile, kind, entryNames,
                                d, overwrite, password,
                                ArchiveUtils.commonParentPrefix(entryNames), cb, token);
                    }
                };
                if (ArchiveUtils.needsPassword(archiveFile, kind)) {
                    askPassword(a, new PasswordListener() {
                        @Override public void onPassword(String pw) {
                            if (pw == null) return; // user hủy
                            startExtract(a, destDir, true, pw, config, "Đã sao chép xong", onDone);
                        }
                    });
                } else {
                    startExtract(a, destDir, true, null, config, "Đã sao chép xong", onDone);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // v1.2-m1: hỏi ghi đè khi đích đã có mục trùng tên (luồng Sao chép)
    // ------------------------------------------------------------------

    private interface OverwriteChoice {
        void onProceed();
        void onCancel();
    }

    /**
     * Kiểm tra các mục cấp-1 (sau khi bỏ tiền tố thư mục cha) có mục nào
     * đã tồn tại ở đích không. Không trùng -> chạy ngay với overwrite=true
     * (không có gì để đè). Có trùng -> popup hỏi "Ghi đè không?":
     * Có -> chạy tiếp (đè), Hủy -> dừng hẳn.
     */
    private static void resolveEntryOverwrite(final Activity a,
                                              final List<String> entryNames,
                                              final File destDir,
                                              final OverwriteChoice choice) {
        new Thread(new Runnable() {
            @Override public void run() {
                String strip = ArchiveUtils.commonParentPrefix(entryNames);
                java.util.LinkedHashSet<String> tops =
                        new java.util.LinkedHashSet<String>();
                for (String n : entryNames) {
                    String rel = ArchiveUtils.stripPrefix(n, strip);
                    if (rel == null || rel.isEmpty()) continue;
                    int slash = rel.indexOf('/');
                    tops.add(slash >= 0 ? rel.substring(0, slash) : rel);
                }
                final boolean shizuku = destNeedsShizuku(destDir);
                final java.util.List<String> conflicts =
                        new java.util.ArrayList<String>();
                for (String t : tops) {
                    boolean exists = shizuku
                            ? shellPathExists(destDir.getAbsolutePath() + "/" + t)
                            : new File(destDir, t).exists();
                    if (exists) conflicts.add(t);
                }
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (conflicts.isEmpty()) {
                            choice.onProceed();
                            return;
                        }
                        StringBuilder msg = new StringBuilder(
                                "Thư mục đích đã có sẵn các mục trùng tên:");
                        for (int i = 0; i < conflicts.size() && i < 5; i++) {
                            msg.append("\n• ").append(conflicts.get(i));
                        }
                        if (conflicts.size() > 5) msg.append("\n• ...");
                        msg.append("\n\nGhi đè lên các mục này?");
                        new CatBuilder(a, R.style.DialogTheme)
                                .setTitle("Mục đã tồn tại")
                                .setMessage(msg.toString())
                                .setPositiveButton("Có",
                                        new DialogInterface.OnClickListener() {
                                            @Override public void onClick(
                                                    DialogInterface d, int w) {
                                                choice.onProceed();
                                            }
                                        })
                                .setNegativeButton("Hủy",
                                        new DialogInterface.OnClickListener() {
                                            @Override public void onClick(
                                                    DialogInterface d, int w) {
                                                choice.onCancel();
                                            }
                                        })
                                .show();
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // v1.2-m1: đích ở vùng Shizuku + cảnh báo dung lượng trước khi giải nén
    // ------------------------------------------------------------------

    /**
     * Đích (hoặc tổ tiên gần nhất đang tồn tại của nó) có nằm ở vùng chỉ
     * đọc/ghi được qua Shizuku không. Kiểm tra tổ tiên để đích chưa tồn
     * tại (vd "./<tên file>/") trong thư mục thường không bị nhận nhầm.
     */
    private static boolean destNeedsShizuku(File destDir) {
        File p = destDir;
        while (p != null && !p.exists()) p = p.getParentFile();
        if (p == null) return false;
        return ShizukuFileHelper.needsShizuku(p.getAbsolutePath());
    }

    /**
     * Giải nén vào thư mục tạm trong cache, rồi đẩy kết quả vào đích
     * Shizuku bằng shell (mkdir -p + cp -r). entryNames == null = cả archive.
     */
    private static void runExtractViaShizuku(final Activity a, final File archiveFile,
                                             final int kind, final List<String> entryNames,
                                             final File destDir, final String password,
                                             final String successMsg, final Runnable onDone) {
        final boolean whole = entryNames == null;
        runArchiveOp(a, whole ? "Đang giải nén..." : "Đang sao chép...",
                new Op() {
                    @Override public void start(final FileOps.OpCallback cb,
                                                final CancelToken token) {
                        // v1.2-m1 (vá theo test của Kazoi): staging PHẢI nằm ở
                        // bộ nhớ ngoài (external cache). Để ở cache riêng
                        // /data/user/0/... thì tiến trình Shizuku (user shell)
                        // không đọc được -> cp "Permission denied".
                        File base = a.getExternalCacheDir();
                        if (base == null) base = a.getCacheDir();
                        final File staging = new File(base,
                                "kaizen-extract-" + System.currentTimeMillis());
                        FileOps.OpCallback stageCb = new FileOps.OpCallback() {
                            @Override public void onProgress(int d, int t, String n) {
                                cb.onProgress(d, t, n);
                            }
                            @Override public void onDone(boolean ok, String err) {
                                if (!ok) {
                                    deleteRecursiveQuiet(staging);
                                    cb.onDone(false, err);
                                    return;
                                }
                                pushStagingToShizuku(staging, destDir, cb, token);
                            }
                        };
                        if (whole) {
                            ArchiveUtils.extractAll(archiveFile, kind, staging, true,
                                    password, stageCb, token);
                        } else {
                            ArchiveUtils.extractEntries(archiveFile, kind, entryNames,
                                    staging, true, password,
                                    ArchiveUtils.commonParentPrefix(entryNames),
                                    stageCb, token);
                        }
                    }
                }, successMsg, onDone);
    }

    /** Đẩy từng mục con của staging vào đích bằng cp -r qua Shizuku. */
    private static void pushStagingToShizuku(final File staging, final File destDir,
                                             final FileOps.OpCallback cb,
                                             final CancelToken token) {
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    if (!ShizukuFileHelper.mkdir(destDir.getAbsolutePath())) {
                        err = ShizukuFileHelper.getLastError();
                    } else {
                        File[] kids = staging.listFiles();
                        if (kids != null) {
                            for (int i = 0; i < kids.length; i++) {
                                if (token != null && token.isCancelled()) {
                                    err = "Đã hủy";
                                    break;
                                }
                                cb.onProgress(i + 1, kids.length, kids[i].getName());
                                String destKid = destDir.getAbsolutePath()
                                        + "/" + kids[i].getName();
                                boolean ok;
                                if (kids[i].isDirectory()
                                        && shellPathExists(destKid)) {
                                    // v1.2-m1 vá 3 (theo test của Kazoi):
                                    // đích ĐÃ có folder trùng tên -> GỘP
                                    // nội dung vào trong. cp -r thẳng vào
                                    // folder có sẵn làm nó lồng thêm một
                                    // tầng (b/b/c, c/c) thay vì merge như
                                    // giải nén thường.
                                    ok = ShizukuFileHelper.mkdir(destKid)
                                            && pushMergeDir(kids[i], destKid, token);
                                } else {
                                    // Chưa có ở đích (hoặc là file): chép
                                    // nguyên cây / ghi đè như cũ.
                                    ok = ShizukuFileHelper.copy(
                                            kids[i].getAbsolutePath(), destKid);
                                }
                                if (!ok) {
                                    err = ShizukuFileHelper.getLastError();
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    err = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    deleteRecursiveQuiet(staging);
                }
                cb.onDone(err == null, err);
            }
        }).start();
    }

    /** Đích có tồn tại không, hỏi qua shell (File API không stat được vùng data). */
    private static boolean shellPathExists(String path) {
        try {
            String r = ShizukuFileHelper.runCommand("ls", "-d", path);
            return r != null && !r.startsWith("ERROR");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Đổ nội dung srcDir vào destDir (đã tồn tại ở đích) theo kiểu gộp
     * đệ quy: thư mục con thì mkdir rồi đệ quy, file thì cp ghi đè.
     */
    private static boolean pushMergeDir(File srcDir, String destDirPath,
                                        CancelToken token) {
        File[] kids = srcDir.listFiles();
        if (kids == null) return true;
        java.util.Arrays.sort(kids,
                (x, y) -> x.getName().compareTo(y.getName()));
        for (File kid : kids) {
            if (token != null && token.isCancelled()) return false;
            String destKid = destDirPath + "/" + kid.getName();
            if (kid.isDirectory()) {
                if (!ShizukuFileHelper.mkdir(destKid)) return false;
                if (!pushMergeDir(kid, destKid, token)) return false;
            } else {
                if (!ShizukuFileHelper.copy(kid.getAbsolutePath(), destKid)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void deleteRecursiveQuiet(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursiveQuiet(k);
            }
        }
        try { f.delete(); } catch (Exception ignored) {}
    }

    /**
     * Ước tính dung lượng giải nén (tổng size các entry sẽ bung) và so
     * với chỗ trống ở đích; thiếu thì hỏi xác nhận trước thay vì để máy
     * báo "hết dung lượng" giữa chừng. entryNames == null = cả archive.
     */
    private static void checkSpaceThenRun(final Activity a, final File af, final int kind,
                                          final List<String> entryNames, final File destDir,
                                          final Runnable proceed) {
        new Thread(new Runnable() {
            @Override public void run() {
                long need = 0;
                try {
                    List<ArchiveEntryInfo> all = ArchiveUtils.listEntries(af, kind);
                    if (entryNames == null) {
                        for (ArchiveEntryInfo e : all) {
                            if (!e.isDir) need += e.size;
                        }
                    } else {
                        java.util.Set<String> want =
                                new java.util.HashSet<String>(entryNames);
                        for (ArchiveEntryInfo e : all) {
                            if (e.isDir) continue;
                            if (want.contains(e.name)) {
                                need += e.size;
                                continue;
                            }
                            for (String w : entryNames) {
                                if (w.endsWith("/") && e.name.startsWith(w)) {
                                    need += e.size;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                File probe = destDir;
                while (probe != null && !probe.exists()) probe = probe.getParentFile();
                final long free = probe != null ? probe.getUsableSpace() : 0;
                final long needF = need;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (a.isFinishing()) return;
                        if (needF > 0 && free > 0 && needF > free) {
                            new CatBuilder(a, R.style.DialogTheme)
                                    .setTitle("Sắp hết dung lượng")
                                    .setMessage("Giải nén cần khoảng "
                                            + FileOps.formatSize(needF)
                                            + " nhưng thư mục đích chỉ còn trống "
                                            + FileOps.formatSize(free)
                                            + ". Vẫn tiếp tục?")
                                    .setPositiveButton("Vẫn tiếp tục",
                                            new DialogInterface.OnClickListener() {
                                                @Override public void onClick(
                                                        DialogInterface d, int w) {
                                                    proceed.run();
                                                }
                                            })
                                    .setNegativeButton("Hủy", null)
                                    .show();
                        } else {
                            proceed.run();
                        }
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Shared extract dialog + background op runner
    // ------------------------------------------------------------------

    private interface ExtractConfig {
        void run(File destDir, boolean overwrite, String password,
                 FileOps.OpCallback cb, CancelToken token);
    }

    private interface Op {
        void start(FileOps.OpCallback cb, CancelToken token);
    }

    /**
     * Starter cho các op file đơn giản (xóa / sao chép / di chuyển) muốn dùng
     * chung dialog tiến trình đẹp (Rem + LED RGB + Ẩn/Hủy + notification).
     */
    public interface FileOpStarter {
        void start(FileOps.OpCallback cb);
    }

    /**
     * Hiện dialog tiến trình Rem cho op file đơn giản (xóa, sao chép, di chuyển).
     * Giống hệt dialog nén/giải nén: Rem ngồi trên mép, thanh LED RGB animate,
     * tên file hiện tại, X/Y, %, nút HUỶ (CancelToken) + ẨN (notification).
     */
    public static CancelToken showFileOpProgress(final Activity a, String progressTitle,
                                                 final FileOpStarter starter,
                                                 final String successMsg,
                                                 final Runnable onDone) {
        return runArchiveOp(a, progressTitle, new Op() {
            @Override
            public void start(FileOps.OpCallback cb, CancelToken token) {
                starter.start(cb);
            }
        }, successMsg, onDone);
    }

    /**
     * Chạy op archive với dialog tiến trình, op nhận được CancelToken
     * (để hủy process native như 7zz rn/d).
     */
    public interface OpWithToken {
        void start(FileOps.OpCallback cb, CancelToken token);
    }

    public static CancelToken runOpWithProgress(final Activity a, String progressTitle,
                                               final OpWithToken op, final String successMsg,
                                               final Runnable onDone) {
        return runArchiveOp(a, progressTitle, new Op() {
            @Override
            public void start(FileOps.OpCallback cb, CancelToken token) {
                op.start(cb, token);
            }
        }, successMsg, onDone);
    }

    interface PasswordListener {
        /** pw == null khi user bấm Hủy. */
        void onPassword(String pw);
    }

    /** Dialog nhập mật khẩu giải nén (kèm nút mắt). */
    static void askPassword(final Activity a, final PasswordListener listener) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        layout.setPadding(p, dp(a, 8), p, dp(a, 4));

        TextView label = new TextView(a);
        label.setText("File được mã hóa. Nhập mật khẩu để giải nén:");
        layout.addView(label);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText pwInput = new EditText(a);
        pwInput.setHint("Mật khẩu");
        pwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwInput.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(pwInput, lp);
        final Button eye = new Button(a);
        eye.setText("\uD83D\uDC41");
        eye.setBackground(null);
        eye.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int t = pwInput.getInputType();
                boolean showing = (t & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0;
                pwInput.setInputType(showing
                        ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
                pwInput.setSelection(pwInput.getText().length());
                eye.setText(showing ? "\uD83D\uDC41" : "\uD83D\uDE45");
            }
        });
        row.addView(eye);
        layout.addView(row);

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("File mã hóa")
                .setView(layout)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        listener.onPassword(pwInput.getText().toString());
                    }
                })
                .setNegativeButton("Hủy", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        listener.onPassword(null);
                    }
                })
                .show();
    }

    private static void startExtract(final Activity a, final File destDir, final boolean overwrite,
                                     final String password, final ExtractConfig config,
                                     final String successMsg, final Runnable onDone) {
        runArchiveOp(a, "Đang giải nén...", new Op() {
            @Override
            public void start(FileOps.OpCallback cb, CancelToken token) {
                config.run(destDir, overwrite, password, cb, token);
            }
        }, successMsg + ": " + destDir.getName(), onDone);
    }

    private static void showExtractCommon(final Activity a, final File archiveFile, final int kind,
                                          String titleSuffix,
                                          final ExtractConfig config,
                                          final File defaultDestDir,
                                          final String successMsg,
                                          final Runnable onDone) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        layout.setPadding(p, dp(a, 8), p, dp(a, 4));

        final EditText pathInput = new EditText(a);
        pathInput.setHint("Đường dẫn đích");
        pathInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pathInput.setSingleLine(true);
        pathInput.setText(defaultDestDir != null ? defaultDestDir.getAbsolutePath() : "");
        layout.addView(pathInput);

        final CheckBox overwriteBox = new CheckBox(a);
        overwriteBox.setText("Ghi đè file trùng");
        overwriteBox.setChecked(false);
        layout.addView(overwriteBox);

        new CatBuilder(a, R.style.DialogTheme)
                .setTitle("Giải nén — " + titleSuffix)
                .setView(layout)
                .setPositiveButton("Giải nén", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String path = pathInput.getText().toString().trim();
                        if (path.isEmpty()) {
                            Toast.makeText(a, "Đường dẫn đích không được để trống",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        final File destDir = new File(path);
                        final boolean overwrite = overwriteBox.isChecked();

                        if (ArchiveUtils.needsPassword(archiveFile, kind)) {
                            askPassword(a, new PasswordListener() {
                                @Override public void onPassword(String pw) {
                                    if (pw == null) return; // user hủy
                                    startExtract(a, destDir, overwrite, pw, config,
                                            successMsg, onDone);
                                }
                            });
                        } else {
                            startExtract(a, destDir, overwrite, null, config, successMsg, onDone);
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Run an archive op with a custom Rem-themed progress dialog
     * (R.layout.dialog_progress) + ProgressNotification fallback.
     *
     * The CancelToken is returned so the caller / UI layer can cancel the op
     * from anywhere (e.g. a notification action). Inside, the dialog's
     * "Hủy" button calls token.cancel() directly.
     *
     * "Ẩn" dismisses the dialog and routes progress into a notification
     * that is completed/cancelled in onDone.
     *
     * Utils call back on a background thread -> post everything to UI.
     */
    private static CancelToken runArchiveOp(final Activity a, String progressTitle,
                                            final Op op, final String successMsg,
                                            final Runnable onDone) {
        final Dialog dlg = new Dialog(a, R.style.ProgressDialogTheme);
        dlg.setContentView(R.layout.dialog_progress);
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);

        final TextView tvTitle = (TextView) dlg.findViewById(R.id.tvTitle);
        final ProgressBar bar = (ProgressBar) dlg.findViewById(R.id.progressBar);
        final TextView tvPercent = (TextView) dlg.findViewById(R.id.tvPercent);
        final TextView tvFile = (TextView) dlg.findViewById(R.id.tvFile);
        final Button btnHide = (Button) dlg.findViewById(R.id.btnHide);
        final Button btnCancel = (Button) dlg.findViewById(R.id.btnCancel);
        final CheckBox cbKeepScreen = (CheckBox) dlg.findViewById(R.id.cbKeepScreen);

        tvTitle.setText(progressTitle);
        final AnimatedRainbowDrawable rainbowDrawable = applyAnimatedRainbow(bar, a);
        // Chữ % đổi màu LED RGB liên tục, đồng bộ cùng ValueAnimator với thanh progress
        rainbowDrawable.setOnHueChangeListener(new AnimatedRainbowDrawable.OnHueChangeListener() {
            private final float[] hsv = new float[]{0f, 1f, 1f};
            @Override
            public void onHueChange(float hue) {
                hsv[0] = hue;
                tvPercent.setTextColor(Color.HSVToColor(hsv));
            }
        });

        // Checkbox "Giữ cho màn hình bật" -> FLAG_KEEP_SCREEN_ON
        if (cbKeepScreen != null) {
            cbKeepScreen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Window w = dlg.getWindow();
                    if (w == null) return;
                    if (isChecked) {
                        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
        }

        // Fresh token per op run — returned to the caller so the UI layer
        // can cancel this op from anywhere (dialog button, notification...).
        final CancelToken token = new CancelToken();
        final boolean[] dialogVisible = new boolean[]{true};
        final boolean[] cancelClicked = new boolean[]{false};
        final ProgressNotification[] notif = new ProgressNotification[]{null};

        // --- "Hủy": stop the op, close dialog, toast "Đã hủy" ---
        btnCancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                token.cancel();
                cancelClicked[0] = true;
                rainbowDrawable.stop();
                safeDismiss(dlg);
                dialogVisible[0] = false;
                Toast.makeText(a, "Đã hủy", Toast.LENGTH_SHORT).show();
            }
        });

        // --- "Ẩn": chỉ ẩn dialog, notification đã hiện từ lúc op bắt đầu ---
        btnHide.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (!dialogVisible[0]) return;
                dialogVisible[0] = false;
                rainbowDrawable.stop();
                safeDismiss(dlg);
            }
        });

        // Cho phép Rem vượt ra ngoài viền window mà không bị cắt (như ZArchiver)
        // Phải set TRƯỚC khi show
        Window winPre = dlg.getWindow();
        if (winPre != null) {
            winPre.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }

        dlg.show();

        // Notification hiện NGAY khi op bắt đầu (kiểu ZArchiver) — không chờ bấm "Ẩn".
        // Kazoi bật full thông báo nhưng không thấy vì trước đây chỉ tạo khi bấm Ẩn.
        if (notif[0] == null) {
            try {
                notif[0] = ProgressNotification.show(a, progressTitle);
            } catch (Exception ignored) {
                // không có quyền/không tạo được notification -> op vẫn chạy với dialog
            }
        }

        // Thu bề ngang popup lại ~88% màn hình (Kazoi: đang dài quá)
        Window win = dlg.getWindow();
        if (win != null) {
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            int w88 = (int) (dm.widthPixels * 0.88);
            win.setLayout(w88, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        op.start(new FileOps.OpCallback() {
            @Override
            public void onProgress(final int done, final int total, final String currentName) {
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        // Luôn cập nhật notification (nếu tạo được)
                        if (notif[0] != null) {
                            notif[0].update(done, total, currentName);
                        }
                        // Cập nhật dialog nếu đang hiện
                        if (!dialogVisible[0] || !dlg.isShowing()) return;
                        if (total > 0) {
                            int safeDone = Math.min(done, total);
                            bar.setMax(total);
                            bar.setProgress(safeDone);
                            int pct = (int) ((safeDone * 100L) / total);
                            tvPercent.setText(pct + "%");
                        }
                        tvFile.setText(currentName != null ? currentName : "");
                    }
                });
            }

            @Override
            public void onDone(final boolean ok, final String error) {
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean cancelled = !ok && error != null
                                && (error.equals("Đã hủy") || error.contains("hủy"));
                        // Kết thúc notification (nếu có)
                        if (notif[0] != null) {
                            if (ok) {
                                notif[0].complete(successMsg);
                            } else {
                                notif[0].cancel();
                            }
                        }
                        dialogVisible[0] = false;
                        rainbowDrawable.stop();
                        safeDismiss(dlg);
                        if (a.isFinishing()) return;
                        if (ok) {
                            Toast.makeText(a, successMsg, Toast.LENGTH_SHORT).show();
                        } else if (cancelled) {
                            // Tránh toast trùng nếu user vừa bấm "Hủy" (đã toast ở trên)
                            if (!cancelClicked[0]) {
                                Toast.makeText(a, "Đã hủy", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(a, "Lỗi: " + (error != null ? error : "không rõ"),
                                    Toast.LENGTH_LONG).show();
                        }
                        if (onDone != null) onDone.run();
                    }
                });
            }
        }, token);

        return token;
    }

    /** Đóng dialog an toàn (activity có thể đã chết). */
    private static void safeDismiss(Dialog dlg) {
        try {
            if (dlg != null && dlg.isShowing()) dlg.dismiss();
        } catch (Exception ignored) {
            // activity may be gone
        }
    }

    /**
     * Áp thanh progress cầu vồng ANIMATE liên tục như đèn LED RGB.
     * Dựng hoàn toàn bằng code (không dùng XML) vì một số OEM skin
     * (vd iQOO OriginOS) parse gradient nhiều <item> trong XML không đúng.
     *
     * Trả về drawable để caller gọi stop() khi dialog đóng.
     */
    private static AnimatedRainbowDrawable applyAnimatedRainbow(ProgressBar bar, Activity a) {
        float density = a.getResources().getDisplayMetrics().density;
        AnimatedRainbowDrawable rainbow = new AnimatedRainbowDrawable(5 * density);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFF3A3A3A);
        track.setCornerRadius(5 * density);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
                track,
                new ClipDrawable(rainbow, Gravity.START, ClipDrawable.HORIZONTAL)
        });
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        bar.setProgressDrawable(ld);
        rainbow.start();
        return rainbow;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Default archive name: first item name (+ ext); strip extension for a single file. */
    private static String defaultArchiveName(List<File> sources, int fmt) {
        String name = sources.get(0).getName();
        if (sources.size() == 1 && sources.get(0).isFile()) {
            name = stripKnownExtension(name);
        }
        return name + FMT_EXTS[fmt];
    }

    /** Remove a trailing known archive extension, if present. */
    private static String stripKnownExtension(String name) {
        String lower = name.toLowerCase();
        String[] exts = {".tar.gz", ".tgz", ".zip", ".7z", ".rem", ".tar", ".gz", ".bz2", ".xz", ".rar"};
        for (String e : exts) {
            if (lower.endsWith(e) && name.length() > e.length()) {
                return name.substring(0, name.length() - e.length());
            }
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    private static String replaceExtension(String name, String newExt) {
        return stripKnownExtension(name) + newExt;
    }

    /** Select the base name (without extension) in the EditText. */
    private static void selectBaseName(EditText input, int fmt) {
        String txt = input.getText().toString();
        String ext = FMT_EXTS[fmt];
        if (txt.toLowerCase().endsWith(ext)) {
            input.setSelection(0, txt.length() - ext.length());
        }
    }

    private static int dp(Activity a, int dp) {
        float density = a.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
