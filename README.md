# USB Camera Monitor

USB Camera Monitor 是一個 Android USB camera 預覽 app，可以讀取接在手機 USB-C / OTG 充電孔上的 UVC camera，並把畫面顯示在手機上。

[English README](readme_en.md)

## 功能特色

- 預覽 USB-C / OTG 連接的 UVC camera 畫面。
- 自動偵測已連接的 USB camera。
- 支援多台 USB camera 切換。
- 獨立設定頁面，可調整：
  - Camera 裝置
  - 解析度
  - MJPEG / YUYV 格式
  - FPS 範圍
  - Camera 聲音播放到手機喇叭
- 設定會自動儲存，從設定頁返回主畫面時自動套用。
- 預覽控制按鈕會在閒置後自動隱藏，點擊畫面後重新顯示。
- 支援三種畫面模式：
  - 初始顯示模式：保持 camera 原始比例，完整顯示畫面。
  - 強制全螢幕拉伸模式：不保持比例，強制填滿整個螢幕。
  - 等比全螢幕覆蓋模式：保持比例並放大到覆蓋整個螢幕。
- 支援雙指縮放與單指拖動畫面。
- 支援 reset 回到初始畫面狀態。
- 支援旋轉手機時保持預覽流程穩定，避免 camera 因旋轉重新啟動。

## 使用需求

- 支援 USB OTG / USB Host 的 Android 手機。
- UVC 相容 USB camera。
- USB-C 轉接頭或 USB Hub。
- Android 4.4 以上。

## 安裝

可以前往 [GitHub Releases](https://github.com/breeze0305/Android-USB-Camera-App/releases) 下載最新 APK，然後安裝到 Android 手機。

若手機顯示「不明來源」或「未知應用程式」提示，請依照系統提示允許安裝。

## 贊助

這個 app 維持無廣告。如果你覺得它對你有幫助，歡迎贊助支持開發。

<a href="https://p.ecpay.com.tw/ED0F9CA"><img src="https://payment.ecpay.com.tw/Upload/QRCode/202304/QRCode_6eefa1d4-cfe8-4dc3-a344-37455453a7a3.png" alt="贊助 QR Code" width="180"></a>

[前往贊助頁面](https://p.ecpay.com.tw/ED0F9CA)

## Camera 聲音狀態

設定頁可以開啟「播放 camera 聲音到喇叭」。開啟後，app 會嘗試讀取 USB camera 的 UAC 音訊介面，並透過手機喇叭播放。

目前 GitHub 版本重新包含 `arm64-v8a` 的 `libUACAudio.so`，因此主要支援 arm64 Android 手機。如果 camera 本身沒有 UAC 音訊介面，或手機 / camera 不相容，app 會保留影像預覽並略過聲音播放。

## 開發環境

- Android Studio 與 Android SDK。
- Android NDK。
- Gradle Wrapper 已包含在專案內。

第一次建置前請建立 `local.properties`，內容依照你本機 Android SDK / NDK 位置調整：

```properties
sdk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk\\ndk\\21.1.6352462
```

`local.properties` 是本機環境設定，已經加入 `.gitignore`，不應提交到 GitHub。

## 建置 Debug APK

```powershell
.\gradlew.bat assembleDebug
```

輸出的 APK 位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安裝到已連接手機

可使用 USB 偵錯或無線偵錯：

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以用 adb 啟動 app：

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

## 安全與維護備註

本專案已經清理大量未使用的 legacy 程式碼與未實作流程，但仍包含 USB camera native stack。詳細風險與整理紀錄請參考 [SECURITY_NOTES.md](SECURITY_NOTES.md)。

## 授權

本專案基於 AndroidUSBCamera / AUSBC 相關技術整理與改作，依照專案內授權檔案與 Apache-2.0 License 條款使用。
