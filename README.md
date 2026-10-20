<div align="center">
  <img src="docs/icon-512.png" width="112" height="112" alt="HyperBoost icon" />
  <h1>HyperBoost</h1>
  <p>Volume booster and equalizer for Android with Xiaomi Hyper Island support</p>
  <p>
    <b>English</b> •
    <a href="README.ru.md">Русский</a> •
    <a href="https://github.com/MrQuster/HyperBoost/releases/latest">Download APK (Releases)</a>
  </p>
</div>

---

HyperBoost is an Android app that pushes audio volume above the standard 100% system cap on phone speakers, Bluetooth headphones, and USB DACs. It uses a translucent glass interface (Liquid Glass) and runs on Android 10 (API 29) and newer.

<!-- TODO: add a short personal note here if you want, e.g., why you started building HyperBoost or which headphones/speakers you use it with -->

## How it works

Android handles audio effects differently depending on the manufacturer and Android version. HyperBoost includes two separate audio engines so you can switch if one is restricted by your firmware:

1. **Soft Mode (Session 0):** Attaches Android's hardware `LoudnessEnhancer` and a 6-band `DynamicsProcessing` stage to the global output mix (`sessionId = 0`). It adds no audio latency and works across all apps, including DRM-protected streaming services.
2. **Audio Capture Mode (PCM DSP):** Uses `MediaProjection` and `AudioPlaybackCapture` to read the raw stereo PCM stream, applies a 64-bit digital gain multiplier, a Biquad low-shelf bass filter (95 Hz), a Mid/Side stereo widener, and a soft-knee (`tanh`) limiter. This bypasses system mixer caps on firmwares where Session 0 is attenuated.

## Multi-vendor support on Android

This is an Android-only app (it does not run on iOS or desktop). On startup, `DeviceProfileDetector` checks `Build` fields, system properties, and installed system packages to adapt to the current ROM:

| Device / OS Family | Status | Notes |
| :--- | :--- | :--- |
| **Xiaomi 17T Pro (HyperOS 4, Android 17 / API 37)** | Tested on physical device | Full support: audio engines, Super Island (`miui.focus.param`) with `−10%` / `+10%` controls, autostart shortcuts. |
| **Other Xiaomi / Redmi / POCO (HyperOS 1–3, MIUI 14)** | Expected to work (needs testing) | Uses the same focus notification format and HyperOS security intents. |
| **Android 16+ devices (Pixel, Nothing, Motorola)** | Expected to work (needs testing) | Uses Android 16 promoted ongoing notifications (Live Updates) and standard AOSP battery settings. |
| **Samsung One UI, OnePlus OxygenOS, OPPO ColorOS, vivo, Honor** | Expected to work (needs testing) | Standard notification controls + vendor-specific background setup steps. Built-in Dolby Atmos or Dirac may compress Session 0 peaks above 150%; use Audio Capture mode if that happens. |

If you test HyperBoost on another phone, open the **System** tab, tap **Copy Report**, and open an issue so we can update this table.

## Main features

- **No artificial cap:** Standard scale goes from 0% to 1000%, with an optional toggle in Settings to unlock up to 10,000% for very quiet recordings.
- **Quick island & shade controls:** Compact droplet icon with current percentage, plus `−10%`, `+10%`, and `On/Off` buttons in Xiaomi Super Island, Android 16+ Live Updates, home screen widget, and Quick Settings tile.
- **5-band equalizer & working bass/surround sliders:** Automatically routes Bass Boost, 3D Surround, and Left/Right balance through hardware `AudioEffect`, `DynamicsProcessing` PreEQ, or software Biquad/Mid-Side DSP depending on what the phone supports.
- **Per-device profiles:** Remembers separate boost levels for the internal speaker, each Bluetooth device, wired headphones, and USB DACs.
- **Zero network access:** The app does not request the `INTERNET` permission.

## Known limitations

- **DRM and capture policy:** Apps that set `ALLOW_CAPTURE_BY_NONE` (some banking apps, voice calls, and certain DRM video streams) block `AudioPlaybackCapture`. Use **Soft Mode (Session 0)** for those apps.
- **Xiaomi Super Island whitelist:** On some global HyperOS builds, `miui.systemui.plugin` restricts custom island layouts to whitelisted system packages. When that happens, HyperBoost falls back to standard ongoing notifications with the same `−10%` / `+10%` buttons.
- **Speaker & hearing safety:** High boost levels can cause speaker clipping or hearing fatigue. Enable the Soft (`tanh`) limiter in Settings if you hear distortion.

## Screenshots

<!-- Place screenshots in docs/screenshots/ and uncomment or keep the table below -->
| Home | Equalizer | Devices | System |
| :---: | :---: | :---: | :---: |
| ![Home](docs/screenshots/home.png) | ![Equalizer](docs/screenshots/equalizer.png) | ![Devices](docs/screenshots/devices.png) | ![System](docs/screenshots/system.png) |

*(Screenshots go into `docs/screenshots/`: `home.png`, `equalizer.png`, `devices.png`, `settings.png`, `system.png`).*

## Installation

1. Go to the [Releases](https://github.com/MrQuster/HyperBoost/releases/latest) page and download `HyperBoost-vX.Y.Z.apk`.
2. Open the APK on your phone and allow installation from your browser or file manager.
3. If Google Play Protect shows an "Unknown developer" prompt, tap **More details** -> **Install anyway**.
4. To verify the download on Linux before installing:
   ```bash
   sha256sum -c HyperBoost-v1.3.0.apk.sha256
