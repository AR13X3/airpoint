# Contributing to Airpoint

Thanks for your interest. Bug reports, ideas, design feedback, code and documentation are
all welcome.

## Ways to contribute

- **Report a bug** or **suggest a feature** through [GitHub Issues](https://github.com/AR13X3/airpoint/issues).
  For bugs, include your phone and S Pen model, Android version, Windows version, and the
  steps to reproduce. The PC's log (tray › **Open log folder**) helps a lot.
- **Improve the code or docs** with a pull request.

## Project structure

```
android/                     Android app: Kotlin, Jetpack Compose, Material 3
  app/src/main/…/core        models and settings (DataStore)
  app/src/main/…/net         PC link (OkHttp WebSocket) and UDP discovery
  app/src/main/…/session     the pointing session: S Pen + link + reconnects
  app/src/main/…/service     foreground service and notification
  app/src/main/…/ui          theme, components and screens
  app/src/spen/              Samsung S Pen Remote SDK implementation
  app/src/simulated/         stand-in pen for builds without the SDK
desktop/                     Windows tray app: Python
  airpoint/server.py         WebSocket server and authentication
  airpoint/pairing.py        PINs, tokens and lockouts
  airpoint/pointer.py        eased mouse output
  airpoint/discovery.py      UDP discovery responder
  airpoint/tray.py           tray icon and menu
  tests/                     pytest suite
tools/brand/                 generators for every logo asset
docs/                        user guide, design system, protocol
```

## Development setup

### Desktop

```bash
cd desktop
python -m venv .venv
.venv\Scripts\pip install -r requirements-dev.txt
.venv\Scripts\python -m pytest                          # run the tests
.venv\Scripts\python -m airpoint --headless --dry-run    # run without touching your mouse
```

`--dry-run` logs pointer events instead of moving the cursor, which is handy when testing
against an emulator or the simulated pen. Set `AIRPOINT_DATA_DIR` to keep test pairings
out of your real settings.

### Android

Open `android/` in Android Studio, or use Gradle directly (JDK 17+):

```bash
cd android
./gradlew assembleDebug                          # real S Pen (needs the SDK jars)
./gradlew assembleDebug -Pairpoint.simulateSpen  # simulated pen, any device
```

The S Pen Remote SDK is proprietary and isn't in the repository. See
[`android/app/libs/README.md`](android/app/libs/README.md). Without the jars, the build
automatically uses the simulated pen.

To test pairing on an **emulator**, run the desktop app on the same machine. The emulator
reaches the host as `10.0.2.2`, and discovery works through it.

### Brand assets

The logo is generated, never hand-edited. To change the mark, edit the parameters in
`tools/brand/build_brand.py` and run:

```bash
python tools/brand/build_brand.py     # SVGs, Android vectors, Compose centerline, tray paths
python tools/brand/animate_mark.py    # the write-on GIF
desktop\.venv\Scripts\python desktop/tools/make_icons.py   # the .exe icon
```

## Guidelines

- **Read [DESIGN.md](docs/DESIGN.md) before changing UI.** Use the color tokens, type
  styles and motion specs in `ui/theme/`, not raw values.
- **Motion explains state.** Use springs (`Motion.calm()` / `Motion.lively()`), and check
  that anything ambient switches off when `Air.reduceMotion` is true.
- **Accessibility is part of done.** Text must reach 4.5:1 contrast, touch targets 44 dp,
  and controls need semantics for TalkBack.
- **Write copy for people.** Say what happened and what to do next, with no error codes.
- **Keep the protocol compatible.** New message types must be ignorable by older peers.
  Update [PROTOCOL.md](docs/PROTOCOL.md) with any change.
- Match the style of the surrounding code, keep comments short and useful, and add tests
  for desktop logic.

## Pull requests

1. Fork the repository and branch from `main`.
2. Keep each pull request focused, and explain what and why. Include before/after
   screenshots or a short recording for UI changes.
3. Make sure `pytest` passes and both Android variants build.

## Licensing

Airpoint is MIT-licensed. By contributing, you agree that your contribution is licensed
under the same terms.
