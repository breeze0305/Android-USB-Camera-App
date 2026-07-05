# Security Notes

This project currently keeps a small Android app layer on top of legacy USB camera native code. The app code has been cleaned up where practical, but several bundled native dependencies still need a planned replacement or upgrade pass before treating the project as a long-term production base.

## Immediate Fixes Already Applied

- Disabled the unimplemented streaming fallback so calling the pusher API no longer throws `NotImplementedError`.
- Turned off `USBMonitor.DEBUG` by default to reduce USB device detail leakage in release builds.
- Tightened key USB, optional UAC audio, and UVC camera exception paths so interruption, timeout, cancellation, native-load failure, and runtime failures are logged more explicitly.
- Removed the unused LAME/MP3 native encoder path from `libnative`, then removed the now-unused recording/streaming surface from the app library.
- Removed the remaining YUV JNI helper and later removed the unused Kotlin YUV compatibility helper after the H.264/raw-capture path was deleted.
- Replaced the UVC parameter JSON writer with a local implementation and removed bundled RapidJSON sources.
- Removed the prebuilt `libUACAudio.so` binaries and the unused UAC Java/Kotlin playback pipeline because no matching source implementation is bundled. The app no longer exposes the dead camera-audio setting or requests `RECORD_AUDIO`.
- Removed the unused prebuilt `libuvc-3.2.9.aar`; the app now builds the active UVC camera libraries from the checked-in native source tree.
- Pruned unused third-party native docs, examples, tests, IDE projects, generated docs, desktop packaging files, and test images that are not referenced by the Android NDK build.
- Removed unreferenced native `_original` backup sources and Android makefile copies that were not part of the active NDK build.
- Removed unused libjpeg-turbo desktop build metadata, documentation, man pages, and test templates while keeping the source files required by the Android NDK build.
- Removed unused libjpeg-turbo command-line tools, format-conversion helpers, JNI sample binding, and benchmark/unit-test sources that are not referenced by the Android NDK build.
- Removed unused libuvc desktop build metadata, generated documentation config, changelog/readme copies, and example/test/desktop-only source files that are not referenced by the Android NDK build.
- Removed unused libusb desktop build metadata, Android example/test makefiles, Windows resources, and non-Android OS backend sources while keeping the Android usbfs/netlink/poll/thread implementation used by the NDK build.
- Removed unimplemented relative pan/tilt/roll UVC control bridge entries from the Java and JNI surfaces instead of exposing native methods that always fail.
- Added an explicit preview-start failure path in the active UVC camera open flow so unsupported surfaces or native preview startup failures close the camera and emit an error state instead of escaping as an uncaught runtime failure.
- Removed unused camera-parameter helper methods from the app-facing base activity so the app no longer exposes brightness, contrast, gain, gamma, hue, saturation, sharpness, zoom, or raw command controls through its inherited activity API.
- Removed the corresponding unused UVC Kotlin wrapper methods for raw commands and camera-parameter controls, leaving the app library focused on preview setup and device switching.
- Removed the remaining public Java UVC parameter/capture wrappers from `UVCCamera`, and stopped the app preview startup path from invoking autofocus, white-balance, or parameter refresh calls that are not needed for display-only USB camera preview.
- Removed the unused Java native declarations and JNI registration entries for UVC camera-parameter, capture, and raw-command controls so class loading now registers only the native methods required for USB preview.
- Removed the now-unregistered JNI bridge function bodies for UVC camera-parameter, capture, and raw-command controls from `serenegiant_usb_UVCCamera.cpp`.
- Removed the unused native `UVCCamera/pipeline` sources for legacy capture, callback, preview pipeline, publisher, and SQLite buffering paths because they are not included by the active Android NDK build and have no Java/Kotlin entry point in this app.
- Removed the public raw frame callback API (`IFrameCallback` / `setFrameCallback`) and its JNI bridge because this app only needs display preview and does not expose raw camera frames to Java callers.
- Removed the now-unreachable native capture surface/thread/frame-queue path from `UVCPreview`; preview frames are drawn to the display surface and immediately recycled instead of being copied into a second capture queue.
- Removed the remaining unreachable native UVC camera-parameter/control method implementations and cached state from `UVCCamera.cpp` / `UVCCamera.h`; the native camera wrapper now keeps only USB connection, status/button callbacks, supported-size metadata, and display preview methods.
- Removed the matching dead Java UVC control constants and native-populated min/max/default cache fields from `UVCCamera`; the Java wrapper now exposes only display-preview state and metadata used by this app.
- Trimmed `libuvc/src/ctrl.c` and `libuvc.h` to the request error-code helpers still needed by streaming; unused generic control transfer, brightness, focus, exposure, zoom, white-balance, pan/tilt, and processing-unit get/set APIs are no longer compiled or advertised.
- Added allocation-failure handling to the UVC stream frame-buffer growth path so a failed `realloc` no longer loses the previous buffer or falls through into an unsafe copy.
- Hardened the shared UVC frame resize helper so failed `realloc` calls preserve the previous frame buffer and return `UVC_ERROR_NO_MEM` before mutating frame size metadata.
- Added allocation and startup-failure cleanup checks to UVC stream open/start paths, including transfer buffer allocation, transfer allocation, callback-thread creation, and partial libusb transfer submission.
- Added allocation checks to active UVC descriptor parsing paths so malformed or memory-constrained USB descriptor handling returns an error instead of dereferencing null parser nodes.
- Added allocation and null-cleanup checks to UVC device discovery, descriptor fetch, camera open, and multi-device list construction paths.
- Removed unused AUSBC demo surfaces: pusher APIs, the old single-camera client, fragment/dialog demo bases, demo resources, recording/streaming/image-capture APIs, raw preview callback/FBO readback support, render effects, capture widgets, media helper code, render event bus, Activity stack/crash utility framework, and legacy Camera1/Camera2/strategy abstractions that are not used by this app's USB preview flow.
- Narrowed the preview host surface to the TextureView/OpenGL path used by this app and removed unused SurfaceView/GLSurfaceView render-mode selection code.
- Removed unused app demo theme resources and trimmed unused AUSBC utility helpers that were not referenced by the USB camera preview flow.
- Rewrote the active aspect-ratio preview widget and interface with a small project-owned implementation, including safer null handling for missing `SurfaceTexture` instances.
- Rewrote small active AUSBC callback and camera-request data APIs as concise project-owned Kotlin definitions while preserving the existing public method names used by the app.
- Reworked thin active AUSBC utility wrappers and added explicit null handling around USB device filter checks.
- Replaced the legacy AQS-based `SettableFuture` helper with a small project-owned synchronized implementation used by camera/render wait paths.
- Narrowed the active camera activity base toward this app's TextureView preview flow, removed unused generic container branches, and added a guard for null USB permission requests.
- Consolidated the active multi-camera USB event dispatch path behind a project-owned filter helper to keep attach/connect/detach/cancel handling consistent.
- Rewrote `MultiCameraClient` as a project-owned implementation while preserving the app-facing API, with clearer preview start/stop helpers, OpenGL setup, size waiting, and preview-size selection.
- Rewrote the active UVC camera adapter with a project-owned open/configure/start flow while preserving permission checks, preview-size fallback, and explicit error callbacks.
- Replaced the small render rotation enum with a project-owned definition while keeping the same constants used by the preview pipeline.
- Rewrote the active render manager with project-owned render-thread lifecycle, GL message dispatch, SurfaceTexture waiting, frame drawing, and cleanup flow.
- Replaced the screen render wrapper with a small project-owned adapter around the EGL environment while preserving the existing preview shader path.
- Rewrote the EGL environment helper with explicit display/config/context/surface ownership checks and safer surface replacement cleanup.

