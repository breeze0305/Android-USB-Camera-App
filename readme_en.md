# USB Camera Monitor

USB Camera Monitor is an Android USB camera preview app. It can read a UVC camera connected through the phone USB-C / OTG charging port and display the camera feed on the phone.

[中文 README](README.md)

## Features

- Preview the UVC camera feed from a USB-C / OTG connection.
- Automatically detects connected USB cameras.
- Supports switching between multiple USB cameras.
- Separate settings page for:
  - Camera device
  - Resolution
  - MJPEG / YUYV format
  - FPS range
  - Camera audio playback through the phone speaker
- Settings are saved automatically and applied when returning from the settings page.
- Preview controls hide automatically after inactivity and reappear when tapping the preview.
- Supports three display modes:
  - Initial display mode: preserves the camera aspect ratio and shows the full frame.
  - Forced fullscreen stretch mode: fills the entire screen without preserving aspect ratio.
  - Aspect-ratio fullscreen cover mode: preserves aspect ratio and scales up until the screen is covered.
- Supports pinch-to-zoom and one-finger drag on the preview.
- Supports reset back to the initial display state.
- Keeps the preview flow stable during phone rotation and avoids restarting the camera due to rotation.

## Requirements

- Android phone with USB OTG / USB Host support.
- UVC-compatible USB camera.
- USB-C adapter or USB hub.
- Android 4.4 or later.

## Installation

Download the latest APK from [GitHub Releases](https://github.com/breeze0305/Android-USB-Camera-App/releases), then install it on your Android phone.

If the phone shows an "unknown source" or "unknown app" warning, follow the Android system prompt to allow installation.

## Sponsorship

This app stays ad-free. If you find it useful, sponsorship is welcome and helps support development.

<a href="https://p.ecpay.com.tw/ED0F9CA"><img src="https://payment.ecpay.com.tw/Upload/QRCode/202304/QRCode_6eefa1d4-cfe8-4dc3-a344-37455453a7a3.png" alt="Sponsor QR Code" width="180"></a>

[Go to sponsor page](https://p.ecpay.com.tw/ED0F9CA)

## Camera Audio Status

The settings page can enable "play camera audio to speaker". When enabled, the app tries to read the USB camera UAC audio interface and play it through the phone speaker.

The current GitHub version includes the `arm64-v8a` `libUACAudio.so`, so it mainly supports arm64 Android phones. If the camera does not have a UAC audio interface, or if the phone / camera is incompatible, the app keeps video preview running and skips audio playback.

## Development Environment

- Android Studio and Android SDK.
- Android NDK.
- Gradle Wrapper is included in this repository.

Before the first build, create `local.properties` and adjust the Android SDK / NDK paths for your machine:

```properties
sdk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk\\ndk\\21.1.6352462
```

`local.properties` is a local environment file, is already included in `.gitignore`, and should not be committed to GitHub.

## Build Debug APK

```powershell
.\gradlew.bat assembleDebug
```

APK output path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install To A Connected Phone

Use USB debugging or wireless debugging:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also launch the app with adb:

```powershell
adb shell am start -n com.breeze.usbcamera/.MainActivity
```

## Project Structure

```text
app/        Main Android application
libausbc/   Camera control and OpenGL render layer
libuvc/     Native UVC camera support
gradle/     Gradle wrapper
```

## Security And Maintenance Notes

This project has cleaned up a large amount of unused legacy code and unimplemented flows, but it still contains a USB camera native stack. See [SECURITY_NOTES.md](SECURITY_NOTES.md) for detailed risks and cleanup notes.

## License

This project is distributed under the Apache-2.0 License. See the license file in this repository for details.
