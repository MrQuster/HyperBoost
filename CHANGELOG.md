# Changelog

All notable changes to **HyperBoost** are documented in this file.
Format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed
- **DSP Limiter Continuity:** Fixed $C^1$ jump of ~3,700 amplitude units at the knee boundary ($0.75 \times \text{threshold}$) in `SoftwarePcmDspProcessor` and `AudioBoosterService` soft-knee tanh limiters.
- **Audio Loop Allocations:** Eliminated 48,000 `Pair<Double, Double>` heap allocations per second in PCM audio capture loop via zero-allocation `processStereoSampleDirect`.
- **Audio Lifecycle:** Added missing `audioManager.requestAudioFocus(req)` call in `AudioBoosterService` and registered `AudioDeviceCallback` with clean unregistration on service destroy.
- **UI Interaction:** Connected `freeLensDragEnabled` setting to `GlassLensTabBar`, ensuring 100% reliable tab switching on normal tap without accidental drag interception.
- **Localization & Icons:** Eliminated hardcoded strings in Glance Widget, Home, Island JSON Builder, and System screens, achieving 100% key parity between English and Russian resources. Replaced deprecated Compose icons with `AutoMirrored` counterparts.
- **Documentation:** Restored complete release instructions in `docs/RELEASING.md` and cleaned markdown formatting in `README.ru.md`.

## [1.3.1] - 2026-10-20

### Added
- Added author information (`MrQuster`) and GitHub profile/repository links to the About section in Settings, the final onboarding step, and the diagnostic report header (`HyperBoost 1.3.1 by MrQuster`).

## [1.3.0] - 2026-10-20

### Added
- Software 64-bit Biquad low-shelf bass filter (95 Hz) and Mid/Side stereo widener (`SoftwarePcmDspProcessor`) for Audio Capture mode.
- Automatic fallback routing for Bass Boost, 3D Surround, and Left/Right balance through a 6-band `DynamicsProcessing.PreEq` stage on Session 0.
- Real-time backdrop blur under the translucent bottom navigation bar using `dev.chrisbanes.haze`.
- Full English and Russian localization across all screens, notifications, and Super Island.

## [1.2.0] - 2026-10-19
- Fixed circular wave clipping in `CircularLiquidDial`, aligned `0-100-200-500-1000%` scale quarters, and corrected acoustic dB readout (`20 * log10(gain)`).

## [1.1.0] - 2026-10-19
- Added droplet adaptive icon, Super Island event ring buffer (100 entries), and 5-step island self-test.

## [1.0.0] - 2026-10-19
- Initial public release (`io.github.mrquster.hyperboost`).
