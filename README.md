# OldMusic

兼容 Android 2.3.3（minSdk 10）的本地音乐播放器，UI 接入
[wear-uikit](https://github.com/styisme/wear-uikit)。

## 功能

- 本地音乐扫描（MediaStore）
- 播放列表（顺序 / 单曲循环 / 随机）
- 后台播放服务（MediaPlayer + WakeLock + 音频焦点）
- LRC 歌词解析与逐行高亮滚动
- 通知栏展示当前歌曲，点击回到界面
- 睡眠定时、耳机媒体按键
- 播放队列与进度持久化
- GitHub Actions 自动编译并上传 APK

## 环境

- JDK 8（旧版 AGP 必须）
- Gradle 5.6.4
- Android SDK Platform 28、Build-Tools 28.0.3

## 本地构建

```bash
# 首次需要生成 wrapper（Android Studio 打开项目会自动生成）
gradle wrapper --gradle-version 5.6.4

# 构建 debug 包
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 关于 wear-uikit

`UikitBridge` 通过反射调用 `UikitConfig`、`UikitTheme`、`UikitUi`，
避免因库版本 / 包名不同导致编译失败。

- 如果 JitPack 上的 `com.github.styisme:wear-uikit:v1.2.0` 不可用，
  请修改 `app/build.gradle` 中的依赖坐标，或暂时注释该行。
- 如果希望直接使用 `UikitButton`、`UikitProgressBar` 等组件替换标准控件，
  可按库当前文档调整 `activity_main.xml` 与 `MainActivity` 中的 import。

## Android 2.3.3 注意事项

- 通知栏按钮在 API 11 以下不可靠，本项目的通知只做展示与点击回界面。
- 不使用 RecyclerView、java.util.function、try-with-resources。
- SharedPreferences 使用 `commit()`。
- 均衡器若启用，需要用 `getNumberOfBands()` 动态取频段。
