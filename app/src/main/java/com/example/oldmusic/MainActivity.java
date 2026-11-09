package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.uikit.*; // 正确包名

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements PlaybackService.Listener {

    private static final int REQ_PERM = 1001;

    private PlaybackService service;
    private boolean bound = false;

    // UI 组件
    private TextView titleView;
    private TextView artistView;
    private LyricView lyricView;
    private UikitProgressBar progressBar;
    private UikitButton playBtn;
    private UikitButton modeBtn;
    private LinearLayout listContainer;

    private final Handler handler = new Handler();
    private List<LrcParser.Line> lyrics = new ArrayList<LrcParser.Line>();
    private List<Song> songs = new ArrayList<Song>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UikitUi.wrapScale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UikitTheme.applyTheme(this);
        UikitUi.applyEntryAnimation(this);

        buildUi();
        startAndBindService();
        handler.post(progressTask);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        boolean isDark = UikitTheme.isDarkMode(this);
        root.setBackgroundColor(isDark ? 0xFF000000 : 0xFFF4F6F8);

        // 1. 歌曲信息
        titleView = new TextView(this);
        titleView.setText("未播放");
        titleView.setTextSize(22);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(UikitColor.getAccentColor(this, isDark));
        titleView.setGravity(Gravity.CENTER);
        
        artistView = new TextView(this);
        artistView.setText("");
        artistView.setTextSize(14);
        artistView.setTextColor(isDark ? 0xFFAAAAAA : 0xFF666666);
        artistView.setGravity(Gravity.CENTER);
        artistView.setPadding(0, 8, 0, 24);

        root.addView(titleView);
        root.addView(artistView);

        // 2. 歌词视图 (利用自定义 View)
        lyricView = new LyricView(this);
        LinearLayout.LayoutParams lyricLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UikitScreenUtils.dp2px(this, 150));
        lyricLp.bottomMargin = UikitScreenUtils.dp2px(this, 16);
        root.addView(lyricView, lyricLp);

        // 3. 进度条 (UikitProgressBar 只能展示，不能拖动)
        progressBar = new UikitProgressBar(this);
        progressBar.setProgress(0f);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UikitScreenUtils.dp2px(this, 8));
        pbLp.bottomMargin = UikitScreenUtils.dp2px(this, 24);
        root.addView(progressBar, pbLp);

        // 4. 播放控制按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        UikitButton prevBtn = new UikitButton(this);
        prevBtn.setText("⏮");
        prevBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bound && service != null) service.prev();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });
        btnRow.addView(prevBtn, new LinearLayout.LayoutParams(
                UikitScreenUtils.dp2px(this, 60), UikitScreenUtils.dp2px(this, 60)));

        playBtn = new UikitButton(this);
        playBtn.setText("▶");
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bound && service != null) service.toggle();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                UikitScreenUtils.dp2px(this, 80), UikitScreenUtils.dp2px(this, 80));
        playLp.leftMargin = UikitScreenUtils.dp2px(this, 16);
        playLp.rightMargin = UikitScreenUtils.dp2px(this, 16);
        btnRow.addView(playBtn, playLp);

        UikitButton nextBtn = new UikitButton(this);
        nextBtn.setText("⏭");
        nextBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bound && service != null) service.next();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });
        btnRow.addView(nextBtn, new LinearLayout.LayoutParams(
                UikitScreenUtils.dp2px(this, 60), UikitScreenUtils.dp2px(this, 60)));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = UikitScreenUtils.dp2px(this, 24);
        root.addView(btnRow, rowLp);

        // 5. 播放模式
        modeBtn = new UikitButton(this);
        modeBtn.setText("顺序播放");
        modeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bound && service != null) {
                    service.setMode((service.getMode() + 1) % 3);
                    updateModeButton();
                }
            }
        });
        root.addView(modeBtn);

        // 6. 播放列表容器
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = UikitScreenUtils.dp2px(this, 16);
        root.addView(listContainer, listLp);
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).getService();
            service.setListener(MainActivity.this);
            bound = true;

            songs = service.getPlaylist();
            if (songs.isEmpty()) {
                loadSongs();
                service.setPlaylist(new ArrayList<Song>(songs));
                if (!songs.isEmpty()) service.playIndex(0);
            } else {
                onSongChanged(service.getCurrentSong());
                rebuildPlaylist();
            }

            updateModeButton();
            updatePlayButton(service.isPlaying());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (bound && service != null) {
                int pos = service.getPosition();
                int dur = service.getDuration();
                if (dur > 0) {
                    float percent = (float) pos / dur;
                    progressBar.setProgress(percent);
                } else {
                    progressBar.setProgress(0f);
                }
                updateLyric(pos);
            }
            handler.postDelayed(this, 300L);
        }
    };

    private void loadSongs() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }
        songs.clear();
        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };
            cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    songs.add(new Song(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4)));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "扫描本地音乐失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            loadSongs();
            if (bound && service != null) {
                service.setPlaylist(new ArrayList<Song>(songs));
                if (!songs.isEmpty()) service.playIndex(0);
            }
        }
    }

    private void updateLyric(int position) {
        if (lyrics == null || lyrics.isEmpty()) return;
        int idx = LrcParser.findIndex(lyrics, position);
        if (idx >= 0) lyricView.setCurrentIndex(idx);
    }

    private void updatePlayButton(boolean playing) {
        if (playBtn != null) playBtn.setText(playing ? "⏸" : "▶");
    }

    private void updateModeButton() {
        if (bound && service != null && modeBtn != null) {
            int m = service.getMode();
            if (m == PlaybackService.MODE_REPEAT_ONE) modeBtn.setText("单曲循环");
            else if (m == PlaybackService.MODE_SHUFFLE) modeBtn.setText("随机播放");
            else modeBtn.setText("顺序播放");
        }
    }

    private void rebuildPlaylist() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        for (int i = 0; i < songs.size(); i++) {
            final int index = i;
            Song song = songs.get(i);
            
            UikitListItem item = new UikitListItem(this, song.title, song.artist);
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (bound && service != null) service.playIndex(index);
                }
            });
            listContainer.addView(item);
        }
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        updatePlayButton(playing);
    }

    @Override
    public void onSongChanged(Song song) {
        if (song == null) return;
        titleView.setText(song.title);
        artistView.setText(song.artist);
        
        lyrics = new ArrayList<LrcParser.Line>();
        if (song.path != null && song.path.length() > 0) {
            String lrcPath = song.path;
            int dot = lrcPath.lastIndexOf('.');
            if (dot > 0) lrcPath = lrcPath.substring(0, dot) + ".lrc";
            lyrics = LrcParser.parse(lrcPath);
        }
        lyricView.setLines(lyrics);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressTask);
        if (bound) {
            try { service.setListener(null); } catch (Exception ignored) {}
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }
}
