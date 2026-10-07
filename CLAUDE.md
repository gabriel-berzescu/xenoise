# xenoise

Android noise machine in Kotlin and Jetpack Compose. Same toolchain as the sibling
android-bedrock-loom project: AGP 8.7.2, Kotlin 2.0.21, Gradle 8.9, compileSdk 35, minSdk 26.

## Commands

- Install on the USB-connected phone: `./gradlew installDebug`
- Unit tests: `./gradlew testDebugUnitTest`

## Notes

- The first version was written in a cloud session without the Android SDK. The audio, model
  and playback code was compiled and tested there against stand-ins for the Android APIs, but
  the Compose UI was never compiled. If the first build fails, the errors will most likely be
  in `ui/`; fix them in place.
- `audio/NoiseSynth.kt`, `audio/Fft.kt`, `audio/Levels.kt` and everything in `model/` are
  plain Kotlin with no Android imports. Keep them that way so the unit tests run on the JVM.
- A noise is only a slope in dB per octave. Presets are −6, −3, 0, +3, +6. Custom slopes
  range from −9 to +9 and are straight lines only, by design.
- `NoisePlayer` is the single source of truth. The UI and `PlaybackService` only observe
  its `state` and call its methods on the main thread.
