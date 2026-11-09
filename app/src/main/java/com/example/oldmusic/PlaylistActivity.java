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
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
        // 读取深色模式配置
        boolean isDark = UikitConfig.prefs(this).getBoolean("is_dark_mode", true);
        UikitConfig.setThemeMode(this, isDark ? UikitConfig.THEME_DARK : UikitConfig.THEME_LIGHT);
        
        // 关键修复：强制开启圆屏适配，防止卡片切边
        UikitConfig.setScreenShape(this, UikitConfig.SHAPE_ROUND);
        UikitTheme.applyTheme(this);

        // 使用 FrameLayout 作为根，确保背景全黑
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(isDark ? 0xFF121212 : 0xFFF4F6F8);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        // 圆屏安全区：上下留白，方便滑到顶/底时依然居中
        scroll.setPadding(0, dp(32), 0, dp(32)); 

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // 加大左右边距 (24dp)，防止圆屏边缘切掉卡片
        root.setPadding(dp(24), dp(16), dp(24), dp(32));
        scroll.addView(root);

        rootFrame.addView(scroll);
        setContentView(rootFrame);

        // 库自带的圆屏判断和边距（会再次检查并叠加上去）
        UikitUi.applyRoundPadding(this, root);
        // 3D 滚轮效果
        UikitUi.setupWheelScroll(scroll, root);

        TextView title = new TextView(this);
        title.setText("播放列表");
        title.setTextSize(22);
        title.setTextColor(UikitColor.getPrimaryColor(this));
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

            // 对超长标题做截断处理，防止把卡片撑爆
            if (title.length() > 15) title = title.substring(0, 15) + "...";
            if (artist.length() > 15) artist = artist.substring(0, 15) + "...";

            UikitListItem item = new UikitListItem(this, title, artist);
            if (i == currentIndex) {
                item.setArrow(); 
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
                        finish();
                    }
                }
            });
            listContainer.addView(item);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (UikitConfig.prefs(this).getBoolean("uikit_swipe_back", true)) {
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
