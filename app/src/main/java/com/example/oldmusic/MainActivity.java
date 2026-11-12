package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private FrameLayout playBtn;
    private FrameLayout modeBtn;
    private UikitSkeleton skeleton;

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
        ThemeHelper.applyGlobalTheme(this);
        UikitTheme.applyTheme(this);
        UikitUi.applyEntryAnimation(this);

        buildModernUi();
        startAndBindService();
        handler.post(progressTask);
        handleExternalIntent(getIntent());
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

        // 1. 骨架屏
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
        
        titleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UikitImageViewerActivity.show(MainActivity.this, "demo_image.png");
            }
        });

        root.addView(titleView);
        root.addView(artistView);

        // 3. 歌词视图
        lyricView = new LyricView(this);
        LinearLayout.LayoutParams lyricLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        lyricLp.bottomMargin = dp(24);
        root.addView(lyricView, lyricLp);

        // 4. 进度条
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

        // 5. 控制按钮区
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        FrameLayout prevBtn = createIconButton(R.drawable.ic_uikit_prev, 64, 64);
        ((LinearLayout.LayoutParams) prevBtn.getLayoutParams()).rightMargin = dp(12);
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) service.prev();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        playBtn = createIconButton(R.drawable.ic_uikit_play, 80, 64);
        ((LinearLayout.LayoutParams) playBtn.getLayoutParams()).leftMargin = dp(4);
        ((LinearLayout.LayoutParams) playBtn.getLayoutParams()).rightMargin = dp(4);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (UikitClickUtils.isFastDoubleClick()) return;
                if (bound && service != null) service.toggle();
                WearHardwareUtils.vibrate(MainActivity.this, WearHardwareUtils.HAPTIC_LIGHT);
            }
        });

        FrameLayout nextBtn = createIconButton(R.drawable.ic_uikit_next, 64, 64);
        ((LinearLayout.LayoutParams) nextBtn.getLayoutParams()).leftMargin = dp(12);
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

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(24);
        root.addView(btnRow, rowLp);

        // 6. 菜单按钮区
        LinearLayout menuRow1 = new LinearLayout(this);
        menuRow1.setOrientation(LinearLayout.HORIZONTAL);
        menuRow1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        FrameLayout playlistBtn = createTextIconButton(R.drawable.ic_uikit_menu, "播放列表");
        playlistBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
        
        FrameLayout sleepBtn = createTextIconButton(R.drawable.ic_uikit_time, "定时");
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

        LinearLayout menuRow2 = new LinearLayout(this);
        menuRow2.setOrientation(LinearLayout.HORIZONTAL);
        menuRow2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout settingsBtn = createTextIconButton(R.drawable.ic_uikit_settings, "设置");
        settingsBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));

        modeBtn = createTextIconButton(R.drawable.ic_uikit_music, "顺序播放");
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

    // 底层拦截动画，顶层只显示
    private FrameLayout createIconButton(int resId, int widthDp, int heightDp) {
        final FrameLayout wrapper = new FrameLayout(this);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(32));
        bg.setColor(UikitColor.getPrimaryColor(this));
        wrapper.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(resId);
        icon.setClickable(false);
        icon.setFocusable(false);
        int textColor = UikitColor.getOnPrimaryColor(this);
        if (Build.VERSION.SDK_INT >= 21) {
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(textColor));
        } else {
            icon.setColorFilter(textColor);
        }
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        iconLp.gravity = Gravity.CENTER;
        wrapper.addView(icon, iconLp);
        wrapper.setTag(icon);

        wrapper.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (Build.VERSION.SDK_INT < 11) return false;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.7f).setDuration(100).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
                        break;
                }
                return false;
            }
        });
        return wrapper;
    }

    private FrameLayout createTextIconButton(int resId, String text) {
        final FrameLayout wrapper = new FrameLayout(this);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(32));
        bg.setColor(UikitColor.getPrimaryColor(this));
        wrapper.setBackground(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER);
        content.setClickable(false);
        content.setFocusable(false);

        int textColor = UikitColor.getOnPrimaryColor(this);

        ImageView icon = new ImageView(this);
        icon.setImageResource(resId);
        icon.setClickable(false);
        icon.setFocusable(false);
        if (Build.VERSION.SDK_INT >= 21) {
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(textColor));
        } else {
            icon.setColorFilter(textColor);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconLp.rightMargin = dp(6);
        content.addView(icon, iconLp);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(textColor);
        tv.setClickable(false);
        tv.setFocusable(false);
        content.addView(tv);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentLp.gravity = Gravity.CENTER;
        wrapper.addView(content, contentLp);
        wrapper.setTag(tv);

        wrapper.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (Build.VERSION.SDK_INT < 11) return false;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(100).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
                        break;
                }
                return false;
            }
        });
        return wrapper;
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
        skeleton.setVisibility(View.VISIBLE);
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
        skeleton.setVisibility(View.GONE);
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
            ImageView icon = (ImageView) playBtn.getTag();
            if (icon != null) icon.setImageResource(playing ? R.drawable.ic_uikit_pause : R.drawable.ic_uikit_play);
        }
    }

    private void updateModeButton() {
        if (bound && service != null && modeBtn != null) {
            int m = service.getMode();
            String text = "顺序播放";
            if (m == PlaybackService.MODE_REPEAT_ONE) text = "单曲循环";
            else if (m == PlaybackService.MODE_SHUFFLE) text = "随机播放";
            TextView tv = (TextView) modeBtn.getTag();
            if (tv != null) tv.setText(text);
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    private void handleExternalIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            final android.net.Uri uri = intent.getData();
            if (uri != null) {
                // 精准解析真实文件名
                String title = getFileNameFromUri(uri);
                if (title == null) title = "外部音频";
                final String finalTitle = title;
                
                if (bound && service != null) {
                    service.playExternalUri(uri, finalTitle);
                    UikitToast.show(MainActivity.this, "正在播放外部音频");
                } else {
                    // 如果 Service 还没连上，用一个简单的轮询替代死板的延迟
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (bound && service != null) {
                                service.playExternalUri(uri, finalTitle);
                                UikitToast.show(MainActivity.this, "正在播放外部音频");
                            } else {
                                handler.postDelayed(this, 200); // 每 200ms 检查一次
                            }
                        }
                    }, 200);
                }
            }
        }
    }

    private String getFileNameFromUri(android.net.Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
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
