# USB Camera Monitor

Android app for previewing a USB UVC camera connected through the phone USB-C charging port.

This project is based on the AUSBC / AndroidUSBCamera stack and adds a focused app experience for phone-side USB camera monitoring.

## Features

- Preview USB UVC camera video on an Android phone.
- Automatically detects attached USB camera devices.
- Supports switching between multiple connected cameras.
- Separate settings page for camera, resolution, format, FPS, and audio playback.
- Settings are saved automatically and applied when returning to preview.
- Optional camera audio playback to the phone speaker.
- Hidden preview controls: controls fade out after inactivity and reappear when tapping the preview.
- Preview display modes:
  - Initial contain mode: preserves camera aspect ratio and shows the full frame.
  - Stretch mode: fills the full screen without preserving aspect ratio.
  - Cover mode: preserves aspect ratio and crops to cover the full screen.
- Pinch to zoom and one-finger drag on preview.
- Reset button to return to the initial display state.
- Rotation handling keeps preview alive and re-syncs render size after orientation changes.

## Requirements

- Android phone with USB OTG / USB host support.
- UVC-compatible USB camera.
- Android SDK and Gradle environment.
- Android NDK configured through `local.properties`.

Example `local.properties`:

```properties
sdk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\your_name\\AppData\\Local\\Android\\Sdk\\ndk\\21.1.6352462
```

`local.properties` is intentionally ignored by Git because it is machine-specific.

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install To A Connected Phone

Use USB debugging or wireless debugging:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch manually from the phone, or with:

```powershell
adb shell am start -n com.breeze.usbcamera/.MainActivity
```

## Project Structure

```text
app/        Main Android application
libausbc/   AUSBC camera wrapper and rendering layer
libuvc/     Native UVC camera support
libnative/  Native helper module
gradle/     Gradle wrapper files
```

## Git Notes

The repository intentionally ignores local build outputs and machine-specific files:

- `local.properties`
- `.gradle/`
- `build/`
- `app/build/`
- `app/release/`
- `*.apk`
- `*.aab`

Do not commit generated APK/AAB files. Build them locally or through CI when needed.

## License

This project includes code derived from AndroidUSBCamera / AUSBC and keeps the original Apache-2.0 license file.
