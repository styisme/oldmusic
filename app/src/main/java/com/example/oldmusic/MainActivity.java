package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;


import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.uikit.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements PlaybackService.Listener {

    private static final int REQ_PERM = 1001;
    private static final int REQ_SLEEP = 1002;

    private PlaybackService service;
    private boolean bound = false;

    private TextView titleView;
    private TextView artistView;
    private LyricView lyricView;
    private UikitSlider slider;
    private UikitButton playBtn;
    private UikitButton modeBtn;
    private UikitSkeleton skeleton;

    private final Handler handler = new Handler();
    private List<LrcParser.Line> lyrics = new ArrayList<LrcParser.Line>();
    private List<Song> songs = new ArrayList<Song>();

    // 长按快进/快退控制
    private boolean isLongPressing = false;
    private Runnable longPressRunnable;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UikitUi.wrapScale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyGlobalTheme(this);
        UikitTheme.applyTheme(this);
        UikitUi.applyEntryAnimation(this);

        buildModernUi();
        startAndBindService();
        handler.post(progressTask);
    }

    private void buildModernUi() {
        boolean isDark = UikitTheme.isDarkMode(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(isDark ? 0xFF121212 : 0xFFF4F6F8);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(48)); 
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        // 1. 骨架屏 (初始隐藏)
        skeleton = new UikitSkeleton(this);
        skeleton.setVisibility(View.GONE);
        LinearLayout.LayoutParams skLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        skLp.bottomMargin = dp(16);
        root.addView(skeleton, skLp);

        // 2. 歌曲信息
        titleView = new TextView(this);
        titleView.setText("未播放");
        titleView.setTextSize(24);
        titleView.getPaint().setFakeBoldText(true);
        titleView.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1C1B1F);
        titleView.setGravity(Gravity.CENTER);
        
        artistView = new TextView(this);
        artistView.setText("未知歌手");
        artistView.setTextSize(14);
        artistView.setTextColor(isDark ? 0xFFAAAAAA : 0xFF666666);
        artistView.setGravity(Gravity.CENTER);
        artistView.setPadding(0, dp(8), 0, dp(24));
        // 点击封面/文字可查看大图 (测试全屏查看器)
        titleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UikitImageViewerActivity.show(MainActivity.this, "demo_image.png"); // 需要 assets 里有图
            }
        });

        root.addView(titleView);
        root.addView(artistView);

        // 3. 歌词视图
        lyricView = new LyricView(this);
        LinearLayout.LayoutParams lyricLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        lyricLp.bottomMargin = dp(24);
        root.addView(lyricView, lyricLp);

        // 4. 进度条 (UikitSlider)
        slider = new UikitSlider(this);
        slider.setMaxProgress(100f);
        slider.setProgress(0f);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        pbLp.bottomMargin = dp(16);
        root.addView(slider, pbLp);

        slider.setOnProgressChangedListener(new UikitSlider.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(float progress) {
                if (bound && service != null) {
                    int dur = service.getDuration();
                    if (dur > 0) service.seekTo((int) (progress / 100f * dur));
                }
            }
        });

        // 5. 控制按钮区 (加入长按快进/快退)
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        UikitButton prevBtn = new UikitButton(this);
        prevBtn.setText("");
        prevBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_uikit_prev, 0, 0, 0);
        prevBtn.setTextSize(14);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        prevLp.rightMargin = dp(8);
        prevBtn.setLayoutParams(prevLp);
        
        // 长按快退
        prevBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                isLongPressing = true;
                startSeeking(-10000);
                return true;
            }
        });
        prevBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    stopSeeking();
                } else if (event.getAction() == MotionEvent.ACTION_DOWN && !isLongPressing) {
                    if (bound && service != null) service.prev();
                    WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
                }
                return false;
            }
        });

        playBtn = new UikitButton(this);
        playBtn.setText("");
        playBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_uikit_play, 0, 0, 0);
        playBtn.setTextSize(18);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(0, dp(56), 1.2f);
        playLp.leftMargin = dp(8);
        playLp.rightMargin = dp(8);
        playBtn.setLayoutParams(playLp);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (UikitClickUtils.isFastDoubleClick()) return; // 防抖
                if (bound && service != null) service.toggle();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        UikitButton nextBtn = new UikitButton(this);
        nextBtn.setText("");
        nextBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_uikit_next, 0, 0, 0);
        nextBtn.setTextSize(14);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        nextLp.leftMargin = dp(8);
        nextBtn.setLayoutParams(nextLp);
        
        // 长按快进
        nextBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                isLongPressing = true;
                startSeeking(10000);
                return true;
            }
        });
        nextBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    stopSeeking();
                } else if (event.getAction() == MotionEvent.ACTION_DOWN && !isLongPressing) {
                    if (bound && service != null) service.next();
                    WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
                }
                return false;
            }
        });

        btnRow.addView(prevBtn);
        btnRow.addView(playBtn);
        btnRow.addView(nextBtn);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(24);
        root.addView(btnRow, rowLp);

        // 6. 菜单按钮
        LinearLayout menuRow1 = new LinearLayout(this);
        menuRow1.setOrientation(LinearLayout.HORIZONTAL);
        menuRow1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        UikitButton playlistBtn = createCustomBtn(R.drawable.ic_uikit_menu, "播放列表");
        playlistBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
        
        UikitButton sleepBtn = createCustomBtn(R.drawable.ic_uikit_time, "定时");
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        sLp.leftMargin = dp(8);
        sleepBtn.setLayoutParams(sLp);

        menuRow1.addView(playlistBtn);
        menuRow1.addView(sleepBtn);
        LinearLayout.LayoutParams menu1Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        menu1Lp.bottomMargin = dp(12);
        root.addView(menuRow1, menu1Lp);

        playlistBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (UikitClickUtils.isFastDoubleClick()) return;
                showPlaylistDialog();
            }
        });
        sleepBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (UikitClickUtils.isFastDoubleClick()) return;
                UikitPickerActivity.show(MainActivity.this, "睡眠定时", new String[]{"关闭定时", "15 分钟", "30 分钟", "60 分钟"}, REQ_SLEEP);
            }
        });

        // 7. 设置按钮 + 播放模式
        LinearLayout menuRow2 = new LinearLayout(this);
        menuRow2.setOrientation(LinearLayout.HORIZONTAL);
        menuRow2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        UikitButton settingsBtn = createCustomBtn(R.drawable.ic_uikit_settings, "设置");
        settingsBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));

        modeBtn = createCustomBtn(R.drawable.ic_uikit_music, "顺序播放");
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        modeLp.leftMargin = dp(8);
        modeBtn.setLayoutParams(modeLp);

        menuRow2.addView(settingsBtn);
        menuRow2.addView(modeBtn);
        root.addView(menuRow2);

        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        modeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.setMode((service.getMode() + 1) % 3);
                    updateModeButton();
                }
            }
        });
    }

    private void startSeeking(final int deltaMs) {
        if (!bound || service == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isLongPressing) {
                    int pos = service.getPosition() + deltaMs;
                    if (pos < 0) pos = 0;
                    service.seekTo(pos);
                    WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
                    handler.postDelayed(this, 500); // 每 500ms 跳一次
                }
            }
        });
    }

    private void stopSeeking() {
        isLongPressing = false;
    }

    private void showPlaylistDialog() {
        if (songs.isEmpty()) {
            UikitToast.show(this, "暂无本地歌曲");
            return;
        }
        ArrayList<UikitListItemModel> items = new ArrayList<UikitListItemModel>();
        int currentIndex = (bound && service != null) ? service.getCurrentIndex() : -1;
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            String t = song.title;
            if (t == null || t.trim().isEmpty() || t.equals("<unknown>")) t = "未知歌曲";
            String a = song.artist;
            if (a == null || a.trim().isEmpty() || a.equals("<unknown>")) a = "未知歌手";
            if (t.length() > 16) t = t.substring(0, 16) + "...";
            if (a.length() > 16) a = a.substring(0, 16) + "...";
            int accentColor = (i == currentIndex) ? 0xFFFBC02D : 0;
            if (i == currentIndex) t = "▶ " + t;
            items.add(new UikitListItemModel(t, a, accentColor));
        }
        UikitFullListActivity.show(this, "播放列表", items, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            int index = data.getIntExtra(UikitFullListActivity.EXTRA_RESULT_INDEX, -1);
            if (index >= 0 && bound && service != null) service.playIndex(index);
        } else if (requestCode == REQ_SLEEP && resultCode == RESULT_OK && data != null) {
            int index = data.getIntExtra(UikitPickerActivity.EXTRA_RESULT_INDEX, -1);
            if (index >= 0 && bound && service != null) {
                int minutes = 0;
                if (index == 1) minutes = 15;
                else if (index == 2) minutes = 30;
                else if (index == 3) minutes = 60;
                service.setSleepTimer(minutes);
                UikitToast.show(this, minutes > 0 ? ("已设置 " + minutes + " 分钟后暂停") : "已取消定时");
            }
        }
    }


    private UikitButton createCustomBtn(int iconRes, String text) {
        UikitButton btn = new UikitButton(this);
        btn.setPadding(0, 0, 0, 0);
        btn.setGravity(Gravity.CENTER);
        
        if (iconRes == 0) {
            btn.setText(text);
            return btn;
        }

        int size = dp(20);
        android.graphics.drawable.Drawable drawable = getResources().getDrawable(iconRes);
        drawable.setBounds(0, 0, size, size);
        
        // 自动获取按钮的文字颜色来给图标染色
        int textColor = btn.getCurrentTextColor();
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            drawable.setTint(textColor);
        } else {
            drawable.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }

        // 核心：加一个空格作为间距，防止图标和文字粘连
        String targetText = text != null && !text.isEmpty() ? text : "";
        String finalText = targetText.isEmpty() ? " " : ("  " + targetText); 
        
        SpannableString spannableString = new SpannableString(finalText);
        ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
        spannableString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        btn.setText(spannableString);
        return btn;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

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
            }
            updateModeButton();
            updatePlayButton(service.isPlaying());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) { bound = false; service = null; }
    };

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (bound && service != null) {
                int pos = service.getPosition();
                int dur = service.getDuration();
                if (dur > 0) slider.setProgress((float) pos / dur * 100f);
                else slider.setProgress(0f);
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
        skeleton.setVisibility(View.VISIBLE); // 显示骨架屏
        songs.clear();
        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };
            cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null,
                    MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    songs.add(new Song(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4)));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "扫描失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) cursor.close();
        }
        skeleton.setVisibility(View.GONE); // 隐藏骨架屏
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
        if (playBtn != null) {
            int resId = playing ? R.drawable.ic_uikit_pause : R.drawable.ic_uikit_play;
            int size = dp(28);
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(resId);
            drawable.setBounds(0, 0, size, size);
            int textColor = playBtn.getCurrentTextColor();
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                drawable.setTint(textColor);
            } else {
                drawable.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            SpannableString spannableString = new SpannableString(" ");
            ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
            spannableString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            playBtn.setText(spannableString);
        }
    }

    private void updateModeButton() {
        if (bound && service != null && modeBtn != null) {
            int m = service.getMode();
            String text = "顺序播放";
            if (m == PlaybackService.MODE_REPEAT_ONE) text = "单曲循环";
            else if (m == PlaybackService.MODE_SHUFFLE) text = "随机播放";
            
            int size = dp(18);
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.ic_uikit_music);
            drawable.setBounds(0, 0, size, size);
            int textColor = modeBtn.getCurrentTextColor();
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                drawable.setTint(textColor);
            } else {
                drawable.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            String finalText = "  " + text;
            SpannableString spannableString = new SpannableString(finalText);
            ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
            spannableString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            modeBtn.setText(spannableString);
        }
    }

    @Override
    public void onPlayStateChanged(boolean playing) { updatePlayButton(playing); }

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
