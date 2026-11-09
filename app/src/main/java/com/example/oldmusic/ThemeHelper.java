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
        
        // 2. 处理主色调（默认赛博黄）
        int primaryColor = sp.getInt("primary_color", 0xFFFBC02D);
        UikitConfig.setCustomPrimaryColor(context, primaryColor);
        
        // 3. 强调色（默认浅黄）
        UikitConfig.setCustomAccentColor(context, 0xFFFFD54F);

        // 4. 强制圆屏适配（防止卡片切边）
        UikitConfig.setScreenShape(context, UikitConfig.SHAPE_ROUND);
    }
}
