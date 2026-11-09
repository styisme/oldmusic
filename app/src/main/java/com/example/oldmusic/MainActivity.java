package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.uikit.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements PlaybackService.Listener {

    private static final int REQ_PERM = 1001;

    private PlaybackService service;
    private boolean bound = false;

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
        // 核心：强制配置主题色为官方演示图的“赛博黄”
        UikitConfig.setCustomPrimaryColor(this, 0xFFFBC02D);
        UikitConfig.setCustomAccentColor(this, 0xFFFFD54F);
        
        UikitTheme.applyTheme(this);
        UikitUi.applyEntryAnimation(this);

        buildModernUi();
        startAndBindService();
        handler.post(progressTask);
    }

    private void buildModernUi() {
        ScrollView scroll = new ScrollView(this);
        // 官方 Demo 的背景色：深空灰
        scroll.setBackgroundColor(0xFF121212); 
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        // 增加充足的边距，避免拥挤
        root.setPadding(dp(24), dp(48), dp(24), dp(32)); 
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        // 1. 歌曲信息
        titleView = new TextView(this);
        titleView.setText("未播放");
        titleView.setTextSize(24);
        titleView.getPaint().setFakeBoldText(true);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setGravity(Gravity.CENTER);
        
        artistView = new TextView(this);
        artistView.setText("未知歌手");
        artistView.setTextSize(14);
        artistView.setTextColor(0xFFAAAAAA);
        artistView.setGravity(Gravity.CENTER);
        artistView.setPadding(0, dp(8), 0, dp(24));

        root.addView(titleView);
        root.addView(artistView);

        // 2. 歌词视图（自带 3D 滚轮效果）
        lyricView = new LyricView(this);
        LinearLayout.LayoutParams lyricLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        lyricLp.bottomMargin = dp(24);
        root.addView(lyricView, lyricLp);

        // 3. 进度条（严格限制高度，否则自绘会变形）
        progressBar = new UikitProgressBar(this);
        progressBar.setProgress(0f);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        pbLp.bottomMargin = dp(32);
        root.addView(progressBar, pbLp);

        // 4. 控制按钮区（利用权重均分，强制撑开药丸形状）
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        UikitButton prevBtn = new UikitButton(this);
        prevBtn.setText("⏮");
        prevBtn.setTextSize(20);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, dp(64), 1f);
        prevLp.rightMargin = dp(12);
        prevBtn.setLayoutParams(prevLp);
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) service.prev();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        playBtn = new UikitButton(this);
        playBtn.setText("▶");
        playBtn.setTextSize(24);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(0, dp(64), 1.5f); // 播放键稍大
        playLp.leftMargin = dp(12);
        playLp.rightMargin = dp(12);
        playBtn.setLayoutParams(playLp);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) service.toggle();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        UikitButton nextBtn = new UikitButton(this);
        nextBtn.setText("⏭");
        nextBtn.setTextSize(20);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(64), 1f);
        nextLp.leftMargin = dp(12);
        nextBtn.setLayoutParams(nextLp);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) service.next();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        btnRow.addView(prevBtn);
        btnRow.addView(playBtn);
        btnRow.addView(nextBtn);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(24);
        root.addView(btnRow, rowLp);

        // 5. 播放模式（长条胶囊）
        modeBtn = new UikitButton(this);
        modeBtn.setText("顺序播放");
        modeBtn.setTextSize(16);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        modeLp.bottomMargin = dp(32);
        root.addView(modeBtn, modeLp);
        modeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.setMode((service.getMode() + 1) % 3);
                    updateModeButton();
                }
            }
        });

        // 6. 播放列表标题
        TextView listTitle = new TextView(this);
        listTitle.setText("当前播放列表");
        listTitle.setTextColor(0xFFFBC02D); // 金黄
        listTitle.setTextSize(14);
        listTitle.getPaint().setFakeBoldText(true);
        listTitle.setPadding(0, dp(16), 0, dp(16));
        root.addView(listTitle);

        // 7. 播放列表容器
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
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
                    progressBar.setProgress((float) pos / dur);
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
            
            String title = song.title;
            if (title == null || title.equals("<unknown>") || title.trim().isEmpty()) {
                title = "未知歌曲";
            }
            String artist = song.artist;
            if (artist == null || artist.equals("<unknown>") || artist.trim().isEmpty()) {
                artist = "未知歌手";
            }

            // 使用官方的列表项控件，自带深灰色圆角卡片背景
            UikitListItem item = new UikitListItem(this, title, artist);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12); // 卡片间距
            item.setLayoutParams(lp);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
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
        
        String title = song.title;
        if (title == null || title.equals("<unknown>") || title.trim().isEmpty()) title = "未知歌曲";
        String artist = song.artist;
        if (artist == null || artist.equals("<unknown>") || artist.trim().isEmpty()) artist = "未知歌手";

        titleView.setText(title);
        artistView.setText(artist);
        
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
