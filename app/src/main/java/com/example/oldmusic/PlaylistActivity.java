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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.uikit.*;

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
        ThemeHelper.applyGlobalTheme(this);
        UikitTheme.applyTheme(this);

        boolean isDark = UikitTheme.isDarkMode(this);

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(isDark ? 0xFF121212 : 0xFFF4F6F8);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, dp(32), 0, dp(32)); 

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(32));
        scroll.addView(root);

        rootFrame.addView(scroll);
        setContentView(rootFrame);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        TextView title = new TextView(this);
        title.setText("播放列表");
        title.setTextSize(22);
        // 修复颜色对比度：深色模式用亮黄，浅色模式用深蓝
        title.setTextColor(isDark ? 0xFFFBC02D : 0xFF1976D2);
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

            // 截断超长文本，防止撑破卡片
            if (title.length() > 14) title = title.substring(0, 14) + "...";
            if (artist.length() > 14) artist = artist.substring(0, 14) + "...";

            UikitListItem item = new UikitListItem(this, title, artist);
            if (i == currentIndex) {
                item.setArrow(); 
            }
            
            // 修复粘连：给 LayoutParams 强制设置 bottomMargin 并且用 FrameLayout 包裹
            FrameLayout wrapper = new FrameLayout(this);
            LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wrapperLp.bottomMargin = dp(12); 
            wrapper.setLayoutParams(wrapperLp);
            
            wrapper.addView(item, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

            wrapper.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (service != null) {
                        service.playIndex(index);
                        finish();
                    }
                }
            });
            listContainer.addView(wrapper);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ThemeHelper.getPrefs(this).getBoolean("swipe_back", true)) {
            if (UikitUi.handleSwipeBack(this, ev)) return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        if (bound) unbindService(connection);
        super.onDestroy();
    }
}
