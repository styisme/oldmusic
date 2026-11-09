package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.uikit.*;

import java.util.ArrayList;
import java.util.List;

public class PlaylistActivity extends Activity {

    private PlaybackService service;
    private boolean bound = false;
    private LinearLayout listContainer;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).getService();
            bound = true;
            buildList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UikitUi.wrapScale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 核心修复：读取用户保存的深色模式状态
        boolean isDark = UikitConfig.prefs(this).getBoolean("is_dark_mode", true);
        UikitConfig.setThemeMode(this, isDark ? UikitConfig.THEME_DARK : UikitConfig.THEME_LIGHT);
        UikitTheme.applyTheme(this);

        ScrollView scroll = new ScrollView(this);
        boolean isDark = UikitTheme.isDarkMode(this);
        scroll.setBackgroundColor(isDark ? 0xFF121212 : 0xFFF4F6F8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        TextView title = new TextView(this);
        title.setText("播放列表");
        title.setTextSize(22);
        title.setTextColor(0xFFFBC02D);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        Intent intent = new Intent(this, PlaybackService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void buildList() {
        listContainer.removeAllViews();
        if (service == null) return;
        List<Song> songs = service.getPlaylist();
        int currentIndex = service.getCurrentIndex();

        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无歌曲");
            empty.setTextColor(0xFF888888);
            empty.setGravity(Gravity.CENTER);
            listContainer.addView(empty);
            return;
        }

        for (int i = 0; i < songs.size(); i++) {
            final int index = i;
            Song song = songs.get(i);
            String title = song.title;
            if (title == null || title.equals("<unknown>") || title.trim().isEmpty()) title = "未知歌曲";
            String artist = song.artist;
            if (artist == null || artist.equals("<unknown>") || artist.trim().isEmpty()) artist = "未知歌手";

            UikitListItem item = new UikitListItem(this, title, artist);
            if (i == currentIndex) {
                item.setArrow(); // 当前播放的歌曲右边加个箭头提示
            }
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            item.setLayoutParams(lp);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (service != null) {
                        service.playIndex(index);
                        finish(); // 选中后自动返回主界面
                    }
                }
            });
            listContainer.addView(item);
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (UikitConfig.prefs(this).getBoolean("uikit_swipe_back", true)) {
            if (UikitUi.handleSwipeBack(this, ev)) return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (bound) unbindService(connection);
        super.onDestroy();
    }
}
