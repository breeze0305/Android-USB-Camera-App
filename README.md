# USB Camera Monitor

USB Camera Monitor 是一個 Android USB camera 預覽 app，可以讀取接在手機 USB-C 充電孔上的 UVC camera，並把畫面顯示在手機上。

[English README](readme_en.md)

## 主要功能

- 讀取 USB-C / OTG 連接的 UVC camera。
- 在手機上即時顯示 camera 畫面。
- 支援多台 USB camera 切換。
- 獨立設定頁面，可調整：
  - Camera 裝置
  - 解析度 / 畫質
  - MJPEG / YUYV 格式
  - FPS 上限
  - Camera 聲音播放開關（此乾淨版不內建 UAC 原生音訊庫，開啟時若裝置音訊不可用會提示不支援）
- 設定會自動保存，不需要按套用。
- 從設定頁返回主畫面後會自動套用新設定。
- 主畫面控制列會自動隱藏，點擊預覽畫面後重新顯示。
- 三種預覽顯示模式：
  - 初始模式：保持比例，完整顯示畫面。
  - 拉伸模式：不保持比例，強制填滿螢幕。
  - 覆蓋模式：保持比例，放大到覆蓋螢幕並裁切超出部分。
- 支援雙指縮放與單指拖曳畫面。
- 提供 reset 按鈕回到初始畫面狀態。
- 支援手機旋轉後重新同步預覽比例，避免畫面被壓縮。

## 使用需求

- 支援 USB OTG / USB Host 的 Android 手機。
- UVC 相容的 USB camera。
- USB-C 轉接頭或 USB Hub。
- Android 4.4 以上，建議使用較新的 Android 版本。

## 音訊支援狀態

為了避免提交沒有原始碼的預編譯 UAC 音訊庫，GitHub 版本目前只內建 USB camera 影像預覽。設定頁仍保留 camera 聲音播放開關；若目前建置不包含可用的 UAC 音訊實作，app 會顯示音訊不可用，而不會改播手機麥克風。

## 下載與安裝

可以從 GitHub Releases 下載 APK，然後安裝到 Android 手機。

若手機跳出未知來源安裝提示，請依系統指示允許安裝。

## 開發環境

- Android Studio 或 Android SDK。
- Android NDK。
- Gradle Wrapper 已包含在專案中。

本機需要建立 `local.properties`，例如：

```properties
sdk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk\\ndk\\21.1.6352462
```

`local.properties` 是每台電腦不同的本機設定，已經被 `.gitignore` 排除，不應該提交到 GitHub。

## 建置 Debug APK

```powershell
.\gradlew.bat assembleDebug
```

輸出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安裝到已連線手機

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以用指令直接啟動：

```powershell
adb shell am start -n com.breeze.usbcamera/.MainActivity
```

## 專案結構

```text
app/        Android app 主程式
libausbc/   AUSBC camera wrapper 與 OpenGL render 層
libuvc/     UVC camera native 支援
gradle/     Gradle wrapper
```

## 安全與第三方元件

本專案保留部分 USB camera native stack。已知風險與後續替換計畫請看 [SECURITY_NOTES.md](SECURITY_NOTES.md)。

## 授權

本專案基於 AndroidUSBCamera / AUSBC 修改，保留原始 Apache-2.0 License。
