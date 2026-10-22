package com.kaizen.tool;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Trình xem ảnh trong app: pinch-to-zoom, double-tap zoom,
 * vuốt ngang chuyển ảnh, tap hiện/ẩn toolbar.
 */
public class ImageViewerActivity extends Activity {

    private ImageView photo;
    private View toolbar;
    private TextView title;
    private TextView counter;

    private List<File> images;
    private int index;
    private File current;

    // ---- zoom state ----
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    private final PointF startPt = new PointF();
    private final PointF midPt = new PointF();
    private float oldDist;
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mode = NONE;
    private float minScale = 1f;
    private static final float MAX_SCALE = 4f;
    private long lastTapTime;
    private float downX, downY;
    private boolean uiVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.apply(this);
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_image_viewer);

        photo = (ImageView) findViewById(R.id.iv_photo);
        toolbar = findViewById(R.id.iv_toolbar);
        title = (TextView) findViewById(R.id.iv_title);
        counter = (TextView) findViewById(R.id.iv_counter);

        String path = getIntent().getStringExtra("path");
        String dir = getIntent().getStringExtra("dir");
        if (path == null) { finish(); return; }
        current = new File(path);
        images = MediaHelper.listMedia(
                dir != null ? new File(dir) : current.getParentFile(),
                MediaHelper.TYPE_IMAGE);
        index = 0;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getAbsolutePath().equals(current.getAbsolutePath())) {
                index = i; break;
            }
        }
        if (images.isEmpty()) images.add(current);

        findViewById(R.id.iv_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.iv_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareCurrent(); }
        });
        findViewById(R.id.iv_delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { deleteCurrent(); }
        });

        photo.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                return handleTouch(e);
            }
        });

        showCurrent();
    }

    private void showCurrent() {
        current = images.get(index);
        title.setText(current.getName());
        counter.setText((index + 1) + " / " + images.size());
        matrix.reset();
        loadImage(current);
    }

    private void loadImage(File f) {
        photo.setImageDrawable(null);
        // GIF động (API 28+): dùng ImageDecoder để giữ animation
        if (Build.VERSION.SDK_INT >= 28
                && f.getName().toLowerCase().endsWith(".gif")) {
            try {
                android.graphics.ImageDecoder.Source src =
                        android.graphics.ImageDecoder.createSource(f);
                Drawable d = android.graphics.ImageDecoder.decodeDrawable(src);
                photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                photo.setImageDrawable(d);
                if (d instanceof Animatable) ((Animatable) d).start();
                return;
            } catch (Exception ignored) { /* fallback tĩnh */ }
        }
        photo.setScaleType(ImageView.ScaleType.MATRIX);
        Bitmap bmp = decodeSampled(f);
        if (bmp == null) {
            Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
            return;
        }
        photo.setImageBitmap(bmp);
        fitToScreen(bmp.getWidth(), bmp.getHeight());
    }

    private Bitmap decodeSampled(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            int vw = getResources().getDisplayMetrics().widthPixels;
            int vh = getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while ((o.outWidth / (sample * 2)) >= vw
                    && (o.outHeight / (sample * 2)) >= vh) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private void fitToScreen(int bw, int bh) {
        int vw = photo.getWidth() > 0 ? photo.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int vh = photo.getHeight() > 0 ? photo.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        float scale = Math.min((float) vw / bw, (float) vh / bh);
        minScale = scale;
        matrix.setScale(scale, scale);
        matrix.postTranslate((vw - bw * scale) / 2f, (vh - bh * scale) / 2f);
        photo.setImageMatrix(matrix);
    }

    private float currentScale() {
        float[] v = new float[9];
        matrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private boolean handleTouch(MotionEvent e) {
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                startPt.set(e.getX(), e.getY());
                downX = e.getX(); downY = e.getY();
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(e);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(midPt, e);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(e.getX() - startPt.x, e.getY() - startPt.y);
                    photo.setImageMatrix(matrix);
                } else if (mode == ZOOM) {
                    float nd = spacing(e);
                    if (nd > 10f) {
                        matrix.set(savedMatrix);
                        float s = nd / oldDist;
                        float target = currentScale() * s / minScale;
                        if (target > MAX_SCALE) s = MAX_SCALE * minScale / currentScale();
                        if (target < 1f) s = minScale / currentScale();
                        matrix.postScale(s, s, midPt.x, midPt.y);
                        photo.setImageMatrix(matrix);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                long now = System.currentTimeMillis();
                if (mode == DRAG && Math.abs(dx) < 12 && Math.abs(dy) < 12) {
                    // tap: double-tap = zoom, single = ẩn/hiện toolbar
                    if (now - lastTapTime < 300) {
                        toggleZoom(e.getX(), e.getY());
                        lastTapTime = 0;
                    } else {
                        lastTapTime = now;
                        photo.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (lastTapTime != 0) {
                                    lastTapTime = 0;
                                    toggleUi();
                                }
                            }
                        }, 320);
                    }
                } else if (mode == DRAG && currentScale() <= minScale * 1.02f
                        && Math.abs(dx) > 140 && Math.abs(dy) < 100) {
                    // vuốt ngang khi chưa zoom: chuyển ảnh
                    if (dx < 0) next(); else prev();
                } else if (mode == DRAG) {
                    clampPan();
                }
                mode = NONE;
                break;
        }
        return true;
    }

    private void toggleZoom(float x, float y) {
        float cs = currentScale();
        float target = (cs <= minScale * 1.1f) ? minScale * 2.5f : minScale;
        float s = target / cs;
        matrix.postScale(s, s, x, y);
        if (target <= minScale * 1.01f) {
            // về fit: căn giữa lại
            Drawable d = photo.getDrawable();
            if (d != null && d.getIntrinsicWidth() > 0) {
                fitToScreen(d.getIntrinsicWidth(), d.getIntrinsicHeight());
                return;
            }
        }
        photo.setImageMatrix(matrix);
    }

    private void clampPan() {
        // đơn giản: không cho trôi quá xa — reset nhẹ nếu vượt biên nhiều
        photo.setImageMatrix(matrix);
    }

    private void toggleUi() {
        uiVisible = !uiVisible;
        toolbar.setVisibility(uiVisible ? View.VISIBLE : View.GONE);
        counter.setVisibility(uiVisible ? View.VISIBLE : View.GONE);
    }

    private void next() {
        if (index < images.size() - 1) { index++; showCurrent(); }
    }

    private void prev() {
        if (index > 0) { index--; showCurrent(); }
    }

    private float spacing(MotionEvent e) {
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF p, MotionEvent e) {
        p.set((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f);
    }

    private void shareCurrent() {
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/*");
            Uri uri = GenericFileProvider.uriFor(current);
            it.putExtra(Intent.EXTRA_STREAM, uri);
            // Như shareFiles: một số app chỉ nhận URI permission qua ClipData.
            it.setClipData(android.content.ClipData.newRawUri("file", uri));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "Chia sẻ"));
        } catch (Exception e) {
            Toast.makeText(this, "Không chia sẻ được", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteCurrent() {
        Dialogs.showDeleteConfirm(this, 1, new Runnable() {
            @Override public void run() {
                if (current.delete()) {
                    images.remove(index);
                    if (images.isEmpty()) { finish(); return; }
                    if (index >= images.size()) index = images.size() - 1;
                    showCurrent();
                    Toast.makeText(ImageViewerActivity.this,
                            "Đã xóa", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ImageViewerActivity.this,
                            "Xóa thất bại", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
