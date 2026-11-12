package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.uikit.*;

public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private boolean isDark;
    private PlaybackService service;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).getService();
            bound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { bound = false; service = null; }
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
        sp = ThemeHelper.getPrefs(this);
        isDark = UikitTheme.isDarkMode(this);

        Intent intent = new Intent(this, PlaybackService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        buildUI();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(isDark ? 0xFF121212 : 0xFFF4F6F8);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(32), 0, dp(32));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        TextView title = new TextView(this);
        title.setText("UI 设置");
        title.setTextSize(22);
        title.setTextColor(isDark ? 0xFFFBC02D : 0xFF1976D2);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        // 1. 深色模式
        UikitListItem itemTheme = new UikitListItem(this, "深色模式", "切换深色与浅色主题");
        itemTheme.setSwitch(isDark, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                sp.edit().putString("theme_mode", isChecked ? UikitConfig.THEME_DARK : UikitConfig.THEME_LIGHT).commit();
                recreate();
            }
        });
        addItemWithMargin(root, itemTheme);

        // 2. 均衡器预设
        UikitListItem itemEq = new UikitListItem(this, "均衡器", "切换音效预设");
        itemEq.setArrow();
        itemEq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UikitPickerActivity.show(SettingsActivity.this, "选择音效", 
                    new String[]{"正常", "流行", "摇滚", "重低音", "人声"}, 1001);
            }
        });
        addItemWithMargin(root, itemEq);

        // 3. 强制圆屏适配
        boolean isForceRound = sp.getInt("screen_shape", UikitConfig.SHAPE_AUTO) == UikitConfig.SHAPE_ROUND;
        UikitListItem itemRound = new UikitListItem(this, "强制圆屏模式", "强制启用圆屏的边距与布局");
        itemRound.setSwitch(isForceRound, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                sp.edit().putInt("screen_shape", isChecked ? UikitConfig.SHAPE_ROUND : UikitConfig.SHAPE_AUTO).commit();
                recreate();
            }
        });
        addItemWithMargin(root, itemRound);

        // 4. 侧滑返回
        boolean swipeBack = sp.getBoolean("swipe_back", true);
        UikitListItem itemSwipe = new UikitListItem(this, "侧滑返回", "通过手势快速退出当前页面");
        itemSwipe.setSwitch(swipeBack, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean("swipe_back", isChecked).commit();
            }
        });
        addItemWithMargin(root, itemSwipe);

        // 5. 关于 OldMusic
        UikitListItem itemAbout = new UikitListItem(this, "关于 OldMusic", "版本信息与开源声明");
        itemAbout.setArrow();
        itemAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String longText = "OldMusic 是一款专为低版本 Android 与 Wear OS 打造的开源本地音乐播放器。" +
                                  "它完美适配 Android 2.3 至最新系统。" +
                                  "它拥有极简的 Wear OS 风格 UI。" +
                                  "它不仅支持 LRC 歌词，还内置了均衡器与睡眠定时。" +
                                  "本项目基于优秀的 wear-uikit 组件库构建。" +
                                  "项目开源地址是：https://github.com/styisme/oldmusic。" +
                                  "感谢所有开源贡献者！";
                UikitFullTextActivity.show(SettingsActivity.this, "关于 OldMusic", longText, "我知道了", false, 1002);
            }
        });
        addItemWithMargin(root, itemAbout);
    }

    private void addItemWithMargin(LinearLayout root, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        lp.rightMargin = dp(8);
        lp.bottomMargin = dp(12);
        view.setLayoutParams(lp);
        root.addView(view);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            int index = data.getIntExtra(UikitPickerActivity.EXTRA_RESULT_INDEX, -1);
            if (index >= 0 && bound && service != null) {
                // 映射为原生的预设编号 (通常 0 是正常，1 是流行...)
                service.setEqualizerPreset((short) index);
                UikitToast.show(this, "音效已切换");
            }
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (sp.getBoolean("swipe_back", true)) {
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
