# Stardust Sandbox

A generative-art toy for Android, written in Kotlin with no external libraries. Paint with glowing stars, drop black holes, and tilt your phone to pour the stardust around. Every touch plays a note.

## Features

- **Emit mode:** drag a finger to paint streaming stars. Flick to throw them. Hold still for a supernova burst.
- **Well mode:** tap to drop a black hole (up to 5). Drag to move it. Stars orbit it and get swallowed.
- **Repel mode:** your finger becomes a force field that pushes stars away.
- **Tilt gravity:** the accelerometer pours the stars toward the low side of your phone.
- **5 colour palettes:** Aurora, Sunset, Ocean, Neon, Ember.
- **Synthesized sound:** notes on a pentatonic scale, generated in code (no audio files). Pitch depends on where you touch.
- **Haptics** on taps and supernovas.

Everything is drawn with the Android Canvas using additive blending on the GPU. No permissions are required.

## Build

**Requirements:** JDK 17 and the Android SDK (or Android Studio).

With Android Studio: open the project folder and press **Run**, or choose **Build > Build APK(s)**.

With Gradle:

```
gradle assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds the debug APK on every push to `main` and uploads it as a build artifact.

## Install on a phone

Copy `app-debug.apk` to an Android device and open it. You may need to allow "Install unknown apps" for your file manager or browser. This is a debug build signed with a debug key.

## Tech

- Kotlin, Android SDK (min SDK 24, target SDK 34), Gradle Kotlin DSL
- Custom `View` with a struct-of-arrays particle system (up to 1,400 particles)
- Sound synthesized with `AudioTrack`; tilt via `SensorManager`

## Project structure

```
app/src/main/java/com/zak/stardust/
  MainActivity.kt    Button bar and accelerometer
  StardustView.kt    Particle physics and drawing
  Synth.kt           Tiny sound synthesizer
```
