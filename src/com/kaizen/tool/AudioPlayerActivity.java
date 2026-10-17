package com.kaizen.tool;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Trình nghe nhạc trong app: MediaPlayer + play/pause/next/prev/seekbar.
 * (Chạy trong activity; thoát activity thì dừng.)
 */
public class AudioPlayerActivity extends Activity {

    private MediaPlayer player;
    private ImageButton btnPlay;
    private SeekBar seek;
    private TextView title;
    private TextView counter;
    private TextView cur;
    private TextView total;

    private List<File> tracks;
    private int index;
    private boolean tracking;
    private boolean prepared;

    private final Handler handler = new Handler();
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (player != null && prepared && player.isPlaying() && !tracking) {
                int pos = player.getCurrentPosition();
                seek.setProgress(pos);
                cur.setText(fmt(pos));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.apply(this);
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);

        String path = getIntent().getStringExtra("path");
        String dir = getIntent().getStringExtra("dir");
        if (path == null) { finish(); return; }

        tracks = MediaHelper.listMedia(
                dir != null ? new File(dir) : new File(path).getParentFile(),
                MediaHelper.TYPE_AUDIO);
        index = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getAbsolutePath().equals(path)) { index = i; break; }
        }
        if (tracks.isEmpty()) tracks.add(new File(path));

        btnPlay = (ImageButton) findViewById(R.id.ap_play);
        seek = (SeekBar) findViewById(R.id.ap_seek);
        title = (TextView) findViewById(R.id.ap_title);
        counter = (TextView) findViewById(R.id.ap_counter);
        cur = (TextView) findViewById(R.id.ap_cur);
        total = (TextView) findViewById(R.id.ap_total);

        findViewById(R.id.ap_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        findViewById(R.id.ap_next).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playAt((index + 1) % tracks.size()); }
        });
        findViewById(R.id.ap_prev).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                playAt((index - 1 + tracks.size()) % tracks.size());
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) cur.setText(fmt(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { tracking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                tracking = false;
                if (player != null && prepared) player.seekTo(s.getProgress());
            }
        });

        title.setSelected(true); // marquee
        playAt(index);
    }

    private void playAt(int i) {
        index = i;
        releasePlayer();
        File f = tracks.get(index);
        title.setText(f.getName());
        counter.setText((index + 1) + " / " + tracks.size());
        cur.setText("00:00");
        total.setText("00:00");
        seek.setProgress(0);

        player = new MediaPlayer();
        try {
            player.setDataSource(f.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được file nhạc",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                prepared = true;
                int dur = mp.getDuration();
                seek.setMax(dur);
                total.setText(fmt(dur));
                mp.start();
                btnPlay.setImageResource(R.drawable.ic_pause);
                handler.post(updater);
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                playAt((index + 1) % tracks.size()); // tự chuyển bài
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(AudioPlayerActivity.this,
                        "Không phát được file này", Toast.LENGTH_SHORT).show();
                playAt((index + 1) % tracks.size());
                return true;
            }
        });
        try {
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "Không phát được", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlay() {
        if (player == null || !prepared) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlay.setImageResource(R.drawable.ic_play);
        } else {
            player.start();
            btnPlay.setImageResource(R.drawable.ic_pause);
        }
    }

    private void releasePlayer() {
        handler.removeCallbacks(updater);
        prepared = false;
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (Exception ignored) { }
            player = null;
        }
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
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}
