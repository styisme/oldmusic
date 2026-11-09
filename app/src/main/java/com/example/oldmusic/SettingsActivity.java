package com.example.oldmusic;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.uikit.*;

public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private boolean isDark;

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

        // 2. 主题色
        UikitListItem itemColor = new UikitListItem(this, "主题颜色", "更改应用主色调");
        itemColor.setArrow();
        itemColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UikitUi.showList(SettingsActivity.this, "选择主题色",
                        new String[]{"赛博黄", "极客绿", "经典蓝", "玫瑰红"},
                        new UikitUi.OnItemSelectListener() {
                            @Override
                            public void onItemSelect(int position) {
                                int color = 0xFFFBC02D;
                                if (position == 1) color = 0xFF388E3C;
                                else if (position == 2) color = 0xFF1976D2;
                                else if (position == 3) color = 0xFFD32F2F;
                                
                                sp.edit().putInt("primary_color", color).commit();
                                recreate(); 
                            }
                        }).show();
            }
        });
        addItemWithMargin(root, itemColor);

        // 3. 强制圆屏适配（补回来的开关）
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

        // 5. 低性能模式
        UikitListItem itemPerf = new UikitListItem(this, "低性能模式", "关闭 3D 滚轮与滑动动画");
        itemPerf.setSwitch(UikitConfig.isLowPerf(this), new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                UikitConfig.setLowPerf(SettingsActivity.this, isChecked);
                recreate();
            }
        });
        addItemWithMargin(root, itemPerf);
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (sp.getBoolean("swipe_back", true)) {
            if (UikitUi.handleSwipeBack(this, ev)) return true;
        }
        return super.dispatchTouchEvent(ev);
    }
}
