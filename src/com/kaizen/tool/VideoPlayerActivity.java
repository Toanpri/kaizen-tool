package com.kaizen.tool;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.Locale;

/**
 * Trình phát video trong app: VideoView + điều khiển tùy chỉnh
 * (play/pause, seekbar, fullscreen), nền đen.
 */
public class VideoPlayerActivity extends Activity {

    private VideoView video;
    private View topbar;
    private View controls;
    private ImageButton btnPlay;
    private SeekBar seek;
    private TextView cur;
    private TextView total;
    private boolean landscape;

    private final Handler handler = new Handler();
    private boolean tracking;
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (video.isPlaying() && !tracking) {
                int pos = video.getCurrentPosition();
                seek.setProgress(pos);
                cur.setText(fmt(pos));
            }
            handler.postDelayed(this, 500);
        }
    };
    private final Runnable hideUi = new Runnable() {
        @Override public void run() { setUiVisible(false); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.apply(this);
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_video_player);

        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        File f = new File(path);

        video = (VideoView) findViewById(R.id.vv_video);
        topbar = findViewById(R.id.vv_topbar);
        controls = findViewById(R.id.vv_controls);
        btnPlay = (ImageButton) findViewById(R.id.vv_play);
        seek = (SeekBar) findViewById(R.id.vv_seek);
        cur = (TextView) findViewById(R.id.vv_cur);
        total = (TextView) findViewById(R.id.vv_total);
        ((TextView) findViewById(R.id.vv_title)).setText(f.getName());

        findViewById(R.id.vv_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        findViewById(R.id.vv_full).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFullscreen(); }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) cur.setText(fmt(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { tracking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                tracking = false;
                video.seekTo(s.getProgress());
            }
        });

        video.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    setUiVisible(topbar.getVisibility() != View.VISIBLE);
                }
                return true;
            }
        });

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                int dur = video.getDuration();
                seek.setMax(dur);
                total.setText(fmt(dur));
                video.start();
                btnPlay.setImageResource(R.drawable.ic_pause);
                handler.post(updater);
                scheduleHide();
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                btnPlay.setImageResource(R.drawable.ic_play);
                setUiVisible(true);
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(VideoPlayerActivity.this,
                        "Không phát được video này", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            }
        });

        try {
            video.setVideoURI(Uri.fromFile(f));
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được video", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void togglePlay() {
        if (video.isPlaying()) {
            video.pause();
            btnPlay.setImageResource(R.drawable.ic_play);
        } else {
            video.start();
            btnPlay.setImageResource(R.drawable.ic_pause);
            scheduleHide();
        }
    }

    private void toggleFullscreen() {
        landscape = !landscape;
        setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void setUiVisible(boolean visible) {
        topbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(hideUi);
        if (visible) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideUi);
        handler.postDelayed(hideUi, 3000);
    }

    private static String fmt(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s %= 60;
        int h = m / 60;
        m %= 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) video.pause();
        handler.removeCallbacks(updater);
        handler.removeCallbacks(hideUi);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updater);
        handler.removeCallbacks(hideUi);
        if (video != null) video.stopPlayback();
    }
}
