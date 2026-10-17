package com.kaizen.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProgressNotification — notification hiển thị tiến trình nén/giải nén
 * khi user bấm "Ẩn" trên dialog tiến trình.
 *
 * Pure android.app — KHÔNG dùng AndroidX.
 */
public class ProgressNotification {

    public static final String CHANNEL_ID = "kaizen_progress";
    private static final String CHANNEL_NAME = "Tiến trình";
    private static final int PINK_ACCENT = 0xFFF06292;

    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    private final Context context;
    private final NotificationManager manager;
    private final int id;
    private final Notification.Builder builder;

    private ProgressNotification(Context context, String title) {
        this.context = context.getApplicationContext();
        this.manager = (NotificationManager) this.context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        this.id = ID_SEQ.incrementAndGet();
        ensureChannel();

        Intent intent = new Intent(this.context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this.context, id, intent, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this.context, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this.context);
        }
        this.builder = b
                .setContentTitle(title)
                .setContentText("Đang chuẩn bị...")
                .setSmallIcon(R.drawable.ic_launcher)
                .setColor(PINK_ACCENT)
                .setContentIntent(pi)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(100, 0, false);
        manager.notify(id, builder.build());
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Hiển thị tiến trình nén và giải nén");
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Hiển thị notification tiến trình mới. Trả về đối tượng notification.
     */
    public static ProgressNotification show(Context context, String title) {
        return new ProgressNotification(context, title);
    }

    /** Trả về id notification (ít khi cần, vì đối tượng giữ sẵn). */
    public int getId() {
        return id;
    }

    /** Cập nhật tiến trình. */
    public void update(int done, int total, String fileName) {
        int pct = 0;
        if (total > 0) {
            pct = (int) ((done * 100L) / total);
            builder.setProgress(100, Math.min(pct, 100), false);
        } else {
            builder.setProgress(0, 0, true);
        }
        // % nằm bên phải (như thông báo thường), tên file ở giữa
        builder.setContentInfo(pct + "%");
        builder.setContentText(fileName != null ? fileName : "");
        try {
            manager.notify(id, builder.build());
        } catch (Exception ignored) {
            // context đã chết — bỏ qua
        }
    }

    /** Báo hoàn thành: hết ongoing, bấm để xóa, tự gỡ sau vài giây. */
    public void complete(String msg) {
        builder.setContentTitle("Hoàn thành")
                .setContentText(msg != null ? msg : "")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true);
        try {
            manager.notify(id, builder.build());
        } catch (Exception ignored) {
            // context đã chết — bỏ qua
        }
        // Tự gỡ sau 4 giây để notification không treo (Kazoi báo:
        // "hoàn tất công việc rồi vẫn thấy có thông báo").
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    new Runnable() {
                        @Override public void run() {
                            cancel();
                        }
                    }, 4000);
        } catch (Exception ignored) {
            // không post được — notification sẽ chờ user vuốt
        }
    }

    /** Hủy (gỡ) notification. */
    public void cancel() {
        try {
            manager.cancel(id);
        } catch (Exception ignored) {
            // context đã chết — bỏ qua
        }
    }
}
