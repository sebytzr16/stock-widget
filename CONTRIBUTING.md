# Contributing

Thanks for helping. Bug reports, ideas and pull requests are all welcome.

## Principles

The app should stay **light, fast and simple**:

- Prefer the platform and the existing AndroidX libraries over new dependencies. A new library needs a strong reason.
- Keep logic that doesn't need Android in `core/`, as plain Kotlin with unit tests.
- Put every user-facing string in `res/values/strings.xml`.
- Match the surrounding code: naming, comment density and formatting (Kotlin official style).

## Building and testing

You need JDK 17 and the Android SDK (Android Studio includes both).

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI runs the same command on every push and pull request.

## Pull requests

- Keep each pull request focused on one change.
- Describe what changed and why, and how you tested it (device or emulator, Android version).
- For UI changes, include a screenshot in light and dark theme.

## Reporting bugs

Please include your Android version, device, the symbols involved and the steps to reproduce.
