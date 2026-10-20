# Changelog

All notable changes to **HyperBoost** are documented in this file.
Format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3.0] - 2026-10-20

### Added
- Software 64-bit Biquad low-shelf bass filter (95 Hz) and Mid/Side stereo widener (`SoftwarePcmDspProcessor`) for Audio Capture mode.
- Automatic fallback routing for Bass Boost, 3D Surround, and Left/Right balance through a 6-band `DynamicsProcessing.PreEq` stage when legacy hardware effects report `strengthSupported = false` on Session 0.
- Audio effect self-test button (**Verify Audio Effects**) in the System tab with per-effect route reporting.
- Real-time backdrop blur under the translucent bottom navigation bar using `dev.chrisbanes.haze`.
- Full English and Russian localization across all screens, dialogs, diagnostics, notifications, and Super Island (`locales_config.xml` + instant service locale switching).
- Unit tests (`DspAndLocalizationTest`) verifying Biquad bass amplification, Mid/Side widening, and 100% key parity between `values/strings.xml` and `values-ru/strings.xml`.

### Changed
- Rewrote `README.md` (English) and `README.ru.md` (Russian) with a clear device compatibility table and straightforward documentation.

## [1.2.0] - 2026-10-19
- Fixed circular wave clipping in `CircularLiquidDial`, aligned `0-100-200-500-1000%` scale quarters, and corrected acoustic dB readout (`20 * log10(gain)`).

## [1.1.0] - 2026-10-19
- Added droplet adaptive icon, Super Island event ring buffer (100 entries), and 5-step island self-test.

## [1.0.0] - 2026-10-19
- Initial public release (`io.github.mrquster.hyperboost`).
