# Changelog

Notable changes to Airpoint. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-07

First public release. This is a ground-up rebuild of the earlier *S Pen Pointer* prototype
under a new name and identity.

### Added

- **Brand**: the Loop mark, generated from a prolate trochoid, in display and small
  optical cuts, with an adaptive, themed and notification icon set and a write-on
  animation.
- **Android app** rebuilt in Jetpack Compose with a full design system: WCAG AA color
  tokens for light and dark, Sora display type, a custom icon set, spring motion and
  haptics.
- Onboarding with in-context permission requests.
- Link stage that visualizes S Pen → phone → PC, animated by real pen motion.
- Live pad that mirrors the pen, with a tapering trail and click ripples.
- **Automatic discovery** of PCs on the local network, with a manual-address fallback.
- **PIN pairing** with per-phone tokens; exponential lockouts after wrong PINs.
- Automatic reconnects with backoff, including finding a PC whose IP address changed.
- Plain-language status and recovery actions for every failure.
- Speed and smoothing that apply live; optional double-press to center.
- **Windows tray app** with a status-colored icon, PIN display, paired-phone management,
  pause, start with Windows and a log folder.
- Simulated S Pen build variant, so the app can be developed without Samsung's SDK.
- `--dry-run` desktop mode for testing without moving the cursor.
- Documentation: user guide, design system and protocol specification.

### Changed

- The WebSocket server now requires authentication, and browsers are refused.
- The mouse button is released if a phone disconnects mid-press.
- Sub-pixel motion no longer loses a pixel to floating-point rounding.
- Release builds are minified with R8 (3 MB APK).

### Removed

- Samsung's proprietary SDK files from the repository. See `android/app/libs/README.md`.

[1.0.0]: https://github.com/AR13X3/airpoint/releases/tag/v1.0.0
