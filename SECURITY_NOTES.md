# Security Notes

This project currently keeps a small Android app layer on top of legacy USB camera native code. The app code has been cleaned up where practical, but several bundled native dependencies still need a planned replacement or upgrade pass before treating the project as a long-term production base.

## Immediate Fixes Already Applied

- Disabled the unimplemented streaming fallback so calling the pusher API no longer throws `NotImplementedError`.
- Turned off `USBMonitor.DEBUG` by default to reduce USB device detail leakage in release builds.
- Tightened key USB, UAC audio, and UVC camera exception paths so interruption, timeout, cancellation, and runtime failures are logged more explicitly.
- Removed the unused LAME/MP3 native encoder path from `libnative`; MP3 recording now reports unsupported instead of loading legacy native code.
- Replaced the remaining YUV JNI helper with a Kotlin implementation and removed the `libnative` module.
- Replaced the UVC parameter JSON writer with a local implementation and removed bundled RapidJSON sources.

## Bundled Native Components

| Component | Local path | Detected version | Risk note | Recommended action |
| --- | --- | --- | --- | --- |
| libuvc | `libuvc/src/main/jni/libuvc` | 0.0.4 | NVD lists CVE-2026-1991 for libuvc up to 0.0.7 in the UVC descriptor handler. | Replace with maintained source or rewrite the narrow UVC descriptor/stream bridge used by this app. |
| libusb | `libuvc/src/main/jni/libusb` | 1.0.19 | NVD lists CVE-2026-23679 for libusb before 1.0.30. | Upgrade to libusb 1.0.30+ and re-test Android USB permission/control-block flow. |
| libjpeg-turbo | `libuvc/src/main/jni/libjpeg-turbo-1.5.0` | 1.5.0 | Old JPEG decoder code; NVD lists historical crafted-input issues such as CVE-2020-35538. | Prefer Android platform decoding or upgrade to a current libjpeg-turbo release if MJPEG native decode remains needed. |
| RapidJSON | removed | not bundled | Formerly used only for native UVC parameter JSON serialization. | Keep the local writer limited to generated camera metadata; use a maintained parser if untrusted JSON input is added later. |
| libnative | removed | not bundled | Former MP3/LAME and YUV JNI helpers are no longer shipped. | Keep YUV conversion in Kotlin unless profiling proves a native implementation is required again. |

## Current Release Stance

The app can be used for local USB camera preview testing after the applied runtime fixes. Do not treat the bundled native stack as fully audited. The next cleanup phase should reduce the app to the minimum owned surface:

1. Keep the app UI, settings persistence, preview transform, and gesture code as owned Kotlin code.
2. Replace broad AUSBC demo surfaces with a narrow USB camera service interface.
3. Upgrade or replace libusb/libuvc/libjpeg-turbo before publishing a production release.
4. Remove unused native folders and example/test sources that are not required to build the preview app.

## References

- [NVD CVE-2026-1991: libuvc descriptor null pointer dereference](https://nvd.nist.gov/vuln/detail/CVE-2026-1991)
- [NVD CVE-2026-23679: libusb before 1.0.30 null pointer dereference](https://nvd.nist.gov/vuln/detail/CVE-2026-23679)
- [NVD CVE-2020-35538: libjpeg-turbo crafted input null pointer dereference](https://nvd.nist.gov/vuln/detail/CVE-2020-35538)
- [NVD CVE-2024-39684: RapidJSON integer overflow](https://nvd.nist.gov/vuln/detail/CVE-2024-39684)
