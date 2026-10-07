# xenoise

A noise machine for Android. Brown, pink, white, blue and violet noise, plus your own
custom noises, shaped by tilting a single straight line on a spectrum.

## What it does

- **Five classic colors.** Each one is a straight line on a chart with frequency on a log
  scale and loudness in decibels. They differ only in how steeply the line tilts.

  | Color  | Slope per octave | Sounds like            |
  |--------|------------------|------------------------|
  | Brown  | −6 dB            | a distant waterfall    |
  | Pink   | −3 dB            | steady rain            |
  | White  | 0 dB             | a fan on high          |
  | Blue   | +3 dB            | a fine spray           |
  | Violet | +6 dB            | hissing steam          |

- **Custom noises.** Open the editor, start from pink or any other color, and drag anywhere
  on the chart to tilt the line between −9 and +9 dB per octave. The sound follows your
  finger live. The line snaps onto the five presets with a light haptic tick. Name it and
  save it. Saved noises can be edited or deleted later.
- **Keeps playing with the screen off.** A media notification and lock-screen controls
  offer play, pause and stop. Unplugging headphones pauses. A phone call pauses the noise
  and resumes it afterwards.
- **Sleep timer.** From 15 minutes to 8 hours. The noise fades out gently over the last
  minute, then stops.
- **Level matched.** Every slope plays at the same perceived loudness, so switching colors
  never jumps in volume.

## Build and install

Connect the phone over USB with USB debugging on, then:

```bash
# Build and install on the connected phone
./gradlew installDebug

# Build only (APK lands in app/build/outputs/apk/debug/)
./gradlew assembleDebug

# Unit tests for the sound engine and helpers
./gradlew testDebugUnitTest
```

Requirements: Android 8.0 (API 26) or newer on the phone, JDK 17 and the Android SDK on the
computer. The debug build installs as `com.xenoise.debug`.

If Gradle says "SDK location not found", point it at the SDK. Copy `local.properties` from
another Android project, or set `ANDROID_HOME`, or open the project once in Android Studio.

## How the sound is made

`NoiseSynth` builds the noise in the frequency domain. Every 43 ms it fills a spectrum
with the target slope and random phases, runs one FFT, and overlap-adds the windowed result
into the output. The real and imaginary parts of the FFT become the left and right
channels, which come out as two independent noises with the same spectrum.

- The spectrum is exact: measured slopes land within a few hundredths of a dB per octave.
- Slope changes glide over about a third of a second, so dragging the line never clicks.
- Each slope is normalized with K-weighting, the curve LUFS loudness meters use.
- `NoiseEngine` streams the result to an `AudioTrack` on its own thread and ramps every
  level change sample by sample. When paused, the thread sleeps and uses no CPU.

## Project layout

```
app/src/main/kotlin/com/xenoise/
├── audio/      NoiseSynth (DSP), Fft, Levels (volume curve, limiter), NoiseEngine (AudioTrack)
├── model/      Noise and Presets, Slopes (snap, format, describe), NoiseColors, TimerOptions
├── data/       NoiseLibrary: saved noises, volume, last selection (SharedPreferences)
├── playback/   NoisePlayer (state, timer, audio focus), PlaybackService (notification), Artwork
└── ui/         XenoiseRoot, home/, editor/, components/ (NoiseOrb, SpectrumPlot, ...), theme/
```