## Bundled Native Components

| Component | Local path | Detected version | Risk note | Recommended action |
| --- | --- | --- | --- | --- |
| libuvc | `libuvc/src/main/jni/libuvc` | 0.0.4 | NVD lists CVE-2026-1991 for libuvc up to 0.0.7 in the UVC descriptor handler. | Replace with maintained source or rewrite the narrow UVC descriptor/stream bridge used by this app. |
| libusb | `libuvc/src/main/jni/libusb` | 1.0.19 | NVD lists CVE-2026-23679 for libusb before 1.0.30. | Upgrade to libusb 1.0.30+ and re-test Android USB permission/control-block flow. |
| libjpeg-turbo | `libuvc/src/main/jni/libjpeg-turbo-1.5.0` | 1.5.0 | Old JPEG decoder code; NVD lists historical crafted-input issues such as CVE-2018-14498 for libjpeg-turbo through 1.5.90. | Prefer Android platform decoding or upgrade to a current libjpeg-turbo release if MJPEG native decode remains needed. |
| RapidJSON | removed | not bundled | Formerly used only for native UVC parameter JSON serialization. | Keep the local writer limited to generated camera metadata; use a maintained parser if untrusted JSON input is added later. |
| libnative | removed | not bundled | Former MP3/LAME and YUV JNI helpers are no longer shipped. | Reintroduce native helpers only with source, licensing notes, and a measured need. |
| UAC audio native binary | removed | not bundled | Formerly shipped only as `libUACAudio.so` without corresponding source in this repository. | Reintroduce camera audio only with a source-built UAC implementation or a clearly documented, licensed binary distribution. |
| libuvc AAR | removed | not bundled | Former `libuvc-3.2.9.aar` was an unused prebuilt package. | Keep distribution source-first; rebuild release artifacts from source instead of committing AAR outputs. |

## Current Release Stance

The app can be used for local USB camera preview testing after the applied runtime fixes. Do not treat the bundled native stack as fully audited. The next cleanup phase should reduce the app to the minimum owned surface:

1. Keep the app UI, settings persistence, preview transform, and gesture code as owned Kotlin code.
2. Continue replacing the remaining broad AUSBC wrapper surface with a narrow USB camera service interface.
3. Upgrade or replace libusb/libuvc/libjpeg-turbo before publishing a production release.
4. Continue shrinking unused native surfaces and example/test sources as the app replaces the legacy UVC stack.

## References

- [NVD CVE-2026-1991: libuvc descriptor null pointer dereference](https://nvd.nist.gov/vuln/detail/CVE-2026-1991)
- [NVD CVE-2026-23679: libusb before 1.0.30 null pointer dereference](https://nvd.nist.gov/vuln/detail/CVE-2026-23679)
- [NVD CVE-2018-14498: libjpeg-turbo out-of-bounds read](https://nvd.nist.gov/vuln/detail/CVE-2018-14498)
- [NVD CVE-2024-39684: RapidJSON integer overflow](https://nvd.nist.gov/vuln/detail/CVE-2024-39684)
