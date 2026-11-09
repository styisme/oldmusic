package com.example.oldmusic;

import android.app.Activity;

/**
 * wear-uikit 接入桥。
 * 库地址: https://github.com/styisme/wear-uikit
 *
 * 这里使用反射调用，避免因库版本 / 包名差异导致整个项目编译失败。
 * 如果你的 wear-uikit 版本 API 稳定，可改为直接 import 调用。
 */
public final class UikitBridge {

    private UikitBridge() {
    }

    public static void init(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            Class<?> config = Class.forName("com.styisme.wearuikit.UikitConfig");
            config.getMethod("init", String.class).invoke(null, "oldmusic_prefs");
        } catch (Throwable ignored) {
        }
        try {
            Class<?> theme = Class.forName("com.styisme.wearuikit.UikitTheme");
            theme.getMethod("applyTheme", Activity.class).invoke(null, activity);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ui = Class.forName("com.styisme.wearuikit.UikitUi");
            ui.getMethod("applyEntryAnimation", Activity.class).invoke(null, activity);
        } catch (Throwable ignored) {
        }
    }
}
