package com.example.oldmusic;

import android.content.Context;
import android.content.SharedPreferences;
import org.uikit.UikitConfig;

public class ThemeHelper {
    private static final String PREF_NAME = "oldmusic_settings";

    public static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void applyGlobalTheme(Context context) {
        SharedPreferences sp = getPrefs(context);
        
        // 1. 处理深色模式（默认强制深色）
        String themeMode = sp.getString("theme_mode", UikitConfig.THEME_DARK);
        UikitConfig.setThemeMode(context, themeMode);
        
        // 2. 锁定主色调为赛博黄（不再提供切换）
        UikitConfig.setCustomPrimaryColor(context, 0xFFFBC02D);
        UikitConfig.setCustomAccentColor(context, 0xFFFFD54F);

        // 3. 屏幕形状适配（默认 SHAPE_AUTO，由系统自动判断）
        int shape = sp.getInt("screen_shape", UikitConfig.SHAPE_AUTO);
        UikitConfig.setScreenShape(context, shape);
    }
}
