# Samsung S Pen Remote SDK

Airpoint reads the S Pen's button and air motion through Samsung's **S Pen Remote SDK**.
The SDK is proprietary, so it is not included in this repository.

To build the real app:

1. Download **S Pen Remote SDK 1.0.1** from Samsung Developers:
   <https://developer.samsung.com/galaxy-spen-remote>
2. Unzip it and copy both jars into this folder:
   - `sdk-v1.0.0.jar`
   - `spenremote-v1.0.1.jar`
3. Build as usual (`./gradlew assembleDebug`). Gradle detects the jars and compiles
   `src/spen/` against them.

Both jars are required: `spenremote` depends on `sdk` at runtime, and leaving it out
causes a `NoClassDefFoundError` when connecting.

Without the jars, or when building with `-Pairpoint.simulateSpen`, the app compiles
`src/simulated/` instead. That variant uses a stand-in pen that traces a slow
figure-eight, so you can work on the UI on any phone or emulator. Settings › About shows
which variant you're running.

The jars are gitignored. Please don't commit them.
