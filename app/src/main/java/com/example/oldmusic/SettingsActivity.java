package com.example.oldmusic;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.uikit.*;

public class SettingsActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UikitUi.wrapScale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UikitTheme.applyTheme(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        UikitUi.applyRoundPadding(this, root);
        UikitUi.setupWheelScroll(scroll, root);

        TextView title = new TextView(this);
        title.setText("UI 设置");
        title.setTextSize(22);
        title.setTextColor(0xFFFBC02D);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        // 1. 切换深色/浅色模式
        boolean isDark = UikitTheme.isDarkMode(this);
        UikitListItem itemTheme = new UikitListItem(this, "深色模式", "切换深色与浅色主题");
        itemTheme.setSwitch(isDark, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                UikitConfig.setThemeMode(SettingsActivity.this, 
                        isChecked ? UikitConfig.THEME_DARK : UikitConfig.THEME_LIGHT);
                recreate(); // 重启当前页面生效
            }
        });
        addItemWithMargin(root, itemTheme);

        // 2. 切换主色调
        UikitListItem itemColor = new UikitListItem(this, "主题颜色", "当前：赛博黄");
        itemColor.setArrow();
        itemColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UikitUi.showList(SettingsActivity.this, "选择主题色", 
                        new String[]{"赛博黄", "极客绿", "经典蓝", "玫瑰红"},
                        new UikitUi.OnItemSelectListener() {
                            @Override
                            public void onItemSelect(int position) {
                                int color = 0xFFFBC02D; // 默认黄
                                if (position == 1) color = 0xFF388E3C; // 绿
                                else if (position == 2) color = 0xFF1976D2; // 蓝
                                else if (position == 3) color = 0xFFD32F2F; // 红
                                
                                UikitConfig.setCustomPrimaryColor(SettingsActivity.this, color);
                                Toast.makeText(SettingsActivity.this, "主题色已切换，请重启应用查看", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
            }
        });
        addItemWithMargin(root, itemColor);

        // 3. 震动反馈开关
        UikitListItem itemVibrate = new UikitListItem(this, "震动反馈", "按键操作时触发马达震动");
        itemVibrate.setSwitch(true, null);
        addItemWithMargin(root, itemVibrate);
    }

    private void addItemWithMargin(LinearLayout root, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        view.setLayoutParams(lp);
        root.addView(view);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
